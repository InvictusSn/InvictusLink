package com.invictus.link

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Catalog of provider types the user can add from the phone. */
data class ProviderCatalogEntry(
    val type: String,
    val label: String,
    val tagline: String,
    val brandColor: Color,
    val monogram: String,
    val needsKey: Boolean,
    val needsBaseUrl: Boolean,
    val defaultBaseUrl: String,
    val modelHint: String,
    val isLocal: Boolean,
)

val ProviderCatalog = listOf(
    ProviderCatalogEntry(
        type = "cursor", label = "Cursor", tagline = "Full coding agent on your PC",
        brandColor = Color(0xFFB4BCD0), monogram = "C",
        needsKey = true, needsBaseUrl = false, defaultBaseUrl = "",
        modelHint = "composer-2.5", isLocal = false,
    ),
    ProviderCatalogEntry(
        type = "openai", label = "OpenAI", tagline = "GPT models",
        brandColor = Color(0xFF10A37F), monogram = "O",
        needsKey = true, needsBaseUrl = false, defaultBaseUrl = "",
        modelHint = "gpt-4o-mini", isLocal = false,
    ),
    ProviderCatalogEntry(
        type = "anthropic", label = "Claude", tagline = "Anthropic models",
        brandColor = Color(0xFFD97757), monogram = "A",
        needsKey = true, needsBaseUrl = false, defaultBaseUrl = "",
        modelHint = "claude-sonnet-4-5", isLocal = false,
    ),
    ProviderCatalogEntry(
        type = "xai", label = "xAI", tagline = "Grok — grok-latest tracks xAI's newest model",
        brandColor = Color(0xFFE8EAED), monogram = "X",
        needsKey = true, needsBaseUrl = false, defaultBaseUrl = "",
        modelHint = "grok-latest", isLocal = false,
    ),
    ProviderCatalogEntry(
        type = "google", label = "Gemini", tagline = "Google models",
        brandColor = Color(0xFF4285F4), monogram = "G",
        needsKey = true, needsBaseUrl = false, defaultBaseUrl = "",
        modelHint = "gemini-2.5-flash", isLocal = false,
    ),
    ProviderCatalogEntry(
        type = "ollama", label = "Ollama", tagline = "Local models on your PC",
        brandColor = Color(0xFF8B9EF0), monogram = "OL",
        needsKey = false, needsBaseUrl = true, defaultBaseUrl = "http://127.0.0.1:11434",
        modelHint = "llama3.2", isLocal = true,
    ),
    ProviderCatalogEntry(
        type = "lmstudio", label = "LM Studio", tagline = "Local models on your PC",
        brandColor = Color(0xFFA78BFA), monogram = "LM",
        needsKey = false, needsBaseUrl = true, defaultBaseUrl = "http://127.0.0.1:1234",
        modelHint = "loaded model name", isLocal = true,
    ),
    ProviderCatalogEntry(
        type = "custom", label = "Custom", tagline = "Any OpenAI-compatible server",
        brandColor = Color(0xFF9BA3B5), monogram = "+",
        needsKey = false, needsBaseUrl = true, defaultBaseUrl = "",
        modelHint = "model id", isLocal = true,
    ),
)

fun catalogEntryFor(type: String): ProviderCatalogEntry =
    ProviderCatalog.firstOrNull { it.type == type } ?: ProviderCatalog.last()

@Composable
private fun ProviderMonogram(entry: ProviderCatalogEntry, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3).dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        entry.brandColor.copy(alpha = 0.28f),
                        entry.brandColor.copy(alpha = 0.10f),
                    ),
                ),
            )
            .border(
                1.dp,
                entry.brandColor.copy(alpha = 0.35f),
                RoundedCornerShape((size * 0.3).dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            entry.monogram,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.4).sp,
            ),
            color = entry.brandColor,
        )
    }
}

/**
 * "AI Providers" section for the Settings screen — shows which APIs are
 * connected (never the keys), which one is live, and lets the user switch.
 */
