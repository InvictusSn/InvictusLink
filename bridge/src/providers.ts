/**
 * Multi-provider AI registry for the Invictus Link bridge.
 *
 * Providers are stored in config/providers.json on the PC. API keys never
 * leave this machine — the phone only ever sees masked keys and metadata.
 *
 * Two execution kinds:
 *  - "agent": Cursor SDK — full coding agent with file/tool access
 *  - "chat":  OpenAI-compatible or Anthropic chat completions (incl. local
 *             Ollama / LM Studio) — conversation only, no PC file access
 */

import fs from "node:fs";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { getXaiFrontierModel, getXaiRates } from "./xaiPricing.js";

export type ProviderType =
  | "cursor"
  | "openai"
  | "anthropic"
  | "xai"
  | "google"
  | "ollama"
  | "lmstudio"
  | "custom";

export type ProviderRecord = {
  id: string;
  type: ProviderType;
  label: string;
  /** Secret — PC only. Never serialized to API responses. */
  apiKey?: string;
  /** For local/custom OpenAI-compatible servers. */
  baseUrl?: string;
  model?: string;
  createdAt: number;
};

export type ProviderPublic = {
  id: string;
  type: ProviderType;
  label: string;
  model: string;
  maskedKey: string;
  baseUrl?: string;
  isActive: boolean;
  /** "agent" = full Cursor coding agent, "chat" = conversation only */
  kind: "agent" | "chat";
  isLocal: boolean;
  isBuiltIn: boolean;
};

export type RoutingMode = "manual" | "auto";

type ProviderStore = {
  activeProviderId?: string;
  /** Restored when Auto mode is turned off. */
  lastManualProviderId?: string;
  routingMode?: RoutingMode;
  providers: ProviderRecord[];
};

export type TokenUsage = {
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  costUsd: number;
};

export type ChatCompletionResult = {
  text: string;
  usage?: TokenUsage;
};

export type ChatCompletionOptions = {
  /** Stable x-grok-conv-id for xAI prompt caching. */
  grokConvId?: string;
  /** Full message list (append-only). When set, overrides systemPrompt/userPrompt. */
  messages?: Array<{ role: "system" | "user" | "assistant"; content: string }>;
  /** Image data URLs for the current user turn (xAI vision). */
  images?: Array<{ dataUrl: string; detail?: "auto" | "low" | "high" }>;
  /** Grok 4.5 reasoning depth (default API is high — we pick lower for casual chat). */
  reasoningEffort?: "low" | "medium" | "high";
  /** When false, omit web_search / x_search for faster casual replies. */
  enableSearchTools?: boolean;
};

export const PROVIDER_META: Record<
  ProviderType,
  { label: string; defaultModel: string; defaultBaseUrl?: string; isLocal: boolean; needsKey: boolean }
> = {
  cursor: { label: "Cursor", defaultModel: "composer-2.5", isLocal: false, needsKey: true },
  openai: { label: "OpenAI", defaultModel: "gpt-4o-mini", isLocal: false, needsKey: true },
  anthropic: { label: "Claude", defaultModel: "claude-sonnet-4-5", isLocal: false, needsKey: true },
  xai: { label: "xAI", defaultModel: "grok-latest", isLocal: false, needsKey: true },
  google: { label: "Gemini", defaultModel: "gemini-2.5-flash", isLocal: false, needsKey: true },
  ollama: {
    label: "Ollama",
    defaultModel: "llama3.2",
    defaultBaseUrl: "http://127.0.0.1:11434",
    isLocal: true,
    needsKey: false,
  },
  lmstudio: {
    label: "LM Studio",
    defaultModel: "local-model",
    defaultBaseUrl: "http://127.0.0.1:1234",
    isLocal: true,
    needsKey: false,
  },
  custom: { label: "Custom", defaultModel: "", isLocal: true, needsKey: false },
};

const PROVIDERS_STORE_PATH =
  process.env.PROVIDERS_PATH ??
  path.join(process.cwd(), "config", "providers.json");

