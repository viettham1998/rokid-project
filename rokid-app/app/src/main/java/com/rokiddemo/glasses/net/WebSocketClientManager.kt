package com.rokiddemo.glasses.net

import android.os.Handler
import android.os.Looper
import com.rokiddemo.glasses.ClientBus
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * The glasses are the CLIENT. Connects to the phone's WebSocket server, says
 * HELLO, and waits for HELLO_ACK. Auto-reconnects every RECONNECT_MS while
 * `shouldRun` is true, so if the phone hiccups or the demo Wi-Fi blips, the
 * glasses recover on their own.
 */
class WebSocketClientManager {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)   // keep the socket alive
        .retryOnConnectionFailure(true)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null

    @Volatile
    private var shouldRun = false

    @Volatile
    private var url: String = ""

    /** (Re)start the connection loop targeting ws://host:port. */
    fun start(host: String, port: Int) {
        url = "ws://$host:$port"
        shouldRun = true
        handler.removeCallbacksAndMessages(null)
        connect()
    }

    fun stop() {
        shouldRun = false
        handler.removeCallbacksAndMessages(null)
        ws?.close(1000, "client stop")
        ws = null
        ClientBus.state("DISCONNECTED")
    }

    /** Send raw text if the socket is open. Returns false if not connected. */
    fun send(text: String): Boolean {
        val s = ws ?: return false
        return s.send(text)
    }

    /**
     * Send binary bytes (a JPEG frame). Drops the frame if the socket isn't open or
     * if too much is already queued (backpressure — keeps latency from piling up).
     */
    fun sendBytes(bytes: ByteArray): Boolean {
        val s = ws ?: return false
        if (s.queueSize() > 1_000_000) return false   // ~1 MB already waiting -> skip
        return s.send(ByteString.of(*bytes))
    }

    private fun connect() {
        if (!shouldRun) return
        ClientBus.state("CONNECTING…")
        ClientBus.log("Connecting to $url")

        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                ClientBus.state("CONNECTED (handshaking)")
                val hello = MessageProtocol.hello()
                webSocket.send(hello)
                ClientBus.log("→ $hello")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                ClientBus.log("← $text")
                if (MessageProtocol.typeOf(text) == MessageProtocol.HELLO_ACK) {
                    ClientBus.state("PHONE CONNECTED")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ClientBus.log("Closed (code=$code)")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ClientBus.state("DISCONNECTED")
                ClientBus.log("Failure: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldRun) return
        handler.postDelayed({ if (shouldRun) connect() }, RECONNECT_MS)
    }

    companion object {
        private const val RECONNECT_MS = 2000L
    }
}
