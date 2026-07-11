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
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger(this)
        enableEdgeToEdge()
        createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        LinkBackgroundWork.schedulePeriodicSync(this)
        setContent { InvictusLinkScreen() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
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
private const val PREF_LAST_NOTIFIED_UPDATE_CODE = "last_notified_update_code"
private const val PREF_SEEN_COMPLETION_KEYS = "seen_completion_keys_json"
internal const val PREF_PENDING_UPDATE_CODE = "pending_update_code"
internal const val PREF_PENDING_UPDATE_NAME = "pending_update_name"
internal const val PREF_PENDING_UPDATE_URL = "pending_update_url"
internal const val PREF_PINNED_PROVIDER_IDS = "pinned_provider_ids_json"
private const val PREF_PHONE_TASK_IDS = "phone_originated_task_ids_json"
private const val MAX_PERSISTED_LOG_ENTRIES = 200
private const val MAX_PROMPT_HISTORY = 20
private const val NOTIFICATION_CHANNEL_ID = "app_alerts"
const val PREFS_NAME = "invictus_prefs"

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
    return octets[0] == 100 && octets[1] in 64..127
}

internal fun isInvictusVpnHost(host: String): Boolean {
    if (host.isBlank()) return false
    val parts = host.split(".")
    if (parts.size != 4) return false
    val octets = parts.mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) return false
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

internal const val ATTACHMENT_MAX_BYTES = 25L * 1024L * 1024L

internal fun resolveAttachmentInfo(context: Context, uri: Uri): PendingAttachment {
    var name = uri.lastPathSegment ?: "attachment"
    var size = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
        }
    }
    val mime = context.contentResolver.getType(uri)
        ?: android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"
    return PendingAttachment(uri = uri, name = name, mimeType = mime, sizeBytes = size)
}

internal fun decodeAttachmentThumbnail(
    context: Context,
    uri: Uri,
    targetPx: Int = 128,
): android.graphics.Bitmap? {
    return runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= targetPx &&
            bounds.outHeight / (sample * 2) >= targetPx
        ) {
            sample *= 2
        }
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}

internal fun createCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera")
    dir.mkdirs()
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val file = File(dir, "IMG_$stamp.jpg")
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
}

internal fun loadPinnedProviderIds(context: Context): Set<String> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_PINNED_PROVIDER_IDS, null) ?: return emptySet()
    val arr = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return emptySet()
    return buildSet {
        for (i in 0 until arr.length()) {
            val id = arr.optString(i, "")
            if (id.isNotBlank()) add(id)
        }
    }
}

internal fun savePinnedProviderIds(context: Context, ids: Set<String>) {
    val arr = org.json.JSONArray()
    ids.forEach { arr.put(it) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PINNED_PROVIDER_IDS, arr.toString())
        .apply()
}

internal fun sortProvidersForDisplay(
    providers: List<AiProviderInfo>,
    pinnedIds: Set<String>,
): List<AiProviderInfo> {
    if (pinnedIds.isEmpty()) return providers
    return providers.sortedWith(
        compareByDescending<AiProviderInfo> { pinnedIds.contains(it.id) }
            .thenByDescending { it.isActive }
            .thenBy { it.label.lowercase() },
    )
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
                        taskId = obj.optString("i", "").ifBlank { null },
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
                .apply { entry.taskId?.let { put("i", it) } }
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PROMPT_HISTORY, arr.toString())
        .apply()
}

/** Fill in or correct history when a phone prompt finishes after a disconnect or late sync. */
internal fun patchHistoryWithTaskResult(
    context: Context,
    baseUrl: String,
    token: String,
    taskId: String,
): Boolean {
    if (taskId.isBlank()) return false
    val task = runCatching { fetchTask(baseUrl, taskId, token) }.getOrNull() ?: return false
    if (task.status != "completed" && task.status != "error") return false

    val history = loadPromptHistory(context).toMutableList()
    val idx = history.indexOfLast { it.taskId == taskId }
    if (idx < 0) return false

    val response = formatTaskHistoryResponse(task.status, task.output, task.error)
    val ok = task.status == "completed"
    val old = history[idx]
    if (old.ok == ok && old.response == response) return false
    if (old.ok && !ok) return false

    history[idx] = old.copy(response = response, ok = ok)
    savePromptHistory(context, history)
    return true
}

