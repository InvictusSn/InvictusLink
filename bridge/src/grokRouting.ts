/**
 * Pick Grok 4.5 reasoning effort, search tools, and vision detail.
 * Default API effort is "high" (slow/expensive) — casual chat should use "low".
 * Cache hits don't make high-effort / search / high-detail vision cheap.
 */

export type GrokReasoningEffort = "low" | "medium" | "high";
export type GrokImageDetail = "auto" | "low" | "high";

const RESEARCH_RE =
  /\b(research|look\s*up|search|browse|find\s+out|what'?s\s+going\s+on|latest|news|price|pricing|cost|how\s+much|compare|review|opinion\s+on\s+https?|analyze\s+this\s+link)\b/i;

const COMPLEX_RE =
  /\b(debug|fix|implement|architect|design|prove|derive|algorithm|refactor|migrate|benchmark|optimize|write\s+(a\s+)?(full|complete)|step[\s-]?by[\s-]?step|deep\s+dive)\b/i;

const URL_RE = /https?:\/\/[^\s<>"')\]]+/i;

/** Careful vision — OCR, extract text, inspect UI, etc. */
const HIGH_DETAIL_IMAGE_RE =
  /\b(analyze|analys[e]?|ocr|transcribe|extract|read\s+(the\s+)?(text|screen|ui|error)|carefully|inspect|zoom|pixel|what\s+does\s+(this|it)\s+say|detail(ed)?\s+(look|analysis)|describe\s+every)\b/i;

/** Casual “look at this” — low detail is enough. */
const LOW_DETAIL_IMAGE_RE =
  /\b(lmk|let\s+me\s+know|what\s+do\s+you\s+think|thoughts|look\s+at\s+this|check\s+this\s+out|lol|haha|funny|cool|nice|wtf|omg)\b/i;

export function pickGrokReasoningEffort(prompt: string, hasImages: boolean): GrokReasoningEffort {
  const trimmed = prompt.trim();
  if (URL_RE.test(trimmed) || RESEARCH_RE.test(trimmed)) return "medium";
  if (COMPLEX_RE.test(trimmed) || trimmed.length > 600) return "high";
  if (hasImages) {
    // Casual screenshot opinions stay medium-low; careful analysis stays medium.
    if (HIGH_DETAIL_IMAGE_RE.test(trimmed)) return "medium";
    if (trimmed.length < 160 || LOW_DETAIL_IMAGE_RE.test(trimmed)) return "low";
    return "medium";
  }
  // Short / casual chat — fast mode.
  if (trimmed.length < 280) return "low";
  return "medium";
}

/** Only attach search tools when the user likely wants live lookup. */
export function shouldEnableGrokSearchTools(prompt: string): boolean {
  const trimmed = prompt.trim();
  if (URL_RE.test(trimmed)) return true;
  if (RESEARCH_RE.test(trimmed)) return true;
  // Current-events / “today” style asks.
  if (/\b(today|right\s+now|currently|this\s+week|202[6-9])\b/i.test(trimmed)) return true;
  return false;
}

/**
 * Vision token cost scales with detail. Prefer low/auto for casual screenshots;
 * high only when the user clearly wants careful analysis / OCR.
 */
export function pickGrokImageDetail(prompt: string): GrokImageDetail {
  const trimmed = prompt.trim();
  if (HIGH_DETAIL_IMAGE_RE.test(trimmed)) return "high";
  if (trimmed.length < 120 || LOW_DETAIL_IMAGE_RE.test(trimmed)) return "low";
  return "auto";
}
