package com.invictus.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
internal fun StatLine(label: String, value: String) {
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