const ENV_CURSOR_ID = "cursor-env";

let cachedEnvCursorKey = "";
let cachedEnvCursorModel = "composer-2.5";

/** Register the CURSOR_API_KEY/.env fallback so it appears as a built-in provider. */
export function configureEnvCursorProvider(apiKey: string, modelId: string) {
  cachedEnvCursorKey = apiKey;
  cachedEnvCursorModel = modelId;
}

function loadStore(): ProviderStore {
  try {
    const raw = fs.readFileSync(PROVIDERS_STORE_PATH, "utf-8");
    const parsed = JSON.parse(raw) as ProviderStore;
    if (!Array.isArray(parsed.providers)) return { providers: [] };
    return parsed;
  } catch {
    return { providers: [] };
  }
}

function saveStore(store: ProviderStore) {
  fs.mkdirSync(path.dirname(PROVIDERS_STORE_PATH), { recursive: true });
  fs.writeFileSync(PROVIDERS_STORE_PATH, JSON.stringify(store, null, 2), "utf-8");
}

/** Built-in provider synthesized from .env — always present when the key is set. */
function envCursorRecord(): ProviderRecord | undefined {
  if (!cachedEnvCursorKey) return undefined;
  return {
    id: ENV_CURSOR_ID,
    type: "cursor",
    label: "Cursor",
    apiKey: cachedEnvCursorKey,
    model: cachedEnvCursorModel,
    createdAt: 0,
  };
}

export function listProviderRecords(): ProviderRecord[] {
  const store = loadStore();
  const env = envCursorRecord();
  return env ? [env, ...store.providers] : [...store.providers];
}

export function getActiveProvider(): ProviderRecord | undefined {
  const store = loadStore();
  const all = listProviderRecords();
  if (all.length === 0) return undefined;
  const active = all.find((p) => p.id === store.activeProviderId);
  return active ?? all[0];
}

export function getRoutingMode(): RoutingMode {
  const store = loadStore();
  return store.routingMode === "auto" ? "auto" : "manual";
}

export function setRoutingMode(mode: RoutingMode): RoutingMode {
  const store = loadStore();
  if (mode === "auto") {
    if (store.activeProviderId) {
      store.lastManualProviderId = store.activeProviderId;
    }
  } else if (store.lastManualProviderId) {
    const exists = listProviderRecords().some((p) => p.id === store.lastManualProviderId);
    if (exists) {
      store.activeProviderId = store.lastManualProviderId;
    }
  }
  store.routingMode = mode;
  saveStore(store);
  return mode;
}

/** Estimated pricing per provider type (USD per million tokens). */
type PricingEntry = { input: number; cachedInput: number; output: number };

/** Friendly names → valid xAI API model ids. */
const XAI_MODEL_ALIASES: Record<string, string> = {
  grok: "grok-latest",
  "grok-beta": "grok-latest",
  "grok 4.5": "grok-4.5",
  "grok4.5": "grok-4.5",
  "grok 4.5 latest": "grok-4.5-latest",
};

/** Rolling-latest ids — resolved to xAI's newest flagship from the live catalog. */
const XAI_AUTO_LATEST_IDS = new Set(["", "grok-latest", "grok-beta", "grok"]);

/** Old defaults from earlier Link builds — upgraded to the live frontier. */
const XAI_LEGACY_PINNED = new Set(["grok-4.3", "grok-4.20"]);

function resolveXaiModel(raw: string): string {
  const lowered = raw.toLowerCase().trim();
  const aliased = XAI_MODEL_ALIASES[lowered] ?? lowered;
  if (!aliased) return PROVIDER_META.xai.defaultModel;

  // xAI's grok-latest alias can lag behind the real frontier (e.g. still Grok 4.3
  // when 4.5 is GA). Pick the newest grok-X.Y from our cached catalog instead.
  if (XAI_AUTO_LATEST_IDS.has(aliased) || XAI_LEGACY_PINNED.has(aliased)) {
    const frontier = getXaiFrontierModel();
    // Hard fallback: current public frontier when the catalog hasn't loaded yet.
    return frontier || "grok-4.5";
  }

  return aliased;
}

