package com.tabdeck.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrowserUpdated
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.tabdeck.app.TabDeckViewModel
import com.tabdeck.app.engine.UrlNormalizer
import com.tabdeck.app.model.AccentStyle
import com.tabdeck.app.model.BridgeScope
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.ControlState
import com.tabdeck.app.model.DeckDefinition
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.DuplicateCluster
import com.tabdeck.app.model.GroupDefinition
import com.tabdeck.app.model.LibraryLayout
import com.tabdeck.app.model.LibraryQuery
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.SmartView
import com.tabdeck.app.model.SyncMissingPolicy
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabStatus
import com.tabdeck.app.model.ThemeMode
import com.tabdeck.app.model.TransferPacing
import com.tabdeck.app.model.ViewDensity
import com.tabdeck.app.transfer.BrowserTransferCoordinator
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    onImport: () -> Unit,
    onConnect: () -> Unit,
    onComplete: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        Triple(Icons.Outlined.Tab, "Your Android tab control plane", "Collect browser tabs through explicit connectors, then search, normalize, categorize, deduplicate, archive, snooze, and transfer them from one durable inventory."),
        Triple(Icons.Outlined.Security, "Power without false promises", "Android isolates browser data. TabDeck uses Sharesheet imports, Firefox Android WebExtensions, a local authenticated bridge, and optional ADB tooling—never root or hidden database scraping."),
        Triple(Icons.Outlined.Bolt, "Built for thousands of tabs", "Database-backed paging, saved smart views, launch decks, regex automation, reversible cleanup, and rate-limited transfer queues keep large collections controllable."),
    )
    val current = pages[page]
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(current.first, null, Modifier.padding(20.dp).size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("TABDECK 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Text(current.second, style = MaterialTheme.typography.displaySmall)
            Text(current.third, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    Surface(
                        modifier = Modifier.size(if (index == page) 28.dp else 10.dp, 10.dp),
                        shape = CircleShape,
                        color = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ) {}
                }
            }
            if (page == pages.lastIndex) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onComplete(); onImport() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Download, null)
                        Text("Import my first tabs", Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Extension, null)
                        Text("Set up browser connectors", Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text("Explore the empty control deck") }
                }
            } else {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    state: ControlState,
    installedBrowsers: Map<BrowserId, Boolean>,
    onImport: () -> Unit,
    onOpenQuery: (LibraryQuery) -> Unit,
    onOrganize: () -> Unit,
    onConnect: () -> Unit,
    onApplyRules: () -> Unit,
    onOpenTab: (TabItem) -> Unit,
) {
    val stats = state.stats
    val health = remember(stats) { collectionHealth(stats) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "Android browser operations",
                title = "Control center",
                subtitle = "A truthful, local-first command surface for every tab TabDeck can see.",
                actions = { SecurityPill("Local-first") },
            )
        }
        item {
            HeroPanel(
                title = if (stats.total == 0) "Start building one dependable inventory" else "${stats.active} active tabs, under control",
                body = if (stats.total == 0) "Import from Android browsers, connect Firefox, or receive a desktop-assisted snapshot. Nothing is deleted automatically."
                else "Health score $health/100 · ${stats.duplicateCopies} duplicate copies · ${stats.stale} stale · ${stats.inbox} waiting in Inbox.",
                icon = Icons.Outlined.BrowserUpdated,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport) { Icon(Icons.Outlined.Download, null); Text("Import", Modifier.padding(start = 7.dp)) }
                    FilledTonalButton(onClick = onApplyRules, enabled = stats.active > 0) { Icon(Icons.Outlined.AutoAwesome, null); Text("Run rules", Modifier.padding(start = 7.dp)) }
                    OutlinedButton(onClick = onOrganize) { Icon(Icons.Outlined.Tune, null); Text("Automate", Modifier.padding(start = 7.dp)) }
                }
            }
        }
        item {
            BoxWithConstraints {
                val columns = when {
                    maxWidth >= 900.dp -> 4
                    maxWidth >= 520.dp -> 2
                    else -> 1
                }
                val metrics = listOf(
                    MetricSpec("Active", stats.active, "Ready to review or transfer", Icons.Outlined.Tab) { onOpenQuery(LibraryQuery(statuses = setOf(TabStatus.ACTIVE))) },
                    MetricSpec("Duplicates", stats.duplicateCopies, "Reviewable redundant copies", Icons.Outlined.ContentCopy) { onOpenQuery(LibraryQuery(statuses = setOf(TabStatus.ACTIVE))) },
                    MetricSpec("Inbox", stats.inbox, "Uncategorized active tabs", Icons.Outlined.Inbox) { onOpenQuery(LibraryQuery(groups = setOf("Inbox"))) },
                    MetricSpec("Snoozed", stats.snoozed, "Hidden until their return time", Icons.Outlined.Schedule) { onOpenQuery(LibraryQuery(statuses = setOf(TabStatus.SNOOZED))) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    metrics.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { metric ->
                                MetricTile(metric.label, metric.value.toString(), metric.helper, metric.icon, Modifier.weight(1f), emphasized = metric.label == "Duplicates" && metric.value > 0, onClick = metric.action)
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Collection health", "Focus on the pressure points that create tab debt")
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProgressStrip(health / 100f, "Overall health", "$health / 100")
                    HealthLine("Duplicate pressure", stats.duplicateCopies, stats.active, Icons.Outlined.ContentCopy)
                    HealthLine("Stale pressure", stats.stale, stats.active, Icons.Outlined.History)
                    HealthLine("Inbox pressure", stats.inbox, stats.active, Icons.Outlined.Inbox)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { onOpenQuery(LibraryQuery(staleOnly = true)) }, label = { Text("Review ${stats.stale} stale") }, leadingIcon = { Icon(Icons.Outlined.History, null) })
                        AssistChip(onClick = { onOpenQuery(LibraryQuery(statuses = setOf(TabStatus.TRASHED))) }, label = { Text("Trash ${stats.trashed}") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                        AssistChip(onClick = onConnect, label = { Text("Connector health") }, leadingIcon = { Icon(Icons.Outlined.Extension, null) })
                    }
                }
            }
        }
        if (state.groupCounts.isNotEmpty()) {
            item { SectionTitle("Top groups", "Active inventory distribution") }
            item {
                ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        val max = state.groupCounts.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                        state.groupCounts.take(8).forEach { facet ->
                            ProgressStrip(facet.count.toFloat() / max, facet.key.ifBlank { "Inbox" }, "${facet.count}")
                        }
                    }
                }
            }
        }
        if (state.sourceDeviceCounts.isNotEmpty() || state.sourceGroupCounts.isNotEmpty()) {
            item { SectionTitle("Source topology", "Jump directly into a captured device or native browser group") }
            item {
                ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.sourceDeviceCounts.isNotEmpty()) {
                            Text("Devices", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.sourceDeviceCounts.take(10).forEach { facet ->
                                    AssistChip(
                                        onClick = { onOpenQuery(LibraryQuery(sourceDevices = setOf(facet.key))) },
                                        label = { Text("${facet.key} · ${facet.count}") },
                                        leadingIcon = { Icon(Icons.Outlined.Devices, null) },
                                    )
                                }
                            }
                        }
                        if (state.sourceGroupCounts.isNotEmpty()) {
                            Text("Native browser groups", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.sourceGroupCounts.take(10).forEach { facet ->
                                    AssistChip(
                                        onClick = { onOpenQuery(LibraryQuery(sourceGroups = setOf(facet.key))) },
                                        label = { Text("${facet.key} · ${facet.count}") },
                                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Browser readiness", "Installed transfer targets and connector routes") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedBrowsers.filterValues { it }.keys.forEach { BrowserBadge(it, true) }
                if (installedBrowsers.none { it.value }) AssistChip(onClick = onConnect, label = { Text("Refresh browser discovery") })
            }
        }
        if (state.recentTabs.isNotEmpty()) {
            item { SectionTitle("Recently captured", "Newest active records") }
            items(state.recentTabs.take(6), key = { it.id }) { tab ->
                TabCard(tab, selected = false, density = ViewDensity.COMPACT, onToggle = {}, onOpen = { onOpenTab(tab) }, onDetails = { onOpenTab(tab) }, onTogglePinned = {})
            }
        }
        if (state.importHistory.isNotEmpty() || state.transferHistory.isNotEmpty()) {
            item { SectionTitle("Recent operations", "Evidence of what entered and left the deck") }
            items(state.importHistory.take(3), key = { it.id }) { event ->
                ActivityCard(Icons.Outlined.Download, event.sourceLabel, "Accepted ${event.accepted} of ${event.received} · ${formatTime(event.createdAtEpochMs)}")
            }
            items(state.transferHistory.take(3), key = { it.id }) { event ->
                ActivityCard(Icons.Outlined.SwapHoriz, "To ${event.targetBrowser.displayName}", "Opened ${event.opened}, failed ${event.failed} · ${formatTime(event.createdAtEpochMs)}")
            }
        }
    }
}

private data class MetricSpec(val label: String, val value: Int, val helper: String, val icon: ImageVector, val action: () -> Unit)

@Composable
private fun HealthLine(label: String, count: Int, total: Int, icon: ImageVector) {
    val ratio = if (total == 0) 0f else count.toFloat() / total
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.width(110.dp).height(7.dp))
    }
}

