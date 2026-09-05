package com.rokiddemo.phone.net

import com.rokiddemo.phone.ServerBus
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Broadcasts the phone server's presence over UDP so the glasses can auto-discover
 * it — no typing an IP on the glasses. Sends "ROKID_PHONE:<wsPort>" once per second
 * to the global broadcast address AND every interface's broadcast address (covers
 * both shared-Wi-Fi and phone-hotspot cases). The glasses read the sender's IP from
 * the packet, so the payload only needs the WebSocket port.
 */
class DiscoveryBroadcaster(
    private val wsPort: Int,
    private val discoveryPort: Int = DISCOVERY_PORT
) {
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread {
            try {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    val msg = "ROKID_PHONE:$wsPort".toByteArray()
                    ServerBus.log("Discovery broadcast started (udp $discoveryPort)")
                    while (running) {
                        for (addr in broadcastAddresses()) {
                            try {
                                sock.send(DatagramPacket(msg, msg.size, addr, discoveryPort))
                            } catch (_: Exception) { /* per-address failure is fine */ }
                        }
                        Thread.sleep(1000)
                    }
                }
            } catch (e: Exception) {
                ServerBus.log("Discovery broadcaster error: ${e.message}")
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() { running = false }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        try { out.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    ia.broadcast?.let { out.add(it) }
                }
            }
        } catch (_: Exception) {}
        return out
    }

    companion object {
        const val DISCOVERY_PORT = 8888
    }
}