const PROVIDER_PRICING: Partial<Record<ProviderType, PricingEntry>> = {
  xai: { input: 2.0, cachedInput: 0.5, output: 6.0 },
  openai: { input: 0.15, cachedInput: 0.075, output: 0.6 }, // gpt-4o-mini class
  anthropic: { input: 3.0, cachedInput: 0.3, output: 15.0 }, // sonnet class
  google: { input: 0.3, cachedInput: 0.075, output: 2.5 }, // gemini flash class
  // ollama / lmstudio / custom are local → $0
};

/** Reference rate used to estimate savings when a local model handled the task. */
export const LOCAL_SAVINGS_REFERENCE: PricingEntry = {
  input: 2.0,
  cachedInput: 0.5,
  output: 6.0,
};

export function estimateChatCost(
  type: ProviderType,
  usage: { promptTokens: number; cachedTokens: number; completionTokens: number },
  model?: string,
): number {
  let pricing = PROVIDER_PRICING[type];
  // xAI: prefer exact live per-model rates over the static estimate.
  if (type === "xai" && model) {
    const live = getXaiRates(model);
    if (live) {
      pricing = {
        input: live.inputPerM,
        cachedInput: live.cachedInputPerM,
        output: live.outputPerM,
      };
    }
  }
  if (!pricing) return 0;
  const cached = Math.min(usage.cachedTokens, usage.promptTokens);
  const nonCached = Math.max(0, usage.promptTokens - cached);
  const inputCost = (nonCached * pricing.input + cached * pricing.cachedInput) / 1_000_000;
  const outputCost = (usage.completionTokens * pricing.output) / 1_000_000;
  return inputCost + outputCost;
}

/** xAI pricing (USD per million tokens). */
export function calculateGrokCost(usage: {
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
}): number {
  return estimateChatCost("xai", usage);
}

export function isLocalProviderType(type: ProviderType): boolean {
  return PROVIDER_META[type]?.isLocal ?? false;
}

export function providerKind(type: ProviderType): "agent" | "chat" {
  return type === "cursor" ? "agent" : "chat";
}

function maskKey(key?: string): string {
  if (!key) return "";
  if (key.length <= 4) return "••••";
  return `••••${key.slice(-4)}`;
}

export function toPublic(rec: ProviderRecord, activeId?: string): ProviderPublic {
  const meta = PROVIDER_META[rec.type];
  return {
    id: rec.id,
    type: rec.type,
    label: rec.label,
    model: resolveModel(rec),
    maskedKey: maskKey(rec.apiKey),
    baseUrl: meta.isLocal ? rec.baseUrl || meta.defaultBaseUrl : undefined,
    isActive: rec.id === activeId,
    kind: providerKind(rec.type),
    isLocal: meta.isLocal,
    isBuiltIn: rec.id === ENV_CURSOR_ID,
  };
}

export function listProvidersPublic(): {
  activeProviderId: string;
  routingMode: RoutingMode;
  providers: ProviderPublic[];
} {
  const all = listProviderRecords();
  const routingMode = getRoutingMode();
  const active = getActiveProvider();
  const activeIdForDisplay = routingMode === "auto" ? undefined : active?.id;
  return {
    activeProviderId: active?.id ?? "",
    routingMode,
    providers: all.map((p) => toPublic(p, activeIdForDisplay)),
  };
}

