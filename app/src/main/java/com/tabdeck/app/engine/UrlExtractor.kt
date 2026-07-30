package com.tabdeck.app.engine

/** Extracts browser URLs from shares, Markdown, HTML-ish text, and exported tab lists. */
object UrlExtractor {
    private val urlRegex = Regex("(?i)(?<![\\p{L}\\p{N}_])((?:https?://|www\\.)[^\\s<>\\\"'`]+)")
    private val terminalPunctuation = setOf('.', ',', ';', ':', '!', '?')

    fun extract(text: String): List<String> = urlRegex.findAll(text)
        .map { cleanCandidate(it.groupValues[1]) }
        .mapNotNull(UrlNormalizer::sanitizeWebUrl)
        .toList()

    private fun cleanCandidate(raw: String): String {
        var value = raw.trim().replace("&amp;", "&", ignoreCase = true)
        while (value.lastOrNull() in terminalPunctuation) value = value.dropLast(1)
        value = trimUnmatchedCloser(value, '(', ')')
        value = trimUnmatchedCloser(value, '[', ']')
        value = trimUnmatchedCloser(value, '{', '}')
        return value
    }

    private fun trimUnmatchedCloser(value: String, opener: Char, closer: Char): String {
        var result = value
        while (result.endsWith(closer) && result.count { it == closer } > result.count { it == opener }) {
            result = result.dropLast(1)
        }
        return result
    }
}
