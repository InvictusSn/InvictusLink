package com.invictus.link

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash diagnostics: capture every uncaught exception to a local file so the
 * user can review it in Settings and upload it to the PC bridge after a crash.
 */

private const val CRASH_LOG_FILE = "crash-log.txt"
private const val CRASH_LOG_MAX_BYTES = 256 * 1024

/** Install once at app start. Chains to the previous handler so Android still
 *  shows its normal crash flow after we save the report. */
fun installCrashLogger(context: Context) {
    val appContext = context.applicationContext
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    if (previous is LinkCrashHandler) return
    Thread.setDefaultUncaughtExceptionHandler(LinkCrashHandler(appContext, previous))
}

private class LinkCrashHandler(
    private val context: Context,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashReport(context, thread, throwable)
        } catch (_: Throwable) {
            // Never let crash logging cause a second failure.
        }
        previous?.uncaughtException(thread, throwable)
    }
}

private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val stackTrace = StringWriter().also { sw ->
        throwable.printStackTrace(PrintWriter(sw))
    }.toString()
    val report = buildString {
        appendLine("==== CRASH $stamp ====")
        appendLine("App: Invictus Link ${getAppVersionName(context)} (code ${getAppVersionCode(context)})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        appendLine("Thread: ${thread.name}")
        appendLine(stackTrace.trimEnd())
        appendLine("==== END CRASH ====")
        appendLine()
    }

    val file = crashLogFile(context)
    val existing = if (file.exists()) file.readText() else ""
    // Newest crash first; trim the tail if the file grows too large.
    var combined = report + existing
    if (combined.length > CRASH_LOG_MAX_BYTES) {
        combined = combined.take(CRASH_LOG_MAX_BYTES)
    }
    file.writeText(combined)
}

internal fun crashLogFile(context: Context): File = File(context.filesDir, CRASH_LOG_FILE)

internal fun readCrashLog(context: Context): String? {
    val file = crashLogFile(context)
    if (!file.exists()) return null
    val text = runCatching { file.readText() }.getOrNull()
    return text?.takeIf { it.isNotBlank() }
}

internal fun clearCrashLog(context: Context) {
    runCatching { crashLogFile(context).delete() }
}

/** First "==== CRASH <stamp> ====" header in the log, if any. */
internal fun latestCrashTimestamp(log: String): String? {
    val match = Regex("==== CRASH (.+?) ====").find(log) ?: return null
    return match.groupValues.getOrNull(1)
}

internal fun countCrashes(log: String): Int =
    Regex("==== CRASH ").findAll(log).count()

/** Upload the crash log text to the bridge; returns the saved file name on the PC. */
internal fun uploadCrashLog(baseUrl: String, token: String, content: String): String {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/crashlog"
    val response = LinkHttp.postText(
        url = url,
        text = content,
        token = token,
        connectTimeoutMs = 15000,
        readTimeoutMs = 30000,
    )
    if (response.code !in 200..299) {
        if (response.code == 404) {
            throw RuntimeException("Bridge needs an update for crash logs — restart the PC bridge")
        }
        throw RuntimeException("Crash log upload failed (${response.code})")
    }
    val name = runCatching {
        org.json.JSONObject(response.body).optString("file", "")
    }.getOrNull().orEmpty()
    return name.ifBlank { "crash log" }
}
