package com.tabdeck.app.bridge

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
}
