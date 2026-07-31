package com.tabdeck.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeNetworkTest {
    @Test
    fun advertisedEndpointsAreLoopbackOnly() {
        assertEquals(listOf(BridgeNetwork.LOOPBACK_ENDPOINT), BridgeNetwork.endpoints())
        assertTrue(BridgeNetwork.endpoints().all { it.startsWith("http://127.0.0.1:") })
    }
}