internal fun loadSavedSession(context: Context, currentVersionCode: Int): SessionInfo? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val legacyToken = prefs.getString(PREF_SESSION_TOKEN, null)
    if (!legacyToken.isNullOrBlank()) {
        LinkSecureStore.saveSessionToken(context, legacyToken)
        prefs.edit().remove(PREF_SESSION_TOKEN).apply()
    }
    val token = LinkSecureStore.loadSessionToken(context) ?: return null
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
    LinkSecureStore.saveSessionToken(context, session.token)
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .remove(PREF_SESSION_TOKEN)
        .putLong(PREF_SESSION_EXPIRES_AT, session.expiresAtMs)
        .putLong(PREF_SESSION_STARTED_AT, session.startedAtMs)
        .putInt(PREF_SESSION_APP_VERSION, session.appVersionCode)
        .apply()
}

internal fun clearSession(context: Context) {
    LinkSecureStore.clearSessionToken(context)
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
        val descriptionText = "Updates, approvals, spending alerts, and publish status"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

internal fun showLinkNotification(context: Context, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    val launchIntent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
        context,
        0,
        launchIntent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_link)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    with(NotificationManagerCompat.from(context)) {
        notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }
}

internal fun loadSeenCompletionKeys(context: Context): Set<String> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_SEEN_COMPLETION_KEYS, null) ?: return emptySet()
    val arr = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return emptySet()
    return buildSet {
        for (i in 0 until arr.length()) {
            val key = arr.optString(i, "")
            if (key.isNotBlank()) add(key)
        }
    }
}

internal fun saveSeenCompletionKeys(context: Context, keys: Set<String>) {
    val arr = org.json.JSONArray()
    keys.toList().takeLast(80).forEach { arr.put(it) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_SEEN_COMPLETION_KEYS, arr.toString())
        .apply()
}

internal fun loadPhoneOriginatedTaskIds(context: Context): Set<String> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_PHONE_TASK_IDS, null) ?: return emptySet()
    val arr = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return emptySet()
    return buildSet {
        for (i in 0 until arr.length()) {
            val id = arr.optString(i, "")
            if (id.isNotBlank()) add(id)
        }
    }
}

internal fun addPhoneOriginatedTaskId(context: Context, taskId: String) {
    if (taskId.isBlank()) return
    val next = loadPhoneOriginatedTaskIds(context) + taskId
    val arr = org.json.JSONArray()
    next.toList().takeLast(20).forEach { arr.put(it) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PHONE_TASK_IDS, arr.toString())
        .apply()
}

internal fun removePhoneOriginatedTaskId(context: Context, taskId: String) {
    if (taskId.isBlank()) return
    val next = loadPhoneOriginatedTaskIds(context) - taskId
    val arr = org.json.JSONArray()
    next.forEach { arr.put(it) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PHONE_TASK_IDS, arr.toString())
        .apply()
}

internal fun suppressCompletionNotificationsForTask(
    context: Context,
    baseUrl: String,
    token: String?,
    taskId: String,
) {
    if (taskId.isBlank()) return
    removePhoneOriginatedTaskId(context, taskId)
    if (token.isNullOrBlank() || baseUrl.isBlank()) return
    runCatching {
        val events = fetchNotifyableBridgeEvents(baseUrl, token)
        val keys = events.filter { it.taskId == taskId }.map { it.key }
        if (keys.isNotEmpty()) {
            saveSeenCompletionKeys(context, loadSeenCompletionKeys(context) + keys)
        }
    }
}

internal fun shouldSkipCompletionNotification(
    context: Context,
    event: NotifyableBridgeEvent,
    foregroundTaskId: String?,
): Boolean {
    val taskId = event.taskId ?: return false
    if (taskId == foregroundTaskId) return true
    return taskId in loadPhoneOriginatedTaskIds(context)
}

internal fun getLastNotifiedUpdateCode(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(PREF_LAST_NOTIFIED_UPDATE_CODE, 0)
}

internal fun setLastNotifiedUpdateCode(context: Context, versionCode: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(PREF_LAST_NOTIFIED_UPDATE_CODE, versionCode)
        .apply()
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
