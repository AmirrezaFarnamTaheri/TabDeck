package com.tabdeck.app.model

import androidx.compose.runtime.Immutable
import java.security.SecureRandom
import java.util.UUID

private val secureRandom = SecureRandom()

fun newBridgeToken(): String = ByteArray(32).also(secureRandom::nextBytes).joinToString("") { "%02x".format(it) }

enum class BrowserId(
    val displayName: String,
    val packageName: String?,
    val family: String,
    val accentKey: String,
) {
    CHROME("Chrome", "com.android.chrome", "Chromium", "chrome"),
    CHROME_BETA("Chrome Beta", "com.chrome.beta", "Chromium", "chrome"),
    CHROME_DEV("Chrome Dev", "com.chrome.dev", "Chromium", "chrome"),
    CHROME_CANARY("Chrome Canary", "com.chrome.canary", "Chromium", "chrome"),
    FIREFOX("Firefox", "org.mozilla.firefox", "Gecko", "firefox"),
    FIREFOX_BETA("Firefox Beta", "org.mozilla.firefox_beta", "Gecko", "firefox"),
    FIREFOX_NIGHTLY("Firefox Nightly", "org.mozilla.fenix", "Gecko", "firefox"),
    OPERA("Opera", "com.opera.browser", "Chromium", "opera"),
    OPERA_BETA("Opera Beta", "com.opera.browser.beta", "Chromium", "opera"),
    BRAVE("Brave", "com.brave.browser", "Chromium", "brave"),
    BRAVE_BETA("Brave Beta", "com.brave.browser_beta", "Chromium", "brave"),
    EDGE("Microsoft Edge", "com.microsoft.emmx", "Chromium", "edge"),
    EDGE_BETA("Edge Beta", "com.microsoft.emmx.beta", "Chromium", "edge"),
    VIVALDI("Vivaldi", "com.vivaldi.browser", "Chromium", "vivaldi"),
    SAMSUNG_INTERNET("Samsung Internet", "com.sec.android.app.sbrowser", "Chromium", "samsung"),
    DUCKDUCKGO("DuckDuckGo", "com.duckduckgo.mobile.android", "WebView", "duckduckgo"),
    TOR("Tor Browser", "org.torproject.torbrowser", "Gecko", "tor"),
    SHARE_SHEET("Share sheet", null, "Import", "import"),
    CLIPBOARD("Clipboard", null, "Import", "import"),
    FILE_IMPORT("File import", null, "Import", "import"),
    EXTENSION_BRIDGE("Extension bridge", null, "Bridge", "bridge"),
    DESKTOP_LINK("Desktop Link", null, "Bridge", "bridge"),
    UNKNOWN("Unknown", null, "Other", "other");

    val isLaunchTarget: Boolean get() = packageName != null

    companion object {
        fun fromWireName(value: String?): BrowserId {
            val normalized = value.orEmpty().trim().lowercase()
            return entries.firstOrNull {
                it.name.lowercase() == normalized || it.displayName.lowercase() == normalized
            } ?: when {
                "firefox" in normalized && "beta" in normalized -> FIREFOX_BETA
                "nightly" in normalized || "fenix" in normalized -> FIREFOX_NIGHTLY
                "firefox" in normalized -> FIREFOX
                "canary" in normalized -> CHROME_CANARY
                "chrome" in normalized && "beta" in normalized -> CHROME_BETA
                "chrome" in normalized && "dev" in normalized -> CHROME_DEV
                "chrome" in normalized -> CHROME
                "brave" in normalized && "beta" in normalized -> BRAVE_BETA
                "brave" in normalized -> BRAVE
                "opera" in normalized && "beta" in normalized -> OPERA_BETA
                "opera" in normalized -> OPERA
                "edge" in normalized && "beta" in normalized -> EDGE_BETA
                "edge" in normalized -> EDGE
                "vivaldi" in normalized -> VIVALDI
                "samsung" in normalized -> SAMSUNG_INTERNET
                "duck" in normalized -> DUCKDUCKGO
                "tor" in normalized -> TOR
                "desktop" in normalized || "adb" in normalized -> DESKTOP_LINK
                else -> EXTENSION_BRIDGE
            }
        }
    }
}

enum class RegexTarget(val label: String) {
    ANY("URL, title or host"),
    URL("URL"),
    TITLE("Title"),
    HOST("Host"),
    SOURCE_GROUP("Source group"),
}

enum class DedupeMode(val label: String, val description: String) {
    EXACT_URL("Exact URL", "Only byte-for-byte identical URLs"),
    NORMALIZED_URL("Normalized URL", "Ignores common trackers, fragments, query order and trivial URL differences"),
    HOST_AND_PATH("Host + path", "Treats query-string variants as the same destination"),
}

enum class KeepPolicy(val label: String) {
    NEWEST("Keep newest"),
    OLDEST("Keep oldest"),
    RICHEST("Keep richest metadata"),
    PINNED_FIRST("Prefer pinned"),
}

enum class TabStatus(val label: String) {
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    TRASHED("Trash"),
    SNOOZED("Snoozed"),
}

