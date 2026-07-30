package com.tabdeck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tabdeck.app.engine.RegexCategorizer
import com.tabdeck.app.engine.UrlExtractor
import com.tabdeck.app.engine.UrlNormalizer
import com.tabdeck.app.model.AppSettings
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.DuplicateCluster
import com.tabdeck.app.model.GroupDefinition
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.LibraryQuery
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.RegexTarget
import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabSort
import com.tabdeck.app.model.TabStatus
import com.tabdeck.app.model.TagEditMode
import java.text.DateFormat
import java.util.Date

@Composable
fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit, onChooseFile: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val detected = remember(text) { UrlExtractor.extract(text).take(25_000) }
    val normalizedKeys = remember(detected) { detected.map(UrlNormalizer::normalized) }
    val duplicateCopies = remember(normalizedKeys) { normalizedKeys.size - normalizedKeys.distinct().size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bring tabs into the control deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste URLs, Markdown links, exported text, or an HTML snippet. TabDeck extracts and validates only HTTP and HTTPS destinations.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500_000) },
                    label = { Text("URLs or text") },
                    minLines = 7,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (text.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        AssistChip(onClick = {}, label = { Text("${detected.size} valid links") })
                        AssistChip(onClick = {}, label = { Text("$duplicateCopies duplicate copies") })
                        if (detected.size >= 25_000) AssistChip(onClick = {}, label = { Text("25,000-link safety cap") })
                    }
                    if (detected.isEmpty()) {
                        Text("No valid HTTP(S) links detected yet.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            detected.take(5).forEach { url ->
                                Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            if (detected.size > 5) Text("+ ${detected.size - 5} more", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                OutlinedButton(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileOpen, null)
                    Text("Choose text, HTML, or backup file", Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { Button(onClick = { onImport(text) }, enabled = detected.isNotEmpty()) { Text("Import ${detected.size}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TabDetailDialog(
    tab: TabItem,
    groups: List<String>,
    onDismiss: () -> Unit,
    onSave: (TabItem) -> Unit,
    onOpen: () -> Unit,
) {
    var title by remember(tab.id) { mutableStateOf(tab.title) }
    var group by remember(tab.id) { mutableStateOf(tab.assignedGroup) }
    var notes by remember(tab.id) { mutableStateOf(tab.notes) }
    var tags by remember(tab.id) { mutableStateOf(tab.tags.sorted().joinToString(", ")) }
    var groupMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tab details") },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(tab.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { OutlinedTextField(title, { title = it.take(500) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Column {
                        OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Folder, null)
                            Text(group.ifBlank { "Inbox" }, Modifier.padding(start = 8.dp))
                        }
                        DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                            groups.distinct().sorted().forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { group = option; groupMenu = false })
                            }
                        }
                    }
                }
                item { OutlinedTextField(tags, { tags = it.take(1_000) }, label = { Text("Tags, comma separated") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(notes, { notes = it.take(20_000) }, label = { Text("Notes") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth()) }
                item {
                    KeyValueRow("Source", tab.browser.displayName)
                    KeyValueRow("Imported", formatTime(tab.importedAtEpochMs))
                    KeyValueRow("Last seen", formatTime(tab.lastSeenAtEpochMs))
                    KeyValueRow("Transfers", tab.transferCount.toString())
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    tab.copy(
                        title = title.trim(),
                        assignedGroup = group.trim().ifBlank { "Inbox" },
                        notes = notes.trim(),
                        tags = tags.split(',').map(String::trim).filter(String::isNotBlank).take(64).toSet(),
                    ),
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun GroupPickerDialog(groups: List<String>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = groups.distinct().sorted().filter { it.contains(search, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(search, { search = it.take(100) }, label = { Text("Find group") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it }) { group ->
                        TextButton(onClick = { onChoose(group); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(group, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun BulkTagDialog(onDismiss: () -> Unit, onApply: (TagEditMode, Set<String>) -> Unit) {
    var rawTags by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TagEditMode.ADD) }
    val parsed = rawTags.split(',', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .take(64)
        .toCollection(linkedSetOf())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tags to selection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (mode) {
                        TagEditMode.ADD -> "Add tags while preserving every existing tag."
                        TagEditMode.REMOVE -> "Remove only the listed tags; all other tags remain."
                        TagEditMode.REPLACE -> "Replace the complete tag set. Leave the field empty to clear tags."
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TagEditMode.entries.forEach { option ->
                        FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(option.label) })
                    }
                }
                OutlinedTextField(
                    value = rawTags,
                    onValueChange = { rawTags = it.take(2_000) },
                    label = { Text("Tags") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsed.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parsed.take(12).forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(mode, parsed); onDismiss() },
                enabled = parsed.isNotEmpty() || mode == TagEditMode.REPLACE,
            ) { Text(mode.label) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SnoozeDialog(onDismiss: () -> Unit, onChoose: (Long) -> Unit) {
    val now = System.currentTimeMillis()
    val options = listOf(
        "Later today" to now + 4 * 60 * 60 * 1000L,
        "Tomorrow" to now + 24 * 60 * 60 * 1000L,
        "This weekend" to now + 3 * 24 * 60 * 60 * 1000L,
        "Next week" to now + 7 * 24 * 60 * 60 * 1000L,
        "Next month" to now + 30L * 24 * 60 * 60 * 1000L,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Snooze selected tabs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (label, value) ->
                    OutlinedButton(onClick = { onChoose(value); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, Modifier.weight(1f))
                        Text(formatDate(value), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DedupeDialog(
    settings: AppSettings,
    preview: List<DuplicateCluster>,
    busyAction: String?,
    onDismiss: () -> Unit,
    onAnalyze: (DedupeMode) -> Unit,
    onApply: (DedupeMode, KeepPolicy, Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(settings.defaultDedupeMode) }
    var keep by remember { mutableStateOf(settings.defaultKeepPolicy) }
    var mergeMetadata by remember { mutableStateOf(true) }
    LaunchedEffect(mode) { onAnalyze(mode) }
    val duplicateCopies = preview.sumOf { it.removableCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate control deck") },
        text = {
            LazyColumn(Modifier.heightIn(max = 660.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Review clusters before moving redundant copies to Trash. Pinned state, notes, tags, and history can be merged into the survivor.")
                }
                item { Text("Match strategy", style = MaterialTheme.typography.titleMedium) }
                items(DedupeMode.entries, key = { it.name }) { option ->
                    RadioLine(mode == option, option.label, option.description) { mode = option }
                }
                item { HorizontalDivider() }
                item { Text("Survivor policy", style = MaterialTheme.typography.titleMedium) }
                items(KeepPolicy.entries, key = { it.name }) { option ->
                    RadioLine(keep == option, option.label, "Choose which tab remains active") { keep = option }
                }
                item {
                    SwitchLine("Merge metadata into survivor", mergeMetadata) { mergeMetadata = it }
                }
                item {
                    ControlCard(
                        title = if (busyAction != null) busyAction else "$duplicateCopies removable copies",
                        body = if (preview.isEmpty()) "No clusters loaded for this strategy" else "${preview.size} clusters are ready for review",
                        icon = Icons.Outlined.AutoAwesome,
                    )
                }
                items(preview.take(8), key = { it.key }) { cluster ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${cluster.tabs.size} copies", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(cluster.tabs.firstOrNull()?.title.orEmpty().ifBlank { cluster.key }, maxLines = 1)
                        cluster.tabs.take(3).forEach { tab ->
                            Text("• ${tab.browser.displayName} · ${tab.assignedGroup}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(mode, keep, mergeMetadata); onDismiss() }, enabled = duplicateCopies > 0 && busyAction == null) {
                Text("Move $duplicateCopies copies to Trash")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun RuleDialog(existing: RegexRule?, groups: List<String>, onDismiss: () -> Unit, onSave: (RegexRule) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var pattern by remember(existing?.id) { mutableStateOf(existing?.pattern.orEmpty()) }
    var destination by remember(existing?.id) { mutableStateOf(existing?.destinationGroup ?: groups.firstOrNull().orEmpty()) }
    var priority by remember(existing?.id) { mutableStateOf((existing?.priority ?: 100).toString()) }
    var tags by remember(existing?.id) { mutableStateOf(existing?.addTags?.sorted()?.joinToString(", ").orEmpty()) }
    var target by remember(existing?.id) { mutableStateOf(existing?.target ?: RegexTarget.ANY) }
    var ignoreCase by remember(existing?.id) { mutableStateOf(existing?.ignoreCase ?: true) }
    var stopAfterMatch by remember(existing?.id) { mutableStateOf(existing?.stopAfterMatch ?: true) }
    var enabled by remember(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    var destinationMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    val candidate = RegexRule(
        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim(),
        pattern = pattern,
        target = target,
        destinationGroup = destination.trim(),
        priority = priority.toIntOrNull()?.coerceIn(-10_000, 10_000) ?: 100,
        enabled = enabled,
        ignoreCase = ignoreCase,
        addTags = tags.split(',').map(String::trim).filter(String::isNotBlank).take(32).toSet(),
        stopAfterMatch = stopAfterMatch,
    )
    val validation = RegexCategorizer.validate(candidate)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create categorization rule" else "Edit categorization rule") },
        text = {
            LazyColumn(Modifier.heightIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                item { OutlinedTextField(name, { name = it.take(120) }, label = { Text("Rule name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(pattern, { pattern = it.take(2_000) }, label = { Text("RE2/J pattern") }, minLines = 3, maxLines = 7, modifier = Modifier.fillMaxWidth(), isError = pattern.isNotBlank() && !validation.valid, supportingText = { if (!validation.valid) Text(validation.error) }) }
                item {
                    Column {
                        OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Match: ${target.label}") }
                        DropdownMenu(targetMenu, { targetMenu = false }) {
                            RegexTarget.entries.forEach { option -> DropdownMenuItem({ Text(option.label) }, { target = option; targetMenu = false }) }
                        }
                    }
                }
                item {
                    Column {
                        OutlinedButton(onClick = { destinationMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Destination: ${destination.ifBlank { "Choose group" }}") }
                        DropdownMenu(destinationMenu, { destinationMenu = false }) {
                            groups.distinct().sorted().forEach { group -> DropdownMenuItem({ Text(group) }, { destination = group; destinationMenu = false }) }
                        }
                    }
                }
                item { OutlinedTextField(priority, { priority = it.filter { ch -> ch.isDigit() || ch == '-' }.take(7) }, label = { Text("Priority (lower runs first)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(tags, { tags = it.take(500) }, label = { Text("Tags to add") }, modifier = Modifier.fillMaxWidth()) }
                item { SwitchLine("Enabled", enabled) { enabled = it } }
                item { SwitchLine("Ignore case", ignoreCase) { ignoreCase = it } }
                item { SwitchLine("Stop after this rule matches", stopAfterMatch) { stopAfterMatch = it } }
            }
        },
        confirmButton = { Button(onClick = { onSave(candidate); onDismiss() }, enabled = validation.valid && name.isNotBlank() && destination.isNotBlank()) { Text("Save rule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun GroupDialog(existing: GroupDefinition?, onDismiss: () -> Unit, onSave: (GroupDefinition) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.colorKey ?: "indigo") }
    var icon by remember(existing?.id) { mutableStateOf(existing?.iconKey ?: "folder") }
    var sortOrder by remember(existing?.id) { mutableStateOf((existing?.sortOrder ?: 100).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create group" else "Edit group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("Group name") }, modifier = Modifier.fillMaxWidth(), enabled = existing?.isSystem != true)
                OutlinedTextField(color, { color = it.take(40) }, label = { Text("Color key") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(icon, { icon = it.take(40) }, label = { Text("Icon key") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sortOrder, { sortOrder = it.filter(Char::isDigit).take(6) }, label = { Text("Sort order") }, modifier = Modifier.fillMaxWidth())
                if (existing?.isSystem == true) Text("The Inbox name is protected, but its visual metadata can be changed.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        GroupDefinition(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            colorKey = color.trim().ifBlank { "indigo" },
                            iconKey = icon.trim().ifBlank { "folder" },
                            sortOrder = sortOrder.toIntOrNull()?.coerceIn(0, 100_000) ?: 100,
                            isSystem = existing?.isSystem ?: false,
                        ),
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text("Save group") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SaveViewDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this smart view") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("The current search, status lanes, filters, and sort order will be reusable from the Library and Organize screens.")
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("View name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim()); onDismiss() }, enabled = name.isNotBlank()) { Icon(Icons.Outlined.Save, null); Text("Save", Modifier.padding(start = 8.dp)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CreateDeckDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create launch deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A deck is a deliberate, reusable set of selected tabs. It can be transferred later without re-running a search.")
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("Deck name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(500) }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onCreate(name.trim(), description.trim()); onDismiss() }, enabled = name.isNotBlank()) { Text("Create deck") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LibraryFilterDialog(
    query: LibraryQuery,
    groups: List<String>,
    browsers: List<BrowserId>,
    sourceDevices: List<String>,
    sourceGroups: List<String>,
    onDismiss: () -> Unit,
    onApply: (LibraryQuery) -> Unit,
) {
    var working by remember(query) { mutableStateOf(query) }
    var tags by remember(query) { mutableStateOf(query.tags.sorted().joinToString(", ")) }
    var sortMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter and sort") },
        text = {
            LazyColumn(Modifier.heightIn(max = 700.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                item { Text("Status lanes", style = MaterialTheme.typography.titleMedium) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        TabStatus.entries.forEach { status ->
                            FilterChip(
                                selected = status in working.statuses,
                                onClick = {
                                    val next = working.statuses.toMutableSet().apply { if (!add(status)) remove(status) }
                                    working = working.copy(statuses = next.ifEmpty { setOf(TabStatus.ACTIVE) })
                                },
                                label = { Text(status.label) },
                            )
                        }
                    }
                }
                item { Text("Browsers", style = MaterialTheme.typography.titleMedium) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        browsers.forEach { browser ->
                            FilterChip(
                                selected = browser in working.browsers,
                                onClick = {
                                    val next = working.browsers.toMutableSet().apply { if (!add(browser)) remove(browser) }
                                    working = working.copy(browsers = next)
                                },
                                label = { Text(browser.displayName) },
                            )
                        }
                    }
                }
                item { Text("Groups", style = MaterialTheme.typography.titleMedium) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        groups.forEach { group ->
                            FilterChip(
                                selected = group in working.groups,
                                onClick = {
                                    val next = working.groups.toMutableSet().apply { if (!add(group)) remove(group) }
                                    working = working.copy(groups = next)
                                },
                                label = { Text(group) },
                            )
                        }
                    }
                }
                if (sourceDevices.isNotEmpty()) {
                    item { Text("Source devices", style = MaterialTheme.typography.titleMedium) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            sourceDevices.forEach { device ->
                                FilterChip(
                                    selected = device in working.sourceDevices,
                                    onClick = {
                                        val next = working.sourceDevices.toMutableSet().apply { if (!add(device)) remove(device) }
                                        working = working.copy(sourceDevices = next)
                                    },
                                    label = { Text(device) },
                                )
                            }
                        }
                    }
                }
                if (sourceGroups.isNotEmpty()) {
                    item { Text("Native source groups", style = MaterialTheme.typography.titleMedium) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            sourceGroups.forEach { sourceGroup ->
                                FilterChip(
                                    selected = sourceGroup in working.sourceGroups,
                                    onClick = {
                                        val next = working.sourceGroups.toMutableSet().apply { if (!add(sourceGroup)) remove(sourceGroup) }
                                        working = working.copy(sourceGroups = next)
                                    },
                                    label = { Text(sourceGroup) },
                                )
                            }
                        }
                    }
                }
                item { OutlinedTextField(tags, { tags = it.take(500) }, label = { Text("Required tags, comma separated") }, modifier = Modifier.fillMaxWidth()) }
                item { SwitchLine("Pinned only", working.pinnedOnly) { working = working.copy(pinnedOnly = it) } }
                item { SwitchLine("Has notes", working.hasNotesOnly) { working = working.copy(hasNotesOnly = it) } }
                item { SwitchLine("Stale only", working.staleOnly) { working = working.copy(staleOnly = it) } }
                item {
                    Column {
                        OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Sort: ${working.sort.label}") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            TabSort.entries.forEach { sort -> DropdownMenuItem({ Text(sort.label) }, { working = working.copy(sort = sort); sortMenu = false }) }
                        }
                    }
                }
                item { SwitchLine("Descending order", working.descending) { working = working.copy(descending = it) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(working.copy(tags = tags.split(',').map(String::trim).filter(String::isNotBlank).take(16).toSet()))
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(LibraryQuery()); onDismiss() }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = { onConfirm(); onDismiss() }) {
                if (destructive) Icon(Icons.Outlined.Delete, null)
                Text(confirmLabel, Modifier.padding(start = if (destructive) 8.dp else 0.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

data class PaletteAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: String = "",
    val action: () -> Unit,
)

fun defaultPaletteIcons(): List<ImageVector> = listOf(
    Icons.Outlined.Dashboard,
    Icons.Outlined.Tab,
    Icons.Outlined.FilterAlt,
    Icons.Outlined.SwapHoriz,
    Icons.Outlined.Extension,
    Icons.Outlined.Backup,
    Icons.Outlined.Rule,
    Icons.Outlined.Archive,
    Icons.Outlined.Add,
)

@Composable
fun CommandPaletteDialog(actions: List<PaletteAction>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = actions.filter {
        val haystack = "${it.title} ${it.subtitle} ${it.keywords}"
        query.isBlank() || haystack.contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Command palette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(query, { query = it.take(120) }, modifier = Modifier.fillMaxWidth(), label = { Text("Search actions") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(filtered, key = { it.title }) { item ->
                        TextButton(
                            onClick = { item.action(); onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(item.icon, null)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.Start) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun RadioLine(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchLine(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    supporting: String? = null,
) {
    SwitchLine(label, checked, supporting, onChecked)
}

@Composable
fun SwitchLine(label: String, checked: Boolean, supporting: String? = null, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onChecked)
    }
}

private fun formatTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
private fun formatDate(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
