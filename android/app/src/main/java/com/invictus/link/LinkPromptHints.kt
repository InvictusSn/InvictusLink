package com.invictus.link

private const val LINK_APP_UPDATE_HINT =
    "\n\n---\nUnderstood — head to **Settings → Customize & publish** to build and publish the update, then **Check for update** to install it on your phone."

private fun isConversationalAppMention(lowered: String): Boolean =
    Regex("""\bhad to (fix|change|update|work on|deal with)\b""").containsMatchIn(lowered) ||
        Regex("""\bi (fixed|changed|updated|was fixing|had fixed)\b""").containsMatchIn(lowered) ||
        Regex("""\b(was|been|just) (fixing|changing|updating|working on)\b""").containsMatchIn(lowered) ||
        Regex("""\bsomething with the app\b""").containsMatchIn(lowered) ||
        Regex("""\bwhat did (you|i) (fix|change|end up fixing)\b""").containsMatchIn(lowered) ||
        Regex("""\bto answer your question\b""").containsMatchIn(lowered) ||
        Regex("""\btesting is going\b""").containsMatchIn(lowered)

private fun hasUiChangeTarget(lowered: String): Boolean =
    Regex(
        """\b(home\s+(screen|tab|page)|settings\s+(screen|tab|page)|attach(ment)?\s*(sheet|menu|button)?|snackbar|linkscreens?|linkapp|bottom\s+(strip|banner|bar)|notification\s+(strip|banner)?|white\s+strip)\b""",
    ).containsMatchIn(lowered)

private fun hasDirectChangeRequest(lowered: String): Boolean {
    val changeVerbs =
        """(fix|add|change|update|improve|polish|tweak|modify|implement|remove|refactor|ship|release|bump|crash|bug)"""
    return Regex(
        """\b(please|can you|could you|would you|i need you to|help me|go ahead and|let's|yeah please)\b[^.!?]{0,60}\b$changeVerbs\b""",
    ).containsMatchIn(lowered) ||
        Regex("""^\s*$changeVerbs\b""").containsMatchIn(lowered) ||
        Regex("""\b$changeVerbs\s+(the|this|that|a|an)\b""").containsMatchIn(lowered) ||
        Regex("""\bmake\s+(the\s+)?app\b""").containsMatchIn(lowered)
}

internal fun promptImpliesLinkAppUpdate(prompt: String): Boolean {
    val lowered = prompt.lowercase().trim()
    if (lowered.length < 8) return false

    if (Regex("""\b(publish|build)\s+(the\s+)?(app\s+)?update\b""").containsMatchIn(lowered)) return true
    if (Regex("""\bbuild\s+and\s+publish\b""").containsMatchIn(lowered)) return true
    if (isConversationalAppMention(lowered)) return false

    val changeIntent =
        Regex(
            """\b(fix|add|change|update|improve|polish|tweak|modify|implement|remove|refactor|ship|release|bump|version|crash|bug)\b""",
        ).containsMatchIn(lowered)
    if (!changeIntent) return false

    val uiTarget = hasUiChangeTarget(lowered)
    val appContext =
        Regex("""\b(invictus\s*link|link\s+app|the\s+app|android\s+app|mobile\s+app)\b""").containsMatchIn(lowered) ||
            Regex("""\b(in|on|to|for)\s+(the\s+)?app\b""").containsMatchIn(lowered) ||
            uiTarget ||
            (
                Regex("""\b(ui|screen|button|tab|sheet|dialog|snackbar)\b""").containsMatchIn(lowered) &&
                    Regex("""\bapp\b""").containsMatchIn(lowered)
                )
    val directRequest = hasDirectChangeRequest(lowered)
    val bridgeOnly = Regex("""\bbridge\b""").containsMatchIn(lowered) && !appContext

    if (uiTarget) return !bridgeOnly
    if (appContext && directRequest) return !bridgeOnly
    return false
}

internal fun appendLinkAppUpdateHintIfNeeded(prompt: String, output: String): String {
    if (!promptImpliesLinkAppUpdate(prompt)) return output
    val lowered = output.lowercase()
    if (
        lowered.contains("customize & publish") ||
        lowered.contains("publish update") ||
        lowered.contains("check for update")
    ) {
        return output
    }
    return output.trimEnd() + LINK_APP_UPDATE_HINT
}
