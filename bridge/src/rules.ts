/**
 * Rules — persistent user instructions injected into every prompt.
 *
 * Scopes:
 *  - "global":   applies to every prompt
 *  - "provider": applies when the resolved provider matches targetId (provider type)
 *  - "project":  applies when the task's session/project matches targetId
 *
 * Rules can reference Obsidian vault notes (explicit relative paths or
 * [[wikilinks]] in the rule text). Notes are resolved on the PC, size-capped,
 * and only included while the referencing rule is enabled — the vault is
 * never bulk-dumped into requests.
 */

import fs from "node:fs";
import path from "node:path";
import { randomUUID } from "node:crypto";

export type RuleScope = "global" | "provider" | "project";

export type RuleRecord = {
  id: string;
  scope: RuleScope;
  /** Provider type for "provider" scope, projectId for "project" scope. */
  targetId?: string;
  title: string;
  text: string;
  enabled: boolean;
  /** Vault-relative note paths to include as context (e.g. "10-Projects/invictus/Link.md"). */
  vaultNotes?: string[];
  createdAt: number;
};

type RulesStore = { rules: RuleRecord[] };

const RULES_PATH =
  process.env.RULES_PATH ?? path.join(process.cwd(), "config", "rules.json");

/**
 * Optional Obsidian vault integration. Set OBSIDIAN_VAULT_PATH in the bridge
 * .env to let rules reference vault notes; unset, vault lookups are skipped.
 */
const VAULT_PATH = process.env.OBSIDIAN_VAULT_PATH ?? "";

/** Per-note and total caps keep vault context lean. */
const NOTE_MAX_CHARS = 4000;
const TOTAL_VAULT_MAX_CHARS = 8000;

function loadStore(): RulesStore {
  try {
    const raw = fs.readFileSync(RULES_PATH, "utf-8");
    const parsed = JSON.parse(raw) as RulesStore;
    if (!Array.isArray(parsed.rules)) return { rules: [] };
    return parsed;
  } catch {
    return { rules: [] };
  }
}

function saveStore(store: RulesStore) {
  fs.mkdirSync(path.dirname(RULES_PATH), { recursive: true });
  fs.writeFileSync(RULES_PATH, JSON.stringify(store, null, 2), "utf-8");
}

export function listRules(): RuleRecord[] {
  return loadStore().rules;
}

export function addRule(input: {
  scope: RuleScope;
  targetId?: string;
  title: string;
  text: string;
  vaultNotes?: string[];
}): RuleRecord {
  if (input.scope !== "global" && !input.targetId?.trim()) {
    throw new Error(`${input.scope} rules need a target`);
  }
  const store = loadStore();
  const rec: RuleRecord = {
    id: `rule_${randomUUID().slice(0, 8)}`,
    scope: input.scope,
    targetId: input.scope === "global" ? undefined : input.targetId?.trim(),
    title: input.title.trim(),
    text: input.text.trim(),
    enabled: true,
    vaultNotes: input.vaultNotes?.map((n) => n.trim()).filter(Boolean),
    createdAt: Date.now(),
  };
  store.rules.push(rec);
  saveStore(store);
  return rec;
}

export function updateRule(
  id: string,
  patch: Partial<Pick<RuleRecord, "title" | "text" | "enabled" | "vaultNotes" | "scope" | "targetId">>,
): RuleRecord {
  const store = loadStore();
  const rec = store.rules.find((r) => r.id === id);
  if (!rec) throw new Error("Rule not found");
  if (patch.title !== undefined) rec.title = patch.title.trim();
  if (patch.text !== undefined) rec.text = patch.text.trim();
  if (patch.enabled !== undefined) rec.enabled = patch.enabled;
  if (patch.scope !== undefined) rec.scope = patch.scope;
  if (patch.targetId !== undefined) rec.targetId = patch.targetId.trim() || undefined;
  if (patch.vaultNotes !== undefined) {
    rec.vaultNotes = patch.vaultNotes.map((n) => n.trim()).filter(Boolean);
  }
  saveStore(store);
  return rec;
}

export function deleteRule(id: string): boolean {
  const store = loadStore();
  const index = store.rules.findIndex((r) => r.id === id);
  if (index < 0) return false;
  store.rules.splice(index, 1);
  saveStore(store);
  return true;
}

function isInsideVault(candidate: string): boolean {
  if (!VAULT_PATH) return false;
  const relative = path.relative(path.resolve(VAULT_PATH), path.resolve(candidate));
  return relative !== ".." && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative);
}

