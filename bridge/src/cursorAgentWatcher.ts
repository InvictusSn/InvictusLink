import fs from "node:fs";
import os from "node:os";
import path from "node:path";

type CompletionCallback = (details: {
  transcriptId: string;
  status: string;
  summaryPreview: string;
}) => void;

function walkTranscripts(root: string, onFile: (filePath: string) => void) {
  if (!fs.existsSync(root)) return;
  const walk = (dir: string) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile() && entry.name.endsWith(".jsonl") && full.includes("agent-transcripts")) {
        onFile(full);
      }
    }
  };
  walk(root);
}

function transcriptIdFromPath(filePath: string): string {
  const base = path.basename(filePath, ".jsonl");
  if (base && base !== "subagents") return base;
  const parent = path.basename(path.dirname(filePath));
  return parent || base || "unknown";
}

function extractUserPreview(line: string): string | null {
  if (!line.includes('"role":"user"') && !line.includes("<user_query>")) return null;
  const match = line.match(/<user_query>\s*([\s\S]*?)\s*<\/user_query>/);
  if (match?.[1]) return match[1].trim().slice(0, 180);
  const textMatch = line.match(/"text"\s*:\s*"([^"]{1,200})"/);
  return textMatch?.[1]?.replace(/\\n/g, " ")?.trim() ?? null;
}

export class CursorAgentWatcher {
  private offsets = new Map<string, number>();
  private lastUserPreview = new Map<string, string>();
  private pollTimer: NodeJS.Timeout | null = null;
  private emitted = new Set<string>();

  start(onComplete: CompletionCallback) {
    this.stop();
    const root = path.join(os.homedir(), ".cursor", "projects");
    walkTranscripts(root, (filePath) => {
      if (fs.existsSync(filePath)) {
        this.offsets.set(filePath, fs.statSync(filePath).size);
      }
    });

    const scanFile = (filePath: string) => {
      if (!fs.existsSync(filePath)) return;
      const stat = fs.statSync(filePath);
      const prev = this.offsets.get(filePath) ?? 0;
      if (stat.size < prev) this.offsets.set(filePath, 0);
      const start = this.offsets.get(filePath) ?? 0;
      if (stat.size <= start) return;

      const fd = fs.openSync(filePath, "r");
      try {
        const len = stat.size - start;
        const buf = Buffer.alloc(len);
        fs.readSync(fd, buf, 0, len, start);
        this.offsets.set(filePath, stat.size);
        const transcriptId = transcriptIdFromPath(filePath);
        for (const line of buf.toString("utf8").split("\n")) {
          if (!line.trim()) continue;
          const userPreview = extractUserPreview(line);
          if (userPreview) this.lastUserPreview.set(filePath, userPreview);

          if (!line.includes('"type":"turn_ended"') && !line.includes('"type": "turn_ended"')) {
            continue;
          }
          let status = "unknown";
          try {
            const parsed = JSON.parse(line) as { status?: string };
            status = parsed.status ?? "unknown";
          } catch {
            const statusMatch = line.match(/"status"\s*:\s*"([^"]+)"/);
            status = statusMatch?.[1] ?? "unknown";
          }
          const key = `${transcriptId}:${stat.mtimeMs}:${status}`;
          if (this.emitted.has(key)) continue;
          this.emitted.add(key);
          if (this.emitted.size > 500) {
            const first = this.emitted.values().next().value;
            if (first) this.emitted.delete(first);
          }
          onComplete({
            transcriptId,
            status,
            summaryPreview: this.lastUserPreview.get(filePath) ?? "Cursor agent finished on PC",
          });
        }
      } finally {
        fs.closeSync(fd);
      }
    };

    const scanAll = () => walkTranscripts(root, scanFile);
    this.pollTimer = setInterval(scanAll, 2000);
  }

  stop() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.offsets.clear();
    this.lastUserPreview.clear();
  }
}
