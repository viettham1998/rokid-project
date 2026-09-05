package com.rokiddemo.phone

import android.app.Application
import com.rokiddemo.phone.core.FrameProcessor
import com.rokiddemo.phone.net.DiscoveryBroadcaster
import com.rokiddemo.phone.net.WebSocketServerManager

/**
 * Owns the WebSocket server for the whole app lifetime so it survives Activity
 * rotations / backgrounding. For a live demo the phone screen stays on, so an
 * Application-scoped server is enough (we can promote it to a foreground Service
 * later if screen-off during the demo becomes a problem).
 */
class App : Application() {

    lateinit var server: WebSocketServerManager
        private set

    private lateinit var discovery: DiscoveryBroadcaster
    private lateinit var frameProcessor: FrameProcessor

    override fun onCreate() {
        super.onCreate()
        instance = this
        server = WebSocketServerManager(PORT)

        // Object detection: each incoming JPEG -> detect -> broadcast result to glasses.
        frameProcessor = FrameProcessor(this) { json -> server.broadcastText(json) }
        server.onBinary = { bytes -> frameProcessor.submit(bytes) }

        try {
            server.start()
        } catch (e: Exception) {
            ServerBus.state("SERVER FAILED", 0)
            ServerBus.log("Failed to start server: ${e.message}")
        }
        // Announce our presence so the glasses auto-connect without typing an IP.
        discovery = DiscoveryBroadcaster(PORT)
        discovery.start()
    }

    companion object {
        const val PORT = 8080

        @Volatile
        lateinit var instance: App
            private set
    }
}
