import fs from "node:fs";
import path from "node:path";
import { Agent, CursorAgentError } from "@cursor/sdk";
import {
  getActiveProvider,
  getRoutingMode,
  isLocalProviderType,
  providerKind,
  resolveModel,
  runChatCompletion,
  type ProviderRecord,
} from "./providers.js";
import { planPromptRoutes, routePrompt, type RoutePlanItem } from "./autoRouter.js";
import { buildRulesContext } from "./rules.js";
import {
  appendGrokTurn,
  buildGrokRequestMessages,
  getGrokThread,
  GROK_STABLE_SYSTEM_PROMPT,
} from "./grokThreads.js";
import {
  PROJECT_ROOT,
  TASK_TIMEOUT_MINUTES,
  TASK_TIMEOUT_MS,
} from "./bridgeConfig.js";
import {
  appendUpdateLog,
  isWorkerBusy,
  now,
  queue,
  setWorkerBusy,
  tasks,
} from "./bridgeState.js";
import { resolveProject } from "./bridgeProjects.js";
import { promptImpliesBridgeRestart, runBridgeRestartTask } from "./bridgeRestart.js";
import {
  registerTaskCancel,
  unregisterTaskCancel,
} from "./taskCancellation.js";
import {
  buildAttachmentContextNote,
  extractUrlsFromText,
  loadChatImageAttachments,
} from "./chatAttachments.js";
import {
  pickGrokReasoningEffort,
  shouldEnableGrokSearchTools,
  pickGrokImageDetail,
} from "./grokRouting.js";

const LINK_APP_UPDATE_HINT =
  "\n\n---\nUnderstood — head to **Settings → Customize & publish** to build and publish the update, then **Check for update** to install it on your phone.";

const CHANGE_VERBS =
  "(fix|add|change|update|improve|polish|tweak|modify|implement|remove|refactor|ship|release|bump|version|crash|bug)";

function isConversationalAppMention(lowered: string): boolean {
  return (
    /\bhad to (fix|change|update|work on|deal with)\b/.test(lowered) ||
    /\bi (fixed|changed|updated|was fixing|had fixed)\b/.test(lowered) ||
    /\b(was|been|just) (fixing|changing|updating|working on)\b/.test(lowered) ||
    /\bsomething with the app\b/.test(lowered) ||
    /\bwhat did (you|i) (fix|change|end up fixing)\b/.test(lowered) ||
    /\bto answer your question\b/.test(lowered) ||
    /\btesting is going\b/.test(lowered)
  );
}

function hasUiChangeTarget(lowered: string): boolean {
  return /\b(home\s+(screen|tab|page)|settings\s+(screen|tab|page)|attach(ment)?\s*(sheet|menu|button)?|snackbar|linkscreens?|linkapp|bottom\s+(strip|banner|bar)|notification\s+(strip|banner)?|white\s+strip)\b/.test(
    lowered,
  );
}

function hasDirectChangeRequest(lowered: string): boolean {
  return (
    new RegExp(
      `\\b(please|can you|could you|would you|i need you to|help me|go ahead and|let's|yeah please)\\b[^.!?]{0,60}\\b${CHANGE_VERBS}\\b`,
    ).test(lowered) ||
    new RegExp(`^\\s*${CHANGE_VERBS}\\b`).test(lowered) ||
    new RegExp(`\\b${CHANGE_VERBS}\\s+(the|this|that|a|an)\\b`).test(lowered) ||
    /\bmake\s+(the\s+)?app\b/.test(lowered)
  );
}

function hasChangeIntent(lowered: string): boolean {
  return new RegExp(`\\b${CHANGE_VERBS}\\b`).test(lowered);
}

export function promptImpliesLinkAppUpdate(prompt: string): boolean {
  const lowered = prompt.toLowerCase().trim();
  if (lowered.length < 8) return false;

  if (/\b(publish|build)\s+(the\s+)?(app\s+)?update\b/.test(lowered)) return true;
  if (/\bbuild\s+and\s+publish\b/.test(lowered)) return true;
  if (isConversationalAppMention(lowered)) return false;
  if (!hasChangeIntent(lowered)) return false;

  const uiTarget = hasUiChangeTarget(lowered);
  const appContext =
    /\b(invictus\s*link|link\s+app|the\s+app|android\s+app|mobile\s+app)\b/.test(lowered) ||
    /\b(in|on|to|for)\s+(the\s+)?app\b/.test(lowered) ||
    uiTarget ||
    (/\b(ui|screen|button|tab|sheet|dialog|snackbar)\b/.test(lowered) && /\bapp\b/.test(lowered));
  const directRequest = hasDirectChangeRequest(lowered);
  const bridgeOnly = /\bbridge\b/.test(lowered) && !appContext;

  if (uiTarget) return !bridgeOnly;
  if (appContext && directRequest) return !bridgeOnly;
  return false;
}

