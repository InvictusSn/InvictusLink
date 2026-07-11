import type express from "express";
import { registerDownloadRoutes } from "./routes/downloadRoutes.js";
import { registerHealthRoutes } from "./routes/healthRoutes.js";
import { registerProviderRoutes } from "./routes/providerRoutes.js";
import { registerSessionRoutes } from "./routes/sessionRoutes.js";
import { registerAttachmentRoutes } from "./routes/attachmentRoutes.js";
import { registerAuthRoutes } from "./routes/authRoutes.js";
import { registerTaskRoutes } from "./routes/taskRoutes.js";
import { registerAdminRoutes } from "./routes/adminRoutes.js";
import { registerQrRoutes } from "./routes/qrRoutes.js";
import { registerRuleRoutes } from "./routes/ruleRoutes.js";
import { registerCostRoutes } from "./routes/costRoutes.js";
import { registerExportRoutes } from "./routes/exportRoutes.js";

export function registerRoutes(app: express.Application) {
  registerDownloadRoutes(app);
  registerHealthRoutes(app);
  registerProviderRoutes(app);
  registerSessionRoutes(app);
  registerAttachmentRoutes(app);
  registerAuthRoutes(app);
  registerTaskRoutes(app);
  registerAdminRoutes(app);
  registerQrRoutes(app);
  registerRuleRoutes(app);
  registerCostRoutes(app);
  registerExportRoutes(app);
}
