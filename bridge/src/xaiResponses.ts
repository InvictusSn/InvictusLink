/**
 * xAI Responses API with agentic web + X search (+ optional vision images).
 *
 * Chat Completions alone has no live internet access. The Responses API with
 * web_search / x_search matches grok.com research behavior.
 */

import type { ChatCompletionOptions, ChatCompletionResult, ProviderRecord, TokenUsage } from "./providers.js";
import { estimateChatCost, resolveModel } from "./providers.js";
import { refreshXaiPricing } from "./xaiPricing.js";

type ResponseTextPart = { type: "input_text"; text: string };
type ResponseImagePart = {
  type: "input_image";
  image_url: string;
  detail?: "auto" | "low" | "high";
};
type ResponseContent = string | Array<ResponseTextPart | ResponseImagePart>;

type ResponseInputMessage = {
  role: "system" | "user" | "assistant" | "developer";
  content: ResponseContent;
};

function toResponsesInput(
  messages: Array<{ role: "system" | "user" | "assistant"; content: string }>,
  images: Array<{ dataUrl: string; detail?: "auto" | "low" | "high" }> = [],
): ResponseInputMessage[] {
  return messages.map((m, index) => {
    const isLastUser =
      m.role === "user" && index === messages.length - 1 && images.length > 0;
    if (!isLastUser) {
      return { role: m.role, content: m.content };
    }
    const parts: Array<ResponseTextPart | ResponseImagePart> = [
      ...images.map((img) => ({
        type: "input_image" as const,
        image_url: img.dataUrl,
        detail: img.detail ?? "high",
      })),
      { type: "input_text", text: m.content },
    ];
    return { role: "user", content: parts };
  });
}

function usageFromResponseCompleted(
  json: {
    response?: {
      usage?: {
        input_tokens?: number;
        output_tokens?: number;
        prompt_tokens?: number;
        completion_tokens?: number;
        input_tokens_details?: { cached_tokens?: number };
        prompt_tokens_details?: { cached_tokens?: number };
      };
    };
  },
  model: string,
): TokenUsage | undefined {
  const usage = json.response?.usage;
  if (!usage) return undefined;
  const promptTokens = usage.input_tokens ?? usage.prompt_tokens ?? 0;
  const completionTokens = usage.output_tokens ?? usage.completion_tokens ?? 0;
  const cachedTokens =
    usage.input_tokens_details?.cached_tokens ??
    usage.prompt_tokens_details?.cached_tokens ??
    0;
  return {
    promptTokens,
    cachedTokens,
    completionTokens,
    costUsd: estimateChatCost("xai", { promptTokens, cachedTokens, completionTokens }, model),
  };
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

export async function runXaiResponsesStream(
  rec: ProviderRecord,
  systemPrompt: string,
  userPrompt: string,
  onDelta: (fullText: string) => void,
  signal: AbortSignal,
  options: ChatCompletionOptions = {},
): Promise<ChatCompletionResult> {
  // Keep exact per-model pricing warm (background, never blocks the prompt).
  refreshXaiPricing(rec.apiKey);

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (rec.apiKey) headers["Authorization"] = `Bearer ${rec.apiKey}`;
  if (options.grokConvId) {
    headers["x-grok-conv-id"] = options.grokConvId;
  }

  const messages =
    options.messages ??
    [
      { role: "system" as const, content: systemPrompt },
      { role: "user" as const, content: userPrompt },
    ];

  const body: Record<string, unknown> = {
    model: resolveModel(rec),
    input: toResponsesInput(messages, options.images ?? []),
    stream: true,
    reasoning: { effort: options.reasoningEffort ?? "low" },
  };
  if (options.enableSearchTools !== false) {
    body.tools = [{ type: "web_search" }, { type: "x_search" }];
  }
  if (options.grokConvId) {
    body.prompt_cache_key = options.grokConvId;
  }

  const res = await fetch("https://api.x.ai/v1/responses", {
    method: "POST",
    headers,
    signal,
    body: JSON.stringify(body),
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
        type?: string;
        delta?: string;
        choices?: { delta?: { content?: string } }[];
        response?: {
          usage?: {
            input_tokens?: number;
            output_tokens?: number;
            prompt_tokens?: number;
            completion_tokens?: number;
            input_tokens_details?: { cached_tokens?: number };
            prompt_tokens_details?: { cached_tokens?: number };
          };
        };
        usage?: {
          prompt_tokens?: number;
          completion_tokens?: number;
          prompt_tokens_details?: { cached_tokens?: number };
        };
      };

      if (json.type === "response.output_text.delta" && json.delta) {
        full += json.delta;
        onDelta(full);
        continue;
      }

      // Some SDK-compatible streams still emit chat.completion.chunk shapes.
      const chatChunk = json.choices?.[0]?.delta?.content;
      if (chatChunk) {
        full += chatChunk;
        onDelta(full);
      }

      if (json.type === "response.completed") {
        usage = usageFromResponseCompleted(json, resolveModel(rec)) ?? usage;
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
            "xai",
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
