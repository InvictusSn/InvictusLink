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

data class PromptExchange(
    val timestampMs: Long = System.currentTimeMillis(),
    val prompt: String,
    val response: String,
    val projectId: String,
    val ok: Boolean,
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
