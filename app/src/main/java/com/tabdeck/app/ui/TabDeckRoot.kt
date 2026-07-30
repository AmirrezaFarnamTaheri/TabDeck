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

internal enum class AppSection(val label: String, val icon: ImageVector) {
    DASHBOARD("Overview", Icons.Outlined.Dashboard),
    LIBRARY("Library", Icons.Outlined.Tab),
    ORGANIZE("Organize", Icons.Outlined.FilterAlt),
    TRANSFER("Transfer", Icons.Outlined.SwapHoriz),
    CONNECT("Control room", Icons.Outlined.Extension),
}

@Composable
fun TabDeckRoot(viewModel: TabDeckViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.libraryQuery.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val duplicatePreview by viewModel.duplicatePreview.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val pagedTabs = viewModel.pagedTabs.collectAsLazyPagingItems()
    var section by rememberSaveable { mutableStateOf(AppSection.DASHBOARD) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
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
                TabDeckViewModel.AppCommand.OPEN_IMPORT -> showImportDialog = true
                TabDeckViewModel.AppCommand.OPEN_LIBRARY -> section = AppSection.LIBRARY
                TabDeckViewModel.AppCommand.OPEN_TRANSFER -> section = AppSection.TRANSFER
                TabDeckViewModel.AppCommand.OPEN_CONNECT -> section = AppSection.CONNECT
                TabDeckViewModel.AppCommand.OPEN_AUTOMATE -> section = AppSection.ORGANIZE
                TabDeckViewModel.AppCommand.OPEN_COMMAND_PALETTE -> showCommandPalette = true
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useDrawer = maxWidth >= 1180.dp
        val useRail = maxWidth >= 760.dp && !useDrawer
        if (!state.settings.onboardingComplete) {
            OnboardingScreen(
                onImport = { showImportDialog = true },
                onConnect = { viewModel.completeOnboarding(); section = AppSection.CONNECT },
                onComplete = viewModel::completeOnboarding,
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { if (section == AppSection.LIBRARY) showImportDialog = true else showCommandPalette = true },
                        icon = { Icon(if (section == AppSection.LIBRARY) Icons.Outlined.Add else Icons.Outlined.KeyboardCommandKey, null) },
                        text = { Text(if (section == AppSection.LIBRARY) "Import" else "Commands") },
                    )
                },
                bottomBar = {
                    if (!useDrawer && !useRail) {
                        NavigationBar {
                            AppSection.entries.forEach { item ->
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
                        AppSection.DASHBOARD -> DashboardScreen(
                            state = state,
                            installedBrowsers = viewModel.installedBrowsers(),
                            onImport = { showImportDialog = true },
                            onOpenQuery = { libraryQuery -> viewModel.setLibraryQuery(libraryQuery); section = AppSection.LIBRARY },
                            onOrganize = { section = AppSection.ORGANIZE },
                            onConnect = { section = AppSection.CONNECT },
                            onApplyRules = { viewModel.applyRules() },
                            onOpenTab = viewModel::openTab,
                        )
                        AppSection.LIBRARY -> LibraryScreen(
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
                            onOpenLibrary = { section = AppSection.LIBRARY },
                            onOpenTransfer = { section = AppSection.TRANSFER },
                        )
                        AppSection.TRANSFER -> TransferScreen(
                            state = state,
                            query = query,
                            selectedCount = selectedIds.size,
                            installedBrowsers = viewModel.installedBrowsers(),
                            progress = transferProgress,
                            viewModel = viewModel,
                        )
                        AppSection.CONNECT -> ConnectScreen(
                            state = state,
                            installedBrowsers = viewModel.installedBrowsers(),
                            endpoints = viewModel.bridgeEndpoints(),
                            viewModel = viewModel,
                            onBridgeEnabled = setBridgeEnabledSafely,
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
            PaletteAction("Import tabs", "Paste or choose a file", Icons.Outlined.Add, "capture add") { showImportDialog = true },
            PaletteAction("Search library", "Open the paged tab inventory", Icons.Outlined.Search, "find filter") { section = AppSection.LIBRARY },
            PaletteAction("Analyze duplicates", "Build a normalized duplicate preview", Icons.Outlined.ContentCopy, "dedupe cleanup") { section = AppSection.LIBRARY; viewModel.analyzeDuplicates(DedupeMode.NORMALIZED_URL) },
            PaletteAction("Run categorization rules", "Apply enabled RE2/J rules to active tabs", Icons.Outlined.AutoAwesome, "automation regex") { viewModel.applyRules() },
            PaletteAction("Open automation studio", "Manage views, decks, rules, and groups", Icons.Outlined.FilterAlt) { section = AppSection.ORGANIZE },
            PaletteAction("Transfer tabs", "Choose a target Android browser", Icons.Outlined.SwapHoriz) { section = AppSection.TRANSFER },
            PaletteAction("Connector control room", "Bridge, security, appearance, and data", Icons.Outlined.Extension) { section = AppSection.CONNECT },
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
        AppSection.entries.forEach { item ->
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
                    Text("Android control plane", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppSection.entries.forEach { item ->
                NavigationDrawerItem(label = { Text(item.label) }, selected = item == section, onClick = { onSection(item) }, icon = { Icon(item.icon, null) })
            }
            Spacer(Modifier.weight(1f))
            NavigationDrawerItem(label = { Text("Command palette") }, selected = false, onClick = onCommands, icon = { Icon(Icons.Outlined.KeyboardCommandKey, null) })
        }
    }
}
