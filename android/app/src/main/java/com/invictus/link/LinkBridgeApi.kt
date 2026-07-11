package com.invictus.link

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI

internal fun isTransientNetworkError(error: Throwable): Boolean {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is IOException) return true
        cause = cause.cause
    }
    val message = error.message?.lowercase().orEmpty()
    return message.contains("failed to connect") ||
        message.contains("timeout") ||
        message.contains("connection reset") ||
        message.contains("broken pipe") ||
        message.contains("connection refused")
}

private suspend fun <T> withNetworkRetry(
    maxAttempts: Int = 5,
    initialDelayMs: Long = 1000,
    block: suspend () -> T,
): T {
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (error: Exception) {
            if (!isTransientNetworkError(error) || attempt == maxAttempts - 1) throw error
            lastError = error
            delay(initialDelayMs * (attempt + 1))
        }
    }
    throw lastError ?: RuntimeException("Network request failed")
}

internal data class TaskResponse(
    val status: String,
    val output: String?,
    val error: String?,
    val routingNote: String? = null,
    val usage: TokenUsage? = null,
)

/** Task reached a terminal state; [output] may still hold streamed agent text on failure. */
internal class TaskFinishedException(
    val taskStatus: String,
    val errorMessage: String?,
    val output: String?,
) : Exception(errorMessage ?: "Task failed")

internal const val BRIDGE_RESTART_TASK_ERROR =
    "The PC bridge restarted while this task was running. Send the prompt again if you still need it."

internal fun isTaskMissingOnBridge(error: Exception): Boolean {
    val message = error.message.orEmpty()
    return message.contains("(404)") || message.contains("task not found", ignoreCase = true)
}

internal fun formatTaskHistoryResponse(status: String, output: String?, error: String?): String {
    val body = output?.takeIf { it.isNotBlank() }
    return when {
        status == "completed" -> body ?: "(No output)"
        body != null -> "$body\n\n---\n⚠ ${error ?: "Task failed"}"
        else -> error ?: "Task failed"
    }
}

internal data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
)

internal data class NotifyableBridgeEvent(
    val key: String,
    val event: String,
    val title: String,
    val body: String,
    val taskId: String? = null,
    val notify: Boolean = true,
)

internal data class BuildJobInfo(
    val status: String,
    val error: String?,
    val lastOutput: String,
)

private data class CreateTaskResponse(
    val taskId: String,
    val status: String,
    val requiresApproval: Boolean,
)

internal suspend fun submitAndWait(
    baseUrl: String,
    prompt: String,
    projectId: String?,
    token: String?,
    onStatus: (String) -> Unit,
    onPartial: (String) -> Unit = {},
    attachments: List<String> = emptyList(),
    onTaskCreated: (String) -> Unit = {},
    onTaskMeta: (routingNote: String?, usage: TokenUsage?) -> Unit = { _, _ -> },
    onApprovalRequired: () -> Unit = {},
): String {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val created = withNetworkRetry {
        createTask(normalizedBaseUrl, prompt, projectId, token, attachments)
    }
    val taskId = created.taskId
    withContext(Dispatchers.Main) { onTaskCreated(taskId) }
    var approvalNotified = false
    var consecutivePollFailures = 0
    repeat(660) {
        val task = try {
            withNetworkRetry { getTask(normalizedBaseUrl, taskId, token) }.also {
                consecutivePollFailures = 0
            }
        } catch (error: Exception) {
            if (isTaskMissingOnBridge(error)) {
                throw TaskFinishedException("error", BRIDGE_RESTART_TASK_ERROR, null)
            }
            consecutivePollFailures += 1
            if (consecutivePollFailures >= 3) {
                val lastTask = runCatching {
                    getTask(normalizedBaseUrl, taskId, token)
                }.getOrNull()
                when (lastTask?.status) {
                    "completed" -> return lastTask.output ?: "(No output)"
                    "error" -> throw TaskFinishedException(
                        "error",
                        lastTask.error,
                        lastTask.output,
                    )
                }
                throw RuntimeException(
                    "Lost connection to PC while waiting for the agent. " +
                        "Your prompt may still be running — check History in a minute.",
                    error,
                )
            }
            withContext(Dispatchers.Main) {
                onStatus("Reconnecting… ($consecutivePollFailures/3)")
            }
            delay(2000)
            return@repeat
        }
        if (!task.routingNote.isNullOrBlank() || task.usage != null) {
            withContext(Dispatchers.Main) {
                onTaskMeta(task.routingNote, task.usage)
            }
        }
        when (task.status) {
            "awaiting_approval" -> {
                if (!approvalNotified) {
                    approvalNotified = true
                    withContext(Dispatchers.Main) {
                        onStatus("Waiting for approval")
                        onApprovalRequired()
                    }
                }
            }
            "queued" -> withContext(Dispatchers.Main) { onStatus("Queued") }
            "running" -> {
                val partial = task.output
                withContext(Dispatchers.Main) {
                    onStatus("Running")
                    if (!partial.isNullOrBlank()) onPartial(partial)
                }
            }
            "completed" -> return task.output ?: "(No output)"
            "error" -> throw TaskFinishedException("error", task.error, task.output)
            else -> withContext(Dispatchers.Main) { onStatus(task.status) }
        }
        delay(1000)
    }
    throw RuntimeException("Timed out waiting for task")
}

