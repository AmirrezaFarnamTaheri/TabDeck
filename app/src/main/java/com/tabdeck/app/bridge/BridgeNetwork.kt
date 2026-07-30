package com.tabdeck.app.bridge

import java.net.Inet4Address
import java.net.NetworkInterface

object BridgeNetwork {
    const val PORT = 48721

    fun endpoints(): List<String> {
        val addresses = mutableListOf("http://127.0.0.1:$PORT")
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            interfaces.asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .map { "http://${it.hostAddress}:$PORT" }
                .distinct()
                .forEach(addresses::add)
        }
        return addresses.distinct()
    }
}
