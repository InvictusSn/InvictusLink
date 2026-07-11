import { PORT } from "./bridgeConfig.js";
import { appendUpdateLog, now, tasks } from "./bridgeState.js";
import { scheduleBridgeRestartProcess } from "./bridgeRestartProcess.js";

/**
 * True only for clear restart *commands* (e.g. "please restart the bridge").
 * Questions / chat about restarting must not kill the bridge.
 */
export function promptImpliesBridgeRestart(prompt: string): boolean {
  const lowered = prompt.toLowerCase().trim();
  if (lowered.length < 8) return false;

  // "did the bridge restart?" / "has it reloaded?" — never treat as a command.
  if (/^(did|does|has|have|will|would|can|could|is|was|are|were)\b/.test(lowered)) {
    return false;
  }
  if (/\?\s*$/.test(lowered) && !/\b(please|pls)\b/.test(lowered)) {
    return false;
  }

  return (
    /\b(please\s+|pls\s+)?(restart|reboot|reload)\s+(the\s+)?(invictus\s+link\s+)?(pc\s+)?(link\s+)?bridge\b/.test(
      lowered,
    ) ||
    /\b(please\s+|pls\s+)?(restart|reboot|reload)\s+(the\s+)?invictus\s+link\b/.test(lowered) ||
    /\bpower[\s-]*cycle\s+(the\s+)?(link\s+)?bridge\b/.test(lowered) ||
    /\b(turn|power)\s+(the\s+)?(link\s+)?bridge\s+off\s+and\s+on\b/.test(lowered) ||
    /\bstop\s+and\s+start\s+(the\s+)?(link\s+)?bridge\b/.test(lowered)
  );
}

export async function runBridgeRestartTask(taskId: string): Promise<void> {
  const task = tasks.get(taskId);
  if (!task) return;

  task.status = "running";
  task.updatedAt = now();
  task.providerLabel = "System";
  task.routingNote = "Bridge restart";
  task.output = "Restarting Invictus Link bridge on your PC…";

  task.status = "completed";
  task.output =
    "Done — the Invictus Link bridge is restarting on your PC. " +
    "You should be reconnected in a few seconds.";
  task.updatedAt = now();

  appendUpdateLog("bridge_restart_requested", {
    taskId,
    projectId: task.projectId,
    promptPreview: task.prompt.slice(0, 180),
  });
  appendUpdateLog("task_completed", {
    taskId,
    projectId: task.projectId,
    provider: "System",
    providerType: "system",
    isLocal: true,
    summaryPreview: task.output.slice(0, 240),
  });

  scheduleBridgeRestartProcess();
}