export function isLinkAppProject(cwd: string): boolean {
  const linkSrc = path.join(
    cwd,
    "android",
    "app",
    "src",
    "main",
    "java",
    "com",
    "invictus",
    "link",
  );
  if (fs.existsSync(linkSrc)) return true;
  const fromRoot = path.join(
    PROJECT_ROOT,
    "android",
    "app",
    "src",
    "main",
    "java",
    "com",
    "invictus",
    "link",
  );
  return path.resolve(cwd) === path.resolve(PROJECT_ROOT) && fs.existsSync(fromRoot);
}

export function shouldShowLinkAppUpdateHint(prompt: string, projectCwd: string): boolean {
  if (promptImpliesLinkAppUpdate(prompt)) return true;
  if (!isLinkAppProject(projectCwd)) return false;
  const lowered = prompt.toLowerCase();
  if (isConversationalAppMention(lowered)) return false;
  const touchesAndroid =
    /\bandroid\b/.test(lowered) ||
    /\b(linkscreens?|linkapp|mainactivity|build\.gradle)\b/.test(lowered) ||
    hasUiChangeTarget(lowered);
  return (
    touchesAndroid &&
    hasChangeIntent(lowered) &&
    hasDirectChangeRequest(lowered) &&
    !/\bbridge\b/.test(lowered)
  );
}

export function appendLinkAppUpdateHint(output: string): string {
  const lowered = output.toLowerCase();
  if (
    lowered.includes("customize & publish") ||
    lowered.includes("publish update") ||
    lowered.includes("check for update")
  ) {
    return output;
  }
  return `${output.trimEnd()}${LINK_APP_UPDATE_HINT}`;
}

/** Risky agent actions — word-boundary patterns to avoid false positives in casual chat. */
const RISKY_AGENT_PATTERNS: RegExp[] = [
  /\bdelete\b/,
  /\bremove\b/,
  /\bdrop table\b/,
  /\btruncate\b/,
  /\breset --hard\b/,
  /\bformat disk\b/,
  /\bdeploy\b/,
  /\bproduction\b/,
  /\bgit push\b/,
  /\bpush --force\b/,
  /\bforce push\b/,
  /\bpublish\b/,
  /\bshutdown\b/,
  /\breboot\b/,
];

export function needsApproval(prompt: string): boolean {
  const lowered = prompt.toLowerCase();
  return RISKY_AGENT_PATTERNS.some((p) => p.test(lowered));
}

/** Chat providers cannot touch the PC — skip approval for them. */
export async function taskRequiresApproval(prompt: string): Promise<boolean> {
  if (promptImpliesBridgeRestart(prompt)) return false;
  const routingMode = getRoutingMode();
  if (routingMode === "auto") {
    const plans = await planPromptRoutes(prompt);
    return plans.some(
      (plan) =>
        providerKind(plan.provider.type) === "agent" && needsApproval(plan.subPrompt),
    );
  }
  if (!(await willUseAgentProvider(prompt))) return false;
  return needsApproval(prompt);
}

async function willUseAgentProvider(prompt: string): Promise<boolean> {
  const routingMode = getRoutingMode();
  if (routingMode === "manual") {
    const provider = getActiveProvider();
    return provider ? providerKind(provider.type) === "agent" : true;
  }
  const plans = await planPromptRoutes(prompt);
  if (plans.length > 0) {
    return plans.some((plan) => providerKind(plan.provider.type) === "agent");
  }
  const active = getActiveProvider();
  return active ? providerKind(active.type) === "agent" : true;
}

export function getPendingApprovals() {
  return [...tasks.values()]
    .filter((t) => t.status === "awaiting_approval")
    .sort((a, b) => b.createdAt - a.createdAt);
}