export function addProvider(input: {
  type: ProviderType;
  label?: string;
  apiKey?: string;
  baseUrl?: string;
  model?: string;
}): ProviderRecord {
  const meta = PROVIDER_META[input.type];
  if (!meta) throw new Error(`Unknown provider type: ${input.type}`);
  if (meta.needsKey && !input.apiKey?.trim()) {
    throw new Error(`${meta.label} requires an API key`);
  }
  if (input.type === "custom" && !input.baseUrl?.trim()) {
    throw new Error("Custom providers require a base URL");
  }
  const store = loadStore();
  const rec: ProviderRecord = {
    id: `prov_${randomUUID().slice(0, 8)}`,
    type: input.type,
    label: input.label?.trim() || meta.label,
    apiKey: input.apiKey?.trim() || undefined,
    baseUrl: input.baseUrl?.trim() || undefined,
    model: input.model?.trim() || undefined,
    createdAt: Date.now(),
  };
  store.providers.push(rec);
  // First provider added becomes active automatically.
  if (!store.activeProviderId && !envCursorRecord()) {
    store.activeProviderId = rec.id;
  }
  saveStore(store);
  return rec;
}

export function deleteProvider(id: string): boolean {
  if (id === ENV_CURSOR_ID) {
    throw new Error("The built-in Cursor provider comes from .env on the PC and can't be removed here");
  }
  const store = loadStore();
  const index = store.providers.findIndex((p) => p.id === id);
  if (index < 0) return false;
  store.providers.splice(index, 1);
  if (store.activeProviderId === id) {
    store.activeProviderId = undefined;
  }
  saveStore(store);
  return true;
}

export function activateProvider(id: string): ProviderRecord {
  const all = listProviderRecords();
  const rec = all.find((p) => p.id === id);
  if (!rec) throw new Error("Provider not found");
  const store = loadStore();
  store.activeProviderId = id;
  store.lastManualProviderId = id;
  saveStore(store);
  return rec;
}

function resolveBaseUrl(rec: ProviderRecord): string {
  const meta = PROVIDER_META[rec.type];
  switch (rec.type) {
    case "openai":
      return "https://api.openai.com/v1";
    case "xai":
      return "https://api.x.ai/v1";
    case "google":
      return "https://generativelanguage.googleapis.com/v1beta/openai";
    case "anthropic":
      return "https://api.anthropic.com/v1";
    default: {
      const base = (rec.baseUrl || meta.defaultBaseUrl || "").replace(/\/$/, "");
      if (!base) throw new Error("Provider has no base URL configured");
      // Local OpenAI-compatible servers expose /v1.
      return base.endsWith("/v1") ? base : `${base}/v1`;
    }
  }
}

export function resolveModel(rec: ProviderRecord): string {
  const meta = PROVIDER_META[rec.type];
  const raw = (rec.model || meta.defaultModel).trim();
  if (rec.type === "xai") {
    // Users often type "Grok" in the optional model field — not a valid API id.
    if (!raw) return resolveXaiModel(meta.defaultModel);
    return resolveXaiModel(raw);
  }
  return raw;
}

async function describeHttpError(res: Response, label: string): Promise<string> {
  const body = await res.text().catch(() => "");
  let message = body;
  try {
    const json = JSON.parse(body) as { error?: string | { message?: string } };
    if (typeof json.error === "string") message = json.error;
    else if (json.error?.message) message = json.error.message;
  } catch {
    // Keep raw body.
  }
  const trimmed = message.trim().slice(0, 240);
  if (res.status === 403 && label.includes("xAI")) {
    return trimmed
      ? `xAI returned 403: ${trimmed}. In console.x.ai, open your API key and enable Chat + Models access (or all endpoints).`
      : "xAI returned 403 — in console.x.ai, enable Chat and Models access for this API key.";
  }
  return trimmed ? `${label} returned ${res.status}: ${trimmed}` : `${label} returned ${res.status}`;
}

/**
 * Quick connectivity check. Cheap by design — model listing, no completions.
 */
