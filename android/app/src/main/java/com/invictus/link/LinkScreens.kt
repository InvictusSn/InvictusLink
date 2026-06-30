package com.invictus.link

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun InvictusAppShell(
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    pendingCount: Int,
    snackbarHostState: SnackbarHostState,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            InvictusBottomBar(
                currentTab = currentTab,
                onTabSelected = onTabSelected,
                pendingCount = pendingCount,
            )
        }
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

private fun formatElapsed(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prompt: String,
    onPromptChange: (String) -> Unit,
    sending: Boolean,
    status: String,
    result: String,
    elapsedSec: Int,
    connectionOk: Boolean,
    projects: List<ProjectInfo>,
    selectedProjectId: String?,
    onProjectSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    history: List<PromptExchange>,
    onResend: (PromptExchange) -> Unit,
    onClearHistory: () -> Unit,
    onSend: () -> Unit,
    onConnectFirst: () -> Unit,
) {
    var responseExpanded by remember { mutableStateOf(true) }
    val responseText = when {
        sending -> {
            val base = status.ifBlank { "Thinking" }
            "$base… ${formatElapsed(elapsedSec)}"
        }
        result.isNotBlank() -> result
        status.isNotBlank() && status != "Done" -> status
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .invictusScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InvictusSectionHeader(
                title = "Home",
                subtitle = if (connectionOk) "Message your PC agent" else "Pair to get started",
                modifier = Modifier.weight(1f),
            )
            if (connectionOk) {
                SessionSelector(
                    modifier = Modifier.widthIn(max = 180.dp),
                    projects = projects,
                    selectedProjectId = selectedProjectId,
                    enabled = !sending,
                    onProjectSelected = onProjectSelected,
                    onNewSession = onNewSession,
                    onDeleteSession = onDeleteSession,
                )
            }
        }

        if (!connectionOk) {
            EmptyStateCard(
                title = "Connect to your PC",
                message = "Turn on WireGuard, then open Connection to pair with your PC bridge.",
                actionLabel = "Go to Connection",
                onAction = onConnectFirst,
            )
        } else {
            InvictusStatusChip(
                label = "Connected — ready to send",
                tone = StatusTone.Success,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp, max = 200.dp)
                .invictusCardSurface(background = InvictusBrand.Navy, borderColor = InvictusBrand.HairlineStrong),
        ) {
            InvictusPromptLogoBackground(modifier = Modifier.fillMaxSize())
            InvictusTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxSize(),
                placeholder = "Message your PC agent…",
                enabled = !sending && connectionOk,
                transparentBackground = true,
            )
        }

        InvictusPrimaryButton(
            onClick = onSend,
            modifier = Modifier.fillMaxWidth(),
            enabled = !sending && prompt.isNotBlank() && connectionOk,
        ) {
            Text(if (sending) "Sending…" else "Send to PC")
        }

        AnimatedVisibility(
            visible = sending,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            InvictusSendingIndicator()
        }

        if (sending && elapsedSec >= 540) {
            Text(
                "Long task — the bridge stops waiting at 10 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Warning,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .invictusCardSurface()
                .padding(InvictusDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Agent response", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
                InvictusTextButton(onClick = { responseExpanded = !responseExpanded }) {
                    Icon(
                        if (responseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (responseExpanded) "Collapse" else "Expand",
                        tint = InvictusBrand.Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = responseExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        if (responseText.isBlank()) {
                            Text(
                                "Ask anything — the agent's reply will show up here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.Muted,
                            )
                        } else {
                            Text(
                                responseText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InvictusBrand.White,
                            )
                        }
                    }
                    if (history.isNotEmpty()) {
                        item {
                            HorizontalDivider(color = InvictusBrand.Hairline)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "History (${history.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InvictusBrand.Muted,
                                )
                                InvictusTextButton(onClick = onClearHistory) {
                                    Text("Clear", color = InvictusBrand.Muted)
                                }
                            }
                        }
                        items(history.asReversed()) { exchange ->
                            HistoryItem(
                                exchange = exchange,
                                sending = sending,
                                onResend = { onResend(exchange) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSelector(
    projects: List<ProjectInfo>,
    selectedProjectId: String?,
    enabled: Boolean,
    onProjectSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectInfo?>(null) }
    val selected = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    val label = selected?.name ?: "No session"

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Session?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        expanded = false
                        onDeleteSession(session.id)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Box(modifier = modifier) {
        FilterChip(
            selected = true,
            enabled = enabled,
            onClick = { expanded = true },
            label = {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Choose session",
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text("New Session", fontWeight = FontWeight.SemiBold)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    expanded = false
                    onNewSession()
                },
            )
            if (projects.isNotEmpty()) {
                HorizontalDivider()
            }
            projects.forEach { project ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (project.id == selected?.id) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = InvictusBrand.Success,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Spacer(modifier = Modifier.size(18.dp))
                            }
                            Text(
                                project.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = {
                                    expanded = false
                                    pendingDelete = project
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete session",
                                    modifier = Modifier.size(16.dp),
                                    tint = InvictusBrand.Muted,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onProjectSelected(project.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    exchange: PromptExchange,
    sending: Boolean,
    onResend: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val time = remember(exchange.timestampMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(exchange.timestampMs))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .invictusCardSurface(background = InvictusBrand.NavySurface)
            .clickable { expanded = !expanded }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                time + if (exchange.projectId.isNotBlank()) " · ${exchange.projectId}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = InvictusBrand.Muted,
            )
            InvictusStatusChip(
                label = if (exchange.ok) "Completed" else "Failed",
                tone = if (exchange.ok) StatusTone.Success else StatusTone.Error,
                showDot = false,
            )
        }
        Text(
            exchange.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = InvictusBrand.White,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = InvictusBrand.Hairline)
                Text(
                    exchange.response.ifBlank { "(No output)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                InvictusSecondaryButton(
                    onClick = onResend,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Resend")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ActivityScreen(
    section: ActivitySection,
    onSectionChange: (ActivitySection) -> Unit,
    loadingDigest: Boolean,
    digest: DailyDigestInfo?,
    workflowLog: List<WorkflowEntry>,
    logFilter: WorkflowKind?,
    onLogFilterChange: (WorkflowKind?) -> Unit,
    onRefreshDigest: () -> Unit,
    onRefreshLog: () -> Unit,
    loadingApprovals: Boolean,
    approvals: List<PendingApprovalItem>,
    approvingIds: Set<String>,
    onRefreshApprovals: () -> Unit,
    onApprove: (PendingApprovalItem) -> Unit,
) {
    val refreshing = loadingDigest || loadingApprovals
    val pullState = rememberPullRefreshState(refreshing, onRefresh = {
        when (section) {
            ActivitySection.Digest -> onRefreshDigest()
            ActivitySection.Log -> onRefreshLog()
            ActivitySection.Approvals -> onRefreshApprovals()
        }
    })

    Box(Modifier.pullRefresh(pullState)) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = section.ordinal, containerColor = InvictusBrand.NavySurface) {
                Tab(
                    selected = section == ActivitySection.Digest,
                    onClick = { onSectionChange(ActivitySection.Digest) },
                    text = { Text("Today") }
                )
                Tab(
                    selected = section == ActivitySection.Log,
                    onClick = { onSectionChange(ActivitySection.Log) },
                    text = { Text("Agent log") }
                )
                Tab(
                    selected = section == ActivitySection.Approvals,
                    onClick = { onSectionChange(ActivitySection.Approvals) },
                    text = { Text("Approvals") }
                )
            }

            when (section) {
                ActivitySection.Digest -> DigestSection(loadingDigest, digest)
                ActivitySection.Log -> LogSection(workflowLog, logFilter, onLogFilterChange)
                ActivitySection.Approvals -> ApprovalsSection(
                    loadingApprovals, approvals, approvingIds, onApprove
                )
            }
        }
        PullRefreshIndicator(refreshing, pullState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun DigestSection(loading: Boolean, digest: DailyDigestInfo?) {
    Column(Modifier.invictusScreenPadding(), verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap)) {
        if (loading && digest == null) {
            InvictusSkeletonBlock(height = 8.dp)
            InvictusSkeletonBlock(height = 120.dp)
        } else if (digest == null) {
            EmptyStateCard(
                title = "No activity yet",
                message = "Send a prompt from Home to populate today's stats.",
                actionLabel = null,
                onAction = {}
            )
        } else {
            InvictusCard {
                Text("Today's summary", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatRing("${digest.successRate}%", "Success")
                    StatRing(digest.totalRuns.toString(), "Runs")
                    StatRing("~${digest.timeSavedMinutes}m", "Saved")
                }
                HorizontalDivider(color = InvictusBrand.Hairline)
                StatLine("Date", digest.date)
                StatLine("Successes", digest.successCount.toString())
                StatLine("Failures", digest.failureCount.toString())
            }
        }
    }
}

@Composable
private fun StatRing(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LogSection(
    workflowLog: List<WorkflowEntry>,
    filter: WorkflowKind?,
    onFilterChange: (WorkflowKind?) -> Unit,
) {
    val listState = rememberLazyListState()
    val filtered = workflowLog.filter { entry ->
        when (filter) {
            null -> true
            WorkflowKind.Error -> entry.kind == WorkflowKind.Error
            WorkflowKind.Build -> entry.kind == WorkflowKind.Build
            else -> entry.kind == filter
        }
    }
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.lastIndex)
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { onFilterChange(null) }, label = { Text("All") })
            FilterChip(selected = filter == WorkflowKind.Error, onClick = { onFilterChange(WorkflowKind.Error) }, label = { Text("Errors") })
            FilterChip(selected = filter == WorkflowKind.Build, onClick = { onFilterChange(WorkflowKind.Build) }, label = { Text("Builds") })
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            EmptyStateCard("No log entries", "Activity from prompts and builds appears here.", null, {})
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered) { WorkflowLine(it) }
            }
        }
    }
}

@Composable
private fun ApprovalsSection(
    loading: Boolean,
    items: List<PendingApprovalItem>,
    approvingIds: Set<String>,
    onApprove: (PendingApprovalItem) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading && items.isEmpty()) {
            InvictusSkeletonBlock(height = 8.dp)
            InvictusSkeletonBlock(height = 96.dp)
        }
        if (items.isEmpty()) {
            EmptyStateCard("No pending approvals", "Risky prompts wait here for your approval.", null, {})
        } else {
            items.forEach { item ->
                val busy = approvingIds.contains(item.taskId)
                InvictusCard {
                    Text(item.taskId, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.prompt, style = MaterialTheme.typography.bodyMedium)
                    Text("Project: ${item.projectId}", style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
                    InvictusStatusChip(
                        label = "Requires approval",
                        tone = StatusTone.Warning,
                    )
                    InvictusPrimaryButton(
                        onClick = { onApprove(item) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Approving…" else "Approve")
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionScreen(
    diagnostics: ConnectionDiagnostics,
    bridgeBaseUrl: String,
    onBridgeUrlChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    isPaired: Boolean,
    pairingStatus: String,
    pairingInProgress: Boolean,
    onTestConnection: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenTailscale: () -> Unit,
    bridgeHost: String,
) {
    val statusTone = when {
        isPaired && diagnostics.isReady -> StatusTone.Success
        pairingInProgress -> StatusTone.Active
        diagnostics.bridgeReachable -> StatusTone.Warning
        else -> StatusTone.Neutral
    }
    val statusLabel = when {
        isPaired && diagnostics.isReady -> "Connected to your PC"
        isPaired -> "Paired — checking bridge"
        pairingInProgress -> "Connecting…"
        else -> "Not connected"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .invictusScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(InvictusDimens.sectionGap),
    ) {
        InvictusSectionHeader(
            title = "Connection",
            subtitle = "Secure link between your phone and PC bridge",
        )

        InvictusStatusChip(label = statusLabel, tone = statusTone)

        ConnectionChecklist(diagnostics = diagnostics, isPaired = isPaired)

        InvictusCard {
            Text(
                diagnostics.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = InvictusBrand.White,
            )
            if (diagnostics.showOpenTailscale) {
                InvictusSecondaryButton(
                    onClick = onOpenTailscale,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Tailscale")
                }
            }
            InvictusSecondaryButton(
                onClick = onTestConnection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test connection")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InvictusTextField(
                value = bridgeBaseUrl,
                onValueChange = onBridgeUrlChange,
                label = "Bridge URL",
                placeholder = "http://YOUR-PC-IP:3003",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Text(
                "Use your PC's VPN IP from Invictus Networks setup.",
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted,
            )
        }

        if (isPaired) {
            InvictusTextField(
                value = "••••••••••••",
                onValueChange = {},
                label = "Pairing code",
                readOnly = true,
                enabled = false,
                trailingIcon = {
                    Icon(Icons.Default.CheckCircle, null, tint = InvictusBrand.Success)
                },
            )
            if (bridgeHost.isNotBlank()) {
                Text(
                    "Reachable at $bridgeHost",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Success,
                )
            }
            InvictusSecondaryButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disconnect")
            }
        } else {
            InvictusTextField(
                value = pairingCode,
                onValueChange = onPairingCodeChange,
                label = "Pairing code",
                visualTransformation = PasswordVisualTransformation(),
            )
            InvictusPrimaryButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !pairingInProgress && pairingCode.isNotBlank() && bridgeBaseUrl.isNotBlank(),
            ) {
                Text(if (pairingInProgress) "Connecting…" else "Connect")
            }
        }

        if (pairingStatus.isNotBlank()) {
            Text(
                pairingStatus,
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted,
            )
        }
    }
}

@Composable
fun ConnectionChecklist(diagnostics: ConnectionDiagnostics, isPaired: Boolean) {
    InvictusCard {
        Text("Connection checklist", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
        InvictusChecklistRow(
            label = "VPN active",
            done = when {
                diagnostics.usesInvictusVpnAddress || diagnostics.usesTailscaleAddress ->
                    diagnostics.tailscaleVpnActive
                else -> true
            },
        )
        InvictusChecklistRow("Bridge reachable", diagnostics.bridgeReachable)
        InvictusChecklistRow("Paired with PC", isPaired)
    }
}

@Composable
fun SettingsScreen(
    versionName: String,
    updateStatus: String,
    updateAvailable: Boolean,
    checkingUpdate: Boolean,
    installingUpdate: Boolean,
    buildingUpdate: Boolean,
    backingUp: Boolean,
    buildStatus: String,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onPublishUpdate: () -> Unit,
    onArchiveVersion: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .invictusScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap)
    ) {
        InvictusSectionHeader(title = "Settings", subtitle = "Updates and publishing")
        SettingsSection(title = "About & updates") {
            StatLine("Version", "v$versionName")
            Text(updateStatus, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
            InvictusPrimaryButton(
                onClick = onCheckUpdate,
                enabled = !checkingUpdate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checkingUpdate) "Checking…" else "Check for update")
            }
            if (updateAvailable) {
                Text(
                    "Opens the Android installer — tap Install on that screen, then reopen the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                InvictusSecondaryButton(
                    onClick = onInstallUpdate,
                    enabled = !installingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (installingUpdate) "Installing…" else "Install update")
                }
            }
        }
        SettingsSection(title = "Customize & publish") {
            Text(
                "Build a new APK on your PC and publish it for everyone on your network.",
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted,
            )
            InvictusPrimaryButton(
                onClick = onPublishUpdate,
                enabled = !buildingUpdate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (buildingUpdate) "Publishing…" else "Publish update")
            }
            if (buildStatus.isNotBlank()) {
                Text(buildStatus, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
            }
            InvictusSecondaryButton(
                onClick = onArchiveVersion,
                enabled = !backingUp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (backingUp) "Archiving…" else "Archive current version")
            }
            Text(
                "Saves the current APK and update manifest on your PC before you publish changes.",
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted,
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    InvictusCard {
        Text(title, style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
        content()
    }
}

@Composable
fun SetupWizardOverlay(
    step: Int,
    bridgeUrl: String,
    onBridgeUrlChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalSteps = 3
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InvictusBrand.NavyDeep)
            .statusBarsPadding()
            .padding(InvictusDimens.pageHorizontal),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(InvictusDimens.sectionGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    InvictusTextButton(onClick = onBack) { Text("Back") }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                InvictusTextButton(onClick = onDismiss) { Text("Skip") }
            }

            if (step == 0) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Invictus Link",
                        style = MaterialTheme.typography.headlineLarge,
                        color = InvictusBrand.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your universe, your way",
                        style = MaterialTheme.typography.titleMedium,
                        color = InvictusBrand.Muted,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Connect your phone to the Cursor agent on your PC — privately, over your own network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InvictusBrand.Muted,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap),
                ) {
                    Text(
                        "Step $step of $totalSteps",
                        style = MaterialTheme.typography.labelMedium,
                        color = InvictusBrand.Accent,
                    )
                    when (step) {
                        1 -> {
                            Text(
                                "Install WireGuard and connect your tunnel",
                                style = MaterialTheme.typography.titleLarge,
                                color = InvictusBrand.White,
                            )
                            Text(
                                "Import your phone profile from Invictus Networks (QR or .conf). If you use Tailscale instead, install and connect it first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InvictusBrand.Muted,
                            )
                        }
                        2 -> {
                            Text(
                                "Enter your PC bridge URL",
                                style = MaterialTheme.typography.titleLarge,
                                color = InvictusBrand.White,
                            )
                            Text(
                                "Example: http://YOUR-PC-IP:3003 — your PC's VPN IP on port 3003.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.Muted,
                            )
                            InvictusTextField(
                                value = bridgeUrl,
                                onValueChange = onBridgeUrlChange,
                                label = "PC bridge URL",
                                placeholder = "http://YOUR-PC-IP:3003",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            )
                        }
                        else -> {
                            Text(
                                "Enter your pairing code",
                                style = MaterialTheme.typography.titleLarge,
                                color = InvictusBrand.White,
                            )
                            Text(
                                "Find BRIDGE_TOKEN in your bridge .env on your PC.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.Muted,
                            )
                            InvictusTextField(
                                value = pairingCode,
                                onValueChange = onPairingCodeChange,
                                label = "Pairing code",
                                visualTransformation = PasswordVisualTransformation(),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(totalSteps) { index ->
                    val active = (step == 0 && index == 0) || (step > 0 && index == step - 1)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) InvictusBrand.Accent
                                else InvictusBrand.HairlineStrong,
                            ),
                    )
                }
            }

            InvictusPrimaryButton(
                onClick = if (step >= totalSteps) onFinish else onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        step == 0 -> "Get started"
                        step >= totalSteps -> "Finish setup"
                        else -> "Continue"
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** @deprecated Use [SetupWizardOverlay] — kept as alias for compatibility. */
@Composable
fun SetupWizardDialog(
    step: Int,
    bridgeUrl: String,
    onBridgeUrlChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) = SetupWizardOverlay(
    step = step,
    bridgeUrl = bridgeUrl,
    onBridgeUrlChange = onBridgeUrlChange,
    pairingCode = pairingCode,
    onPairingCodeChange = onPairingCodeChange,
    onNext = onNext,
    onBack = onBack,
    onFinish = onFinish,
    onDismiss = onDismiss,
)

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    InvictusCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = InvictusBrand.White)
        Text(message, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
        if (actionLabel != null) {
            InvictusPrimaryButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun SkeletonBlock(height: Int) {
    InvictusSkeletonBlock(height = height.dp)
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = InvictusBrand.Muted)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun WorkflowLine(entry: WorkflowEntry) {
    val time = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestampMs))
    }
    val prefix = when (entry.kind) {
        WorkflowKind.Prompt -> "[PROMPT]"
        WorkflowKind.Build -> "[BUILD]"
        WorkflowKind.Success -> "[OK]"
        WorkflowKind.Error -> "[ERR]"
        WorkflowKind.Info -> "[INFO]"
    }
    val color = when (entry.kind) {
        WorkflowKind.Success -> InvictusBrand.Success
        WorkflowKind.Error -> InvictusBrand.Error
        WorkflowKind.Prompt -> InvictusBrand.White
        WorkflowKind.Build -> InvictusBrand.Warning
        WorkflowKind.Info -> InvictusBrand.Muted
    }
    Text(
        text = "$time $prefix ${entry.message}",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
}
