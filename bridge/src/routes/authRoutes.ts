import type express from "express";
import { z } from "zod";
import {
  createSessionToken,
  requireSessionAuth,
} from "../bridgeAuth.js";
import { BRIDGE_TOKEN } from "../bridgeConfig.js";
import { appendUpdateLog, LoginSchema, sessionByToken } from "../bridgeState.js";

export function registerAuthRoutes(app: express.Application) {
  app.post("/auth/login", (req, res) => {
    try {
      if (!BRIDGE_TOKEN) {
        res.status(400).json({ error: "Bridge token auth not configured" });
        return;
      }
      const parsed = LoginSchema.parse(req.body ?? {});
      if (parsed.bridgeToken !== BRIDGE_TOKEN) {
        appendUpdateLog("auth_login_failed", {
          reason: "token_mismatch",
          ip: req.ip,
        });
        res.status(401).json({ error: "unauthorized" });
        return;
      }
      const session = createSessionToken();
      appendUpdateLog("auth_login_success", {
        sessionTokenPrefix: session.token.slice(0, 12),
        expiresAt: session.expiresAt,
        ip: req.ip,
      });
      res.status(200).json({
        sessionToken: session.token,
        expiresAt: session.expiresAt,
        permanent: true,
      });
    } catch (err) {
      const message =
        err instanceof z.ZodError ? err.message : err instanceof Error ? err.message : String(err);
      res.status(400).json({ error: message });
    }
  });

  app.post("/auth/rotate-session", (req, res) => {
    try {
      const existing = requireSessionAuth(req);
      sessionByToken.delete(existing.token);
      const next = createSessionToken();
      appendUpdateLog("auth_session_rotated", {
        oldSessionPrefix: existing.token.slice(0, 12),
        newSessionPrefix: next.token.slice(0, 12),
        ip: req.ip,
      });
      res.status(200).json({
        sessionToken: next.token,
        expiresAt: next.expiresAt,
        permanent: true,
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
}
