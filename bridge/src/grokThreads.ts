/**
 * Per-session Grok conversation state, tuned for xAI prompt caching.
 *
 * xAI caches by exact prefix match from the start of the messages array
 * (docs.x.ai → Prompt Caching). Rules that keep the cache hot and cheap:
 *
 *  1. Append-only — never edit or reorder earlier messages.
 *  2. Static content first — base system prompt, then standing user rules.
 *  3. Rules / system prompt are stored ONCE (not rewritten). When they change,
 *     a single "[Rules update]" / "[System update]" message is appended —
 *     cache-safe, and the old prefix stays reusable.
 *  4. Threads are compacted when they exceed a token budget, or after a single
 *     huge assistant turn: older turns collapse into a digest and a fresh
 *     convId is issued. One cache miss, then cheap again — and we stay far
 *     below the long-context pricing tier where even cached tokens cost more.
 */

import fs from "node:fs";
import path from "node:path";
import { createHash, randomUUID } from "node:crypto";

export type GrokChatRole = "system" | "user" | "assistant";

export type GrokChatMessage = {
  role: GrokChatRole;
  content: string;
};

type GrokThread = {
  convId: string;
  messages: GrokChatMessage[];
  /** Hash of the rules block currently active in this thread. */
  rulesHash?: string;
  /** Hash of GROK_STABLE_SYSTEM_PROMPT when this thread last synced. */
  systemPromptHash?: string;
  /** API model id this thread was started with (reset when it changes). */
  modelId?: string;
};

type GrokThreadStore = Record<string, GrokThread>;

const GROK_THREADS_PATH =
  process.env.GROK_THREADS_PATH ??
  path.join(process.cwd(), "config", "grok-threads.json");

/**
 * Compaction budget. ~4 chars/token; 12k tokens keeps threads cheap and far
 * below long-context thresholds. Cached input still bills per token — a
 * bounded thread caps the per-turn floor cost.
 */
const MAX_THREAD_TOKENS = 12_000;
/**
 * After a research dump, compact on the next turn even if the whole thread
 * is still under MAX_THREAD_TOKENS — huge prefixes stay expensive at high hit %.
 */
const HUGE_ASSISTANT_TOKENS = 4_000;
const CHARS_PER_TOKEN = 4;
/** Messages kept verbatim (newest) when a thread is compacted. */
const KEEP_RECENT_MESSAGES = 4;
/** Size caps for the compaction digest. */
const DIGEST_PER_MESSAGE_CHARS = 220;
const DIGEST_TOTAL_CHARS = 2_400;

/** Stable system prompt — identical across turns and threads so caching hits. */
export const GROK_STABLE_SYSTEM_PROMPT = [
  "You are the user's assistant, reached from their phone via Invictus Link.",
  "Reply naturally and match the tone of their message.",
  "You can see images the user attaches from their phone — describe, analyze, or give your opinion on them when asked.",
  "You have real-time web search and X search — use them for current prices, news, products, releases, linked pages, and anything that may have changed recently.",
  "When the user pastes a URL or asks about a link, look it up and give research, context, or your honest opinion — don't refuse just because it's a link.",
  "When the user asks about cost, pricing, availability, or what something is today, search first instead of guessing from memory.",
  "You cannot access the user's PC files or run code on their machine (except images attached in this chat).",
  "If the user asks for file edits or code changes on their PC, explain that they should switch to the Cursor provider in Settings for that.",
].join("\n");

function loadStore(): GrokThreadStore {
  try {
    const raw = fs.readFileSync(GROK_THREADS_PATH, "utf-8");
    const parsed = JSON.parse(raw) as GrokThreadStore;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function saveStore(store: GrokThreadStore) {
  fs.mkdirSync(path.dirname(GROK_THREADS_PATH), { recursive: true });
  fs.writeFileSync(GROK_THREADS_PATH, JSON.stringify(store, null, 2), "utf-8");
}

function hashText(text: string): string {
  if (!text) return "";
  return createHash("sha256").update(text).digest("hex").slice(0, 16);
}

function hashRules(rulesBlock: string): string {
  return hashText(rulesBlock);
}

function hashSystemPrompt(): string {
  return hashText(GROK_STABLE_SYSTEM_PROMPT);
}

function estimateThreadTokens(messages: GrokChatMessage[]): number {
  let chars = 0;
  for (const m of messages) chars += m.content.length + 8;
  return Math.ceil(chars / CHARS_PER_TOKEN);
}

function lastAssistantIsHuge(messages: GrokChatMessage[]): boolean {
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i];
    if (m.role !== "assistant") continue;
    return estimateThreadTokens([m]) > HUGE_ASSISTANT_TOKENS;
  }
  return false;
}

/** Keep continuity but cap a research dump so the next turn's prefix stays cheap. */
function truncateHugeAssistant(m: GrokChatMessage): GrokChatMessage {
  if (m.role !== "assistant") return m;
  if (estimateThreadTokens([m]) <= HUGE_ASSISTANT_TOKENS) return m;
  const maxChars = HUGE_ASSISTANT_TOKENS * CHARS_PER_TOKEN;
  return {
    role: "assistant",
    content:
      m.content.slice(0, maxChars) +
      "\n…[earlier reply truncated after compaction to keep this chat fast and cheap]",
  };
}

