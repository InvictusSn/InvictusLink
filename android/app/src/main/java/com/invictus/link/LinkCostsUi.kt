package com.invictus.link

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import java.util.Locale

@Composable
fun CostDashboardCard(
    loading: Boolean,
    costs: CostDashboardInfo?,
    onRefresh: () -> Unit,
    onSetLimits: (monthly: Double?, daily: Double?) -> Unit,
    savingLimits: Boolean,
) {
    var showLimitsDialog by remember { mutableStateOf(false) }

    if (showLimitsDialog && costs != null) {
        SetLimitsDialog(
            costs = costs,
            saving = savingLimits,
            onDismiss = { if (!savingLimits) showLimitsDialog = false },
            onSave = { monthly, daily ->
                onSetLimits(monthly, daily)
            },
        )
    }

    InvictusCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Spending", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
                    Text(
                        "Estimated API costs this month (all sessions on this bridge)",
                        style = MaterialTheme.typography.labelSmall,
                        color = InvictusBrand.Muted,
                    )
                }
                InvictusRefreshAction(loading = loading, onRefresh = onRefresh)
            }

            when {
                costs == null && loading -> {
                    InvictusSkeletonBlock(height = 48.dp)
                    InvictusSkeletonBlock(height = 80.dp)
                }
                costs == null -> Text(
                    "Send prompts to start tracking costs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                else -> {
                    costs.alert?.let { alert ->
                        val tone = when (alert.level) {
                            "critical" -> StatusTone.Error
                            else -> StatusTone.Warning
                        }
                        val bgColor = when (tone) {
                            StatusTone.Error -> InvictusBrand.Error.copy(alpha = 0.12f)
                            else -> InvictusBrand.Warning.copy(alpha = 0.12f)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            InvictusStatusChip(label = alert.level.replaceFirstChar { it.uppercase() }, tone = tone)
                            Text(
                                alert.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.White,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "This month",
                                style = MaterialTheme.typography.labelSmall,
                                color = InvictusBrand.Muted,
                            )
                            Text(
                                formatUsd(costs.monthUsd),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = InvictusBrand.White,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = InvictusBrand.Muted,
                            )
                            Text(
                                formatUsd(costs.todayUsd),
                                style = MaterialTheme.typography.titleMedium,
                                color = InvictusBrand.White,
                            )
                        }
                    }

                    if (
                        kotlin.math.abs(costs.deviceMonthUsd - costs.monthUsd) >= 0.005 ||
                        kotlin.math.abs(costs.deviceTodayUsd - costs.todayUsd) >= 0.005
                    ) {
                        Text(
                            "This phone: ${formatUsd(costs.deviceMonthUsd)} this month · ${formatUsd(costs.deviceTodayUsd)} today",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Muted,
                        )
                    }

                    costs.monthlyLimitUsd?.let { limit ->
                        val progress = (costs.monthUsd / limit).coerceIn(0.0, 1.0).toFloat()
                        val barColor = when {
                            progress < 0.6f -> InvictusBrand.Success
                            progress < 0.9f -> InvictusBrand.Warning
                            else -> InvictusBrand.Error
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(InvictusBrand.Hairline),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(barColor),
                            )
                        }
                        Text(
                            "Limit ${formatUsd(limit)}/month",
                            style = MaterialTheme.typography.labelSmall,
                            color = InvictusBrand.Muted,
                        )
                    }

                    if (costs.byProvider.isNotEmpty()) {
                        HorizontalDivider(color = InvictusBrand.Hairline)
                        costs.byProvider.forEach { provider ->
                            ProviderCostRow(provider)
                        }
                        HorizontalDivider(color = InvictusBrand.Hairline)
                    }

                    if (costs.untrackedProviders.isNotEmpty()) {
                        if (costs.byProvider.isEmpty()) {
                            HorizontalDivider(color = InvictusBrand.Hairline)
                        }
                        Text(
                            "No live cost data",
                            style = MaterialTheme.typography.labelLarge,
                            color = InvictusBrand.White,
                        )
                        Text(
                            "Check the official provider dashboard for usage tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Muted,
                        )
                        costs.untrackedProviders.forEach { provider ->
                            UntrackedProviderRow(provider)
                        }
                        HorizontalDivider(color = InvictusBrand.Hairline)
                    }

                    if (costs.localRuns > 0) {
                        Text(
                            "💰 Local models saved you ~${String.format(Locale.US, "$%.2f", costs.estimatedSavingsUsd)} this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Success,
                        )
                    }
                    if (costs.cacheSavingsUsd > 0) {
                        Text(
                            "⚡ Prompt caching saved you ${formatUsdPrecise(costs.cacheSavingsUsd)} this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = InvictusBrand.Success,
                        )
                    }

                    InvictusTextButton(onClick = { showLimitsDialog = true }) {
                        Text("Set limits", color = InvictusBrand.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCostRow(provider: ProviderCostSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                provider.label,
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (provider.isLocal) {
                Text(
                    "LOCAL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 9.sp),
                    color = InvictusBrand.Success,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(InvictusBrand.Success.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                "${provider.runs} runs",
                style = MaterialTheme.typography.labelSmall,
                color = InvictusBrand.Muted,
            )
        }
        Text(
            when {
                provider.isLocal && provider.costUsd == 0.0 -> "free"
                provider.costTracking == "runs_only" -> "${provider.runs} runs"
                else -> String.format(Locale.US, "$%.3f", provider.costUsd)
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                provider.isLocal && provider.costUsd == 0.0 -> InvictusBrand.Success
                provider.costTracking == "runs_only" -> InvictusBrand.Muted
                else -> InvictusBrand.White
            },
        )
    }
}

@Composable
private fun UntrackedProviderRow(provider: UntrackedProviderSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                provider.label,
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (provider.runs > 0) {
                Text(
                    "${provider.runs} runs",
                    style = MaterialTheme.typography.labelSmall,
                    color = InvictusBrand.Muted,
                )
            }
        }
        Text(
            "Check provider",
            style = MaterialTheme.typography.labelMedium,
            color = InvictusBrand.Muted,
        )
    }
}

@Composable
private fun SetLimitsDialog(
    costs: CostDashboardInfo,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (monthly: Double?, daily: Double?) -> Unit,
) {
    var monthlyText by remember {
        mutableStateOf(costs.monthlyLimitUsd?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    var dailyText by remember {
        mutableStateOf(costs.dailyLimitUsd?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = InvictusBrand.NavyElevated,
        title = {
            Text("Spending limits", style = MaterialTheme.typography.titleLarge, color = InvictusBrand.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Leave blank for no limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                InvictusTextField(
                    value = monthlyText,
                    onValueChange = { monthlyText = it },
                    label = "Monthly limit USD",
                    placeholder = "e.g. 50.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !saving,
                )
                InvictusTextField(
                    value = dailyText,
                    onValueChange = { dailyText = it },
                    label = "Daily limit USD",
                    placeholder = "e.g. 5.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !saving,
                )
            }
        },
        confirmButton = {
            InvictusPrimaryButton(
                onClick = {
                    val monthly = monthlyText.trim().toDoubleOrNull()
                    val daily = dailyText.trim().toDoubleOrNull()
                    onSave(
                        monthlyText.isBlank().let { if (it) null else monthly },
                        dailyText.isBlank().let { if (it) null else daily },
                    )
                },
                enabled = !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = InvictusBrand.White,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel", color = InvictusBrand.Muted)
            }
        },
    )
}

private fun formatUsd(value: Double): String =
    String.format(Locale.US, "$%.2f", value)

private fun formatUsdPrecise(value: Double): String =
    if (value < 0.01) String.format(Locale.US, "$%.4f", value)
    else String.format(Locale.US, "$%.2f", value)
