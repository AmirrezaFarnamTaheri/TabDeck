package com.tabdeck.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.tabdeck.app.TabDeckViewModel
import com.tabdeck.app.data.TabExportFormat
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.LibraryQuery

internal enum class AppSection(val label: String, val icon: ImageVector, val primary: Boolean = true) {
    HOME("Home", Icons.Outlined.Dashboard),
    TABS("Tabs", Icons.Outlined.Tab),
    OPEN("Open", Icons.Outlined.SwapHoriz),
    CAPTURE("Capture", Icons.Outlined.Extension),
    SETTINGS("Settings", Icons.Outlined.Settings),
    ORGANIZE("Organize", Icons.Outlined.FilterAlt, primary = false),
}

private val primarySections = AppSection.entries.filter(AppSection::primary)

@Composable
fun TabDeckRoot(viewModel: TabDeckViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.libraryQuery.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val duplicatePreview by viewModel.duplicatePreview.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val pagedTabs = viewModel.pagedTabs.collectAsLazyPagingItems()
    var section by rememberSaveable { mutableStateOf(AppSection.HOME) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var requestedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setBridgeEnabled(true) else viewModel.messages.tryEmit("Notification permission is required for a visible bridge session")
    }
    val setBridgeEnabledSafely: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setBridgeEnabled(enabled)
        }
    }
    val importTypes = remember { arrayOf("application/json", "text/plain", "text/html", "text/csv", "text/markdown", "application/xhtml+xml") }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importDocument) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportBackup) }
    val markdownExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri -> uri?.let { viewModel.exportReadable(it, TabExportFormat.MARKDOWN) } }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { viewModel.exportReadable(it, TabExportFormat.CSV) } }
    val bookmarksExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri -> uri?.let { viewModel.exportReadable(it, TabExportFormat.NETSCAPE_BOOKMARKS) } }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        viewModel.commands.collect { command ->
            when (command) {
                TabDeckViewModel.AppCommand.OpenImport -> showImportDialog = true
                TabDeckViewModel.AppCommand.OpenLibrary -> section = AppSection.TABS
                TabDeckViewModel.AppCommand.OpenTransfer -> section = AppSection.OPEN
                TabDeckViewModel.AppCommand.OpenConnect -> section = AppSection.CAPTURE
                TabDeckViewModel.AppCommand.OpenAutomate -> section = AppSection.ORGANIZE
                TabDeckViewModel.AppCommand.OpenCommandPalette -> showCommandPalette = true
                is TabDeckViewModel.AppCommand.OpenDeck -> {
                    requestedDeckId = command.deckId
                    section = AppSection.OPEN
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useDrawer = maxWidth >= 1180.dp
        val useRail = maxWidth >= 760.dp && !useDrawer
        if (!state.settings.onboardingComplete) {
            OnboardingScreen(
                onImport = { showImportDialog = true },
                onConnect = { viewModel.completeOnboarding(); section = AppSection.CAPTURE },
                onComplete = viewModel::completeOnboarding,
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { if (section == AppSection.TABS) showImportDialog = true else showCommandPalette = true },
                        icon = { Icon(if (section == AppSection.TABS) Icons.Outlined.Add else Icons.Outlined.KeyboardCommandKey, null) },
                        text = { Text(if (section == AppSection.TABS) "Import" else "Commands") },
                    )
                },
                bottomBar = {
                    if (!useDrawer && !useRail) {
                        NavigationBar {
                            primarySections.forEach { item ->
                                NavigationBarItem(
                                    selected = item == section,
                                    onClick = { section = item },
                                    icon = { Icon(item.icon, null) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                },
            ) { outerPadding ->
                Row(Modifier.fillMaxSize().padding(outerPadding)) {
                    when {
                        useDrawer -> AppDrawer(section, { section = it }, { showCommandPalette = true })
                        useRail -> AppRail(section, { section = it }, { showCommandPalette = true })
                    }
                    when (section) {
                        AppSection.HOME -> DashboardScreen(
                            state = state,
                            installedBrowsers = viewModel.installedBrowsers(),
                            onImport = { showImportDialog = true },
                            onOpenQuery = { libraryQuery -> viewModel.setLibraryQuery(libraryQuery); section = AppSection.TABS },
                            onOpen = { section = AppSection.OPEN },
                            onOrganize = { section = AppSection.ORGANIZE },
                            onConnect = { section = AppSection.CAPTURE },
                            onApplyRules = { viewModel.applyRules() },
                            onOpenTab = viewModel::openTab,
                        )
                        AppSection.TABS -> LibraryScreen(
                            state = state,
                            query = query,
                            tabs = pagedTabs,
                            selectedIds = selectedIds,
                            duplicatePreview = duplicatePreview,
                            busyAction = busyAction,
                            viewModel = viewModel,
                            onImport = { showImportDialog = true },
                        )
                        AppSection.ORGANIZE -> OrganizeScreen(
                            state = state,
                            currentQuery = query,
                            viewModel = viewModel,
                            onOpenLibrary = { section = AppSection.TABS },
                            onOpenTransfer = { section = AppSection.OPEN },
                        )
                        AppSection.OPEN -> TransferScreen(
                            state = state,
                            query = query,
                            selectedCount = selectedIds.size,
                            installedBrowsers = viewModel.installedBrowsers(),
                            progress = transferProgress,
                            viewModel = viewModel,
                            requestedDeckId = requestedDeckId,
                            onDeckRequestConsumed = { requestedDeckId = null },
                        )
                        AppSection.CAPTURE -> CaptureScreen(
                            state = state,
                            installedBrowsers = viewModel.installedBrowsers(),
                            endpoints = viewModel.bridgeEndpoints(),
                            viewModel = viewModel,
                            onBridgeEnabled = setBridgeEnabledSafely,
                            onImport = { showImportDialog = true },
                        )
                        AppSection.SETTINGS -> SettingsScreen(
                            state = state,
                            viewModel = viewModel,
                            onImportBackup = { importLauncher.launch(importTypes) },
                            onExportBackup = { exportLauncher.launch("TabDeck-3-backup.json") },
                            onExportMarkdown = { markdownExportLauncher.launch("TabDeck-tabs.md") },
                            onExportCsv = { csvExportLauncher.launch("TabDeck-tabs.csv") },
                            onExportBookmarks = { bookmarksExportLauncher.launch("TabDeck-bookmarks.html") },
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { viewModel.importText(it); showImportDialog = false },
            onChooseFile = { showImportDialog = false; importLauncher.launch(importTypes) },
        )
    }
    if (showCommandPalette) {
        val actions = listOf(
            PaletteAction("Capture tabs", "Paste links or choose a file", Icons.Outlined.Add, "capture add") { showImportDialog = true },
            PaletteAction("Find tabs", "Search and filter saved tabs", Icons.Outlined.Search, "find filter") { section = AppSection.TABS },
            PaletteAction("Analyze duplicates", "Build a normalized duplicate preview", Icons.Outlined.ContentCopy, "dedupe cleanup") { section = AppSection.TABS; viewModel.analyzeDuplicates(DedupeMode.NORMALIZED_URL) },
            PaletteAction("Run categorization rules", "Apply enabled RE2/J rules to active tabs", Icons.Outlined.AutoAwesome, "automation regex") { viewModel.applyRules() },
            PaletteAction("Organize tabs", "Manage groups, rules, views, and decks", Icons.Outlined.FilterAlt) { section = AppSection.ORGANIZE },
            PaletteAction("Open tabs", "Choose an installed target browser", Icons.Outlined.SwapHoriz) { section = AppSection.OPEN },
            PaletteAction("Capture setup", "Share, extension, and Desktop Link options", Icons.Outlined.Extension) { section = AppSection.CAPTURE },
            PaletteAction("Settings", "Appearance, backups, and local data", Icons.Outlined.Settings) { section = AppSection.SETTINGS },
            PaletteAction("Export backup", "Create a complete portable JSON backup", Icons.Outlined.Backup) { exportLauncher.launch("TabDeck-3-backup.json") },
            PaletteAction("Export Markdown", "Readable grouped outline; Trash excluded", Icons.Outlined.Backup, "markdown text") { markdownExportLauncher.launch("TabDeck-tabs.md") },
            PaletteAction("Export CSV", "Spreadsheet-safe metadata table; Trash excluded", Icons.Outlined.Backup, "csv table") { csvExportLauncher.launch("TabDeck-tabs.csv") },
            PaletteAction("Export bookmarks", "Netscape HTML for browser import", Icons.Outlined.Backup, "html bookmarks") { bookmarksExportLauncher.launch("TabDeck-bookmarks.html") },
        )
        CommandPaletteDialog(actions, { showCommandPalette = false })
    }
}

@Composable
private fun AppRail(section: AppSection, onSection: (AppSection) -> Unit, onCommands: () -> Unit) {
    NavigationRail(header = { IconButton(onClick = onCommands) { Icon(Icons.Outlined.KeyboardCommandKey, "Command palette") } }) {
        primarySections.forEach { item ->
            NavigationRailItem(selected = item == section, onClick = { onSection(item) }, icon = { Icon(item.icon, null) }, label = { Text(item.label) })
        }
    }
}

@Composable
private fun AppDrawer(section: AppSection, onSection: (AppSection) -> Unit, onCommands: () -> Unit) {
    Surface(Modifier.width(236.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.Tab, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("TabDeck", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Save, find, and open tabs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            primarySections.forEach { item ->
                NavigationDrawerItem(label = { Text(item.label) }, selected = item == section, onClick = { onSection(item) }, icon = { Icon(item.icon, null) })
            }
            Spacer(Modifier.weight(1f))
            NavigationDrawerItem(label = { Text("Command palette") }, selected = false, onClick = onCommands, icon = { Icon(Icons.Outlined.KeyboardCommandKey, null) })
        }
    }
}
