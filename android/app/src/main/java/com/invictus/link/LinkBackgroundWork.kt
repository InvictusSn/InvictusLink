package com.invictus.link

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "link_periodic_sync"
private const val BUILD_WATCH_WORK_NAME = "link_build_watch"

/** Background sync: update manifest checks + bridge completion events. */
class LinkPeriodicWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            LinkBackgroundSync.run(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

/** Polls PC build status after publish/agent build, then notifies when APK is ready. */
class LinkBuildWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val bridgeUrl = loadSavedBridgeUrl(applicationContext)
        if (bridgeUrl.isBlank()) return Result.failure()

        val session = loadSavedSession(applicationContext, getAppVersionCode(applicationContext))
        val token = session?.token

        val build = runCatching { getBuildStatus(bridgeUrl, token) }.getOrNull()
        when (build?.status) {
            "running" -> {
                if (runAttemptCount < 30) return Result.retry()
                showLinkNotification(
                    applicationContext,
                    "Publish still running",
                    "Your PC is still building Link. We'll check again for the update.",
                )
                LinkBackgroundWork.schedulePeriodicSync(applicationContext)
                return Result.success()
            }
            "error" -> {
                showLinkNotification(
                    applicationContext,
                    "Publish failed",
                    build.error?.take(200) ?: "The PC build failed. Open Link for details.",
                )
                return Result.failure()
            }
            "completed" -> {
                repeat(3) { attempt ->
                    if (LinkBackgroundSync.checkUpdateAndNotify(applicationContext, bridgeUrl)) {
                        return Result.success()
                    }
                    if (attempt < 2) kotlinx.coroutines.delay(4000)
                }
                showLinkNotification(
                    applicationContext,
                    "Publish complete",
                    "Your PC finished building. Open Link → Settings to check for the update.",
                )
                return Result.success()
            }
            else -> {
                if (runAttemptCount < 20) return Result.retry()
                if (LinkBackgroundSync.checkUpdateAndNotify(applicationContext, bridgeUrl)) {
                    return Result.success()
                }
                return Result.success()
            }
        }
    }
}

object LinkBackgroundSync {
    fun run(context: Context) {
        val bridgeUrl = loadSavedBridgeUrl(context)
        if (bridgeUrl.isBlank()) return
        val session = loadSavedSession(context, getAppVersionCode(context)) ?: return
        checkUpdateAndNotify(context, bridgeUrl)
        pollCompletionsAndNotify(context, bridgeUrl, session.token)
    }

    fun checkUpdateAndNotify(context: Context, bridgeUrl: String): Boolean {
        val currentCode = getAppVersionCode(context)
        val info = runCatching { checkForUpdate(bridgeUrl) }.getOrNull() ?: return false
        if (info.versionCode <= currentCode) return false
        savePendingUpdateInfo(context, info)
        if (info.versionCode <= getLastNotifiedUpdateCode(context)) return false
        showLinkNotification(
            context,
            "Update available",
            "Invictus Link v${info.versionName} is ready. Open Link → Settings to install.",
        )
        setLastNotifiedUpdateCode(context, info.versionCode)
        return true
    }

    private fun pollCompletionsAndNotify(context: Context, bridgeUrl: String, token: String) {
        val events = runCatching {
            fetchNotifyableBridgeEvents(bridgeUrl, token)
        }.getOrElse { return }
        var seen = loadSeenCompletionKeys(context)
        val fresh = events.filter { it.key !in seen }
        if (fresh.isEmpty()) return
        for (event in fresh) {
            val taskId = event.taskId
            if (
                taskId != null &&
                (event.event == "task_completed" || event.event == "task_error")
            ) {
                patchHistoryWithTaskResult(context, bridgeUrl, token, taskId)
            }
            if (!event.notify) continue
            if (shouldSkipCompletionNotification(context, event, foregroundTaskId = null)) continue
            showLinkNotification(context, event.title, event.body)
        }
        seen = seen + fresh.map { it.key }
        saveSeenCompletionKeys(context, seen)
    }
}

object LinkBackgroundWork {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<LinkPeriodicWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleBuildWatch(context: Context) {
        val request = OneTimeWorkRequestBuilder<LinkBuildWatchWorker>()
            .setConstraints(networkConstraints)
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BUILD_WATCH_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

internal fun promptImpliesLinkPublish(prompt: String): Boolean {
    val lowered = prompt.lowercase()
    val publishWords = listOf("publish", "release", "ship", "build apk", "build and publish", "ota")
    val linkWords = listOf("link", "apk", "android app", "mobile app", "invictus link")
    val hasPublish = publishWords.any { lowered.contains(it) }
    val hasLink = linkWords.any { lowered.contains(it) }
    val updatePublish = lowered.contains("update") &&
        (lowered.contains("publish") || lowered.contains("release") || lowered.contains("build"))
    return (hasPublish && hasLink) || updatePublish
}

internal fun savePendingUpdateInfo(context: Context, info: UpdateInfo) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(PREF_PENDING_UPDATE_CODE, info.versionCode)
        .putString(PREF_PENDING_UPDATE_NAME, info.versionName)
        .putString(PREF_PENDING_UPDATE_URL, info.apkUrl)
        .apply()
}

internal fun loadPendingUpdateInfo(context: Context): UpdateInfo? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val code = prefs.getInt(PREF_PENDING_UPDATE_CODE, 0)
    val name = prefs.getString(PREF_PENDING_UPDATE_NAME, null)
    val url = prefs.getString(PREF_PENDING_UPDATE_URL, null)
    if (code <= 0 || name.isNullOrBlank() || url.isNullOrBlank()) return null
    return UpdateInfo(code, name, url)
}

internal fun clearPendingUpdateInfo(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(PREF_PENDING_UPDATE_CODE)
        .remove(PREF_PENDING_UPDATE_NAME)
        .remove(PREF_PENDING_UPDATE_URL)
        .apply()
}
