import fs from "node:fs";
import type express from "express";
import { createHash, randomUUID } from "node:crypto";
import {
  BRIDGE_TOKEN,
  PERMANENT_SESSION_EXPIRES_AT,
  SESSIONS_PATH,
} from "./bridgeConfig.js";
import {
  now,
  sessionByToken,
  type SessionRecord,
} from "./bridgeState.js";

export function loadSessionsFromDisk() {
  try {
    const raw = fs.readFileSync(SESSIONS_PATH, "utf-8");
    const parsed = JSON.parse(raw) as { sessions?: SessionRecord[] };
    for (const s of parsed.sessions ?? []) {
      if (!s?.token) continue;
      if (!Number.isFinite(s.expiresAt)) continue;
      if (s.expiresAt <= now()) continue;
      sessionByToken.set(s.token, s);
    }
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code !== "ENOENT") {
      // Ignore parse/read errors and continue with empty session map.
    }
  }
}

export function saveSessionsToDisk() {
  const sessions = [...sessionByToken.values()];
  fs.writeFileSync(SESSIONS_PATH, JSON.stringify({ sessions }, null, 2), "utf-8");
}

export function pruneExpiredSessions() {
  const t = now();
  let changed = false;
  for (const [token, s] of sessionByToken.entries()) {
    if (s.expiresAt <= t) {
      sessionByToken.delete(token);
      changed = true;
    }
  }
  if (changed) saveSessionsToDisk();
}

export function createSessionToken(): SessionRecord {
  pruneExpiredSessions();
  const createdAt = now();
  const expiresAt = PERMANENT_SESSION_EXPIRES_AT;
  const token = `sess_${randomUUID().replace(/-/g, "")}`;
  const rec: SessionRecord = { token, createdAt, expiresAt };
  sessionByToken.set(token, rec);
  saveSessionsToDisk();
  return rec;
}

export function getBearerTokenFromReq(req: express.Request): string {
  const auth =
    (req.headers["authorization"] ?? "").toString().trim() ||
    (req.headers["x-bridge-token"] ?? "").toString().trim();
  if (!auth) return "";
  return auth.startsWith("Bearer ") ? auth.slice("Bearer ".length).trim() : auth;
}

export function requireAuth(req: express.Request): string {
  const auth =
    (req.headers["authorization"] ?? "").toString().trim() ||
    (req.headers["x-bridge-token"] ?? "").toString().trim();

  if (!BRIDGE_TOKEN) {
    // Fail closed: an unset BRIDGE_TOKEN must never mean "everyone is welcome".
    throw new Error("unauthorized");
  }

  pruneExpiredSessions();
  const bearer = getBearerTokenFromReq(req);
  const ok =
    auth === BRIDGE_TOKEN ||
    auth === `Bearer ${BRIDGE_TOKEN}` ||
    sessionByToken.has(bearer);
  if (!ok) throw new Error("unauthorized");
  return "";
}

export function requireSessionAuth(req: express.Request): SessionRecord {
  pruneExpiredSessions();
  const bearer = getBearerTokenFromReq(req);
  if (!bearer) throw new Error("unauthorized");
  const session = sessionByToken.get(bearer);
  if (!session) throw new Error("unauthorized");
  return session;
}

/**
 * Anonymous per-device identity for spend attribution. Each paired phone has
 * its own session token; hashing it (never storing the raw token) lets the
 * cost dashboard split "your spending" from other devices sharing this bridge
 * or the same provider key. Bridge-token callers group under "admin".
 */
export function getUserKeyFromReq(req: express.Request): string {
  const bearer = getBearerTokenFromReq(req);
  if (!bearer) return "unknown";
  if (bearer === BRIDGE_TOKEN) return "admin";
  return createHash("sha256").update(bearer).digest("hex").slice(0, 12);
}
