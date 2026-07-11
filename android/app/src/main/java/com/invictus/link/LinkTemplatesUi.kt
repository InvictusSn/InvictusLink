package com.invictus.link

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateLibrarySheet(
    visible: Boolean,
    templates: List<PromptTemplate>,
    currentPrompt: String,
    onDismiss: () -> Unit,
    onUseTemplate: (PromptTemplate) -> Unit,
    onAddTemplate: () -> Unit,
    onSaveCurrentAsTemplate: () -> Unit,
    onRequestDelete: (PromptTemplate) -> Unit,
) {
    if (!visible) return

    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState()
    val sheetContext = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val window = (sheetContext as? Activity)?.window
        val previousNavColor = window?.navigationBarColor
        window?.navigationBarColor = InvictusBrand.NavyElevated.toArgb()
        onDispose {
            if (window != null && previousNavColor != null) {
                window.navigationBarColor = previousNavColor
            }
        }
    }

    val sortedTemplates = remember(templates, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            templates
        } else {
            val q = searchQuery.lowercase()
            templates.filter {
                it.title.lowercase().contains(q) || it.text.lowercase().contains(q)
            }
        }
        filtered.sortedWith(
            compareByDescending<PromptTemplate> { it.useCount }
                .thenByDescending { it.createdAt },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InvictusBrand.NavyElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(InvictusBrand.HairlineStrong),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(InvictusBrand.NavyElevated),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Prompt library",
                        style = MaterialTheme.typography.titleMedium,
                        color = InvictusBrand.White,
                    )
                    IconButton(
                        onClick = {
                            performTapHaptic(view)
                            onDismiss()
                            onAddTemplate()
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New template",
                            tint = InvictusBrand.Accent,
                        )
                    }
                }
                Text(
                    "Reusable prompts and templates",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                if (currentPrompt.isNotBlank()) {
                    TemplateSheetActionRow(
                        icon = Icons.Default.Add,
                        title = "Save current prompt as template",
                        subtitle = "Reuse this prompt later",
                        accent = true,
                    ) {
                        performTapHaptic(view)
                        onDismiss()
                        onSaveCurrentAsTemplate()
                    }
                    HorizontalDivider(
                        color = InvictusBrand.Hairline,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                if (templates.size > 5) {
                    InvictusTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search templates…",
                        singleLine = true,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (sortedTemplates.isNotEmpty()) {
                    sortedTemplates.forEach { template ->
                        TemplateSheetRow(
                            template = template,
                            onUse = {
                                performTapHaptic(view)
                                onUseTemplate(template)
                                onDismiss()
                            },
                            onDelete = {
                                performTapHaptic(view)
                                onDismiss()
                                onRequestDelete(template)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateSheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (accent) InvictusBrand.Accent.copy(alpha = 0.08f) else Color.Transparent,
            )
            .border(
                1.dp,
                if (accent) InvictusBrand.Accent.copy(alpha = 0.28f) else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .clickable {
                performTapHaptic(view)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InvictusBrand.NavySurface)
                .border(1.dp, InvictusBrand.Hairline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (accent) InvictusBrand.Accent else InvictusBrand.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = InvictusBrand.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
        }
    }
}

@Composable
fun CreateTemplateDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (title: String, text: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var text by remember(initialText) { mutableStateOf(initialText) }
    val trimmedTitle = title.trim()
    val trimmedText = text.trim()
    val canSave = trimmedTitle.isNotBlank() && trimmedText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InvictusBrand.NavyElevated,
        title = {
            Text(
                "New template",
                style = MaterialTheme.typography.titleLarge,
                color = InvictusBrand.White,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InvictusTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = "Title",
                    placeholder = "e.g. Code review, Weekly summary",
                    singleLine = true,
                )
                InvictusTextField(
                    value = text,
                    onValueChange = { if (it.length <= 8000) text = it },
                    label = "Prompt",
                    placeholder = "Write your reusable prompt…",
                    singleLine = false,
                    minLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmedTitle, trimmedText) },
                enabled = canSave,
            ) {
                Text("Save", color = if (canSave) InvictusBrand.Accent else InvictusBrand.Muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = InvictusBrand.Muted)
            }
        },
    )
}

@Composable
private fun TemplateSheetRow(
    template: PromptTemplate,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(14.dp)
    val variables = remember(template.text) { templateVariables(template.text) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(InvictusBrand.NavySurface)
            .border(1.dp, InvictusBrand.Hairline, shape)
            .clickable {
                performTapHaptic(view)
                onUse()
            }
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Book,
            contentDescription = null,
            tint = InvictusBrand.Accent,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                template.title,
                style = MaterialTheme.typography.titleSmall,
                color = InvictusBrand.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                template.text,
                style = MaterialTheme.typography.bodySmall,
                color = InvictusBrand.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (variables.isNotEmpty()) {
                Text(
                    "{{variables}}: ${variables.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InvictusBrand.Accent.copy(alpha = 0.85f),
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete template",
                tint = InvictusBrand.Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun TemplateVariablesDialog(
    template: PromptTemplate,
    onDismiss: () -> Unit,
    onInsert: (filledText: String) -> Unit,
) {
    val variables = remember(template.text) { templateVariables(template.text) }
    var values by remember(template.id) {
        mutableStateOf(variables.associateWith { "" })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InvictusBrand.NavyElevated,
        title = {
            Text(
                template.title,
                style = MaterialTheme.typography.titleLarge,
                color = InvictusBrand.White,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Fill in template variables, then insert into your prompt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                variables.forEach { variable ->
                    InvictusTextField(
                        value = values[variable] ?: "",
                        onValueChange = { values = values + (variable to it) },
                        label = variable,
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            InvictusPrimaryButton(
                onClick = { onInsert(fillTemplate(template.text, values)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = InvictusBrand.Muted)
            }
        },
    )
}
