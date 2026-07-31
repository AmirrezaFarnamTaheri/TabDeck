package com.tabdeck.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePayloadParserTest {
    private fun payload(identityVersion: String): String = """
        {
          "browser":"Chrome",
          "deviceName":"Pixel",
          "sourceSessionId":"session-1",
          $identityVersion
          "completeSnapshot":true,
          "tabs":[{"id":"tab-1","url":"https://example.com"}]
        }
    """.trimIndent()

    @Test
    fun completeSnapshotRequiresIdentityVersionOne() {
        assertTrue(BridgePayloadParser.parse(payload("\"identityVersion\":1,")).completeSnapshot)
        assertFalse(BridgePayloadParser.parse(payload("\"identityVersion\":2,")).completeSnapshot)
        assertFalse(BridgePayloadParser.parse(payload("")).completeSnapshot)
    }
    @Test
    fun parsesEveryTabBeyondTheFormerCollectionCeiling() {
        val tabs = (0..25_000).joinToString(",") { index ->
            "{\"id\":\"tab-$index\",\"url\":\"https://example.com/$index\"}"
        }
        val raw = """{"browser":"Chrome","deviceName":"Pixel","sourceSessionId":"session-1","identityVersion":1,"completeSnapshot":true,"tabs":[$tabs]}"""

        val parsed = BridgePayloadParser.parse(raw)

        assertEquals(25_001, parsed.tabs.size)
        assertEquals("https://example.com/25000", parsed.tabs.last().url)
    }

}