@Composable
fun AiProvidersSection(
    providers: List<AiProviderInfo>,
    pinnedProviderIds: Set<String>,
    loading: Boolean,
    busyProviderIds: Set<String>,
    routingMode: String,
    routingBusy: Boolean,
    onToggleAutoRouting: (Boolean) -> Unit,
    onActivate: (AiProviderInfo) -> Unit,
    onDelete: (AiProviderInfo) -> Unit,
    onTogglePin: (AiProviderInfo) -> Unit,
    onAddProvider: () -> Unit,
    onRefresh: () -> Unit,
    isPaired: Boolean,
) {
    var pendingDelete by remember { mutableStateOf<AiProviderInfo?>(null) }
    val view = LocalView.current

    pendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${provider.label}?") },
            text = {
                Text(
                    "The API key is deleted from your PC bridge. You can reconnect it anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(provider)
                }) { Text("Remove", color = InvictusBrand.Error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
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
                    Text("AI Providers", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
                    Text(
                        "Keys stay on your PC — never on this phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = InvictusBrand.Muted,
                    )
                }
                InvictusRefreshAction(loading = loading, onRefresh = onRefresh)
            }

            if (isPaired) {
                val autoOn = routingMode == "auto"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (autoOn) InvictusBrand.Accent.copy(alpha = 0.08f)
                            else InvictusBrand.NavySurface,
                        )
                        .border(
                            1.dp,
                            if (autoOn) InvictusBrand.Accent.copy(alpha = 0.35f) else InvictusBrand.Hairline,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Auto", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
                        Text(
                            "Auto mode routes to connected AIs based on your prompt",
                            style = MaterialTheme.typography.labelSmall,
                            color = InvictusBrand.Muted,
                        )
                    }
                    // Keep the Switch mounted while the bridge call runs — a
                    // spinner swap remounts it and replays the thumb animation.
                    Switch(
                        checked = autoOn,
                        onCheckedChange = {
                            if (!routingBusy) {
                                performTapHaptic(view)
                                onToggleAutoRouting(it)
                            }
                        },
                        enabled = !routingBusy,
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
                }
            }

            when {
                !isPaired -> Text(
                    "Pair with your PC on the Connection tab to manage AI providers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                providers.isEmpty() && !loading -> Text(
                    "No providers connected yet. Add one to choose which AI answers your prompts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val autoOn = routingMode == "auto"
                    providers.forEach { provider ->
                        ProviderRow(
                            provider = provider,
                            pinned = pinnedProviderIds.contains(provider.id),
                            busy = busyProviderIds.contains(provider.id),
                            showActive = !autoOn,
                            onActivate = {
                                performTapHaptic(view)
                                onActivate(provider)
                            },
                            onDelete = { pendingDelete = provider },
                            onTogglePin = {
                                performTapHaptic(view)
                                onTogglePin(provider)
                            },
                        )
                    }
                }
            }

            if (isPaired) {
                InvictusSecondaryButton(
                    onClick = onAddProvider,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect a provider")
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: AiProviderInfo,
    pinned: Boolean,
    busy: Boolean,
    showActive: Boolean = true,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val entry = catalogEntryFor(provider.type)
    val shape = RoundedCornerShape(14.dp)
    val highlighted = provider.isActive && showActive
    val borderColor =
        if (highlighted) entry.brandColor.copy(alpha = 0.45f) else InvictusBrand.Hairline
    val background =
        if (highlighted) entry.brandColor.copy(alpha = 0.07f) else InvictusBrand.NavySurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = !busy && !highlighted, onClick = onActivate)
            .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (pinned) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (pinned) "Unpin ${provider.label}" else "Pin ${provider.label} to top",
                tint = if (pinned) InvictusBrand.Accent else InvictusBrand.Muted.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp),
            )
        }
        ProviderMonogram(entry)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${provider.label} Connected",
                    style = MaterialTheme.typography.titleSmall,
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
            }
            val subtitle = buildString {
                append(provider.model)
                if (provider.maskedKey.isNotBlank()) append("  ·  ${provider.maskedKey}")
                if (provider.kind == "chat") append("  ·  chat only")
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = InvictusBrand.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Fixed-size trailing slot: the spinner overlays the label instead of
        // replacing it, so the row never reflows while switching providers.
        Box(contentAlignment = Alignment.Center) {
            val labelAlpha = if (busy) 0f else 1f
            when {
                provider.isActive && showActive -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .alpha(labelAlpha)
                        .clip(RoundedCornerShape(999.dp))
                        .background(InvictusBrand.Success.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(InvictusBrand.Success),
                    )
                    Text(
                        "In use",
                        style = MaterialTheme.typography.labelSmall,
                        color = InvictusBrand.Success,
                    )
                }
                else -> Text(
                    "Use",
                    style = MaterialTheme.typography.labelMedium,
                    color = InvictusBrand.Accent,
                    modifier = Modifier.alpha(labelAlpha),
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = InvictusBrand.Accent,
                )
            }
        }
        if (!provider.isBuiltIn) {
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove provider",
                    tint = InvictusBrand.Muted.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Full-card dialog for connecting a new provider: pick the service, enter the
 * key (or server URL for local agents), optional model override.
 */
@Composable
fun AddProviderDialog(
    connecting: Boolean,
    statusMessage: String,
    onConnect: (type: String, apiKey: String, baseUrl: String, model: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(ProviderCatalog[1]) } // OpenAI default
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    val canConnect = !connecting &&
        (!selected.needsKey || apiKey.isNotBlank()) &&
        (selected.type != "custom" || baseUrl.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!connecting) onDismiss() },
        containerColor = InvictusBrand.NavyElevated,
        title = {
            Text("Connect a provider", style = MaterialTheme.typography.titleLarge, color = InvictusBrand.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProviderCatalog.forEach { entry ->
                        val isSelected = entry.type == selected.type
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) entry.brandColor.copy(alpha = 0.10f)
                                    else Color.Transparent,
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) entry.brandColor.copy(alpha = 0.5f)
                                    else InvictusBrand.Hairline,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    selected = entry
                                    baseUrl = entry.defaultBaseUrl
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            ProviderMonogram(entry, size = 34)
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) InvictusBrand.White else InvictusBrand.Muted,
                            )
                        }
                    }
                }

                Text(
                    selected.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                HorizontalDivider(color = InvictusBrand.Hairline)

                if (selected.needsKey) {
                    InvictusTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = "API key",
                        placeholder = "Paste your ${selected.label} API key",
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !connecting,
                    )
                    Text(
                        "Sent once over your private VPN and stored on your PC — never on this phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InvictusBrand.Muted,
                    )
                }
                if (selected.needsBaseUrl) {
                    InvictusTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = "Server URL",
                        placeholder = selected.defaultBaseUrl.ifBlank { "http://127.0.0.1:8080" },
                        enabled = !connecting,
                    )
                    Text(
                        when (selected.type) {
                            "ollama" ->
                                "Ollama's default API on your PC (port 11434). Only change this if Ollama runs on another port or machine."
                            "lmstudio" ->
                                "LM Studio's local server (port 1234) when \"Start server\" is on in LM Studio."
                            else -> "Address of the local AI server as seen from your PC."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = InvictusBrand.Muted,
                    )
                }
                InvictusTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = "Model (optional)",
                    placeholder = selected.modelHint,
                    enabled = !connecting,
                )

                AnimatedVisibility(
                    visible = statusMessage.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusMessage.startsWith("Could not") || statusMessage.contains("failed", true)) {
                            InvictusBrand.Error
                        } else {
                            InvictusBrand.Muted
                        },
                    )
                }
            }
        },
        confirmButton = {
            InvictusPrimaryButton(
                onClick = { onConnect(selected.type, apiKey.trim(), baseUrl.trim(), model.trim()) },
                enabled = canConnect,
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = InvictusBrand.White,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (connecting) "Connecting…" else "Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !connecting) {
                Text("Cancel", color = InvictusBrand.Muted)
            }
        },
    )
}
