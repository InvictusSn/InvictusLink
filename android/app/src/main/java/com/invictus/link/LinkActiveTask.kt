package com.invictus.link

import android.content.Context
import org.json.JSONObject

internal data class ActivePhoneTask(
    val taskId: String,
    val prompt: String,
    val projectId: String,
    val statusLabel: String,
    val partialOutput: String?,
    val routingNote: String?,
    val grokCostUsd: Double?,
    val startedAtMs: Long,
)

private const val PREF_ACTIVE_TASK_JSON = "active_phone_task_json"

internal object LinkAppVisibility {
    @Volatile
    var isInForeground: Boolean = false
}

internal fun saveActivePhoneTask(context: Context, task: ActivePhoneTask) {
    val json = JSONObject()
        .put("taskId", task.taskId)
        .put("prompt", task.prompt)
        .put("projectId", task.projectId)
        .put("statusLabel", task.statusLabel)
        .put("partialOutput", task.partialOutput ?: "")
        .put("routingNote", task.routingNote ?: "")
        .put("grokCostUsd", task.grokCostUsd ?: JSONObject.NULL)
        .put("startedAtMs", task.startedAtMs)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_ACTIVE_TASK_JSON, json.toString())
        .apply()
}

internal fun loadActivePhoneTask(context: Context): ActivePhoneTask? {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_ACTIVE_TASK_JSON, null) ?: return null
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val taskId = json.optString("taskId", "")
    if (taskId.isBlank()) return null
    val cost = if (json.isNull("grokCostUsd")) null else json.optDouble("grokCostUsd")
    return ActivePhoneTask(
        taskId = taskId,
        prompt = json.optString("prompt", ""),
        projectId = json.optString("projectId", ""),
        statusLabel = json.optString("statusLabel", "Running"),
        partialOutput = json.optString("partialOutput", "").takeIf { it.isNotBlank() },
        routingNote = json.optString("routingNote", "").takeIf { it.isNotBlank() },
        grokCostUsd = cost,
        startedAtMs = json.optLong("startedAtMs", System.currentTimeMillis()),
    )
}

internal fun clearActivePhoneTask(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(PREF_ACTIVE_TASK_JSON)
        .apply()
}

internal fun upsertPhoneTaskHistory(
    context: Context,
    prompt: String,
    projectId: String,
    taskId: String,
    response: String,
    ok: Boolean,
) {
    val history = loadPromptHistory(context).toMutableList()
    val idx = history.indexOfLast { it.taskId == taskId }
    val entry = PromptExchange(
        prompt = prompt,
        response = response,
        projectId = projectId,
        ok = ok,
        taskId = taskId,
    )
    if (idx >= 0) {
        history[idx] = entry
    } else {
        history.add(entry)
    }
    savePromptHistory(context, history.takeLast(20))
}

internal fun completePhoneOriginatedTask(
    context: Context,
    baseUrl: String,
    token: String?,
    task: ActivePhoneTask,
    taskResponse: TaskResponse,
) {
    val ok = taskResponse.status == "completed"
    val body = taskResponse.output?.takeIf { it.isNotBlank() } ?: task.partialOutput
    val response = appendLinkAppUpdateHintIfNeeded(
        task.prompt,
        formatTaskHistoryResponse(taskResponse.status, body, taskResponse.error),
    )
    upsertPhoneTaskHistory(
        context = context,
        prompt = task.prompt,
        projectId = task.projectId,
        taskId = task.taskId,
        response = response,
        ok = ok,
    )
    val log = loadWorkflowLog(context) + WorkflowEntry(
        message = response.take(500),
        kind = if (ok) WorkflowKind.Success else WorkflowKind.Error,
    )
    saveWorkflowLog(context, log)
    suppressCompletionNotificationsForTask(context, baseUrl, token, task.taskId)
    clearActivePhoneTask(context)
    if (!LinkAppVisibility.isInForeground) {
        val title = if (ok) "Agent finished" else "Agent task failed"
        showLinkNotification(context, title, response.take(160))
    }
}
