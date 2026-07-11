package com.invictus.link

enum class AppScreen {
    Home,
    Activity,
    Connection,
    Settings,
}

enum class ActivitySection {
    Digest,
    Log,
    Approvals,
}

enum class WorkflowKind {
    Info,
    Prompt,
    Build,
    Success,
    Error,
}

enum class ConnectionBannerState {
    Disconnected,
    VpnOnly,
    Ready,
}

data class WorkflowEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val message: String,
    val kind: WorkflowKind = WorkflowKind.Info,
)

data class ProjectInfo(
    val id: String,
    val name: String,
)

/**
 * An AI provider configured on the PC bridge. API keys stay on the PC —
 * the phone only ever sees the masked tail (e.g. "••••a1b2").
 */
data class AiProviderInfo(
    val id: String,
    val type: String,
    val label: String,
    val model: String,
    val maskedKey: String,
    val baseUrl: String?,
    val isActive: Boolean,
    /** "agent" = full Cursor coding agent, "chat" = conversation only */
    val kind: String,
    val isLocal: Boolean,
    val isBuiltIn: Boolean,
)

data class ProviderTestResult(
    val ok: Boolean,
    val detail: String,
)

/** Bridge /health payload the app cares about. */
data class BridgeHealthInfo(
    val projects: List<ProjectInfo>,
    val activeProviderLabel: String?,
    val activeProviderType: String? = null,
    val routingMode: String = "manual",
)

/** Token usage for a single xAI prompt + response exchange. */
data class TokenUsage(
    val promptTokens: Int,
    val cachedTokens: Int,
    val completionTokens: Int,
    val costUsd: Double,
)

data class ProvidersListResult(
    val providers: List<AiProviderInfo>,
    val routingMode: String,
)

/** A file/photo the user attached to the prompt, not yet uploaded. */
data class PendingAttachment(
    val uri: android.net.Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

data class PromptExchange(
    val timestampMs: Long = System.currentTimeMillis(),
    val prompt: String,
    val response: String,
    val projectId: String,
    val ok: Boolean,
    val taskId: String? = null,
)

data class SessionInfo(
    val token: String,
    val expiresAtMs: Long,
    val startedAtMs: Long,
    val appVersionCode: Int,
)

data class PendingApprovalItem(
    val taskId: String,
    val prompt: String,
    val projectId: String,
    val createdAt: Long,
)

data class DailyDigestInfo(
    val date: String,
    val totalRuns: Int,
    val successCount: Int,
    val failureCount: Int,
    val successRate: Int,
    val timeSavedMinutes: Int,
)

/** A persistent user rule stored on the PC bridge and injected into every prompt. */
data class LinkRule(
    val id: String,
    /** "global" | "provider" | "project" */
    val scope: String,
    /** Provider type for provider scope, session/project id for project scope. */
    val targetId: String?,
    val title: String,
    val text: String,
    val enabled: Boolean,
    val vaultNotes: List<String>,
)

data class ProviderCostSummary(
    val label: String,
    val isLocal: Boolean,
    val runs: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val costUsd: Double,
    /** "priced" = live/estimated cost data; "runs_only" = count without billing API. */
    val costTracking: String = "priced",
)

data class UntrackedProviderSummary(
    val label: String,
    val runs: Int,
    val connected: Boolean = false,
)

data class CostAlertInfo(
    /** "warning" | "critical" */
    val level: String,
    val message: String,
)

data class DailyCostPoint(
    val date: String,
    val costUsd: Double,
)

data class CostDashboardInfo(
    /** Bridge-wide spend this month (all sessions/devices on this PC). */
    val todayUsd: Double,
    val monthUsd: Double,
    val monthLabel: String,
    /** Current phone/session only. */
    val deviceTodayUsd: Double = todayUsd,
    val deviceMonthUsd: Double = monthUsd,
    val byProvider: List<ProviderCostSummary>,
    val localRuns: Int,
    val estimatedSavingsUsd: Double,
    /** Estimated savings from Grok prompt caching. */
    val cacheSavingsUsd: Double = 0.0,
    /** Spend across all devices paired to this bridge. */
    val bridgeMonthUsd: Double = 0.0,
    val bridgeTodayUsd: Double = 0.0,
    /** Distinct pairing sessions that sent tasks this month (re-pairing counts separately). */
    val deviceCount: Int = 1,
    val pairingSessionCount: Int = deviceCount,
    val monthlyLimitUsd: Double?,
    val dailyLimitUsd: Double?,
    val alert: CostAlertInfo?,
    val dailyTotals: List<DailyCostPoint>,
    val untrackedProviders: List<UntrackedProviderSummary> = emptyList(),
)

/** A reusable prompt template saved on the phone. {{variables}} are filled on use. */
data class PromptTemplate(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0,
)

data class ConnectionDiagnostics(
    val usesTailscaleAddress: Boolean,
    val usesInvictusVpnAddress: Boolean,
    val tailscaleInstalled: Boolean,
    val tailscaleVpnActive: Boolean,
    val bridgeReachable: Boolean,
) {
    private val vpnRequired: Boolean
        get() = usesTailscaleAddress || usesInvictusVpnAddress

    val isReady: Boolean
        get() = bridgeReachable && (!vpnRequired || tailscaleVpnActive)

    val bannerState: ConnectionBannerState
        get() = when {
            isReady -> ConnectionBannerState.Ready
            vpnRequired && tailscaleVpnActive -> ConnectionBannerState.VpnOnly
            else -> ConnectionBannerState.Disconnected
        }

    val statusMessage: String
        get() = when {
            usesInvictusVpnAddress && !tailscaleVpnActive ->
                "Turn on WireGuard on this device to reach your PC."
            usesInvictusVpnAddress && !bridgeReachable ->
                "VPN is on, but your PC bridge isn't responding. Is the bridge running on your PC?"
            usesInvictusVpnAddress && bridgeReachable ->
                "Connected to your PC bridge."
            usesTailscaleAddress && !tailscaleVpnActive && !tailscaleInstalled ->
                "Install Tailscale to reach your PC bridge."
            usesTailscaleAddress && !tailscaleVpnActive ->
                "Open Tailscale and connect to your network."
            usesTailscaleAddress && tailscaleVpnActive && !bridgeReachable ->
                "Tailscale is on, but your PC bridge isn't responding."
            !usesTailscaleAddress && !usesInvictusVpnAddress && !bridgeReachable ->
                "Your PC isn't reachable. Check the bridge URL and that the bridge is running."
            bridgeReachable -> "Bridge reachable."
            else -> "Checking connection…"
        }

    val showOpenTailscale: Boolean
        get() = usesTailscaleAddress && !tailscaleVpnActive
}
