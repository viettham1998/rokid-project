package com.rokiddemo.glasses.net

import android.content.Context
import android.net.wifi.WifiManager
import com.rokiddemo.glasses.ClientBus
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Listens for the phone server's UDP broadcast ("ROKID_PHONE:<port>") and reports
 * the phone's IP + port. Lets the glasses auto-connect with no keyboard input.
 *
 * A WifiManager MulticastLock is held so the Wi-Fi chip actually delivers broadcast
 * packets to us (many devices drop them otherwise).
 */
class DiscoveryClient(
    private val context: Context,
    private val discoveryPort: Int = DISCOVERY_PORT
) {
    @Volatile private var running = false
    private var sock: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    fun start(onFound: (host: String, port: Int) -> Unit) {
        if (running) return
        running = true

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        lock = wifi.createMulticastLock("rokid-discovery").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        Thread {
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(discoveryPort))
                }
                sock = s
                ClientBus.log("Listening for phone (udp $discoveryPort)…")
                val buf = ByteArray(256)
                while (running) {
                    val pkt = DatagramPacket(buf, buf.size)
                    s.receive(pkt) // blocks until a packet arrives
                    val text = String(pkt.data, 0, pkt.length)
                    if (text.startsWith("ROKID_PHONE:")) {
                        val port = text.substringAfter(":").trim().toIntOrNull() ?: 8080
                        val host = pkt.address?.hostAddress ?: continue
                        onFound(host, port)
                    }
                }
            } catch (e: Exception) {
                if (running) ClientBus.log("Discovery error: ${e.message}")
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { sock?.close() }
        runCatching { lock?.release() }
        sock = null
        lock = null
    }

    companion object {
        const val DISCOVERY_PORT = 8888
    }
}
