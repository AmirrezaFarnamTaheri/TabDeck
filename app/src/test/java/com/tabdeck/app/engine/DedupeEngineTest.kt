package com.tabdeck.app.engine

import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.TabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupeEngineTest {
    @Test
    fun normalizedModeFindsTrackingVariants() {
        val tabs = listOf(
            TabItem(id = "plain", url = "https://example.com/read?id=4", importedAtEpochMs = 1),
            TabItem(id = "tracked", url = "https://www.example.com/read?utm_source=x&id=4#top", importedAtEpochMs = 2),
        )
        val clusters = DedupeEngine.clusters(tabs, DedupeMode.NORMALIZED_URL)
        assertEquals(1, clusters.size)
        assertEquals(2, clusters.single().tabs.size)
    }

    @Test
    fun richestPolicyKeepsMetadata() {
        val tabs = listOf(
            TabItem(id = "empty", url = "https://example.com", importedAtEpochMs = 2),
            TabItem(id = "rich", url = "https://example.com/", title = "Example", pinned = true, importedAtEpochMs = 1),
        )
        val result = DedupeEngine.deduplicate(tabs, DedupeMode.NORMALIZED_URL, KeepPolicy.RICHEST)
        assertEquals(1, result.size)
        assertTrue(result.single().id == "rich")
    }
    @Test
    fun trackingVariantsStaySeparateWhenStrippingIsDisabled() {
        val tabs = listOf(
            TabItem(id = "plain", url = "https://example.com/read?id=4", importedAtEpochMs = 1),
            TabItem(id = "tracked", url = "https://example.com/read?utm_source=x&id=4", importedAtEpochMs = 2),
        )
        assertEquals(
            0,
            DedupeEngine.clusters(
                tabs,
                DedupeMode.NORMALIZED_URL,
                stripTrackingParameters = false,
            ).size,
        )
    }

    @Test
    fun metadataMergeCombinesNotesTagsAndTransferHistory() {
        val tabs = listOf(
            TabItem(id = "a", url = "https://example.com", notes = "first", tags = setOf("read"), transferCount = 2),
            TabItem(id = "b", url = "https://www.example.com/", notes = "second", tags = setOf("work"), pinned = true, transferCount = 3),
        )
        val plan = DedupeEngine.plan(tabs, DedupeMode.NORMALIZED_URL, KeepPolicy.PINNED_FIRST)
        val survivor = plan.survivors.single()
        assertTrue(survivor.pinned)
        assertEquals(setOf("read", "work"), survivor.tags)
        assertEquals(5, survivor.transferCount)
        assertTrue("first" in survivor.notes && "second" in survivor.notes)
    }

}