export async function testProvider(rec: ProviderRecord): Promise<{ ok: boolean; detail: string }> {
  try {
    if (rec.type === "cursor") {
      return rec.apiKey
        ? { ok: true, detail: "Cursor API key present" }
        : { ok: false, detail: "Missing Cursor API key" };
    }
    if (rec.type === "anthropic") {
      const res = await fetchWithTimeout("https://api.anthropic.com/v1/models?limit=1", {
        headers: {
          "x-api-key": rec.apiKey ?? "",
          "anthropic-version": "2023-06-01",
        },
      });
      return res.ok
        ? { ok: true, detail: "Claude API reachable" }
        : { ok: false, detail: await describeHttpError(res, "Claude API") };
    }
    if (rec.type === "xai") {
      const headers: Record<string, string> = {
        Authorization: `Bearer ${rec.apiKey ?? ""}`,
      };
      // /models often 403 when the key only has chat ACLs — /api-key validates the key itself.
      const keyRes = await fetchWithTimeout("https://api.x.ai/v1/api-key", { headers });
      if (keyRes.ok) {
        return { ok: true, detail: "xAI API key valid" };
      }
      // Fallback: minimal chat ping (what Link actually uses).
      const chatRes = await fetchWithTimeout("https://api.x.ai/v1/chat/completions", {
        method: "POST",
        headers: { ...headers, "Content-Type": "application/json" },
        body: JSON.stringify({
          model: resolveModel(rec),
          max_tokens: 1,
          messages: [{ role: "user", content: "ping" }],
        }),
      });
      return chatRes.ok
        ? { ok: true, detail: "xAI chat API reachable" }
        : { ok: false, detail: await describeHttpError(chatRes, "xAI API") };
    }
    const base = resolveBaseUrl(rec);
    const headers: Record<string, string> = {};
    if (rec.apiKey) headers["Authorization"] = `Bearer ${rec.apiKey}`;
    const res = await fetchWithTimeout(`${base}/models`, { headers });
    return res.ok
      ? { ok: true, detail: "API reachable" }
      : { ok: false, detail: await describeHttpError(res, "API") };
  } catch (err) {
    return {
      ok: false,
      detail: err instanceof Error ? err.message : String(err),
    };
  }
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit & { timeoutMs?: number } = {},
): Promise<Response> {
  const { timeoutMs = 8000, ...rest } = init;
  const controller = new AbortController();
  const handle = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...rest, signal: controller.signal });
  } finally {
    clearTimeout(handle);
  }
}

/**
 * Run a prompt against a chat provider, streaming partial text via onDelta.
 * Returns the full response text.
 */
export async function runChatCompletion(
  rec: ProviderRecord,
  systemPrompt: string,
  userPrompt: string,
  onDelta: (fullText: string) => void,
  signal: AbortSignal,
  options: ChatCompletionOptions = {},
): Promise<ChatCompletionResult> {
  if (rec.type === "anthropic") {
    return runAnthropicStream(rec, systemPrompt, userPrompt, onDelta, signal);
  }
  if (rec.type === "xai") {
    const { runXaiResponsesStream } = await import("./xaiResponses.js");
    return runXaiResponsesStream(rec, systemPrompt, userPrompt, onDelta, signal, options);
  }
  return runOpenAiCompatibleStream(rec, systemPrompt, userPrompt, onDelta, signal, options);
}