enum class TagEditMode(val label: String) {
    ADD("Add"),
    REMOVE("Remove"),
    REPLACE("Replace"),
}

enum class TabSort(val label: String) {
    IMPORTED_NEWEST("Newest import"),
    IMPORTED_OLDEST("Oldest import"),
    LAST_SEEN("Recently seen"),
    CREATED_NEWEST("Newest source tab"),
    TITLE("Title"),
    HOST("Host"),
    GROUP("Group"),
    BROWSER("Browser"),
    TRANSFER_COUNT("Most transferred"),
}

enum class ViewDensity(val label: String) {
    COMFORTABLE("Comfortable"),
    COMPACT("Compact"),
    DENSE("Dense"),
}

enum class LibraryLayout(val label: String) {
    LIST("List"),
    GRID("Grid"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class AccentStyle(val label: String) {
    VIOLET("Violet"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    MONO("Monochrome"),
}

enum class SyncMissingPolicy(val label: String, val description: String) {
    KEEP("Keep missing tabs", "Never change stored tabs just because a connector snapshot no longer contains them"),
    ARCHIVE("Archive missing tabs", "For explicitly complete snapshots, archive previously seen source tabs that are now absent"),
}

enum class BridgeScope(val label: String, val available: Boolean) {
    THIS_DEVICE("This device only (loopback; fixed)", true),
}

enum class TransferPacing(val label: String, val delayMs: Long) {
    GENTLE("Gentle", 550),
    BALANCED("Balanced", 250),
    FAST("Fast", 90),
}

@Immutable
data class TabItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "",
    val browser: BrowserId = BrowserId.UNKNOWN,
    val sourceGroup: String = "",
    val assignedGroup: String = "Inbox",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val importedAtEpochMs: Long = System.currentTimeMillis(),
    val lastSeenAtEpochMs: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val notes: String = "",
    val tags: Set<String> = emptySet(),
    val status: TabStatus = TabStatus.ACTIVE,
    val snoozedUntilEpochMs: Long? = null,
    val sourceDevice: String = "",
    val sourceTabId: String = "",
    val lastTransferredAtEpochMs: Long? = null,
    val transferCount: Int = 0,
)

@Immutable
data class RegexRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val pattern: String,
    val target: RegexTarget = RegexTarget.ANY,
    val destinationGroup: String,
    val priority: Int = 100,
    val enabled: Boolean = true,
    val ignoreCase: Boolean = true,
    val addTags: Set<String> = emptySet(),
    val stopAfterMatch: Boolean = true,
)

@Immutable
data class GroupDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorKey: String = "indigo",
    val iconKey: String = "folder",
    val sortOrder: Int = 100,
    val isSystem: Boolean = false,
)

@Immutable
data class DuplicateCluster(
    val key: String,
    val tabs: List<TabItem>,
) {
    val removableCount: Int get() = (tabs.size - 1).coerceAtLeast(0)
}

@Immutable
data class DedupePlan(
    val survivors: List<TabItem>,
    val duplicateIds: Set<String>,
    val clusters: List<DuplicateCluster>,
    val mergedTabs: Map<String, TabItem>,
)

