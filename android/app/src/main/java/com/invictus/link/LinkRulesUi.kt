package com.invictus.link

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class RuleScopeUi { Global, Provider, Session }

private fun scopeLabel(rule: LinkRule): String = when (rule.scope) {
    "provider" -> "Provider: ${rule.targetId ?: "?"}"
    "project", "session" -> "Session: ${rule.targetId ?: "?"}"
    else -> "Global"
}

@Composable
fun RulesSection(
    rules: List<LinkRule>,
    loading: Boolean,
    busyRuleIds: Set<String>,
    isPaired: Boolean,
    projects: List<ProjectInfo>,
    onRefresh: () -> Unit,
    onAddRule: (scope: String, targetId: String?, title: String, text: String, vaultNotes: List<String>) -> Unit,
    addingRule: Boolean,
    onToggleRule: (LinkRule, Boolean) -> Unit,
    onDeleteRule: (LinkRule) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var addAttempt by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LinkRule?>(null) }
    val view = LocalView.current

    LaunchedEffect(addingRule, addAttempt) {
        if (addAttempt && !addingRule) {
            showAddDialog = false
            addAttempt = false
        }
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete rule?") },
            text = { Text(rule.title, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteRule(rule)
                }) { Text("Delete", color = InvictusBrand.Error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showAddDialog) {
        AddRuleDialog(
            projects = projects,
            saving = addingRule,
            onDismiss = { if (!addingRule) showAddDialog = false },
            onConfirm = { scope, targetId, title, text, vaultNotes ->
                addAttempt = true
                onAddRule(scope, targetId, title, text, vaultNotes)
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
                    Text("Rules", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
                    Text(
                        "Standing instructions the AI follows on every prompt",
                        style = MaterialTheme.typography.labelSmall,
                        color = InvictusBrand.Muted,
                    )
                }
                InvictusRefreshAction(loading = loading, onRefresh = onRefresh)
            }

            when {
                !isPaired -> Text(
                    "Pair with your PC to manage rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                rules.isEmpty() && !loading -> Text(
                    "No rules yet. Add one to give the AI persistent instructions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rules.forEach { rule ->
                        RuleRow(
                            rule = rule,
                            busy = busyRuleIds.contains(rule.id),
                            onToggle = { enabled ->
                                performTapHaptic(view)
                                onToggleRule(rule, enabled)
                            },
                            onDelete = { pendingDelete = rule },
                        )
                    }
                }
            }

            if (isPaired) {
                InvictusSecondaryButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add rule")
                }
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: LinkRule,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InvictusBrand.NavySurface)
            .border(1.dp, InvictusBrand.Hairline, shape)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                rule.title,
                style = MaterialTheme.typography.titleSmall,
                color = InvictusBrand.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                scopeLabel(rule),
                style = MaterialTheme.typography.labelSmall,
                color = InvictusBrand.Muted,
            )
            Text(
                rule.text,
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (rule.vaultNotes.isNotEmpty()) {
                Text(
                    "🔗 vault: ${rule.vaultNotes.size} note(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = InvictusBrand.Muted,
                )
            }
        }
        // Keep the Switch mounted while the bridge call runs — swapping it for
        // a spinner recreated the component, which replayed its thumb animation
        // and shifted the row layout (visible stutter). Disabling it instead
        // keeps the geometry and animation state stable.
        Switch(
            checked = rule.enabled,
            onCheckedChange = { if (!busy) onToggle(it) },
            enabled = !busy,
            colors = SwitchDefaults.colors(
                checkedThumbColor = InvictusBrand.White,
                checkedTrackColor = InvictusBrand.Accent,
                uncheckedThumbColor = InvictusBrand.Muted,
                uncheckedTrackColor = InvictusBrand.Hairline,
                disabledCheckedThumbColor = InvictusBrand.White.copy(alpha = 0.7f),
                disabledCheckedTrackColor = InvictusBrand.Accent.copy(alpha = 0.6f),
                disabledUncheckedThumbColor = InvictusBrand.Muted.copy(alpha = 0.7f),
                disabledUncheckedTrackColor = InvictusBrand.Hairline,
            ),
        )
        IconButton(
            onClick = onDelete,
            enabled = !busy,
            modifier = Modifier.size(26.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete rule",
                tint = InvictusBrand.Muted.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun AddRuleDialog(
    projects: List<ProjectInfo>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (scope: String, targetId: String?, title: String, text: String, vaultNotes: List<String>) -> Unit,
) {
    var scopeUi by remember { mutableStateOf(RuleScopeUi.Global) }
    var targetId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var vaultNotesText by remember { mutableStateOf("") }

    val apiScope = when (scopeUi) {
        RuleScopeUi.Global -> "global"
        RuleScopeUi.Provider -> "provider"
        RuleScopeUi.Session -> "project"
    }
    val needsTarget = scopeUi != RuleScopeUi.Global
    val canConfirm = !saving &&
        title.isNotBlank() &&
        text.isNotBlank() &&
        (!needsTarget || !targetId.isNullOrBlank())

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = InvictusBrand.NavyElevated,
        title = {
            Text("Add rule", style = MaterialTheme.typography.titleLarge, color = InvictusBrand.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RuleScopeUi.entries.forEach { option ->
                        val selected = scopeUi == option
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(
                                    if (selected) InvictusBrand.Accent.copy(alpha = 0.15f)
                                    else InvictusBrand.NavySurface,
                                )
                                .border(
                                    1.dp,
                                    if (selected) InvictusBrand.Accent.copy(alpha = 0.5f)
                                    else InvictusBrand.Hairline,
                                    shape,
                                )
                                .clickable {
                                    scopeUi = option
                                    targetId = null
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (option) {
                                    RuleScopeUi.Global -> "Global"
                                    RuleScopeUi.Provider -> "Provider"
                                    RuleScopeUi.Session -> "Session"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) InvictusBrand.White else InvictusBrand.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                when (scopeUi) {
                    RuleScopeUi.Provider -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ProviderCatalog.forEach { entry ->
                                val isSelected = targetId == entry.type
                                Text(
                                    entry.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) InvictusBrand.White else InvictusBrand.Muted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (isSelected) entry.brandColor.copy(alpha = 0.18f)
                                            else InvictusBrand.NavySurface,
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) entry.brandColor.copy(alpha = 0.5f)
                                            else InvictusBrand.Hairline,
                                            RoundedCornerShape(999.dp),
                                        )
                                        .clickable { targetId = entry.type }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    RuleScopeUi.Session -> {
                        if (projects.isEmpty()) {
                            Text(
                                "No sessions available — create one on Home first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InvictusBrand.Muted,
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                projects.forEach { project ->
                                    val isSelected = targetId == project.id
                                    Text(
                                        project.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) InvictusBrand.White else InvictusBrand.Muted,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isSelected) InvictusBrand.Accent.copy(alpha = 0.15f)
                                                else InvictusBrand.NavySurface,
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) InvictusBrand.Accent.copy(alpha = 0.45f)
                                                else InvictusBrand.Hairline,
                                                RoundedCornerShape(999.dp),
                                            )
                                            .clickable { targetId = project.id }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    RuleScopeUi.Global -> Unit
                }

                InvictusTextField(
                    value = title,
                    onValueChange = { if (it.length <= 80) title = it },
                    label = "Title",
                    placeholder = "Short name for this rule",
                    enabled = !saving,
                )
                InvictusTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Rule text",
                    placeholder = "What the AI should always do…",
                    singleLine = false,
                    minLines = 3,
                    enabled = !saving,
                )
                Text(
                    "Tip: reference vault notes with [[Note Name]] — the bridge includes them automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InvictusBrand.Muted,
                )
                InvictusTextField(
                    value = vaultNotesText,
                    onValueChange = { vaultNotesText = it },
                    label = "Vault notes (optional, comma-separated paths)",
                    placeholder = "path/to/note.md, another/note",
                    enabled = !saving,
                )
            }
        },
        confirmButton = {
            InvictusPrimaryButton(
                onClick = {
                    val notes = vaultNotesText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    onConfirm(apiScope, targetId, title.trim(), text.trim(), notes)
                },
                enabled = canConfirm,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = InvictusBrand.White,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "Adding…" else "Add rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel", color = InvictusBrand.Muted)
            }
        },
    )
}
