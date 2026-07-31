package com.tabdeck.app.data

import com.tabdeck.app.data.local.LibraryQueryCodec
import com.tabdeck.app.bridge.BridgeNetwork
import com.tabdeck.app.model.AccentStyle
import com.tabdeck.app.model.AppSettings
import com.tabdeck.app.model.AppSnapshot
import com.tabdeck.app.model.BridgeScope
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.DeckBackup
import com.tabdeck.app.model.DeckDefinition
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.GroupDefinition
import com.tabdeck.app.model.ImportSession
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.LibraryLayout
import com.tabdeck.app.model.SmartView
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.RegexTarget
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabStatus
import com.tabdeck.app.model.SyncMissingPolicy
import com.tabdeck.app.model.ThemeMode
import com.tabdeck.app.model.TransferEvent
import com.tabdeck.app.model.TransferPacing
import com.tabdeck.app.model.ViewDensity
import com.tabdeck.app.model.newBridgeToken
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Versioned portable backup codec and legacy v1 migration reader. */
object SnapshotJsonCodec {
    const val VERSION = 3

    sealed interface DecodeResult {
        data class Success(val snapshot: AppSnapshot) : DecodeResult
        data object NotBackup : DecodeResult
        data class Rejected(val reason: String) : DecodeResult
    }

    fun encode(snapshot: AppSnapshot): String = JSONObject().apply {
        put("format", "tabdeck-backup")
        put("version", VERSION)
        put("exportedAtEpochMs", System.currentTimeMillis())
        put("settings", settingsToJson(snapshot.settings.copy(bridgeToken = "")))
        put("tabs", JSONArray().apply { snapshot.tabs.forEach { put(tabToJson(it)) } })
        put("rules", JSONArray().apply { snapshot.rules.forEach { put(ruleToJson(it)) } })
        put("groups", JSONArray().apply { snapshot.groups.forEach { put(groupToJson(it)) } })
        put("smartViews", JSONArray().apply { snapshot.smartViews.forEach { put(smartViewToJson(it)) } })
        put("decks", JSONArray().apply { snapshot.deckBackups.forEach { put(deckBackupToJson(it)) } })
        put("transferHistory", JSONArray().apply { snapshot.transferHistory.forEach { put(transferToJson(it)) } })
        put("importHistory", JSONArray().apply { snapshot.importHistory.forEach { put(importToJson(it)) } })
    }.toString(2)

    fun decode(raw: String?): AppSnapshot = (decodeClassified(raw) as? DecodeResult.Success)?.snapshot ?: AppSnapshot()

    /** Backward-compatible nullable decoder. Prefer [decodeClassified] for user-selected input. */
    fun decodeOrNull(raw: String?): AppSnapshot? =
        (decodeClassified(raw) as? DecodeResult.Success)?.snapshot

    /**
     * Classifies input before decoding so malformed or unsupported backup-shaped JSON can never be
     * reinterpreted as a plain URL list. Only definitively unrelated content returns [DecodeResult.NotBackup].
     */
    fun decodeClassified(raw: String?): DecodeResult {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return DecodeResult.NotBackup
        val hintedBackup = BackupInputClassifier.classify(text) == BackupInputClassifier.Kind.BACKUP_SHAPED
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return if (hintedBackup) DecodeResult.Rejected("Malformed TabDeck backup JSON") else DecodeResult.NotBackup
        }

        val format = root.optString("format")
        val backupShape = format == BACKUP_FORMAT || (
            root.has("tabs") && listOf("version", "settings", "bridgeToken", "rules", "groups", "decks")
                .any(root::has)
        )
        if (!backupShape) return DecodeResult.NotBackup
        if (format.isNotBlank() && format != BACKUP_FORMAT) {
            return DecodeResult.Rejected("Unsupported backup format: $format")
        }

        val version = root.optInt("version", 1)
        if (version !in 1..VERSION) {
            return DecodeResult.Rejected("Unsupported TabDeck backup version: $version")
        }
        if (root.optJSONArray("tabs") == null) {
            return DecodeResult.Rejected("Backup has no tab inventory")
        }

