package com.rokiddemo.glasses

import android.os.Handler
import android.os.Looper

/**
 * Simple main-thread event bus between the WebSocket client (OkHttp threads) and
 * the Activity UI. Same pattern as the phone's ServerBus.
 */
object ClientBus {

    interface Listener {
        fun onState(status: String)
        fun onLog(line: String)
        fun onDetections(json: String)
    }

    @Volatile
    var listener: Listener? = null

    @Volatile
    var lastStatus: String = "DISCONNECTED"
        private set

    private val main = Handler(Looper.getMainLooper())

    fun state(status: String) {
        lastStatus = status
        main.post { listener?.onState(status) }
    }

    fun log(line: String) {
        main.post { listener?.onLog(line) }
    }

    fun detections(json: String) {
        main.post { listener?.onDetections(json) }
    }
}
