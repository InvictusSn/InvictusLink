import fs from "node:fs";
import path from "node:path";
import express from "express";
import { z } from "zod";
import { requireAuth } from "../bridgeAuth.js";
import { EXPORT_DIR } from "../bridgeConfig.js";
import { appendUpdateLog } from "../bridgeState.js";
import { isPathInsideRoot } from "../bridgeProjects.js";

const EXPORT_MAX_BYTES = 2 * 1024 * 1024;

const ExportSchema = z.object({
  projectId: z.string().min(1).max(200).optional(),
  filename: z.string().min(1).max(160),
  content: z.string().min(1).max(EXPORT_MAX_BYTES),
});

/** Strip directories and unsafe characters; force a .md extension. */
function sanitizeExportName(raw: string): string {
  let base = path.basename(raw).replace(/[^\w.\- ]+/g, "_").trim();
  if (!base) base = "conversation";
  if (!base.toLowerCase().endsWith(".md")) base = `${base}.md`;
  return base;
}

export function registerExportRoutes(app: express.Application) {
  // Conversations exported from the phone land in GrokResearch on the Desktop.
  app.post(
    "/api/exports",
    express.json({ limit: "3mb" }),
    (req, res) => {
      try {
        requireAuth(req);
        const parsed = ExportSchema.parse(req.body ?? {});
        const safeName = sanitizeExportName(parsed.filename);
        const exportsDir = path.resolve(EXPORT_DIR);
        fs.mkdirSync(exportsDir, { recursive: true });

        let finalName = safeName;
        if (fs.existsSync(path.join(exportsDir, finalName))) {
          const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
          finalName = `${stamp}_${safeName}`;
        }
        const target = path.join(exportsDir, finalName);
        if (!isPathInsideRoot(target, exportsDir)) {
          res.status(400).json({ error: "invalid export filename" });
          return;
        }
        fs.writeFileSync(target, parsed.content, "utf-8");
        const displayPath = path.join("GrokResearch", finalName);
        appendUpdateLog("conversation_exported", {
          projectId: parsed.projectId ?? null,
          path: target,
          bytes: Buffer.byteLength(parsed.content, "utf-8"),
          ip: req.ip,
        });
        res.status(201).json({ ok: true, path: displayPath, name: finalName });
      } catch (err) {
        const message =
          err instanceof z.ZodError
            ? "Invalid export payload"
            : err instanceof Error
              ? err.message
              : String(err);
        const status = message === "unauthorized" ? 401 : 400;
        res.status(status).json({ error: message });
      }
    },
  );
}
