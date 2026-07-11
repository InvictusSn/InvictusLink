package com.invictus.link

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportConversationDialog(
    visible: Boolean,
    exchanges: List<PromptExchange>,
    sessionName: String,
    exporting: Boolean,
    onDismiss: () -> Unit,
    onSaveToPc: (List<PromptExchange>) -> Unit,
    onShare: (List<PromptExchange>) -> Unit,
) {
    if (!visible) return

    var selectedIndices by remember(exchanges) {
        mutableStateOf(exchanges.indices.toSet())
    }

    LaunchedEffect(visible, exchanges) {
        if (visible) {
            selectedIndices = exchanges.indices.toSet()
        }
    }

    val allSelected = exchanges.isNotEmpty() && selectedIndices.size == exchanges.size
    val selectedCount = selectedIndices.size
    val selectedExchanges = remember(exchanges, selectedIndices) {
        exchanges.filterIndexed { index, _ -> index in selectedIndices }
            .sortedBy { it.timestampMs }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InvictusBrand.NavyElevated,
        title = {
            Text(
                "Export conversation",
                style = MaterialTheme.typography.titleLarge,
                color = InvictusBrand.White,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (selectedCount == exchanges.size) {
                        "Export all $selectedCount exchanges from \"$sessionName\" as Markdown."
                    } else {
                        "Export $selectedCount of ${exchanges.size} exchanges from \"$sessionName\" as Markdown."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InvictusBrand.White,
                )
                Text(
                    "Save to PC writes into GrokResearch on your Desktop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )

                if (exchanges.isEmpty()) {
                    Text(
                        "No exchanges to export yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InvictusBrand.Muted,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !exporting) {
                                selectedIndices = if (allSelected) {
                                    emptySet()
                                } else {
                                    exchanges.indices.toSet()
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (allSelected) "Deselect all" else "Select all",
                            style = MaterialTheme.typography.labelLarge,
                            color = InvictusBrand.Accent,
                        )
                        Text(
                            "$selectedCount selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = InvictusBrand.Muted,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .invictusCardSurface(background = InvictusBrand.NavySurface)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        exchanges.asReversed().forEachIndexed { reversedIndex, exchange ->
                            val index = exchanges.lastIndex - reversedIndex
                            val checked = index in selectedIndices
                            val time = remember(exchange.timestampMs) {
                                SimpleDateFormat("MMM d, HH:mm", Locale.US)
                                    .format(Date(exchange.timestampMs))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !exporting) {
                                        selectedIndices = if (checked) {
                                            selectedIndices - index
                                        } else {
                                            selectedIndices + index
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedIndices = if (isChecked) {
                                            selectedIndices + index
                                        } else {
                                            selectedIndices - index
                                        }
                                    },
                                    enabled = !exporting,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = InvictusBrand.Accent,
                                        uncheckedColor = InvictusBrand.Muted,
                                        checkmarkColor = InvictusBrand.White,
                                    ),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InvictusBrand.Muted,
                                    )
                                    Text(
                                        exchange.prompt.trim().ifBlank { "(Empty prompt)" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InvictusBrand.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (reversedIndex < exchanges.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = InvictusBrand.Hairline,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InvictusPrimaryButton(
                        onClick = { onSaveToPc(selectedExchanges) },
                        enabled = !exporting && selectedExchanges.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (exporting) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = InvictusBrand.White,
                                )
                                Text("Saving…")
                            }
                        } else {
                            Text("Save to PC")
                        }
                    }
                    InvictusSecondaryButton(
                        onClick = { onShare(selectedExchanges) },
                        enabled = !exporting && selectedExchanges.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Share from phone")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !exporting) {
                Text("Cancel", color = InvictusBrand.Muted)
            }
        },
    )
}
