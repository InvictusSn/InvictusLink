import fs from "node:fs";
import { z } from "zod";
import { LOCK_PATH, UPDATE_LOG_PATH } from "./bridgeConfig.js";

export type ProjectConfig = {
  id: string;
  name: string;
  cwd: string; // absolute path on this PC
};

export type LinkSession = {
  id: string;
  name: string;
  createdAt: number;
  /** When set, session folder is this path (legacy / workspace-root sessions). */
  cwd?: string;
};

export type TaskStatus = "queued" | "awaiting_approval" | "running" | "completed" | "error";

export type TaskRecord = {
  taskId: string;
  createdAt: number;
  updatedAt: number;
  status: TaskStatus;
  prompt: string;
  projectId: string;
  outputStyle?: "short" | "detailed";
  output?: string;
  error?: string;
  requiresApproval?: boolean;
  approvedAt?: number;
  providerLabel?: string;
  /** Set when Auto mode picks a provider for this task. */
  routingNote?: string;
  /** Token usage for the latest xAI exchange (manual Grok mode). */
  usage?: import("./providers.js").TokenUsage;
  attachments?: string[];
  /** Anonymous per-device key (hash of the caller's bearer token) for individual spend tracking. */
  userKey?: string;
};

export type DigestResponse = {
  date: string;
  totalRuns: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  timeSavedMinutes: number;
};

export type SessionRecord = {
  token: string;
  createdAt: number;
  expiresAt: number;
};

export type BuildStatus = "idle" | "running" | "completed" | "error";

export type BuildJobState = {
  status: BuildStatus;
  startedAt?: number;
  updatedAt: number;
  endedAt?: number;
  lastOutput: string;
  error?: string;
};

export const TaskCreateSchema = z.object({
  prompt: z.string().min(1).max(20_000),
  projectId: z.string().min(1).max(200).optional(),
  // Optional: "short" | "detailed" — controls recap verbosity after real coding work, not casual chat.
  outputStyle: z.enum(["short", "detailed"]).optional(),
  // Relative paths (inside the project) of files uploaded via /api/attachments.
  attachments: z.array(z.string().min(1).max(500)).max(10).optional(),
});

export const LoginSchema = z.object({
  bridgeToken: z.string().min(1),
});

export const CreateLinkSessionSchema = z.object({
  name: z.string().min(1).max(120).optional(),
});

export const RenameLinkSessionSchema = z.object({
  name: z.string().min(1).max(120),
});

export const AddProviderSchema = z.object({
  type: z.enum([
    "cursor",
    "openai",
    "anthropic",
    "xai",
    "google",
    "ollama",
    "lmstudio",
    "custom",
  ]),
  label: z.string().min(1).max(60).optional(),
  apiKey: z.string().min(1).max(500).optional(),
  baseUrl: z.string().min(1).max(300).optional(),
  model: z.string().min(1).max(120).optional(),
});

export const RoutingModeSchema = z.object({
  mode: z.enum(["manual", "auto"]),
});

export const projects: ProjectConfig[] = [];
export const projectById = new Map<string, ProjectConfig>();

export const tasks = new Map<string, TaskRecord>();
export let isWorkerBusy = false;
export const queue: string[] = [];
export let lockOwnedByThisProcess = false;
export const sessionByToken = new Map<string, SessionRecord>();

export const buildJob: BuildJobState = {
  status: "idle",
  updatedAt: now(),
  lastOutput: "",
};

export function now() {
  return Date.now();
}

export function setWorkerBusy(value: boolean) {
  isWorkerBusy = value;
}

export function setLockOwnedByThisProcess(value: boolean) {
  lockOwnedByThisProcess = value;
}

export function appendUpdateLog(
  event: string,
  details: Record<string, unknown> = {},
) {
  const entry = {
    timestamp: new Date().toISOString(),
    event,
    ...details,
  };
  try {
    fs.appendFileSync(UPDATE_LOG_PATH, `${JSON.stringify(entry)}\n`, "utf-8");
  } catch {
    // Logging must never break bridge runtime.
  }
}

export function pidExists(pid: number): boolean {
  if (!Number.isFinite(pid) || pid <= 0) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

export function acquireBridgeLock() {
  const thisPid = process.pid;

  try {
    const existingRaw = fs.readFileSync(LOCK_PATH, "utf-8");
    const existing = JSON.parse(existingRaw) as { pid?: number };
    if (existing.pid && pidExists(existing.pid) && existing.pid !== thisPid) {
      throw new Error(
        `Another bridge process is already running (pid=${existing.pid}).`,
      );
    }
    // stale lock or same pid; overwrite.
  } catch (err) {
    if ((err as NodeJS.ErrnoException)?.code !== "ENOENT") {
      // parse errors or other read errors shouldn't block startup permanently;
      // overwrite with a fresh lock below.
    }
  }

  fs.writeFileSync(
    LOCK_PATH,
    JSON.stringify({ pid: thisPid, startedAt: new Date().toISOString() }, null, 2),
    "utf-8",
  );
  lockOwnedByThisProcess = true;
}

export function releaseBridgeLock() {
  if (!lockOwnedByThisProcess) return;
  try {
    const raw = fs.readFileSync(LOCK_PATH, "utf-8");
    const parsed = JSON.parse(raw) as { pid?: number };
    if (parsed.pid === process.pid) {
      fs.unlinkSync(LOCK_PATH);
    }
  } catch {
    // no-op
  }
  lockOwnedByThisProcess = false;
}

export function hasInFlightTask(): boolean {
  for (const t of tasks.values()) {
    if (t.status === "queued" || t.status === "awaiting_approval" || t.status === "running") return true;
  }
  return false;
}

export function appendBuildLog(chunk: string) {
  if (!chunk) return;
  const next = `${buildJob.lastOutput}${chunk}`;
  // Keep last ~16KB of logs for phone polling.
  buildJob.lastOutput = next.slice(-16 * 1024);
}

export function buildTodayDigest(): DigestResponse {
  const nowDate = new Date();
  const yyyy = nowDate.getFullYear();
  const mm = String(nowDate.getMonth() + 1).padStart(2, "0");
  const dd = String(nowDate.getDate()).padStart(2, "0");
  const dayPrefix = `${yyyy}-${mm}-${dd}`;

  let totalRuns = 0;
  let successCount = 0;
  let failureCount = 0;

  if (fs.existsSync(UPDATE_LOG_PATH)) {
    const lines = fs
      .readFileSync(UPDATE_LOG_PATH, "utf-8")
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter(Boolean);
    for (const line of lines) {
      try {
        const entry = JSON.parse(line) as { timestamp?: string; event?: string };
        if (!entry.timestamp?.startsWith(dayPrefix)) continue;
        if (entry.event === "task_completed") {
          totalRuns += 1;
          successCount += 1;
        } else if (entry.event === "task_error") {
          totalRuns += 1;
          failureCount += 1;
        }
      } catch {
        // ignore malformed line
      }
    }
  }

  const successRate = totalRuns > 0 ? Math.round((successCount / totalRuns) * 100) : 0;
  // Conservative estimate: 12 minutes saved per successful run.
  const timeSavedMinutes = successCount * 12;
  return {
    date: dayPrefix,
    totalRuns,
    successCount,
    failureCount,
    successRate,
    timeSavedMinutes,
  };
}