async function runOpenAiCompatibleStream(
  rec: ProviderRecord,
  systemPrompt: string,
  userPrompt: string,
  onDelta: (fullText: string) => void,
  signal: AbortSignal,
  options: ChatCompletionOptions = {},
): Promise<ChatCompletionResult> {
  const base = resolveBaseUrl(rec);
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (rec.apiKey) headers["Authorization"] = `Bearer ${rec.apiKey}`;
  if (rec.type === "xai" && options.grokConvId) {
    headers["x-grok-conv-id"] = options.grokConvId;
  }

  const messages =
    options.messages ??
    [
      { role: "system" as const, content: systemPrompt },
      { role: "user" as const, content: userPrompt },
    ];

  const res = await fetch(`${base}/chat/completions`, {
    method: "POST",
    headers,
    signal,
    body: JSON.stringify({
      model: resolveModel(rec),
      stream: true,
      stream_options: { include_usage: true },
      messages,
    }),
  });
  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => "");
    throw new Error(
      `${rec.label} request failed (${res.status})${text ? `: ${text.slice(0, 300)}` : ""}`,
    );
  }

  let full = "";
  let usage: TokenUsage | undefined;
  for await (const data of sseDataLines(res.body, signal)) {
    if (data === "[DONE]") break;
    try {
      const json = JSON.parse(data) as {
        choices?: { delta?: { content?: string } }[];
        usage?: {
          prompt_tokens?: number;
          completion_tokens?: number;
          prompt_tokens_details?: { cached_tokens?: number };
        };
      };
      const chunk = json.choices?.[0]?.delta?.content;
      if (chunk) {
        full += chunk;
        onDelta(full);
      }
      if (json.usage) {
        const promptTokens = json.usage.prompt_tokens ?? 0;
        const cachedTokens = json.usage.prompt_tokens_details?.cached_tokens ?? 0;
        const completionTokens = json.usage.completion_tokens ?? 0;
        usage = {
          promptTokens,
          cachedTokens,
          completionTokens,
          costUsd: estimateChatCost(
            rec.type,
            { promptTokens, cachedTokens, completionTokens },
            resolveModel(rec),
          ),
        };
      }
    } catch {
      // Ignore malformed keep-alive lines.
    }
  }
  return { text: full, usage };
}

async function runAnthropicStream(
  rec: ProviderRecord,
  systemPrompt: string,
  userPrompt: string,
  onDelta: (fullText: string) => void,
  signal: AbortSignal,
): Promise<ChatCompletionResult> {
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    signal,
    headers: {
      "Content-Type": "application/json",
      "x-api-key": rec.apiKey ?? "",
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify({
      model: resolveModel(rec),
      max_tokens: 4096,
      stream: true,
      system: systemPrompt,
      messages: [{ role: "user", content: userPrompt }],
    }),
  });
  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => "");
    throw new Error(
      `Claude request failed (${res.status})${text ? `: ${text.slice(0, 300)}` : ""}`,
    );
  }

  let full = "";
  let inputTokens = 0;
  let cachedTokens = 0;
  let outputTokens = 0;
  for await (const data of sseDataLines(res.body, signal)) {
    try {
      const json = JSON.parse(data) as {
        type?: string;
        delta?: { type?: string; text?: string };
        message?: {
          usage?: { input_tokens?: number; cache_read_input_tokens?: number };
        };
        usage?: { output_tokens?: number };
      };
      if (json.type === "content_block_delta" && json.delta?.text) {
        full += json.delta.text;
        onDelta(full);
      }
      if (json.type === "message_start" && json.message?.usage) {
        inputTokens = json.message.usage.input_tokens ?? 0;
        cachedTokens = json.message.usage.cache_read_input_tokens ?? 0;
      }
      if (json.type === "message_delta" && json.usage?.output_tokens != null) {
        outputTokens = json.usage.output_tokens;
      }
    } catch {
      // Ignore non-JSON event lines.
    }
  }
  const usage: TokenUsage | undefined =
    inputTokens > 0 || outputTokens > 0
      ? {
          promptTokens: inputTokens,
          cachedTokens,
          completionTokens: outputTokens,
          costUsd: estimateChatCost("anthropic", {
            promptTokens: inputTokens,
            cachedTokens,
            completionTokens: outputTokens,
          }),
        }
      : undefined;
  return { text: full, usage };
}

/** Parse an SSE byte stream into the payloads of `data:` lines. */
async function* sseDataLines(
  body: ReadableStream<Uint8Array>,
  signal: AbortSignal,
): AsyncGenerator<string> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      if (signal.aborted) throw new Error("aborted");
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let newlineIndex: number;
      while ((newlineIndex = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, newlineIndex).trim();
        buffer = buffer.slice(newlineIndex + 1);
        if (line.startsWith("data:")) {
          yield line.slice("data:".length).trim();
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}
