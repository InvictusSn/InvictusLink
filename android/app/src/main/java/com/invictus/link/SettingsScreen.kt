package com.invictus.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    providers: List<AiProviderInfo>,
    pinnedProviderIds: Set<String>,
    loadingProviders: Boolean,
    busyProviderIds: Set<String>,
    routingMode: String,
    routingBusy: Boolean,
    onToggleAutoRouting: (Boolean) -> Unit,
    isPaired: Boolean,
    onActivateProvider: (AiProviderInfo) -> Unit,
    onDeleteProvider: (AiProviderInfo) -> Unit,
    onTogglePinProvider: (AiProviderInfo) -> Unit,
    onRefreshProviders: () -> Unit,
    onAddProviderRequest: () -> Unit,
    rules: List<LinkRule>,
    loadingRules: Boolean,
    busyRuleIds: Set<String>,
    addingRule: Boolean,
    onRefreshRules: () -> Unit,
    onAddRule: (scope: String, targetId: String?, title: String, text: String, vaultNotes: List<String>) -> Unit,
    onToggleRule: (LinkRule, Boolean) -> Unit,
    onDeleteRule: (LinkRule) -> Unit,
    projects: List<ProjectInfo>,
    crashLog: String?,
    sendingCrashLog: Boolean,
    onSendCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .invictusScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(InvictusDimens.itemGap)
    ) {
        InvictusSectionHeader(title = "Settings", subtitle = "Providers, updates, and publishing")
        AiProvidersSection(
            providers = providers,
            pinnedProviderIds = pinnedProviderIds,
            loading = loadingProviders,
            busyProviderIds = busyProviderIds,
            routingMode = routingMode,
            routingBusy = routingBusy,
            onToggleAutoRouting = onToggleAutoRouting,
            onActivate = onActivateProvider,
            onDelete = onDeleteProvider,
            onTogglePin = onTogglePinProvider,
            onAddProvider = onAddProviderRequest,
            onRefresh = onRefreshProviders,
            isPaired = isPaired,
        )
        RulesSection(
            rules = rules,
            loading = loadingRules,
            busyRuleIds = busyRuleIds,
            isPaired = isPaired,
            projects = projects,
            onRefresh = onRefreshRules,
            onAddRule = onAddRule,
            addingRule = addingRule,
            onToggleRule = onToggleRule,
            onDeleteRule = onDeleteRule,
        )
        SettingsSection(title = "About & updates") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.invictus_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Invictus Link",
                        style = MaterialTheme.typography.titleMedium,
                        color = InvictusBrand.White,
                    )
                    Text(
                        "Version $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = InvictusBrand.Muted,
                    )
                }
            }
            HorizontalDivider(color = InvictusBrand.Hairline)
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
        SettingsSection(title = "Diagnostics") {
            if (crashLog == null) {
                Text(
                    "No crashes recorded. If the app ever crashes, a report is saved here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
            } else {
                val crashCount = remember(crashLog) { countCrashes(crashLog) }
                val latestStamp = remember(crashLog) { latestCrashTimestamp(crashLog) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.BugReport,
                        contentDescription = null,
                        tint = InvictusBrand.Error,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (crashCount == 1) "1 crash recorded" else "$crashCount crashes recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InvictusBrand.White,
                        )
                        if (latestStamp != null) {
                            Text(
                                "Latest: $latestStamp",
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.Muted,
                            )
                        }
                    }
                }
                var showCrashDetails by remember { mutableStateOf(false) }
                TextButton(onClick = { showCrashDetails = !showCrashDetails }) {
                    Text(
                        if (showCrashDetails) "Hide details" else "View details",
                        color = InvictusBrand.Accent,
                    )
                }
                if (showCrashDetails) {
                    Text(
                        crashLog.take(4000),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        color = InvictusBrand.Muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                InvictusPrimaryButton(
                    onClick = onSendCrashLog,
                    enabled = !sendingCrashLog && isPaired,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (sendingCrashLog) "Sending…" else "Send crash log to PC")
                }
                if (!isPaired) {
                    Text(
                        "Pair with your PC to send the crash log through the bridge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InvictusBrand.Muted,
                    )
                }
                InvictusSecondaryButton(
                    onClick = onClearCrashLog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear crash log")
                }
            }
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
