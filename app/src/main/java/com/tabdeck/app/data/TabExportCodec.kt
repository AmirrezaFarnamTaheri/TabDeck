package com.tabdeck.app.data

import com.tabdeck.app.model.TabItem
import com.tabdeck.app.model.TabStatus

/** Human-readable, non-secret exports for interoperability outside TabDeck. */
enum class TabExportFormat(val label: String) {
    MARKDOWN("Markdown outline"),
    CSV("CSV table"),
    NETSCAPE_BOOKMARKS("Browser bookmarks HTML"),
}

object TabExportCodec {
    fun encode(tabs: List<TabItem>, format: TabExportFormat, includeTrash: Boolean = false): String {
        val exportable = tabs.asSequence()
            .filter { includeTrash || it.status != TabStatus.TRASHED }
            .sortedWith(compareBy<TabItem>({ it.assignedGroup.lowercase() }, { it.title.lowercase() }, { it.url }))
            .toList()
        return when (format) {
            TabExportFormat.MARKDOWN -> markdown(exportable)
            TabExportFormat.CSV -> csv(exportable)
            TabExportFormat.NETSCAPE_BOOKMARKS -> bookmarks(exportable)
        }
    }

    private fun markdown(tabs: List<TabItem>): String = buildString {
        appendLine("# TabDeck export")
        appendLine()
        appendLine("${tabs.size} tabs · Trash excluded")
        tabs.groupBy { it.assignedGroup.ifBlank { "Ungrouped" } }.forEach { (group, groupedTabs) ->
            appendLine()
            appendLine("## ${markdownText(group)}")
            groupedTabs.forEach { tab ->
                val title = tab.title.ifBlank { tab.url }
                append("- [").append(markdownText(title)).append("](").append(markdownUrl(tab.url)).append(')')
                val metadata = buildList {
                    add(tab.browser.displayName)
                    if (tab.status != TabStatus.ACTIVE) add(tab.status.label)
                    if (tab.pinned) add("Pinned")
                    if (tab.tags.isNotEmpty()) add(tab.tags.sorted().joinToString(", ") { "#$it" })
                }
                if (metadata.isNotEmpty()) append(" — ").append(metadata.joinToString(" · "))
                appendLine()
                if (tab.notes.isNotBlank()) appendLine("  - ${markdownText(tab.notes.replace('\n', ' '))}")
            }
        }
    }

    private fun csv(tabs: List<TabItem>): String = buildString {
        appendLine("url,title,browser,assigned_group,source_group,source_device,status,pinned,tags,notes,created_at_ms,imported_at_ms,last_seen_at_ms,transfer_count")
        tabs.forEach { tab ->
            appendLine(
                listOf(
                    tab.url,
                    tab.title,
                    tab.browser.displayName,
                    tab.assignedGroup,
                    tab.sourceGroup,
                    tab.sourceDevice,
                    tab.status.label,
                    tab.pinned.toString(),
                    tab.tags.sorted().joinToString("|"),
                    tab.notes,
                    tab.createdAtEpochMs.toString(),
                    tab.importedAtEpochMs.toString(),
                    tab.lastSeenAtEpochMs.toString(),
                    tab.transferCount.toString(),
                ).joinToString(",", transform = ::csvCell),
            )
        }
    }

    private fun bookmarks(tabs: List<TabItem>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>TabDeck Export</TITLE>")
        appendLine("<H1>TabDeck Export</H1>")
        appendLine("<DL><p>")
        tabs.groupBy { it.assignedGroup.ifBlank { "Ungrouped" } }.forEach { (group, groupedTabs) ->
            append("  <DT><H3>").append(html(group)).appendLine("</H3>")
            appendLine("  <DL><p>")
            groupedTabs.forEach { tab ->
                val title = tab.title.ifBlank { tab.url }
                append("    <DT><A HREF=\"").append(htmlAttribute(tab.url)).append("\" ADD_DATE=\"")
                    .append((tab.createdAtEpochMs / 1_000L).coerceAtLeast(0L)).append("\">")
                    .append(html(title)).appendLine("</A>")
                val details = buildList {
                    if (tab.tags.isNotEmpty()) add("Tags: " + tab.tags.sorted().joinToString(", "))
                    if (tab.notes.isNotBlank()) add(tab.notes.replace('\n', ' '))
                    if (tab.sourceDevice.isNotBlank()) add("Source: ${tab.sourceDevice} / ${tab.browser.displayName}")
                }
                if (details.isNotEmpty()) append("    <DD>").append(html(details.joinToString(" · "))).appendLine()
            }
            appendLine("  </DL><p>")
        }
        appendLine("</DL><p>")
    }

    private fun csvCell(raw: String): String {
        val neutralized = if (raw.trimStart().firstOrNull() in setOf('=', '+', '-', '@')) "'" + raw else raw
        val escaped = neutralized.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")
        return "\"" + escaped + "\""
    }

    private fun markdownText(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("\r", " ")
        .replace("\n", " ")

    private fun markdownUrl(raw: String): String = raw.replace("(", "%28").replace(")", "%29")

    private fun html(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun htmlAttribute(raw: String): String = html(raw).replace("`", "&#96;")
}
