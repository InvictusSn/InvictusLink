import "dotenv/config";

import express from "express";
import cors from "cors";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { spawn } from "node:child_process";
import { z } from "zod";
import { Agent, CursorAgentError } from "@cursor/sdk";
import qrcode from "qrcode";

type ProjectConfig = {
  id: string;
  name: string;
  cwd: string; // absolute path on this PC
};

type LinkSession = {
  id: string;
  name: string;
  createdAt: number;
  /** When set, session folder is this path (legacy / workspace-root sessions). */
  cwd?: string;
};

type TaskStatus = "queued" | "awaiting_approval" | "running" | "completed" | "error";

type TaskRecord = {
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
};

type DigestResponse = {
  date: string;
  totalRuns: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  timeSavedMinutes: number;
};

const PORT = Number(process.env.PORT ?? 3001);
const BRIDGE_TOKEN = process.env.BRIDGE_TOKEN ?? "";
const CURSOR_API_KEY = process.env.CURSOR_API_KEY ?? "";
const CURSOR_MODEL_ID = process.env.CURSOR_MODEL_ID ?? "composer-2.5";
/** Max minutes a single agent run may take before the bridge frees itself for new prompts. */
const TASK_TIMEOUT_MINUTES = Math.max(
  1,
  Number(process.env.TASK_TIMEOUT_MINUTES ?? 10) || 10,
);
const TASK_TIMEOUT_MS = TASK_TIMEOUT_MINUTES * 60_000;

const PROJECTS_PATH =
  process.env.PROJECTS_PATH ??
  path.join(process.cwd(), "config", "projects.json");
const LOCK_PATH =
  process.env.BRIDGE_LOCK_PATH ??
  path.join(process.cwd(), ".bridge.lock.json");
const SESSIONS_PATH =
  process.env.BRIDGE_SESSIONS_PATH ??
  path.join(process.cwd(), ".bridge.sessions.json");
const LINK_SESSIONS_PATH =
  process.env.LINK_SESSIONS_PATH ??
  path.join(process.cwd(), "config", "link-sessions.json");
const UPDATE_LOG_PATH =
  process.env.BRIDGE_UPDATE_LOG_PATH ??
  path.join(process.cwd(), "update-log.jsonl");
const PROJECT_ROOT = path.resolve(process.cwd(), "..");
const BUILD_SCRIPT_PATH = path.join(PROJECT_ROOT, "scripts", "build-and-publish-apk.ps1");
const BACKUP_SCRIPT_PATH = path.join(PROJECT_ROOT, "scripts", "backup-app-version.ps1");
/** Sessions stay valid until the user disconnects or reinstalls after an app update. */
const PERMANENT_SESSION_EXPIRES_AT = 9_000_000_000_000;
const DOWNLOAD_DIR = path.join(process.cwd(), "public", "download");
const LATEST_JSON_PATH = path.join(DOWNLOAD_DIR, "latest.json");
const APK_FILENAME = "InvictusLink.apk";
const APK_PATH = path.join(DOWNLOAD_DIR, APK_FILENAME);

const app = express();
app.use(cors());
app.use(express.json({ limit: "200kb" }));

// Serve update artifacts with the caller's reachable host (WireGuard, LAN, etc.).
app.get("/download/latest.json", (req, res) => {
  try {
    if (!fs.existsSync(LATEST_JSON_PATH)) {
      res.status(404).json({ error: "update manifest not found" });
      return;
    }
    const manifest = JSON.parse(
      fs.readFileSync(LATEST_JSON_PATH, "utf-8").replace(/^\uFEFF/, ""),
    ) as {
      versionCode?: number;
      versionName?: string;
      apkUrl?: string;
    };
    const baseUrl = getRequestBaseUrl(req);
    manifest.apkUrl = `${baseUrl}/download/${APK_FILENAME}`;
    res.json(manifest);
  } catch (err) {
    res.status(500).json({
      error: err instanceof Error ? err.message : String(err),
    });
  }
});