@Composable
private fun ActivityCard(icon: ImageVector, title: String, body: String) {
    OutlinedCard(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LibraryScreen(
    state: ControlState,
    query: LibraryQuery,
    tabs: LazyPagingItems<TabItem>,
    selectedIds: Set<String>,
    duplicatePreview: List<DuplicateCluster>,
    busyAction: String?,
    viewModel: TabDeckViewModel,
    onImport: () -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var showSaveView by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }
    var showSnooze by remember { mutableStateOf(false) }
    var showDedupe by remember { mutableStateOf(false) }
    var showCreateDeck by remember { mutableStateOf(false) }
    var showBulkTags by remember { mutableStateOf(false) }
    var detailTab by remember { mutableStateOf<TabItem?>(null) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    val loadedTabs = tabs.itemSnapshotList.items
    val selectedCount = selectedIds.size
    val settings = state.settings

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader(
                eyebrow = "Paged inventory",
                title = "Tab library",
                subtitle = "Search and operate without loading the full collection into memory.",
                actions = {
                    Row {
                        IconButton(onClick = { tabs.refresh() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
                        IconButton(onClick = onImport) { Icon(Icons.Outlined.Add, "Import") }
                    }
                },
            )
            OutlinedTextField(
                value = query.search,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search title, URL, notes, tags, browser, or group") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    Row {
                        if (query.search.isNotBlank()) IconButton(onClick = { viewModel.updateSearch("") }) { Icon(Icons.Outlined.Delete, "Clear search") }
                        IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.FilterAlt, "Filters") }
                    }
                },
                singleLine = true,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                items(TabStatus.entries, key = { it.name }) { status ->
                    FilterChip(
                        selected = status in query.statuses,
                        onClick = {
                            val next = query.statuses.toMutableSet().apply { if (!add(status)) remove(status) }
                            viewModel.updateStatuses(next)
                        },
                        label = { Text(status.label) },
                    )
                }
                item { FilterChip(query.pinnedOnly, viewModel::togglePinnedFilter, { Text("Pinned") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) }) }
                item { FilterChip(query.hasNotesOnly, viewModel::toggleNotesFilter, { Text("Notes") }, leadingIcon = { Icon(Icons.Outlined.MenuBook, null) }) }
                item { FilterChip(query.staleOnly, viewModel::toggleStaleFilter, { Text("Stale") }, leadingIcon = { Icon(Icons.Outlined.History, null) }) }
                if (query.hasActiveFilters) item { AssistChip(onClick = viewModel::resetLibraryQuery, label = { Text("Reset") }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.Tune, null); Text("Filters", Modifier.padding(start = 7.dp)) }
                    OutlinedButton(onClick = { showSaveView = true }) { Icon(Icons.Outlined.Save, null); Text("Save view", Modifier.padding(start = 7.dp)) }
                    OutlinedButton(onClick = { showDedupe = true }) { Icon(Icons.Outlined.ContentCopy, null); Text("Dedupe", Modifier.padding(start = 7.dp)) }
                }
                Row {
                    IconButton(onClick = { viewModel.setLibraryLayout(LibraryLayout.LIST) }) { Icon(Icons.Outlined.List, "List layout", tint = if (settings.libraryLayout == LibraryLayout.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                    IconButton(onClick = { viewModel.setLibraryLayout(LibraryLayout.GRID) }) { Icon(Icons.Outlined.GridView, "Grid layout", tint = if (settings.libraryLayout == LibraryLayout.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                }
            }
            if (state.smartViews.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.smartViews, key = { it.id }) { view ->
                        AssistChip(onClick = { viewModel.applySmartView(view) }, label = { Text(view.name) }, leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) })
                    }
                }
            }
            AnimatedVisibility(selectedCount > 0) {
                SelectionControlBar(
                    selectedCount = selectedCount,
                    loadedCount = loadedTabs.size,
                    query = query,
                    onSelectLoaded = { viewModel.selectIds(loadedTabs.map { it.id }, true) },
                    onSelectAllMatching = viewModel::selectAllMatching,
                    onClear = viewModel::clearSelection,
                    onGroup = { showGroupPicker = true },
                    onTags = { showBulkTags = true },
                    onPin = { viewModel.setSelectedPinned(true) },
                    onUnpin = { viewModel.setSelectedPinned(false) },
                    onArchive = { viewModel.moveSelectedTo(TabStatus.ARCHIVED) },
                    onRestore = { viewModel.moveSelectedTo(TabStatus.ACTIVE) },
                    onTrash = { viewModel.moveSelectedTo(TabStatus.TRASHED) },
                    onSnooze = { showSnooze = true },
                    onCopy = viewModel::copySelected,
                    onShare = viewModel::shareSelected,
                    onDeck = { showCreateDeck = true },
                    onPermanentDelete = { confirmPermanentDelete = true },
                )
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                tabs.loadState.refresh is LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tabs.loadState.refresh is LoadState.Error -> {
                    EmptyState(Icons.Outlined.WarningAmber, "Could not load this view", "The database query failed. Retry without losing your filters.", Modifier.align(Alignment.Center))
                    Button(onClick = { tabs.retry() }, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text("Retry") }
                }
                tabs.itemCount == 0 -> EmptyState(Icons.Outlined.Search, "No matching tabs", "Change the search or filters, or import another browser snapshot.", Modifier.align(Alignment.Center))
                settings.libraryLayout == LibraryLayout.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(280.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            count = tabs.itemCount,
                            key = tabs.itemKey { it.id },
                            contentType = { "tab-grid-card" },
                        ) { index ->
                            tabs[index]?.let { tab ->
                                TabGridCard(tab, tab.id in selectedIds, { viewModel.toggleSelected(tab.id) }, { viewModel.openTab(tab) }, { detailTab = tab }, { viewModel.togglePinned(tab) })
                            }
                        }
                        if (tabs.loadState.append is LoadState.Loading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 120.dp),
                        verticalArrangement = Arrangement.spacedBy(if (settings.viewDensity == ViewDensity.DENSE) 5.dp else 9.dp),
                    ) {
                        items(
                            count = tabs.itemCount,
                            key = tabs.itemKey { it.id },
                            contentType = { "tab-list-card" },
                        ) { index ->
                            tabs[index]?.let { tab ->
                                TabCard(tab, tab.id in selectedIds, settings.viewDensity, { viewModel.toggleSelected(tab.id) }, { viewModel.openTab(tab) }, { detailTab = tab }, { viewModel.togglePinned(tab) })
                            }
                        }
                        if (tabs.loadState.append is LoadState.Loading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (tabs.loadState.append is LoadState.Error) item { OutlinedButton(onClick = { tabs.retry() }, modifier = Modifier.fillMaxWidth()) { Text("Retry loading more") } }
                    }
                }
            }
            busyAction?.let {
                Surface(Modifier.align(Alignment.BottomCenter).padding(20.dp), shape = CircleShape, tonalElevation = 8.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(it, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showFilters) LibraryFilterDialog(
        query = query,
        groups = state.groups.map { it.name },
        browsers = state.browserCounts.mapNotNull { runCatching { BrowserId.valueOf(it.key) }.getOrNull() },
        sourceDevices = state.sourceDeviceCounts.map { it.key },
        sourceGroups = state.sourceGroupCounts.map { it.key },
        onDismiss = { showFilters = false },
        onApply = viewModel::setLibraryQuery,
    )
    if (showSaveView) SaveViewDialog({ showSaveView = false }, viewModel::saveCurrentView)
    if (showGroupPicker) GroupPickerDialog(state.groups.map { it.name }, { showGroupPicker = false }, viewModel::assignSelectedGroup)
    if (showBulkTags) BulkTagDialog({ showBulkTags = false }, viewModel::editTagsOnSelected)
    if (showSnooze) SnoozeDialog({ showSnooze = false }, viewModel::snoozeSelected)
    if (showDedupe) DedupeDialog(state.settings, duplicatePreview, busyAction, { showDedupe = false; viewModel.clearDuplicatePreview() }, viewModel::analyzeDuplicates, viewModel::deduplicate)
    if (showCreateDeck) CreateDeckDialog({ showCreateDeck = false }, viewModel::createDeckFromSelection)
    detailTab?.let { tab -> TabDetailDialog(tab, state.groups.map { it.name }, { detailTab = null }, viewModel::updateTab, { viewModel.openTab(tab) }) }
    if (confirmPermanentDelete) ConfirmDialog("Permanently delete selected tabs?", "This bypasses Trash and cannot be undone from TabDeck.", "Delete permanently", true, { confirmPermanentDelete = false }, viewModel::deleteSelectedPermanently)
    if (confirmEmptyTrash) ConfirmDialog("Empty Trash?", "Every trashed tab will be permanently removed.", "Empty Trash", true, { confirmEmptyTrash = false }, viewModel::emptyTrash)
    if (TabStatus.TRASHED in query.statuses && state.stats.trashed > 0 && selectedCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            OutlinedButton(onClick = { confirmEmptyTrash = true }, modifier = Modifier.padding(bottom = 88.dp)) { Icon(Icons.Outlined.DeleteForever, null); Text("Empty Trash", Modifier.padding(start = 8.dp)) }
        }
    }
}

