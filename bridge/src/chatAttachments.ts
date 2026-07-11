/**
 * Helpers for feeding phone attachments / pasted links into chat providers
 * (especially xAI Grok vision + web research).
 */

import fs from "node:fs";
import path from "node:path";

const IMAGE_EXT = new Set([".jpg", ".jpeg", ".png", ".webp", ".gif"]);
const MAX_IMAGE_BYTES = 18 * 1024 * 1024; // stay under xAI's ~20 MiB limit

export type ChatImageAttachment = {
  relativePath: string;
  mimeType: string;
  /** data:image/...;base64,... */
  dataUrl: string;
};

const URL_RE = /https?:\/\/[^\s<>"')\]]+/gi;

export function extractUrlsFromText(text: string): string[] {
  const found = text.match(URL_RE) ?? [];
  const cleaned = found.map((u) => u.replace(/[.,;:!?]+$/g, ""));
  return [...new Set(cleaned)];
}

function mimeForExt(ext: string): string | null {
  switch (ext) {
    case ".jpg":
    case ".jpeg":
      return "image/jpeg";
    case ".png":
      return "image/png";
    case ".webp":
      return "image/webp";
    case ".gif":
      return "image/gif";
    default:
      return null;
  }
}

/** Load image attachments from the project folder as base64 data URLs. */
export function loadChatImageAttachments(
  projectCwd: string,
  relativePaths: string[],
): ChatImageAttachment[] {
  const out: ChatImageAttachment[] = [];
  for (const relativePath of relativePaths) {
    const ext = path.extname(relativePath).toLowerCase();
    const mime = mimeForExt(ext);
    if (!mime || !IMAGE_EXT.has(ext)) continue;

    const absolute = path.resolve(projectCwd, relativePath);
    const root = path.resolve(projectCwd);
    if (!absolute.startsWith(root + path.sep) && absolute !== root) continue;
    if (!fs.existsSync(absolute)) continue;

    const stat = fs.statSync(absolute);
    if (!stat.isFile() || stat.size <= 0 || stat.size > MAX_IMAGE_BYTES) continue;

    const bytes = fs.readFileSync(absolute);
    out.push({
      relativePath,
      mimeType: mime,
      dataUrl: `data:${mime};base64,${bytes.toString("base64")}`,
    });
  }
  return out;
}

/** Text note stored in the Grok thread (no base64 bloat). */
export function buildAttachmentContextNote(
  relativePaths: string[],
  urls: string[],
  imagesSent: number,
): string {
  const lines: string[] = [];
  if (imagesSent > 0) {
    lines.push(
      `[The user attached ${imagesSent} image${imagesSent === 1 ? "" : "s"} that you can see in this message.]`,
    );
  }
  const nonImages = relativePaths.filter((p) => {
    const ext = path.extname(p).toLowerCase();
    return !IMAGE_EXT.has(ext);
  });
  if (nonImages.length > 0) {
    lines.push(
      `[Also attached (not images — mention if you can't open them): ${nonImages.join(", ")}]`,
    );
  }
  if (urls.length > 0) {
    lines.push(
      "[The user included link(s). Use web_search / browse them and give research, context, or your opinion:]",
      ...urls.map((u) => `- ${u}`),
    );
  }
  return lines.length ? `\n\n${lines.join("\n")}` : "";
}
