import type express from "express";
import { z } from "zod";
import { getUserKeyFromReq, requireAuth } from "../bridgeAuth.js";
import { appendUpdateLog } from "../bridgeState.js";
import {
  buildCostDashboard,
  loadCostSettings,
  saveCostSettings,
} from "../costTracking.js";

const LimitsSchema = z.object({
  monthlyLimitUsd: z.number().min(0).max(100_000).nullable().optional(),
  dailyLimitUsd: z.number().min(0).max(100_000).nullable().optional(),
});

export function registerCostRoutes(app: express.Application) {
  app.get("/admin/costs", (req, res) => {
    try {
      requireAuth(req);
      res.json(buildCostDashboard(getUserKeyFromReq(req)));
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });

  app.post("/admin/costs/limits", (req, res) => {
    try {
      requireAuth(req);
      const parsed = LimitsSchema.parse(req.body ?? {});
      const settings = loadCostSettings();
      if (parsed.monthlyLimitUsd !== undefined) {
        settings.monthlyLimitUsd =
          parsed.monthlyLimitUsd === null || parsed.monthlyLimitUsd === 0
            ? undefined
            : parsed.monthlyLimitUsd;
      }
      if (parsed.dailyLimitUsd !== undefined) {
        settings.dailyLimitUsd =
          parsed.dailyLimitUsd === null || parsed.dailyLimitUsd === 0
            ? undefined
            : parsed.dailyLimitUsd;
      }
      // Limits changed — allow a fresh alert for the new thresholds.
      settings.lastAlertDay = undefined;
      saveCostSettings(settings);
      appendUpdateLog("cost_limits_changed", {
        monthlyLimitUsd: settings.monthlyLimitUsd ?? null,
        dailyLimitUsd: settings.dailyLimitUsd ?? null,
        ip: req.ip,
      });
      res.json({
        ok: true,
        monthlyLimitUsd: settings.monthlyLimitUsd ?? null,
        dailyLimitUsd: settings.dailyLimitUsd ?? null,
      });
    } catch (err) {
      const message =
        err instanceof z.ZodError
          ? "Invalid limit values"
          : err instanceof Error
            ? err.message
            : String(err);
      const status = message === "unauthorized" ? 401 : 400;
      res.status(status).json({ error: message });
    }
  });
}
