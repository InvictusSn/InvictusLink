import fs from "node:fs";
import { UPDATE_LOG_PATH } from "./bridgeConfig.js";
import { now, type TaskRecord } from "./bridgeState.js";

type LogEntry = {
  timestamp?: string;
  event?: string;
  taskId?: string;
  projectId?: string;
  promptPreview?: string;
  summaryPreview?: string;
  error?: string;
  routingNote?: string;
  provider?: string;
};

const BRIDGE_RESTART_ERROR =
  "The PC bridge restarted while this task was running. Send the prompt again if you still need it.";

/** Reconstruct a terminal task snapshot from the update log after a bridge restart. */
export function recoverTaskFromUpdateLog(taskId: string): TaskRecord | null {
  if (!taskId || !fs.existsSync(UPDATE_LOG_PATH)) return null;

  let created: LogEntry | null = null;
  let completed: LogEntry | null = null;
  let errored: LogEntry | null = null;

  for (const line of fs.readFileSync(UPDATE_LOG_PATH, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    let entry: LogEntry;
    try {
      entry = JSON.parse(trimmed) as LogEntry;
    } catch {
      continue;
    }
    if (entry.taskId !== taskId) continue;

    switch (entry.event) {
      case "task_created":
        created = entry;
        break;
      case "task_completed":
        completed = entry;
        break;
      case "task_error":
        errored = entry;
        break;
      default:
        break;
    }
  }

  if (!created) return null;

  const createdAt = Date.parse(created.timestamp ?? "") || now();
  const base = {
    taskId,
    createdAt,
    updatedAt: now(),
    prompt: created.promptPreview ?? "",
    projectId: created.projectId ?? "",
    outputStyle: "short" as const,
  };

  if (completed) {
    return {
      ...base,
      status: "completed",
      output: completed.summaryPreview ?? "(Task finished on PC)",
      providerLabel: completed.provider,
      routingNote: completed.routingNote,
    };
  }

  if (errored) {
    return {
      ...base,
      status: "error",
      error: errored.error ?? errored.summaryPreview ?? "Task failed on PC",
      output: errored.summaryPreview,
      providerLabel: errored.provider,
      routingNote: errored.routingNote,
    };
  }

  return {
    ...base,
    status: "error",
    error: BRIDGE_RESTART_ERROR,
  };
}
