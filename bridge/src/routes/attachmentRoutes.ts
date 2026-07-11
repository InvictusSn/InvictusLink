import fs from "node:fs";
import path from "node:path";
import express from "express";
import { requireAuth } from "../bridgeAuth.js";
import { appendUpdateLog } from "../bridgeState.js";
import { PROJECT_ROOT } from "../bridgeConfig.js";
import { isPathInsideRoot, resolveProject } from "../bridgeProjects.js";

const ATTACHMENT_MAX_BYTES = 25 * 1024 * 1024;

/** Strip directories and unsafe characters; keep the extension. */
function sanitizeAttachmentName(raw: string): string {
  const base = path.basename(raw).replace(/[^\w.\- ]+/g, "_").trim();
  return base || "attachment";
}

export function registerAttachmentRoutes(app: express.Application) {
  // Files attached from the phone are saved into the project's attachments/
  // folder so the Cursor agent can read them like any other workspace file.
  app.post(
    "/api/attachments",
    express.raw({ type: "*/*", limit: "26mb" }),
    (req, res) => {
      try {
        requireAuth(req);
        const projectId = (req.query.projectId ?? "").toString().trim() || undefined;
        const rawName = (req.query.name ?? "").toString().trim();
        if (!rawName) {
          res.status(400).json({ error: "name query parameter required" });
          return;
        }
        const body = req.body as Buffer;
        if (!Buffer.isBuffer(body) || body.length === 0) {
          res.status(400).json({ error: "empty attachment body" });
          return;
        }
        if (body.length > ATTACHMENT_MAX_BYTES) {
          res.status(413).json({ error: "attachment too large (max 25 MB)" });
          return;
        }

        const project = resolveProject(projectId);
        const safeName = sanitizeAttachmentName(rawName);
        const attachmentsDir = path.join(project.cwd, "attachments");
        fs.mkdirSync(attachmentsDir, { recursive: true });

        // De-dupe with a short timestamp prefix if the name is taken.
        let finalName = safeName;
        if (fs.existsSync(path.join(attachmentsDir, finalName))) {
          const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
          finalName = `${stamp}_${safeName}`;
        }
        const target = path.join(attachmentsDir, finalName);
        if (!isPathInsideRoot(target, attachmentsDir)) {
          res.status(400).json({ error: "invalid attachment name" });
          return;
        }
        fs.writeFileSync(target, body);
        const relativePath = `attachments/${finalName}`;
        appendUpdateLog("attachment_uploaded", {
          projectId: project.id,
          path: relativePath,
          bytes: body.length,
          ip: req.ip,
        });
        res.status(201).json({ ok: true, path: relativePath, name: finalName, bytes: body.length });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        const status = message === "unauthorized" ? 401 : 400;
        res.status(status).json({ error: message });
      }
    },
  );

  // Crash reports uploaded from the phone are saved next to the bridge so they
  // can be reviewed on the PC (crash-logs/ folder in the project root).
  app.post(
    "/api/crashlog",
    express.text({ type: "*/*", limit: "1mb" }),
    (req, res) => {
      try {
        requireAuth(req);
        const body = typeof req.body === "string" ? req.body : "";
        if (!body.trim()) {
          res.status(400).json({ error: "empty crash log body" });
          return;
        }
        const crashDir = path.join(PROJECT_ROOT, "crash-logs");
        fs.mkdirSync(crashDir, { recursive: true });
        const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
        const fileName = `link-crash-${stamp}.txt`;
        fs.writeFileSync(path.join(crashDir, fileName), body, "utf-8");
        appendUpdateLog("crash_log_received", {
          file: `crash-logs/${fileName}`,
          bytes: body.length,
          ip: req.ip,
        });
        res.status(201).json({ ok: true, file: fileName });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        const status = message === "unauthorized" ? 401 : 400;
        res.status(status).json({ error: message });
      }
    },
  );
}
