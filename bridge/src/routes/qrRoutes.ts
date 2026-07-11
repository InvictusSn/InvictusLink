import type express from "express";
import qrcode from "qrcode";
import { DEFAULT_QR_URL } from "../bridgeConfig.js";

export function registerQrRoutes(app: express.Application) {
  app.get("/qr", async (req, res) => {
    try {
      const urlParam = req.query.url;
      const url =
        typeof urlParam === "string" && urlParam.trim()
          ? urlParam.trim()
          : DEFAULT_QR_URL;

      const svg = await qrcode.toString(url, {
        type: "svg",
        errorCorrectionLevel: "M",
        margin: 2,
        scale: 8,
      });

      res.setHeader("content-type", "text/html; charset=utf-8");
      res.send(`<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>InvictusLink QR</title>
    <style>
      body { margin: 0; font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; background: #0b0b0f; color: #e9e9f1; display:flex; justify-content:center; align-items:center; min-height:100vh; }
      .wrap { width: min(520px, 92vw); text-align: center; }
      .hint { opacity: 0.85; font-size: 13px; margin-top: 10px; }
      code { display:block; margin-top: 10px; font-size: 12px; opacity: 0.8; word-break: break-all; }
      .qr { display:flex; justify-content:center; }
      svg { background: white; padding: 14px; border-radius: 10px; }
    </style>
  </head>
  <body>
    <div class="wrap">
      <div class="qr">${svg}</div>
      <div class="hint">Scan to install InvictusLink</div>
      <code>${url}</code>
    </div>
  </body>
</html>`);
    } catch (err) {
      res.status(500).send(
        `Failed to generate QR: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
  });
}