/** Filename → vault-relative path index, cached briefly to keep prompts fast. */
let vaultIndexCache: { builtAt: number; byName: Map<string, string> } | undefined;
const VAULT_INDEX_TTL_MS = 5 * 60_000;

function getVaultIndex(): Map<string, string> {
  if (vaultIndexCache && Date.now() - vaultIndexCache.builtAt < VAULT_INDEX_TTL_MS) {
    return vaultIndexCache.byName;
  }
  const byName = new Map<string, string>();
  const walk = (dir: string, rel: string) => {
    let entries: fs.Dirent[];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      if (entry.name.startsWith(".")) continue;
      const childRel = rel ? `${rel}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        walk(path.join(dir, entry.name), childRel);
      } else if (entry.name.toLowerCase().endsWith(".md")) {
        const key = entry.name.slice(0, -3).toLowerCase();
        if (!byName.has(key)) byName.set(key, childRel);
      }
    }
  };
  walk(VAULT_PATH, "");
  vaultIndexCache = { builtAt: Date.now(), byName };
  return byName;
}

/** Resolve a note reference (relative path or bare note name) to vault content. */
function readVaultNote(ref: string): { path: string; content: string } | undefined {
  if (!VAULT_PATH) return undefined;
  const cleaned = ref.replace(/\\/g, "/").replace(/^\/+/, "").trim();
  if (!cleaned) return undefined;
  const candidates = [cleaned, `${cleaned}.md`];
  for (const candidate of candidates) {
    const abs = path.join(VAULT_PATH, candidate);
    if (!isInsideVault(abs)) continue;
    try {
      const stat = fs.statSync(abs);
      if (stat.isFile()) {
        const content = fs.readFileSync(abs, "utf-8").slice(0, NOTE_MAX_CHARS);
        return { path: candidate, content };
      }
    } catch {
      // Try next candidate.
    }
  }
  // Bare note name — look it up in the vault index.
  const fromIndex = getVaultIndex().get(cleaned.toLowerCase().replace(/\.md$/, ""));
  if (fromIndex) {
    try {
      const content = fs
        .readFileSync(path.join(VAULT_PATH, fromIndex), "utf-8")
        .slice(0, NOTE_MAX_CHARS);
      return { path: fromIndex, content };
    } catch {
      return undefined;
    }
  }
  return undefined;
}

/** Extract [[wikilinks]] from rule text. */
function extractWikilinks(text: string): string[] {
  const links: string[] = [];
  const pattern = /\[\[([^\]|#]+)(?:[|#][^\]]*)?\]\]/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    const name = match[1]?.trim();
    if (name) links.push(name);
  }
  return links;
}

export type RulesContext = {
  /** Ready-to-inject prompt block; empty string when no rules apply. */
  block: string;
  ruleCount: number;
};

/**
 * Build the standing-rules block for a task. Fast path: single small JSON read;
 * vault notes only touched when an applicable rule references them.
 */
export function buildRulesContext(
  providerType: string | undefined,
  projectId: string | undefined,
): RulesContext {
  const rules = listRules().filter((r) => {
    if (!r.enabled) return false;
    if (r.scope === "global") return true;
    if (r.scope === "provider") return !!providerType && r.targetId === providerType;
    if (r.scope === "project") return !!projectId && r.targetId === projectId;
    return false;
  });
  if (rules.length === 0) return { block: "", ruleCount: 0 };

  const lines: string[] = ["[Standing rules from the user — follow these on every reply]"];
  const noteRefs: string[] = [];
  for (const rule of rules) {
    lines.push(`- ${rule.title}: ${rule.text}`);
    if (rule.vaultNotes) noteRefs.push(...rule.vaultNotes);
    noteRefs.push(...extractWikilinks(rule.text));
  }

  // Resolve referenced vault notes (deduped, size-capped).
  const seen = new Set<string>();
  let vaultChars = 0;
  const noteBlocks: string[] = [];
  for (const ref of noteRefs) {
    if (vaultChars >= TOTAL_VAULT_MAX_CHARS) break;
    const note = readVaultNote(ref);
    if (!note || seen.has(note.path)) continue;
    seen.add(note.path);
    const remaining = TOTAL_VAULT_MAX_CHARS - vaultChars;
    const content = note.content.slice(0, remaining);
    vaultChars += content.length;
    noteBlocks.push(`--- Vault note: ${note.path} ---\n${content}`);
  }
  if (noteBlocks.length > 0) {
    lines.push("", "[Context from the user's Obsidian vault — referenced by their rules]");
    lines.push(...noteBlocks);
  }

  return { block: lines.join("\n"), ruleCount: rules.length };
}
