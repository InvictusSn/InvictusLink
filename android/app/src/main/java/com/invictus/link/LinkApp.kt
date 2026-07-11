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
    var resumeTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> LinkAppVisibility.isInForeground = true
                Lifecycle.Event.ON_STOP -> LinkAppVisibility.isInForeground = false
                Lifecycle.Event.ON_RESUME -> {
                    currentAppVersionCode = getAppVersionCode(context)
                    displayedVersionName = getAppVersionName(context)
                    resumeTick++
                }
                else -> Unit
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
    var bridgeActivity by remember { mutableStateOf(listOf<WorkflowEntry>()) }
    var loadingBridgeActivity by remember { mutableStateOf(false) }
    var pendingApprovals by remember { mutableStateOf(listOf<PendingApprovalItem>()) }
    var knownApprovalIds by remember { mutableStateOf<Set<String>?>(null) }
    var loadingPendingApprovals by remember { mutableStateOf(false) }
    var approvingTaskIds by remember { mutableStateOf(setOf<String>()) }
    var digestInfo by remember { mutableStateOf<DailyDigestInfo?>(null) }
    var loadingDigest by remember { mutableStateOf(false) }
    var pairingStatus by remember {
        mutableStateOf(if (initialSession != null) "Paired with your PC." else "Not paired yet.")
    }
    var aiProviders by remember { mutableStateOf(listOf<AiProviderInfo>()) }
    var pinnedProviderIds by remember { mutableStateOf(loadPinnedProviderIds(context)) }
    val sortedProviders = remember(aiProviders, pinnedProviderIds) {
        sortProvidersForDisplay(aiProviders, pinnedProviderIds)
    }
    var activeProviderLabel by remember { mutableStateOf<String?>(null) }
    var activeProviderType by remember { mutableStateOf<String?>(null) }
    var routingMode by remember { mutableStateOf("manual") }
    var routingBusy by remember { mutableStateOf(false) }
    var lastRoutingNote by remember { mutableStateOf<String?>(null) }
    var lastGrokCostUsd by remember { mutableStateOf<Double?>(null) }
    var pendingAttachments by remember { mutableStateOf(listOf<PendingAttachment>()) }
    var promptTemplates by remember { mutableStateOf(loadPromptTemplates(context)) }
    var exportingConversation by remember { mutableStateOf(false) }
    var foregroundTaskId by remember { mutableStateOf<String?>(null) }
    var activeWatchTaskId by remember { mutableStateOf<String?>(null) }
    var seenCompletionKeys by remember { mutableStateOf(loadSeenCompletionKeys(context)) }
    var completionKeysInitialized by remember { mutableStateOf(false) }
    var loadingProviders by remember { mutableStateOf(false) }
    var busyProviderIds by remember { mutableStateOf(setOf<String>()) }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var addingProvider by remember { mutableStateOf(false) }
    var addProviderStatus by remember { mutableStateOf("") }
    var linkRules by remember { mutableStateOf(listOf<LinkRule>()) }
    var loadingRules by remember { mutableStateOf(false) }
    var busyRuleIds by remember { mutableStateOf(setOf<String>()) }
    var addingRule by remember { mutableStateOf(false) }
    var costDashboard by remember { mutableStateOf<CostDashboardInfo?>(null) }
    var loadingCosts by remember { mutableStateOf(false) }
    var savingCostLimits by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf(readCrashLog(context)) }
    var sendingCrashLog by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        loadPendingUpdateInfo(context)?.let { info ->
            if (info.versionCode > currentAppVersionCode) {
                updateAvailable = true
                updateUrl = info.apkUrl
                updateStatus = "Update available: v${info.versionName}"
            }
        }
    }

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

    val promptCallbacks = LinkPromptCallbacks(
        onStatus = { status = it },
        onPartial = { result = it },
        onResult = { result = it },
        onSendingChange = { sending = it },
        onPromptClear = { prompt = "" },
        onPendingAttachmentsClear = { pendingAttachments = emptyList() },
        onLastRoutingNote = { lastRoutingNote = it },
        onLastGrokCostUsd = { lastGrokCostUsd = it },
        onForegroundTaskId = { foregroundTaskId = it },
        onActiveTaskStarted = { activeWatchTaskId = it },
        appendWorkflow = { message, kind -> appendWorkflow(message, kind) },
        appendHistory = { appendHistory(it) },
        snack = { snack(it) },
        onApprovalRequired = {
            currentTab = BottomTab.Activity
            activitySection = ActivitySection.Approvals
        },
    )

    fun sendPrompt(text: String) {
        sendLinkPrompt(
            context = context,
            scope = scope,
            text = text,
            sending = sending,
            pendingAttachments = pendingAttachments,
            authToken = authToken,
            bridgeBaseUrl = bridgeBaseUrl,
            selectedProjectId = selectedProjectId,
            projects = projects,
            callbacks = promptCallbacks,
        )
    }

    fun stopActivePrompt() {
        stopActiveLinkPrompt(
            context = context,
            scope = scope,
            bridgeBaseUrl = bridgeBaseUrl,
            authToken = authToken,
            callbacks = LinkStopCallbacks(
                onStopped = { restoredPrompt, response ->
                    restoredPrompt?.let { prompt = it }
                    result = response
                    status = "Stopped"
                    sending = false
                    foregroundTaskId = null
                    activeWatchTaskId = null
                    promptHistory = loadPromptHistory(context)
                    appendWorkflow("Stopped by user", WorkflowKind.Error)
                    snack("Stopped")
                },
            ),
        )
    }

    suspend fun verifyBiometric(): Boolean {
        val activity = context as? FragmentActivity ?: return false
        return authenticateBiometric(activity)
    }

    fun refreshProviders(showErrors: Boolean = false) {
        val token = authToken ?: return
        scope.launch {
            loadingProviders = true
            runCatching {
                withContext(Dispatchers.IO) { fetchAiProviders(bridgeBaseUrl, token) }
            }.onSuccess { result ->
                aiProviders = result.providers
                routingMode = result.routingMode
            }
                .onFailure { if (showErrors) snack(it.message ?: "Could not load providers") }
            loadingProviders = false
        }
    }

    fun refreshRules(showErrors: Boolean = false) {
        val token = authToken ?: return
        scope.launch {
            loadingRules = true
            runCatching {
                withContext(Dispatchers.IO) { fetchRules(bridgeBaseUrl, token) }
            }.onSuccess { linkRules = it }
                .onFailure { if (showErrors) snack(it.message ?: "Could not load rules") }
            loadingRules = false
        }
    }

    fun refreshCosts() {
        val token = authToken ?: return
        scope.launch {
            loadingCosts = true
            runCatching {
                withContext(Dispatchers.IO) { fetchCostDashboard(bridgeBaseUrl, token) }
            }.onSuccess { costDashboard = it }
            loadingCosts = false
        }
    }

    // Re-read the crash log whenever Settings opens (a crash may have happened
    // since the app was last in the foreground).
    LaunchedEffect(currentTab) {
        if (currentTab == BottomTab.Settings) {
            crashLog = withContext(Dispatchers.IO) { readCrashLog(context) }
        }
    }

    // Load the provider list once paired and whenever Settings opens.
    LaunchedEffect(currentTab, authToken, connectionDiagnostics.bridgeReachable) {
        if (currentTab == BottomTab.Settings &&
            !authToken.isNullOrBlank() &&
            connectionDiagnostics.bridgeReachable
        ) {
            refreshProviders()
            refreshRules()
        }
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
                withContext(Dispatchers.IO) { fetchBridgeHealth(bridgeBaseUrl) }
            }.onSuccess { health ->
                projects = health.projects
                activeProviderLabel = health.activeProviderLabel
                activeProviderType = health.activeProviderType
                routingMode = health.routingMode
                if (health.projects.isNotEmpty() && health.projects.none { it.id == selectedProjectId }) {
                    selectedProjectId = health.projects.first().id
                    saveSelectedProjectId(context, selectedProjectId)
                }
            }
        }
    }

    LaunchedEffect(bridgeBaseUrl, authToken) {
        if (!authToken.isNullOrBlank() && bridgeBaseUrl.isNotBlank()) {
            loadingDigest = true
            loadingCosts = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val digest = fetchDailyDigest(bridgeBaseUrl, authToken)
                    val costs = fetchCostDashboard(bridgeBaseUrl, authToken)
                    digest to costs
                }
            }.onSuccess { (digest, costs) ->
                digestInfo = digest
                costDashboard = costs
            }
            loadingDigest = false
            loadingCosts = false
        }
    }

    LaunchedEffect(bridgeBaseUrl, authToken) {
        while (true) {
            if (!authToken.isNullOrBlank() && bridgeBaseUrl.isNotBlank()) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        fetchPendingApprovals(bridgeBaseUrl, authToken)
                    }
                }.onSuccess { items ->
                    val known = knownApprovalIds
                    if (known != null) {
                        val fresh = items.filter { it.taskId !in known }
                        if (fresh.isNotEmpty()) {
                            val first = fresh.first()
                            showLinkNotification(
                                context,
                                if (fresh.size == 1) "Approval required" else "${fresh.size} approvals required",
                                first.prompt.take(160),
                            )
                        }
                    }
                    knownApprovalIds = items.map { it.taskId }.toSet()
                    pendingApprovals = items
                }
            } else {
                pendingApprovals = emptyList()
                knownApprovalIds = null
            }
            delay(8000)
        }
    }

    LaunchedEffect(resumeTick) {
        promptHistory = loadPromptHistory(context)
        loadActivePhoneTask(context)?.let { active ->
            sending = true
            status = active.statusLabel
            result = active.partialOutput.orEmpty()
            foregroundTaskId = active.taskId
            lastRoutingNote = active.routingNote
            lastGrokCostUsd = active.grokCostUsd
            if (activeWatchTaskId != active.taskId) {
                activeWatchTaskId = active.taskId
            }
            LinkAgentWatchService.start(context, active.taskId)
        }
    }

    LaunchedEffect(activeWatchTaskId) {
        val taskId = activeWatchTaskId ?: return@LaunchedEffect
        while (loadActivePhoneTask(context)?.taskId == taskId) {
            loadActivePhoneTask(context)?.let { active ->
                status = active.statusLabel
                active.partialOutput?.let { result = it }
                lastRoutingNote = active.routingNote
                lastGrokCostUsd = active.grokCostUsd
                foregroundTaskId = active.taskId
                if (active.statusLabel == "Waiting for approval") {
                    currentTab = BottomTab.Activity
                    activitySection = ActivitySection.Approvals
                }
            }
            delay(350)
        }
        loadPromptHistory(context).lastOrNull { it.taskId == taskId }?.let { entry ->
            result = entry.response
            status = if (entry.ok) "Done" else "Task failed"
            promptHistory = loadPromptHistory(context)
        }
        suppressCompletionNotificationsForTask(context, bridgeBaseUrl, authToken, taskId)
        foregroundTaskId = null
        activeWatchTaskId = null
        sending = false
    }

    LaunchedEffect(bridgeBaseUrl, authToken, connectionDiagnostics.bridgeReachable, resumeTick) {
        while (true) {
            if (!authToken.isNullOrBlank() && bridgeBaseUrl.isNotBlank() && connectionDiagnostics.bridgeReachable) {
                pollBridgeCompletions(
                    context = context,
                    scope = scope,
                    bridgeBaseUrl = bridgeBaseUrl,
                    authToken = authToken,
                    bridgeReachable = connectionDiagnostics.bridgeReachable,
                    foregroundTaskId = foregroundTaskId,
                    seenCompletionKeys = seenCompletionKeys,
                    completionKeysInitialized = completionKeysInitialized,
                    onSeenCompletionKeysChange = { seenCompletionKeys = it },
                    onCompletionKeysInitializedChange = { completionKeysInitialized = it },
                    onHistoryUpdated = { promptHistory = loadPromptHistory(context) },
                )
                maybeNotifyForUpdate(
                    context = context,
                    scope = scope,
                    bridgeBaseUrl = bridgeBaseUrl,
                    bridgeReachable = connectionDiagnostics.bridgeReachable,
                    onUpdateInfo = { info ->
                        updateAvailable = true
                        updateUrl = info.apkUrl
                        updateStatus = "Update available: v${info.versionName}"
                    },
                )
            } else {
                completionKeysInitialized = false
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
                BottomTab.Home -> {
                    val sessionName = projects.firstOrNull { it.id == selectedProjectId }?.name ?: "session"
                    val exportExchanges = promptHistory
                        .filter { it.projectId == (selectedProjectId ?: "") }
                        .ifEmpty { promptHistory }
                    HomeScreen(
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
                    onRenameSession = { sessionId, newName ->
                        val token = authToken
                        if (token.isNullOrBlank()) {
                            snack("Pair with your PC first")
                            return@HomeScreen
                        }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    renameLinkSession(bridgeBaseUrl, token, sessionId, newName)
                                }
                            }.onSuccess { updated ->
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        fetchProjects(bridgeBaseUrl)
                                    }
                                }.onSuccess { list ->
                                    projects = list
                                    if (selectedProjectId == sessionId) {
                                        selectedProjectId = updated.id
                                        saveSelectedProjectId(context, updated.id)
                                    }
                                    snack("Session renamed")
                                }
                            }.onFailure {
                                snack(it.message ?: "Could not rename session")
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
                        val token = authToken
                        val projectId = selectedProjectId
                        if (!token.isNullOrBlank() && !projectId.isNullOrBlank()) {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        resetGrokThread(bridgeBaseUrl, token, projectId)
                                    }
                                }
                            }
                        }
                        snack("History cleared")
                    },
                    onSend = { sendPrompt(prompt) },
                    onStop = { stopActivePrompt() },
                    onConnectFirst = { currentTab = BottomTab.Connection },
                    activeProviderLabel = activeProviderLabel,
                    routingMode = routingMode,
                    routingNote = lastRoutingNote,
                    grokCostUsd = lastGrokCostUsd,
                    showGrokCost = routingMode == "manual" && activeProviderType == "xai",
                    attachments = pendingAttachments,
                    onAttachmentsChange = { pendingAttachments = it },
                    onAttachmentError = { snack(it) },
                    templates = promptTemplates,
                    onUseTemplate = { template ->
                        promptTemplates = promptTemplates.map {
                            if (it.id == template.id) it.copy(useCount = it.useCount + 1) else it
                        }
                        savePromptTemplates(context, promptTemplates)
                    },
                    onSaveTemplate = { title, text ->
                        promptTemplates = promptTemplates + newPromptTemplate(title, text)
                        savePromptTemplates(context, promptTemplates)
                        snack("Template saved")
                    },
                    onDeleteTemplate = { template ->
                        promptTemplates = promptTemplates.filterNot { it.id == template.id }
                        savePromptTemplates(context, promptTemplates)
                        snack("Template deleted")
                    },
                    sessionName = sessionName,
                    exportExchanges = exportExchanges,
                    exporting = exportingConversation,
                    onExportToPc = { selected ->
                        val token = authToken
                        if (token.isNullOrBlank()) {
                            snack("Pair with your PC first")
                            return@HomeScreen
                        }
                        if (selected.isEmpty()) {
                            snack("Select at least one exchange")
                            return@HomeScreen
                        }
                        val markdown = buildConversationMarkdown(selected, sessionName)
                        scope.launch {
                            exportingConversation = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    exportConversationToPc(
                                        bridgeBaseUrl,
                                        token,
                                        selectedProjectId,
                                        defaultExportFilename(sessionName),
                                        markdown,
                                    )
                                }
                            }.onSuccess { path ->
                                snack("Saved on PC: $path")
                            }.onFailure {
                                snack(it.message ?: "Export failed")
                            }
                            exportingConversation = false
                        }
                    },
                    onExportShare = { selected ->
                        if (selected.isEmpty()) {
                            snack("Select at least one exchange")
                            return@HomeScreen
                        }
                        val markdown = buildConversationMarkdown(selected, sessionName)
                        runCatching {
                            shareConversationMarkdown(
                                context,
                                defaultExportFilename(sessionName),
                                markdown,
                            )
                        }.onFailure {
                            snack("Share failed")
                        }
                    },
                )
                }

                BottomTab.Activity -> ActivityScreen(
                    section = activitySection,
                    onSectionChange = { activitySection = it },
                    loadingDigest = loadingDigest,
                    digest = digestInfo,
                    workflowLog = remember(workflowLog, bridgeActivity) {
                        (workflowLog + bridgeActivity).sortedBy { it.timestampMs }
                    },
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
                    loadingCosts = loadingCosts,
                    costs = costDashboard,
                    onRefreshCosts = { refreshCosts() },
                    onSetCostLimits = { monthly, daily ->
                        val token = authToken ?: return@ActivityScreen
                        scope.launch {
                            savingCostLimits = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    setCostLimits(bridgeBaseUrl, token, monthly, daily)
                                }
                            }.onSuccess {
                                snack("Limits updated")
                                refreshCosts()
                            }.onFailure {
                                snack(it.message ?: "Could not update limits")
                            }
                            savingCostLimits = false
                        }
                    },
                    savingCostLimits = savingCostLimits,
                    onRefreshLog = {
                        scope.launch {
                            val t = authToken
                            if (t.isNullOrBlank()) {
                                snack("Pair with your PC to pull bridge activity")
                                return@launch
                            }
                            loadingBridgeActivity = true
                            runCatching {
                                withContext(Dispatchers.IO) { fetchBridgeActivity(bridgeBaseUrl, t) }
                            }.onSuccess {
                                bridgeActivity = it
                                snack("Pulled latest bridge activity")
                            }.onFailure {
                                snack(it.message ?: "Could not load bridge activity")
                            }
                            loadingBridgeActivity = false
                        }
                    },
                    loadingLog = loadingBridgeActivity,
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
                    providers = sortedProviders,
                    pinnedProviderIds = pinnedProviderIds,
                    loadingProviders = loadingProviders,
                    busyProviderIds = busyProviderIds,
                    routingMode = routingMode,
                    routingBusy = routingBusy,
                    onToggleAutoRouting = { auto ->
                        val token = authToken ?: return@SettingsScreen
                        scope.launch {
                            routingBusy = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    setRoutingMode(bridgeBaseUrl, token, auto)
                                }
                            }.onSuccess {
                                routingMode = if (auto) "auto" else "manual"
                                refreshProviders()
                                snack(if (auto) "Auto routing on" else "Manual provider mode")
                            }.onFailure {
                                snack(it.message ?: "Could not change routing mode")
                            }
                            routingBusy = false
                        }
                    },
                    isPaired = hasSession,
                    onTogglePinProvider = { provider ->
                        pinnedProviderIds = if (provider.id in pinnedProviderIds) {
                            pinnedProviderIds - provider.id
                        } else {
                            pinnedProviderIds + provider.id
                        }
                        savePinnedProviderIds(context, pinnedProviderIds)
                    },
                    onActivateProvider = { provider ->
                        val token = authToken ?: return@SettingsScreen
                        scope.launch {
                            busyProviderIds = busyProviderIds + provider.id
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    activateAiProvider(bridgeBaseUrl, token, provider.id)
                                }
                            }.onSuccess {
                                aiProviders = aiProviders.map {
                                    it.copy(isActive = it.id == provider.id)
                                }
                                activeProviderLabel = provider.label
                                activeProviderType = provider.type
                                snack("${provider.label} is now in use")
                            }.onFailure {
                                snack(it.message ?: "Could not switch provider")
                            }
                            busyProviderIds = busyProviderIds - provider.id
                        }
                    },
                    onDeleteProvider = { provider ->
                        val token = authToken ?: return@SettingsScreen
                        scope.launch {
                            busyProviderIds = busyProviderIds + provider.id
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    deleteAiProvider(bridgeBaseUrl, token, provider.id)
                                }
                            }.onSuccess {
                                snack("${provider.label} removed")
                                refreshProviders()
                            }.onFailure {
                                snack(it.message ?: "Could not remove provider")
                            }
                            busyProviderIds = busyProviderIds - provider.id
                        }
                    },
                    onRefreshProviders = { refreshProviders(showErrors = true) },
                    onAddProviderRequest = {
                        addProviderStatus = ""
                        showAddProviderDialog = true
                    },
                    rules = linkRules,
                    loadingRules = loadingRules,
                    busyRuleIds = busyRuleIds,
                    addingRule = addingRule,
                    onRefreshRules = { refreshRules(showErrors = true) },
                    onAddRule = { ruleScope, targetId, title, text, vaultNotes ->
                        val token = authToken ?: return@SettingsScreen
                        scope.launch {
                            addingRule = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    addLinkRule(bridgeBaseUrl, token, ruleScope, targetId, title, text, vaultNotes)
                                }
                            }.onSuccess {
                                refreshRules()
                                snack("Rule added")
                            }.onFailure {
                                snack(it.message ?: "Could not add rule")
                            }
                            addingRule = false
                        }
                    },
                    onToggleRule = { rule, enabled ->
                        val token = authToken ?: return@SettingsScreen
                        val previous = linkRules
                        linkRules = linkRules.map {
                            if (it.id == rule.id) it.copy(enabled = enabled) else it
                        }
                        scope.launch {
                            busyRuleIds = busyRuleIds + rule.id
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    setRuleEnabled(bridgeBaseUrl, token, rule.id, enabled)
                                }
                            }.onFailure {
                                linkRules = previous
                                snack(it.message ?: "Could not update rule")
                            }
                            busyRuleIds = busyRuleIds - rule.id
                        }
                    },
                    onDeleteRule = { rule ->
                        val token = authToken ?: return@SettingsScreen
                        scope.launch {
                            busyRuleIds = busyRuleIds + rule.id
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    deleteLinkRule(bridgeBaseUrl, token, rule.id)
                                }
                            }.onSuccess {
                                linkRules = linkRules.filterNot { it.id == rule.id }
                                snack("Rule deleted")
                            }.onFailure {
                                snack(it.message ?: "Could not delete rule")
                            }
                            busyRuleIds = busyRuleIds - rule.id
                        }
                    },
                    projects = projects,
                    crashLog = crashLog,
                    sendingCrashLog = sendingCrashLog,
                    onSendCrashLog = {
                        val token = authToken
                        val log = crashLog
                        if (token.isNullOrBlank()) {
                            snack("Pair with your PC first")
                        } else if (log.isNullOrBlank()) {
                            snack("No crash log to send")
                        } else {
                            scope.launch {
                                sendingCrashLog = true
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        uploadCrashLog(bridgeBaseUrl, token, log)
                                    }
                                }.onSuccess { savedAs ->
                                    appendWorkflow("Crash log sent to PC ($savedAs)", WorkflowKind.Info)
                                    snack("Crash log sent to your PC")
                                }.onFailure {
                                    snack(it.message ?: "Could not send crash log")
                                }
                                sendingCrashLog = false
                            }
                        }
                    },
                    onClearCrashLog = {
                        clearCrashLog(context)
                        crashLog = null
                        snack("Crash log cleared")
                    },
                    onCheckUpdate = {
                        scope.launch {
                            checkingUpdate = true
                            runCatching {
                                withContext(Dispatchers.IO) { checkForUpdate(bridgeBaseUrl) }
                            }.onSuccess { info ->
                                if (info.versionCode > currentAppVersionCode) {
                                    savePendingUpdateInfo(context, info)
                                    updateAvailable = true
                                    updateUrl = info.apkUrl
                                    updateStatus = "Update available: v${info.versionName}"
                                    snack("Update available")
                                } else {
                                    clearPendingUpdateInfo(context)
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
                                clearPendingUpdateInfo(context)
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
                            buildStatus = "Publishing on your PC…"
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    startBuildApk(bridgeBaseUrl, authToken)
                                }
                            }.onSuccess {
                                buildStatus = "Publishing in background"
                                LinkBackgroundWork.scheduleBuildWatch(context)
                                snack("Publishing on your PC — we'll notify you when it's ready")
                            }.onFailure {
                                buildStatus = "Publish failed: ${it.message ?: it}"
                                snack("Publish failed")
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

        if (showAddProviderDialog) {
            AddProviderDialog(
                connecting = addingProvider,
                statusMessage = addProviderStatus,
                onConnect = { type, apiKey, providerBaseUrl, model ->
                    val token = authToken
                    if (token.isNullOrBlank()) {
                        addProviderStatus = "Pair with your PC first."
                        return@AddProviderDialog
                    }
                    scope.launch {
                        addingProvider = true
                        addProviderStatus = "Connecting…"
                        runCatching {
                            withContext(Dispatchers.IO) {
                                addAiProvider(
                                    baseUrl = bridgeBaseUrl,
                                    token = token,
                                    type = type,
                                    apiKey = apiKey.takeIf { it.isNotBlank() },
                                    providerBaseUrl = providerBaseUrl.takeIf { it.isNotBlank() },
                                    model = model.takeIf { it.isNotBlank() },
                                )
                            }
                        }.onSuccess { test ->
                            if (test.ok) {
                                showAddProviderDialog = false
                                snack("Provider connected")
                            } else {
                                // Saved on the PC, but the live check failed — tell the user why.
                                addProviderStatus =
                                    "Saved, but the connection check failed: ${test.detail}"
                                snack("Provider saved — check its status")
                            }
                            refreshProviders()
                        }.onFailure {
                            addProviderStatus = it.message ?: "Could not connect provider"
                        }
                        addingProvider = false
                    }
                },
                onDismiss = {
                    showAddProviderDialog = false
                    addProviderStatus = ""
                },
            )
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
