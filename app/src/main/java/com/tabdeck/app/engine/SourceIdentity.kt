package com.tabdeck.app.engine

import java.security.MessageDigest
import java.util.Base64

/**
 * Converts browser-session-scoped tab identifiers into bounded opaque source IDs.
 *
 * Browser tab IDs may be reused after a browser restart. Prefixing the raw tab ID with a
 * one-way session fingerprint prevents an unrelated future tab from being merged with or
 * archived as the previous tab. Legacy payloads without a session ID remain importable,
 * but they are not considered safe evidence for destructive complete-snapshot reconciliation.
 */
object SourceIdentity {
    private const val PREFIX = "sid1:"
    private const val SESSION_FINGERPRINT_BYTES = 12
    private const val MAX_SESSION_ID_LENGTH = 256
    private const val MAX_RAW_TAB_ID_LENGTH = 120
    const val MAX_OPAQUE_TAB_ID_LENGTH = 160

    fun encodeTabId(sessionId: String?, rawTabId: String): String {
        val cleanTabId = clean(rawTabId, MAX_RAW_TAB_ID_LENGTH)
        if (cleanTabId.isBlank()) return ""

        val cleanSessionId = clean(sessionId.orEmpty(), MAX_SESSION_ID_LENGTH)
        if (cleanSessionId.isBlank()) return cleanTabId.take(MAX_OPAQUE_TAB_ID_LENGTH)

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cleanSessionId.toByteArray(Charsets.UTF_8))
            .copyOf(SESSION_FINGERPRINT_BYTES)
        val fingerprint = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return "$PREFIX$fingerprint:$cleanTabId".take(MAX_OPAQUE_TAB_ID_LENGTH)
    }

    fun isSessionScoped(sourceTabId: String): Boolean {
        if (!sourceTabId.startsWith(PREFIX)) return false
        val separator = sourceTabId.indexOf(':', PREFIX.length)
        if (separator != PREFIX.length + 16 || separator == sourceTabId.lastIndex) return false
        return sourceTabId.substring(PREFIX.length, separator)
            .all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    private fun clean(value: String, maxLength: Int): String = value
        .filterNot { it.isISOControl() }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
}