app.get("/download/InvictusLink.apk", (req, res) => {
  if (!fs.existsSync(APK_PATH)) {
    res.status(404).json({ error: "apk not found" });
    return;
  }
  res.setHeader("Content-Type", "application/vnd.android.package-archive");
  res.setHeader(
    "Content-Disposition",
    `attachment; filename="${APK_FILENAME}"`,
  );
  res.sendFile(APK_PATH);
});

app.use(express.static(path.join(process.cwd(), "public")));

function getDefaultPublicUrl() {
  const fromEnv = process.env.PUBLIC_URL;
  if (fromEnv && fromEnv.trim()) return fromEnv.trim();

  const nets = os.networkInterfaces();
  let fallback: string | undefined;
  for (const infos of Object.values(nets)) {
    for (const info of infos || []) {
      if (!info) continue;
      if (info.family !== "IPv4") continue;
      if (info.internal) continue;
      if (info.address.startsWith("169.254.")) continue;
      // Prefer Invictus Networks WireGuard address when present.
      if (info.address.startsWith("10.66.66.")) {
        return `http://${info.address}:${PORT}`;
      }
      fallback ??= `http://${info.address}:${PORT}`;
    }
  }
  return fallback ?? `http://localhost:${PORT}`;
}

function getRequestBaseUrl(req: express.Request): string {
  const host = req.get("host");
  if (!host) return getDefaultPublicUrl().replace(/\/$/, "");
  const proto =
    req.protocol === "https" || req.get("x-forwarded-proto") === "https"
      ? "https"
      : "http";
  return `${proto}://${host}`;
}

const DEFAULT_QR_URL = getDefaultPublicUrl();

const projects: ProjectConfig[] = [];
const projectById = new Map<string, ProjectConfig>();

function getWorkspaceRoot(): string {
  const fromEnv = process.env.WORKSPACE_ROOT?.trim();
  if (fromEnv) return path.resolve(fromEnv);

  const raw = fs.readFileSync(PROJECTS_PATH, "utf-8");
  const parsed = JSON.parse(raw) as
    | { workspaceRoot?: string; projects?: ProjectConfig[] }
    | ProjectConfig[];
  if (!Array.isArray(parsed) && parsed.workspaceRoot) {
    return path.resolve(parsed.workspaceRoot);
  }
  const list = Array.isArray(parsed) ? parsed : parsed.projects ?? [];
  if (list[0]?.cwd) return path.resolve(list[0].cwd);
  throw new Error("No workspace root configured in projects.json");
}

function saveLinkSessions(sessions: LinkSession[]) {
  fs.mkdirSync(path.dirname(LINK_SESSIONS_PATH), { recursive: true });
  fs.writeFileSync(LINK_SESSIONS_PATH, JSON.stringify(sessions, null, 2));
}

function migrateLegacyLinkSessions(): LinkSession[] {
  const raw = fs.readFileSync(PROJECTS_PATH, "utf-8");
  const parsed = JSON.parse(raw) as
    | { projects?: ProjectConfig[] }
    | ProjectConfig[];
  const list = Array.isArray(parsed) ? parsed : parsed.projects ?? [];
  const workspaceRoot = getWorkspaceRoot();
  const sessions: LinkSession[] = list.map((p) => ({
    id: p.id,
    name: p.name,
    createdAt: Date.now(),
    cwd: path.resolve(p.cwd),
  }));
  if (sessions.length === 0) {
    fs.mkdirSync(workspaceRoot, { recursive: true });
    sessions.push({
      id: "default",
      name: "Session 1",
      createdAt: Date.now(),
      cwd: workspaceRoot,
    });
  }
  saveLinkSessions(sessions);
  return sessions;
}

