package com.invictus.link

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge API calls for Rules, the Cost dashboard, and conversation exports.
 * Same conventions as LinkBridgeApi.kt — OkHttp via LinkHttp, bearer auth.
 */

private fun parseRule(item: JSONObject): LinkRule {
    val notes = buildList {
        val arr = item.optJSONArray("vaultNotes") ?: return@buildList
        for (i in 0 until arr.length()) {
            val note = arr.optString(i, "")
            if (note.isNotBlank()) add(note)
        }
    }
    return LinkRule(
        id = item.optString("id", ""),
        scope = item.optString("scope", "global"),
        targetId = item.optString("targetId", "").takeIf { it.isNotBlank() },
        title = item.optString("title", ""),
        text = item.optString("text", ""),
        enabled = item.optBoolean("enabled", true),
        vaultNotes = notes,
    )
}

internal fun fetchRules(baseUrl: String, token: String): List<LinkRule> {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/rules"
    val response = LinkHttp.get(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        if (response.code == 404) {
            throw RuntimeException("Bridge needs an update for rules — restart the PC bridge")
        }
        throw RuntimeException("Load rules failed (${response.code})")
    }
    val arr = JSONObject(response.body).optJSONArray("rules") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val rule = parseRule(item)
            if (rule.id.isNotBlank()) add(rule)
        }
    }
}

internal fun addLinkRule(
    baseUrl: String,
    token: String,
    scope: String,
    targetId: String?,
    title: String,
    text: String,
    vaultNotes: List<String>,
): LinkRule {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/rules"
    val body = JSONObject().apply {
        put("scope", scope)
        if (!targetId.isNullOrBlank()) put("targetId", targetId)
        put("title", title)
        put("text", text)
        if (vaultNotes.isNotEmpty()) put("vaultNotes", JSONArray(vaultNotes))
    }
    val response = LinkHttp.postJson(url, body.toString(), token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Add rule failed (${response.code})")
    }
    val obj = JSONObject(response.body).optJSONObject("rule")
        ?: throw RuntimeException("Add rule returned no rule")
    return parseRule(obj)
}

internal fun setRuleEnabled(baseUrl: String, token: String, ruleId: String, enabled: Boolean) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/rules/$ruleId"
    val body = JSONObject().put("enabled", enabled).toString()
    val response = LinkHttp.patchJson(url, body, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Update rule failed (${response.code})")
    }
}

internal fun deleteLinkRule(baseUrl: String, token: String, ruleId: String) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/rules/$ruleId"
    val response = LinkHttp.delete(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Delete rule failed (${response.code})")
    }
}

private fun parseCostDashboard(json: JSONObject): CostDashboardInfo {
    val providers = buildList {
        val arr = json.optJSONArray("byProvider") ?: return@buildList
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            add(
                ProviderCostSummary(
                    label = item.optString("label", "Unknown"),
                    isLocal = item.optBoolean("isLocal", false),
                    runs = item.optInt("runs", 0),
                    promptTokens = item.optLong("promptTokens", 0L),
                    completionTokens = item.optLong("completionTokens", 0L),
                    costUsd = item.optDouble("costUsd", 0.0),
                    costTracking = item.optString("costTracking", "priced"),
                ),
            )
        }
    }
    val dailyTotals = buildList {
        val arr = json.optJSONArray("dailyTotals") ?: return@buildList
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            add(
                DailyCostPoint(
                    date = item.optString("date", ""),
                    costUsd = item.optDouble("costUsd", 0.0),
                ),
            )
        }
    }
    val alert = json.optJSONObject("alert")?.let {
        CostAlertInfo(
            level = it.optString("level", "warning"),
            message = it.optString("message", ""),
        )
    }
    val untracked = buildList {
        val arr = json.optJSONArray("untrackedProviders") ?: return@buildList
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            add(
                UntrackedProviderSummary(
                    label = item.optString("label", "Unknown"),
                    runs = item.optInt("runs", 0),
                    connected = item.optBoolean("connected", false),
                ),
            )
        }
    }
    return CostDashboardInfo(
        todayUsd = json.optDouble("todayUsd", 0.0),
        monthUsd = json.optDouble("monthUsd", 0.0),
        monthLabel = json.optString("monthLabel", ""),
        deviceTodayUsd = json.optDouble("deviceTodayUsd", json.optDouble("todayUsd", 0.0)),
        deviceMonthUsd = json.optDouble("deviceMonthUsd", json.optDouble("monthUsd", 0.0)),
        byProvider = providers,
        localRuns = json.optInt("localRuns", 0),
        estimatedSavingsUsd = json.optDouble("estimatedSavingsUsd", 0.0),
        cacheSavingsUsd = json.optDouble("cacheSavingsUsd", 0.0),
        bridgeMonthUsd = json.optDouble("bridgeMonthUsd", 0.0),
        bridgeTodayUsd = json.optDouble("bridgeTodayUsd", 0.0),
        deviceCount = json.optInt("deviceCount", 1),
        pairingSessionCount = json.optInt("pairingSessionCount", json.optInt("deviceCount", 1)),
        monthlyLimitUsd = json.optDouble("monthlyLimitUsd", -1.0).takeIf { it >= 0 },
        dailyLimitUsd = json.optDouble("dailyLimitUsd", -1.0).takeIf { it >= 0 },
        alert = alert,
        dailyTotals = dailyTotals,
        untrackedProviders = untracked,
    )
}

internal fun fetchCostDashboard(baseUrl: String, token: String): CostDashboardInfo {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/costs"
    val response = LinkHttp.get(url, token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        if (response.code == 404) {
            throw RuntimeException("Bridge needs an update for cost tracking — restart the PC bridge")
        }
        throw RuntimeException("Load costs failed (${response.code})")
    }
    return parseCostDashboard(JSONObject(response.body))
}

internal fun setCostLimits(
    baseUrl: String,
    token: String,
    monthlyLimitUsd: Double?,
    dailyLimitUsd: Double?,
) {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/admin/costs/limits"
    val body = JSONObject().apply {
        put("monthlyLimitUsd", monthlyLimitUsd ?: JSONObject.NULL)
        put("dailyLimitUsd", dailyLimitUsd ?: JSONObject.NULL)
    }
    val response = LinkHttp.postJson(url, body.toString(), token, connectTimeoutMs = 8000, readTimeoutMs = 8000)
    if (response.code !in 200..299) {
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Update limits failed (${response.code})")
    }
}

/** Save an exported conversation markdown file into the session's exports/ folder on the PC. */
internal fun exportConversationToPc(
    baseUrl: String,
    token: String,
    projectId: String?,
    filename: String,
    content: String,
): String {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val url = "${normalizedBaseUrl.trimEnd('/')}/api/exports"
    val body = JSONObject().apply {
        if (!projectId.isNullOrBlank()) put("projectId", projectId)
        put("filename", filename)
        put("content", content)
    }
    val response = LinkHttp.postJson(url, body.toString(), token, connectTimeoutMs = 8000, readTimeoutMs = 30000)
    if (response.code !in 200..299) {
        if (response.code == 404) {
            throw RuntimeException("Bridge needs an update for exports — restart the PC bridge")
        }
        val err = runCatching { JSONObject(response.body).optString("error") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        throw RuntimeException(err ?: "Export failed (${response.code})")
    }
    val json = JSONObject(response.body)
    return json.optString("path", "exports/")
}