internal suspend fun createPhoneTask(
    baseUrl: String,
    prompt: String,
    projectId: String?,
    token: String?,
    attachments: List<String> = emptyList(),
): String {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val created = withNetworkRetry {
        createTask(normalizedBaseUrl, prompt, projectId, token, attachments)
    }
    return created.taskId
}

private fun createTask(
    baseUrl: String,
    prompt: String,
    projectId: String?,
    token: String?,
    attachments: List<String> = emptyList(),
): CreateTaskResponse {
    val url = "${baseUrl.trimEnd('/')}/tasks"
    val body = JSONObject()
        .put("prompt", prompt)
        .apply {
            if (!projectId.isNullOrBlank()) put("projectId", projectId)
            if (attachments.isNotEmpty()) {
                put("attachments", org.json.JSONArray(attachments))
            }
        }
        .put("outputStyle", "short")
        .toString()

    val response = LinkHttp.postJson(url, body, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Create task failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    return CreateTaskResponse(
        taskId = json.getString("taskId"),
        status = json.optString("status", "queued"),
        requiresApproval = json.optBoolean("requiresApproval", false),
    )
}

internal fun fetchTask(baseUrl: String, taskId: String, token: String?): TaskResponse {
    return getTask(baseUrl, taskId, token)
}

internal fun cancelTask(baseUrl: String, taskId: String, token: String?) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/tasks/$taskId/cancel"
    val response = LinkHttp.postEmptyJson(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Stop task failed (${response.code})")
    }
}

private fun getTask(baseUrl: String, taskId: String, token: String?): TaskResponse {
    val url = "${baseUrl.trimEnd('/')}/tasks/$taskId"
    val response = LinkHttp.get(url, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Get task failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    val output = if (json.has("output") && !json.isNull("output")) {
        json.optString("output")
    } else {
        null
    }
    val error = if (json.has("error") && !json.isNull("error")) {
        json.optString("error")
    } else {
        null
    }
    val routingNote = json.optString("routingNote", "").takeIf { it.isNotBlank() }
    val usage = json.optJSONObject("usage")?.let { u ->
        TokenUsage(
            promptTokens = u.optInt("promptTokens", 0),
            cachedTokens = u.optInt("cachedTokens", 0),
            completionTokens = u.optInt("completionTokens", 0),
            costUsd = u.optDouble("costUsd", 0.0),
        )
    }
    return TaskResponse(
        status = json.optString("status", "unknown"),
        output = output,
        error = error,
        routingNote = routingNote,
        usage = usage,
    )
}

internal fun checkBridgeHealth(baseUrl: String): Boolean {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/health"
    val response = LinkHttp.get(url, connectTimeoutMs = 4000, readTimeoutMs = 4000)
    return response.code in 200..299
}

internal fun fetchBridgeHealth(baseUrl: String): BridgeHealthInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/health"
    val response = LinkHttp.get(url, connectTimeoutMs = 4000, readTimeoutMs = 4000)
    if (response.code !in 200..299) {
        throw RuntimeException("Health check failed (${response.code})")
    }
    val json = JSONObject(response.body)
    val arr = json.optJSONArray("projects")
    val projects = buildList {
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isBlank()) continue
                add(ProjectInfo(id = id, name = item.optString("name", id)))
            }
        }
    }
    val providerObj = json.optJSONObject("activeProvider")
    val providerLabel = providerObj?.optString("label", "")?.takeIf { it.isNotBlank() }
    val providerType = providerObj?.optString("type", "")?.takeIf { it.isNotBlank() }
    val routingMode = json.optString("routingMode", "manual").ifBlank { "manual" }
    return BridgeHealthInfo(
        projects = projects,
        activeProviderLabel = providerLabel,
        activeProviderType = providerType,
        routingMode = routingMode,
    )
}

