import "dotenv/config";

import express from "express";
import cors from "cors";
import path from "node:path";
import { CursorAgentWatcher } from "./cursorAgentWatcher.js";
import { PORT } from "./bridgeConfig.js";
import {
  acquireBridgeLock,
  appendUpdateLog,
  releaseBridgeLock,
} from "./bridgeState.js";
import { loadSessionsFromDisk } from "./bridgeAuth.js";
import { registerRoutes } from "./registerRoutes.js";
import { ensureUpdateLogMigrated } from "./updateLogMigration.js";
import { listProviderRecords } from "./providers.js";
import { refreshXaiPricing } from "./xaiPricing.js";

const app = express();
app.use(cors());
app.use(express.json({ limit: "200kb" }));

registerRoutes(app);

app.use(express.static(path.join(process.cwd(), "public")));

acquireBridgeLock();
ensureUpdateLogMigrated();
loadSessionsFromDisk();

// Warm the xAI model catalog so grok-latest resolves to the live frontier on first prompt.
refreshXaiPricing(listProviderRecords().find((p) => p.type === "xai")?.apiKey, true);

const cursorAgentWatcher = new CursorAgentWatcher();
cursorAgentWatcher.start(({ transcriptId, status, summaryPreview }) => {
  appendUpdateLog("cursor_agent_completed", {
    transcriptId,
    status,
    summaryPreview,
  });
});

process.on("exit", () => {
  cursorAgentWatcher.stop();
  releaseBridgeLock();
});
process.on("SIGINT", () => {
  cursorAgentWatcher.stop();
  releaseBridgeLock();
  process.exit(0);
});
process.on("SIGTERM", () => {
  cursorAgentWatcher.stop();
  releaseBridgeLock();
  process.exit(0);
});

app.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`InvictusLink bridge listening on http://localhost:${PORT}`);
});
