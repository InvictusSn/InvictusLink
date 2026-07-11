package com.invictus.link

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class LinkPromptCallbacks(
    val onStatus: (String) -> Unit,
    val onPartial: (String) -> Unit,
    val onResult: (String) -> Unit,
    val onSendingChange: (Boolean) -> Unit,
    val onPromptClear: () -> Unit,
    val onPendingAttachmentsClear: () -> Unit,
    val onLastRoutingNote: (String?) -> Unit,
    val onLastGrokCostUsd: (Double?) -> Unit,
    val onForegroundTaskId: (String?) -> Unit,
    val onActiveTaskStarted: (String) -> Unit,
    val appendWorkflow: (String, WorkflowKind) -> Unit,
    val appendHistory: (PromptExchange) -> Unit,
    val snack: (String) -> Unit,
    val onApprovalRequired: () -> Unit,
)

internal fun sendLinkPrompt(
    context: Context,
    scope: CoroutineScope,
    text: String,
    sending: Boolean,
    pendingAttachments: List<PendingAttachment>,
    authToken: String?,
    bridgeBaseUrl: String,
    selectedProjectId: String?,
    projects: List<ProjectInfo>,
    callbacks: LinkPromptCallbacks,
) {
    val trimmed = text.trim()
    if (trimmed.isBlank() || sending) return
    val attachmentsToSend = pendingAttachments
    if (attachmentsToSend.isNotEmpty() && authToken.isNullOrBlank()) {
        callbacks.snack("Pair with your PC before sending attachments")
        return
    }
    scope.launch {
        callbacks.onSendingChange(true)
        callbacks.onStatus("Sending")
        val watchPublish = promptImpliesLinkPublish(trimmed)
        callbacks.appendWorkflow(
            if (attachmentsToSend.isEmpty()) "Prompt: $trimmed"
            else "Prompt (+${attachmentsToSend.size} attachment${if (attachmentsToSend.size > 1) "s" else ""}): $trimmed",
            WorkflowKind.Prompt,
        )
        callbacks.onResult("")
        callbacks.onLastRoutingNote(null)
        callbacks.onLastGrokCostUsd(null)
        val projectForTask = selectedProjectId ?: projects.firstOrNull()?.id.orEmpty()
        try {
            runCatching {
                withContext(Dispatchers.IO) {
                    val uploadedPaths = attachmentsToSend.mapIndexed { index, attachment ->
                        callbacks.onStatus(
                            "Uploading ${attachment.name} (${index + 1}/${attachmentsToSend.size})",
                        )
                        uploadAttachment(
                            context = context,
                            baseUrl = bridgeBaseUrl,
                            token = authToken!!,
                            projectId = projectForTask,
                            attachment = attachment,
                        )
                    }
                    createPhoneTask(
                        baseUrl = bridgeBaseUrl,
                        prompt = trimmed,
                        projectId = projectForTask,
                        token = authToken,
                        attachments = uploadedPaths,
                    )
                }
            }.onSuccess { taskId ->
                callbacks.onForegroundTaskId(taskId)
                addPhoneOriginatedTaskId(context, taskId)
                upsertPhoneTaskHistory(
                    context = context,
                    prompt = trimmed,
                    projectId = projectForTask,
                    taskId = taskId,
                    response = "",
                    ok = false,
                )
                saveActivePhoneTask(
                    context,
                    ActivePhoneTask(
                        taskId = taskId,
                        prompt = trimmed,
                        projectId = projectForTask,
                        statusLabel = "Running",
                        partialOutput = null,
                        routingNote = null,
                        grokCostUsd = null,
                        startedAtMs = System.currentTimeMillis(),
                    ),
                )
                LinkAgentWatchService.start(context, taskId)
                if (watchPublish) {
                    LinkBackgroundWork.scheduleBuildWatch(context)
                }
                callbacks.onPromptClear()
                callbacks.onPendingAttachmentsClear()
                callbacks.onStatus("Running")
                callbacks.onActiveTaskStarted(taskId)
            }.onFailure { e ->
                val message = e.message ?: e.toString()
                callbacks.onStatus(message)
                callbacks.onResult(message)
                callbacks.appendWorkflow(message, WorkflowKind.Error)
                callbacks.appendHistory(
                    PromptExchange(
                        prompt = trimmed,
                        response = message,
                        projectId = projectForTask,
                        ok = false,
                        taskId = null,
                    ),
                )
                callbacks.onSendingChange(false)
            }
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            callbacks.onStatus(message)
            callbacks.onResult(message)
            callbacks.onSendingChange(false)
        }
    }
}