internal fun fetchProjects(baseUrl: String): List<ProjectInfo> =
    fetchBridgeHealth(baseUrl).projects

internal fun createLinkSession(
    baseUrl: String,
    token: String?,
    name: String? = null,
): ProjectInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/sessions"
    val body = JSONObject().apply {
        if (!name.isNullOrBlank()) put("name", name)
    }.toString()
    val response = LinkHttp.postJson(url, body, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(
            when (response.code) {
                404 -> "Bridge needs an update — restart the PC bridge, then try again"
                else -> err ?: "Create session failed (${response.code})"
            },
        )
    }
    val json = JSONObject(response.body)
    val id = json.optString("id", "")
    if (id.isBlank()) throw RuntimeException("Create session returned no id")
    return ProjectInfo(id = id, name = json.optString("name", id))
}

internal fun deleteLinkSession(
    baseUrl: String,
    token: String?,
    sessionId: String,
) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/sessions/$sessionId"
    val response = LinkHttp.delete(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Delete session failed (${response.code})")
    }
}

internal fun renameLinkSession(
    baseUrl: String,
    token: String?,
    sessionId: String,
    name: String,
): ProjectInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/sessions/$sessionId"
    val body = JSONObject().put("name", name.trim()).toString()
    val response = LinkHttp.patchJson(url, body, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(
            when (response.code) {
                404 -> "Bridge needs an update — restart the PC bridge, then try again"
                else -> err ?: "Rename session failed (${response.code})"
            },
        )
    }
    val json = JSONObject(response.body)
    val id = json.optString("id", sessionId)
    return ProjectInfo(id = id, name = json.optString("name", name))
}

internal fun uploadAttachment(
    context: Context,
    baseUrl: String,
    token: String,
    projectId: String?,
    attachment: PendingAttachment,
): String {
    val bytes = context.contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
        ?: throw RuntimeException("Could not read ${attachment.name}")
    if (bytes.isEmpty()) throw RuntimeException("${attachment.name} is empty")
    if (bytes.size > ATTACHMENT_MAX_BYTES) {
        throw RuntimeException("${attachment.name} is larger than 25 MB")
    }
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val query = buildString {
        append("name=").append(java.net.URLEncoder.encode(attachment.name, "UTF-8"))
        if (!projectId.isNullOrBlank()) {
            append("&projectId=").append(java.net.URLEncoder.encode(projectId, "UTF-8"))
        }
    }
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/attachments?$query"
    val response = LinkHttp.postBytes(
        url = url,
        bytes = bytes,
        contentType = "application/octet-stream",
        token = token,
        connectTimeoutMs = 15000,
        readTimeoutMs = 60000,
    )
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        if (response.code == 404) {
            throw RuntimeException("Bridge needs an update for attachments — restart the PC bridge")
        }
        throw RuntimeException(err ?: "Upload of ${attachment.name} failed (${response.code})")
    }
    val json = JSONObject(response.body)
    val path = json.optString("path", "")
    if (path.isBlank()) throw RuntimeException("Upload returned no path")
    return path
}

private fun parseProviderInfo(item: JSONObject): AiProviderInfo {
    return AiProviderInfo(
        id = item.optString("id", ""),
        type = item.optString("type", "custom"),
        label = item.optString("label", "Provider"),
        model = item.optString("model", ""),
        maskedKey = item.optString("maskedKey", ""),
        baseUrl = item.optString("baseUrl", "").takeIf { it.isNotBlank() },
        isActive = item.optBoolean("isActive", false),
        kind = item.optString("kind", "chat"),
        isLocal = item.optBoolean("isLocal", false),
        isBuiltIn = item.optBoolean("isBuiltIn", false),
    )
}

internal fun fetchAiProviders(baseUrl: String, token: String): ProvidersListResult {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/providers"
    val response = LinkHttp.get(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        if (response.code == 404) {
            throw RuntimeException("Bridge needs an update — restart the PC bridge, then try again")
        }
        throw RuntimeException("Load providers failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    val arr = json.optJSONArray("providers") ?: return ProvidersListResult(emptyList(), "manual")
    val routingMode = json.optString("routingMode", "manual").ifBlank { "manual" }
    val providers = buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val info = parseProviderInfo(item)
            if (info.id.isNotBlank()) add(info)
        }
    }
    return ProvidersListResult(providers = providers, routingMode = routingMode)
}

