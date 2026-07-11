import fs from "node:fs";
import path from "node:path";
import type express from "express";
import { randomUUID } from "node:crypto";
import { requireAuth } from "../bridgeAuth.js";
import {
  appendUpdateLog,
  CreateLinkSessionSchema,
  RenameLinkSessionSchema,
} from "../bridgeState.js";
import {
  getWorkspaceRoot,
  isPathInsideRoot,
  loadLinkSessions,
  reloadProjectRegistry,
  resolveSessionCwd,
  saveLinkSessions,
} from "../bridgeProjects.js";
import { resetGrokThread } from "../grokThreads.js";

export function registerSessionRoutes(app: express.Application) {
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
      resetGrokThread(sessionId);
      reloadProjectRegistry();
      appendUpdateLog("link_session_deleted", { id: sessionId, cwd });
      res.status(200).json({ ok: true, id: sessionId });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.patch("/api/sessions/:id", (req, res) => {
    try {
      requireAuth(req);
      const sessionId = (req.params.id ?? "").trim();
      if (!sessionId) {
        res.status(400).json({ error: "session id required" });
        return;
      }
      const parsed = RenameLinkSessionSchema.parse(req.body ?? {});
      const name = parsed.name.trim();
      const sessions = loadLinkSessions();
      const index = sessions.findIndex((s) => s.id === sessionId);
      if (index < 0) {
        res.status(404).json({ error: "session not found" });
        return;
      }
      sessions[index] = { ...sessions[index]!, name };
      saveLinkSessions(sessions);
      reloadProjectRegistry();
      appendUpdateLog("link_session_renamed", { id: sessionId, name });
      res.status(200).json({ id: sessionId, name });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });
}
