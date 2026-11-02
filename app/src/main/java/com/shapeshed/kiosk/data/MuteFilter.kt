package com.shapeshed.kiosk.data

/**
 * Turns what the user typed ("https://www.Example.com/x") into the bare host the filter compares
 * against ("example.com"), or null if nothing host-like is left.
 */
fun normalizeMutedDomain(input: String): String? {
    val host = input.trim().lowercase()
        .substringAfter("://")
        .substringBefore('/')
        .removePrefix("www.")
        .trim('.')
    return host.takeIf { it.isNotEmpty() && ' ' !in it }
}

/** Keywords compare case-insensitively; blank input is dropped. */
fun normalizeMutedKeyword(input: String): String? = input.trim().lowercase().takeIf { it.isNotEmpty() }

/**
 * True when the story's host is a muted domain or one of its subdomains, or its title contains a
 * muted keyword as a whole word ("go" mutes "Go 1.24", not "Google").
 */
fun isMuted(story: Story, domains: Set<String>, keywords: Set<String>): Boolean {
    val host = hostOf(story.url)?.lowercase()?.removePrefix("www.")
    if (host != null && domains.any { host == it || host.endsWith(".$it") }) return true
    val title = story.title.lowercase()
    return keywords.any { containsWord(title, it) }
}

private fun containsWord(text: String, word: String): Boolean {
    var from = 0
    while (true) {
        val at = text.indexOf(word, from)
        if (at < 0) return false
        val before = text.getOrNull(at - 1)
        val after = text.getOrNull(at + word.length)
        if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) return true
        from = at + 1
    }
}
