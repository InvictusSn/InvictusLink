/**
 * Live xAI pricing — exact per-model rates from GET /v1/language-models.
 *
 * The endpoint returns prices in USD cents per 100 million tokens
 * (prompt_text_token_price, cached_prompt_text_token_price,
 * completion_text_token_price). Converted here to USD per million tokens,
 * cached to disk, and refreshed at most once per day. When live pricing is
 * unavailable the static estimate table in providers.ts is used instead —
 * so Grok costs are exact whenever the API is reachable.
 */

import fs from "node:fs";
import path from "node:path";

export type XaiModelRates = {
  inputPerM: number;
  cachedInputPerM: number;
  outputPerM: number;
};

type PricingCache = {
  fetchedAt: number;
  /** Model id and each alias map to the same rates. */
  rates: Record<string, XaiModelRates>;
  /** Newest flagship Grok chat model from the live catalog (e.g. grok-4.5). */
  frontierModelId?: string;
};

const PRICING_CACHE_PATH =
  process.env.XAI_PRICING_PATH ??
  path.join(process.cwd(), "config", "xai-pricing.json");

const REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000;
const FETCH_TIMEOUT_MS = 6000;

let memoryCache: PricingCache | undefined;
let refreshInFlight = false;

function loadFromDisk(): PricingCache | undefined {
  try {
    const raw = fs.readFileSync(PRICING_CACHE_PATH, "utf-8");
    const parsed = JSON.parse(raw) as PricingCache;
    if (parsed && typeof parsed.fetchedAt === "number" && parsed.rates) return parsed;
  } catch {
    // No cache yet.
  }
  return undefined;
}

function saveToDisk(cache: PricingCache) {
  try {
    fs.mkdirSync(path.dirname(PRICING_CACHE_PATH), { recursive: true });
    fs.writeFileSync(PRICING_CACHE_PATH, JSON.stringify(cache, null, 2), "utf-8");
  } catch {
    // Pricing cache is best-effort.
  }
}

/** cents per 100M tokens → USD per 1M tokens. */
function centsPer100MToUsdPerM(value: number | null | undefined): number {
  if (!value || !Number.isFinite(value) || value < 0) return 0;
  return value / 100 / 100;
}

/** Compare grok-X.Y version ids (higher = newer). grok-4.20 → 4.2.0, grok-4.5 → 4.5.0. */
function grokVersionScore(id: string): number {
  const match = id.match(/^grok-(\d+)\.(\d+)$/);
  if (!match) return 0;
  const major = Number(match[1]);
  const minorPart = match[2];
  let minor: number;
  if (minorPart.length >= 2) {
    minor = Number(minorPart[0]) + Number(minorPart.slice(1)) / Math.pow(10, minorPart.length - 1);
  } else {
    minor = Number(minorPart);
  }
  return major * 1000 + minor * 10;
}

function compareGrokVersion(a: string, b: string): number {
  return grokVersionScore(a) - grokVersionScore(b);
}

/** Pick the newest stable flagship chat model (grok-4.5 beats grok-4.3). */
export function pickFrontierGrokModel(ids: string[]): string | undefined {
  return ids
    .filter((id) => /^grok-\d+\.\d+$/.test(id))
    .sort(compareGrokVersion)
    .at(-1);
}

/** xAI marks the production frontier with grok-build-latest (currently grok-4.5). */
export function pickFrontierGrokModelFromCatalog(
  models: Array<{ id?: string; aliases?: string[] }>,
): string | undefined {
  for (const model of models) {
    if (model.id && model.aliases?.includes("grok-build-latest")) {
      return model.id.match(/^grok-\d+\.\d+/)?.[0] ?? model.id;
    }
  }
  // Sometimes the catalog lists grok-build-latest as the id with version aliases.
  for (const model of models) {
    if (model.id !== "grok-build-latest") continue;
    const fromAlias = (model.aliases ?? [])
      .map((alias) => alias.match(/^grok-\d+\.\d+$/)?.[0])
      .filter((id): id is string => Boolean(id));
    const picked = pickFrontierGrokModel(fromAlias);
    if (picked) return picked;
  }
  for (const model of models) {
    if (model.id && model.aliases?.includes("grok-4.5-latest")) {
      return model.id.match(/^grok-\d+\.\d+/)?.[0] ?? "grok-4.5";
    }
  }
  return pickFrontierGrokModel(
    models.map((model) => model.id).filter((id): id is string => Boolean(id)),
  );
}

/** Best available frontier model — from live cache or stale pricing keys. */
export function getXaiFrontierModel(): string | undefined {
  if (!memoryCache) memoryCache = loadFromDisk();
  if (memoryCache?.frontierModelId) return memoryCache.frontierModelId;
  if (memoryCache?.rates) {
    return pickFrontierGrokModel(Object.keys(memoryCache.rates));
  }
  return undefined;
}

/** Exact rates for a model id/alias, when live pricing has been fetched. */
export function getXaiRates(model: string): XaiModelRates | undefined {
  if (!memoryCache) memoryCache = loadFromDisk();
  if (!memoryCache) return undefined;
  return memoryCache.rates[model.toLowerCase()];
}

/**
 * Refresh pricing in the background (fire-and-forget from the request path —
 * never blocks or fails a user prompt).
 */
export function refreshXaiPricing(apiKey: string | undefined, force = false): void {
  if (!apiKey || refreshInFlight) return;
  if (!memoryCache) memoryCache = loadFromDisk();
  const cacheStale =
    !memoryCache ||
    force ||
    !memoryCache.frontierModelId ||
    Date.now() - memoryCache.fetchedAt >= REFRESH_INTERVAL_MS;
  if (!cacheStale) return;

  refreshInFlight = true;
  void (async () => {
    const controller = new AbortController();
    const handle = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
    try {
      const res = await fetch("https://api.x.ai/v1/language-models", {
        headers: { Authorization: `Bearer ${apiKey}` },
        signal: controller.signal,
      });
      if (!res.ok) return;
      const json = (await res.json()) as {
        models?: Array<{
          id?: string;
          aliases?: string[];
          prompt_text_token_price?: number;
          cached_prompt_text_token_price?: number;
          completion_text_token_price?: number;
        }>;
      };
      if (!Array.isArray(json.models) || json.models.length === 0) return;
      const rates: Record<string, XaiModelRates> = {};
      for (const model of json.models) {
        const entry: XaiModelRates = {
          inputPerM: centsPer100MToUsdPerM(model.prompt_text_token_price),
          cachedInputPerM: centsPer100MToUsdPerM(model.cached_prompt_text_token_price),
          outputPerM: centsPer100MToUsdPerM(model.completion_text_token_price),
        };
        if (entry.inputPerM <= 0 && entry.outputPerM <= 0) continue;
        for (const key of [model.id, ...(model.aliases ?? [])]) {
          if (key) rates[key.toLowerCase()] = entry;
        }
      }
      if (Object.keys(rates).length === 0) return;
      const frontierModelId = pickFrontierGrokModelFromCatalog(json.models);
      memoryCache = { fetchedAt: Date.now(), rates, frontierModelId };
      saveToDisk(memoryCache);
    } catch {
      // Keep the previous cache / static fallback.
    } finally {
      clearTimeout(handle);
      refreshInFlight = false;
    }
  })();
}
