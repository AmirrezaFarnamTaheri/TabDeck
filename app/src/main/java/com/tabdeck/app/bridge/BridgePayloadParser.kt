package com.tabdeck.app.bridge

import com.tabdeck.app.engine.SourceIdentity
import com.tabdeck.app.engine.UrlNormalizer
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.TabItem
import org.json.JSONArray
import org.json.JSONObject

/** Strict decoder for extension and Desktop Link snapshots. */
object BridgePayloadParser {
    data class ParsedImport(
        val tabs: List<TabItem>,
        val sourceLabel: String,
        val deviceName: String,
        val sourceBrowser: BrowserId,
        /** True only when the payload proves it represents one complete browser/device inventory. */
        val completeSnapshot: Boolean,
    )

    fun parse(raw: String): ParsedImport {
        val root = JSONObject(raw)
        val now = System.currentTimeMillis()
        val sourceBrowser = BrowserId.fromWireName(root.optString("browser"))
        val sourceLabel = cleanText(root.optString("sourceLabel", sourceBrowser.displayName))
            .ifBlank { sourceBrowser.displayName }
        val deviceName = cleanText(root.optString("deviceName", root.optString("deviceId")))
        val sourceSessionId = cleanIdentifier(
            root.optString("sourceSessionId", root.optString("sessionId")),
            MAX_SOURCE_SESSION_ID_LENGTH,
        )
        val identityVersion = root.optInt("identityVersion", 0)
        val sessionGroup = cleanText(root.optString("group"))
        val capturedAt = safeTimestamp(root.optLong("capturedAt", now), now)
        val tabArray = root.optJSONArray("tabs") ?: JSONArray()

        val parsed = buildList {
            for (index in 0 until tabArray.length()) {
                val item = tabArray.optJSONObject(index) ?: continue
                val url = UrlNormalizer.sanitizeWebUrl(item.optString("url")) ?: continue
                val itemBrowserWire = item.optString("browser")
                val itemBrowser = if (itemBrowserWire.isBlank()) sourceBrowser else BrowserId.fromWireName(itemBrowserWire)
                val itemDevice = cleanText(item.optString("deviceId", deviceName))
                val rawSourceTabId = cleanIdentifier(item.optString("id", item.optString("tabId")), 160)
                val sourceTabId = SourceIdentity.encodeTabId(sourceSessionId, rawSourceTabId)
                val group = cleanText(item.optString("group", sessionGroup))
                val createdAt = safeTimestamp(item.optLong("createdAt", capturedAt), now)
                val lastSeenAt = safeTimestamp(item.optLong("lastSeenAt", capturedAt), now)

                add(
                    TabItem(
                        url = url,
                        title = cleanText(item.optString("title")),
                        browser = itemBrowser,
                        sourceGroup = group,
                        assignedGroup = group.ifBlank { "Inbox" },
                        pinned = item.optBoolean("pinned", false),
                        createdAtEpochMs = createdAt,
                        lastSeenAtEpochMs = lastSeenAt,
                        sourceDevice = itemDevice,
                        sourceTabId = sourceTabId,
                    ),
                )
            }
        }

        val requestedCompleteSnapshot = root.optBoolean("completeSnapshot", false)
        val identityIsProvable = identityVersion == CURRENT_IDENTITY_VERSION &&
            sourceBrowser.isLaunchTarget &&
            deviceName.isNotBlank() &&
            sourceSessionId.isNotBlank() &&
            parsed.all { tab ->
                tab.browser == sourceBrowser &&
                    tab.sourceDevice == deviceName &&
                    SourceIdentity.isSessionScoped(tab.sourceTabId)
            }
        return ParsedImport(
            tabs = parsed,
            sourceLabel = sourceLabel,
            deviceName = deviceName,
            sourceBrowser = sourceBrowser,
            completeSnapshot = requestedCompleteSnapshot && identityIsProvable,
        )
    }

    private fun cleanText(value: String, maxLength: Int): String = value
        .filterNot { it.isISOControl() }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)

    private fun safeTimestamp(value: Long, now: Long): Long = value.coerceIn(MIN_REASONABLE_EPOCH_MS, now + MAX_FUTURE_SKEW_MS)

    private const val CURRENT_IDENTITY_VERSION = 1
    private const val MAX_SOURCE_SESSION_ID_LENGTH = 256
    private const val MIN_REASONABLE_EPOCH_MS = 946_684_800_000L // 2000-01-01
    private const val MAX_FUTURE_SKEW_MS = 86_400_000L
}
