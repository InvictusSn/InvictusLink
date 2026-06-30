package com.invictus.link

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        setContent { InvictusLinkScreen() }
    }
}

private const val PREF_SESSION_TOKEN = "session_token"
private const val PREF_SESSION_EXPIRES_AT = "session_expires_at"
private const val PREF_SESSION_APP_VERSION = "session_app_version"
private const val PREF_SESSION_STARTED_AT = "session_started_at"
private const val PREF_BRIDGE_URL = "bridge_base_url"
private const val PREF_WORKFLOW_LOG = "workflow_log_json"
private const val PREF_PROMPT_HISTORY = "prompt_history_json"
private const val PREF_SELECTED_PROJECT = "selected_project_id"
private const val MAX_PERSISTED_LOG_ENTRIES = 200
private const val MAX_PROMPT_HISTORY = 20
private const val NOTIFICATION_CHANNEL_ID = "app_alerts"
const val PREFS_NAME = "invictus_prefs"

internal suspend fun submitAndWait(
    baseUrl: String,
    prompt: String,
    projectId: String?,
    token: String?,
    onStatus: (String) -> Unit
): String {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val created = createTask(normalizedBaseUrl, prompt, projectId, token)
    if (created.requiresApproval || created.status == "awaiting_approval") {
        throw RuntimeException("AWAITING_APPROVAL:${created.taskId}")
    }
    val taskId = created.taskId
    // Poll slightly past the bridge's 10-minute task timeout so the bridge,
    // not the app, decides when a long task has failed.
    repeat(660) {
        val task = getTask(normalizedBaseUrl, taskId)
        when (task.status) {
            "queued" -> withContext(Dispatchers.Main) { onStatus("Queued") }
            "running" -> withContext(Dispatchers.Main) { onStatus("Running") }
            "completed" -> return task.output ?: "(No output)"
            "error" -> throw RuntimeException(task.error ?: "Task failed")
            else -> withContext(Dispatchers.Main) { onStatus(task.status) }
        }
        delay(1000)
    }
    throw RuntimeException("Timed out waiting for task")
}

private data class TaskResponse(
    val status: String,
    val output: String?,
    val error: String?
)

internal data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

private data class BuildJobInfo(
    val status: String,
    val error: String?,
    val lastOutput: String
)

private data class CreateTaskResponse(
    val taskId: String,
    val status: String,
    val requiresApproval: Boolean
)

private fun createTask(
    baseUrl: String,
    prompt: String,
    projectId: String?,
    token: String?
): CreateTaskResponse {
    val url = URL("${baseUrl.trimEnd('/')}/tasks")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (!token.isNullOrBlank()) {
            setRequestProperty("Authorization", "Bearer $token")
        }
    }
    val body = JSONObject()
        .put("prompt", prompt)
        .apply {
            // Omit projectId entirely when unknown; the bridge falls back
            // to its first configured project.
            if (!projectId.isNullOrBlank()) put("projectId", projectId)
        }
        .put("outputStyle", "short")
        .toString()

    OutputStreamWriter(conn.outputStream).use { it.write(body) }
    val code = conn.responseCode
    val responseText = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Create task failed ($code): $responseText")
    }
    val json = JSONObject(responseText)
    return CreateTaskResponse(
        taskId = json.getString("taskId"),
        status = json.optString("status", "queued"),
        requiresApproval = json.optBoolean("requiresApproval", false)
    )
}

private fun getTask(baseUrl: String, taskId: String): TaskResponse {
    val url = URL("${baseUrl.trimEnd('/')}/tasks/$taskId")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
    }
    val code = conn.responseCode
    val responseText = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Get task failed ($code): $responseText")
    }
    val json = JSONObject(responseText)
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
    return TaskResponse(
        status = json.optString("status", "unknown"),
        output = output,
        error = error
    )
}

private fun readHttpBody(conn: HttpURLConnection, success: Boolean): String {
    val stream = if (success) conn.inputStream else conn.errorStream
    if (stream == null) return ""
    return BufferedReader(InputStreamReader(stream)).use { reader ->
        buildString {
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                append(line)
            }
        }
    }
}

internal fun getAppVersionName(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "unknown"
    } catch (_: PackageManager.NameNotFoundException) {
        "unknown"
    }
}

internal fun getAppVersionCode(context: Context): Int {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    } catch (_: Exception) {
        1
    }
}

private val TAILSCALE_PACKAGES = listOf("com.tailscale.ipn")

internal fun extractBridgeHost(baseUrl: String): String {
    return runCatching {
        val normalized = normalizeBaseUrl(baseUrl)
        URI(normalized).host ?: ""
    }.getOrDefault("")
}

