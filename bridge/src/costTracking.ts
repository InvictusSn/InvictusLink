/**
 * Cost dashboard — aggregates estimated spend from update-log.jsonl.
 *
 * Chat providers report token usage per task (locals cost $0 but tokens are
 * still counted so we can estimate what they would have cost on a cloud API).
 * Cursor agent runs don't expose token usage via the SDK, so they appear as
 * run counts only.
 */

import fs from "node:fs";
import path from "node:path";
import { UPDATE_LOG_PATH } from "./bridgeConfig.js";
import { appendUpdateLog } from "./bridgeState.js";
import { LOCAL_SAVINGS_REFERENCE, listProviderRecords, providerKind } from "./providers.js";
import { getXaiRates } from "./xaiPricing.js";

export type CostSettings = {
  monthlyLimitUsd?: number;
  dailyLimitUsd?: number;
  /** Day (YYYY-MM-DD) an alert was last emitted — avoids notification spam. */
  lastAlertDay?: string;
};

export type ProviderCostSummary = {
  label: string;
  isLocal: boolean;
  runs: number;
  promptTokens: number;
  completionTokens: number;
  costUsd: number;
  /** priced = token/cost data available; runs_only = count only (e.g. Cursor agent). */
  costTracking: "priced" | "runs_only";
};

export type UntrackedProviderSummary = {
  label: string;
  runs: number;
  /** Connected on the bridge but Link cannot read live billing/usage from the provider API. */
  connected: boolean;
};

export type CostAlert = {
  level: "warning" | "critical";
  message: string;
};

export type CostDashboard = {
  /** Bridge-wide spend this month (all paired devices / sessions). */
  todayUsd: number;
  monthUsd: number;
  monthLabel: string;
  /** Current phone/session only — useful when multiple devices share this bridge. */
  deviceTodayUsd: number;
  deviceMonthUsd: number;
  byProvider: ProviderCostSummary[];
  localRuns: number;
  estimatedSavingsUsd: number;
  /** Estimated savings from prompt caching (cached vs full input rates). */
  cacheSavingsUsd: number;
  /** Same as monthUsd — kept for older clients. */
  bridgeMonthUsd: number;
  /** Same as todayUsd — kept for older clients. */
  bridgeTodayUsd: number;
  /** Distinct pairing sessions that sent tasks this month (re-pairing counts separately). */
  deviceCount: number;
  /** Same as deviceCount — clearer name for UI. */
  pairingSessionCount: number;
  monthlyLimitUsd?: number;
  dailyLimitUsd?: number;
  alert?: CostAlert;
  /** Daily totals for the last 14 days (bridge-wide), oldest first. */
  dailyTotals: Array<{ date: string; costUsd: number }>;
  /** Providers where we only have run counts — no live cost or plan-usage API. */
  untrackedProviders: UntrackedProviderSummary[];
};

const COST_SETTINGS_PATH =
  process.env.COST_SETTINGS_PATH ??
  path.join(process.cwd(), "config", "cost-settings.json");

