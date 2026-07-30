package com.tabdeck.app.engine

import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.DedupePlan
import com.tabdeck.app.model.DuplicateCluster
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.TabItem

object DedupeEngine {
    fun clusters(
        tabs: List<TabItem>,
        mode: DedupeMode,
        stripTrackingParameters: Boolean = true,
    ): List<DuplicateCluster> =
        tabs.groupBy { keyFor(it, mode, stripTrackingParameters) }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .map { (key, values) -> DuplicateCluster(key, values.sortedByDescending { it.importedAtEpochMs }) }
            .sortedWith(compareByDescending<DuplicateCluster> { it.tabs.size }.thenBy { it.key })

    fun plan(
        tabs: List<TabItem>,
        mode: DedupeMode,
        keepPolicy: KeepPolicy,
        mergeMetadata: Boolean = true,
        stripTrackingParameters: Boolean = true,
    ): DedupePlan {
        val clusters = clusters(tabs, mode, stripTrackingParameters)
        val duplicateIds = linkedSetOf<String>()
        val survivors = mutableListOf<TabItem>()
        val merged = linkedMapOf<String, TabItem>()

        clusters.forEach { cluster ->
            val survivor = chooseSurvivor(cluster.tabs, keepPolicy)
            val enriched = if (mergeMetadata) mergeInto(survivor, cluster.tabs) else survivor
            survivors += enriched
            merged[survivor.id] = enriched
            cluster.tabs.asSequence().filterNot { it.id == survivor.id }.mapTo(duplicateIds) { it.id }
        }
        return DedupePlan(survivors, duplicateIds, clusters, merged)
    }

    fun deduplicate(
        tabs: List<TabItem>,
        mode: DedupeMode,
        keepPolicy: KeepPolicy,
        stripTrackingParameters: Boolean = true,
    ): List<TabItem> {
        val plan = plan(tabs, mode, keepPolicy, stripTrackingParameters = stripTrackingParameters)
        return tabs.asSequence()
            .filterNot { it.id in plan.duplicateIds }
            .map { plan.mergedTabs[it.id] ?: it }
            .toList()
    }

    private fun chooseSurvivor(tabs: List<TabItem>, policy: KeepPolicy): TabItem = when (policy) {
        KeepPolicy.NEWEST -> tabs.maxBy { it.importedAtEpochMs }
        KeepPolicy.OLDEST -> tabs.minBy { it.importedAtEpochMs }
        KeepPolicy.RICHEST -> tabs.maxWith(compareBy<TabItem> { metadataScore(it) }.thenBy { it.importedAtEpochMs })
        KeepPolicy.PINNED_FIRST -> tabs.maxWith(
            compareBy<TabItem> { if (it.pinned) 1 else 0 }
                .thenBy { metadataScore(it) }
                .thenBy { it.importedAtEpochMs },
        )
    }

    private fun mergeInto(survivor: TabItem, all: List<TabItem>): TabItem {
        val richestTitle = all.map { it.title.trim() }.filter(String::isNotBlank).maxByOrNull(String::length).orEmpty()
        val notes = all.map { it.notes.trim() }.filter(String::isNotBlank).distinct().joinToString("\n\n")
        val preferredGroup = all.filter { it.assignedGroup.isNotBlank() && it.assignedGroup != "Inbox" }
            .maxByOrNull(::metadataScore)?.assignedGroup ?: survivor.assignedGroup
        return survivor.copy(
            title = survivor.title.ifBlank { richestTitle },
            sourceGroup = survivor.sourceGroup.ifBlank { all.firstOrNull { it.sourceGroup.isNotBlank() }?.sourceGroup.orEmpty() },
            assignedGroup = preferredGroup,
            pinned = all.any { it.pinned },
            notes = notes.ifBlank { survivor.notes },
            tags = all.flatMapTo(linkedSetOf()) { it.tags },
            createdAtEpochMs = all.minOf { it.createdAtEpochMs },
            importedAtEpochMs = all.maxOf { it.importedAtEpochMs },
            lastSeenAtEpochMs = all.maxOf { it.lastSeenAtEpochMs },
            lastTransferredAtEpochMs = all.mapNotNull { it.lastTransferredAtEpochMs }.maxOrNull(),
            transferCount = all.sumOf { it.transferCount },
        )
    }

    private fun keyFor(tab: TabItem, mode: DedupeMode, stripTrackingParameters: Boolean): String = when (mode) {
        DedupeMode.EXACT_URL -> UrlNormalizer.exact(tab.url)
        DedupeMode.NORMALIZED_URL -> UrlNormalizer.normalized(tab.url, stripTrackingParameters)
        DedupeMode.HOST_AND_PATH -> UrlNormalizer.hostAndPath(tab.url)
    }

    private fun metadataScore(tab: TabItem): Int =
        (if (tab.title.isNotBlank()) 5 else 0) +
            (if (tab.sourceGroup.isNotBlank()) 2 else 0) +
            (if (tab.assignedGroup.isNotBlank() && tab.assignedGroup != "Inbox") 4 else 0) +
            tab.tags.size * 2 +
            (if (tab.notes.isNotBlank()) 4 else 0) +
            (if (tab.pinned) 6 else 0) +
            tab.transferCount.coerceAtMost(3)
}
