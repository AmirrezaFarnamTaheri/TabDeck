package com.tabdeck.app.engine

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** URL validation and normalization shared by import, dedupe, rules, and transfer surfaces. */
object UrlNormalizer {
    private const val MAX_URL_LENGTH = 16_384

    private val trackingKeys = setOf(
        "fbclid",
        "gclid",
        "dclid",
        "msclkid",
        "mc_cid",
        "mc_eid",
        "igshid",
        "ref_src",
        "ref_url",
        "srsltid",
        "mkt_tok",
        "vero_conv",
        "vero_id",
        "_hsenc",
        "_hsmi",
        "oly_anon_id",
        "oly_enc_id",
        "rb_clickid",
        "wickedid",
    )

    fun exact(url: String): String = url.trim()

    /**
     * Returns a browser-openable HTTP(S) URL or null. The original URL is preserved rather than
     * rewritten so imports never silently change a destination. Canonicalization is separate.
     */
    fun sanitizeWebUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length !in 8..MAX_URL_LENGTH || trimmed.any { it.isISOControl() }) return null
        val candidate = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque || uri.rawAuthority.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (!hasValidAuthorityAndPort(uri)) return null
        val host = canonicalHost(uri) ?: return null
        if (host.isBlank() || host.any(Char::isWhitespace)) return null
        return candidate
    }

    fun normalized(url: String, stripTrackingParameters: Boolean = true): String {
        val input = sanitizeWebUrl(url) ?: url.trim()
        if (input.isEmpty()) return input
        return runCatching {
            val uri = URI(input).normalize()
            val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty().ifEmpty { "https" }
            val host = canonicalHost(uri)?.removePrefix("www.") ?: return@runCatching input
            val displayHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
            val port = when {
                uri.port == -1 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
            val path = normalizePath(uri.rawPath)
            val query = normalizeQuery(uri.rawQuery, stripTrackingParameters)
            "$scheme://$displayHost$port$path$query"
        }.getOrDefault(input)
    }

    fun hostAndPath(url: String): String {
        val normalized = normalized(url, stripTrackingParameters = false)
        return runCatching {
            val uri = URI(normalized)
            val host = canonicalHost(uri)?.removePrefix("www.").orEmpty()
            val path = normalizePath(uri.rawPath)
            "$host$path"
        }.getOrDefault(normalized.substringBefore('?').substringBefore('#'))
    }

    fun host(url: String): String = runCatching {
        val sanitized = sanitizeWebUrl(url) ?: return@runCatching ""
        canonicalHost(URI(sanitized))?.removePrefix("www.").orEmpty()
    }.getOrDefault("")

    private fun canonicalHost(uri: URI): String? {
        val raw = uri.host ?: run {
            val authority = uri.rawAuthority?.substringAfterLast('@') ?: return null
            when {
                authority.startsWith('[') -> authority.substringAfter('[').substringBefore(']')
                else -> authority.substringBeforeLast(':', authority)
            }
        }
        val unwrapped = raw.removePrefix("[").removeSuffix("]").trimEnd('.')
        if (unwrapped.isBlank()) return null
        return if (':' in unwrapped) {
            unwrapped.lowercase(Locale.ROOT)
        } else {
            runCatching { IDN.toASCII(unwrapped, IDN.USE_STD3_ASCII_RULES) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.lowercase(Locale.ROOT)
        }
    }

    private fun hasValidAuthorityAndPort(uri: URI): Boolean {
        val authority = uri.rawAuthority ?: return false
        if (authority.count { it == '@' } > 0) return false
        if (authority.startsWith('[')) {
            val closing = authority.indexOf(']')
            if (closing <= 1) return false
            val suffix = authority.substring(closing + 1)
            if (suffix.isEmpty()) return true
            if (!suffix.startsWith(':')) return false
            val port = suffix.drop(1).toIntOrNull() ?: return false
            return port in 1..65_535
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) return true
        if (authority.indexOf(':') != colon) return false
        val port = authority.substring(colon + 1).toIntOrNull() ?: return false
        return port in 1..65_535
    }

    private fun normalizePath(rawPath: String?): String {
        val value = rawPath.orEmpty().ifEmpty { "/" }
        val collapsed = value.replace(Regex("/{2,}"), "/")
        return if (collapsed.length > 1) collapsed.trimEnd('/') else collapsed
    }

    private fun normalizeQuery(rawQuery: String?, stripTrackingParameters: Boolean): String {
        if (rawQuery.isNullOrBlank()) return ""
        val params = rawQuery
            .split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=').decode().trim()
                val value = part.substringAfter('=', "").decode().trim()
                if (key.isBlank() || (stripTrackingParameters && isTrackingKey(key))) null else key to value
            }
            .sortedWith(
                compareBy<Pair<String, String>> { it.first.lowercase(Locale.ROOT) }
                    .thenBy { it.second },
            )
        if (params.isEmpty()) return ""
        return params.joinToString(prefix = "?", separator = "&") { (key, value) ->
            if (value.isEmpty()) key.encode() else "${key.encode()}=${value.encode()}"
        }
    }

    private fun isTrackingKey(key: String): Boolean {
        val normalized = key.lowercase(Locale.ROOT)
        return normalized.startsWith("utm_") || normalized in trackingKeys
    }

    private fun String.decode(): String =
        runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrDefault(this)

    private fun String.encode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