internal fun isTailscaleHost(host: String): Boolean {
    if (host.isBlank()) return false
    val parts = host.split(".")
    if (parts.size != 4) return false
    val octets = parts.mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) return false
    // Tailscale CGNAT range: 100.64.0.0/10
    return octets[0] == 100 && octets[1] in 64..127
}

internal fun isInvictusVpnHost(host: String): Boolean {
    if (host.isBlank()) return false
    val parts = host.split(".")
    if (parts.size != 4) return false
    val octets = parts.mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) return false
    // Invictus Networks WireGuard subnet
    return octets[0] == 10 && octets[1] == 66 && octets[2] == 66
}

internal fun isTailscaleInstalled(context: Context): Boolean {
    val packageManager = context.packageManager
    for (packageName in TAILSCALE_PACKAGES) {
        if (packageManager.getLaunchIntentForPackage(packageName) != null) {
            return true
        }
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
        if (installed) return true
    }
    return false
}

internal fun isTailscaleVpnActive(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    for (network in connectivityManager.allNetworks) {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return true
        }
    }
    return false
}

internal fun evaluateConnectionDiagnostics(
    context: Context,
    baseUrl: String,
): ConnectionDiagnostics {
    if (baseUrl.isBlank()) {
        return ConnectionDiagnostics(
            usesTailscaleAddress = false,
            usesInvictusVpnAddress = false,
            tailscaleInstalled = isTailscaleInstalled(context),
            tailscaleVpnActive = isTailscaleVpnActive(context),
            bridgeReachable = false,
        )
    }
    val host = extractBridgeHost(baseUrl)
    val usesTailscale = isTailscaleHost(host)
    val usesInvictusVpn = isInvictusVpnHost(host)
    val installed = isTailscaleInstalled(context)
    val vpnActive = isTailscaleVpnActive(context)
    val bridgeReachable = if (baseUrl.isBlank()) {
        false
    } else {
        runCatching { checkBridgeHealth(baseUrl) }.getOrDefault(false)
    }
    return ConnectionDiagnostics(
        usesTailscaleAddress = usesTailscale,
        usesInvictusVpnAddress = usesInvictusVpn,
        tailscaleInstalled = installed,
        tailscaleVpnActive = vpnActive,
        bridgeReachable = bridgeReachable,
    )
}

internal fun openTailscaleApp(context: Context) {
    val packageManager = context.packageManager
    for (packageName in TAILSCALE_PACKAGES) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
    val storeIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=${TAILSCALE_PACKAGES.first()}")
    )
    context.startActivity(storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun checkBridgeHealth(baseUrl: String): Boolean {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/health")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 4000
        readTimeout = 4000
    }
    return conn.responseCode in 200..299
}

internal fun fetchProjects(baseUrl: String): List<ProjectInfo> {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/health")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 4000
        readTimeout = 4000
    }
    val code = conn.responseCode
    val body = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Health check failed ($code)")
    }
    val json = JSONObject(body)
    val arr = json.optJSONArray("projects") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isBlank()) continue
            add(ProjectInfo(id = id, name = item.optString("name", id)))
        }
    }
}

internal fun createLinkSession(
    baseUrl: String,
    token: String?,
    name: String? = null,
): ProjectInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/api/sessions")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (!token.isNullOrBlank()) {
            setRequestProperty("Authorization", "Bearer $token")
        }
        connectTimeout = 8000
        readTimeout = 8000
    }
    val body = JSONObject().apply {
        if (!name.isNullOrBlank()) put("name", name)
    }
    conn.outputStream.use { it.write(body.toString().toByteArray()) }
    val code = conn.responseCode
    val responseText = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        val err = runCatching { JSONObject(responseText).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(
            when (code) {
                404 -> "Bridge needs an update — restart the PC bridge, then try again"
                else -> err ?: "Create session failed ($code)"
            },
        )
    }
    val json = JSONObject(responseText)
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
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/api/sessions/$sessionId")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "DELETE"
        if (!token.isNullOrBlank()) {
            setRequestProperty("Authorization", "Bearer $token")
        }
        connectTimeout = 8000
        readTimeout = 8000
    }
    val code = conn.responseCode
    val responseText = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        val err = runCatching { JSONObject(responseText).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Delete session failed ($code)")
    }
}

internal fun loadSelectedProjectId(context: Context): String? {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_SELECTED_PROJECT, null)
}

