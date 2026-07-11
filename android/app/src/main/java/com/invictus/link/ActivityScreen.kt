package com.invictus.link

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    loadingCosts: Boolean,
    costs: CostDashboardInfo?,
    onRefreshCosts: () -> Unit,
    onSetCostLimits: (Double?, Double?) -> Unit,
    savingCostLimits: Boolean,
    onRefreshLog: () -> Unit,
    loadingApprovals: Boolean,
    approvals: List<PendingApprovalItem>,
    approvingIds: Set<String>,
    onRefreshApprovals: () -> Unit,
    onApprove: (PendingApprovalItem) -> Unit,
    loadingLog: Boolean = false,
) {
    val refreshing = when (section) {
        ActivitySection.Digest -> loadingDigest || loadingCosts
        ActivitySection.Log -> loadingLog
        ActivitySection.Approvals -> loadingApprovals
    }
    val pullState = rememberPullRefreshState(refreshing, onRefresh = {
        when (section) {
            ActivitySection.Digest -> {
                onRefreshDigest()
                onRefreshCosts()
            }
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
                ActivitySection.Digest -> DigestSection(
                    loadingDigest = loadingDigest,
                    digest = digest,
                    loadingCosts = loadingCosts,
                    costs = costs,
                    onRefreshCosts = onRefreshCosts,
                    onSetCostLimits = onSetCostLimits,
                    savingCostLimits = savingCostLimits,
                )
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
private fun DigestSection(
    loadingDigest: Boolean,
    digest: DailyDigestInfo?,
    loadingCosts: Boolean,
    costs: CostDashboardInfo?,
    onRefreshCosts: () -> Unit,
    onSetCostLimits: (Double?, Double?) -> Unit,
    savingCostLimits: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .invictusScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap),
    ) {
        if (loadingDigest && digest == null) {
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
                    StatRing(
                        value = "${digest.successRate}%",
                        label = "Success",
                        progress = digest.successRate / 100f,
                        color = InvictusBrand.Success,
                    )
                    StatRing(
                        value = digest.totalRuns.toString(),
                        label = "Runs",
                        progress = if (digest.totalRuns > 0) 1f else 0f,
                        color = InvictusBrand.Accent,
                    )
                    StatRing(
                        value = "~${digest.timeSavedMinutes}m",
                        label = "Saved",
                        progress = (digest.timeSavedMinutes / 120f).coerceIn(0f, 1f),
                        color = InvictusBrand.Warning,
                    )
                }
                HorizontalDivider(color = InvictusBrand.Hairline)
                StatLine("Date", digest.date)
                StatLine("Successes", digest.successCount.toString())
                StatLine("Failures", digest.failureCount.toString())
            }
        }
        CostDashboardCard(
            loading = loadingCosts,
            costs = costs,
            onRefresh = onRefreshCosts,
            onSetLimits = onSetCostLimits,
            savingLimits = savingCostLimits,
        )
    }
}

@Composable
private fun StatRing(
    value: String,
    label: String,
    progress: Float,
    color: Color,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "statRing",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = InvictusBrand.HairlineStrong,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                )
                if (animated > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        style = stroke,
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = InvictusBrand.White,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = InvictusBrand.Muted)
    }
}

internal fun relativeTime(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(timestampMs))
    }
}

@Composable
private fun LogSection(
    workflowLog: List<WorkflowEntry>,
    filter: WorkflowKind?,
    onFilterChange: (WorkflowKind?) -> Unit,
) {
    val listState = rememberLazyListState()
    var rawView by remember { mutableStateOf(false) }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = filter == null, onClick = { onFilterChange(null) }, label = { Text("All") })
            FilterChip(selected = filter == WorkflowKind.Error, onClick = { onFilterChange(WorkflowKind.Error) }, label = { Text("Errors") })
            FilterChip(selected = filter == WorkflowKind.Build, onClick = { onFilterChange(WorkflowKind.Build) }, label = { Text("Builds") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { rawView = !rawView }) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = if (rawView) "Card view" else "Raw log view",
                    tint = if (rawView) InvictusBrand.Accent else InvictusBrand.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            EmptyStateCard("No log entries", "Activity from prompts and builds appears here.", null, {})
        } else if (rawView) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered) { WorkflowLine(it) }
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { WorkflowCard(it) }
            }
        }
    }
}

@Composable
private fun WorkflowCard(entry: WorkflowEntry) {
    val (icon, tint) = when (entry.kind) {
        WorkflowKind.Prompt -> Icons.Outlined.ChatBubbleOutline to InvictusBrand.Accent
        WorkflowKind.Build -> Icons.Outlined.Build to InvictusBrand.Warning
        WorkflowKind.Success -> Icons.Outlined.CheckCircle to InvictusBrand.Success
        WorkflowKind.Error -> Icons.Outlined.ErrorOutline to InvictusBrand.Error
        WorkflowKind.Info -> Icons.Outlined.Info to InvictusBrand.Muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .invictusCardSurface(background = InvictusBrand.NavySurface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                relativeTime(entry.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = InvictusBrand.Muted,
            )
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
                    InvictusStatusChip(
                        label = "Requires approval",
                        tone = StatusTone.Warning,
                    )
                    Text(item.prompt, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Project: ${item.projectId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Muted,
                        )
                        Text(
                            "Task ${item.taskId.take(8)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InvictusBrand.Muted.copy(alpha = 0.6f),
                        )
                    }
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
