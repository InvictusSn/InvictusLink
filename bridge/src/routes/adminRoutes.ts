import fs from "node:fs";
import type express from "express";
import { requireAuth } from "../bridgeAuth.js";
import {
  BACKUP_SCRIPT_PATH,
  BUILD_SCRIPT_PATH,
  DEFAULT_QR_URL,
  DOWNLOAD_DIR,
  PROJECT_ROOT,
  UPDATE_LOG_PATH,
} from "../bridgeConfig.js";
import {
  appendBuildLog,
  appendUpdateLog,
  buildJob,
  buildTodayDigest,
  now,
  queue,
  tasks,
} from "../bridgeState.js";
import { spawnHiddenPowershellFile } from "../processUtils.js";
import { getPendingApprovals, workerLoop } from "../bridgeTasks.js";

export function registerAdminRoutes(app: express.Application) {
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
      const child = spawnHiddenPowershellFile(
        BUILD_SCRIPT_PATH,
        [
          "-ProjectRoot",
          PROJECT_ROOT,
          "-BaseUrl",
          baseUrl,
          "-BridgeDownloadDir",
          DOWNLOAD_DIR,
          "-AutoBump",
        ],
        {
          cwd: PROJECT_ROOT,
          env: {
            ...process.env,
            INVICTUS_APPS_ROOT: PROJECT_ROOT,
          },
        },
      );

      child.stdout?.on("data", (data) => {
        buildJob.updatedAt = now();
        appendBuildLog(data.toString());
      });
      child.stderr?.on("data", (data) => {
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
      const child = spawnHiddenPowershellFile(
        BACKUP_SCRIPT_PATH,
        ["-ProjectRoot", PROJECT_ROOT],
        { cwd: PROJECT_ROOT, env: process.env },
      );
      let output = "";
      child.stdout?.on("data", (d) => {
        output += d.toString();
      });
      child.stderr?.on("data", (d) => {
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
}
