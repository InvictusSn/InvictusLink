import type express from "express";
import { z } from "zod";
import { requireAuth } from "../bridgeAuth.js";
import { appendUpdateLog } from "../bridgeState.js";
import { addRule, deleteRule, listRules, updateRule } from "../rules.js";

const RuleCreateSchema = z.object({
  scope: z.enum(["global", "provider", "project"]),
  targetId: z.string().min(1).max(200).optional(),
  title: z.string().min(1).max(80),
  text: z.string().min(1).max(4000),
  vaultNotes: z.array(z.string().min(1).max(300)).max(5).optional(),
});

const RuleUpdateSchema = z.object({
  scope: z.enum(["global", "provider", "project"]).optional(),
  targetId: z.string().max(200).optional(),
  title: z.string().min(1).max(80).optional(),
  text: z.string().min(1).max(4000).optional(),
  enabled: z.boolean().optional(),
  vaultNotes: z.array(z.string().min(1).max(300)).max(5).optional(),
});

function fail(res: express.Response, err: unknown) {
  const message =
    err instanceof z.ZodError ? "Invalid rule details" : err instanceof Error ? err.message : String(err);
  const status = message === "unauthorized" ? 401 : message === "Rule not found" ? 404 : 400;
  res.status(status).json({ error: message });
}

export function registerRuleRoutes(app: express.Application) {
  app.get("/api/rules", (req, res) => {
    try {
      requireAuth(req);
      res.json({ rules: listRules() });
    } catch (err) {
      fail(res, err);
    }
  });

  app.post("/api/rules", (req, res) => {
    try {
      requireAuth(req);
      const parsed = RuleCreateSchema.parse(req.body ?? {});
      const rec = addRule(parsed);
      appendUpdateLog("rule_added", {
        ruleId: rec.id,
        scope: rec.scope,
        title: rec.title,
        ip: req.ip,
      });
      res.status(201).json({ rule: rec });
    } catch (err) {
      fail(res, err);
    }
  });

  app.patch("/api/rules/:id", (req, res) => {
    try {
      requireAuth(req);
      const parsed = RuleUpdateSchema.parse(req.body ?? {});
      const rec = updateRule((req.params.id ?? "").trim(), parsed);
      appendUpdateLog("rule_updated", {
        ruleId: rec.id,
        enabled: rec.enabled,
        ip: req.ip,
      });
      res.json({ rule: rec });
    } catch (err) {
      fail(res, err);
    }
  });

  app.delete("/api/rules/:id", (req, res) => {
    try {
      requireAuth(req);
      const id = (req.params.id ?? "").trim();
      const removed = deleteRule(id);
      if (!removed) {
        res.status(404).json({ error: "rule not found" });
        return;
      }
      appendUpdateLog("rule_deleted", { ruleId: id, ip: req.ip });
      res.json({ ok: true, id });
    } catch (err) {
      fail(res, err);
    }
  });
}
