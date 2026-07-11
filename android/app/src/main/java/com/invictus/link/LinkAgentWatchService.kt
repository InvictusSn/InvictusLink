package com.invictus.link

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AGENT_CHANNEL_ID = "agent_watch"
private const val AGENT_NOTIFICATION_ID = 9001
private const val EXTRA_TASK_ID = "task_id"

/** Keeps polling the PC bridge while the user switches to other apps. */
class LinkAgentWatchService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureAgentNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWatch()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
                    ?: loadActivePhoneTask(this)?.taskId
                if (taskId.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWatch(taskId)
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWatch(taskId: String) {
        val active = loadActivePhoneTask(this)
        if (active == null || active.taskId != taskId) {
            stopSelf()
            return
        }
        ServiceCompat.startForeground(
            this,
            AGENT_NOTIFICATION_ID,
            buildOngoingNotification(active),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        if (watchJob?.isActive == true) return
        watchJob = scope.launch { pollUntilDone(taskId) }
    }

    private suspend fun pollUntilDone(taskId: String) {
        val bridgeUrl = loadSavedBridgeUrl(this)
        val session = loadSavedSession(this, getAppVersionCode(this))
        val token = session?.token
        if (bridgeUrl.isBlank() || token.isNullOrBlank()) {
            clearActivePhoneTask(this)
            stopWatch()
            return
        }

        var consecutivePollFailures = 0
        var approvalNotified = false
        var polls = 0
        while (polls < 660) {
            polls += 1
            val active = loadActivePhoneTask(this) ?: break
            if (active.taskId != taskId) break

            val task = try {
                fetchTask(bridgeUrl, taskId, token).also { consecutivePollFailures = 0 }
            } catch (e: Exception) {
                if (isTaskMissingOnBridge(e)) {
                    completePhoneOriginatedTask(
                        this,
                        bridgeUrl,
                        token,
                        active,
                        TaskResponse(
                            status = "error",
                            output = active.partialOutput,
                            error = BRIDGE_RESTART_TASK_ERROR,
                        ),
                    )
                    stopWatch()
                    return
                }
                consecutivePollFailures += 1
                if (consecutivePollFailures >= 3) {
                    saveActivePhoneTask(
                        this,
                        active.copy(statusLabel = "Reconnecting to PC…"),
                    )
                    updateOngoingNotification(active.copy(statusLabel = "Reconnecting to PC…"))
                }
                delay(2000)
                continue
            }

            val statusLabel = when (task.status) {
                "awaiting_approval" -> {
                    if (!approvalNotified) approvalNotified = true
                    "Waiting for approval"
                }
                "queued" -> "Queued"
                "running" -> "Running"
                "completed" -> "Done"
                "error" -> "Failed"
                else -> task.status.replaceFirstChar { it.uppercase() }
            }
            val partial = task.output?.takeIf { it.isNotBlank() }
            val updated = active.copy(
                statusLabel = statusLabel,
                partialOutput = partial ?: active.partialOutput,
                routingNote = task.routingNote ?: active.routingNote,
                grokCostUsd = task.usage?.costUsd ?: active.grokCostUsd,
            )
            saveActivePhoneTask(this, updated)
            updateOngoingNotification(updated)

            when (task.status) {
                "completed", "error" -> {
                    completePhoneOriginatedTask(this, bridgeUrl, token, updated, task)
                    stopWatch()
                    return
                }
            }
            delay(1000)
        }
        loadActivePhoneTask(this)?.let { active ->
            completePhoneOriginatedTask(
                this,
                bridgeUrl,
                token,
                active,
                TaskResponse(
                    status = "error",
                    output = active.partialOutput,
                    error = "Timed out waiting for the PC agent.",
                ),
            )
        }
        stopWatch()
    }

    private fun updateOngoingNotification(task: ActivePhoneTask) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(AGENT_NOTIFICATION_ID, buildOngoingNotification(task))
    }

    private fun buildOngoingNotification(task: ActivePhoneTask): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val preview = task.partialOutput?.take(120) ?: task.prompt.take(120)
        return NotificationCompat.Builder(this, AGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("Agent working on your PC")
            .setContentText("${task.statusLabel} · $preview")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${task.statusLabel}\n$preview"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun stopWatch() {
        watchJob?.cancel()
        watchJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_START = "com.invictus.link.action.AGENT_WATCH_START"
        private const val ACTION_STOP = "com.invictus.link.action.AGENT_WATCH_STOP"

        fun start(context: Context, taskId: String) {
            val intent = Intent(context, LinkAgentWatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LinkAgentWatchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

private fun ensureAgentNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val existing = manager.getNotificationChannel(AGENT_CHANNEL_ID)
    if (existing != null) return
    val channel = android.app.NotificationChannel(
        AGENT_CHANNEL_ID,
        "Agent tasks",
        android.app.NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Shows while your PC agent is working so Link can stay alive in the background"
        setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
}
