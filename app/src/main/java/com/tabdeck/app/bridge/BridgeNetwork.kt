package com.tabdeck.app.bridge

/** Bridge transport is intentionally loopback-only until authenticated encrypted LAN transport exists. */
object BridgeNetwork {
    const val PORT = 48721
    const val MAX_SESSION_MINUTES = 6 * 60
    const val LOOPBACK_ENDPOINT = "http://127.0.0.1:$PORT/api/v3/import"

    fun endpoints(): List<String> = listOf(LOOPBACK_ENDPOINT)
}