function loadLinkSessions(): LinkSession[] {
  if (!fs.existsSync(LINK_SESSIONS_PATH)) {
    return migrateLegacyLinkSessions();
  }
  const parsed = JSON.parse(
    fs.readFileSync(LINK_SESSIONS_PATH, "utf-8"),
  ) as LinkSession[] | { sessions?: LinkSession[] };
  const list = Array.isArray(parsed) ? parsed : parsed.sessions ?? [];
  if (list.length === 0) return migrateLegacyLinkSessions();
  return list;
}

function resolveSessionCwd(session: LinkSession, workspaceRoot: string): string {
  if (session.cwd) return path.resolve(session.cwd);
  return path.join(workspaceRoot, session.id);
}

function isPathInsideRoot(child: string, root: string): boolean {
  const relative = path.relative(path.resolve(root), path.resolve(child));
  return relative !== ".." && !relative.startsWith(`..${path.sep}`);
}

function reloadProjectRegistry() {
  const workspaceRoot = getWorkspaceRoot();
  const sessions = loadLinkSessions();
  projects.length = 0;
  projectById.clear();
  for (const session of sessions) {
    const cwd = resolveSessionCwd(session, workspaceRoot);
    const project: ProjectConfig = {
      id: session.id,
      name: session.name,
      cwd,
    };
    projects.push(project);
    projectById.set(session.id, project);
  }
}

reloadProjectRegistry();

const TaskCreateSchema = z.object({
  prompt: z.string().min(1).max(20_000),
  projectId: z.string().min(1).max(200).optional(),
  // Optional extra instruction to keep the agent focused on “summarize the work”.
  // This is intentionally limited so the phone app controls verbosity, not capability.
  outputStyle: z.enum(["short", "detailed"]).optional(),
});
const LoginSchema = z.object({
  bridgeToken: z.string().min(1),
});
const CreateLinkSessionSchema = z.object({
  name: z.string().min(1).max(120).optional(),
});

const tasks = new Map<string, TaskRecord>();
let isWorkerBusy = false;
const queue: string[] = [];
let lockOwnedByThisProcess = false;
type SessionRecord = {
  token: string;
  createdAt: number;
  expiresAt: number;
};
const sessionByToken = new Map<string, SessionRecord>();
type BuildStatus = "idle" | "running" | "completed" | "error";
type BuildJobState = {
  status: BuildStatus;
  startedAt?: number;
  updatedAt: number;
  endedAt?: number;
  lastOutput: string;
  error?: string;
};
const buildJob: BuildJobState = {
  status: "idle",
  updatedAt: now(),
  lastOutput: "",
};