@Composable
private fun SelectionControlBar(
    selectedCount: Int,
    loadedCount: Int,
    query: LibraryQuery,
    onSelectLoaded: () -> Unit,
    onSelectAllMatching: () -> Unit,
    onClear: () -> Unit,
    onGroup: () -> Unit,
    onTags: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onTrash: () -> Unit,
    onSnooze: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDeck: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$selectedCount selected", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSelectLoaded, enabled = loadedCount > 0) { Icon(Icons.Outlined.SelectAll, null); Text("Loaded") }
                TextButton(onClick = onSelectAllMatching) { Text("All matching") }
                TextButton(onClick = onClear) { Text("Clear") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { AssistChip(onClick = onGroup, label = { Text("Group") }, leadingIcon = { Icon(Icons.Outlined.Folder, null) }) }
                item { AssistChip(onClick = onTags, label = { Text("Tags") }, leadingIcon = { Icon(Icons.Outlined.Tag, null) }) }
                item { AssistChip(onClick = onPin, label = { Text("Pin") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) }) }
                item { AssistChip(onClick = onUnpin, label = { Text("Unpin") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) }) }
                item { AssistChip(onClick = onSnooze, label = { Text("Snooze") }, leadingIcon = { Icon(Icons.Outlined.Schedule, null) }) }
                item { AssistChip(onClick = onDeck, label = { Text("Make deck") }, leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) }) }
                item { AssistChip(onClick = onCopy, label = { Text("Copy") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }) }
                item { AssistChip(onClick = onShare, label = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }) }
                if (query.statuses == setOf(TabStatus.TRASHED)) {
                    item { AssistChip(onClick = onRestore, label = { Text("Restore") }, leadingIcon = { Icon(Icons.Outlined.Restore, null) }) }
                    item { AssistChip(onClick = onPermanentDelete, label = { Text("Delete forever") }, leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) }) }
                } else {
                    item { AssistChip(onClick = onArchive, label = { Text("Archive") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }) }
                    item { AssistChip(onClick = onTrash, label = { Text("Trash") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }) }
                }
            }
        }
    }
}

