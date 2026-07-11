package com.invictus.link

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    history: List<PromptExchange>,
    onResend: (PromptExchange) -> Unit,
    onClearHistory: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onConnectFirst: () -> Unit,
    activeProviderLabel: String?,
    routingMode: String = "manual",
    routingNote: String? = null,
    grokCostUsd: Double? = null,
    showGrokCost: Boolean = false,
    attachments: List<PendingAttachment>,
    onAttachmentsChange: (List<PendingAttachment>) -> Unit,
    onAttachmentError: (String) -> Unit,
    templates: List<PromptTemplate>,
    onUseTemplate: (PromptTemplate) -> Unit,
    onSaveTemplate: (String, String) -> Unit,
    onDeleteTemplate: (PromptTemplate) -> Unit,
    sessionName: String,
    exportExchanges: List<PromptExchange>,
    exporting: Boolean,
    onExportToPc: (List<PromptExchange>) -> Unit,
    onExportShare: (List<PromptExchange>) -> Unit,
) {
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    var createTemplateInitialText by remember { mutableStateOf("") }
    var pendingDeleteTemplate by remember { mutableStateOf<PromptTemplate?>(null) }
    var pendingVariableTemplate by remember { mutableStateOf<PromptTemplate?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val responseScrollState = rememberScrollState()
    val streamingLive = sending && result.isNotBlank()
    val responseText = when {
        streamingLive -> result
        sending -> {
            val base = status.ifBlank { "Thinking" }
            "$base… ${formatElapsed(elapsedSec)}"
        }
        result.isNotBlank() -> result
        status.isNotBlank() && status != "Done" -> status
        else -> ""
    }
    // Plain status strings (elapsed timers, errors) shouldn't go through markdown.
    val renderAsMarkdown = streamingLive || (!sending && result.isNotBlank())

    LaunchedEffect(sending) {
        if (sending) responseScrollState.scrollTo(0)
    }

    LaunchedEffect(responseText, sending) {
        if (sending) responseScrollState.scrollTo(0)
    }

    AttachmentPickerSheet(
        visible = showAttachmentSheet,
        remainingSlots = (10 - attachments.size).coerceAtLeast(1),
        onDismiss = { showAttachmentSheet = false },
        onPicked = { picked ->
            val combined = attachments + picked
            if (combined.size > 10) {
                onAttachmentError("Up to 10 attachments per prompt")
                onAttachmentsChange(combined.take(10))
            } else {
                onAttachmentsChange(combined)
            }
        },
        onError = onAttachmentError,
    )

    TemplateLibrarySheet(
        visible = showTemplateSheet,
        templates = templates,
        currentPrompt = prompt,
        onDismiss = { showTemplateSheet = false },
        onUseTemplate = { template ->
            if (templateVariables(template.text).isEmpty()) {
                onPromptChange(template.text)
            } else {
                pendingVariableTemplate = template
            }
            onUseTemplate(template)
        },
        onAddTemplate = {
            createTemplateInitialText = ""
            showCreateTemplateDialog = true
        },
        onSaveCurrentAsTemplate = {
            createTemplateInitialText = prompt
            showCreateTemplateDialog = true
        },
        onRequestDelete = { template ->
            pendingDeleteTemplate = template
        },
    )

    if (showCreateTemplateDialog) {
        CreateTemplateDialog(
            initialText = createTemplateInitialText,
            onDismiss = { showCreateTemplateDialog = false },
            onSave = { title, text ->
                showCreateTemplateDialog = false
                onSaveTemplate(title, text)
            },
        )
    }

    pendingDeleteTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTemplate = null },
            containerColor = InvictusBrand.NavyElevated,
            title = {
                Text(
                    "Delete template?",
                    style = MaterialTheme.typography.titleMedium,
                    color = InvictusBrand.White,
                )
            },
            text = {
                Text(
                    "\"${template.title}\" will be removed from your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InvictusBrand.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteTemplate = null
                    onDeleteTemplate(template)
                }) {
                    Text("Delete", color = InvictusBrand.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTemplate = null }) {
                    Text("Cancel", color = InvictusBrand.Muted)
                }
            },
        )
    }

    pendingVariableTemplate?.let { template ->
        TemplateVariablesDialog(
            template = template,
            onDismiss = { pendingVariableTemplate = null },
            onInsert = { filled ->
                onPromptChange(filled)
                pendingVariableTemplate = null
            },
        )
    }

    ExportConversationDialog(
        visible = showExportDialog,
        exchanges = exportExchanges,
        sessionName = sessionName,
        exporting = exporting,
        onDismiss = { showExportDialog = false },
        onSaveToPc = { selected ->
            onExportToPc(selected)
            showExportDialog = false
        },
        onShare = { selected ->
            onExportShare(selected)
            showExportDialog = false
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .invictusScreenPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InvictusSectionHeader(
                title = "Home",
                subtitle = if (connectionOk) homeGreeting() else "Pair to get started",
                modifier = Modifier.weight(1f),
            )
            if (connectionOk) {
                SessionSelector(
                    modifier = Modifier.widthIn(max = 200.dp),
                    projects = projects,
                    selectedProjectId = selectedProjectId,
                    enabled = !sending,
                    onProjectSelected = onProjectSelected,
                    onNewSession = onNewSession,
                    onRenameSession = onRenameSession,
                    onDeleteSession = onDeleteSession,
                )
            }
        }

        if (!connectionOk) {
            Spacer(Modifier.height(8.dp))
            EmptyStateCard(
                title = "Connect to your PC",
                message = "Turn on WireGuard, then open Connection to pair with your PC bridge.",
                actionLabel = "Go to Connection",
                onAction = onConnectFirst,
            )
            Spacer(Modifier.weight(1f))
        } else {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InvictusStatusChip(
                    label = "Connected",
                    tone = StatusTone.Success,
                )
                if (!activeProviderLabel.isNullOrBlank()) {
                    InvictusStatusChip(
                        label = if (routingMode == "auto") "via Auto" else "via $activeProviderLabel",
                        tone = StatusTone.Active,
                        showDot = false,
                    )
                }
            }
            // Auto mode picks providers silently — no routing note on Home.

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                val composerMaxHeight = (maxHeight * 0.55f).coerceIn(96.dp, 320.dp)
                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .invictusCardSurface()
                            .padding(InvictusDimens.cardPadding),
                    ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Agent response", style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showGrokCost && grokCostUsd != null && responseText.isNotBlank() && !sending) {
                            Text(
                                formatGrokCostUsd(grokCostUsd),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = InvictusBrand.Muted.copy(alpha = 0.65f),
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        if (renderAsMarkdown && responseText.isNotBlank()) {
                            InvictusTextButton(
                                onClick = {
                                    performTapHaptic(view)
                                    clipboard.setText(AnnotatedString(responseText))
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy response",
                                    tint = InvictusBrand.Muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (streamingLive) {
                    Text(
                        "Live — streaming from your PC · ${formatElapsed(elapsedSec)}",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = InvictusBrand.Accent,
                    )
                }
                if (sending && elapsedSec >= 540) {
                    Text(
                        "Long task — the bridge stops waiting at 10 minutes.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = InvictusBrand.Warning,
                    )
                }
                AnimatedVisibility(
                    visible = sending,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    InvictusSendingIndicator()
                }

                val showHistory = history.isNotEmpty() && !sending && responseText.isNotBlank()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .verticalScroll(responseScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        responseText.isBlank() && !sending -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = InvictusBrand.Muted.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    "Ask anything",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InvictusBrand.Muted,
                                )
                                Text(
                                    "Replies from your PC agent land here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InvictusBrand.Muted.copy(alpha = 0.65f),
                                )
                            }
                        }
                        streamingLive -> Text(
                            responseText,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InvictusBrand.White,
                        )
                        renderAsMarkdown -> MarkdownText(
                            responseText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        responseText.isNotBlank() -> Text(
                            responseText,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InvictusBrand.White,
                        )
                    }
                    if (showHistory) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "History (${history.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = InvictusBrand.Muted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                InvictusTextButton(onClick = { showExportDialog = true }) {
                                    Text("Export", color = InvictusBrand.Muted)
                                }
                                InvictusTextButton(onClick = onClearHistory) {
                                    Text("Clear", color = InvictusBrand.Muted)
                                }
                            }
                        }
                        history.asReversed().forEach { exchange ->
                            HistoryItem(
                                exchange = exchange,
                                sending = sending,
                                onResend = { onResend(exchange) },
                            )
                        }
                    }
                }
                    }

                    HomePromptComposer(
                        prompt = prompt,
                        onPromptChange = onPromptChange,
                        sending = sending,
                        connectionOk = connectionOk,
                        attachments = attachments,
                        onAttachmentsChange = onAttachmentsChange,
                        onAttachClick = { showAttachmentSheet = true },
                        onTemplateClick = { showTemplateSheet = true },
                        onSend = onSend,
                        onStop = onStop,
                        maxHeight = composerMaxHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePromptComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    sending: Boolean,
    connectionOk: Boolean,
    attachments: List<PendingAttachment>,
    onAttachmentsChange: (List<PendingAttachment>) -> Unit,
    onAttachClick: () -> Unit,
    onTemplateClick: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    maxHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    // Toolbar: tallest button (send, 46dp) + equal 10dp clearance to the card's
    // bottom edge, matching the 10dp side padding — even, symmetrical margins.
    val toolbarEdgeGap = 10.dp
    val toolbarHeight = 46.dp + toolbarEdgeGap
    val verticalPad = 8.dp
    val bottomReserve = toolbarHeight + verticalPad
    val maxContentHeight = (maxHeight - bottomReserve - verticalPad).coerceAtLeast(48.dp)
    val contentScrollState = rememberScrollState()

    LaunchedEffect(prompt) {
        if (contentScrollState.maxValue > 0) {
            contentScrollState.animateScrollTo(contentScrollState.maxValue)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(min = 76.dp, max = maxHeight)
            .invictusCardSurface(background = InvictusBrand.Navy, borderColor = InvictusBrand.HairlineStrong),
    ) {
        InvictusPromptLogoBackground(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = verticalPad, bottom = bottomReserve)
                .heightIn(max = maxContentHeight)
                .verticalScroll(contentScrollState),
        ) {
            if (attachments.isNotEmpty()) {
                AttachmentChipsRow(
                    attachments = attachments,
                    uploading = sending,
                    onRemove = { removed ->
                        onAttachmentsChange(attachments.filterNot { it === removed })
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            InvictusTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                placeholder = "Message your PC agent…",
                enabled = !sending && connectionOk,
                singleLine = false,
                minLines = 1,
                maxLines = Int.MAX_VALUE,
                transparentBackground = true,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .requiredHeight(toolbarHeight)
                .padding(start = toolbarEdgeGap, end = toolbarEdgeGap, bottom = toolbarEdgeGap),
            // Bottom-align so every button sits exactly toolbarEdgeGap from the
            // card's bottom edge regardless of its own height.
            verticalAlignment = Alignment.Bottom,
        ) {
            InvictusRoundIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Attach a file or photo",
                enabled = !sending && connectionOk,
                onClick = onAttachClick,
            )
            InvictusRoundIconButton(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = "Prompt library",
                enabled = !sending && connectionOk,
                onClick = onTemplateClick,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            InvictusSendButton(
                onSend = onSend,
                onStop = onStop,
                enabled = !sending && prompt.isNotBlank() && connectionOk,
                sending = sending,
            )
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
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectInfo?>(null) }
    var pendingRename by remember { mutableStateOf<ProjectInfo?>(null) }
    val selected = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    val label = selected?.name ?: "No session"
    val view = LocalView.current

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = InvictusBrand.NavyElevated,
            title = {
                Text("Delete session?", style = MaterialTheme.typography.titleMedium, color = InvictusBrand.White)
            },
            text = {
                Text(
                    "\"${session.name}\" and its files on your PC will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InvictusBrand.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    sheetVisible = false
                    onDeleteSession(session.id)
                }) {
                    Text("Delete", color = InvictusBrand.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = InvictusBrand.Muted)
                }
            },
        )
    }

    pendingRename?.let { session ->
        RenameSessionDialog(
            initialName = session.name,
            onDismiss = { pendingRename = null },
            onSave = { newName ->
                pendingRename = null
                onRenameSession(session.id, newName)
            },
        )
    }

    if (sheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        val sheetContext = LocalContext.current
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
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
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
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        color = InvictusBrand.White,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Text(
                        "Each session is a separate workspace on your PC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InvictusBrand.Muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    SessionSheetActionRow(
                        icon = Icons.Default.Add,
                        title = "New session",
                        subtitle = "Start a fresh workspace folder",
                        accent = true,
                    ) {
                        performTapHaptic(view)
                        sheetVisible = false
                        onNewSession()
                    }
                    if (projects.isNotEmpty()) {
                        HorizontalDivider(
                            color = InvictusBrand.Hairline,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        projects.forEach { project ->
                            SessionSheetRow(
                                project = project,
                                selected = project.id == selected?.id,
                                onSelect = {
                                    performTapHaptic(view)
                                    sheetVisible = false
                                    onProjectSelected(project.id)
                                },
                                onRename = {
                                    performTapHaptic(view)
                                    pendingRename = project
                                },
                                onDelete = {
                                    performTapHaptic(view)
                                    pendingDelete = project
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(InvictusDimens.chipRadius))
            .background(InvictusBrand.NavyElevated)
            .border(1.dp, InvictusBrand.HairlineStrong, RoundedCornerShape(InvictusDimens.chipRadius))
            .clickable(enabled = enabled) {
                performTapHaptic(view)
                sheetVisible = true
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = InvictusBrand.Accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = InvictusBrand.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun SessionSheetActionRow(
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
private fun SessionSheetRow(
    project: ProjectInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(14.dp)
    val borderColor =
        if (selected) InvictusBrand.Accent.copy(alpha = 0.45f) else InvictusBrand.Hairline
    val background =
        if (selected) InvictusBrand.Accent.copy(alpha = 0.07f) else InvictusBrand.NavySurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable {
                performTapHaptic(view)
                onSelect()
            }
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) InvictusBrand.Success else InvictusBrand.Muted.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                project.name,
                style = MaterialTheme.typography.bodyLarge,
                color = InvictusBrand.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                project.id,
                style = MaterialTheme.typography.labelSmall,
                color = InvictusBrand.Muted.copy(alpha = 0.7f),
            )
        }
        IconButton(
            onClick = onRename,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Rename session",
                tint = InvictusBrand.Muted,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete session",
                tint = InvictusBrand.Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RenameSessionDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val canSave = trimmed.isNotBlank() && trimmed != initialName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InvictusBrand.NavyElevated,
        title = {
            Text("Rename session", style = MaterialTheme.typography.titleLarge, color = InvictusBrand.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose a name you'll recognize on the Home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvictusBrand.Muted,
                )
                InvictusTextField(
                    value = name,
                    onValueChange = { if (it.length <= 120) name = it },
                    label = "Session name",
                    placeholder = "e.g. Link polish, Weekend project",
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmed) },
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
                if (exchange.response.isBlank()) {
                    Text(
                        "(No output)",
                        style = MaterialTheme.typography.bodySmall,
                        color = InvictusBrand.Muted,
                    )
                } else {
                    MarkdownText(exchange.response)
                }
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


private fun homeGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Working late"
    }
}