function freshThread(rulesBlock: string, modelId?: string): GrokThread {
  const messages: GrokChatMessage[] = [
    { role: "system", content: GROK_STABLE_SYSTEM_PROMPT },
  ];
  if (rulesBlock) {
    messages.push({ role: "system", content: rulesBlock });
  }
  return {
    convId: randomUUID(),
    messages,
    rulesHash: hashRules(rulesBlock),
    systemPromptHash: hashSystemPrompt(),
    modelId,
  };
}

/** Collapse older turns into a compact digest; keep the newest turns verbatim. */
function compactThread(thread: GrokThread, rulesBlock: string): GrokThread {
  const conversational = thread.messages.filter((m) => m.role !== "system");
  const recent = conversational.slice(-KEEP_RECENT_MESSAGES);
  const older = conversational.slice(0, -KEEP_RECENT_MESSAGES);

  const digestLines: string[] = [];
  let digestChars = 0;
  for (const m of older) {
    const prefix = m.role === "user" ? "User" : "Assistant";
    const snippet = m.content.replace(/\s+/g, " ").slice(0, DIGEST_PER_MESSAGE_CHARS);
    const line = `- ${prefix}: ${snippet}${m.content.length > DIGEST_PER_MESSAGE_CHARS ? "…" : ""}`;
    if (digestChars + line.length > DIGEST_TOTAL_CHARS) break;
    digestLines.push(line);
    digestChars += line.length;
  }

  // Preserve modelId so model-change detection stays continuous after compact.
  const next = freshThread(rulesBlock, thread.modelId);
  if (digestLines.length > 0) {
    next.messages.push({
      role: "system",
      content: [
        "[Conversation so far — condensed to keep this chat fast and cheap. Earlier turns in brief:]",
        ...digestLines,
      ].join("\n"),
    });
  }
  next.messages.push(...recent.map(truncateHugeAssistant));
  return next;
}

/**
 * Get (or create) the session's thread, keeping the standing-rules message
 * current and compacting when the thread exceeds its token budget.
 */
export function getGrokThread(projectId: string, rulesBlock = "", modelId?: string): GrokThread {
  const store = loadStore();
  let thread = store[projectId];

  if (!thread?.convId || !Array.isArray(thread.messages) || thread.messages.length === 0) {
    thread = freshThread(rulesBlock, modelId);
    store[projectId] = thread;
    saveStore(store);
    return thread;
  }

  // Model upgrade (e.g. grok-latest → grok-4.5) — start a clean thread so old
  // "I'm Grok 4" turns don't poison identity / sticky routing.
  if (modelId && thread.modelId && thread.modelId !== modelId) {
    thread = freshThread(rulesBlock, modelId);
    store[projectId] = thread;
    saveStore(store);
    return thread;
  }

  let changed = false;
  if (modelId && !thread.modelId) {
    thread.modelId = modelId;
    changed = true;
  }

  // System prompt changed since this thread last synced → append one update
  // (append-only keeps the cached prefix valid).
  const currentSystemHash = hashSystemPrompt();
  if ((thread.systemPromptHash ?? "") !== currentSystemHash) {
    const head = thread.messages[0];
    const headIsStale =
      head?.role === "system" && head.content !== GROK_STABLE_SYSTEM_PROMPT;
    // Known prior hash, or legacy thread whose head text already drifted.
    if (thread.systemPromptHash || headIsStale) {
      thread.messages = [
        ...thread.messages,
        {
          role: "system",
          content: `[System update — updated assistant instructions]\n${GROK_STABLE_SYSTEM_PROMPT}`,
        },
      ];
    }
    thread.systemPromptHash = currentSystemHash;
    changed = true;
  }

  // Rules changed since this thread last saw them → append one update message
  // (append-only keeps the cached prefix valid).
  const currentHash = hashRules(rulesBlock);
  if ((thread.rulesHash ?? "") !== currentHash) {
    thread.messages = [
      ...thread.messages,
      {
        role: "system",
        content: rulesBlock
          ? `[Rules update — these standing rules replace any earlier ones]\n${rulesBlock}`
          : "[Rules update — the user's standing rules have been cleared. Ignore earlier standing rules.]",
      },
    ];
    thread.rulesHash = currentHash;
    changed = true;
  }

  // Over budget, or last assistant turn was a research dump → compact.
  if (
    estimateThreadTokens(thread.messages) > MAX_THREAD_TOKENS ||
    lastAssistantIsHuge(thread.messages)
  ) {
    thread = compactThread(thread, rulesBlock);
    changed = true;
  }

  if (changed) {
    store[projectId] = thread;
    saveStore(store);
  }
  return thread;
}

export function resetGrokThread(projectId: string): void {
  const store = loadStore();
  delete store[projectId];
  saveStore(store);
}

/** Append user + assistant turns without modifying earlier messages. */
export function appendGrokTurn(
  projectId: string,
  userContent: string,
  assistantContent: string,
): GrokThread {
  const store = loadStore();
  const thread = store[projectId] ?? freshThread("");
  thread.messages = [
    ...thread.messages,
    { role: "user", content: userContent },
    { role: "assistant", content: assistantContent },
  ];
  store[projectId] = thread;
  saveStore(store);
  return thread;
}

export function buildGrokRequestMessages(
  thread: GrokThread,
  userContent: string,
): GrokChatMessage[] {
  return [...thread.messages, { role: "user", content: userContent }];
}