private enum class OrganizeTab(val label: String, val icon: ImageVector) {
    VIEWS("Smart views", Icons.Outlined.Visibility),
    DECKS("Launch decks", Icons.Outlined.Bookmarks),
    RULES("Rules", Icons.Outlined.Rule),
    GROUPS("Groups", Icons.Outlined.Folder),
}

@Composable
fun OrganizeScreen(
    state: ControlState,
    currentQuery: LibraryQuery,
    viewModel: TabDeckViewModel,
    onOpenLibrary: () -> Unit,
    onOpenTransfer: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSaveView by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RegexRule?>(null) }
    var creatingRule by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<GroupDefinition?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    var deleteView by remember { mutableStateOf<SmartView?>(null) }
    var deleteDeck by remember { mutableStateOf<DeckDefinition?>(null) }
    var deleteRule by remember { mutableStateOf<RegexRule?>(null) }
    var deleteGroup by remember { mutableStateOf<GroupDefinition?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            ScreenHeader("Automation studio", "Organize", "Turn repeat work into reusable views, decks, rules, and durable groups.")
            HeroPanel("From tab pile to operating system", "Rules classify incoming tabs; smart views preserve questions; decks preserve intentional sets. Each layer stays editable and reversible.", Icons.Outlined.AutoAwesome) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.applyRules() }, enabled = state.stats.active > 0) { Icon(Icons.Outlined.PlayArrow, null); Text("Apply all rules", Modifier.padding(start = 7.dp)) }
                    OutlinedButton(onClick = onOpenLibrary) { Icon(Icons.Outlined.Tab, null); Text("Open library", Modifier.padding(start = 7.dp)) }
                }
            }
        }
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
            OrganizeTab.entries.forEachIndexed { index, tab ->
                Tab(selectedTab == index, { selectedTab = index }, text = { Text(tab.label) }, icon = { Icon(tab.icon, null) })
            }
        }
        when (OrganizeTab.entries[selectedTab]) {
            OrganizeTab.VIEWS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionTitle("Saved questions", "Each smart view restores filters, status lanes, and sort order") {
                        Button(onClick = { showSaveView = true }) { Icon(Icons.Outlined.Add, null); Text("Save current", Modifier.padding(start = 7.dp)) }
                    }
                }
                if (state.smartViews.isEmpty()) item { EmptyState(Icons.Outlined.Visibility, "No smart views yet", "Build a useful Library query, then save it here.") }
                items(state.smartViews, key = { it.id }) { view ->
                    ControlCard(view.name, describeQuery(view.query), Icons.Outlined.Visibility, trailing = {
                        Row {
                            IconButton(onClick = { viewModel.updateSmartView(view.copy(pinned = !view.pinned)) }) {
                                Icon(
                                    Icons.Outlined.PushPin,
                                    if (view.pinned) "Unpin view" else "Pin view",
                                    tint = if (view.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                            }
                            IconButton(onClick = { viewModel.applySmartView(view); onOpenLibrary() }) { Icon(Icons.Outlined.PlayArrow, "Open") }
                            IconButton(onClick = { deleteView = view }) { Icon(Icons.Outlined.Delete, "Delete") }
                        }
                    }) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            view.query.statuses.forEach { MiniPill(it.label) }
                            view.query.groups.forEach { MiniPill(it) }
                            view.query.browsers.take(2).forEach { MiniPill(it.displayName) }
                        }
                    }
                }
            }
            OrganizeTab.DECKS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SectionTitle("Launch decks", "Stable, intentional tab sets created from a Library selection") }
                item { ControlCard("Create from selection", "Select tabs in Library, then choose Make deck from the bulk control bar.", Icons.Outlined.Bookmarks, trailing = { IconButton(onClick = onOpenLibrary) { Icon(Icons.Outlined.ChevronRight, "Open Library") } }) }
                if (state.decks.isEmpty()) item { EmptyState(Icons.Outlined.Bookmarks, "No launch decks", "Create a reusable research session, work set, reading queue, or handoff bundle.") }
                items(state.decks, key = { it.id }) { deck ->
                    ControlCard(deck.name, deck.description.ifBlank { "Reusable set of ${deck.tabCount} tabs" }, Icons.Outlined.Bookmarks, trailing = {
                        Row {
                            IconButton(onClick = onOpenTransfer) { Icon(Icons.Outlined.SwapHoriz, "Transfer deck") }
                            IconButton(onClick = { deleteDeck = deck }) { Icon(Icons.Outlined.Delete, "Delete deck") }
                        }
                    }) {
                        KeyValueRow("Tabs", deck.tabCount.toString())
                        KeyValueRow("Updated", formatTime(deck.updatedAtEpochMs))
                    }
                }
            }
            OrganizeTab.RULES -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionTitle("Regex categorization", "RE2/J rules run in priority order without catastrophic backtracking") {
                        Button(onClick = { creatingRule = true }) { Icon(Icons.Outlined.Add, null); Text("New rule", Modifier.padding(start = 7.dp)) }
                    }
                }
                items(state.rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { viewModel.upsertRule(rule.copy(enabled = !rule.enabled)) },
                        onTest = { viewModel.testRule(rule) },
                        onEdit = { editingRule = rule },
                        onDelete = { deleteRule = rule },
                    )
                }
            }
            OrganizeTab.GROUPS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionTitle("Groups", "Portable semantic categories independent from native browser groups") {
                        Button(onClick = { creatingGroup = true }) { Icon(Icons.Outlined.Add, null); Text("New group", Modifier.padding(start = 7.dp)) }
                    }
                }
                itemsIndexed(state.groups, key = { _, group -> group.id }) { _, group ->
                    val count = state.groupCounts.firstOrNull { it.key == group.name }?.count ?: 0
                    ControlCard(group.name, "$count active tabs · ${group.colorKey} · ${group.iconKey}", Icons.Outlined.Folder, trailing = {
                        Row {
                            IconButton(onClick = { editingGroup = group }) { Icon(Icons.Outlined.Edit, "Edit") }
                            if (!group.isSystem) IconButton(onClick = { deleteGroup = group }) { Icon(Icons.Outlined.Delete, "Delete") }
                        }
                    })
                }
            }
        }
    }

    if (showSaveView) SaveViewDialog({ showSaveView = false }, viewModel::saveCurrentView)
    if (creatingRule) RuleDialog(null, state.groups.map { it.name }, { creatingRule = false }, viewModel::upsertRule)
    editingRule?.let { RuleDialog(it, state.groups.map { group -> group.name }, { editingRule = null }, viewModel::upsertRule) }
    if (creatingGroup) GroupDialog(null, { creatingGroup = false }, viewModel::upsertGroup)
    editingGroup?.let { GroupDialog(it, { editingGroup = null }, viewModel::upsertGroup) }
    deleteView?.let { view -> ConfirmDialog("Delete smart view?", "The underlying tabs are untouched.", "Delete view", true, { deleteView = null }) { viewModel.deleteSmartView(view.id) } }
    deleteDeck?.let { deck -> ConfirmDialog("Delete launch deck?", "The deck membership is removed; its tabs remain in the Library.", "Delete deck", true, { deleteDeck = null }) { viewModel.deleteDeck(deck.id) } }
    deleteRule?.let { rule -> ConfirmDialog("Delete ${rule.name}?", "Future imports will no longer be categorized by this rule.", "Delete rule", true, { deleteRule = null }) { viewModel.deleteRule(rule.id) } }
    deleteGroup?.let { group -> ConfirmDialog("Delete ${group.name}?", "Tabs and rules using this group will be redirected safely to Inbox.", "Delete group", true, { deleteGroup = null }) { viewModel.deleteGroup(group.id) } }
}