@Immutable
data class TransferEvent(
    val id: String = UUID.randomUUID().toString(),
    val targetBrowser: BrowserId,
    val attempted: Int,
    val opened: Int,
    val failed: Int,
    val cancelled: Boolean = false,
    val durationMs: Long = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

@Immutable
data class ImportSession(
    val id: String = UUID.randomUUID().toString(),
    val source: BrowserId,
    val sourceLabel: String = source.displayName,
    val received: Int,
    val accepted: Int,
    val rejected: Int,
    val deviceName: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

@Immutable
data class BridgeSession(
    val enabled: Boolean = false,
    val startedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val acceptedRequests: Int = 0,
    val rejectedRequests: Int = 0,
)

@Immutable
data class LibraryQuery(
    val search: String = "",
    val statuses: Set<TabStatus> = setOf(TabStatus.ACTIVE),
    val browsers: Set<BrowserId> = emptySet(),
    val groups: Set<String> = emptySet(),
    val sourceDevices: Set<String> = emptySet(),
    val sourceGroups: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val pinnedOnly: Boolean = false,
    val hasNotesOnly: Boolean = false,
    val staleOnly: Boolean = false,
    val sort: TabSort = TabSort.IMPORTED_NEWEST,
    val descending: Boolean = true,
) {
    val hasActiveFilters: Boolean get() = search.isNotBlank() || browsers.isNotEmpty() || groups.isNotEmpty() ||
        sourceDevices.isNotEmpty() || sourceGroups.isNotEmpty() || tags.isNotEmpty() || pinnedOnly || hasNotesOnly || staleOnly ||
        statuses != setOf(TabStatus.ACTIVE)
}

@Immutable
data class SmartView(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val query: LibraryQuery,
    val iconKey: String = "filter",
    val colorKey: String = "indigo",
    val pinned: Boolean = false,
    val sortOrder: Int = 100,
)

@Immutable
data class DeckDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val iconKey: String = "deck",
    val colorKey: String = "violet",
    val tabCount: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Immutable
data class DeckBackup(
    val deck: DeckDefinition,
    val tabIds: List<String> = emptyList(),
)

@Immutable
data class DashboardStats(
    val total: Int = 0,
    val active: Int = 0,
    val archived: Int = 0,
    val snoozed: Int = 0,
    val trashed: Int = 0,
    val pinned: Int = 0,
    val inbox: Int = 0,
    val untitled: Int = 0,
    val stale: Int = 0,
    val duplicateCopies: Int = 0,
    val transferred: Int = 0,
)

@Immutable
data class FacetCount(val key: String, val count: Int)

@Immutable
data class ControlState(
    val stats: DashboardStats = DashboardStats(),
    val groupCounts: List<FacetCount> = emptyList(),
    val browserCounts: List<FacetCount> = emptyList(),
    val sourceDeviceCounts: List<FacetCount> = emptyList(),
    val sourceGroupCounts: List<FacetCount> = emptyList(),
    val recentTabs: List<TabItem> = emptyList(),
    val rules: List<RegexRule> = AppSnapshot.defaultRules(),
    val groups: List<GroupDefinition> = AppSnapshot.defaultGroups(),
    val smartViews: List<SmartView> = emptyList(),
    val decks: List<DeckDefinition> = emptyList(),
    val transferHistory: List<TransferEvent> = emptyList(),
    val importHistory: List<ImportSession> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val bridgeSession: BridgeSession = BridgeSession(),
)

@Immutable
data class AppSettings(
    val bridgeToken: String = newBridgeToken(),
    val bridgeScope: BridgeScope = BridgeScope.THIS_DEVICE,
    val bridgeSessionMinutes: Int = 20,
    val onboardingComplete: Boolean = false,
    val autoCategorizeImports: Boolean = true,
    val stripTrackingParameters: Boolean = true,
    val defaultDedupeMode: DedupeMode = DedupeMode.NORMALIZED_URL,
    val defaultKeepPolicy: KeepPolicy = KeepPolicy.RICHEST,
    val transferPacing: TransferPacing = TransferPacing.BALANCED,
    val transferBatchLimit: Int = 80,
    val viewDensity: ViewDensity = ViewDensity.COMFORTABLE,
    val libraryLayout: LibraryLayout = LibraryLayout.LIST,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentStyle: AccentStyle = AccentStyle.VIOLET,
    val reduceMotion: Boolean = false,
    val hapticFeedback: Boolean = true,
    val syncMissingPolicy: SyncMissingPolicy = SyncMissingPolicy.KEEP,
    val staleAfterDays: Int = 30,
    val showAdvancedControls: Boolean = false,
)

@Immutable
data class AppSnapshot(
    val tabs: List<TabItem> = emptyList(),
    val rules: List<RegexRule> = defaultRules(),
    val groups: List<GroupDefinition> = defaultGroups(),
    val smartViews: List<SmartView> = emptyList(),
    val deckBackups: List<DeckBackup> = emptyList(),
    val transferHistory: List<TransferEvent> = emptyList(),
    val importHistory: List<ImportSession> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val bridgeSession: BridgeSession = BridgeSession(),
) {
    companion object {
        fun defaultGroups(): List<GroupDefinition> = listOf(
            GroupDefinition(id = "system-inbox", name = "Inbox", colorKey = "slate", iconKey = "inbox", sortOrder = 0, isSystem = true),
            GroupDefinition(id = "default-development", name = "Development", colorKey = "indigo", iconKey = "code", sortOrder = 10),
            GroupDefinition(id = "default-reading", name = "Reading", colorKey = "teal", iconKey = "book", sortOrder = 20),
            GroupDefinition(id = "default-video", name = "Video", colorKey = "rose", iconKey = "video", sortOrder = 30),
            GroupDefinition(id = "default-shopping", name = "Shopping", colorKey = "amber", iconKey = "cart", sortOrder = 40),
        )

        fun defaultRules(): List<RegexRule> = listOf(
            RegexRule(
                id = "default-rule-development",
                name = "Development",
                pattern = "(?:github\\.com|gitlab\\.com|stackoverflow\\.com|developer\\.|docs\\.)",
                destinationGroup = "Development",
                priority = 10,
                addTags = setOf("work"),
            ),
            RegexRule(
                id = "default-rule-video",
                name = "Video",
                pattern = "(?:youtube\\.com|youtu\\.be|vimeo\\.com|twitch\\.tv)",
                destinationGroup = "Video",
                priority = 20,
                addTags = setOf("watch"),
            ),
            RegexRule(
                id = "default-rule-reading",
                name = "Reading",
                pattern = "(?:medium\\.com|substack\\.com|wikipedia\\.org|arxiv\\.org)",
                destinationGroup = "Reading",
                priority = 30,
                addTags = setOf("read"),
            ),
            RegexRule(
                id = "default-rule-shopping",
                name = "Shopping",
                pattern = "(?:amazon\\.|ebay\\.|etsy\\.|aliexpress\\.)",
                destinationGroup = "Shopping",
                priority = 40,
                addTags = setOf("shopping"),
            ),
        )
    }
}
