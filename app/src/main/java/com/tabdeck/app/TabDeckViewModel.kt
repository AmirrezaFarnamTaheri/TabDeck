package com.tabdeck.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tabdeck.app.bridge.BridgeNetwork
import com.tabdeck.app.bridge.LocalBridgeService
import com.tabdeck.app.data.SnapshotJsonCodec
import com.tabdeck.app.data.TabExportCodec
import com.tabdeck.app.data.TabExportFormat
import com.tabdeck.app.data.TabDeckRepository
import com.tabdeck.app.engine.RegexCategorizer
import com.tabdeck.app.engine.UrlExtractor
import com.tabdeck.app.model.AccentStyle
import com.tabdeck.app.model.AppSettings
import com.tabdeck.app.model.BridgeScope
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.ControlState
import com.tabdeck.app.model.DeckDefinition
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.DuplicateCluster
import com.tabdeck.app.model.GroupDefinition
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.LibraryLayout
import com.tabdeck.app.model.LibraryQuery
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.SmartView
import com.tabdeck.app.model.SyncMissingPolicy
import com.tabdeck.app.model.TagEditMode
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabSort
import com.tabdeck.app.model.TabStatus
import com.tabdeck.app.model.ThemeMode
import com.tabdeck.app.model.TransferPacing
import com.tabdeck.app.model.ViewDensity
import com.tabdeck.app.transfer.BrowserTransferCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class TabDeckViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TabDeckApplication
    private val repository: TabDeckRepository = app.repository
    private val transferCoordinator = BrowserTransferCoordinator(application)
    private var installedBrowserCacheAt = 0L
    private var installedBrowserCache: Map<BrowserId, Boolean> = emptyMap()

    val state: StateFlow<ControlState> = repository.controlState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        repository.initialState,
    )

    private val _libraryQuery = MutableStateFlow(LibraryQuery())
    val libraryQuery: StateFlow<LibraryQuery> = _libraryQuery.asStateFlow()

    val pagedTabs: Flow<PagingData<TabItem>> = _libraryQuery
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest(repository::pagedTabs)
        .cachedIn(viewModelScope)

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _transferProgress = MutableStateFlow<BrowserTransferCoordinator.Progress?>(null)
    val transferProgress: StateFlow<BrowserTransferCoordinator.Progress?> = _transferProgress.asStateFlow()
    private var transferJob: Job? = null

    private val _duplicatePreview = MutableStateFlow<List<DuplicateCluster>>(emptyList())
    val duplicatePreview: StateFlow<List<DuplicateCluster>> = _duplicatePreview.asStateFlow()

    private val _busyAction = MutableStateFlow<String?>(null)
    val busyAction: StateFlow<String?> = _busyAction.asStateFlow()

    enum class AppCommand { OPEN_IMPORT, OPEN_LIBRARY, OPEN_TRANSFER, OPEN_CONNECT, OPEN_AUTOMATE, OPEN_COMMAND_PALETTE }

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 24)
    val commands = MutableSharedFlow<AppCommand>(extraBufferCapacity = 8)

    init {
        viewModelScope.launch(Dispatchers.IO) { repository.initialize() }
        viewModelScope.launch {
            combine(_libraryQuery, state) { query, current -> query to current.stats.total }
                .collect { (query, _) ->
                    if (query.statuses.isEmpty()) _libraryQuery.update { it.copy(statuses = setOf(TabStatus.ACTIVE)) }
                }
        }
    }

    @Synchronized
    fun installedBrowsers(forceRefresh: Boolean = false): Map<BrowserId, Boolean> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && installedBrowserCache.isNotEmpty() && now - installedBrowserCacheAt < 30_000) {
            return installedBrowserCache
        }
        installedBrowserCache = BrowserId.entries
            .filter { it.isLaunchTarget }
            .associateWith { transferCoordinator.isInstalled(it) }
        installedBrowserCacheAt = now
        return installedBrowserCache
    }

    fun importFromIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_OPEN_IMPORT -> commands.tryEmit(AppCommand.OPEN_IMPORT)
            ACTION_OPEN_LIBRARY -> commands.tryEmit(AppCommand.OPEN_LIBRARY)
            ACTION_OPEN_TRANSFER -> commands.tryEmit(AppCommand.OPEN_TRANSFER)
            ACTION_OPEN_CONNECT -> commands.tryEmit(AppCommand.OPEN_CONNECT)
            ACTION_OPEN_AUTOMATE -> commands.tryEmit(AppCommand.OPEN_AUTOMATE)
            ACTION_OPEN_COMMAND_PALETTE -> commands.tryEmit(AppCommand.OPEN_COMMAND_PALETTE)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val sourcePackage = sequenceOf(
                intent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)?.host,
                intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)?.let { runCatching { Uri.parse(it).host }.getOrNull() },
                intent.`package`.takeUnless { it == getApplication<Application>().packageName },
            ).firstOrNull { !it.isNullOrBlank() }
            val sourceBrowser = TabDeckRepository.browserForPackage(sourcePackage)
            val textParts = collectIntentTextParts(intent)
            val urls = textParts.flatMap(UrlExtractor::extract)
            if (urls.isNotEmpty()) importUrls(urls, if (sourceBrowser == BrowserId.UNKNOWN) BrowserId.SHARE_SHEET else sourceBrowser)
        }
    }

    fun importText(text: String, browser: BrowserId = BrowserId.CLIPBOARD) {
        val urls = UrlExtractor.extract(text)
        if (urls.isEmpty()) {
            messages.tryEmit("No valid web URLs found")
            return
        }
        launch(Dispatchers.IO) { importUrls(urls, browser) }
    }

    fun importDocument(uri: Uri) = launch(Dispatchers.IO) {
        val text = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use(::readTextLimited)
        }.getOrElse {
            messages.emit(it.message ?: "The selected file is too large or unreadable")
            return@launch
        }.orEmpty()
        if (text.isBlank()) {
            messages.emit("The selected file is empty or unreadable")
            return@launch
        }
        when (val decoded = SnapshotJsonCodec.decodeClassified(text)) {
            is SnapshotJsonCodec.DecodeResult.Success -> {
                val imported = repository.mergeBackup(decoded.snapshot)
                messages.emit("Merged $imported tabs from backup")
            }
            is SnapshotJsonCodec.DecodeResult.Rejected -> {
                messages.emit("Backup rejected: ${decoded.reason}")
            }
            SnapshotJsonCodec.DecodeResult.NotBackup -> {
                val urls = UrlExtractor.extract(text)
                if (urls.isEmpty()) {
                    messages.emit("No valid web URLs found in the selected file")
                } else {
                    val imported = repository.importTabs(urls.map { TabItem(url = it, browser = BrowserId.FILE_IMPORT) })
                    messages.emit("Imported $imported tabs from file")
                }
            }
        }
    }

    fun exportBackup(uri: Uri) = launch(Dispatchers.IO) {
        _busyAction.value = "Preparing backup"
        try {
            val content = SnapshotJsonCodec.encode(repository.current())
            val success = runCatching {
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("The destination could not be opened")
                output.bufferedWriter().use { it.write(content) }
            }.isSuccess
            messages.emit(if (success) "Bridge secrets were excluded; backup exported" else "Could not write the backup")
        } finally {
            _busyAction.value = null
        }
    }

    fun exportReadable(uri: Uri, format: TabExportFormat) = launch(Dispatchers.IO) {
        _busyAction.value = "Preparing ${format.label}"
        try {
            val content = TabExportCodec.encode(repository.current().tabs, format)
            val success = runCatching {
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("The destination could not be opened")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            }.isSuccess
            messages.emit(if (success) "${format.label} exported; Trash was excluded" else "Could not write the export")
        } finally {
            _busyAction.value = null
        }
    }

    private suspend fun importUrls(urls: List<String>, browser: BrowserId) {
        val imported = repository.importTabs(urls.map { TabItem(url = it, browser = browser) })
        messages.emit("Imported $imported tab${if (imported == 1) "" else "s"}")
    }

    fun setLibraryQuery(query: LibraryQuery) {
        _libraryQuery.value = query
        clearSelection()
    }

    fun updateSearch(value: String) { _libraryQuery.update { it.copy(search = value) } }
    fun updateStatuses(value: Set<TabStatus>) { _libraryQuery.update { it.copy(statuses = value.ifEmpty { setOf(TabStatus.ACTIVE) }) }; clearSelection() }
    fun updateBrowsers(value: Set<BrowserId>) { _libraryQuery.update { it.copy(browsers = value) }; clearSelection() }
    fun updateGroups(value: Set<String>) { _libraryQuery.update { it.copy(groups = value) }; clearSelection() }
    fun updateSourceDevices(value: Set<String>) { _libraryQuery.update { it.copy(sourceDevices = value) }; clearSelection() }
    fun updateSourceGroups(value: Set<String>) { _libraryQuery.update { it.copy(sourceGroups = value) }; clearSelection() }
    fun updateTags(value: Set<String>) { _libraryQuery.update { it.copy(tags = value) }; clearSelection() }
    fun updateSort(sort: TabSort, descending: Boolean = _libraryQuery.value.descending) { _libraryQuery.update { it.copy(sort = sort, descending = descending) } }
    fun togglePinnedFilter() { _libraryQuery.update { it.copy(pinnedOnly = !it.pinnedOnly) }; clearSelection() }
    fun toggleNotesFilter() { _libraryQuery.update { it.copy(hasNotesOnly = !it.hasNotesOnly) }; clearSelection() }
    fun toggleStaleFilter() { _libraryQuery.update { it.copy(staleOnly = !it.staleOnly) }; clearSelection() }
    fun resetLibraryQuery() { setLibraryQuery(LibraryQuery()) }
    fun applySmartView(view: SmartView) { setLibraryQuery(view.query) }

    fun saveCurrentView(name: String) = launch(Dispatchers.IO) {
        repository.upsertSmartView(SmartView(name = name, query = _libraryQuery.value))
        messages.emit("Smart view saved")
    }

    fun updateSmartView(view: SmartView) = launch(Dispatchers.IO) { repository.upsertSmartView(view); messages.emit("Smart view updated") }
    fun deleteSmartView(id: String) = launch(Dispatchers.IO) { repository.deleteSmartView(id); messages.emit("Smart view removed") }

    fun toggleSelected(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    fun selectIds(ids: Collection<String>, selected: Boolean) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (selected) addAll(ids) else removeAll(ids.toSet())
        }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun selectAllMatching() = launch(Dispatchers.IO) {
        _busyAction.value = "Selecting matching tabs"
        try {
            val ids = repository.tabsForQuery(_libraryQuery.value).mapTo(linkedSetOf()) { it.id }
            _selectedIds.value = ids
            messages.emit(if (ids.isEmpty()) "No tabs match the current view" else "Selected all ${ids.size} matching tabs")
        } finally {
            _busyAction.value = null
        }
    }

    fun setSelectedPinned(pinned: Boolean) = launch(Dispatchers.IO) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return@launch
        repository.setPinned(ids, pinned)
        messages.emit(if (pinned) "Pinned ${ids.size} tabs" else "Unpinned ${ids.size} tabs")
    }

    fun editTagsOnSelected(mode: TagEditMode, tags: Set<String>) = launch(Dispatchers.IO) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return@launch
        val changed = repository.editTags(ids, tags, mode)
        val verb = when (mode) {
            TagEditMode.ADD -> "Tagged"
            TagEditMode.REMOVE -> "Removed tags from"
            TagEditMode.REPLACE -> "Replaced tags on"
        }
        messages.emit(if (changed == 0) "No tags changed" else "$verb $changed tabs")
    }

    fun moveSelectedTo(status: TabStatus) = launch(Dispatchers.IO) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return@launch
        repository.setStatus(ids, status)
        clearSelection()
        val verb = when (status) {
            TabStatus.ACTIVE -> "Restored"
            TabStatus.ARCHIVED -> "Archived"
            TabStatus.TRASHED -> "Moved to Trash"
            TabStatus.SNOOZED -> "Snoozed"
        }
        messages.emit("$verb ${ids.size} tab${if (ids.size == 1) "" else "s"}")
    }

    fun snoozeSelected(untilEpochMs: Long) = launch(Dispatchers.IO) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return@launch
        repository.snooze(ids, untilEpochMs)
        SnoozeWakeWorker.schedule(getApplication(), untilEpochMs)
        clearSelection()
        messages.emit("Snoozed ${ids.size} tabs")
    }

    fun deleteSelectedPermanently() = launch(Dispatchers.IO) {
        val ids = _selectedIds.value
        repository.deletePermanently(ids)
        clearSelection()
        messages.emit("Permanently deleted ${ids.size} tabs")
    }

    fun emptyTrash() = launch(Dispatchers.IO) {
        repository.emptyTrash()
        clearSelection()
        messages.emit("Trash emptied")
    }

    fun pruneTrash(days: Int) = launch(Dispatchers.IO) {
        val removed = repository.pruneTrash(days)
        messages.emit("Permanently removed $removed old Trash item${if (removed == 1) "" else "s"}")
    }

    fun assignSelectedGroup(group: String) = launch(Dispatchers.IO) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return@launch
        repository.assignGroup(ids, group)
        messages.emit("Assigned ${ids.size} tabs to ${group.ifBlank { "Inbox" }}")
    }

    fun updateTab(tab: TabItem) = launch(Dispatchers.IO) {
        repository.updateTab(tab)
        messages.emit("Tab details updated")
    }

    fun togglePinned(tab: TabItem) = launch(Dispatchers.IO) { repository.setPinned(tab.id, !tab.pinned) }

    fun applyRules(selectedOnly: Boolean = false) = launch(Dispatchers.IO) {
        _busyAction.value = "Applying rules"
        try {
            val ids = _selectedIds.value.takeIf { selectedOnly && it.isNotEmpty() }
            val changed = repository.applyRules(ids)
            messages.emit(if (ids == null) "Rules updated $changed active tabs" else "Rules updated $changed selected tabs")
        } finally {
            _busyAction.value = null
        }
    }

    fun upsertRule(rule: RegexRule) {
        val validation = RegexCategorizer.validate(rule)
        if (!validation.valid) {
            messages.tryEmit(validation.error)
            return
        }
        launch(Dispatchers.IO) { repository.upsertRule(rule); messages.emit("Rule saved") }
    }

    fun testRule(rule: RegexRule) = launch(Dispatchers.IO) {
        _busyAction.value = "Testing rule"
        try {
            val count = repository.countRuleMatches(rule)
            messages.emit(
                if (count == 0) "${rule.name} matches no active tabs"
                else "${rule.name} matches $count active tab${if (count == 1) "" else "s"}",
            )
        } finally {
            _busyAction.value = null
        }
    }

    fun deleteRule(id: String) = launch(Dispatchers.IO) { repository.deleteRule(id); messages.emit("Rule deleted") }
    fun upsertGroup(group: GroupDefinition) = launch(Dispatchers.IO) { repository.upsertGroup(group); messages.emit("Group saved") }
    fun deleteGroup(id: String) = launch(Dispatchers.IO) { repository.deleteGroup(id); messages.emit("Group removed") }

    fun analyzeDuplicates(mode: DedupeMode) = launch(Dispatchers.IO) {
        _busyAction.value = "Analyzing duplicates"
        try {
            _duplicatePreview.value = repository.duplicateClusters(mode)
            val copies = _duplicatePreview.value.sumOf { it.removableCount }
            messages.emit(if (copies == 0) "No duplicates found" else "Found $copies removable duplicate copies")
        } finally {
            _busyAction.value = null
        }
    }

    fun clearDuplicatePreview() { _duplicatePreview.value = emptyList() }

    fun deduplicate(mode: DedupeMode, keepPolicy: KeepPolicy, mergeMetadata: Boolean = true) = launch(Dispatchers.IO) {
        _busyAction.value = "Deduplicating"
        try {
            val removed = repository.deduplicate(mode, keepPolicy, mergeMetadata)
            clearSelection()
            clearDuplicatePreview()
            messages.emit("Moved $removed duplicate tab${if (removed == 1) "" else "s"} to Trash")
        } finally {
            _busyAction.value = null
        }
    }

    fun createDeckFromSelection(name: String, description: String = "") = launch(Dispatchers.IO) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) {
            messages.emit("Select tabs before creating a deck")
            return@launch
        }
        repository.saveDeck(DeckDefinition(name = name, description = description), ids)
        messages.emit("Created deck with ${ids.size} tabs")
    }

    fun updateDeck(deck: DeckDefinition, tabIds: Collection<String>) = launch(Dispatchers.IO) {
        repository.saveDeck(deck, tabIds)
        messages.emit("Deck updated")
    }

    fun deleteDeck(id: String) = launch(Dispatchers.IO) { repository.deleteDeck(id); messages.emit("Deck removed") }

    fun transfer(target: BrowserId, selectedOnly: Boolean, group: String?) {
        launch(Dispatchers.IO) {
            val candidates = when {
                selectedOnly -> repository.tabsByIds(_selectedIds.value).filter { it.status == TabStatus.ACTIVE }
                !group.isNullOrBlank() -> repository.tabsForQuery(LibraryQuery(groups = setOf(group), statuses = setOf(TabStatus.ACTIVE)))
                else -> repository.tabsForQuery(LibraryQuery(statuses = setOf(TabStatus.ACTIVE)))
            }
            startTransfer(target, candidates)
        }
    }

    fun transferCurrentView(target: BrowserId) = launch(Dispatchers.IO) {
        startTransfer(target, repository.tabsForQuery(_libraryQuery.value))
    }

    fun transferDeck(target: BrowserId, deckId: String) = launch(Dispatchers.IO) {
        startTransfer(target, repository.tabsForDeck(deckId).filter { it.status == TabStatus.ACTIVE })
    }

    private suspend fun startTransfer(target: BrowserId, candidates: List<TabItem>) {
        if (transferJob?.isActive == true) {
            messages.emit("A transfer is already running")
            return
        }
        if (candidates.isEmpty()) {
            messages.emit("No active tabs match this transfer scope")
            return
        }
        val settings = state.value.settings
        withContext(Dispatchers.Main) {
            transferJob = viewModelScope.launch {
                try {
                    _transferProgress.value = BrowserTransferCoordinator.Progress(target, candidates.size, 0, 0, 0)
                    val result = transferCoordinator.transfer(
                        tabs = candidates,
                        target = target,
                        pacing = settings.transferPacing,
                        onProgress = { _transferProgress.value = it },
                    )
                    withContext(NonCancellable + Dispatchers.IO) {
                        repository.recordTransfer(result.event, result.successfulIds)
                        messages.emit(
                            when {
                                result.event.cancelled -> "Open request cancelled after ${result.event.opened} URLs were dispatched"
                                result.event.failed == 0 -> "Sent ${result.event.opened} new-tab requests to ${target.displayName}"
                                else -> "Sent ${result.event.opened} new-tab requests; ${result.event.failed} could not be dispatched"
                            },
                        )
                    }
                } catch (problem: Exception) {
                    withContext(NonCancellable) {
                        messages.emit(problem.message?.take(180)?.ifBlank { null } ?: "The transfer could not be completed")
                    }
                } finally {
                    withContext(NonCancellable) {
                        _transferProgress.value = null
                        transferJob = null
                    }
                }
            }
        }
    }

    fun cancelTransfer() { transferJob?.cancel() }

    fun openTab(tab: TabItem, browser: BrowserId? = null) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
            putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            putExtra(Browser.EXTRA_APPLICATION_ID, getApplication<Application>().packageName)
            browser?.packageName?.let(::setPackage)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
            .onFailure { messages.tryEmit("No browser could open this URL") }
    }

    fun shareSelected() = launch(Dispatchers.IO) {
        val selected = repository.tabsByIds(_selectedIds.value)
        if (selected.isEmpty()) {
            messages.emit("Select tabs to share")
            return@launch
        }
        val text = selected.joinToString("\n") { it.url }
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            "Share ${selected.size} tabs",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) {
            runCatching { getApplication<Application>().startActivity(intent) }
                .onFailure { messages.tryEmit("No compatible share target is available") }
        }
    }

    fun copySelected() = launch(Dispatchers.IO) {
        val urls = repository.tabsByIds(_selectedIds.value).joinToString("\n") { it.url }
        if (urls.isBlank()) {
            messages.emit("Select tabs to copy")
            return@launch
        }
        withContext(Dispatchers.Main) { copyToClipboard("TabDeck URLs", urls) }
    }

    fun setBridgeEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, LocalBridgeService::class.java).apply {
            action = if (enabled) LocalBridgeService.ACTION_START else LocalBridgeService.ACTION_STOP
        }
        if (enabled) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }

    fun regenerateBridgeToken() = launch(Dispatchers.IO) {
        if (state.value.bridgeSession.enabled) setBridgeEnabled(false)
        repository.regenerateBridgeToken()
        messages.emit("Bridge token rotated; reconnect extensions")
    }

    fun bridgeEndpoints(): List<String> = BridgeNetwork.endpoints()

    fun updateSettings(transform: (AppSettings) -> AppSettings) = launch(Dispatchers.IO) { repository.updateSettings(transform) }
    fun setBridgeScope(value: BridgeScope) = updateSettings {
        it.copy(bridgeScope = value.takeIf(BridgeScope::available) ?: BridgeScope.THIS_DEVICE)
    }
    fun setBridgeSessionMinutes(value: Int) = updateSettings {
        it.copy(bridgeSessionMinutes = value.coerceIn(1, BridgeNetwork.MAX_SESSION_MINUTES))
    }
    fun setTransferPacing(value: TransferPacing) = updateSettings { it.copy(transferPacing = value) }
    fun setViewDensity(value: ViewDensity) = updateSettings { it.copy(viewDensity = value) }
    fun setLibraryLayout(value: LibraryLayout) = updateSettings { it.copy(libraryLayout = value) }
    fun setThemeMode(value: ThemeMode) = updateSettings { it.copy(themeMode = value) }
    fun setDynamicColor(value: Boolean) = updateSettings { it.copy(dynamicColor = value) }
    fun setAccentStyle(value: AccentStyle) = updateSettings { it.copy(accentStyle = value) }
    fun setReduceMotion(value: Boolean) = updateSettings { it.copy(reduceMotion = value) }
    fun setHapticFeedback(value: Boolean) = updateSettings { it.copy(hapticFeedback = value) }
    fun setSyncMissingPolicy(value: SyncMissingPolicy) = updateSettings { it.copy(syncMissingPolicy = value) }
    fun setStaleAfterDays(value: Int) = updateSettings { it.copy(staleAfterDays = value.coerceAtLeast(1)) }
    fun setShowAdvancedControls(value: Boolean) = updateSettings { it.copy(showAdvancedControls = value) }
    fun setAutoCategorize(value: Boolean) = updateSettings { it.copy(autoCategorizeImports = value) }
    fun setStripTracking(value: Boolean) = updateSettings { it.copy(stripTrackingParameters = value) }
    fun completeOnboarding() = updateSettings { it.copy(onboardingComplete = true) }

    fun copyToClipboard(label: String, value: String) {
        val manager = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(label, value))
        messages.tryEmit("Copied $label")
    }

    fun resetAll() = launch(Dispatchers.IO) {
        transferJob?.cancelAndJoin()
        transferJob = null
        setBridgeEnabled(false)
        repository.reset()
        clearSelection()
        _libraryQuery.value = LibraryQuery()
        messages.emit("TabDeck data reset and defaults restored")
    }

    @Suppress("DEPRECATION")
    private fun collectIntentTextParts(intent: Intent): List<String> {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        var remainingBytes = MAX_IMPORT_DOCUMENT_BYTES
        val parts = mutableListOf<String>()
        val seenUris = linkedSetOf<Uri>()

        fun utf8Prefix(value: String, byteLimit: Int): Pair<String, Int> {
            var index = 0
            var byteCount = 0
            while (index < value.length) {
                val current = value[index]
                val validPair = current.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()
                val width = when {
                    validPair -> 4
                    current.isSurrogate() -> 1 // UTF-8 replaces an unpaired surrogate with a single byte.
                    current.code <= 0x7f -> 1
                    current.code <= 0x7ff -> 2
                    else -> 3
                }
                if (byteCount + width > byteLimit) break
                byteCount += width
                index += if (validPair) 2 else 1
            }
            return value.substring(0, index) to byteCount
        }

        fun addText(value: CharSequence?) {
            if (value.isNullOrBlank() || remainingBytes <= 0) return
            val raw = value.toString()
            val fullSize = raw.toByteArray(Charsets.UTF_8).size
            val (accepted, acceptedBytes) = if (fullSize <= remainingBytes) raw to fullSize
            else utf8Prefix(raw, remainingBytes)
            if (accepted.isNotEmpty()) parts += accepted
            remainingBytes -= acceptedBytes
        }

        fun addUri(uri: Uri?) {
            if (uri == null || !seenUris.add(uri) || remainingBytes <= 0) return
            when (uri.scheme?.lowercase()) {
                "http", "https" -> addText(uri.toString())
                "content", "file" -> {
                    val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty().lowercase()
                    val fallbackMime = intent.type.orEmpty().lowercase()
                    val textLike = mime.startsWith("text/") || fallbackMime.startsWith("text/") ||
                        mime in setOf("application/json", "application/xhtml+xml") ||
                        fallbackMime in setOf("application/json", "application/xhtml+xml")
                    if (textLike) {
                        runCatching {
                            resolver.openInputStream(uri)?.use { readTextLimited(it, remainingBytes) }
                        }.getOrNull()?.let(::addText)
                    }
                }
            }
        }

        addText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
        intent.data?.let { data ->
            if (data.scheme.equals("tabdeck", ignoreCase = true) && data.host.equals("import", ignoreCase = true)) {
                data.getQueryParameters("url").forEach(::addText)
                data.getQueryParameters("text").forEach(::addText)
            } else addUri(data)
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index)
                addText(item.text)
                addUri(item.uri)
                addUri(item.intent?.data)
            }
        }
        addUri(intent.getParcelableExtra(Intent.EXTRA_STREAM))
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?.forEach(::addUri)
        return parts
    }

    private fun readTextLimited(input: InputStream, byteLimit: Int = MAX_IMPORT_DOCUMENT_BYTES): String {
        val safeLimit = byteLimit.coerceIn(1, MAX_IMPORT_DOCUMENT_BYTES)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= safeLimit) { "Import files are limited to 16 MiB" }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun launch(dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main, block: suspend () -> Unit) {
        viewModelScope.launch(dispatcher) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: Exception) {
                messages.emit(problem.message?.take(180)?.ifBlank { null } ?: "TabDeck could not complete that action")
            }
        }
    }

    companion object {
        private const val MAX_IMPORT_DOCUMENT_BYTES = 16 * 1024 * 1024
        const val ACTION_OPEN_IMPORT = "com.tabdeck.app.OPEN_IMPORT"
        const val ACTION_OPEN_LIBRARY = "com.tabdeck.app.OPEN_LIBRARY"
        const val ACTION_OPEN_TRANSFER = "com.tabdeck.app.OPEN_TRANSFER"
        const val ACTION_OPEN_CONNECT = "com.tabdeck.app.OPEN_CONNECT"
        const val ACTION_OPEN_AUTOMATE = "com.tabdeck.app.OPEN_AUTOMATE"
        const val ACTION_OPEN_COMMAND_PALETTE = "com.tabdeck.app.OPEN_COMMAND_PALETTE"
    }
}
