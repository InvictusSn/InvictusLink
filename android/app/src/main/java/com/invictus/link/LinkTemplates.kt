package com.invictus.link

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Prompt library — reusable templates saved on the phone.
 * Templates support {{variable}} placeholders that are filled in on use.
 */

private const val PREF_PROMPT_TEMPLATES = "prompt_templates_json"
private const val MAX_TEMPLATES = 100

internal fun loadPromptTemplates(context: Context): List<PromptTemplate> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_PROMPT_TEMPLATES, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val title = obj.optString("title", "")
                val text = obj.optString("text", "")
                if (id.isBlank() || title.isBlank() || text.isBlank()) continue
                add(
                    PromptTemplate(
                        id = id,
                        title = title,
                        text = text,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        useCount = obj.optInt("useCount", 0),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun savePromptTemplates(context: Context, templates: List<PromptTemplate>) {
    val arr = JSONArray()
    templates.take(MAX_TEMPLATES).forEach { template ->
        arr.put(
            JSONObject()
                .put("id", template.id)
                .put("title", template.title)
                .put("text", template.text)
                .put("createdAt", template.createdAt)
                .put("useCount", template.useCount),
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PROMPT_TEMPLATES, arr.toString())
        .apply()
}

internal fun newPromptTemplate(title: String, text: String): PromptTemplate =
    PromptTemplate(
        id = "tpl_${UUID.randomUUID().toString().take(8)}",
        title = title.trim(),
        text = text.trim(),
    )

/** Distinct {{variable}} names in template order. */
internal fun templateVariables(text: String): List<String> {
    val results = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val open = text.indexOf("{{", index)
        if (open < 0) break
        val close = text.indexOf("}}", open + 2)
        if (close < 0) break
        val name = text.substring(open + 2, close).trim()
        if (name.isNotBlank() && name !in results) results.add(name)
        index = close + 2
    }
    return results
}

/** Replace {{variable}} placeholders with the provided values. */
internal fun fillTemplate(text: String, values: Map<String, String>): String {
    if (values.isEmpty()) return text
    val out = StringBuilder()
    var index = 0
    while (index < text.length) {
        val open = text.indexOf("{{", index)
        if (open < 0) {
            out.append(text.substring(index))
            break
        }
        out.append(text.substring(index, open))
        val close = text.indexOf("}}", open + 2)
        if (close < 0) {
            out.append(text.substring(open))
            break
        }
        val name = text.substring(open + 2, close).trim()
        val replacement = values[name]
        if (replacement != null) out.append(replacement) else out.append(text, open, close + 2)
        index = close + 2
    }
    return out.toString()
}
