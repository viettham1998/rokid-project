package com.rokiddemo.phone.net

import com.rokiddemo.phone.ServerBus
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections

/**
 * The phone is the SERVER. Rokid glasses (and the HTML test client) connect in.
 *
 * Binding to InetSocketAddress(port) with a wildcard address means we listen on
 * ALL interfaces (regular Wi-Fi AND the phone's hotspot ap0 interface), which is
 * exactly what we want for the demo.
 *
 * Phase 1 behaviour: on a HELLO message, reply with HELLO_ACK. Later phases add
 * more `when (type)` branches in onMessage().
 */
class WebSocketServerManager(private val serverPort: Int) :
    WebSocketServer(InetSocketAddress(serverPort)) {

    private val clients = Collections.synchronizedSet(HashSet<WebSocket>())

    // Audio arrives chunked (small frames avoid Java-WebSocket's frame-size limit);
    // we reassemble here and hand the full utterance to onAudio at AUDIO_END.
    private val audioBuf = ByteArrayOutputStream()

    /** Called for each incoming JPEG frame (binary). Set by App to run detection. */
    var onBinary: ((ByteArray) -> Unit)? = null

    /** Called with an utterance's PCM bytes from the glasses (Phase 4, Plan B). */
    var onAudio: ((ByteArray) -> Unit)? = null

    init {
        isReuseAddr = true
        // Drop dead connections after 60s of silence (glasses will ping/reconnect).
        connectionLostTimeout = 60
    }

    override fun onStart() {
        ServerBus.log("Server listening on 0.0.0.0:$serverPort")
        ServerBus.state("LISTENING", 0)
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
        clients.add(conn)
        ServerBus.log("Client connected: ${conn.remoteSocketAddress}")
        ServerBus.state("CLIENT CONNECTED", clients.size)
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        clients.remove(conn)
        ServerBus.log("Client disconnected (code=$code)")
        ServerBus.state(if (clients.isEmpty()) "LISTENING" else "CLIENT CONNECTED", clients.size)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        ServerBus.log("← $message")
        when (MessageProtocol.typeOf(message)) {
            MessageProtocol.HELLO -> {
                val ack = MessageProtocol.helloAck()
                conn.send(ack)
                ServerBus.log("→ $ack")
            }
            MessageProtocol.AUDIO_START -> synchronized(audioBuf) { audioBuf.reset() }
            MessageProtocol.AUDIO_CHUNK -> {
                val b = MessageProtocol.pcmOf(message)
                synchronized(audioBuf) { audioBuf.write(b) }
            }
            MessageProtocol.AUDIO_END -> {
                val pcm = synchronized(audioBuf) {
                    val a = audioBuf.toByteArray(); audioBuf.reset(); a
                }
                onAudio?.invoke(pcm)
            }
            else -> {
                // Other types ignored.
            }
        }
    }

    /** Binary frames are JPEG camera frames from the glasses (Phase 2). */
    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        val arr = ByteArray(message.remaining())
        message.get(arr)
        ServerBus.frame(arr)      // preview on phone
        onBinary?.invoke(arr)     // run object detection (Phase 3)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        ServerBus.log("Error: ${ex.message}")
    }

    /** Broadcast a text message to every connected client (used in later phases). */
    fun broadcastText(text: String) {
        synchronized(clients) {
            for (c in clients) {
                if (c.isOpen) c.send(text)
            }
        }
    }
}
