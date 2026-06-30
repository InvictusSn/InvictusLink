package com.invictus.link

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREF_SETUP_COMPLETE = "setup_complete"

@Composable
fun InvictusLinkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var currentAppVersionCode by remember { mutableIntStateOf(getAppVersionCode(context)) }
    var displayedVersionName by remember { mutableStateOf(getAppVersionName(context)) }
    val initialSession = remember(context, currentAppVersionCode) {
        loadSavedSession(context, currentAppVersionCode)
    }
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentAppVersionCode = getAppVersionCode(context)
                displayedVersionName = getAppVersionName(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var currentTab by remember { mutableStateOf(BottomTab.Home) }
    var activitySection by remember { mutableStateOf(ActivitySection.Log) }
    var logFilter by remember { mutableStateOf<WorkflowKind?>(null) }
    var bridgeBaseUrl by remember { mutableStateOf(loadSavedBridgeUrl(context)) }
    var pairingCode by remember { mutableStateOf("") }
    var session by remember { mutableStateOf(initialSession) }
    var prompt by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    var promptHistory by remember { mutableStateOf(loadPromptHistory(context)) }
    var projects by remember { mutableStateOf(listOf<ProjectInfo>()) }
    var selectedProjectId by remember { mutableStateOf(loadSelectedProjectId(context)) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("Tap Check for update to see if a newer build is available.") }
    var updateUrl by remember { mutableStateOf("") }
    var installingUpdate by remember { mutableStateOf(false) }
    var buildingUpdate by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    var buildStatus by remember { mutableStateOf("") }
    var pairingInProgress by remember { mutableStateOf(false) }
    var workflowLog by remember { mutableStateOf(loadWorkflowLog(context)) }
    var pendingApprovals by remember { mutableStateOf(listOf<PendingApprovalItem>()) }
    var loadingPendingApprovals by remember { mutableStateOf(false) }
    var approvingTaskIds by remember { mutableStateOf(setOf<String>()) }
    var digestInfo by remember { mutableStateOf<DailyDigestInfo?>(null) }
    var loadingDigest by remember { mutableStateOf(false) }
    var pairingStatus by remember {
        mutableStateOf(if (initialSession != null) "Paired with your PC." else "Not paired yet.")
    }
    var showSetupWizard by remember {
        mutableStateOf(!prefs.getBoolean(PREF_SETUP_COMPLETE, false) && initialSession == null)
    }
    var setupStep by remember { mutableStateOf(0) }
    var connectionDiagnostics by remember {
        mutableStateOf(
            ConnectionDiagnostics(false, false, false, false, false)
        )
    }

    val authToken = session?.token
    val hasSession = session != null
    val connectionOk = connectionDiagnostics.isReady && hasSession
    val bridgeHost = remember(bridgeBaseUrl) { extractBridgeHost(bridgeBaseUrl) }

    fun appendWorkflow(message: String, kind: WorkflowKind = WorkflowKind.Info) {
        workflowLog = workflowLog + WorkflowEntry(message = message, kind = kind)
        saveWorkflowLog(context, workflowLog)
    }

    fun appendHistory(exchange: PromptExchange) {
        promptHistory = (promptHistory + exchange).takeLast(20)
        savePromptHistory(context, promptHistory)
    }

    fun snack(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun sendPrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || sending) return
        scope.launch {
            sending = true
            status = "Sending"
            appendWorkflow("Prompt: $trimmed", WorkflowKind.Prompt)
            result = ""
            val projectForTask = selectedProjectId ?: projects.firstOrNull()?.id
            runCatching {
                withContext(Dispatchers.IO) {
                    submitAndWait(bridgeBaseUrl, trimmed, projectForTask, authToken) { status = it }
                }
            }.onSuccess { output ->
                result = output
                status = "Done"
                appendWorkflow(output.take(500), WorkflowKind.Success)
                appendHistory(
                    PromptExchange(
                        prompt = trimmed,
                        response = output,
                        projectId = projectForTask.orEmpty(),
                        ok = true,
                    )
                )
                snack("Sent to PC")
            }.onFailure { e ->
                val message = e.message ?: e.toString()
                if (message.startsWith("AWAITING_APPROVAL:")) {
                    status = "Waiting for approval"
                    currentTab = BottomTab.Activity
                    activitySection = ActivitySection.Approvals
                    snack("Approval required")
                } else {
                    status = message
                    result = message
                    appendWorkflow(message, WorkflowKind.Error)
                    appendHistory(
                        PromptExchange(
                            prompt = trimmed,
                            response = message,
                            projectId = projectForTask.orEmpty(),
                            ok = false,
                        )
                    )
                }
            }
            sending = false
        }
    }

    suspend fun verifyBiometric(): Boolean {
        val activity = context as? FragmentActivity ?: return false
        return authenticateBiometric(activity)
    }

    LaunchedEffect(bridgeBaseUrl) {
        while (true) {
            connectionDiagnostics = runCatching {
                withContext(Dispatchers.IO) {
                    evaluateConnectionDiagnostics(context, bridgeBaseUrl)
                }
            }.getOrElse {
                ConnectionDiagnostics(
                    usesTailscaleAddress = isTailscaleHost(extractBridgeHost(bridgeBaseUrl)),
                    usesInvictusVpnAddress = isInvictusVpnHost(extractBridgeHost(bridgeBaseUrl)),
                    tailscaleInstalled = isTailscaleInstalled(context),
                    tailscaleVpnActive = isTailscaleVpnActive(context),
                    bridgeReachable = false,
                )
            }
            delay(8000)
        }
    }

    LaunchedEffect(bridgeBaseUrl) {
        if (bridgeBaseUrl.isNotBlank()) {
            saveBridgeUrl(context, bridgeBaseUrl)
        }
    }

    LaunchedEffect(sending) {
        if (sending) {
            val startedAt = System.currentTimeMillis()
            while (true) {
                elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                delay(1000)
            }
        } else {
            elapsedSec = 0
        }
    }

    LaunchedEffect(bridgeBaseUrl, connectionDiagnostics.bridgeReachable) {
        if (connectionDiagnostics.bridgeReachable && bridgeBaseUrl.isNotBlank()) {
            runCatching {
                withContext(Dispatchers.IO) { fetchProjects(bridgeBaseUrl) }
            }.onSuccess { list ->
                projects = list
                if (list.isNotEmpty() && list.none { it.id == selectedProjectId }) {
                    selectedProjectId = list.first().id
                    saveSelectedProjectId(context, selectedProjectId)
                }
            }
        }
    }

    LaunchedEffect(bridgeBaseUrl, authToken) {
        if (!authToken.isNullOrBlank() && bridgeBaseUrl.isNotBlank()) {
            loadingDigest = true
            runCatching {
                withContext(Dispatchers.IO) { fetchDailyDigest(bridgeBaseUrl, authToken) }
            }.onSuccess { digestInfo = it }
            loadingDigest = false
        }
    }

    LaunchedEffect(bridgeBaseUrl, authToken) {
        while (true) {
            if (!authToken.isNullOrBlank() && bridgeBaseUrl.isNotBlank()) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        fetchPendingApprovals(bridgeBaseUrl, authToken)
                    }
                }.onSuccess { pendingApprovals = it }
            } else {
                pendingApprovals = emptyList()
            }
            delay(8000)
        }
    }

    InvictusTheme {
        Box(Modifier.fillMaxSize()) {
        InvictusAppShell(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            pendingCount = pendingApprovals.size,
            snackbarHostState = snackbar,
        ) { contentModifier ->
            AnimatedContent(
                targetState = currentTab,
                modifier = contentModifier,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                },
                label = "tabContent",
            ) { tab ->
            Box(Modifier.fillMaxSize()) {
            when (tab) {
                BottomTab.Home -> HomeScreen(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    sending = sending,
                    status = status,
                    result = result,
                    elapsedSec = elapsedSec,
                    connectionOk = connectionOk,
                    projects = projects,
                    selectedProjectId = selectedProjectId,
                    onProjectSelected = { id ->
                        selectedProjectId = id
                        saveSelectedProjectId(context, id)
                    },
                    onNewSession = {
                        val token = authToken
                        if (token.isNullOrBlank()) {
                            snack("Pair with your PC first")
                            return@HomeScreen
                        }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    createLinkSession(bridgeBaseUrl, token)
                                }
                            }.onSuccess { created ->
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        fetchProjects(bridgeBaseUrl)
                                    }
                                }.onSuccess { list ->
                                    projects = list
                                    selectedProjectId = created.id
                                    saveSelectedProjectId(context, created.id)
                                    snack("Session created")
                                }
                            }.onFailure {
                                snack(it.message ?: "Could not create session")
                            }
                        }
                    },
                    onDeleteSession = { sessionId ->
                        val token = authToken
                        if (token.isNullOrBlank()) {
                            snack("Pair with your PC first")
                            return@HomeScreen
                        }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    deleteLinkSession(bridgeBaseUrl, token, sessionId)
                                }
                            }.onSuccess {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        fetchProjects(bridgeBaseUrl)
                                    }
                                }.onSuccess { list ->
                                    projects = list
                                    if (selectedProjectId == sessionId) {
                                        selectedProjectId = list.firstOrNull()?.id
                                        saveSelectedProjectId(context, selectedProjectId)
                                    }
                                    snack("Session deleted")
                                }
                            }.onFailure {
                                snack(it.message ?: "Could not delete session")
                            }
                        }
                    },
                    history = promptHistory,
                    onResend = { exchange ->
                        prompt = exchange.prompt
                        if (exchange.projectId.isNotBlank() &&
                            projects.any { it.id == exchange.projectId }
                        ) {
                            selectedProjectId = exchange.projectId
                            saveSelectedProjectId(context, exchange.projectId)
                        }
                        sendPrompt(exchange.prompt)
                    },
                    onClearHistory = {
                        promptHistory = emptyList()
                        savePromptHistory(context, emptyList())
                        snack("History cleared")
                    },
                    onSend = { sendPrompt(prompt) },
                    onConnectFirst = { currentTab = BottomTab.Connection }
                )

                BottomTab.Activity -> ActivityScreen(
                    section = activitySection,
                    onSectionChange = { activitySection = it },
                    loadingDigest = loadingDigest,
                    digest = digestInfo,
                    workflowLog = workflowLog,
                    logFilter = logFilter,
                    onLogFilterChange = { logFilter = it },
                    onRefreshDigest = {
                        scope.launch {
                            val t = authToken ?: return@launch
                            loadingDigest = true
                            runCatching {
                                withContext(Dispatchers.IO) { fetchDailyDigest(bridgeBaseUrl, t) }
                            }.onSuccess { digestInfo = it }
                            loadingDigest = false
                        }
                    },
                    onRefreshLog = {
                        scope.launch {
                            val t = authToken ?: return@launch
                            runCatching {
                                withContext(Dispatchers.IO) { fetchDailyDigest(bridgeBaseUrl, t) }
                            }.onSuccess { digestInfo = it }
                            snack("Activity refreshed")
                        }
                    },
                    loadingApprovals = loadingPendingApprovals,
                    approvals = pendingApprovals,
                    approvingIds = approvingTaskIds,
                    onRefreshApprovals = {
                        scope.launch {
                            val t = authToken ?: return@launch
                            loadingPendingApprovals = true
                            runCatching {
                                withContext(Dispatchers.IO) { fetchPendingApprovals(bridgeBaseUrl, t) }
                            }.onSuccess { pendingApprovals = it }
                            loadingPendingApprovals = false
                        }
                    },
                    onApprove = { item ->
                        scope.launch {
                            val t = authToken ?: return@launch
                            if (!verifyBiometric()) {
                                snack("Biometric check canceled")
                                return@launch
                            }
                            approvingTaskIds = approvingTaskIds + item.taskId
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    approvePendingTask(bridgeBaseUrl, t, item.taskId)
                                }
                            }.onSuccess {
                                pendingApprovals = pendingApprovals.filterNot { it.taskId == item.taskId }
                                appendWorkflow("Approved ${item.taskId}", WorkflowKind.Success)
                                snack("Approved")
                            }.onFailure { snack("Approval failed") }
                            approvingTaskIds = approvingTaskIds - item.taskId
                        }
                    }
                )

                BottomTab.Connection -> ConnectionScreen(
                    diagnostics = connectionDiagnostics,
                    bridgeBaseUrl = bridgeBaseUrl,
                    onBridgeUrlChange = { bridgeBaseUrl = it.trim() },
                    pairingCode = pairingCode,
                    onPairingCodeChange = { pairingCode = it },
                    isPaired = hasSession,
                    pairingStatus = pairingStatus,
                    pairingInProgress = pairingInProgress,
                    onTestConnection = {
                        scope.launch {
                            val d = withContext(Dispatchers.IO) {
                                evaluateConnectionDiagnostics(context, bridgeBaseUrl)
                            }
                            connectionDiagnostics = d
                            pairingStatus = d.statusMessage
                            snack(if (d.bridgeReachable) "Bridge reachable" else "Bridge not reachable")
                        }
                    },
                    onConnect = {
                        scope.launch {
                            pairingInProgress = true
                            val d = withContext(Dispatchers.IO) {
                                evaluateConnectionDiagnostics(context, bridgeBaseUrl)
                            }
                            connectionDiagnostics = d
                            if (!d.isReady) {
                                pairingStatus = d.statusMessage
                                pairingInProgress = false
                                return@launch
                            }
                            if (!verifyBiometric()) {
                                pairingStatus = "Biometric check canceled."
                                pairingInProgress = false
                                return@launch
                            }
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    pairSession(bridgeBaseUrl, pairingCode.trim(), currentAppVersionCode)
                                }
                            }.onSuccess { newSession ->
                                session = newSession
                                saveSession(context, newSession)
                                saveBridgeUrl(context, bridgeBaseUrl)
                                pairingCode = ""
                                pairingStatus = "Paired with your PC."
                                prefs.edit().putBoolean(PREF_SETUP_COMPLETE, true).apply()
                                snack("Connected to your PC")
                            }.onFailure { e ->
                                pairingStatus = "Pairing failed: ${e.message ?: e}"
                            }
                            pairingInProgress = false
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            if (!verifyBiometric()) return@launch
                            session = null
                            clearSession(context)
                            pairingStatus = "Disconnected."
                            snack("Disconnected")
                        }
                    },
                    onOpenTailscale = { openTailscaleApp(context) },
                    bridgeHost = bridgeHost
                )

                BottomTab.Settings -> SettingsScreen(
                    versionName = displayedVersionName,
                    updateStatus = updateStatus,
                    updateAvailable = updateAvailable,
                    checkingUpdate = checkingUpdate,
                    installingUpdate = installingUpdate,
                    buildingUpdate = buildingUpdate,
                    backingUp = backingUp,
                    buildStatus = buildStatus,
                    onCheckUpdate = {
                        scope.launch {
                            checkingUpdate = true
                            runCatching {
                                withContext(Dispatchers.IO) { checkForUpdate(bridgeBaseUrl) }
                            }.onSuccess { info ->
                                if (info.versionCode > currentAppVersionCode) {
                                    updateAvailable = true
                                    updateUrl = info.apkUrl
                                    updateStatus = "Update available: v${info.versionName}"
                                    snack("Update available")
                                } else {
                                    updateAvailable = false
                                    updateUrl = ""
                                    updateStatus = "You're on the latest version."
                                }
                            }.onFailure {
                                updateStatus = "Update check failed: ${it.message ?: it}"
                            }
                            checkingUpdate = false
                        }
                    },
                    onInstallUpdate = {
                        scope.launch {
                            installingUpdate = true
                            runCatching {
                                val url = resolveApkUrl(bridgeBaseUrl, updateUrl)
                                withContext(Dispatchers.IO) {
                                    downloadAndInstallUpdate(context, url, currentAppVersionCode)
                                }
                            }.onSuccess {
                                snack(
                                    "Tap Install on the Android screen, then reopen Invictus Link."
                                )
                            }.onFailure { snack("Install failed: ${it.message ?: it}") }
                            installingUpdate = false
                        }
                    },
                    onPublishUpdate = {
                        scope.launch {
                            buildingUpdate = true
                            buildStatus = "Publishing…"
                            runCatching {
                                triggerBuildAndWait(bridgeBaseUrl, authToken) {
                                    buildStatus = it
                                }
                            }.onSuccess {
                                buildStatus = "Publish complete. Check for update to install."
                                snack("Publish complete")
                            }.onFailure {
                                buildStatus = "Publish failed: ${it.message ?: it}"
                            }
                            buildingUpdate = false
                        }
                    },
                    onArchiveVersion = {
                        scope.launch {
                            backingUp = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    triggerArchiveApp(bridgeBaseUrl, authToken)
                                }
                            }.onSuccess { snack("Archived on PC: $it") }
                                .onFailure { snack("Archive failed: ${it.message ?: it}") }
                            backingUp = false
                        }
                    }
                )
            }
            }
            }
        }

        if (showSetupWizard) {
            SetupWizardOverlay(
                step = setupStep,
                bridgeUrl = bridgeBaseUrl,
                onBridgeUrlChange = { bridgeBaseUrl = it },
                pairingCode = pairingCode,
                onPairingCodeChange = { pairingCode = it },
                onNext = { setupStep += 1 },
                onBack = { if (setupStep > 0) setupStep -= 1 },
                onFinish = {
                    prefs.edit().putBoolean(PREF_SETUP_COMPLETE, true).apply()
                    showSetupWizard = false
                    currentTab = BottomTab.Connection
                },
                onDismiss = {
                    prefs.edit().putBoolean(PREF_SETUP_COMPLETE, true).apply()
                    showSetupWizard = false
                },
            )
        }
        }
    }
}
