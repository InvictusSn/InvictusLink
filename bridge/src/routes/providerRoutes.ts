import type express from "express";
import { z } from "zod";
import { requireAuth } from "../bridgeAuth.js";
import { appendUpdateLog, AddProviderSchema, RoutingModeSchema } from "../bridgeState.js";
import {
  activateProvider,
  addProvider,
  deleteProvider,
  getActiveProvider,
  getRoutingMode,
  listProviderRecords,
  listProvidersPublic,
  setRoutingMode,
  testProvider,
  toPublic,
  type RoutingMode,
} from "../providers.js";
import { resetGrokThread } from "../grokThreads.js";

export function registerProviderRoutes(app: express.Application) {
  app.get("/api/providers", (req, res) => {
    try {
      requireAuth(req);
      res.json(listProvidersPublic());
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.post("/api/providers", async (req, res) => {
    try {
      requireAuth(req);
      const parsed = AddProviderSchema.parse(req.body ?? {});
      const rec = addProvider(parsed);
      const test = await testProvider(rec);
      const active = getActiveProvider();
      appendUpdateLog("provider_added", {
        providerId: rec.id,
        type: rec.type,
        label: rec.label,
        testOk: test.ok,
        ip: req.ip,
      });
      res.status(201).json({
        provider: toPublic(rec, active?.id),
        test,
      });
    } catch (err) {
      const message =
        err instanceof z.ZodError ? "Invalid provider details" : err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.delete("/api/providers/:id", (req, res) => {
    try {
      requireAuth(req);
      const id = (req.params.id ?? "").trim();
      const removed = deleteProvider(id);
      if (!removed) {
        res.status(404).json({ error: "provider not found" });
        return;
      }
      appendUpdateLog("provider_deleted", { providerId: id, ip: req.ip });
      res.json({ ok: true, id });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.post("/api/providers/:id/activate", (req, res) => {
    try {
      requireAuth(req);
      const id = (req.params.id ?? "").trim();
      const rec = activateProvider(id);
      appendUpdateLog("provider_activated", {
        providerId: rec.id,
        type: rec.type,
        label: rec.label,
        ip: req.ip,
      });
      res.json({ ok: true, provider: toPublic(rec, rec.id) });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status =
        message === "unauthorized" ? 401 : message === "Provider not found" ? 404 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.post("/api/providers/routing-mode", (req, res) => {
    try {
      requireAuth(req);
      const parsed = RoutingModeSchema.parse(req.body ?? {});
      const mode = setRoutingMode(parsed.mode as RoutingMode);
      appendUpdateLog("routing_mode_changed", { mode, ip: req.ip });
      res.json({ ok: true, routingMode: mode });
    } catch (err) {
      const message =
        err instanceof z.ZodError ? "Invalid routing mode" : err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.post("/api/grok-threads/:projectId/reset", (req, res) => {
    try {
      requireAuth(req);
      const projectId = (req.params.projectId ?? "").trim();
      if (!projectId) {
        res.status(400).json({ error: "project id required" });
        return;
      }
      resetGrokThread(projectId);
      res.json({ ok: true, projectId });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.post("/api/providers/:id/test", async (req, res) => {
    try {
      requireAuth(req);
      const id = (req.params.id ?? "").trim();
      const rec = listProviderRecords().find((p) => p.id === id);
      if (!rec) {
        res.status(404).json({ error: "provider not found" });
        return;
      }
      const result = await testProvider(rec);
      res.json(result);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });
}
