import fs from "node:fs";
import path from "node:path";
import { LINK_SESSIONS_PATH, PROJECTS_PATH } from "./bridgeConfig.js";
import {
  projectById,
  projects,
  type LinkSession,
  type ProjectConfig,
} from "./bridgeState.js";

export function getWorkspaceRoot(): string {
  const fromEnv = process.env.WORKSPACE_ROOT?.trim();
  if (fromEnv) return path.resolve(fromEnv);

  const raw = fs.readFileSync(PROJECTS_PATH, "utf-8");
  const parsed = JSON.parse(raw) as
    | { workspaceRoot?: string; projects?: ProjectConfig[] }
    | ProjectConfig[];
  if (!Array.isArray(parsed) && parsed.workspaceRoot) {
    return path.resolve(parsed.workspaceRoot);
  }
  const list = Array.isArray(parsed) ? parsed : parsed.projects ?? [];
  if (list[0]?.cwd) return path.resolve(list[0].cwd);
  throw new Error("No workspace root configured in projects.json");
}

export function saveLinkSessions(sessions: LinkSession[]) {
  fs.mkdirSync(path.dirname(LINK_SESSIONS_PATH), { recursive: true });
  fs.writeFileSync(LINK_SESSIONS_PATH, JSON.stringify(sessions, null, 2));
}

export function migrateLegacyLinkSessions(): LinkSession[] {
  const raw = fs.readFileSync(PROJECTS_PATH, "utf-8");
  const parsed = JSON.parse(raw) as
    | { projects?: ProjectConfig[] }
    | ProjectConfig[];
  const list = Array.isArray(parsed) ? parsed : parsed.projects ?? [];
  const workspaceRoot = getWorkspaceRoot();
  const sessions: LinkSession[] = list.map((p) => ({
    id: p.id,
    name: p.name,
    createdAt: Date.now(),
    cwd: path.resolve(p.cwd),
  }));
  if (sessions.length === 0) {
    fs.mkdirSync(workspaceRoot, { recursive: true });
    sessions.push({
      id: "default",
      name: "Session 1",
      createdAt: Date.now(),
      cwd: workspaceRoot,
    });
  }
  saveLinkSessions(sessions);
  return sessions;
}

export function loadLinkSessions(): LinkSession[] {
  if (!fs.existsSync(LINK_SESSIONS_PATH)) {
    return migrateLegacyLinkSessions();
  }
  const parsed = JSON.parse(
    fs.readFileSync(LINK_SESSIONS_PATH, "utf-8"),
  ) as LinkSession[] | { sessions?: LinkSession[] };
  const list = Array.isArray(parsed) ? parsed : parsed.sessions ?? [];
  if (list.length === 0) return migrateLegacyLinkSessions();
  return list;
}

export function resolveSessionCwd(session: LinkSession, workspaceRoot: string): string {
  if (session.cwd) return path.resolve(session.cwd);
  return path.join(workspaceRoot, session.id);
}

export function isPathInsideRoot(child: string, root: string): boolean {
  const relative = path.relative(path.resolve(root), path.resolve(child));
  return relative !== ".." && !relative.startsWith(`..${path.sep}`);
}

export function reloadProjectRegistry() {
  const workspaceRoot = getWorkspaceRoot();
  const sessions = loadLinkSessions();
  projects.length = 0;
  projectById.clear();
  for (const session of sessions) {
    const cwd = resolveSessionCwd(session, workspaceRoot);
    const project: ProjectConfig = {
      id: session.id,
      name: session.name,
      cwd,
    };
    projects.push(project);
    projectById.set(session.id, project);
  }
}

export function resolveProject(projectId?: string): ProjectConfig {
  if (!projectId) {
    const first = projects[0];
    if (!first) throw new Error("No projects configured");
    return first;
  }
  const p = projectById.get(projectId);
  if (!p) throw new Error(`Unknown projectId: ${projectId}`);
  return p;
}

reloadProjectRegistry();