export function toSafeString(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function buildAgentPrompt(
  userPrompt: string,
  outputStyle: "short" | "detailed" = "short",
  attachments: string[] = [],
  projectCwd?: string,
  rulesBlock = "",
): string {
  const workRecapHint =
    outputStyle === "detailed"
      ? "If you edited files or ran tools, add a brief recap of what changed and which files matter."
      : "If you edited files or ran tools, add a short recap of what you did.";

  const linkUpdateHint =
    projectCwd && shouldShowLinkAppUpdateHint(userPrompt, projectCwd)
      ? "The user asked for Invictus Link app changes. Acknowledge their request and end with one clear sentence: tell them to open Settings → Customize & publish → Publish update, then Check for update to install on their phone."
      : "";

  const attachmentLines =
    attachments.length > 0
      ? [
          "",
          "The user attached these files from their phone. They are saved in the project working directory at the relative paths below — read or view them as needed:",
          ...attachments.map((p) => `- ${p}`),
        ]
      : [];

  return [
    "You are helping the user from their phone via Invictus Link.",
    "Reply naturally and match the tone of their message.",
    "For greetings, small talk, thanks, or simple questions: answer directly in plain language.",
    "Do not use meta formats (for example \"## Summary\", \"What you asked\", \"What I did\") unless you actually completed coding or file work.",
    workRecapHint,
    linkUpdateHint,
    ...(rulesBlock ? ["", rulesBlock] : []),
    ...attachmentLines,
    "",
    "User message:",
    userPrompt,
  ].join("\n");
}

function mergeAssistantStreamText(
  previous: string,
  content: Array<{ type: string; text?: string }>,
): string {
  const parts = content
    .filter((b) => b.type === "text" && b.text)
    .map((b) => b.text as string);
  if (parts.length === 0) return previous;
  const snapshot = parts.join("");
  if (!previous) return snapshot;
  // SDK events are usually cumulative snapshots of the assistant message.
  if (snapshot.startsWith(previous)) return snapshot;
  if (previous.startsWith(snapshot)) return previous;
  // Occasional delta-only events — append without extra newlines.
  return previous + snapshot;
}

export async function runTask(taskId: string) {
  const task = tasks.get(taskId);
  if (!task) return;

  if (promptImpliesBridgeRestart(task.prompt)) {
    await runBridgeRestartTask(taskId);
    return;
  }

  const routingMode = getRoutingMode();

  if (routingMode === "auto") {
    const plans = await planPromptRoutes(task.prompt);
    if (plans.length > 1) {
      await runMultiRouteTask(taskId, plans);
      return;
    }
    if (plans.length === 1) {
      await runSingleRouteTask(taskId, plans[0]!);
      return;
    }
  }

  const provider =
    routingMode === "auto"
      ? (await routePrompt(task.prompt))?.provider ?? getActiveProvider()
      : getActiveProvider();

  if (!provider) {
    await failTaskNoProvider(taskId);
    return;
  }

  const routingNote =
    routingMode === "auto"
      ? (await routePrompt(task.prompt))?.reason
      : undefined;

  await runSingleRouteTask(taskId, {
    provider,
    reason: routingNote ?? provider.label,
    category: "fallback",
    subPrompt: task.prompt,
  });
}

type RouteSectionResult = {
  label: string;
  output: string;
  error?: string;
  usage?: import("./providers.js").TokenUsage;
  provider: ProviderRecord;
};

function formatMergedRouteOutput(sections: RouteSectionResult[]): string {
  if (sections.length === 1) {
    const only = sections[0]!;
    if (only.error && !only.output.trim()) return `**${only.label}:**\n\n⚠ ${only.error}`;
    if (only.error) return `**${only.label}:**\n\n${only.output}\n\n⚠ ${only.error}`;
    return only.output;
  }
  return sections
    .map((section) => {
      const body = section.error && !section.output.trim()
        ? `⚠ ${section.error}`
        : section.error
          ? `${section.output}\n\n⚠ ${section.error}`
          : section.output || "(No output)";
      return `**${section.label}:**\n\n${body}`;
    })
    .join("\n\n---\n\n");
}

async function failTaskNoProvider(taskId: string) {
  const task = tasks.get(taskId);
  if (!task) return;
  task.status = "error";
  task.error =
    "No AI provider configured. Add one in the app's Settings → AI Providers, " +
    "or set CURSOR_API_KEY in the bridge .env.";
  task.updatedAt = now();
  appendUpdateLog("task_error", {
    taskId,
    projectId: task.projectId,
    error: task.error,
  });
}

async function runSingleRouteTask(taskId: string, plan: RoutePlanItem) {
  const task = tasks.get(taskId);
  if (!task) return;

  const routingMode = getRoutingMode();
  task.status = "running";
  task.updatedAt = now();
  task.providerLabel = plan.provider.label;
  task.routingNote = plan.reason;
  appendUpdateLog("task_running", {
    taskId,
    projectId: task.projectId,
    provider: plan.provider.label,
    routingMode,
    routingNote: plan.reason,
    model: resolveModel(plan.provider),
    ...(plan.provider.type === "xai"
      ? {
          reasoningEffort: pickGrokReasoningEffort(plan.subPrompt, false),
          searchTools: shouldEnableGrokSearchTools(plan.subPrompt),
        }
      : {}),
  });

  const rulesContext = buildRulesContext(plan.provider.type, task.projectId);
  const section =
    providerKind(plan.provider.type) === "chat"
      ? await executeChatRoute(taskId, plan.provider, plan.subPrompt, rulesContext.block, {
          streamToTask: true,
        })
      : await executeAgentRoute(taskId, plan.provider, plan.subPrompt, rulesContext.block);

  const current = tasks.get(taskId);
  if (!current) return;
  if (current.status === "error" && current.error === "Stopped by user.") {
    current.updatedAt = now();
    return;
  }

  if (section.error && !section.output.trim()) {
    task.status = "error";
    task.error = section.error;
    task.output = section.output;
    appendUpdateLog("task_error", {
      taskId,
      projectId: task.projectId,
      provider: plan.provider.label,
      error: task.error,
      summaryPreview: (section.output || task.error).slice(0, 240),
    });
  } else {
    task.status = "completed";
    task.output = section.output;
    task.error = section.error;
    appendUpdateLog("task_completed", {
      taskId,
      projectId: task.projectId,
      provider: plan.provider.label,
      providerType: plan.provider.type,
      model: resolveModel(plan.provider),
      isLocal: isLocalProviderType(plan.provider.type),
      userKey: task.userKey,
      summaryPreview: section.output.slice(0, 240),
      usage: section.usage,
    });
  }
  if (section.usage) task.usage = section.usage;
  task.updatedAt = now();
}

async function runMultiRouteTask(taskId: string, plans: RoutePlanItem[]) {
  const task = tasks.get(taskId);
  if (!task) return;

  const routingMode = getRoutingMode();
  const routingNote = plans.map((p) => p.reason).join(" · ");
  task.status = "running";
  task.updatedAt = now();
  task.providerLabel = plans.map((p) => p.provider.label).join(" + ");
  task.routingNote = routingNote;
  appendUpdateLog("task_running", {
    taskId,
    projectId: task.projectId,
    provider: task.providerLabel,
    routingMode,
    routingNote,
    multiRoute: true,
    routeCount: plans.length,
  });

  const sections: RouteSectionResult[] = plans.map((plan) => ({
    label: plan.provider.label,
    output: "",
    provider: plan.provider,
  }));

  const refreshMerged = () => {
    const current = tasks.get(taskId);
    if (!current || current.status !== "running") return;
    current.output = formatMergedRouteOutput(sections);
    current.updatedAt = now();
  };

  refreshMerged();

  await Promise.all(
    plans.map(async (plan, index) => {
      const rulesContext = buildRulesContext(plan.provider.type, task.projectId);
      const section =
        providerKind(plan.provider.type) === "chat"
          ? await executeChatRoute(taskId, plan.provider, plan.subPrompt, rulesContext.block, {
              streamToTask: false,
            })
          : await executeAgentRoute(taskId, plan.provider, plan.subPrompt, rulesContext.block);
      sections[index] = section;
      refreshMerged();
      appendUpdateLog("task_completed", {
        taskId,
        projectId: task.projectId,
        provider: section.provider.label,
        providerType: section.provider.type,
        model: resolveModel(section.provider),
        isLocal: isLocalProviderType(section.provider.type),
        userKey: task.userKey,
        summaryPreview: section.output.slice(0, 240),
        usage: section.usage,
        multiRoute: true,
      });
    }),
  );

  const current = tasks.get(taskId);
  if (!current) return;

  const failures = sections.filter((s) => s.error && !s.output.trim());
  const partialFailures = sections.filter((s) => s.error && s.output.trim());
  current.output = formatMergedRouteOutput(sections);
  current.updatedAt = now();

  if (failures.length === sections.length) {
    current.status = "error";
    current.error = failures.map((s) => `${s.label}: ${s.error}`).join("; ");
    appendUpdateLog("task_error", {
      taskId,
      projectId: task.projectId,
      error: current.error,
      summaryPreview: current.output.slice(0, 240),
      multiRoute: true,
    });
    return;
  }

  current.status = "completed";
  if (partialFailures.length > 0) {
    current.error = partialFailures.map((s) => `${s.label}: ${s.error}`).join("; ");
  }
}

async function executeAgentRoute(
  taskId: string,
  provider: ProviderRecord,
  subPrompt: string,
  rulesBlock: string,
): Promise<RouteSectionResult> {
  const task = tasks.get(taskId);
  if (!task) {
    return { label: provider.label, output: "", error: "Task not found", provider };
  }

  const project = resolveProject(task.projectId);
  const outputStyle = task.outputStyle ?? "short";
  const prompt = buildAgentPrompt(
    subPrompt,
    outputStyle,
    task.attachments ?? [],
    project.cwd,
    rulesBlock,
  );

  let agent: Awaited<ReturnType<typeof Agent.create>> | undefined;
  let streamedText = "";
  try {
    if (!provider.apiKey) {
      throw new Error(
        "Missing Cursor API key. Set it in Settings → AI Providers or in the bridge .env.",
      );
    }

    agent = await Agent.create({
      apiKey: provider.apiKey,
      model: { id: resolveModel(provider) },
      local: { cwd: project.cwd },
    });
    const run = await agent.send(prompt);
    registerTaskCancel(taskId, {
      cancel: async () => {
        if (run.supports("cancel")) await run.cancel();
      },
    });

    streamedText = "";
    const streaming = (async () => {
      for await (const event of run.stream()) {
        if (event.type === "assistant") {
          const next = mergeAssistantStreamText(streamedText, event.message.content);
          if (next !== streamedText) {
            streamedText = next;
            const current = tasks.get(taskId);
            if (current && current.status === "running" && !current.routingNote?.includes(" · ")) {
              current.output = streamedText;
              current.updatedAt = now();
            }
          }
        }
      }
    })();
    void streaming.catch(() => {});

    const runWait = run.wait();
    void runWait.catch(() => {});

    let timeoutHandle: NodeJS.Timeout | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timeoutHandle = setTimeout(() => {
        reject(
          new Error(
            `Task timed out after ${TASK_TIMEOUT_MINUTES} minutes. ` +
              `The agent may still be finishing on the PC, but the bridge is free for new prompts.`,
          ),
        );
      }, TASK_TIMEOUT_MS);
    });

    let result;
    try {
      result = await Promise.race([runWait, timeout]);
    } catch (raceErr) {
      try {
        if (run.supports("cancel")) await run.cancel();
      } catch {
        // Cancellation is best-effort.
      }
      throw raceErr;
    } finally {
      clearTimeout(timeoutHandle);
    }

    if (result.status === "error") {
      return {
        label: provider.label,
        output: streamedText,
        error: `Cursor run failed (id=${result.id ?? "n/a"}).`,
        provider,
      };
    }

    let out = toSafeString(result.result) || streamedText;
    if (shouldShowLinkAppUpdateHint(subPrompt, project.cwd)) {
      out = appendLinkAppUpdateHint(out);
    }
    return { label: provider.label, output: out, provider };
  } catch (err) {
    const message =
      err instanceof CursorAgentError
        ? `Cursor agent failed to start: ${err.message}`
        : err instanceof Error
          ? err.message
          : String(err);
    return {
      label: provider.label,
      output: streamedText,
      error: message,
      provider,
    };
  } finally {
    unregisterTaskCancel(taskId);
    if (agent) {
      try {
        agent.close();
      } catch {
        // Disposal must never break the worker loop.
      }
    }
  }
}

