import type express from "express";
import { BRIDGE_VERSION } from "../bridgeConfig.js";
import { pruneExpiredSessions } from "../bridgeAuth.js";
import { reloadProjectRegistry } from "../bridgeProjects.js";
import { projects } from "../bridgeState.js";
import {
  getActiveProvider,
  getRoutingMode,
  resolveModel,
} from "../providers.js";

export function registerHealthRoutes(app: express.Application) {
  app.get("/health", (_req, res) => {
    pruneExpiredSessions();
    reloadProjectRegistry();
    const active = getActiveProvider();
    // Only id/name — filesystem paths and API keys stay on the PC.
    res.json({
      ok: true,
      bridgeVersion: BRIDGE_VERSION,
      uptimeMs: process.uptime() * 1000,
      projects: projects.map((p) => ({ id: p.id, name: p.name })),
      activeProvider: active
        ? { id: active.id, type: active.type, label: active.label, model: resolveModel(active) }
        : null,
      routingMode: getRoutingMode(),
    });
  });
}
