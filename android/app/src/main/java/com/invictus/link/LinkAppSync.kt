package com.invictus.link

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun maybeNotifyForUpdate(
    context: Context,
    scope: CoroutineScope,
    bridgeBaseUrl: String,
    bridgeReachable: Boolean,
    onUpdateInfo: (UpdateInfo) -> Unit,
) {
    if (bridgeBaseUrl.isBlank() || !bridgeReachable) return
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                if (LinkBackgroundSync.checkUpdateAndNotify(context, bridgeBaseUrl)) {
                    loadPendingUpdateInfo(context)?.let { info -> info }
                } else {
                    null
                }
            }
        }.onSuccess { info ->
            if (info != null) onUpdateInfo(info)
        }
    }
}

internal fun pollBridgeCompletions(
    context: Context,
    scope: CoroutineScope,
    bridgeBaseUrl: String,
    authToken: String?,
    bridgeReachable: Boolean,
    foregroundTaskId: String?,
    seenCompletionKeys: Set<String>,
    completionKeysInitialized: Boolean,
    onSeenCompletionKeysChange: (Set<String>) -> Unit,
    onCompletionKeysInitializedChange: (Boolean) -> Unit,
    onHistoryUpdated: () -> Unit = {},
) {
    val token = authToken ?: return
    if (bridgeBaseUrl.isBlank() || !bridgeReachable) return
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                fetchNotifyableBridgeEvents(bridgeBaseUrl, token)
            }
        }.onSuccess { events ->
            if (!completionKeysInitialized) {
                onSeenCompletionKeysChange(events.map { it.key }.toSet() + seenCompletionKeys)
                onCompletionKeysInitializedChange(true)
                saveSeenCompletionKeys(
                    context,
                    events.map { it.key }.toSet() + seenCompletionKeys,
                )
                return@onSuccess
            }
            val fresh = events.filter { it.key !in seenCompletionKeys }
            var historyPatched = false
            for (event in fresh) {
                val taskId = event.taskId
                if (
                    taskId != null &&
                    (event.event == "task_completed" || event.event == "task_error")
                ) {
                    historyPatched = patchHistoryWithTaskResult(
                        context,
                        bridgeBaseUrl,
                        token,
                        taskId,
                    ) || historyPatched
                }
                if (!event.notify) continue
                if (shouldSkipCompletionNotification(context, event, foregroundTaskId)) continue
                showLinkNotification(context, event.title, event.body)
            }
            if (historyPatched) onHistoryUpdated()
            if (fresh.isNotEmpty()) {
                val updated = seenCompletionKeys + fresh.map { it.key }
                onSeenCompletionKeysChange(updated)
                saveSeenCompletionKeys(context, updated)
            }
        }
    }
}