internal fun saveSelectedProjectId(context: Context, projectId: String?) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .apply {
            if (projectId.isNullOrBlank()) remove(PREF_SELECTED_PROJECT)
            else putString(PREF_SELECTED_PROJECT, projectId)
        }
        .apply()
}

internal fun loadPromptHistory(context: Context): List<PromptExchange> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_PROMPT_HISTORY, null)
        ?: return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    PromptExchange(
                        timestampMs = obj.optLong("t", System.currentTimeMillis()),
                        prompt = obj.optString("p", ""),
                        response = obj.optString("r", ""),
                        projectId = obj.optString("j", ""),
                        ok = obj.optBoolean("ok", true),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun savePromptHistory(context: Context, history: List<PromptExchange>) {
    val arr = org.json.JSONArray()
    history.takeLast(MAX_PROMPT_HISTORY).forEach { entry ->
        arr.put(
            JSONObject()
                .put("t", entry.timestampMs)
                .put("p", entry.prompt)
                .put("r", entry.response)
                .put("j", entry.projectId)
                .put("ok", entry.ok)
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PROMPT_HISTORY, arr.toString())
        .apply()
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
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/download/latest.json")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
    }
    val code = conn.responseCode
    val responseText = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Update manifest failed ($code): $responseText")
    }
    val json = JSONObject(responseText)
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
    val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 120000
    }
    val code = conn.responseCode
    if (code !in 200..299) {
        throw RuntimeException("Download failed ($code)")
    }
    conn.inputStream.use { input ->
        FileOutputStream(cacheFile).use { output ->
            input.copyTo(output)
        }
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
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/auth/login")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
    }
    val body = JSONObject().put("bridgeToken", bridgeToken).toString()
    OutputStreamWriter(conn.outputStream).use { it.write(body) }
    val code = conn.responseCode
    val responseText = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Pairing failed ($code): $responseText")
    }
    val json = JSONObject(responseText)
    val sessionToken = json.optString("sessionToken", "")
    val expiresAt = json.optLong("expiresAt", 0L)
    if (sessionToken.isBlank() || expiresAt <= 0L) {
        throw RuntimeException("Invalid pairing response")
    }
    return SessionInfo(
        token = sessionToken,
        expiresAtMs = expiresAt,
        startedAtMs = System.currentTimeMillis(),
        appVersionCode = currentVersionCode
    )
}

internal fun fetchPendingApprovals(baseUrl: String, token: String): List<PendingApprovalItem> {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/admin/pending-approvals")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Authorization", "Bearer $token")
    }
    val code = conn.responseCode
    val responseText = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Pending approvals failed ($code): $responseText")
    }
    val json = JSONObject(responseText)
    val arr = json.optJSONArray("items") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            add(
                PendingApprovalItem(
                    taskId = item.optString("taskId", ""),
                    prompt = item.optString("prompt", ""),
                    projectId = item.optString("projectId", "unknown"),
                    createdAt = item.optLong("createdAt", 0L)
                )
            )
        }
    }.filter { it.taskId.isNotBlank() }
}

internal fun approvePendingTask(baseUrl: String, token: String, taskId: String) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/admin/pending-approvals/$taskId/approve")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Bearer $token")
    }
    OutputStreamWriter(conn.outputStream).use { it.write("{}") }
    val code = conn.responseCode
    val body = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Approve failed ($code): $body")
    }
}

internal fun fetchDailyDigest(baseUrl: String, token: String): DailyDigestInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/admin/daily-digest")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Authorization", "Bearer $token")
    }
    val code = conn.responseCode
    val body = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Daily digest failed ($code): $body")
    }
    val json = JSONObject(body)
    return DailyDigestInfo(
        date = json.optString("date", "unknown"),
        totalRuns = json.optInt("totalRuns", 0),
        successCount = json.optInt("successCount", 0),
        failureCount = json.optInt("failureCount", 0),
        successRate = json.optInt("successRate", 0),
        timeSavedMinutes = json.optInt("timeSavedMinutes", 0)
    )
}

internal fun loadSavedSession(context: Context, currentVersionCode: Int): SessionInfo? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val token = prefs.getString(PREF_SESSION_TOKEN, null) ?: return null
    val expiresAt = prefs.getLong(PREF_SESSION_EXPIRES_AT, 0L)
    val appVersion = prefs.getInt(PREF_SESSION_APP_VERSION, -1)
    if (appVersion != currentVersionCode) {
        clearSession(context)
        return null
    }
    val startedAt = prefs.getLong(PREF_SESSION_STARTED_AT, System.currentTimeMillis())
    return SessionInfo(
        token = token,
        expiresAtMs = expiresAt,
        startedAtMs = startedAt,
        appVersionCode = appVersion
    )
}