@Composable
private fun RuleCard(rule: RegexRule, onToggle: () -> Unit, onTest: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    ControlCard(rule.name, "Priority ${rule.priority} · ${rule.target.label} → ${rule.destinationGroup}", Icons.Outlined.Rule, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(rule.enabled, { onToggle() })
            IconButton(onClick = onTest) { Icon(Icons.Outlined.BarChart, "Test against active tabs") }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
        }
    }) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(rule.pattern, Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rule.ignoreCase) MiniPill("Ignore case")
            if (rule.stopAfterMatch) MiniPill("Stop on match")
            rule.addTags.forEach { MiniPill("#$it") }
        }
    }
}

private enum class TransferScope(val label: String) {
    SELECTED("Selected"),
    CURRENT_VIEW("Current view"),
    ALL_ACTIVE("All active"),
    GROUP("Group"),
    DECK("Deck"),
}

@Composable
fun TransferScreen(
    state: ControlState,
    query: LibraryQuery,
    selectedCount: Int,
    installedBrowsers: Map<BrowserId, Boolean>,
    progress: BrowserTransferCoordinator.Progress?,
    viewModel: TabDeckViewModel,
) {
    var target by remember { mutableStateOf<BrowserId?>(null) }
    var scope by remember { mutableStateOf(if (selectedCount > 0) TransferScope.SELECTED else TransferScope.CURRENT_VIEW) }
    var group by remember { mutableStateOf(state.groups.firstOrNull()?.name ?: "Inbox") }
    var deckId by remember { mutableStateOf(state.decks.firstOrNull()?.id.orEmpty()) }
    var showUnavailable by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var deckMenu by remember { mutableStateOf(false) }
    var confirmTransfer by remember { mutableStateOf(false) }
    val targets = BrowserId.entries.filter { it.isLaunchTarget && (showUnavailable || installedBrowsers[it] == true) }
    val estimatedCount = when (scope) {
        TransferScope.SELECTED -> selectedCount
        TransferScope.ALL_ACTIVE -> state.stats.active
        TransferScope.GROUP -> state.groupCounts.firstOrNull { it.key == group }?.count ?: 0
        TransferScope.DECK -> state.decks.firstOrNull { it.id == deckId }?.tabCount ?: 0
        TransferScope.CURRENT_VIEW -> -1
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item { ScreenHeader("Explicit package transfer", "Transfer queue", "Open validated tab URLs in a chosen installed Android browser with pacing, limits, progress, and cancellation.") }
        if (progress != null) {
            item {
                ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Opening in ${progress.target.displayName}", style = MaterialTheme.typography.titleLarge)
                        ProgressStrip(if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total, "${progress.completed} of ${progress.total}", "${progress.failed} failed")
                        OutlinedButton(onClick = viewModel::cancelTransfer, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Pause, null); Text("Cancel safely", Modifier.padding(start = 7.dp)) }
                    }
                }
            }
        }
        item {
            SectionTitle("1. Destination", "Only installed targets are shown by default") {
                Switch(showUnavailable, { showUnavailable = it })
            }
        }
        if (targets.isEmpty()) item { EmptyState(Icons.Outlined.Language, "No installed browser targets found", "Install or refresh a supported Android browser, then return here.") }
        items(targets, key = { it.name }) { browser ->
            OutlinedCard(
                onClick = { if (installedBrowsers[browser] == true) target = browser },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = if (target == browser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(browser.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${browser.family} · ${browser.packageName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (installedBrowsers[browser] == true) Icons.Outlined.CheckCircle else Icons.Outlined.CloudOff, null, tint = if (installedBrowsers[browser] == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
                }
            }
        }
        item { SectionTitle("2. Source scope", "Choose exactly what enters the queue") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransferScope.entries.forEach { option ->
                    val enabled = when (option) {
                        TransferScope.SELECTED -> selectedCount > 0
                        TransferScope.DECK -> state.decks.isNotEmpty()
                        else -> true
                    }
                    FilterChip(scope == option, { scope = option }, { Text(option.label) }, enabled = enabled)
                }
            }
        }
        if (scope == TransferScope.GROUP) item {
            Column {
                OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Folder, null); Text(group, Modifier.padding(start = 8.dp)) }
                DropdownMenu(groupMenu, { groupMenu = false }) { state.groups.forEach { option -> DropdownMenuItem({ Text(option.name) }, { group = option.name; groupMenu = false }) } }
            }
        }
        if (scope == TransferScope.DECK) item {
            Column {
                val deck = state.decks.firstOrNull { it.id == deckId }
                OutlinedButton(onClick = { deckMenu = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Bookmarks, null); Text(deck?.name ?: "Choose deck", Modifier.padding(start = 8.dp)) }
                DropdownMenu(deckMenu, { deckMenu = false }) { state.decks.forEach { option -> DropdownMenuItem({ Text("${option.name} (${option.tabCount})") }, { deckId = option.id; deckMenu = false }) } }
            }
        }
        item {
            ControlCard("Queue estimate", if (estimatedCount < 0) "The current paged query will be counted when transfer begins" else "$estimatedCount candidate tabs before the safety cap", Icons.Outlined.DataObject) {
                KeyValueRow("Hard cap", state.settings.transferBatchLimit.toString())
                KeyValueRow("Pacing", state.settings.transferPacing.label)
                KeyValueRow("Native groups", "Not universally controllable")
            }
        }
        item { SectionTitle("3. Safety and pacing", "Tune how aggressively Android intents are launched") }
        item {
            ControlCard("Transfer pacing", "A delay between launches prevents browser overload and preserves cancellation opportunities.", Icons.Outlined.Speed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransferPacing.entries.forEach { pacing -> FilterChip(state.settings.transferPacing == pacing, { viewModel.setTransferPacing(pacing) }, { Text(pacing.label) }) }
                }
                Text("Batch limit: ${state.settings.transferBatchLimit}", style = MaterialTheme.typography.labelLarge)
                Slider(value = state.settings.transferBatchLimit.toFloat(), onValueChange = { viewModel.setTransferBatchLimit(it.roundToInt()) }, valueRange = 1f..250f, steps = 248)
            }
        }
        item {
            Button(onClick = { confirmTransfer = true }, enabled = target != null && progress == null && (estimatedCount != 0), modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Outlined.SwapHoriz, null)
                Text("Review and start transfer", Modifier.padding(start = 8.dp))
            }
        }
        if (state.transferHistory.isNotEmpty()) {
            item { SectionTitle("Transfer history", "Last 100 locally recorded operations") }
            items(state.transferHistory.take(10), key = { it.id }) { event ->
                ActivityCard(Icons.Outlined.SwapHoriz, event.targetBrowser.displayName, "${event.opened}/${event.attempted} opened · ${event.failed} failed${if (event.cancelled) " · cancelled" else ""} · ${formatTime(event.createdAtEpochMs)}")
            }
        }
    }

    if (confirmTransfer && target != null) {
        val destination = target!!
        ConfirmDialog(
            title = "Open tabs in ${destination.displayName}?",
            body = "Scope: ${scope.label}. TabDeck will launch at most ${state.settings.transferBatchLimit} validated URLs with ${state.settings.transferPacing.label.lowercase()} pacing. Source tabs remain untouched.",
            confirmLabel = "Start transfer",
            onDismiss = { confirmTransfer = false },
        ) {
            when (scope) {
                TransferScope.SELECTED -> viewModel.transfer(destination, selectedOnly = true, group = null)
                TransferScope.CURRENT_VIEW -> viewModel.transferCurrentView(destination)
                TransferScope.ALL_ACTIVE -> viewModel.transfer(destination, selectedOnly = false, group = null)
                TransferScope.GROUP -> viewModel.transfer(destination, selectedOnly = false, group = group)
                TransferScope.DECK -> viewModel.transferDeck(destination, deckId)
            }
        }
    }
}

