/**
 * Lightweight prompt routing for Auto mode — keyword scoring only.
 *
 * Local models (Ollama / LM Studio / custom) are preferred for quick tasks,
 * code questions, and privacy-sensitive prompts when a local server is up.
 * Availability is probed with a short timeout and cached so routing stays fast.
 */

import { PROVIDER_META, type ProviderRecord } from "./providers.js";
import { getActiveProvider, listProviderRecords } from "./providers.js";

export type RouteCategory =
  | "coding"
  | "research"
  | "local-quick"
  | "local-code"
  | "local-private"
  | "fallback";

export type RouteDecision = {
  provider: ProviderRecord;
  reason: string;
  category: RouteCategory;
};

/** One provider + focused sub-prompt when Auto mode fans out to multiple AIs. */
export type RoutePlanItem = RouteDecision & {
  subPrompt: string;
};

type PatternRule = { pattern: RegExp; weight: number };

/** Agentic work that needs the Cursor agent's file/tool access on the PC. */
const AGENTIC_RULES: PatternRule[] = [
  { pattern: /\b(implement|refactor|debug|fix\s+(the\s+)?bug)\b/i, weight: 4 },
  { pattern: /\b(compile|build|deploy|gradle|npm|git|pull\s+request|pr\b)\b/i, weight: 3 },
  { pattern: /\b(agent|tool\s*use|cursor|composer|file\s+edit)\b/i, weight: 4 },
  {
    pattern: /\b(create|add|update|delete|modify|remove|hide|disable)\b.+\b(file|module|component|api|endpoint|ui|screen|button|popup|notification|snackbar|toast|banner|strip)\b/i,
    weight: 4,
  },
  {
    pattern: /\b(remove|hide|disable)\b.+\b(pop[\s-]?up|notification|snackbar|toast|strip|banner)\b/i,
    weight: 4,
  },
  { pattern: /\b(my|the|our)\s+(repo|project|codebase|app|workspace)\b/i, weight: 3 },
  { pattern: /\b(error:|stack\s*trace|exception|null\s*pointer|lint)\b/i, weight: 2 },
];