internal fun saveSession(context: Context, session: SessionInfo) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(PREF_SESSION_TOKEN, session.token)
        .putLong(PREF_SESSION_EXPIRES_AT, session.expiresAtMs)
        .putLong(PREF_SESSION_STARTED_AT, session.startedAtMs)
        .putInt(PREF_SESSION_APP_VERSION, session.appVersionCode)
        .apply()
}

internal fun clearSession(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .remove(PREF_SESSION_TOKEN)
        .remove(PREF_SESSION_EXPIRES_AT)
        .remove(PREF_SESSION_STARTED_AT)
        .remove(PREF_SESSION_APP_VERSION)
        .apply()
}

internal fun loadSavedBridgeUrl(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_BRIDGE_URL, "")
        ?.trim()
        .orEmpty()
}

internal fun saveBridgeUrl(context: Context, url: String) {
    val trimmed = url.trim()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_BRIDGE_URL, trimmed)
        .apply()
}

internal fun loadWorkflowLog(context: Context): List<WorkflowEntry> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_WORKFLOW_LOG, null)
        ?: return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val kindName = obj.optString("k", WorkflowKind.Info.name)
                val kind = runCatching { WorkflowKind.valueOf(kindName) }.getOrDefault(WorkflowKind.Info)
                add(
                    WorkflowEntry(
                        timestampMs = obj.optLong("t", System.currentTimeMillis()),
                        message = obj.optString("m", ""),
                        kind = kind,
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun saveWorkflowLog(context: Context, log: List<WorkflowEntry>) {
    val arr = org.json.JSONArray()
    log.takeLast(MAX_PERSISTED_LOG_ENTRIES).forEach { entry ->
        arr.put(
            org.json.JSONObject()
                .put("t", entry.timestampMs)
                .put("m", entry.message)
                .put("k", entry.kind.name)
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_WORKFLOW_LOG, arr.toString())
        .apply()
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Invictus Link"
        val descriptionText = "Build status, approvals, and session reminders"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

private fun showBuildNotification(context: Context, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    with(NotificationManagerCompat.from(context)) {
        notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }
}

internal fun triggerArchiveApp(baseUrl: String, token: String?): String {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = URL("${normalizedBaseUrl.trimEnd('/')}/admin/backup-app")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (!token.isNullOrBlank()) {
            setRequestProperty("Authorization", "Bearer $token")
        }
    }
    OutputStreamWriter(conn.outputStream).use { it.write("{}") }
    val code = conn.responseCode
    val body = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Archive failed ($code): $body")
    }
    val json = JSONObject(body)
    return json.optString("path", "backup created")
}

internal suspend fun triggerBuildAndWait(
    baseUrl: String,
    token: String?,
    onStatus: (String) -> Unit
){
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val startBody = withContext(Dispatchers.IO) {
        val startUrl = URL("${normalizedBaseUrl.trimEnd('/')}/admin/build-apk")
        val startConn = (startUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        OutputStreamWriter(startConn.outputStream).use { it.write("{}") }
        val startCode = startConn.responseCode
        val body = readHttpBody(startConn, startCode in 200..299)
        if (startCode !in 200..299) {
            throw RuntimeException("Build start failed ($startCode): $body")
        }
        body
    }
    if (startBody.isBlank()) { /* no-op */ }

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

private fun getBuildStatus(baseUrl: String, token: String?): BuildJobInfo {
    val url = URL("${baseUrl.trimEnd('/')}/admin/build-apk/status")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        if (!token.isNullOrBlank()) {
            setRequestProperty("Authorization", "Bearer $token")
        }
    }
    val code = conn.responseCode
    val body = readHttpBody(conn, code in 200..299)
    if (code !in 200..299) {
        throw RuntimeException("Build status failed ($code): $body")
    }
    val json = JSONObject(body)
    return BuildJobInfo(
        status = json.optString("status", "unknown"),
        error = if (json.has("error") && !json.isNull("error")) json.optString("error") else null,
        lastOutput = json.optString("lastOutput", "")
    )
}

private fun normalizeBaseUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        throw RuntimeException("Bridge URL is empty")
    }

    // Handle accidental paste collisions like "...3003http:..."
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

internal suspend fun authenticateBiometric(activity: FragmentActivity): Boolean {
    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return false

    return suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }

                override fun onAuthenticationFailed() {
                    // Keep prompt active; final success/error decides result.
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm your identity")
            .setSubtitle("Confirm to connect to your PC")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }
}

