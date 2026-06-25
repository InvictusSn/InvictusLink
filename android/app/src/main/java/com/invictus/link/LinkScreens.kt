package com.invictus.link

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
            NavigationBar(containerColor = InvictusBrand.NavySurface) {
                BottomTab.entries.forEach { tab ->
                    val badgeCount = if (tab == BottomTab.Activity) pendingCount else 0
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = {
                            if (badgeCount > 0) {
                                BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                                    Icon(
                                        if (currentTab == tab) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label
                                    )
                                }
                            } else {
                                Icon(
                                    if (currentTab == tab) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!connectionOk) {
            EmptyStateCard(
                title = "Connect to your PC",
                message = "Turn on WireGuard, then open Connection to pair with your PC bridge.",
                actionLabel = "Go to Connection",
                onAction = onConnectFirst
            )
        } else {
            Text("Ready to send", style = MaterialTheme.typography.titleMedium, color = InvictusBrand.Success)
        }

        if (connectionOk) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SessionSelector(
                    modifier = Modifier.widthIn(max = 160.dp),
                    projects = projects,
                    selectedProjectId = selectedProjectId,
                    enabled = !sending,
                    onProjectSelected = onProjectSelected,
                    onNewSession = onNewSession,
                    onDeleteSession = onDeleteSession,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InvictusBrand.Navy)
        ) {
            InvictusPromptLogoBackground(modifier = Modifier.fillMaxSize())
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("Message your PC agent…", color = InvictusBrand.Muted) },
                enabled = !sending,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = InvictusBrand.Accent,
                    focusedTextColor = InvictusBrand.White,
                    unfocusedTextColor = InvictusBrand.White,
                ),
            )
        }

        Button(
            onClick = onSend,
            modifier = Modifier.fillMaxWidth(),
            enabled = !sending && prompt.isNotBlank() && connectionOk,
            colors = invictusButtonColors(),
        ) {
            Text(if (sending) "Sending…" else "Send to PC")
        }

        if (sending && elapsedSec >= 540) {
            Text(
                "Long task — the bridge stops waiting at 10 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Warning
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = InvictusBrand.NavyElevated)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Agent response", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
                    IconButton(onClick = { responseExpanded = !responseExpanded }) {
                        Icon(
                            if (responseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                if (responseExpanded) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            if (responseText.isBlank()) {
                                Text(
                                    "Ask anything — like the time, weather, or a question about your project — and the agent's reply will show up here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InvictusBrand.Muted
                                )
                            } else {
                                Text(
                                    responseText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InvictusBrand.White
                                )
                            }
                        }
                        if (history.isNotEmpty()) {
                            item {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "History (${history.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = InvictusBrand.Muted
                                    )
                                    TextButton(onClick = onClearHistory) {
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InvictusBrand.NavySurface),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    time + if (exchange.projectId.isNotBlank()) " · ${exchange.projectId}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = InvictusBrand.Muted
                )
                Text(
                    if (exchange.ok) "OK" else "ERR",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exchange.ok) InvictusBrand.Success else InvictusBrand.Error
                )
            }
            Text(
                exchange.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = InvictusBrand.White,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded) {
                HorizontalDivider()
                Text(
                    exchange.response.ifBlank { "(No output)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted
                )
                OutlinedButton(
                    onClick = onResend,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                    colors = invictusOutlinedButtonColors(),
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
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading && digest == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SkeletonBlock(height = 120)
        } else if (digest == null) {
            EmptyStateCard(
                title = "No activity yet",
                message = "Send a prompt from Home to populate today's stats.",
                actionLabel = null,
                onAction = {}
            )
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Today's summary", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatRing("${digest.successRate}%", "Success")
                        StatRing(digest.totalRuns.toString(), "Runs")
                        StatRing("~${digest.timeSavedMinutes}m", "Saved")
                    }
                    HorizontalDivider()
                    StatLine("Date", digest.date)
                    StatLine("Successes", digest.successCount.toString())
                    StatLine("Failures", digest.failureCount.toString())
                }
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
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (items.isEmpty()) {
            EmptyStateCard("No pending approvals", "Risky prompts wait here for your approval.", null, {})
        } else {
            items.forEach { item ->
                val busy = approvingIds.contains(item.taskId)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.taskId, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.prompt, style = MaterialTheme.typography.bodyMedium)
                        Text("Project: ${item.projectId}", style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
                        Text("Requires approval — destructive or risky action", style = MaterialTheme.typography.labelSmall, color = InvictusBrand.Warning)
                        Button(
                            onClick = { onApprove(item) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = invictusButtonColors(),
                        ) {
                            Text(if (busy) "Approving…" else "Approve")
                        }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PC connection", style = MaterialTheme.typography.titleMedium)
        ConnectionChecklist(diagnostics = diagnostics, isPaired = isPaired)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(diagnostics.statusMessage, style = MaterialTheme.typography.bodyMedium)
                if (diagnostics.showOpenTailscale) {
                    OutlinedButton(
                        onClick = onOpenTailscale,
                        modifier = Modifier.fillMaxWidth(),
                        colors = invictusOutlinedButtonColors(),
                    ) {
                        Text("Open Tailscale")
                    }
                }
                OutlinedButton(
                    onClick = onTestConnection,
                    modifier = Modifier.fillMaxWidth(),
                    colors = invictusOutlinedButtonColors(),
                ) {
                    Text("Test connection")
                }
            }
        }

        OutlinedTextField(
            value = bridgeBaseUrl,
            onValueChange = onBridgeUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bridge URL") },
            placeholder = { Text("http://YOUR-PC-IP:3003") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Text(
            "Use your PC's VPN IP from Invictus Networks setup.",
            style = MaterialTheme.typography.bodySmall,
            color = InvictusBrand.Muted
        )

        if (isPaired) {
            OutlinedTextField(
                value = "••••••••••••",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pairing code") },
                trailingIcon = { Icon(Icons.Default.CheckCircle, null, tint = InvictusBrand.Success) }
            )
            if (bridgeHost.isNotBlank()) {
                Text("Connected to $bridgeHost", style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Success)
            }
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = invictusOutlinedButtonColors(),
            ) {
                Text("Disconnect")
            }
        } else {
            OutlinedTextField(
                value = pairingCode,
                onValueChange = onPairingCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pairing code") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !pairingInProgress && pairingCode.isNotBlank() && bridgeBaseUrl.isNotBlank(),
                colors = invictusButtonColors(),
            ) {
                Text(if (pairingInProgress) "Connecting…" else "Connect Once")
            }
        }

        if (pairingStatus.isNotBlank()) {
            Text(pairingStatus, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
        }
    }
}

@Composable
fun ConnectionChecklist(diagnostics: ConnectionDiagnostics, isPaired: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChecklistRow(
                "VPN active",
                when {
                    diagnostics.usesInvictusVpnAddress || diagnostics.usesTailscaleAddress ->
                        diagnostics.tailscaleVpnActive
                    else -> true
                }
            )
            ChecklistRow("Bridge reachable", diagnostics.bridgeReachable)
            ChecklistRow("Paired with PC", isPaired)
        }
    }
}

@Composable
private fun ChecklistRow(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) InvictusBrand.Success else InvictusBrand.Muted,
            modifier = Modifier.size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleMedium)
        SettingsSection(title = "About & updates") {
            StatLine("Version", "v$versionName")
            Text(updateStatus, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
            Button(
                onClick = onCheckUpdate,
                enabled = !checkingUpdate,
                modifier = Modifier.fillMaxWidth(),
                colors = invictusButtonColors(),
            ) {
                Text(if (checkingUpdate) "Checking…" else "Check for update")
            }
            if (updateAvailable) {
                OutlinedButton(
                    onClick = onInstallUpdate,
                    enabled = !installingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = invictusOutlinedButtonColors(),
                ) {
                    Text(if (installingUpdate) "Installing…" else "Install update")
                }
            }
        }
        SettingsSection(title = "Customize & publish") {
            Text(
                "Build a new APK on your PC and publish it for everyone on your network.",
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted
            )
            Button(
                onClick = onPublishUpdate,
                enabled = !buildingUpdate,
                modifier = Modifier.fillMaxWidth(),
                colors = invictusButtonColors(),
            ) {
                Text(if (buildingUpdate) "Publishing…" else "Publish update")
            }
            if (buildStatus.isNotBlank()) {
                Text(buildStatus, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = onArchiveVersion,
                enabled = !backingUp,
                modifier = Modifier.fillMaxWidth(),
                colors = invictusOutlinedButtonColors(),
            ) {
                Text(if (backingUp) "Archiving…" else "Archive current version")
            }
            Text(
                "Saves the current APK and update manifest on your PC before you publish changes.",
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = InvictusBrand.NavyElevated)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (step > 0) {
                Text("Invictus Link setup")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (step == 0) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> {
                        Text(
                            "Invictus",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = InvictusBrand.White,
                        )
                        Text(
                            "Your universe, Your way",
                            style = MaterialTheme.typography.titleMedium,
                            color = InvictusBrand.Muted,
                        )
                    }
                    1 -> {
                        Text(
                            "Step 1: Install WireGuard on your phone and turn on your Invictus Networks tunnel.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Import your WireGuard phone profile (QR code or .conf file from your VPN setup). If you use Tailscale instead, install and connect the Tailscale app first — see the project documentation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Muted
                        )
                    }
                    2 -> {
                        Text(
                            "Step 2: Enter your PC bridge URL — the address your phone uses to reach the bridge over Invictus Networks.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Example: http://YOUR-PC-IP:3003\n(use your PC's VPN IP, port 3003)",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Muted
                        )
                        OutlinedTextField(
                            value = bridgeUrl,
                            onValueChange = onBridgeUrlChange,
                            label = { Text("PC bridge URL") },
                            placeholder = { Text("http://YOUR-PC-IP:3003") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                    }
                    else -> {
                        Text(
                            "Step 3: Enter the pairing code from your PC bridge. Find it in your bridge .env file as BRIDGE_TOKEN.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Example: your-bridge-token-here\n(copy the full token from your PC)",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Muted
                        )
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = onPairingCodeChange,
                            label = { Text("Pairing code") },
                            placeholder = { Text("your-bridge-token-here") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (step >= 3) onFinish else onNext) {
                Text(if (step >= 3) "Done" else "Next")
            }
        },
        dismissButton = {
            if (step > 0) TextButton(onClick = onBack) { Text("Back") }
        }
    )
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InvictusBrand.NavyElevated)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
            if (actionLabel != null) {
                Button(onClick = onAction, colors = invictusButtonColors()) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun SkeletonBlock(height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InvictusBrand.NavyElevated)
    )
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
