package com.rokiddemo.phone

import android.os.Handler
import android.os.Looper

/**
 * Dead-simple event bus between the WebSocket server (background threads) and
 * the UI. No coroutines / LiveData to keep the prototype minimal: the server
 * posts state + log lines, the Activity registers a listener while visible.
 * All callbacks are delivered on the main thread.
 */
object ServerBus {

    interface Listener {
        fun onState(status: String, clients: Int)
        fun onLog(line: String)
    }

    @Volatile
    var listener: Listener? = null

    // Remember the last state so a freshly-attached Activity can render immediately.
    @Volatile
    var lastStatus: String = "STARTING…"
        private set

    @Volatile
    var lastClients: Int = 0
        private set

    private val main = Handler(Looper.getMainLooper())

    fun state(status: String, clients: Int) {
        lastStatus = status
        lastClients = clients
        main.post { listener?.onState(status, clients) }
    }

    fun log(line: String) {
        main.post { listener?.onLog(line) }
    }
}