internal fun setRoutingMode(baseUrl: String, token: String, auto: Boolean) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/providers/routing-mode"
    val body = JSONObject().put("mode", if (auto) "auto" else "manual").toString()
    val response = LinkHttp.postJson(url, body, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Routing mode update failed (${response.code})")
    }
}

internal fun resetGrokThread(baseUrl: String, token: String, projectId: String) {
    if (projectId.isBlank()) return
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/grok-threads/$projectId/reset"
    val response = LinkHttp.postEmptyJson(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Reset Grok thread failed (${response.code})")
    }
}

internal fun formatGrokCostUsd(costUsd: Double): String =
    "$" + String.format(java.util.Locale.US, "%.3f", costUsd)

internal fun addAiProvider(
    baseUrl: String,
    token: String,
    type: String,
    apiKey: String?,
    providerBaseUrl: String?,
    model: String?,
): ProviderTestResult {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/providers"
    val body = JSONObject().apply {
        put("type", type)
        if (!apiKey.isNullOrBlank()) put("apiKey", apiKey)
        if (!providerBaseUrl.isNullOrBlank()) put("baseUrl", providerBaseUrl)
        if (!model.isNullOrBlank()) put("model", model)
    }.toString()
    val response = LinkHttp.postJson(url, body, token, connectTimeoutMs = 12000, readTimeoutMs = 12000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Add provider failed (${response.code})")
    }
    val json = JSONObject(response.body)
    val test = json.optJSONObject("test")
    return ProviderTestResult(
        ok = test?.optBoolean("ok", false) ?: false,
        detail = test?.optString("detail", "") ?: "",
    )
}

internal fun deleteAiProvider(baseUrl: String, token: String, providerId: String) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/providers/$providerId"
    val response = LinkHttp.delete(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Remove provider failed (${response.code})")
    }
}

internal fun activateAiProvider(baseUrl: String, token: String, providerId: String) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/providers/$providerId/activate"
    val response = LinkHttp.postEmptyJson(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Switch provider failed (${response.code})")
    }
}

internal fun resolveApkUrl(baseUrl: String, manifestApkUrl: String): String {
    val bridgeUri = URI(normalizeBaseUrl(baseUrl))
    val apkUri = runCatching { URI(manifestApkUrl) }.getOrNull()
    val apkPath = apkUri?.path?.takeIf { it.isNotBlank() }
        ?: "/download/InvictusLink.apk"
    return "${bridgeUri.scheme}://${bridgeUri.authority}$apkPath"
}

internal fun checkForUpdate(baseUrl: String): UpdateInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/download/latest.json"
    val response = LinkHttp.get(url)
    if (response.code !in 200..299) {
        throw RuntimeException("Update manifest failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    val versionCode = json.optInt("versionCode", 0)
    val versionName = json.optString("versionName", "unknown")
    val manifestApkUrl = json.optString("apkUrl", "")
    if (versionCode <= 0 || manifestApkUrl.isBlank()) {
        throw RuntimeException("Invalid update manifest")
    }
    val apkUrl = resolveApkUrl(baseUrl, manifestApkUrl)
    return UpdateInfo(versionCode, versionName, apkUrl)
}

internal fun readApkVersionCode(context: Context, apkFile: File): Int {
    val info = context.packageManager.getPackageArchiveInfo(
        apkFile.absolutePath,
        PackageManager.GET_ACTIVITIES,
    ) ?: throw RuntimeException("Could not read APK metadata")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        info.versionCode
    }
}

internal fun downloadAndInstallUpdate(context: Context, apkUrl: String, currentVersionCode: Int) {
    if (apkUrl.isBlank()) {
        throw RuntimeException("No update URL")
    }
    val cacheFile = File(context.cacheDir, "invictus-link-update.apk")
    val response = LinkHttp.downloadToFile(
        url = apkUrl,
        dest = cacheFile,
        connectTimeoutMs = 15000,
        readTimeoutMs = 120000,
    )
    if (response.code !in 200..299) {
        throw RuntimeException("Download failed (${response.code})")
    }
    if (cacheFile.length() < 1024L) {
        throw RuntimeException("Downloaded APK is too small")
    }
    val downloadedCode = readApkVersionCode(context, cacheFile)
    if (downloadedCode <= currentVersionCode) {
        throw RuntimeException(
            "Downloaded APK is v$downloadedCode but this device is v$currentVersionCode. " +
                "Your PC bridge may be serving an old file — restart the bridge and publish again.",
        )
    }
    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, cacheFile)
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(installIntent)
}

