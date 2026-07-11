import { appendUpdateLog, now, queue, tasks } from "./bridgeState.js";

type TaskCancelHandle = {
  cancel: () => Promise<void>;
};

const activeCancels = new Map<string, TaskCancelHandle>();

export function registerTaskCancel(taskId: string, handle: TaskCancelHandle) {
  activeCancels.set(taskId, handle);
}

export function unregisterTaskCancel(taskId: string) {
  activeCancels.delete(taskId);
}

export async function cancelTaskById(taskId: string): Promise<boolean> {
  const task = tasks.get(taskId);
  if (!task) return false;
  if (task.status === "completed" || task.status === "error") return false;

  if (task.status === "queued" || task.status === "awaiting_approval") {
    const previousStatus = task.status;
    const index = queue.indexOf(taskId);
    if (index >= 0) queue.splice(index, 1);
    task.status = "error";
    task.error = "Stopped by user.";
    task.updatedAt = now();
    appendUpdateLog("task_cancelled", {
      taskId,
      projectId: task.projectId,
      previousStatus,
    });
    return true;
  }

  if (task.status === "running") {
    const handle = activeCancels.get(taskId);
    if (handle) {
      try {
        await handle.cancel();
      } catch {
        // Best-effort — still mark the task stopped for the phone.
      }
    }
    task.status = "error";
    task.error = "Stopped by user.";
    task.updatedAt = now();
    appendUpdateLog("task_cancelled", {
      taskId,
      projectId: task.projectId,
      previousStatus: "running",
    });
    return true;
  }

  return false;
}