@Composable
fun ConnectScreen(
    state: ControlState,
    installedBrowsers: Map<BrowserId, Boolean>,
    endpoints: List<String>,
    viewModel: TabDeckViewModel,
    onBridgeEnabled: (Boolean) -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportCsv: () -> Unit,
    onExportBookmarks: () -> Unit,
) {
    var showToken by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmPrune by remember { mutableStateOf(false) }
    val settings = state.settings
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item { ScreenHeader("Connectors, security, preferences", "Control room", "Configure Android browser capture, transfer behavior, appearance, and local data lifecycle.") }
        item {
            HeroPanel(
                if (state.bridgeSession.enabled) "Bridge session is live" else "Bridge is off by default",
                if (state.bridgeSession.enabled) "Authenticated requests are accepted until ${state.bridgeSession.expiresAtEpochMs?.let(::formatTime) ?: "the session ends"}."
                else "Start a short, visible session only when an extension or Desktop Link needs to send a snapshot.",
                if (state.bridgeSession.enabled) Icons.Outlined.Sync else Icons.Outlined.Lock,
            ) {
                SwitchLine("Local bridge", state.bridgeSession.enabled, onBridgeEnabled, "Foreground, token-authenticated, size-bounded")
            }
        }
        item { SectionTitle("Capture routes", "Use the strongest explicit connector each Android browser permits") }
        item { ConnectorCard("Firefox Android extension", "Direct snapshot and duplicate preview through supported WebExtension APIs.", "Strong", Icons.Outlined.Extension) }
        item { ConnectorCard("Android Sharesheet", "Send one or more visible links from any browser or app into TabDeck.", "Universal", Icons.Outlined.Share) }
        item { ConnectorCard("Desktop Link + ADB", "Optional Windows-assisted inspection for Android Chromium builds exposing a DevTools socket.", "Conditional", Icons.Outlined.Devices) }
        item { ConnectorCard("Paste and file import", "Text, Markdown, HTML, JSON backup, or exported URL lists.", "Fallback", Icons.Outlined.FileOpen) }

        item { SectionTitle("Bridge session", "Network scope, duration, endpoint, and credentials") }
        item {
            ControlCard("Exposure", settings.bridgeScope.label, Icons.Outlined.Dns) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BridgeScope.entries.forEach { scope -> FilterChip(settings.bridgeScope == scope, { viewModel.setBridgeScope(scope) }, { Text(scope.label) }, enabled = !state.bridgeSession.enabled) }
                }
                Text("Session duration: ${settings.bridgeSessionMinutes} minutes", style = MaterialTheme.typography.labelLarge)
                Slider(value = settings.bridgeSessionMinutes.toFloat(), onValueChange = { viewModel.setBridgeSessionMinutes(it.roundToInt()) }, valueRange = 5f..120f, steps = 22, enabled = !state.bridgeSession.enabled)
                KeyValueRow("Accepted requests", state.bridgeSession.acceptedRequests.toString())
                KeyValueRow("Rejected requests", state.bridgeSession.rejectedRequests.toString())
            }
        }
        item {
            ControlCard("Endpoint and token", "Copy these into the Firefox or Chromium connector. Rotate immediately after any suspected disclosure.", Icons.Outlined.Link) {
                endpoints.forEach { endpoint ->
                    OutlinedButton(onClick = { viewModel.copyToClipboard("Bridge endpoint", endpoint) }, modifier = Modifier.fillMaxWidth()) {
                        Text(endpoint, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ContentCopy, null)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showToken) settings.bridgeToken else "••••••••••••••••••••••••", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { showToken = !showToken }) { Icon(Icons.Outlined.Visibility, if (showToken) "Hide token" else "Show token") }
                    IconButton(onClick = { viewModel.copyToClipboard("Bridge token", settings.bridgeToken) }) { Icon(Icons.Outlined.ContentCopy, "Copy token") }
                }
                OutlinedButton(onClick = viewModel::regenerateBridgeToken, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Security, null); Text("Rotate bridge token", Modifier.padding(start = 8.dp)) }
            }
        }

        item { SectionTitle("Android browsers", "Package discovery informs explicit transfer targets") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedBrowsers.filterKeys { it.isLaunchTarget }.forEach { (browser, installed) -> BrowserBadge(browser, installed) }
            }
        }
        if (state.sourceDeviceCounts.isNotEmpty() || state.sourceGroupCounts.isNotEmpty()) {
            item { SectionTitle("Captured identities", "The source metadata available for filtering and snapshot reconciliation") }
            item {
                ControlCard("Known sources", "Device and native-group identities are stored independently from TabDeck groups.", Icons.Outlined.Devices) {
                    state.sourceDeviceCounts.take(12).forEach { KeyValueRow(it.key, "${it.count} tabs") }
                    if (state.sourceGroupCounts.isNotEmpty()) HorizontalDivider()
                    state.sourceGroupCounts.take(12).forEach { KeyValueRow(it.key, "${it.count} tabs") }
                }
            }
        }

        item { SectionTitle("Import and synchronization", "Choose safe defaults for recurring snapshots") }
        item {
            ControlCard("Incoming snapshots", "These settings are applied at persistence boundaries, not just in the UI.", Icons.Outlined.Download) {
                SwitchLine("Auto-categorize imports", settings.autoCategorizeImports, viewModel::setAutoCategorize)
                SwitchLine("Remove common tracking parameters", settings.stripTrackingParameters, viewModel::setStripTracking)
                Text("Missing tabs in complete snapshots", style = MaterialTheme.typography.labelLarge)
                SyncMissingPolicy.entries.forEach { policy ->
                    FilterChip(settings.syncMissingPolicy == policy, { viewModel.setSyncMissingPolicy(policy) }, { Text(policy.label) })
                    Text(policy.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Mark stale after ${settings.staleAfterDays} days", style = MaterialTheme.typography.labelLarge)
                Slider(value = settings.staleAfterDays.toFloat(), onValueChange = { viewModel.setStaleAfterDays(it.roundToInt()) }, valueRange = 1f..365f, steps = 363)
            }
        }

        item { SectionTitle("Appearance and ergonomics", "Tune density without sacrificing accessibility") }
        item {
            ControlCard("Theme", settings.themeMode.label, Icons.Outlined.Palette) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode -> FilterChip(settings.themeMode == mode, { viewModel.setThemeMode(mode) }, { Text(mode.label) }) }
                }
                SwitchLine("Use Android dynamic color", settings.dynamicColor, viewModel::setDynamicColor)
                Text("Fallback accent", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentStyle.entries.forEach { accent -> FilterChip(settings.accentStyle == accent, { viewModel.setAccentStyle(accent) }, { Text(accent.label) }, enabled = !settings.dynamicColor) }
                }
            }
        }
        item {
            ControlCard("Library ergonomics", "List density and layout are independent preferences.", Icons.Outlined.Tune) {
                Text("Density", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ViewDensity.entries.forEach { density -> FilterChip(settings.viewDensity == density, { viewModel.setViewDensity(density) }, { Text(density.label) }) } }
                Text("Layout", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LibraryLayout.entries.forEach { layout -> FilterChip(settings.libraryLayout == layout, { viewModel.setLibraryLayout(layout) }, { Text(layout.label) }) } }
                SwitchLine("Reduce motion", settings.reduceMotion, viewModel::setReduceMotion)
                SwitchLine("Haptic feedback", settings.hapticFeedback, viewModel::setHapticFeedback)
            }
        }

        item { SectionTitle("Portable data", "Backups exclude the live bridge token") }
        item {
            ControlCard("Backup and interoperability", "Keep a complete TabDeck backup or export readable, browser-compatible copies. Readable exports exclude Trash.", Icons.Outlined.Backup) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onImportBackup, Modifier.weight(1f)) { Icon(Icons.Outlined.FileOpen, null); Text("Import backup", Modifier.padding(start = 6.dp)) }
                    Button(onClick = onExportBackup, Modifier.weight(1f)) { Icon(Icons.Outlined.Backup, null); Text("Full backup", Modifier.padding(start = 6.dp)) }
                }
                HorizontalDivider()
                Text("Portable exports", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportMarkdown, Modifier.weight(1f)) { Icon(Icons.Outlined.MenuBook, null); Text("Markdown", Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = onExportCsv, Modifier.weight(1f)) { Icon(Icons.Outlined.DataObject, null); Text("CSV", Modifier.padding(start = 6.dp)) }
                }
                OutlinedButton(onClick = onExportBookmarks, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Bookmarks, null); Text("Browser bookmarks HTML", Modifier.padding(start = 7.dp)) }
                Text("CSV cells are neutralized against spreadsheet formula execution; bookmarks preserve TabDeck groups as folders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ControlCard("Maintenance", "Trash is reversible until permanently pruned. Reset removes all local TabDeck data.", Icons.Outlined.Storage) {
                KeyValueRow("Total records", state.stats.total.toString())
                KeyValueRow("Trash", state.stats.trashed.toString())
                OutlinedButton(onClick = { confirmPrune = true }, modifier = Modifier.fillMaxWidth(), enabled = state.stats.trashed > 0) { Icon(Icons.Outlined.DeleteForever, null); Text("Prune Trash older than 30 days", Modifier.padding(start = 7.dp)) }
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.WarningAmber, null); Text("Reset TabDeck", Modifier.padding(start = 7.dp)) }
            }
        }
        item {
            SwitchLine("Show advanced controls", settings.showAdvancedControls, viewModel::setShowAdvancedControls, "Expose implementation-oriented diagnostics and limits")
        }
        if (settings.showAdvancedControls) item {
            ControlCard("Diagnostics", "Useful when auditing bridge behavior or large-library pressure.", Icons.Outlined.Code) {
                KeyValueRow("Database records", state.stats.total.toString())
                KeyValueRow("Duplicate copies", state.stats.duplicateCopies.toString())
                KeyValueRow("Transfer cap", settings.transferBatchLimit.toString())
                KeyValueRow("Bridge scope", settings.bridgeScope.name)
                KeyValueRow("Schema generation", "Room/KSP")
            }
        }
    }

    if (confirmPrune) ConfirmDialog("Prune old Trash?", "Trashed records older than 30 days will be permanently removed.", "Prune", true, { confirmPrune = false }) { viewModel.pruneTrash(30) }
    if (confirmReset) ConfirmDialog("Reset all TabDeck data?", "Tabs, rules, groups, views, decks, history, and preferences will be deleted locally. This cannot be undone without a backup.", "Reset everything", true, { confirmReset = false }, viewModel::resetAll)
}