        return runCatching { DecodeResult.Success(decodeRoot(root)) }
            .getOrElse { DecodeResult.Rejected(it.message ?: "Malformed TabDeck backup") }
    }

    private fun decodeRoot(root: JSONObject): AppSnapshot {
        val settingsObject = root.optJSONObject("settings")
        val legacyToken = root.optString("bridgeToken").ifBlank { newBridgeToken() }
        val settings = if (settingsObject != null) settingsFromJson(settingsObject) else AppSettings(
            bridgeToken = legacyToken,
            onboardingComplete = root.optBoolean("onboardingComplete", false),
        )
        return AppSnapshot(
            tabs = root.optJSONArray("tabs").toTabList(),
            rules = root.optJSONArray("rules").toRuleList().ifEmpty { AppSnapshot.defaultRules() },
            groups = root.optJSONArray("groups").toGroupList().ifEmpty { AppSnapshot.defaultGroups() },
            smartViews = root.optJSONArray("smartViews").toSmartViewList(),
            deckBackups = root.optJSONArray("decks").toDeckBackupList(),
            transferHistory = root.optJSONArray("transferHistory").toTransferList(),
            importHistory = root.optJSONArray("importHistory").toImportList(),
            settings = settings,
        )
    }

    private fun settingsToJson(value: AppSettings): JSONObject = JSONObject().apply {
        put("bridgeToken", value.bridgeToken)
        put("bridgeScope", value.bridgeScope.name)
        put("bridgeSessionMinutes", value.bridgeSessionMinutes)
        put("onboardingComplete", value.onboardingComplete)
        put("autoCategorizeImports", value.autoCategorizeImports)
        put("stripTrackingParameters", value.stripTrackingParameters)
        put("defaultDedupeMode", value.defaultDedupeMode.name)
        put("defaultKeepPolicy", value.defaultKeepPolicy.name)
        put("transferPacing", value.transferPacing.name)
        put("viewDensity", value.viewDensity.name)
        put("libraryLayout", value.libraryLayout.name)
        put("themeMode", value.themeMode.name)
        put("dynamicColor", value.dynamicColor)
        put("accentStyle", value.accentStyle.name)
        put("reduceMotion", value.reduceMotion)
        put("hapticFeedback", value.hapticFeedback)
        put("syncMissingPolicy", value.syncMissingPolicy.name)
        put("staleAfterDays", value.staleAfterDays)
        put("showAdvancedControls", value.showAdvancedControls)
    }

    private fun settingsFromJson(value: JSONObject): AppSettings = AppSettings(
        bridgeToken = value.optString("bridgeToken").ifBlank { newBridgeToken() },
        bridgeScope = BridgeScope.THIS_DEVICE,
        bridgeSessionMinutes = value.optInt("bridgeSessionMinutes", 20).coerceIn(1, BridgeNetwork.MAX_SESSION_MINUTES),
        onboardingComplete = value.optBoolean("onboardingComplete", false),
        autoCategorizeImports = value.optBoolean("autoCategorizeImports", true),
        stripTrackingParameters = value.optBoolean("stripTrackingParameters", true),
        defaultDedupeMode = enumOrDefault(value.optString("defaultDedupeMode"), DedupeMode.NORMALIZED_URL),
        defaultKeepPolicy = enumOrDefault(value.optString("defaultKeepPolicy"), KeepPolicy.RICHEST),
        transferPacing = enumOrDefault(value.optString("transferPacing"), TransferPacing.BALANCED),
        viewDensity = enumOrDefault(value.optString("viewDensity"), ViewDensity.COMFORTABLE),
        libraryLayout = enumOrDefault(value.optString("libraryLayout"), LibraryLayout.LIST),
        themeMode = enumOrDefault(value.optString("themeMode"), ThemeMode.SYSTEM),
        dynamicColor = value.optBoolean("dynamicColor", false),
        accentStyle = enumOrDefault(value.optString("accentStyle"), AccentStyle.OCEAN),
        reduceMotion = value.optBoolean("reduceMotion", false),
        hapticFeedback = value.optBoolean("hapticFeedback", true),
        syncMissingPolicy = enumOrDefault(value.optString("syncMissingPolicy"), SyncMissingPolicy.KEEP),
        staleAfterDays = value.optInt("staleAfterDays", 30).coerceAtLeast(1),
        showAdvancedControls = value.optBoolean("showAdvancedControls", false),
    )

    private fun tabToJson(tab: TabItem): JSONObject = JSONObject().apply {
        put("id", tab.id)
        put("url", tab.url)
        put("title", tab.title)
        put("browser", tab.browser.name)
        put("sourceGroup", tab.sourceGroup)
        put("assignedGroup", tab.assignedGroup)
        put("createdAtEpochMs", tab.createdAtEpochMs)
        put("importedAtEpochMs", tab.importedAtEpochMs)
        put("lastSeenAtEpochMs", tab.lastSeenAtEpochMs)
        put("pinned", tab.pinned)
        put("notes", tab.notes)
        put("tags", JSONArray(tab.tags.toList().sorted()))
        put("status", tab.status.name)
        tab.snoozedUntilEpochMs?.let { put("snoozedUntilEpochMs", it) }
        put("sourceDevice", tab.sourceDevice)
        put("sourceTabId", tab.sourceTabId)
        tab.lastTransferredAtEpochMs?.let { put("lastTransferredAtEpochMs", it) }
        put("transferCount", tab.transferCount)
    }

    private fun ruleToJson(rule: RegexRule): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("name", rule.name)
        put("pattern", rule.pattern)
        put("target", rule.target.name)
        put("destinationGroup", rule.destinationGroup)
        put("priority", rule.priority)
        put("enabled", rule.enabled)
        put("ignoreCase", rule.ignoreCase)
        put("addTags", JSONArray(rule.addTags.toList().sorted()))
        put("stopAfterMatch", rule.stopAfterMatch)
    }

    private fun groupToJson(group: GroupDefinition): JSONObject = JSONObject().apply {
        put("id", group.id)
        put("name", group.name)
        put("colorKey", group.colorKey)
        put("iconKey", group.iconKey)
        put("sortOrder", group.sortOrder)
        put("isSystem", group.isSystem)
    }

    private fun smartViewToJson(view: SmartView): JSONObject = JSONObject().apply {
        put("id", view.id)
        put("name", view.name)
        put("query", JSONObject(LibraryQueryCodec.encode(view.query)))
        put("iconKey", view.iconKey)
        put("colorKey", view.colorKey)
        put("pinned", view.pinned)
        put("sortOrder", view.sortOrder)
    }

    private fun deckBackupToJson(value: DeckBackup): JSONObject = JSONObject().apply {
        val deck = value.deck
        put("id", deck.id)
        put("name", deck.name)
        put("description", deck.description)
        put("iconKey", deck.iconKey)
        put("colorKey", deck.colorKey)
        put("createdAtEpochMs", deck.createdAtEpochMs)
        put("updatedAtEpochMs", deck.updatedAtEpochMs)
        put("tabIds", JSONArray(value.tabIds.distinct()))
    }

    private fun transferToJson(event: TransferEvent): JSONObject = JSONObject().apply {
        put("id", event.id)
        put("targetBrowser", event.targetBrowser.name)
        put("attempted", event.attempted)
        put("opened", event.opened)
        put("failed", event.failed)
        put("cancelled", event.cancelled)
        put("durationMs", event.durationMs)
        put("createdAtEpochMs", event.createdAtEpochMs)
    }

    private fun importToJson(session: ImportSession): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("source", session.source.name)
        put("sourceLabel", session.sourceLabel)
        put("received", session.received)
        put("accepted", session.accepted)
        put("rejected", session.rejected)
        put("deviceName", session.deviceName)
        put("createdAtEpochMs", session.createdAtEpochMs)
    }

    private fun JSONArray?.toTabList(): List<TabItem> = buildList {
        val array = this@toTabList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = item.optString("url")
            if (url.isBlank()) continue
            add(
                TabItem(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    url = url,
                    title = item.optString("title"),
                    browser = enumOrDefault(item.optString("browser"), BrowserId.UNKNOWN),
                    sourceGroup = item.optString("sourceGroup"),
                    assignedGroup = item.optString("assignedGroup", "Inbox"),
                    createdAtEpochMs = item.optLong("createdAtEpochMs", System.currentTimeMillis()),
                    importedAtEpochMs = item.optLong("importedAtEpochMs", System.currentTimeMillis()),
                    lastSeenAtEpochMs = item.optLong("lastSeenAtEpochMs", item.optLong("importedAtEpochMs", System.currentTimeMillis())),
                    pinned = item.optBoolean("pinned", false),
                    notes = item.optString("notes"),
                    tags = item.optJSONArray("tags").toStringSet(),
                    status = enumOrDefault(item.optString("status"), TabStatus.ACTIVE),
                    snoozedUntilEpochMs = item.optLongOrNull("snoozedUntilEpochMs"),
                    sourceDevice = item.optString("sourceDevice"),
                    sourceTabId = item.optString("sourceTabId"),
                    lastTransferredAtEpochMs = item.optLongOrNull("lastTransferredAtEpochMs"),
                    transferCount = item.optInt("transferCount", 0),
                ),
            )
        }
    }

    private fun JSONArray?.toRuleList(): List<RegexRule> = buildList {
        val array = this@toRuleList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val pattern = item.optString("pattern")
            if (pattern.isBlank()) continue
            add(
                RegexRule(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = item.optString("name", "Rule ${index + 1}"),
                    pattern = pattern,
                    target = enumOrDefault(item.optString("target"), RegexTarget.ANY),
                    destinationGroup = item.optString("destinationGroup", "Inbox"),
                    priority = item.optInt("priority", 100),
                    enabled = item.optBoolean("enabled", true),
                    ignoreCase = item.optBoolean("ignoreCase", true),
                    addTags = item.optJSONArray("addTags").toStringSet(),
                    stopAfterMatch = item.optBoolean("stopAfterMatch", true),
                ),
            )
        }
    }

    private fun JSONArray?.toGroupList(): List<GroupDefinition> = buildList {
        val array = this@toGroupList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isBlank()) continue
            add(
                GroupDefinition(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    colorKey = item.optString("colorKey", "indigo"),
                    iconKey = item.optString("iconKey", "folder"),
                    sortOrder = item.optInt("sortOrder", 100),
                    isSystem = item.optBoolean("isSystem", false),
                ),
            )
        }
    }

    private fun JSONArray?.toSmartViewList(): List<SmartView> = buildList {
        val array = this@toSmartViewList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            val queryJson = item.optJSONObject("query")?.toString() ?: item.optString("queryJson")
            add(
                SmartView(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    query = LibraryQueryCodec.decode(queryJson),
                    iconKey = item.optString("iconKey", "filter"),
                    colorKey = item.optString("colorKey", "indigo"),
                    pinned = item.optBoolean("pinned", false),
                    sortOrder = item.optInt("sortOrder", 100),
                ),
            )
        }
    }

    private fun JSONArray?.toDeckBackupList(): List<DeckBackup> = buildList {
        val array = this@toDeckBackupList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            val now = System.currentTimeMillis()
            val ids = buildList {
                val values = item.optJSONArray("tabIds") ?: JSONArray()
                for (position in 0 until values.length()) {
                    values.optString(position).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }.distinct()
            add(
                DeckBackup(
                    deck = DeckDefinition(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        description = item.optString("description"),
                        iconKey = item.optString("iconKey", "deck"),
                        colorKey = item.optString("colorKey", "violet"),
                        tabCount = ids.size,
                        createdAtEpochMs = item.optLong("createdAtEpochMs", now),
                        updatedAtEpochMs = item.optLong("updatedAtEpochMs", now),
                    ),
                    tabIds = ids,
                ),
            )
        }
    }

    private fun JSONArray?.toTransferList(): List<TransferEvent> = buildList {
        val array = this@toTransferList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                TransferEvent(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    targetBrowser = enumOrDefault(item.optString("targetBrowser"), BrowserId.UNKNOWN),
                    attempted = item.optInt("attempted"),
                    opened = item.optInt("opened"),
                    failed = item.optInt("failed"),
                    cancelled = item.optBoolean("cancelled", false),
                    durationMs = item.optLong("durationMs", 0),
                    createdAtEpochMs = item.optLong("createdAtEpochMs", System.currentTimeMillis()),
                ),
            )
        }
    }

    private fun JSONArray?.toImportList(): List<ImportSession> = buildList {
        val array = this@toImportList ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                ImportSession(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    source = enumOrDefault(item.optString("source"), BrowserId.UNKNOWN),
                    sourceLabel = item.optString("sourceLabel", "Import"),
                    received = item.optInt("received"),
                    accepted = item.optInt("accepted"),
                    rejected = item.optInt("rejected"),
                    deviceName = item.optString("deviceName"),
                    createdAtEpochMs = item.optLong("createdAtEpochMs", System.currentTimeMillis()),
                ),
            )
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        val array = this@toStringSet ?: return@buildSet
        for (index in 0 until array.length()) array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }


    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)
    private const val BACKUP_FORMAT = "tabdeck-backup"
}
