package com.invictus.link

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export a conversation (prompt history) as clean Markdown — either shared
 * from the phone or saved into the session's exports/ folder on the PC.
 */

internal fun buildConversationMarkdown(
    exchanges: List<PromptExchange>,
    sessionName: String,
): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val exportedAt = dateFormat.format(Date())
    val builder = StringBuilder()
    builder.appendLine("# Invictus Link conversation")
    builder.appendLine()
    builder.appendLine("- **Session:** $sessionName")
    builder.appendLine("- **Exported:** $exportedAt")
    builder.appendLine("- **Exchanges:** ${exchanges.size}")
    builder.appendLine()
    builder.appendLine("---")
    for (exchange in exchanges) {
        val stamp = dateFormat.format(Date(exchange.timestampMs))
        builder.appendLine()
        builder.appendLine("## You — $stamp")
        builder.appendLine()
        builder.appendLine(exchange.prompt.trim())
        builder.appendLine()
        builder.appendLine(if (exchange.ok) "## Agent" else "## Agent (error)")
        builder.appendLine()
        builder.appendLine(exchange.response.trim())
        builder.appendLine()
        builder.appendLine("---")
    }
    return builder.toString()
}

internal fun defaultExportFilename(sessionName: String): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
    val safeSession = sessionName.replace(Regex("[^\\w-]+"), "-").trim('-').ifBlank { "session" }
    return "conversation-$safeSession-$stamp.md"
}

/** Write the markdown to cache and open the Android share sheet. */
internal fun shareConversationMarkdown(context: Context, filename: String, content: String) {
    val dir = File(context.cacheDir, "exports")
    dir.mkdirs()
    val file = File(dir, filename)
    file.writeText(content, Charsets.UTF_8)
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, filename)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Share conversation").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
