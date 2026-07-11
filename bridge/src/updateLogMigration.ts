import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { PROJECT_ROOT, UPDATE_LOG_PATH } from "./bridgeConfig.js";

function legacyUpdateLogPaths(): string[] {
  const paths: string[] = [
    path.join(PROJECT_ROOT, "bridge", "update-log.jsonl"),
    path.join(PROJECT_ROOT, "update-log.jsonl"),
    path.join(os.homedir(), "OneDrive", "Desktop", "InvictusApps", "bridge", "update-log.jsonl"),
    path.join(os.homedir(), "Desktop", "InvictusApps", "bridge", "update-log.jsonl"),
    path.join(os.homedir(), "OneDrive", "Desktop", "done", "bridge", "update-log.jsonl"),
    path.join(os.homedir(), "OneDrive", "Desktop", "CursorMobile", "bridge", "update-log.jsonl"),
  ];

  const configFile = path.join(
    process.env.APPDATA || path.join(os.homedir(), "AppData", "Roaming"),
    "Invictus Link",
    "bridge-dir.txt",
  );
  try {
    const cached = fs.readFileSync(configFile, "utf8").trim();
    if (cached) paths.push(path.join(cached, "update-log.jsonl"));
  } catch {
    // no cached legacy bridge dir
  }

  const target = path.resolve(UPDATE_LOG_PATH);
  return [...new Set(paths.map((p) => path.resolve(p)))].filter((p) => p !== target);
}

/** Merge JSONL lines from legacy bridge installs into the active update log. */
export function mergeUpdateLogFiles(sources: string[], target: string): boolean {
  const lines = new Set<string>();
  if (fs.existsSync(target)) {
    for (const line of fs.readFileSync(target, "utf8").split(/\r?\n/)) {
      const trimmed = line.trim();
      if (trimmed) lines.add(trimmed);
    }
  }

  const before = lines.size;
  for (const source of sources) {
    if (!fs.existsSync(source)) continue;
    for (const line of fs.readFileSync(source, "utf8").split(/\r?\n/)) {
      const trimmed = line.trim();
      if (trimmed) lines.add(trimmed);
    }
  }

  if (lines.size === before) return false;

  const merged = [...lines].sort((a, b) => {
    try {
      const ta = (JSON.parse(a) as { timestamp?: string }).timestamp ?? "";
      const tb = (JSON.parse(b) as { timestamp?: string }).timestamp ?? "";
      return ta.localeCompare(tb);
    } catch {
      return a.localeCompare(b);
    }
  });

  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, `${merged.join("\n")}\n`, "utf8");
  return true;
}

/** Pull historical task/cost events into the bridge's active update log on startup. */
export function ensureUpdateLogMigrated(): void {
  try {
    mergeUpdateLogFiles(legacyUpdateLogPaths(), UPDATE_LOG_PATH);
  } catch {
    // Migration must never block bridge startup.
  }
}