internal fun pairSession(baseUrl: String, bridgeToken: String, currentVersionCode: Int): SessionInfo {
    if (bridgeToken.isBlank()) {
        throw RuntimeException("Bridge token is required for first-time pairing")
    }
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/auth/login"
    val body = JSONObject().put("bridgeToken", bridgeToken).toString()
    val response = LinkHttp.postJson(url, body)
    if (response.code !in 200..299) {
        throw RuntimeException("Pairing failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    val sessionToken = json.optString("sessionToken", "")
    val expiresAt = json.optLong("expiresAt", 0L)
    if (sessionToken.isBlank() || expiresAt <= 0L) {
        throw RuntimeException("Invalid pairing response")
    }
    return SessionInfo(
        token = sessionToken,
        expiresAtMs = expiresAt,
        startedAtMs = System.currentTimeMillis(),
        appVersionCode = currentVersionCode,
    )
}

internal fun fetchPendingApprovals(baseUrl: String, token: String): List<PendingApprovalItem> {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/pending-approvals"
    val response = LinkHttp.get(url, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Pending approvals failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    val arr = json.optJSONArray("items") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            add(
                PendingApprovalItem(
                    taskId = item.optString("taskId", ""),
                    prompt = item.optString("prompt", ""),
                    projectId = item.optString("projectId", "unknown"),
                    createdAt = item.optLong("createdAt", 0L),
                ),
            )
        }
    }.filter { it.taskId.isNotBlank() }
}

internal fun approvePendingTask(baseUrl: String, token: String, taskId: String) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/pending-approvals/$taskId/approve"
    val response = LinkHttp.postEmptyJson(url, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Approve failed (${response.code}): ${response.body}")
    }
}

internal fun fetchDailyDigest(baseUrl: String, token: String): DailyDigestInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/daily-digest"
    val response = LinkHttp.get(url, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Daily digest failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    return DailyDigestInfo(
        date = json.optString("date", "unknown"),
        totalRuns = json.optInt("totalRuns", 0),
        successCount = json.optInt("successCount", 0),
        failureCount = json.optInt("failureCount", 0),
        successRate = json.optInt("successRate", 0),
        timeSavedMinutes = json.optInt("timeSavedMinutes", 0),
    )
}

internal fun fetchNotifyableBridgeEvents(
    baseUrl: String,
    token: String,
    limit: Int = 40,
): List<NotifyableBridgeEvent> {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/activity?limit=$limit"
    val response = LinkHttp.get(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        throw RuntimeException("Bridge activity failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    val arr = json.optJSONArray("entries") ?: return emptyList()
    val syncEvents = setOf("task_completed", "task_error", "cost_alert")
    val notifyEvents = setOf("task_error", "cost_alert")
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val event = item.optString("event", "")
            if (event !in syncEvents) continue
            val timestamp = item.optString("timestamp", "")
            val taskId = item.optString("taskId", "").ifBlank { null }
            val transcriptId = item.optString("transcriptId", "").ifBlank { null }
            val key = listOfNotNull(timestamp, event, taskId, transcriptId).joinToString(":")
            val preview = item.optString("promptPreview", "")
                .ifBlank { item.optString("summaryPreview", "") }
                .ifBlank { item.optString("message", "") }
                .ifBlank { item.optString("error", "") }
                .ifBlank { "Task finished on your PC" }
            val (title, bodyText) = when (event) {
                "task_completed" -> "Agent finished" to preview
                "task_error" -> "Agent failed" to preview
                "cost_alert" -> "Spending alert" to preview
                "cursor_agent_completed" -> {
                    val status = item.optString("status", "success")
                    if (status == "success" || status == "completed") {
                        "Cursor agent finished" to preview
                    } else {
                        "Cursor agent failed" to preview
                    }
                }
                else -> "PC update" to preview
            }
            add(
                NotifyableBridgeEvent(
                    key = key,
                    event = event,
                    title = title,
                    body = bodyText,
                    taskId = taskId,
                    notify = event in notifyEvents,
                ),
            )
        }
    }
}

