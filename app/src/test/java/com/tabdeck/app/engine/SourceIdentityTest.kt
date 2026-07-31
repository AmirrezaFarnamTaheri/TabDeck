package com.tabdeck.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIdentityTest {
    @Test
    fun sameSessionAndTabProduceStableOpaqueIdentity() {
        val first = SourceIdentity.encodeTabId("session-a", "42")
        val second = SourceIdentity.encodeTabId("session-a", "42")
        assertEquals(first, second)
        assertTrue(SourceIdentity.isSessionScoped(first))
    }

    @Test
    fun reusedTabIdInAnotherSessionDoesNotCollide() {
        assertNotEquals(SourceIdentity.encodeTabId("session-a", "42"), SourceIdentity.encodeTabId("session-b", "42"))
    }

    @Test
    fun legacyPayloadsRemainReadableButAreNotSessionScoped() {
        val legacy = SourceIdentity.encodeTabId(null, "42")
        assertEquals("42", legacy)
        assertFalse(SourceIdentity.isSessionScoped(legacy))
    }

    @Test
    fun identityIsSanitizedAndBounded() {
        val encoded = SourceIdentity.encodeTabId("  session\nvalue  ", " tab\u0000    7 ")
        assertTrue(SourceIdentity.isSessionScoped(encoded))
        assertTrue(encoded.endsWith(":tab 7"))
        assertTrue(encoded.length <= SourceIdentity.MAX_OPAQUE_TAB_ID_LENGTH)
    }
}