/**
 * Chat-provider execution path (OpenAI, Claude, xAI, Gemini, Ollama, LM Studio…).
 * Conversation only — no filesystem or tool access on the PC (xAI can see attached images).
 */
async function executeChatRoute(
  taskId: string,
  provider: ProviderRecord,
  subPrompt: string,
  rulesBlock = "",
  options: { streamToTask?: boolean } = {},
): Promise<RouteSectionResult> {
  const task = tasks.get(taskId);
  if (!task) {
    return { label: provider.label, output: "", error: "Task not found", provider };
  }

  const isXai = provider.type === "xai";
  const project = resolveProject(task.projectId);
  const attachmentPaths = task.attachments ?? [];
  const urls = extractUrlsFromText(subPrompt);
  const images = isXai
    ? loadChatImageAttachments(project.cwd, attachmentPaths)
    : [];
  const attachmentNote = isXai
    ? buildAttachmentContextNote(attachmentPaths, urls, images.length)
    : attachmentPaths.length > 0
      ? `\n\n[The user attached files (${attachmentPaths.join(", ")}). They are saved on their PC, but you are a chat model and cannot open them — mention that the Cursor provider can read attachments, or switch to xAI/Grok for image understanding.]`
      : urls.length > 0
        ? `\n\n[The user included link(s): ${urls.join(", ")}. If you can browse the web, look them up; otherwise say you can't open links.]`
        : "";

  const userContent = subPrompt + attachmentNote;
  const modelId = resolveModel(provider);
  const grokThread = isXai ? getGrokThread(task.projectId, rulesBlock, modelId) : undefined;
  const grokMessages = grokThread
    ? buildGrokRequestMessages(grokThread, userContent)
    : undefined;

  const systemPrompt = isXai
    ? GROK_STABLE_SYSTEM_PROMPT
    : [
        "You are the user's assistant, reached from their phone via Invictus Link.",
        "Reply naturally and match the tone of their message.",
        "You are running as a chat model without access to the user's PC files or tools.",
        "If the user asks for file edits or code changes on their PC, explain that they should switch to the Cursor provider in Settings for that.",
        attachmentPaths.length > 0
          ? `The user attached files (${attachmentPaths.join(", ")}). They are saved on their PC, but you are a chat model and cannot open them — mention that Cursor can read files, or xAI/Grok can see images.`
          : "",
        rulesBlock,
      ]
        .filter(Boolean)
        .join("\n");

  const timeoutController = new AbortController();
  registerTaskCancel(taskId, {
    cancel: async () => {
      timeoutController.abort();
    },
  });
  const timeoutHandle = setTimeout(() => timeoutController.abort(), TASK_TIMEOUT_MS);
  try {
    const result = await runChatCompletion(
      provider,
      systemPrompt,
      userContent,
      (fullText) => {
        if (!options.streamToTask) return;
        const current = tasks.get(taskId);
        if (current && current.status === "running" && !current.routingNote?.includes(" · ")) {
          current.output = fullText;
          current.updatedAt = now();
        }
      },
      timeoutController.signal,
      isXai && grokThread
        ? {
            grokConvId: grokThread.convId,
            messages: grokMessages,
            images: images.map((img) => ({
              dataUrl: img.dataUrl,
              detail: pickGrokImageDetail(subPrompt),
            })),
            reasoningEffort: pickGrokReasoningEffort(subPrompt, images.length > 0),
            enableSearchTools: shouldEnableGrokSearchTools(subPrompt),
          }
        : {},
    );
    const output = result.text || "(No output)";
    if (isXai && grokThread) {
      appendGrokTurn(task.projectId, userContent, output);
    }
    return {
      label: provider.label,
      output,
      usage: result.usage,
      provider,
    };
  } catch (err) {
    const message = timeoutController.signal.aborted
      ? tasks.get(taskId)?.error === "Stopped by user."
        ? "Stopped by user."
        : `Task timed out after ${TASK_TIMEOUT_MINUTES} minutes.`
      : err instanceof Error
        ? err.message
        : String(err);
    return { label: provider.label, output: "", error: message, provider };
  } finally {
    unregisterTaskCancel(taskId);
    clearTimeout(timeoutHandle);
  }
}

export async function workerLoop() {
  if (isWorkerBusy) return;
  setWorkerBusy(true);
  try {
    while (queue.length > 0) {
      const taskId = queue.shift()!;
      // Skip if already updated/removed.
      if (!tasks.has(taskId)) continue;
      // Run sequentially to avoid concurrent file edits.
      await runTask(taskId);
    }
  } finally {
    setWorkerBusy(false);
  }
}