internal fun fetchBridgeActivity(baseUrl: String, token: String, limit: Int = 50): List<WorkflowEntry> {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/activity?limit=$limit"
    val response = LinkHttp.get(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        throw RuntimeException("Bridge activity failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    val arr = json.optJSONArray("entries") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val event = item.optString("event", "")
            if (event.isBlank()) continue
            val timestamp = runCatching {
                java.time.Instant.parse(item.optString("timestamp")).toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
            val kind = when {
                event.endsWith("_error") -> WorkflowKind.Error
                event == "task_completed" || event == "task_approved" || event == "cursor_agent_completed" -> WorkflowKind.Success
                event.startsWith("build_") -> WorkflowKind.Build
                event == "task_created" || event == "task_approval_required" -> WorkflowKind.Prompt
                else -> WorkflowKind.Info
            }
            val detail = item.optString("promptPreview", "")
                .ifBlank { item.optString("summaryPreview", "") }
                .ifBlank { item.optString("error", "") }
            val message = if (detail.isNotBlank()) {
                "Bridge · ${event.replace('_', ' ')} — $detail"
            } else {
                "Bridge · ${event.replace('_', ' ')}"
            }
            add(WorkflowEntry(timestampMs = timestamp, message = message, kind = kind))
        }
    }
}

internal fun triggerArchiveApp(baseUrl: String, token: String?): String {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/backup-app"
    val response = LinkHttp.postEmptyJson(url, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Archive failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    return json.optString("path", "backup created")
}

internal fun startBuildApk(baseUrl: String, token: String?) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/build-apk"
    val response = LinkHttp.postEmptyJson(url, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Build start failed (${response.code}): ${response.body}")
    }
}

internal suspend fun triggerBuildAndWait(
    baseUrl: String,
    token: String?,
    onStatus: (String) -> Unit,
) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    withContext(Dispatchers.IO) { startBuildApk(normalizedBaseUrl, token) }

    repeat(240) {
        val info = withContext(Dispatchers.IO) { getBuildStatus(normalizedBaseUrl, token) }
        when (info.status) {
            "idle" -> withContext(Dispatchers.Main) { onStatus("Build status: idle") }
            "running" ->
                withContext(Dispatchers.Main) {
                    onStatus("Building... ${info.lastOutput.takeLast(140)}")
                }
            "completed" -> return
            "error" -> throw RuntimeException(info.error ?: "Build failed")
            else -> withContext(Dispatchers.Main) { onStatus("Build status: ${info.status}") }
        }
        delay(1500)
    }
    throw RuntimeException("Build timed out")
}

internal fun getBuildStatus(baseUrl: String, token: String?): BuildJobInfo {
    val url = "${baseUrl.trimEnd('/')}/admin/build-apk/status"
    val response = LinkHttp.get(url, token)
    if (response.code !in 200..299) {
        throw RuntimeException("Build status failed (${response.code}): ${response.body}")
    }
    val json = JSONObject(response.body)
    return BuildJobInfo(
        status = json.optString("status", "unknown"),
        error = if (json.has("error") && !json.isNull("error")) json.optString("error") else null,
        lastOutput = json.optString("lastOutput", ""),
    )
}

internal fun normalizeBaseUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        throw RuntimeException("Bridge URL is empty")
    }

    val firstHttp = trimmed.indexOf("http://").let { idx ->
        if (idx >= 0) idx else trimmed.indexOf("https://")
    }
    val candidate = if (firstHttp >= 0) trimmed.substring(firstHttp) else trimmed
    val secondHttp = candidate.indexOf("http://", startIndex = 1).let { idx ->
        if (idx >= 0) idx else candidate.indexOf("https://", startIndex = 1)
    }
    val singleUrl = if (secondHttp > 0) candidate.substring(0, secondHttp) else candidate

    val withScheme = if (singleUrl.startsWith("http://") || singleUrl.startsWith("https://")) {
        singleUrl
    } else {
        "http://$singleUrl"
    }

    val uri = try {
        URI(withScheme)
    } catch (e: Exception) {
        throw RuntimeException("Invalid Bridge URL: $input")
    }
    val host = uri.host ?: throw RuntimeException("Invalid Bridge URL host: $input")
    val scheme = uri.scheme ?: "http"
    val portPart = if (uri.port > 0) ":${uri.port}" else ""
    return "$scheme://$host$portPart"
}