export function loadCostSettings(): CostSettings {
  try {
    const raw = fs.readFileSync(COST_SETTINGS_PATH, "utf-8");
    const parsed = JSON.parse(raw) as CostSettings;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

export function saveCostSettings(settings: CostSettings) {
  fs.mkdirSync(path.dirname(COST_SETTINGS_PATH), { recursive: true });
  fs.writeFileSync(COST_SETTINGS_PATH, JSON.stringify(settings, null, 2), "utf-8");
}

type LogUsage = {
  promptTokens?: number;
  cachedTokens?: number;
  completionTokens?: number;
  costUsd?: number;
};

type LogEntry = {
  timestamp?: string;
  event?: string;
  provider?: string;
  providerType?: string;
  model?: string;
  isLocal?: boolean;
  userKey?: string;
  usage?: LogUsage;
};

/** USD saved per million cached input tokens vs full input price. */
function cacheSavingsPerMillion(model?: string, providerType?: string): number {
  if (providerType === "xai" || (model && /^grok/i.test(model))) {
    const live = model ? getXaiRates(model) : undefined;
    if (live) return live.inputPerM - live.cachedInputPerM;
    // Prefer frontier rates when the log line has no model / stale cache miss.
    const frontier =
      getXaiRates("grok-4.5") ?? getXaiRates("grok-4") ?? getXaiRates("grok-3");
    if (frontier) return frontier.inputPerM - frontier.cachedInputPerM;
  }
  return LOCAL_SAVINGS_REFERENCE.input - LOCAL_SAVINGS_REFERENCE.cachedInput;
}

function dayOf(timestamp: string): string {
  const parsed = Date.parse(timestamp);
  if (!Number.isFinite(parsed)) return timestamp.slice(0, 10);
  return localCalendarDay(new Date(parsed));
}

function localCalendarDay(date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function localMonthPrefix(date = new Date()): string {
  return localCalendarDay(date).slice(0, 7);
}

function matchesRequestingDevice(
  entryUserKey: string | undefined,
  requestUserKey?: string,
): boolean {
  if (!requestUserKey || requestUserKey === "admin") return true;
  return (entryUserKey ?? "unknown") === requestUserKey;
}

/**
 * Build the dashboard. Primary numbers are bridge-wide totals; deviceMonthUsd
 * covers only the requesting phone/session.
 */
export function buildCostDashboard(requestUserKey?: string): CostDashboard {
  const settings = loadCostSettings();
  const today = localCalendarDay();
  const monthPrefix = localMonthPrefix();

  let deviceTodayUsd = 0;
  let deviceMonthUsd = 0;
  let bridgeTodayUsd = 0;
  let bridgeMonthUsd = 0;
  let localRuns = 0;
  let estimatedSavingsUsd = 0;
  let cacheSavingsUsd = 0;
  const deviceKeys = new Set<string>();
  const byProvider = new Map<string, ProviderCostSummary>();
  const untrackedProviders = new Map<string, UntrackedProviderSummary>();
  const dailyMap = new Map<string, number>();

  // Pre-seed the last 14 days so the chart has continuous data.
  for (let i = 13; i >= 0; i--) {
    const d = localCalendarDay(new Date(Date.now() - i * 86_400_000));
    dailyMap.set(d, 0);
  }

  if (fs.existsSync(UPDATE_LOG_PATH)) {
    const lines = fs.readFileSync(UPDATE_LOG_PATH, "utf-8").split(/\r?\n/);
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      let entry: LogEntry;
      try {
        entry = JSON.parse(trimmed) as LogEntry;
      } catch {
        continue;
      }
      if (entry.event !== "task_completed" || !entry.timestamp) continue;
      const day = dayOf(entry.timestamp);
      const inMonth = day.startsWith(monthPrefix);
      const usage = entry.usage;
      const cost = usage?.costUsd ?? 0;
      const isLocal = entry.isLocal === true;
      // Entries logged before provider labels existed were Cursor agent runs
      // (chat completions always carried a provider label).
      const label = entry.provider ?? "Cursor";

      const forDevice = matchesRequestingDevice(entry.userKey, requestUserKey);

      if (inMonth) {
        bridgeMonthUsd += cost;
        if (entry.userKey) deviceKeys.add(entry.userKey);
        if (day === today) bridgeTodayUsd += cost;

        if (forDevice) {
          deviceMonthUsd += cost;
          if (day === today) deviceTodayUsd += cost;
        }

        const hasPricedUsage =
          cost > 0 ||
          (usage?.promptTokens ?? 0) > 0 ||
          (usage?.completionTokens ?? 0) > 0;

        if (hasPricedUsage) {
          const summary = byProvider.get(label) ?? {
            label,
            isLocal,
            runs: 0,
            promptTokens: 0,
            completionTokens: 0,
            costUsd: 0,
            costTracking: "priced" as const,
          };
          summary.runs += 1;
          summary.promptTokens += usage?.promptTokens ?? 0;
          summary.completionTokens += usage?.completionTokens ?? 0;
          summary.costUsd += cost;
          byProvider.set(label, summary);
        } else {
          const untracked = untrackedProviders.get(label) ?? {
            label,
            runs: 0,
            connected: false,
          };
          untracked.runs += 1;
          untrackedProviders.set(label, untracked);
        }

        if (usage && hasPricedUsage) {
          const cached = Math.min(usage.cachedTokens ?? 0, usage.promptTokens ?? 0);
          const nonCached = Math.max(0, (usage.promptTokens ?? 0) - cached);
          if (isLocal) {
            localRuns += 1;
            estimatedSavingsUsd +=
              (nonCached * LOCAL_SAVINGS_REFERENCE.input +
                cached * LOCAL_SAVINGS_REFERENCE.cachedInput) /
                1_000_000 +
              ((usage.completionTokens ?? 0) * LOCAL_SAVINGS_REFERENCE.output) / 1_000_000;
          } else if (cached > 0) {
            cacheSavingsUsd +=
              (cached * cacheSavingsPerMillion(entry.model, entry.providerType)) / 1_000_000;
          }
        }
      }
      if (dailyMap.has(day)) {
        dailyMap.set(day, (dailyMap.get(day) ?? 0) + cost);
      }
    }
  }

  // Limits guard the key owner's wallet — evaluated against bridge-wide spend.
  let alert: CostAlert | undefined;
  if (settings.monthlyLimitUsd && settings.monthlyLimitUsd > 0) {
    const ratio = bridgeMonthUsd / settings.monthlyLimitUsd;
    if (ratio >= 1) {
      alert = {
        level: "critical",
        message: `Monthly limit reached — $${bridgeMonthUsd.toFixed(2)} of $${settings.monthlyLimitUsd.toFixed(2)}`,
      };
    } else if (ratio >= 0.8) {
      alert = {
        level: "warning",
        message: `Approaching monthly limit — $${bridgeMonthUsd.toFixed(2)} of $${settings.monthlyLimitUsd.toFixed(2)}`,
      };
    }
  }
  if (!alert && settings.dailyLimitUsd && settings.dailyLimitUsd > 0) {
    const ratio = bridgeTodayUsd / settings.dailyLimitUsd;
    if (ratio >= 1) {
      alert = {
        level: "critical",
        message: `Daily limit reached — $${bridgeTodayUsd.toFixed(2)} of $${settings.dailyLimitUsd.toFixed(2)}`,
      };
    } else if (ratio >= 0.8) {
      alert = {
        level: "warning",
        message: `Approaching daily limit — $${bridgeTodayUsd.toFixed(2)} of $${settings.dailyLimitUsd.toFixed(2)}`,
      };
    }
  }

  // Emit at most one alert event per day so the phone gets a notification.
  if (alert && settings.lastAlertDay !== today) {
    settings.lastAlertDay = today;
    saveCostSettings(settings);
    appendUpdateLog("cost_alert", { level: alert.level, message: alert.message });
  }

  // Include connected agent providers (e.g. Cursor) even when they have no runs yet.
  for (const provider of listProviderRecords()) {
    if (providerKind(provider.type) !== "agent") continue;
    const existing = untrackedProviders.get(provider.label);
    if (existing) {
      existing.connected = true;
      continue;
    }
    untrackedProviders.set(provider.label, {
      label: provider.label,
      runs: 0,
      connected: true,
    });
  }

  const pairingSessionCount = deviceKeys.size;

  return {
    todayUsd: bridgeTodayUsd,
    monthUsd: bridgeMonthUsd,
    monthLabel: monthPrefix,
    deviceTodayUsd,
    deviceMonthUsd,
    byProvider: [...byProvider.values()].sort((a, b) => b.costUsd - a.costUsd),
    localRuns,
    estimatedSavingsUsd,
    cacheSavingsUsd,
    bridgeMonthUsd,
    bridgeTodayUsd,
    deviceCount: Math.max(pairingSessionCount, 1),
    pairingSessionCount: Math.max(pairingSessionCount, 1),
    monthlyLimitUsd: settings.monthlyLimitUsd,
    dailyLimitUsd: settings.dailyLimitUsd,
    alert,
    dailyTotals: [...dailyMap.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, costUsd]) => ({ date, costUsd })),
    untrackedProviders: [...untrackedProviders.values()].sort((a, b) => b.runs - a.runs),
  };
}
