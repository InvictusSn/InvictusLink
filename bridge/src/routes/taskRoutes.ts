import type express from "express";
import { randomUUID } from "node:crypto";
import { z } from "zod";
import { getUserKeyFromReq, requireAuth } from "../bridgeAuth.js";
import {
  appendUpdateLog,
  hasInFlightTask,
  now,
  queue,
  TaskCreateSchema,
  tasks,
  type TaskRecord,
} from "../bridgeState.js";
import { resolveProject } from "../bridgeProjects.js";
import { taskRequiresApproval, workerLoop } from "../bridgeTasks.js";
import { recoverTaskFromUpdateLog } from "../taskRecovery.js";
import { cancelTaskById } from "../taskCancellation.js";
import { promptImpliesBridgeRestart } from "../bridgeRestart.js";

export function registerTaskRoutes(app: express.Application) {
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

      const requiresApproval = promptImpliesBridgeRestart(parsed.prompt)
        ? false
        : await taskRequiresApproval(parsed.prompt);
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
        attachments: parsed.attachments,
        userKey: getUserKeyFromReq(req),
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
    try {
      requireAuth(req);
    } catch {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    const task = tasks.get(req.params.taskId) ?? recoverTaskFromUpdateLog(req.params.taskId);
    if (!task) {
      res.status(404).json({ error: "task not found" });
      return;
    }
    const {
      taskId,
      createdAt,
      updatedAt,
      status,
      output,
      error,
      prompt,
      projectId,
      providerLabel,
      routingNote,
      usage,
    } = task;
    res.json({
      taskId,
      createdAt,
      updatedAt,
      status,
      projectId,
      prompt,
      output,
      error,
      providerLabel,
      routingNote,
      usage,
    });
  });

  app.post("/tasks/:taskId/cancel", async (req, res) => {
    try {
      requireAuth(req);
      const cancelled = await cancelTaskById(req.params.taskId);
      if (!cancelled) {
        const task = tasks.get(req.params.taskId) ?? recoverTaskFromUpdateLog(req.params.taskId);
        if (!task) {
          res.status(404).json({ error: "task not found" });
          return;
        }
        res.status(409).json({ error: "task is not running", status: task.status });
        return;
      }
      const task = tasks.get(req.params.taskId);
      res.status(200).json({
        ok: true,
        taskId: req.params.taskId,
        status: task?.status ?? "error",
        error: task?.error ?? "Stopped by user.",
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });
}
