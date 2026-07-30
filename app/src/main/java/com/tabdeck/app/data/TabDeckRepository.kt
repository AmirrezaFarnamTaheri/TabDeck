package com.tabdeck.app.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.tabdeck.app.data.local.DeckTabEntity
import com.tabdeck.app.data.local.TabDeckDatabase
import com.tabdeck.app.data.local.TabQueryBuilder
import com.tabdeck.app.data.local.toEntity
import com.tabdeck.app.data.local.toModel
import com.tabdeck.app.engine.DedupeEngine
import com.tabdeck.app.engine.RegexCategorizer
import com.tabdeck.app.engine.UrlNormalizer
import com.tabdeck.app.model.AppSettings
import com.tabdeck.app.model.AppSnapshot
import com.tabdeck.app.model.BridgeSession
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.ControlState
import com.tabdeck.app.model.DashboardStats
import com.tabdeck.app.model.DeckBackup
import com.tabdeck.app.model.DeckDefinition
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.DuplicateCluster
import com.tabdeck.app.model.FacetCount
import com.tabdeck.app.model.GroupDefinition
import com.tabdeck.app.model.ImportSession
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.LibraryQuery
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.SmartView
import com.tabdeck.app.model.SyncMissingPolicy
import com.tabdeck.app.model.TagEditMode
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabStatus
import com.tabdeck.app.model.TransferEvent
import com.tabdeck.app.widget.TabDeckWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

class TabDeckRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = TabDeckDatabase.get(context)
    private val tabs = database.tabDao()
    private val rules = database.ruleDao()
    private val groups = database.groupDao()
    private val history = database.historyDao()
    private val smartViews = database.smartViewDao()
    private val decks = database.deckDao()
    private val settingsStore = SettingsStore(context)
    private val initializationMutex = Mutex()
    @Volatile private var initialized = false

    val initialState: ControlState = ControlState()

    private data class MetricsState(
        val stats: DashboardStats,
        val groupCounts: List<FacetCount>,
        val browserCounts: List<FacetCount>,
        val sourceDeviceCounts: List<FacetCount>,
        val sourceGroupCounts: List<FacetCount>,
        val recentTabs: List<TabItem>,
    )

    private data class FacetState(
        val groupCounts: List<FacetCount>,
        val browserCounts: List<FacetCount>,
        val sourceDeviceCounts: List<FacetCount>,
        val sourceGroupCounts: List<FacetCount>,
    )

    private data class OrganizerState(
        val rules: List<RegexRule>,
        val groups: List<GroupDefinition>,
        val smartViews: List<SmartView>,
        val decks: List<DeckDefinition>,
    )

    private data class HistoryState(
        val transfers: List<TransferEvent>,
        val imports: List<ImportSession>,
    )

    private val facetState = combine(
        tabs.observeGroupCounts(),
        tabs.observeBrowserCounts(),
        tabs.observeSourceDeviceCounts(),
        tabs.observeSourceGroupCounts(),
    ) { groupRows, browserRows, deviceRows, sourceGroupRows ->
        FacetState(
            groupCounts = groupRows.map { it.toModel() },
            browserCounts = browserRows.map { it.toModel() },
            sourceDeviceCounts = deviceRows.map { it.toModel() },
            sourceGroupCounts = sourceGroupRows.map { it.toModel() },
        )
    }

    private val metricsState = settingsStore.settings.flatMapLatest { settings ->
        val staleBefore = staleBefore(settings)
        combine(
            tabs.observeStats(staleBefore),
            tabs.observeDuplicateCopies(),
            tabs.observeRecent(),
            facetState,
        ) { row, duplicateCopies, recentRows, facets ->
            MetricsState(
                stats = row.toModel(duplicateCopies),
                groupCounts = facets.groupCounts,
                browserCounts = facets.browserCounts,
                sourceDeviceCounts = facets.sourceDeviceCounts,
                sourceGroupCounts = facets.sourceGroupCounts,
                recentTabs = recentRows.map { it.toModel() },
            )
        }
    }

    private val organizerState = combine(
        rules.observeAll(),
        groups.observeAll(),
        smartViews.observeAll(),
        decks.observeSummaries(),
    ) { ruleRows, groupRows, viewRows, deckRows ->
        OrganizerState(
            rules = ruleRows.map { it.toModel() }.ifEmpty { AppSnapshot.defaultRules() },
            groups = groupRows.map { it.toModel() }.ifEmpty { AppSnapshot.defaultGroups() },
            smartViews = viewRows.map { it.toModel() },
            decks = deckRows.map { it.toModel() },
        )
    }

    private val historyState = combine(
        history.observeTransfers(),
        history.observeImports(),
    ) { transferRows, importRows ->
        HistoryState(
            transfers = transferRows.map { it.toModel() },
            imports = importRows.map { it.toModel() },
        )
    }

    val controlState: Flow<ControlState> = combine(
        metricsState,
        organizerState,
        historyState,
        settingsStore.settings,
        settingsStore.bridgeSession,
    ) { metrics, organizer, histories, settings, bridge ->
        ControlState(
            stats = metrics.stats,
            groupCounts = metrics.groupCounts,
            browserCounts = metrics.browserCounts,
            sourceDeviceCounts = metrics.sourceDeviceCounts,
            sourceGroupCounts = metrics.sourceGroupCounts,
            recentTabs = metrics.recentTabs,
            rules = organizer.rules,
            groups = organizer.groups,
            smartViews = organizer.smartViews,
            decks = organizer.decks,
            transferHistory = histories.transfers,
            importHistory = histories.imports,
            settings = settings,
            bridgeSession = bridge,
        )
    }

    suspend fun initialize() = initializationMutex.withLock {
        if (initialized) return
        val (alreadyMigrated, legacyJson) = settingsStore.legacyMigrationPayload()
        if (!alreadyMigrated) {
            val legacy = legacyJson?.let(SnapshotJsonCodec::decode)
            database.withTransaction {
                if (legacy != null && tabs.count() == 0) tabs.upsertAll(legacy.tabs.map { it.toEntity() })
                if (legacy != null && rules.count() == 0) rules.upsertAll(legacy.rules.map { it.toEntity() })
                if (legacy != null && groups.count() == 0) groups.upsertAll(legacy.groups.map { it.toEntity() })
                legacy?.transferHistory?.forEach { history.insertTransfer(it.toEntity()) }
            }
            legacy?.let { old ->
                settingsStore.updateSettings { current ->
                    current.copy(
                        bridgeToken = old.settings.bridgeToken,
                        onboardingComplete = old.settings.onboardingComplete,
                    )
                }
            }
            settingsStore.finishLegacyMigration()
        }
        settingsStore.ensureDefaults()
        database.withTransaction {
            if (rules.count() == 0) rules.upsertAll(AppSnapshot.defaultRules().map { it.toEntity() })
            if (groups.count() == 0) groups.upsertAll(AppSnapshot.defaultGroups().map { it.toEntity() })
            tabs.wakeDueTabs(System.currentTimeMillis())
        }
        initialized = true
    }

    suspend fun currentState(): ControlState {
        initialize()
        return controlState.first()
    }

    suspend fun current(): AppSnapshot {
        initialize()
        val settings = settingsStore.currentSettings()
        val deckBackups = decks.listSummaries().map { row ->
            DeckBackup(
                deck = row.toModel(),
                tabIds = decks.tabIdsForDeck(row.id).take(MAX_DECK_TABS),
            )
        }
        return AppSnapshot(
            tabs = tabs.listAll().map { it.toModel() },
            rules = rules.listAll().map { it.toModel() },
            groups = groups.listAll().map { it.toModel() },
            smartViews = smartViews.listAll().map { it.toModel() },
            deckBackups = deckBackups,
            transferHistory = history.listTransfers().map { it.toModel() },
            importHistory = history.listImports().map { it.toModel() },
            settings = settings,
            bridgeSession = settingsStore.currentBridgeSession(),
        )
    }

    fun pagedTabs(query: LibraryQuery): Flow<PagingData<TabItem>> {
        val stableQuery = sanitizeQuery(query)
        return settingsStore.settings.flatMapLatest { settings ->
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    initialLoadSize = 120,
                    prefetchDistance = 20,
                    enablePlaceholders = false,
                    maxSize = 720,
                ),
                pagingSourceFactory = {
                    tabs.pagingSource(TabQueryBuilder.select(stableQuery, staleBefore(settings)))
                },
            ).flow.map { pagingData -> pagingData.map { it.toModel() } }
        }
    }

    suspend fun countTabs(query: LibraryQuery): Int {
        initialize()
        val settings = settingsStore.currentSettings()
        return tabs.queryCount(TabQueryBuilder.count(sanitizeQuery(query), staleBefore(settings)))
    }

    suspend fun tabsForQuery(query: LibraryQuery, limit: Int = MAX_IMPORT_TABS): List<TabItem> {
        initialize()
        val settings = settingsStore.currentSettings()
        return tabs.queryTabs(
            TabQueryBuilder.select(sanitizeQuery(query), staleBefore(settings), limit.coerceIn(1, MAX_IMPORT_TABS)),
        ).map { it.toModel() }
    }

    suspend fun tabsByIds(ids: Collection<String>): List<TabItem> {
        initialize()
        if (ids.isEmpty()) return emptyList()
        return ids.asSequence().distinct().chunked(SQLITE_IN_CHUNK)
            .flatMap { tabs.findByIds(it).asSequence() }
            .map { it.toModel() }
            .toList()
    }

    suspend fun tabsForDeck(deckId: String): List<TabItem> {
        initialize()
        return decks.tabsForDeck(deckId).map { it.toModel() }
    }

    suspend fun importTabs(
        incoming: List<TabItem>,
        autoCategorize: Boolean? = null,
        sourceLabel: String = incoming.firstOrNull()?.browser?.displayName.orEmpty(),
        deviceName: String = incoming.firstOrNull()?.sourceDevice.orEmpty(),
        completeSnapshot: Boolean = false,
        snapshotBrowser: BrowserId? = null,
        snapshotDeviceName: String = deviceName,
    ): Int {
        initialize()
        val bounded = incoming.take(MAX_IMPORT_TABS)
        val now = System.currentTimeMillis()
        val sanitized = bounded.mapNotNull { sanitizeIncomingTab(it, now) }
        val valid = coalesceSourceIdentityDuplicates(sanitized)
        val appSettings = settingsStore.currentSettings()
        val shouldCategorize = autoCategorize ?: appSettings.autoCategorizeImports
        val ruleSet = if (shouldCategorize) rules.listAll().map { it.toModel() } else emptyList()
        val compiledRules = RegexCategorizer.compileEnabled(ruleSet)

        val existingByIdentity = valid.asSequence()
            .filter { it.sourceDevice.isNotBlank() && it.sourceTabId.isNotBlank() }
            .groupBy { it.sourceDevice to it.browser }
            .flatMap { (identity, deviceTabs) ->
                deviceTabs.map { it.sourceTabId }.distinct().chunked(SQLITE_IN_CHUNK).flatMap { sourceIds ->
                    tabs.findBySourceIds(identity.first, identity.second.name, sourceIds)
                }
            }
            .associateBy { Triple(it.sourceDevice, it.browser, it.sourceTabId) }

        val prepared = valid.map { incomingTab ->
            val existing = existingByIdentity[
                Triple(incomingTab.sourceDevice, incomingTab.browser.name, incomingTab.sourceTabId)
            ]?.toModel()
            val base = if (existing == null) {
                incomingTab.copy(importedAtEpochMs = incomingTab.importedAtEpochMs.coerceAtMost(now), lastSeenAtEpochMs = now)
            } else {
                incomingTab.copy(
                    id = existing.id,
                    assignedGroup = existing.assignedGroup,
                    pinned = existing.pinned || incomingTab.pinned,
                    notes = existing.notes.ifBlank { incomingTab.notes },
                    tags = existing.tags + incomingTab.tags,
                    status = if (existing.status == TabStatus.TRASHED) TabStatus.ACTIVE else existing.status,
                    importedAtEpochMs = existing.importedAtEpochMs,
                    lastSeenAtEpochMs = now,
                    lastTransferredAtEpochMs = existing.lastTransferredAtEpochMs,
                    transferCount = existing.transferCount,
                )
            }
            if (shouldCategorize && existing == null) RegexCategorizer.categorize(base, compiledRules) else base
        }

        database.withTransaction {
            if (prepared.isNotEmpty()) tabs.upsertAll(prepared.map { it.toEntity() })
            if (completeSnapshot && appSettings.syncMissingPolicy == SyncMissingPolicy.ARCHIVE) {
                val snapshotTabsByIdentity = prepared.asSequence()
                    .filter { it.sourceDevice.isNotBlank() && it.sourceTabId.isNotBlank() && it.browser.isLaunchTarget }
                    .groupBy { it.sourceDevice to it.browser }
                    .toMutableMap()
                val rootIdentity = snapshotBrowser
                    ?.takeIf { it.isLaunchTarget }
                    ?.let { browser -> snapshotDeviceName.singleLine(MAX_SOURCE_DEVICE_LENGTH).takeIf(String::isNotBlank)?.let { it to browser } }
                // A complete, empty snapshot is meaningful: it archives every previously seen tab for that exact source identity.
                if (rootIdentity != null) snapshotTabsByIdentity.putIfAbsent(rootIdentity, emptyList())

                snapshotTabsByIdentity.forEach { (identity, sourceTabs) ->
                    val presentIds = sourceTabs.map { it.sourceTabId }.distinct()
                    if (presentIds.isEmpty()) {
                        tabs.archiveAllSourceTabs(identity.first, identity.second.name)
                    } else if (presentIds.size <= SQLITE_IN_CHUNK) {
                        tabs.archiveMissingSourceTabs(identity.first, identity.second.name, presentIds)
                    } else {
                        // SQLite host-parameter limits make a giant NOT IN unsafe; compare IDs in memory and update in chunks.
                        val present = presentIds.toHashSet()
                        val missing = tabs.sourceIdsForIdentity(identity.first, identity.second.name).filterNot(present::contains)
                        missing.chunked(SQLITE_IN_CHUNK).forEach { sourceIds ->
                            val rows = tabs.findBySourceIds(identity.first, identity.second.name, sourceIds)
                            if (rows.isNotEmpty()) tabs.setStatus(rows.map { it.id }.toSet(), TabStatus.ARCHIVED.name)
                        }
                    }
                }
            }
            history.insertImport(
                ImportSession(
                    source = prepared.firstOrNull()?.browser ?: snapshotBrowser ?: incoming.firstOrNull()?.browser ?: BrowserId.UNKNOWN,
                    sourceLabel = sourceLabel.ifBlank { prepared.firstOrNull()?.browser?.displayName ?: "Import" },
                    received = incoming.size.coerceAtMost(MAX_IMPORT_TABS),
                    accepted = prepared.size,
                    rejected = (incoming.size - prepared.size).coerceAtLeast(0),
                    deviceName = deviceName,
                ).toEntity(),
            )
        }
        refreshWidgets()
        return prepared.size
    }

    suspend fun assignGroup(ids: Set<String>, group: String) {
        if (ids.isEmpty()) return
        initialize()
        val canonicalGroup = ensureGroup(group)
        database.withTransaction { ids.chunkedIds().forEach { tabs.assignGroup(it, canonicalGroup) } }
        refreshWidgets()
    }

    suspend fun setStatus(ids: Set<String>, status: TabStatus) {
        if (ids.isEmpty()) return
        initialize()
        database.withTransaction { ids.chunkedIds().forEach { tabs.setStatus(it, status.name) } }
        refreshWidgets()
    }

    suspend fun snooze(ids: Set<String>, untilEpochMs: Long) {
        if (ids.isEmpty()) return
        initialize()
        val wakeAt = untilEpochMs.coerceAtLeast(System.currentTimeMillis())
        database.withTransaction { ids.chunkedIds().forEach { tabs.snooze(it, TabStatus.SNOOZED.name, wakeAt) } }
        refreshWidgets()
    }

    suspend fun wakeDueTabs(now: Long = System.currentTimeMillis()): Int {
        initialize()
        val awakened = tabs.wakeDueTabs(now)
        if (awakened > 0) refreshWidgets()
        return awakened
    }

    suspend fun pruneTrash(olderThanDays: Int): Int {
        initialize()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(olderThanDays.coerceIn(1, 3650).toLong())
        val removed = tabs.pruneTrash(cutoff)
        if (removed > 0) refreshWidgets()
        return removed
    }

    suspend fun deletePermanently(ids: Set<String>) {
        if (ids.isEmpty()) return
        initialize()
        database.withTransaction { ids.chunkedIds().forEach { tabs.deleteByIds(it) } }
        refreshWidgets()
    }

    suspend fun emptyTrash() {
        initialize()
        tabs.emptyTrash()
        refreshWidgets()
    }

    suspend fun updateTab(tab: TabItem) {
        initialize()
        val clean = sanitizeIncomingTab(tab, System.currentTimeMillis())
            ?: error("This tab no longer contains a valid HTTP(S) URL")
        val canonicalGroup = ensureGroup(clean.assignedGroup)
        tabs.upsert(clean.copy(assignedGroup = canonicalGroup).toEntity())
        refreshWidgets()
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        initialize()
        tabs.setPinned(id, pinned)
        refreshWidgets()
    }

    suspend fun setPinned(ids: Set<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        initialize()
        database.withTransaction { ids.chunkedIds().forEach { tabs.setPinned(it, pinned) } }
        refreshWidgets()
    }

    suspend fun editTags(ids: Set<String>, requestedTags: Set<String>, mode: TagEditMode): Int {
        if (ids.isEmpty()) return 0
        initialize()
        val cleanTags = requestedTags.asSequence()
            .map { it.singleLine(MAX_TAG_LENGTH) }
            .filter(String::isNotBlank)
            .take(MAX_TAGS)
            .toCollection(linkedSetOf())
        if (cleanTags.isEmpty() && mode != TagEditMode.REPLACE) return 0
        var changed = 0
        database.withTransaction {
            ids.asSequence().distinct().chunked(SQLITE_IN_CHUNK).forEach { chunk ->
                val existing = tabs.findByIds(chunk)
                val updated = existing.map { entity ->
                    val model = entity.toModel()
                    val nextTags = when (mode) {
                        TagEditMode.ADD -> (model.tags + cleanTags).take(MAX_TAGS).toSet()
                        TagEditMode.REMOVE -> model.tags - cleanTags
                        TagEditMode.REPLACE -> cleanTags
                    }
                    if (nextTags != model.tags) changed++
                    model.copy(tags = nextTags).toEntity()
                }
                if (updated.isNotEmpty()) tabs.upsertAll(updated)
            }
        }
        if (changed > 0) refreshWidgets()
        return changed
    }

    suspend fun addTags(ids: Set<String>, requestedTags: Set<String>): Int =
        editTags(ids, requestedTags, TagEditMode.ADD)

    suspend fun applyRules(ids: Set<String>? = null): Int {
        initialize()
        val ruleSet = rules.listAll().map { it.toModel() }
        var changed = 0
        if (!ids.isNullOrEmpty()) {
            val candidates = tabsByIds(ids).filter { it.status == TabStatus.ACTIVE }
            val categorized = RegexCategorizer.categorizeAll(candidates, ruleSet)
            changed = categorized.zip(candidates).count { (after, before) -> after != before }
            if (categorized.isNotEmpty()) tabs.upsertAll(categorized.map { it.toEntity() })
        } else {
            var offset = 0
            while (true) {
                val page = tabs.activePage(RULE_BATCH_SIZE, offset).map { it.toModel() }
                if (page.isEmpty()) break
                val categorized = RegexCategorizer.categorizeAll(page, ruleSet)
                changed += categorized.zip(page).count { (after, before) -> after != before }
                tabs.upsertAll(categorized.map { it.toEntity() })
                offset += page.size
            }
        }
        if (changed > 0) refreshWidgets()
        return changed
    }

    suspend fun upsertRule(rule: RegexRule) {
        initialize()
        val validation = RegexCategorizer.validate(rule)
        require(validation.valid) { validation.error }
        val state = currentState()
        require(state.rules.any { it.id == rule.id } || state.rules.size < MAX_RULES) { "A maximum of $MAX_RULES rules is supported" }
        val destination = ensureGroup(rule.destinationGroup)
        val clean = rule.copy(
            name = rule.name.singleLine(MAX_RULE_NAME_LENGTH).ifBlank { "Untitled rule" },
            pattern = rule.pattern.trim().take(MAX_RULE_PATTERN_LENGTH),
            destinationGroup = destination,
            priority = rule.priority.coerceIn(0, 99_999),
            addTags = rule.addTags.asSequence()
                .map { it.singleLine(MAX_TAG_LENGTH) }
                .filter(String::isNotBlank)
                .take(MAX_TAGS)
                .toCollection(linkedSetOf()),
        )
        rules.upsert(clean.toEntity())
    }

    suspend fun countRuleMatches(rule: RegexRule): Int {
        initialize()
        val validation = RegexCategorizer.validate(rule)
        require(validation.valid) { validation.error }
        return tabsForQuery(
            LibraryQuery(statuses = setOf(TabStatus.ACTIVE)),
            MAX_IMPORT_TABS,
        ).count { RegexCategorizer.matches(it, rule) }
    }

    suspend fun deleteRule(id: String) {
        initialize()
        rules.delete(id)
    }

    suspend fun upsertGroup(group: GroupDefinition) {
        initialize()
        val state = currentState()
        val existing = state.groups.firstOrNull { it.id == group.id }
        require(existing != null || state.groups.size < MAX_GROUPS) { "A maximum of $MAX_GROUPS groups is supported" }
        val requestedName = group.name.singleLine(MAX_GROUP_LENGTH).ifBlank { "Untitled" }
        val effectiveName = if (existing?.isSystem == true) existing.name else requestedName
        require(state.groups.none { it.id != group.id && it.name.equals(effectiveName, ignoreCase = true) }) {
            "A group named '$effectiveName' already exists"
        }
        val clean = group.copy(
            name = effectiveName,
            colorKey = group.colorKey.singleLine(32).ifBlank { "indigo" },
            iconKey = group.iconKey.singleLine(32).ifBlank { "folder" },
            sortOrder = group.sortOrder.coerceIn(0, 100_000),
            isSystem = existing?.isSystem ?: false,
        )
        database.withTransaction {
            if (existing != null && existing.name != clean.name) {
                tabs.renameAssignedGroup(existing.name, clean.name)
                rules.renameDestinationGroup(existing.name, clean.name)
            }
            groups.upsert(clean.toEntity())
        }
        refreshWidgets()
    }

    suspend fun deleteGroup(id: String, fallback: String = "Inbox") {
        initialize()
        val state = currentState()
        val group = state.groups.firstOrNull { it.id == id } ?: return
        if (group.isSystem) return
        val canonicalFallback = ensureGroup(fallback)
        database.withTransaction {
            tabs.renameAssignedGroup(group.name, canonicalFallback)
            rules.renameDestinationGroup(group.name, canonicalFallback)
            groups.delete(id)
        }
        refreshWidgets()
    }

    suspend fun duplicateClusters(mode: DedupeMode, limitClusters: Int = 250): List<DuplicateCluster> {
        initialize()
        val settings = settingsStore.currentSettings()
        val rows = duplicateRows(mode, limitClusters.coerceIn(1, MAX_DUPLICATE_CLUSTERS), settings.stripTrackingParameters)
        return DedupeEngine.clusters(rows, mode, settings.stripTrackingParameters)
    }

    suspend fun deduplicate(mode: DedupeMode, keepPolicy: KeepPolicy, mergeMetadata: Boolean = true): Int {
        initialize()
        val settings = settingsStore.currentSettings()
        val activeDuplicates = duplicateRows(mode, MAX_DUPLICATE_CLUSTERS, settings.stripTrackingParameters)
        val plan = DedupeEngine.plan(
            activeDuplicates,
            mode,
            keepPolicy,
            mergeMetadata,
            stripTrackingParameters = settings.stripTrackingParameters,
        )
        database.withTransaction {
            if (plan.survivors.isNotEmpty()) tabs.upsertAll(plan.survivors.map { it.toEntity() })
            if (plan.duplicateIds.isNotEmpty()) plan.duplicateIds.chunkedIds().forEach { tabs.setStatus(it, TabStatus.TRASHED.name) }
        }
        refreshWidgets()
        return plan.duplicateIds.size
    }

    suspend fun upsertSmartView(view: SmartView) {
        initialize()
        val clean = view.copy(
            name = view.name.singleLine(MAX_VIEW_NAME_LENGTH).ifBlank { "Untitled view" },
            iconKey = view.iconKey.singleLine(32).ifBlank { "filter" },
            colorKey = view.colorKey.singleLine(32).ifBlank { "indigo" },
            sortOrder = view.sortOrder.coerceIn(0, 100_000),
            query = sanitizeQuery(view.query),
        )
        smartViews.upsert(clean.toEntity())
    }

    suspend fun deleteSmartView(id: String) {
        initialize()
        smartViews.delete(id)
    }

    suspend fun saveDeck(deck: DeckDefinition, tabIds: Collection<String>) {
        initialize()
        val distinctIds = tabIds.asSequence().distinct().take(MAX_DECK_TABS).toList()
        require(distinctIds.isNotEmpty()) { "A deck needs at least one tab" }
        val existingIds = tabsByIds(distinctIds).mapTo(hashSetOf()) { it.id }
        require(existingIds.isNotEmpty()) { "None of the selected tabs still exist" }
        val now = System.currentTimeMillis()
        val clean = deck.copy(
            name = deck.name.singleLine(MAX_DECK_NAME_LENGTH).ifBlank { "Untitled deck" },
            description = deck.description.cleanMultiline(MAX_DECK_DESCRIPTION_LENGTH),
            iconKey = deck.iconKey.singleLine(32).ifBlank { "deck" },
            colorKey = deck.colorKey.singleLine(32).ifBlank { "violet" },
            createdAtEpochMs = deck.createdAtEpochMs.coerceIn(0, now),
            updatedAtEpochMs = now,
        )
        database.withTransaction {
            decks.upsertDeck(clean.toEntity())
            decks.clearDeckTabs(clean.id)
            decks.upsertDeckTabs(
                distinctIds.filter(existingIds::contains).mapIndexed { index, id ->
                    DeckTabEntity(clean.id, id, index, now)
                },
            )
        }
    }

    suspend fun deleteDeck(id: String) {
        initialize()
        decks.deleteDeck(id)
    }

    suspend fun recordTransfer(event: TransferEvent, successfullyOpenedIds: Set<String> = emptySet()) {
        initialize()
        database.withTransaction {
            history.insertTransfer(event.toEntity())
            if (successfullyOpenedIds.isNotEmpty()) successfullyOpenedIds.chunkedIds().forEach {
                tabs.markTransferred(it, event.createdAtEpochMs)
            }
        }
        refreshWidgets()
    }

    suspend fun mergeBackup(backup: AppSnapshot): Int {
        initialize()
        backup.groups.take(MAX_GROUPS).forEach { importedGroup ->
            runCatching {
                val state = currentState()
                val sameName = state.groups.firstOrNull { it.name.equals(importedGroup.name, ignoreCase = true) }
                val candidate = if (sameName != null) {
                    importedGroup.copy(id = sameName.id, name = sameName.name, isSystem = sameName.isSystem)
                } else importedGroup.copy(isSystem = false)
                upsertGroup(candidate)
            }
        }
        backup.rules.take(MAX_RULES).forEach { importedRule -> runCatching { upsertRule(importedRule) } }
        val importedTabs = importTabs(
            backup.tabs.map { it.copy(browser = if (it.browser == BrowserId.UNKNOWN) BrowserId.FILE_IMPORT else it.browser) },
            autoCategorize = false,
            sourceLabel = "TabDeck backup",
        )
        backup.smartViews.take(MAX_SMART_VIEWS).forEach { importedView ->
            runCatching { upsertSmartView(importedView) }
        }
        val restoredIdByBackupId = backup.tabs.associate { it.id to it.id }.toMutableMap()
        backup.tabs.asSequence()
            .filter { it.sourceDevice.isNotBlank() && it.sourceTabId.isNotBlank() }
            .groupBy { it.sourceDevice to it.browser }
            .forEach { (identity, sourceTabs) ->
                val backupBySourceId = sourceTabs.associateBy { it.sourceTabId }
                sourceTabs.map { it.sourceTabId }.distinct().chunked(SQLITE_IN_CHUNK).forEach { sourceIds ->
                    tabs.findBySourceIds(identity.first, identity.second.name, sourceIds).forEach { stored ->
                        backupBySourceId[stored.sourceTabId]?.let { original -> restoredIdByBackupId[original.id] = stored.id }
                    }
                }
            }
        backup.deckBackups.take(MAX_DECKS).forEach { backupDeck ->
            val restoredIds = backupDeck.tabIds.mapNotNull(restoredIdByBackupId::get).distinct()
            if (restoredIds.isNotEmpty()) runCatching { saveDeck(backupDeck.deck, restoredIds) }
        }
        val now = System.currentTimeMillis()
        database.withTransaction {
            backup.transferHistory.take(MAX_HISTORY_ITEMS).forEach { event ->
                history.insertTransfer(
                    event.copy(
                        attempted = event.attempted.coerceIn(0, MAX_IMPORT_TABS),
                        opened = event.opened.coerceIn(0, MAX_IMPORT_TABS),
                        failed = event.failed.coerceIn(0, MAX_IMPORT_TABS),
                        durationMs = event.durationMs.coerceIn(0, MAX_HISTORY_DURATION_MS),
                        createdAtEpochMs = event.createdAtEpochMs.coerceIn(0, now + MAX_FUTURE_CLOCK_SKEW_MS),
                    ).toEntity(),
                )
            }
            backup.importHistory.take(MAX_HISTORY_ITEMS).forEach { session ->
                history.insertImport(
                    session.copy(
                        sourceLabel = session.sourceLabel.singleLine(120),
                        received = session.received.coerceIn(0, MAX_IMPORT_TABS),
                        accepted = session.accepted.coerceIn(0, MAX_IMPORT_TABS),
                        rejected = session.rejected.coerceIn(0, MAX_IMPORT_TABS),
                        deviceName = session.deviceName.singleLine(MAX_SOURCE_DEVICE_LENGTH),
                        createdAtEpochMs = session.createdAtEpochMs.coerceIn(0, now + MAX_FUTURE_CLOCK_SKEW_MS),
                    ).toEntity(),
                )
            }
        }
        val currentSettings = settingsStore.currentSettings()
        settingsStore.updateSettings {
            backup.settings.copy(
                bridgeToken = currentSettings.bridgeToken,
                onboardingComplete = currentSettings.onboardingComplete,
            )
        }
        refreshWidgets()
        return importedTabs
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.updateSettings(transform)
        refreshWidgets()
    }

    suspend fun regenerateBridgeToken() {
        settingsStore.regenerateBridgeToken()
        refreshWidgets()
    }

    suspend fun setBridgeSession(session: BridgeSession) {
        settingsStore.setBridgeSession(session)
        refreshWidgets()
    }

    suspend fun recordBridgeRequest(accepted: Boolean) {
        settingsStore.recordBridgeRequest(accepted)
    }

    suspend fun reset() {
        initialize()
        database.withTransaction {
            decks.deleteAllDecks()
            smartViews.deleteAll()
            tabs.deleteAll()
            rules.deleteAll()
            groups.deleteAll()
            history.deleteTransfers()
            history.deleteImports()
            rules.upsertAll(AppSnapshot.defaultRules().map { it.toEntity() })
            groups.upsertAll(AppSnapshot.defaultGroups().map { it.toEntity() })
        }
        settingsStore.reset()
        refreshWidgets()
    }

    private suspend fun duplicateRows(mode: DedupeMode, limitClusters: Int, stripTracking: Boolean): List<TabItem> {
        if (mode == DedupeMode.NORMALIZED_URL && !stripTracking) {
            return tabsForQuery(LibraryQuery(statuses = setOf(TabStatus.ACTIVE)), MAX_IMPORT_TABS)
        }
        val keys = when (mode) {
            DedupeMode.EXACT_URL -> tabs.exactDuplicateKeys(limitClusters)
            DedupeMode.NORMALIZED_URL -> tabs.normalizedDuplicateKeys(limitClusters)
            DedupeMode.HOST_AND_PATH -> tabs.hostPathDuplicateKeys(limitClusters)
        }.map { it.key }
        if (keys.isEmpty()) return emptyList()
        return keys.chunked(SQLITE_IN_CHUNK).flatMap { chunk ->
            when (mode) {
                DedupeMode.EXACT_URL -> tabs.tabsForExactKeys(chunk)
                DedupeMode.NORMALIZED_URL -> tabs.tabsForNormalizedKeys(chunk)
                DedupeMode.HOST_AND_PATH -> tabs.tabsForHostPathKeys(chunk)
            }
        }.map { it.toModel() }
    }

    private suspend fun refreshWidgets() {
        runCatching { TabDeckWidget().updateAll(appContext) }
    }

    private suspend fun ensureGroup(name: String): String {
        val clean = name.singleLine(MAX_GROUP_LENGTH).ifBlank { "Inbox" }
        val state = currentState()
        state.groups.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.name }
        require(state.groups.size < MAX_GROUPS) { "A maximum of $MAX_GROUPS groups is supported" }
        groups.upsert(
            GroupDefinition(
                name = clean,
                sortOrder = ((state.groups.maxOfOrNull { it.sortOrder } ?: 0) + 10).coerceAtMost(100_000),
            ).toEntity(),
        )
        return clean
    }

    private fun sanitizeIncomingTab(tab: TabItem, now: Long): TabItem? {
        val cleanUrl = UrlNormalizer.sanitizeWebUrl(tab.url) ?: return null
        val maxFuture = now + MAX_FUTURE_CLOCK_SKEW_MS
        return tab.copy(
            url = cleanUrl,
            title = tab.title.singleLine(MAX_TITLE_LENGTH),
            sourceGroup = tab.sourceGroup.singleLine(MAX_SOURCE_GROUP_LENGTH),
            assignedGroup = tab.assignedGroup.singleLine(MAX_GROUP_LENGTH).ifBlank { "Inbox" },
            notes = tab.notes.cleanMultiline(MAX_NOTES_LENGTH),
            tags = tab.tags.asSequence()
                .map { it.singleLine(MAX_TAG_LENGTH) }
                .filter(String::isNotBlank)
                .take(MAX_TAGS)
                .toCollection(linkedSetOf()),
            createdAtEpochMs = tab.createdAtEpochMs.coerceIn(0L, maxFuture),
            importedAtEpochMs = tab.importedAtEpochMs.coerceIn(0L, now),
            lastSeenAtEpochMs = tab.lastSeenAtEpochMs.coerceIn(0L, maxFuture),
            snoozedUntilEpochMs = tab.snoozedUntilEpochMs
                ?.coerceIn(now, MAX_SNOOZE_EPOCH_MS)
                ?.takeIf { tab.status == TabStatus.SNOOZED },
            sourceDevice = tab.sourceDevice.singleLine(MAX_SOURCE_DEVICE_LENGTH),
            sourceTabId = tab.sourceTabId.singleLine(MAX_SOURCE_TAB_ID_LENGTH),
            transferCount = tab.transferCount.coerceIn(0, MAX_TRANSFER_COUNT),
        )
    }

    private fun coalesceSourceIdentityDuplicates(items: List<TabItem>): List<TabItem> {
        val result = ArrayList<TabItem>(items.size)
        val identityIndex = linkedMapOf<Triple<String, BrowserId, String>, Int>()
        for (item in items) {
            if (item.sourceDevice.isBlank() || item.sourceTabId.isBlank()) {
                result += item
                continue
            }
            val key = Triple(item.sourceDevice, item.browser, item.sourceTabId)
            val previousIndex = identityIndex[key]
            if (previousIndex == null) {
                identityIndex[key] = result.size
                result += item
            } else {
                val previous = result[previousIndex]
                result[previousIndex] = item.copy(
                    id = previous.id,
                    pinned = previous.pinned || item.pinned,
                    notes = listOf(previous.notes, item.notes).filter(String::isNotBlank).distinct().joinToString("\n\n"),
                    tags = previous.tags + item.tags,
                    createdAtEpochMs = minOf(previous.createdAtEpochMs, item.createdAtEpochMs),
                    importedAtEpochMs = minOf(previous.importedAtEpochMs, item.importedAtEpochMs),
                    lastSeenAtEpochMs = maxOf(previous.lastSeenAtEpochMs, item.lastSeenAtEpochMs),
                )
            }
        }
        return result
    }

    private fun sanitizeQuery(query: LibraryQuery): LibraryQuery = query.copy(
        search = query.search.singleLine(MAX_SEARCH_LENGTH),
        groups = query.groups.asSequence().map { it.singleLine(MAX_GROUP_LENGTH) }.filter(String::isNotBlank).take(128).toSet(),
        sourceDevices = query.sourceDevices.asSequence().map { it.singleLine(MAX_SOURCE_DEVICE_LENGTH) }.filter(String::isNotBlank).take(128).toSet(),
        sourceGroups = query.sourceGroups.asSequence().map { it.singleLine(MAX_SOURCE_GROUP_LENGTH) }.filter(String::isNotBlank).take(128).toSet(),
        tags = query.tags.asSequence().map { it.singleLine(MAX_TAG_LENGTH) }.filter(String::isNotBlank).take(32).toSet(),
    )

    private fun staleBefore(settings: AppSettings): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(settings.staleAfterDays.coerceIn(1, 3650).toLong())

    private fun Set<String>.chunkedIds(): List<Set<String>> =
        asSequence().chunked(SQLITE_IN_CHUNK).map { it.toSet() }.toList()

    private fun String.singleLine(maxLength: Int): String =
        replace(Regex("""[\p{Cc}\p{Cf}]+"""), " ").replace(Regex("""\s+"""), " ").trim().take(maxLength)

    private fun String.cleanMultiline(maxLength: Int): String =
        replace("\r\n", "\n")
            .replace('\r', '\n')
            .filter { it == '\n' || !it.isISOControl() }
            .trim()
            .take(maxLength)

    companion object {
        private const val MAX_IMPORT_TABS = 25_000
        private const val SQLITE_IN_CHUNK = 800
        private const val RULE_BATCH_SIZE = 500
        private const val MAX_DUPLICATE_CLUSTERS = 25_000
        private const val MAX_TITLE_LENGTH = 500
        private const val MAX_SOURCE_GROUP_LENGTH = 120
        private const val MAX_GROUP_LENGTH = 80
        private const val MAX_NOTES_LENGTH = 10_000
        private const val MAX_TAGS = 32
        private const val MAX_GROUPS = 500
        private const val MAX_RULES = 250
        private const val MAX_RULE_NAME_LENGTH = 80
        private const val MAX_RULE_PATTERN_LENGTH = 512
        private const val MAX_TAG_LENGTH = 40
        private const val MAX_SOURCE_DEVICE_LENGTH = 120
        private const val MAX_SOURCE_TAB_ID_LENGTH = 160
        private const val MAX_TRANSFER_COUNT = 1_000_000
        private const val MAX_HISTORY_ITEMS = 100
        private const val MAX_HISTORY_DURATION_MS = 24 * 60 * 60_000L
        private const val MAX_FUTURE_CLOCK_SKEW_MS = 5 * 60_000L
        private const val MAX_SNOOZE_EPOCH_MS = 4_102_444_800_000L
        private const val MAX_VIEW_NAME_LENGTH = 80
        private const val MAX_DECK_NAME_LENGTH = 100
        private const val MAX_DECK_DESCRIPTION_LENGTH = 2_000
        private const val MAX_DECK_TABS = 5_000
        private const val MAX_SMART_VIEWS = 250
        private const val MAX_DECKS = 250
        private const val MAX_SEARCH_LENGTH = 500

        fun browserForPackage(packageName: String?): BrowserId =
            BrowserId.entries.firstOrNull { it.packageName == packageName } ?: BrowserId.UNKNOWN
    }
}