@Composable
private fun ConnectorCard(title: String, body: String, strength: String, icon: ImageVector) {
    ControlCard(title, body, icon, trailing = { MiniPill(strength, MaterialTheme.colorScheme.primaryContainer) })
}

private fun collectionHealth(stats: com.tabdeck.app.model.DashboardStats): Int {
    if (stats.active <= 0) return 100
    val duplicatePenalty = (stats.duplicateCopies.toDouble() / stats.active * 35).roundToInt().coerceAtMost(35)
    val stalePenalty = (stats.stale.toDouble() / stats.active * 25).roundToInt().coerceAtMost(25)
    val inboxPenalty = (stats.inbox.toDouble() / stats.active * 20).roundToInt().coerceAtMost(20)
    val untitledPenalty = (stats.untitled.toDouble() / stats.active * 10).roundToInt().coerceAtMost(10)
    return (100 - duplicatePenalty - stalePenalty - inboxPenalty - untitledPenalty).coerceIn(0, 100)
}

private fun describeQuery(query: LibraryQuery): String = buildList {
    if (query.search.isNotBlank()) add("Search ‘${query.search.take(40)}’")
    if (query.statuses.isNotEmpty()) add(query.statuses.joinToString { it.label })
    if (query.groups.isNotEmpty()) add(query.groups.joinToString())
    if (query.browsers.isNotEmpty()) add(query.browsers.joinToString { it.displayName })
    if (query.sourceDevices.isNotEmpty()) add("Devices: ${query.sourceDevices.joinToString()}")
    if (query.sourceGroups.isNotEmpty()) add("Source groups: ${query.sourceGroups.joinToString()}")
    if (query.tags.isNotEmpty()) add(query.tags.joinToString { "#$it" })
    if (query.pinnedOnly) add("Pinned")
    if (query.hasNotesOnly) add("Has notes")
    if (query.staleOnly) add("Stale")
    add(query.sort.label)
}.joinToString(" · ")

private fun formatTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
