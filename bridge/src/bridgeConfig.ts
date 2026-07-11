import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import type express from "express";
import { configureEnvCursorProvider } from "./providers.js";

export const PORT = Number(process.env.PORT ?? 3003);
export const BRIDGE_TOKEN = process.env.BRIDGE_TOKEN ?? "";
export const CURSOR_API_KEY = process.env.CURSOR_API_KEY ?? "";
export const CURSOR_MODEL_ID = process.env.CURSOR_MODEL_ID ?? "composer-2.5";
configureEnvCursorProvider(CURSOR_API_KEY, CURSOR_MODEL_ID);

/** Max minutes a single agent run may take before the bridge frees itself for new prompts. */
export const TASK_TIMEOUT_MINUTES = Math.max(
  1,
  Number(process.env.TASK_TIMEOUT_MINUTES ?? 10) || 10,
);
export const TASK_TIMEOUT_MS = TASK_TIMEOUT_MINUTES * 60_000;

export const PROJECTS_PATH =
  process.env.PROJECTS_PATH ??
  path.join(process.cwd(), "config", "projects.json");
export const LOCK_PATH =
  process.env.BRIDGE_LOCK_PATH ??
  path.join(process.cwd(), ".bridge.lock.json");
export const SESSIONS_PATH =
  process.env.BRIDGE_SESSIONS_PATH ??
  path.join(process.cwd(), ".bridge.sessions.json");
export const LINK_SESSIONS_PATH =
  process.env.LINK_SESSIONS_PATH ??
  path.join(process.cwd(), "config", "link-sessions.json");
export const UPDATE_LOG_PATH =
  process.env.BRIDGE_UPDATE_LOG_PATH ??
  path.join(process.cwd(), "update-log.jsonl");

/** Where Link conversation exports from the phone are saved on the PC. */
export function getDefaultExportDir(): string {
  const home = os.homedir();
  const oneDriveDesktop = path.join(home, "OneDrive", "Desktop", "GrokResearch");
  const desktop = path.join(home, "Desktop", "GrokResearch");
  if (fs.existsSync(path.join(home, "OneDrive", "Desktop"))) return oneDriveDesktop;
  return desktop;
}

export const EXPORT_DIR =
  process.env.EXPORT_DIR?.trim() || getDefaultExportDir();

function looksLikeInvictusAppsRoot(dir: string): boolean {
  return (
    fs.existsSync(path.join(dir, "android", "gradlew.bat")) &&
    fs.existsSync(path.join(dir, "scripts", "build-and-publish-apk.ps1"))
  );
}

function readCachedAppsRoot(): string | null {
  const file = path.join(
    process.env.APPDATA || path.join(os.homedir(), "AppData", "Roaming"),
    "Invictus Link",
    "apps-root.txt",
  );
  try {
    const cached = fs.readFileSync(file, "utf8").trim();
    return cached || null;
  } catch {
    return null;
  }
}

/** Android sources live in the repo; bridge may run from %LOCALAPPDATA%. */
export function discoverInvictusAppsRoot(): string {
  const fromEnv = process.env.INVICTUS_APPS_ROOT?.trim();
  if (fromEnv) {
    const resolved = path.resolve(fromEnv);
    if (looksLikeInvictusAppsRoot(resolved)) return resolved;
  }

  const sibling = path.resolve(process.cwd(), "..");
  if (looksLikeInvictusAppsRoot(sibling)) return sibling;

  const cached = readCachedAppsRoot();
  if (cached && looksLikeInvictusAppsRoot(cached)) return path.resolve(cached);

  const home = os.homedir();
  for (const candidate of [
    path.join(home, "OneDrive", "Desktop", "InvictusApps"),
    path.join(home, "Desktop", "InvictusApps"),
  ]) {
    if (looksLikeInvictusAppsRoot(candidate)) return candidate;
  }

  return sibling;
}

export const PROJECT_ROOT = discoverInvictusAppsRoot();
export const BUILD_SCRIPT_PATH = path.join(PROJECT_ROOT, "scripts", "build-and-publish-apk.ps1");
export const BACKUP_SCRIPT_PATH = path.join(PROJECT_ROOT, "scripts", "backup-app-version.ps1");
/** Sessions stay valid until the user disconnects or reinstalls after an app update. */
export const PERMANENT_SESSION_EXPIRES_AT = 9_000_000_000_000;
export const DOWNLOAD_DIR = path.join(process.cwd(), "public", "download");
export const LATEST_JSON_PATH = path.join(DOWNLOAD_DIR, "latest.json");
export const APK_FILENAME = "InvictusLink.apk";
export const APK_PATH = path.join(DOWNLOAD_DIR, APK_FILENAME);
export const PULSE_LATEST_JSON_PATH = path.join(DOWNLOAD_DIR, "pulse-latest.json");
export const PULSE_APK_FILENAME = "InvictusPulse.apk";
export const PULSE_APK_PATH = path.join(DOWNLOAD_DIR, PULSE_APK_FILENAME);

export const BRIDGE_VERSION = "2.0.0";

export function getDefaultPublicUrl() {
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

export function getRequestBaseUrl(req: express.Request): string {
  const host = req.get("host");
  if (!host) return getDefaultPublicUrl().replace(/\/$/, "");
  const proto =
    req.protocol === "https" || req.get("x-forwarded-proto") === "https"
      ? "https"
      : "http";
  return `${proto}://${host}`;
}

export const DEFAULT_QR_URL = getDefaultPublicUrl();
