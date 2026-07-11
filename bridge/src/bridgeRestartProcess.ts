import { spawn } from "node:child_process";
import path from "node:path";
import { PORT } from "./bridgeConfig.js";

/** Spawn a fresh bridge process after this one exits, freeing the listen port. */
export function scheduleBridgeRestartProcess(delayMs = 1200): void {
  const bridgeDir = process.cwd();
  const entry = path.join(bridgeDir, "dist", "index.js");
  const nodeCmd = process.platform === "win32" ? "node.exe" : "node";
  const port = String(PORT);

  const script = `
    setTimeout(() => {
      const { spawn } = require("node:child_process");
      const child = spawn(${JSON.stringify(nodeCmd)}, [${JSON.stringify(entry)}], {
        cwd: ${JSON.stringify(bridgeDir)},
        detached: true,
        stdio: "ignore",
        windowsHide: true,
        env: { ...process.env, PORT: ${JSON.stringify(port)} },
      });
      child.unref();
    }, ${delayMs});
  `;

  const starter = spawn(nodeCmd, ["-e", script], {
    detached: true,
    stdio: "ignore",
    windowsHide: true,
  });
  starter.unref();

  setTimeout(() => process.exit(0), 400);
}