/** Local files, folders, and exports on the user's PC — needs the Cursor agent. */
const FILE_ACCESS_RULES: PatternRule[] = [
  { pattern: /\b(folder|directory|file|files|path|exports?|image|screenshot|attachment)\b/i, weight: 3 },
  {
    pattern: /\b(what'?s in|list|contents of|show me what|read the|open the|check the|look in|look at|review)\b/i,
    weight: 4,
  },
  { pattern: /\b(grokresearch|desktop|documents|downloads|linkimages|invictusapps)\b/i, weight: 3 },
  { pattern: /\b(on my (pc|computer|machine|desktop))\b/i, weight: 3 },
  { pattern: /\b[\w-]+\.(png|jpe?g|gif|webp|bmp|pdf|md|txt|json|csv)\b/i, weight: 3 },
  { pattern: /\b(just added|added to|saved to|in the)\b/i, weight: 2 },
];

/** Code questions a capable local model answers well — no PC access needed. */
const CODE_QUESTION_RULES: PatternRule[] = [
  { pattern: /\b(write|show|give)\s+(me\s+)?(a\s+)?(function|snippet|script|regex|query|example)\b/i, weight: 4 },
  { pattern: /\b(explain|what\s+does)\b.+\b(code|function|regex|error|snippet)\b/i, weight: 4 },
  { pattern: /\b(syntax|one[\s-]?liner|pseudo\s*code)\b/i, weight: 3 },
  { pattern: /\b(typescript|kotlin|python|rust|javascript|sql|bash|powershell)\b/i, weight: 2 },
];

const RESEARCH_RULES: PatternRule[] = [
  { pattern: /\b(research|investigate|explore|survey|literature)\b/i, weight: 4 },
  { pattern: /\b(how\s+much|price|pricing|cost|costs|fee|fees|subscription)\b/i, weight: 4 },
  { pattern: /\b(current|latest|today|recent|news|announced|release[d]?)\b/i, weight: 3 },
  { pattern: /\b(physics|simulation|quantum|thermodynamic|relativity)\b/i, weight: 4 },
  { pattern: /\b(analyze|analysis|compare|contrast|evaluate)\b/i, weight: 2 },
  { pattern: /\b(explain|why\s+does|how\s+does|what\s+is|deep\s+dive)\b/i, weight: 2 },
  { pattern: /\b(pros\s+and\s+cons|trade[\s-]?offs?|implications)\b/i, weight: 2 },
];

/** Fast, self-contained tasks where a local model is quick, free, and private. */
const QUICK_TASK_RULES: PatternRule[] = [
  { pattern: /\b(summariz|tl;?dr|rewrite|rephrase|reword|translate|proofread|grammar)\b/i, weight: 4 },
  { pattern: /\b(draft|outline|bullet\s+points|list\s+of|brainstorm)\b/i, weight: 3 },
  { pattern: /\b(quick|simple|briefly|short\s+answer)\b/i, weight: 2 },
];

/** Prompts the user likely wants to keep off cloud APIs entirely. */
const PRIVACY_RULES: PatternRule[] = [
  { pattern: /\b(private|personal|confidential|secret|sensitive)\b/i, weight: 4 },
  { pattern: /\b(journal|diary|feelings|therapy|medical|health|finances)\b/i, weight: 4 },
  { pattern: /\b(password|credentials|api\s+key|token)\b/i, weight: 3 },
];

function scoreRules(prompt: string, rules: PatternRule[]): number {
  let score = 0;
  for (const rule of rules) {
    if (rule.pattern.test(prompt)) score += rule.weight;
  }
  return score;
}

function findByType(
  providers: ProviderRecord[],
  type: ProviderRecord["type"],
): ProviderRecord | undefined {
  return providers.find((p) => p.type === type);
}

function firstChatFallback(providers: ProviderRecord[]): ProviderRecord | undefined {
  return providers.find((p) => p.type !== "cursor");
}

function localChatProviders(providers: ProviderRecord[]): ProviderRecord[] {
  return providers.filter((p) => p.type !== "cursor" && PROVIDER_META[p.type]?.isLocal);
}

// ---------------------------------------------------------------------------
// Local availability probe — cached so Auto mode never blocks on a dead server.

const AVAILABILITY_TTL_MS = 60_000;
const AVAILABILITY_TIMEOUT_MS = 1_200;
const availabilityCache = new Map<string, { ok: boolean; checkedAt: number }>();

function localBaseUrl(rec: ProviderRecord): string {
  const meta = PROVIDER_META[rec.type];
  const base = (rec.baseUrl || meta?.defaultBaseUrl || "").replace(/\/$/, "");
  if (!base) return "";
  return base.endsWith("/v1") ? base : `${base}/v1`;
}

async function isLocalProviderAvailable(rec: ProviderRecord): Promise<boolean> {
  const cached = availabilityCache.get(rec.id);
  if (cached && Date.now() - cached.checkedAt < AVAILABILITY_TTL_MS) {
    return cached.ok;
  }
  const base = localBaseUrl(rec);
  if (!base) {
    availabilityCache.set(rec.id, { ok: false, checkedAt: Date.now() });
    return false;
  }
  let ok = false;
  const controller = new AbortController();
  const handle = setTimeout(() => controller.abort(), AVAILABILITY_TIMEOUT_MS);
  try {
    const res = await fetch(`${base}/models`, { signal: controller.signal });
    ok = res.ok;
  } catch {
    ok = false;
  } finally {
    clearTimeout(handle);
  }
  availabilityCache.set(rec.id, { ok, checkedAt: Date.now() });
  return ok;
}

/** First reachable local chat provider, or undefined. */
async function pickAvailableLocal(
  providers: ProviderRecord[],
): Promise<ProviderRecord | undefined> {
  for (const rec of localChatProviders(providers)) {
    if (await isLocalProviderAvailable(rec)) return rec;
  }
  return undefined;
}

// ---------------------------------------------------------------------------

/**
 * Split compound prompts ("first X, then Y") into segments Auto can route separately.
 */
export function splitCompoundPrompt(prompt: string): string[] {
  const trimmed = prompt.trim();
  if (!trimmed) return [];

  const thenParts = trimmed.split(/\s*,?\s+then\s+/i);
  if (thenParts.length > 1) {
    return thenParts
      .map((part, index) => {
        let segment = part.trim();
        if (index === 0) {
          segment = segment.replace(/^.*?\bfirst,?\s+/i, "").trim() || part.trim();
        }
        return segment;
      })
      .filter(Boolean);
  }

  const alsoParts = trimmed.split(/\s*,?\s+also\s+/i);
  if (alsoParts.length > 1) {
    return alsoParts.map((p) => p.trim()).filter(Boolean);
  }

  return [trimmed];
}

/**
 * Plan every provider Auto should use for this prompt.
 * Compound prompts can fan out to Cursor (files) + Grok (research) in parallel.
 */
export async function planPromptRoutes(
  prompt: string,
  providers: ProviderRecord[] = listProviderRecords(),
): Promise<RoutePlanItem[]> {
  if (providers.length === 0) return [];

  const segments = splitCompoundPrompt(prompt);
  const routes: RoutePlanItem[] = [];
  const byProviderId = new Map<string, RoutePlanItem>();

  for (const segment of segments) {
    const decision = await routePrompt(segment, providers);
    if (!decision) continue;

    const existing = byProviderId.get(decision.provider.id);
    if (existing) {
      existing.subPrompt = `${existing.subPrompt}\n\nAlso: ${segment}`;
      existing.reason = `${existing.reason}; ${decision.reason}`;
      continue;
    }

    const item: RoutePlanItem = { ...decision, subPrompt: segment };
    byProviderId.set(decision.provider.id, item);
    routes.push(item);
  }

  if (routes.length > 0) return routes;

  const single = await routePrompt(prompt, providers);
  return single ? [{ ...single, subPrompt: prompt }] : [];
}

// ---------------------------------------------------------------------------

/**
 * Pick the best connected provider for a prompt.
 *
 * Priority: privacy → local · files/folders → Cursor · agentic → Cursor ·
 * research → Grok · code questions / quick tasks → local · else fallback chat.
 */
export async function routePrompt(
  prompt: string,
  providers: ProviderRecord[] = listProviderRecords(),
): Promise<RouteDecision | null> {
  if (providers.length === 0) return null;

  const agenticScore = scoreRules(prompt, AGENTIC_RULES);
  const fileAccessScore = scoreRules(prompt, FILE_ACCESS_RULES);
  const codeQuestionScore = scoreRules(prompt, CODE_QUESTION_RULES);
  const researchScore = scoreRules(prompt, RESEARCH_RULES);
  const quickScore = scoreRules(prompt, QUICK_TASK_RULES);
  const privacyScore = scoreRules(prompt, PRIVACY_RULES);

  const cursor = findByType(providers, "cursor");
  const xai = findByType(providers, "xai");
  const hasLocalConfigured = localChatProviders(providers).length > 0;

  // Privacy first — keep sensitive prompts off cloud APIs when possible.
  if (privacyScore >= 4 && hasLocalConfigured) {
    const local = await pickAvailableLocal(providers);
    if (local) {
      return {
        provider: local,
        category: "local-private",
        reason: `Private topic → ${local.label} (stays on your PC)`,
      };
    }
  }

  // Local files / folders on the PC need the Cursor agent — before research routing.
  if (fileAccessScore >= 3 && cursor) {
    return {
      provider: cursor,
      category: "coding",
      reason: "Files / folders on your PC → Cursor",
    };
  }

  // Agentic work needs the Cursor agent's file and tool access.
  if (agenticScore > 0 && agenticScore >= researchScore && cursor) {
    return {
      provider: cursor,
      category: "coding",
      reason: "Coding / agentic task → Cursor",
    };
  }

  // Research and current-events prompts need web search — cloud only.
  if (researchScore > 0 && researchScore >= codeQuestionScore && xai) {
    return {
      provider: xai,
      category: "research",
      reason: "Research & reasoning → Grok",
    };
  }

  // Code questions without PC work — a local model is fast, free, private.
  if (codeQuestionScore > 0 && hasLocalConfigured) {
    const local = await pickAvailableLocal(providers);
    if (local) {
      return {
        provider: local,
        category: "local-code",
        reason: `Code question → ${local.label} (local, free)`,
      };
    }
  }

  // Code question but no local available — Cursor still handles it well.
  if (codeQuestionScore > 0 && cursor) {
    return {
      provider: cursor,
      category: "coding",
      reason: "Coding task → Cursor",
    };
  }

  // Quick self-contained tasks → local when reachable.
  if (quickScore > 0 && hasLocalConfigured) {
    const local = await pickAvailableLocal(providers);
    if (local) {
      return {
        provider: local,
        category: "local-quick",
        reason: `Quick task → ${local.label} (local, free)`,
      };
    }
  }

  const active = getActiveProvider() ?? providers[0];
  if (!active) return null;

  const fallback = firstChatFallback(providers) ?? active;
  return {
    provider: fallback,
    category: "fallback",
    reason: `General prompt → ${fallback.label}`,
  };
}
