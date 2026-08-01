package com.tabdeck.app.data

import android.content.Context
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
import com.tabdeck.app.engine.MaintenancePolicy
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
import com.tabdeck.app.model.MaintenanceStatus
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.SmartView
import com.tabdeck.app.model.SyncMissingPolicy
import com.tabdeck.app.model.TagEditMode
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabStatus
import com.tabdeck.app.model.TransferEvent
import com.tabdeck.app.widget.updateAllTabDeckWidgets
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
    private val maintenanceMutex = Mutex()
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

    private data class OperationalState(
        val bridgeSession: BridgeSession,
        val maintenanceStatus: MaintenanceStatus,
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

    private val operationalState = combine(
        settingsStore.bridgeSession,
        settingsStore.maintenanceStatus,
    ) { bridgeSession, maintenanceStatus ->
        OperationalState(bridgeSession, maintenanceStatus)
    }

    val controlState: Flow<ControlState> = combine(
        metricsState,
        organizerState,
        historyState,
        settingsStore.settings,
        operationalState,
    ) { metrics, organizer, histories, settings, operational ->
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
            bridgeSession = operational.bridgeSession,
            maintenanceStatus = operational.maintenanceStatus,
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
                tabIds = decks.tabIdsForDeck(row.id),
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
        TabQueryBuilder.requireSupported(stableQuery)
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

    suspend fun tabsForQuery(query: LibraryQuery, limit: Int? = null): List<TabItem> {
        initialize()
        val settings = settingsStore.currentSettings()
        return tabs.queryTabs(
            TabQueryBuilder.select(sanitizeQuery(query), staleBefore(settings), limit),
        ).map { it.toModel() }
    }

    suspend fun tabsByIds(ids: Collection<String>): List<TabItem> {
        initialize()
        if (ids.isEmpty()) return emptyList()
        val result = mutableListOf<TabItem>()
        for (chunk in ids.asSequence().distinct().chunked(SQLITE_IN_CHUNK)) {
            result += tabs.findByIds(chunk).map { it.toModel() }
        }
        return result
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
        val now = System.currentTimeMillis()
        val sanitized = incoming.mapNotNull { sanitizeIncomingTab(it, now) }
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
            if (shouldCategorize && existing == null) RegexCategorizer.categorizeCompiled(base, compiledRules) else base
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
                    ?.let { browser -> snapshotDeviceName.singleLine().takeIf(String::isNotBlank)?.let { it to browser } }
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
                    received = incoming.size,
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
        val cutoff = MaintenancePolicy.pruneBeforeEpochMs(System.currentTimeMillis(), olderThanDays)
        val removed = tabs.pruneTrash(cutoff)
        if (removed > 0) refreshWidgets()
        return removed
    }

    suspend fun runMaintenance(retentionDays: Int): MaintenanceStatus = maintenanceMutex.withLock {
        initialize()
        val completedAt = System.currentTimeMillis()
        val (awakened, pruned) = database.withTransaction {
            val awakened = tabs.wakeDueTabs(completedAt)
            val pruned = tabs.pruneTrash(MaintenancePolicy.pruneBeforeEpochMs(completedAt, retentionDays))
            awakened to pruned
        }
        val status = MaintenanceStatus(
            lastRunAtEpochMs = completedAt,
            awakened = awakened,
            pruned = pruned,
            message = when {
                awakened == 0 && pruned == 0 -> "No maintenance changes were needed"
                else -> "Restored $awakened snoozed tabs and pruned $pruned Trash items"
            },
        )
        settingsStore.recordMaintenance(status)
        refreshWidgets()
        status
    }

    suspend fun recordMaintenanceFailure(message: String) {
        settingsStore.recordMaintenance(
            MaintenanceStatus(
                lastRunAtEpochMs = System.currentTimeMillis(),
                failed = true,
                message = message.trim().ifBlank { "Maintenance failed" }.take(180),
            ),
        )
        refreshWidgets()
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
            .map { it.singleLine() }
            .filter(String::isNotBlank)
            .toCollection(linkedSetOf())
        if (cleanTags.isEmpty() && mode != TagEditMode.REPLACE) return 0
        var changed = 0
        database.withTransaction {
            ids.asSequence().distinct().chunked(SQLITE_IN_CHUNK).forEach { chunk ->
                val existing = tabs.findByIds(chunk)
                val updated = existing.map { entity ->
                    val model = entity.toModel()
                    val nextTags = when (mode) {
                        TagEditMode.ADD -> (model.tags + cleanTags).toSet()
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
        val destination = ensureGroup(rule.destinationGroup)
        val clean = rule.copy(
            name = rule.name.singleLine().ifBlank { "Untitled rule" },
            pattern = rule.pattern.trim(),
            destinationGroup = destination,
            priority = rule.priority,
            addTags = rule.addTags.asSequence()
                .map { it.singleLine() }
                .filter(String::isNotBlank)
                .toCollection(linkedSetOf()),
        )
        rules.upsert(clean.toEntity())
    }

    suspend fun countRuleMatches(rule: RegexRule): Int {
        initialize()
        val validation = RegexCategorizer.validate(rule)
        require(validation.valid) { validation.error }
        return tabsForQuery(LibraryQuery(statuses = setOf(TabStatus.ACTIVE)))
            .count { RegexCategorizer.matches(it, rule) }
    }

    suspend fun deleteRule(id: String) {
        initialize()
        rules.delete(id)
    }

    suspend fun upsertGroup(group: GroupDefinition) {
        initialize()
        val state = currentState()
        val existing = state.groups.firstOrNull { it.id == group.id }
        val requestedName = group.name.singleLine().ifBlank { "Untitled" }
        val effectiveName = if (existing?.isSystem == true) existing.name else requestedName
        require(state.groups.none { it.id != group.id && it.name.equals(effectiveName, ignoreCase = true) }) {
            "A group named '$effectiveName' already exists"
        }
        val clean = group.copy(
            name = effectiveName,
            colorKey = group.colorKey.singleLine().ifBlank { "indigo" },
            iconKey = group.iconKey.singleLine().ifBlank { "folder" },
            sortOrder = group.sortOrder,
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

    suspend fun duplicateClusters(mode: DedupeMode): List<DuplicateCluster> {
        initialize()
        val settings = settingsStore.currentSettings()
        val rows = duplicateRows(mode, settings.stripTrackingParameters)
        return DedupeEngine.clusters(rows, mode, settings.stripTrackingParameters)
    }

    suspend fun deduplicate(mode: DedupeMode, keepPolicy: KeepPolicy, mergeMetadata: Boolean = true): Int {
        initialize()
        val settings = settingsStore.currentSettings()
        val activeDuplicates = duplicateRows(mode, settings.stripTrackingParameters)
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
            name = view.name.singleLine().ifBlank { "Untitled view" },
            iconKey = view.iconKey.singleLine().ifBlank { "filter" },
            colorKey = view.colorKey.singleLine().ifBlank { "indigo" },
            sortOrder = view.sortOrder,
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
        val distinctIds = tabIds.asSequence().distinct().toList()
        require(distinctIds.isNotEmpty()) { "A deck needs at least one tab" }
        val existingIds = tabsByIds(distinctIds).mapTo(hashSetOf()) { it.id }
        require(existingIds.isNotEmpty()) { "None of the selected tabs still exist" }
        val now = System.currentTimeMillis()
        val clean = deck.copy(
            name = deck.name.singleLine().ifBlank { "Untitled deck" },
            description = deck.description.cleanMultiline(),
            iconKey = deck.iconKey.singleLine().ifBlank { "deck" },
            colorKey = deck.colorKey.singleLine().ifBlank { "violet" },
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
        backup.groups.forEach { importedGroup ->
            runCatching {
                val state = currentState()
                val sameName = state.groups.firstOrNull { it.name.equals(importedGroup.name, ignoreCase = true) }
                val candidate = if (sameName != null) {
                    importedGroup.copy(id = sameName.id, name = sameName.name, isSystem = sameName.isSystem)
                } else importedGroup.copy(isSystem = false)
                upsertGroup(candidate)
            }
        }
        backup.rules.forEach { importedRule -> runCatching { upsertRule(importedRule) } }
        val importedTabs = importTabs(
            backup.tabs.map { it.copy(browser = if (it.browser == BrowserId.UNKNOWN) BrowserId.FILE_IMPORT else it.browser) },
            autoCategorize = false,
            sourceLabel = "TabDeck backup",
        )
        backup.smartViews.forEach { importedView ->
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
        backup.deckBackups.forEach { backupDeck ->
            val restoredIds = backupDeck.tabIds.mapNotNull(restoredIdByBackupId::get).distinct()
            if (restoredIds.isNotEmpty()) runCatching { saveDeck(backupDeck.deck, restoredIds) }
        }
        val now = System.currentTimeMillis()
        database.withTransaction {
            backup.transferHistory.forEach { event ->
                history.insertTransfer(
                    event.copy(
                        attempted = event.attempted.coerceAtLeast(0),
                        opened = event.opened.coerceAtLeast(0),
                        failed = event.failed.coerceAtLeast(0),
                        durationMs = event.durationMs.coerceAtLeast(0),
                        createdAtEpochMs = event.createdAtEpochMs.coerceIn(0, now + MAX_FUTURE_CLOCK_SKEW_MS),
                    ).toEntity(),
                )
            }
            backup.importHistory.forEach { session ->
                history.insertImport(
                    session.copy(
                        sourceLabel = session.sourceLabel.singleLine(),
                        received = session.received.coerceAtLeast(0),
                        accepted = session.accepted.coerceAtLeast(0),
                        rejected = session.rejected.coerceAtLeast(0),
                        deviceName = session.deviceName.singleLine(),
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

    private suspend fun duplicateRows(mode: DedupeMode, stripTracking: Boolean): List<TabItem> {
        if (mode == DedupeMode.NORMALIZED_URL && !stripTracking) {
            return tabsForQuery(LibraryQuery(statuses = setOf(TabStatus.ACTIVE)))
        }
        val keys = when (mode) {
            DedupeMode.EXACT_URL -> tabs.exactDuplicateKeys()
            DedupeMode.NORMALIZED_URL -> tabs.normalizedDuplicateKeys()
            DedupeMode.HOST_AND_PATH -> tabs.hostPathDuplicateKeys()
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
        updateAllTabDeckWidgets(appContext)
    }

    private suspend fun ensureGroup(name: String): String {
        val clean = name.singleLine().ifBlank { "Inbox" }
        val state = currentState()
        state.groups.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.name }
        groups.upsert(
            GroupDefinition(
                name = clean,
                sortOrder = (state.groups.maxOfOrNull { it.sortOrder } ?: 0) + 10,
            ).toEntity(),
        )
        return clean
    }

    private fun sanitizeIncomingTab(tab: TabItem, now: Long): TabItem? {
        val cleanUrl = UrlNormalizer.sanitizeWebUrl(tab.url) ?: return null
        val maxFuture = now + MAX_FUTURE_CLOCK_SKEW_MS
        return tab.copy(
            url = cleanUrl,
            title = tab.title.singleLine(MAX_TAB_TITLE_CHARS),
            sourceGroup = tab.sourceGroup.singleLine(MAX_GROUP_LABEL_CHARS),
            assignedGroup = tab.assignedGroup.singleLine(MAX_GROUP_LABEL_CHARS).ifBlank { "Inbox" },
            notes = tab.notes.cleanMultiline(MAX_NOTES_CHARS),
            tags = tab.tags.asSequence().boundedTags(),
            createdAtEpochMs = tab.createdAtEpochMs.coerceIn(0L, maxFuture),
            importedAtEpochMs = tab.importedAtEpochMs.coerceIn(0L, now),
            lastSeenAtEpochMs = tab.lastSeenAtEpochMs.coerceIn(0L, maxFuture),
            snoozedUntilEpochMs = tab.snoozedUntilEpochMs
                ?.coerceAtLeast(now)
                ?.takeIf { tab.status == TabStatus.SNOOZED },
            sourceDevice = tab.sourceDevice.singleLine(MAX_SOURCE_DEVICE_CHARS),
            sourceTabId = tab.sourceTabId.singleLine(MAX_SOURCE_TAB_ID_CHARS),
            transferCount = tab.transferCount.coerceAtLeast(0),
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
                    notes = listOf(previous.notes, item.notes)
                        .filter(String::isNotBlank)
                        .distinct()
                        .joinToString("\n\n")
                        .cleanMultiline(MAX_NOTES_CHARS),
                    tags = (previous.tags.asSequence() + item.tags.asSequence()).boundedTags(),
                    createdAtEpochMs = minOf(previous.createdAtEpochMs, item.createdAtEpochMs),
                    importedAtEpochMs = minOf(previous.importedAtEpochMs, item.importedAtEpochMs),
                    lastSeenAtEpochMs = maxOf(previous.lastSeenAtEpochMs, item.lastSeenAtEpochMs),
                )
            }
        }
        return result
    }

    private fun sanitizeQuery(query: LibraryQuery): LibraryQuery = query.copy(
        search = query.search.singleLine(),
        groups = query.groups.asSequence().map { it.singleLine() }.filter(String::isNotBlank).toSet(),
        sourceDevices = query.sourceDevices.asSequence().map { it.singleLine() }.filter(String::isNotBlank).toSet(),
        sourceGroups = query.sourceGroups.asSequence().map { it.singleLine() }.filter(String::isNotBlank).toSet(),
        tags = query.tags.asSequence().map { it.singleLine() }.filter(String::isNotBlank).toSet(),
    )

    private fun staleBefore(settings: AppSettings): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(settings.staleAfterDays.coerceAtLeast(1).toLong())

    private fun Set<String>.chunkedIds(): List<Set<String>> =
        asSequence().chunked(SQLITE_IN_CHUNK).map { it.toSet() }.toList()

    private fun Sequence<String>.boundedTags(): Set<String> {
        val result = linkedSetOf<String>()
        var remaining = MAX_TAG_TEXT_BUDGET
        for (raw in this) {
            val clean = raw.singleLine(MAX_TAG_CHARS)
            if (clean.isBlank() || clean in result) continue
            val storageCost = clean.length + 3
            if (storageCost > remaining) break
            result += clean
            remaining -= storageCost
        }
        return result
    }

    private fun String.singleLine(maxLength: Int = Int.MAX_VALUE): String =
        replace(Regex("""[\p{Cc}\p{Cf}]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(maxLength)

    private fun String.cleanMultiline(maxLength: Int = Int.MAX_VALUE): String =
        replace("\r\n", "\n")
            .replace('\r', '\n')
            .filter { it == '\n' || !it.isISOControl() }
            .trim()
            .take(maxLength)

    companion object {
        private const val SQLITE_IN_CHUNK = 800
        private const val RULE_BATCH_SIZE = 500
        private const val MAX_FUTURE_CLOCK_SKEW_MS = 5 * 60_000L
        private const val MAX_TAB_TITLE_CHARS = 4_096
        private const val MAX_GROUP_LABEL_CHARS = 1_024
        private const val MAX_NOTES_CHARS = 65_536
        private const val MAX_TAG_CHARS = 1_024
        private const val MAX_TAG_TEXT_BUDGET = 32_768
        private const val MAX_SOURCE_DEVICE_CHARS = 1_024
        private const val MAX_SOURCE_TAB_ID_CHARS = 2_048

        fun browserForPackage(packageName: String?): BrowserId =
            BrowserId.entries.firstOrNull { it.packageName == packageName } ?: BrowserId.UNKNOWN
    }
}
