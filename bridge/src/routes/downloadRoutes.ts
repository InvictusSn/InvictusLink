import fs from "node:fs";
import type express from "express";
import {
  APK_FILENAME,
  APK_PATH,
  LATEST_JSON_PATH,
  PULSE_APK_FILENAME,
  PULSE_APK_PATH,
  PULSE_LATEST_JSON_PATH,
  getRequestBaseUrl,
} from "../bridgeConfig.js";

function serveApkDownload(_req: express.Request, res: express.Response) {
  if (!fs.existsSync(APK_PATH)) {
    res.status(404).json({ error: "apk not found" });
    return;
  }
  res.setHeader("Content-Type", "application/vnd.android.package-archive");
  res.setHeader(
    "Content-Disposition",
    `attachment; filename="${APK_FILENAME}"`,
  );
  res.sendFile(APK_PATH);
}

function servePulseApkDownload(_req: express.Request, res: express.Response) {
  if (!fs.existsSync(PULSE_APK_PATH)) {
    res.status(404).json({ error: "pulse apk not found" });
    return;
  }
  res.setHeader("Content-Type", "application/vnd.android.package-archive");
  res.setHeader(
    "Content-Disposition",
    `attachment; filename="${PULSE_APK_FILENAME}"`,
  );
  res.sendFile(PULSE_APK_PATH);
}

export function registerDownloadRoutes(app: express.Application) {
  // Serve update artifacts with the caller's reachable host (WireGuard, LAN, etc.).
  app.get("/download/latest.json", (req, res) => {
    try {
      if (!fs.existsSync(LATEST_JSON_PATH)) {
        res.status(404).json({ error: "update manifest not found" });
        return;
      }
      const manifest = JSON.parse(
        fs.readFileSync(LATEST_JSON_PATH, "utf-8").replace(/^\uFEFF/, ""),
      ) as {
        versionCode?: number;
        versionName?: string;
        apkUrl?: string;
      };
      const baseUrl = getRequestBaseUrl(req);
      manifest.apkUrl = `${baseUrl}/download/${APK_FILENAME}`;
      res.json(manifest);
    } catch (err) {
      res.status(500).json({
        error: err instanceof Error ? err.message : String(err),
      });
    }
  });

  app.get("/download/InvictusLink.apk", serveApkDownload);

  app.get("/download/pulse-latest.json", (req, res) => {
    try {
      if (!fs.existsSync(PULSE_LATEST_JSON_PATH)) {
        res.status(404).json({ error: "pulse update manifest not found" });
        return;
      }
      const manifest = JSON.parse(
        fs.readFileSync(PULSE_LATEST_JSON_PATH, "utf-8").replace(/^\uFEFF/, ""),
      ) as {
        versionCode?: number;
        versionName?: string;
        apkUrl?: string;
      };
      const baseUrl = getRequestBaseUrl(req);
      manifest.apkUrl = `${baseUrl}/download/${PULSE_APK_FILENAME}`;
      res.json(manifest);
    } catch (err) {
      res.status(500).json({
        error: err instanceof Error ? err.message : String(err),
      });
    }
  });

  app.get("/download/InvictusPulse.apk", servePulseApkDownload);
}
