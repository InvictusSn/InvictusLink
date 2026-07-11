package com.invictus.link

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun stopActiveLinkPrompt(
    context: Context,
    scope: CoroutineScope,
    bridgeBaseUrl: String,
    authToken: String?,
    callbacks: LinkStopCallbacks,
) {
    val active = loadActivePhoneTask(context) ?: run {
        callbacks.onStopped(null, "Stopped")
        return
    }
    val taskId = active.taskId
    scope.launch {
        runCatching {
            if (!authToken.isNullOrBlank() && bridgeBaseUrl.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    cancelTask(bridgeBaseUrl, taskId, authToken)
                }
            }
        }
        LinkAgentWatchService.stop(context)
        val response = active.partialOutput?.takeIf { it.isNotBlank() } ?: "Stopped."
        upsertPhoneTaskHistory(
            context = context,
            prompt = active.prompt,
            projectId = active.projectId,
            taskId = taskId,
            response = response,
            ok = false,
        )
        suppressCompletionNotificationsForTask(context, bridgeBaseUrl, authToken, taskId)
        clearActivePhoneTask(context)
        callbacks.onStopped(active.prompt, response)
    }
}

internal data class LinkStopCallbacks(
    val onStopped: (restoredPrompt: String?, response: String) -> Unit,
)