function appendUpdateLog(
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

function pidExists(pid: number): boolean {
  if (!Number.isFinite(pid) || pid <= 0) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function acquireBridgeLock() {
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

function releaseBridgeLock() {
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

function hasInFlightTask(): boolean {
  for (const t of tasks.values()) {
    if (t.status === "queued" || t.status === "awaiting_approval" || t.status === "running") return true;
  }
  return false;
}

function needsApproval(prompt: string): boolean {
  const lowered = prompt.toLowerCase();
  const riskyPatterns = [
    "delete ",
    "remove ",
    "drop table",
    "truncate",
    "reset --hard",
    "format disk",
    "deploy",
    "production",
    "push",
    "publish",
    "shutdown",
    "reboot",
  ];
  return riskyPatterns.some((p) => lowered.includes(p));
}

function getPendingApprovals() {
  return [...tasks.values()]
    .filter((t) => t.status === "awaiting_approval")
    .sort((a, b) => b.createdAt - a.createdAt);
}

function buildTodayDigest(): DigestResponse {
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

function now() {
  return Date.now();
}

function loadSessionsFromDisk() {
  try {
    const raw = fs.readFileSync(SESSIONS_PATH, "utf-8");
    const parsed = JSON.parse(raw) as { sessions?: SessionRecord[] };
    for (const s of parsed.sessions ?? []) {
      if (!s?.token) continue;
      if (!Number.isFinite(s.expiresAt)) continue;
      if (s.expiresAt <= now()) continue;
      sessionByToken.set(s.token, s);
    }
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code !== "ENOENT") {
      // Ignore parse/read errors and continue with empty session map.
    }
  }
}

function saveSessionsToDisk() {
  const sessions = [...sessionByToken.values()];
  fs.writeFileSync(SESSIONS_PATH, JSON.stringify({ sessions }, null, 2), "utf-8");
}

function pruneExpiredSessions() {
  const t = now();
  let changed = false;
  for (const [token, s] of sessionByToken.entries()) {
    if (s.expiresAt <= t) {
      sessionByToken.delete(token);
      changed = true;
    }
  }
  if (changed) saveSessionsToDisk();
}

function createSessionToken(): SessionRecord {
  pruneExpiredSessions();
  const createdAt = now();
  const expiresAt = PERMANENT_SESSION_EXPIRES_AT;
  const token = `sess_${randomUUID().replace(/-/g, "")}`;
  const rec: SessionRecord = { token, createdAt, expiresAt };
  sessionByToken.set(token, rec);
  saveSessionsToDisk();
  return rec;
}

function appendBuildLog(chunk: string) {
  if (!chunk) return;
  const next = `${buildJob.lastOutput}${chunk}`;
  // Keep last ~16KB of logs for phone polling.
  buildJob.lastOutput = next.slice(-16 * 1024);
}

function getBearerTokenFromReq(req: express.Request): string {
  const auth =
    (req.headers["authorization"] ?? "").toString().trim() ||
    (req.headers["x-bridge-token"] ?? "").toString().trim();
  if (!auth) return "";
  return auth.startsWith("Bearer ") ? auth.slice("Bearer ".length).trim() : auth;
}

function requireAuth(req: express.Request): string {
  const auth =
    (req.headers["authorization"] ?? "").toString().trim() ||
    (req.headers["x-bridge-token"] ?? "").toString().trim();

  if (!BRIDGE_TOKEN) {
    // For local dev only; for any real remote access, set BRIDGE_TOKEN.
    return "";
  }

  pruneExpiredSessions();
  const bearer = getBearerTokenFromReq(req);
  const ok =
    auth === BRIDGE_TOKEN ||
    auth === `Bearer ${BRIDGE_TOKEN}` ||
    sessionByToken.has(bearer);
  if (!ok) throw new Error("unauthorized");
  return "";
}

function requireSessionAuth(req: express.Request): SessionRecord {
  pruneExpiredSessions();
  const bearer = getBearerTokenFromReq(req);
  if (!bearer) throw new Error("unauthorized");
  const session = sessionByToken.get(bearer);
  if (!session) throw new Error("unauthorized");
  return session;
}

function resolveProject(projectId?: string): ProjectConfig {
  if (!projectId) {
    const first = projects[0];
    if (!first) throw new Error("No projects configured");
    return first;
  }
  const p = projectById.get(projectId);
  if (!p) throw new Error(`Unknown projectId: ${projectId}`);
  return p;
}

function toSafeString(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

async function runTask(taskId: string) {
  const task = tasks.get(taskId);
  if (!task) return;

  task.status = "running";
  task.updatedAt = now();
  appendUpdateLog("task_running", {
    taskId,
    projectId: task.projectId,
  });

  const project = resolveProject(task.projectId);
  const outputStyle = task.outputStyle;

  const promptPrefix =
    outputStyle === "detailed"
      ? "Write a detailed summary of what you did, including key files/changes."
      : "Write a short summary of what you did and the final outcome.";

  const prompt = `${promptPrefix}\n\nUser prompt:\n${task.prompt}`;

  try {
    if (!CURSOR_API_KEY) {
      throw new Error(
        "Missing CURSOR_API_KEY env var on the PC running the bridge.",
      );
    }

    const agentRun = Agent.prompt(prompt, {
      apiKey: CURSOR_API_KEY,
      model: { id: CURSOR_MODEL_ID },
      local: { cwd: project.cwd },
    });
    // If the run outlives the timeout, swallow its eventual settle so a late
    // rejection doesn't crash the process after we've already moved on.
    void agentRun.catch(() => {});

    let timeoutHandle: NodeJS.Timeout | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timeoutHandle = setTimeout(() => {
        reject(
          new Error(
            `Task timed out after ${TASK_TIMEOUT_MINUTES} minutes. ` +
              `The agent may still be finishing on the PC, but the bridge is free for new prompts.`,
          ),
        );
      }, TASK_TIMEOUT_MS);
    });

    let result;
    try {
      result = await Promise.race([agentRun, timeout]);
    } finally {
      clearTimeout(timeoutHandle);
    }

    // result.status is "completed" | "error" (startup errors throw CursorAgentError)
    if (result.status === "error") {
      task.status = "error";
      task.error = `Cursor run failed (id=${result.id ?? "n/a"}).`;
      appendUpdateLog("task_error", {
        taskId,
        projectId: task.projectId,
        error: task.error,
      });
    } else {
      const out = toSafeString(result.result);
      task.status = "completed";
      task.output = out;
      appendUpdateLog("task_completed", {
        taskId,
        projectId: task.projectId,
        summaryPreview: out.slice(0, 240),
      });
    }
    task.updatedAt = now();
  } catch (err) {
    task.status = "error";
    if (err instanceof CursorAgentError) {
      task.error = `Cursor agent failed to start: ${err.message}`;
    } else {
      task.error = err instanceof Error ? err.message : String(err);
    }
    appendUpdateLog("task_error", {
      taskId,
      projectId: task.projectId,
      error: task.error,
    });
    task.updatedAt = now();
  }
}

async function workerLoop() {
  if (isWorkerBusy) return;
  isWorkerBusy = true;
  try {
    while (queue.length > 0) {
      const taskId = queue.shift()!;
      // Skip if already updated/removed.
      if (!tasks.has(taskId)) continue;
      // Run sequentially to avoid concurrent file edits.
      await runTask(taskId);
    }
  } finally {
    isWorkerBusy = false;
  }
}

app.get("/health", (_req, res) => {
  pruneExpiredSessions();
  reloadProjectRegistry();
  res.json({
    ok: true,
    bridgeVersion: "0.1.0",
    uptimeMs: process.uptime() * 1000,
    projects: projects.map((p) => ({ id: p.id, name: p.name, cwd: p.cwd })),
  });
});

app.post("/api/sessions", (req, res) => {
  try {
    requireAuth(req);
    const parsed = CreateLinkSessionSchema.parse(req.body ?? {});
    const workspaceRoot = getWorkspaceRoot();
    const sessions = loadLinkSessions();
    const id = randomUUID().slice(0, 8);
    const cwd = path.join(workspaceRoot, id);
    fs.mkdirSync(cwd, { recursive: true });
    const name =
      parsed.name?.trim() || `Session ${sessions.length + 1}`;
    sessions.push({ id, name, createdAt: Date.now() });
    saveLinkSessions(sessions);
    reloadProjectRegistry();
    appendUpdateLog("link_session_created", { id, name, cwd });
    res.status(201).json({ id, name, cwd });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    const status = message === "unauthorized" ? 401 : 400;
    res.status(status).json({ error: message });
  }
});

app.delete("/api/sessions/:id", (req, res) => {
  try {
    requireAuth(req);
    const sessionId = (req.params.id ?? "").trim();
    if (!sessionId) {
      res.status(400).json({ error: "session id required" });
      return;
    }
    const workspaceRoot = getWorkspaceRoot();
    const sessions = loadLinkSessions();
    const index = sessions.findIndex((s) => s.id === sessionId);
    if (index < 0) {
      res.status(404).json({ error: "session not found" });
      return;
    }
    const session = sessions[index]!;
    const cwd = resolveSessionCwd(session, workspaceRoot);
    if (!isPathInsideRoot(cwd, workspaceRoot)) {
      res.status(400).json({ error: "invalid session path" });
      return;
    }
    const isWorkspaceRoot =
      path.resolve(cwd) === path.resolve(workspaceRoot);
    if (!isWorkspaceRoot) {
      fs.rmSync(cwd, { recursive: true, force: true });
    }
    sessions.splice(index, 1);
    saveLinkSessions(sessions);
    reloadProjectRegistry();
    appendUpdateLog("link_session_deleted", { id: sessionId, cwd });
    res.status(200).json({ ok: true, id: sessionId });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    const status = message === "unauthorized" ? 401 : 400;
    res.status(status).json({ error: message });
  }
});

app.post("/auth/login", (req, res) => {
  try {
    if (!BRIDGE_TOKEN) {
      res.status(400).json({ error: "Bridge token auth not configured" });
      return;
    }
    const parsed = LoginSchema.parse(req.body ?? {});
    if (parsed.bridgeToken !== BRIDGE_TOKEN) {
      appendUpdateLog("auth_login_failed", {
        reason: "token_mismatch",
        ip: req.ip,
      });
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    const session = createSessionToken();
    appendUpdateLog("auth_login_success", {
      sessionTokenPrefix: session.token.slice(0, 12),
      expiresAt: session.expiresAt,
      ip: req.ip,
    });
    res.status(200).json({
      sessionToken: session.token,
      expiresAt: session.expiresAt,
      permanent: true,
    });
  } catch (err) {
    const message =
      err instanceof z.ZodError ? err.message : err instanceof Error ? err.message : String(err);
    res.status(400).json({ error: message });
  }
});

app.post("/auth/rotate-session", (req, res) => {
  try {
    const existing = requireSessionAuth(req);
    sessionByToken.delete(existing.token);
    const next = createSessionToken();
    appendUpdateLog("auth_session_rotated", {
      oldSessionPrefix: existing.token.slice(0, 12),
      newSessionPrefix: next.token.slice(0, 12),
      ip: req.ip,
    });
    res.status(200).json({
      sessionToken: next.token,
      expiresAt: next.expiresAt,
      permanent: true,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.post("/tasks", async (req, res) => {
  try {
    requireAuth(req);
    const parsed = TaskCreateSchema.parse(req.body);
    if (hasInFlightTask()) {
      res.status(429).json({
        error:
          "Bridge is busy with another prompt. Wait for it to finish, then send the next one.",
      });
      return;
    }

    const project = resolveProject(parsed.projectId);
    const taskId = randomUUID();

    const requiresApproval = needsApproval(parsed.prompt);
    tasks.set(taskId, {
      taskId,
      createdAt: now(),
      updatedAt: now(),
      status: requiresApproval ? "awaiting_approval" : "queued",
      prompt: parsed.prompt,
      projectId: project.id,
      // internal field to control prompt verbosity
      outputStyle: parsed.outputStyle ?? "short",
      requiresApproval,
    } as TaskRecord);

    if (requiresApproval) {
      appendUpdateLog("task_approval_required", {
        taskId,
        projectId: project.id,
        promptPreview: parsed.prompt.slice(0, 180),
        ip: req.ip,
      });
    } else {
      queue.push(taskId);
    }
    void workerLoop();
    appendUpdateLog("task_created", {
      taskId,
      projectId: project.id,
      promptPreview: parsed.prompt.slice(0, 180),
      outputStyle: parsed.outputStyle ?? "short",
      ip: req.ip,
    });

    res.status(202).json({
      taskId,
      status: requiresApproval ? "awaiting_approval" : "queued",
      requiresApproval,
    });
  } catch (err) {
    const message =
      err instanceof z.ZodError ? err.message : err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.get("/tasks/:taskId", (req, res) => {
  const task = tasks.get(req.params.taskId);
  if (!task) {
    res.status(404).json({ error: "task not found" });
    return;
  }
  const { taskId, createdAt, updatedAt, status, output, error, prompt, projectId } =
    task;
  res.json({
    taskId,
    createdAt,
    updatedAt,
    status,
    projectId,
    prompt,
    output,
    error,
  });
});

app.get("/admin/pending-approvals", (req, res) => {
  try {
    requireAuth(req);
    const items = getPendingApprovals().map((t) => ({
      taskId: t.taskId,
      createdAt: t.createdAt,
      updatedAt: t.updatedAt,
      projectId: t.projectId,
      prompt: t.prompt,
    }));
    res.json({ count: items.length, items });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.post("/admin/pending-approvals/:taskId/approve", (req, res) => {
  try {
    requireAuth(req);
    const task = tasks.get(req.params.taskId);
    if (!task) {
      res.status(404).json({ error: "task not found" });
      return;
    }
    if (task.status !== "awaiting_approval") {
      res.status(409).json({ error: "task is not awaiting approval" });
      return;
    }
    task.status = "queued";
    task.updatedAt = now();
    task.approvedAt = now();
    queue.push(task.taskId);
    appendUpdateLog("task_approved", {
      taskId: task.taskId,
      projectId: task.projectId,
      ip: req.ip,
    });
    void workerLoop();
    res.status(200).json({ ok: true, taskId: task.taskId, status: task.status });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.get("/admin/daily-digest", (req, res) => {
  try {
    requireAuth(req);
    res.json(buildTodayDigest());
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.post("/admin/build-apk", (req, res) => {
  try {
    requireAuth(req);
    if (buildJob.status === "running") {
      res.status(409).json({ error: "build already running" });
      return;
    }
    if (!fs.existsSync(BUILD_SCRIPT_PATH)) {
      res.status(500).json({ error: `Build script not found: ${BUILD_SCRIPT_PATH}` });
      return;
    }

    buildJob.status = "running";
    buildJob.startedAt = now();
    buildJob.updatedAt = now();
    buildJob.endedAt = undefined;
    buildJob.error = undefined;
    buildJob.lastOutput = "";
    appendUpdateLog("build_started", {
      ip: req.ip,
    });

    const baseUrl = process.env.PUBLIC_URL?.trim() || DEFAULT_QR_URL.replace(/\/$/, "");
    const child = spawn(
      "powershell",
      [
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        BUILD_SCRIPT_PATH,
        "-ProjectRoot",
        PROJECT_ROOT,
        "-BaseUrl",
        baseUrl,
        "-AutoBump",
      ],
      {
        cwd: PROJECT_ROOT,
        env: process.env,
      },
    );

    child.stdout.on("data", (data) => {
      buildJob.updatedAt = now();
      appendBuildLog(data.toString());
    });
    child.stderr.on("data", (data) => {
      buildJob.updatedAt = now();
      appendBuildLog(data.toString());
    });
    child.on("exit", (code) => {
      buildJob.updatedAt = now();
      buildJob.endedAt = now();
      if (code === 0) {
        buildJob.status = "completed";
        appendUpdateLog("build_completed", {
          startedAt: buildJob.startedAt,
          endedAt: buildJob.endedAt,
        });
      } else {
        buildJob.status = "error";
        buildJob.error = `Build exited with code ${code ?? "unknown"}`;
        appendUpdateLog("build_error", {
          startedAt: buildJob.startedAt,
          endedAt: buildJob.endedAt,
          error: buildJob.error,
        });
      }
    });
    child.on("error", (err) => {
      buildJob.updatedAt = now();
      buildJob.endedAt = now();
      buildJob.status = "error";
      buildJob.error = `Failed to start build: ${err.message}`;
      appendUpdateLog("build_error", {
        startedAt: buildJob.startedAt,
        endedAt: buildJob.endedAt,
        error: buildJob.error,
      });
    });

    res.status(202).json({ ok: true, status: buildJob.status });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.post("/admin/backup-app", (req, res) => {
  try {
    requireAuth(req);
    if (!fs.existsSync(BACKUP_SCRIPT_PATH)) {
      res.status(500).json({ error: `Backup script not found: ${BACKUP_SCRIPT_PATH}` });
      return;
    }
    const child = spawn(
      "powershell",
      [
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        BACKUP_SCRIPT_PATH,
        "-ProjectRoot",
        PROJECT_ROOT,
      ],
      { cwd: PROJECT_ROOT, env: process.env },
    );
    let output = "";
    child.stdout.on("data", (d) => {
      output += d.toString();
    });
    child.stderr.on("data", (d) => {
      output += d.toString();
    });
    child.on("exit", (code) => {
      if (code === 0) {
        const match = output.match(/Archived to (.+)/);
        res.status(200).json({
          ok: true,
          path: match?.[1]?.trim() ?? "backup created",
        });
      } else {
        res.status(500).json({ error: output.trim() || `Backup exited with code ${code}` });
      }
    });
    child.on("error", (err) => {
      res.status(500).json({ error: err.message });
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.get("/admin/build-apk/status", (req, res) => {
  try {
    requireAuth(req);
    res.json({
      status: buildJob.status,
      startedAt: buildJob.startedAt,
      updatedAt: buildJob.updatedAt,
      endedAt: buildJob.endedAt,
      error: buildJob.error,
      lastOutput: buildJob.lastOutput,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.get("/admin/activity", (req, res) => {
  try {
    requireAuth(req);
    const limitRaw = Number(req.query.limit ?? 30);
    const limit = Number.isFinite(limitRaw)
      ? Math.max(1, Math.min(200, Math.floor(limitRaw)))
      : 30;

    if (!fs.existsSync(UPDATE_LOG_PATH)) {
      res.json({ entries: [] });
      return;
    }

    const raw = fs.readFileSync(UPDATE_LOG_PATH, "utf-8");
    const lines = raw
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    const entries = lines
      .slice(-limit)
      .map((line) => {
        try {
          return JSON.parse(line) as Record<string, unknown>;
        } catch {
          return { timestamp: new Date().toISOString(), event: "log_parse_error", raw: line };
        }
      })
      .reverse();

    res.json({ entries });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "unauthorized") {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    res.status(400).json({ error: message });
  }
});

app.get("/qr", async (req, res) => {
  try {
    const urlParam = req.query.url;
    const url =
      typeof urlParam === "string" && urlParam.trim()
        ? urlParam.trim()
        : DEFAULT_QR_URL;

    const svg = await qrcode.toString(url, {
      type: "svg",
      errorCorrectionLevel: "M",
      margin: 2,
      scale: 8,
    });

    res.setHeader("content-type", "text/html; charset=utf-8");
    res.send(`<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>InvictusLink QR</title>
    <style>
      body { margin: 0; font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; background: #0b0b0f; color: #e9e9f1; display:flex; justify-content:center; align-items:center; min-height:100vh; }
      .wrap { width: min(520px, 92vw); text-align: center; }
      .hint { opacity: 0.85; font-size: 13px; margin-top: 10px; }
      code { display:block; margin-top: 10px; font-size: 12px; opacity: 0.8; word-break: break-all; }
      .qr { display:flex; justify-content:center; }
      svg { background: white; padding: 14px; border-radius: 10px; }
    </style>
  </head>
  <body>
    <div class="wrap">
      <div class="qr">${svg}</div>
      <div class="hint">Scan to install InvictusLink</div>
      <code>${url}</code>
    </div>
  </body>
</html>`);
  } catch (err) {
    res.status(500).send(
      `Failed to generate QR: ${err instanceof Error ? err.message : String(err)}`,
    );
  }
});

acquireBridgeLock();
loadSessionsFromDisk();
process.on("exit", releaseBridgeLock);
process.on("SIGINT", () => {
  releaseBridgeLock();
  process.exit(0);
});
process.on("SIGTERM", () => {
  releaseBridgeLock();
  process.exit(0);
});

app.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`InvictusLink bridge listening on http://localhost:${PORT}`);
});

