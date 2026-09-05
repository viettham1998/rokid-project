package com.rokiddemo.glasses

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rokiddemo.glasses.net.DiscoveryClient
import com.rokiddemo.glasses.net.WebSocketClientManager

/**
 * Glasses UI — designed for a device with NO keyboard and an uncertain display
 * rotation:
 *   - No text input. The phone is found automatically via UDP discovery and we
 *     auto-connect. A hardcoded fallback (192.168.43.1:8080, the usual hotspot
 *     gateway) is tried if nothing is discovered within a few seconds.
 *   - TAP anywhere (or press the touchpad / D-pad centre) to rotate the screen.
 *     The choice is saved, so you only fix rotation once.
 */
class MainActivity : AppCompatActivity(), ClientBus.Listener {

    private val wsClient = WebSocketClientManager()
    private lateinit var discovery: DiscoveryClient
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var targetText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    @Volatile private var currentTarget: String? = null
    @Volatile private var connected = false

    // Rotation options cycled by tapping.
    private val orientations = intArrayOf(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    )
    private var orientIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved rotation BEFORE inflating so it looks right immediately.
        orientIndex = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ORIENT, 0)
        requestedOrientation = orientations[orientIndex]

        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        targetText = findViewById(R.id.targetText)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)

        val root = findViewById<View>(R.id.root)
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener { cycleOrientation() }
        root.requestFocus()

        discovery = DiscoveryClient(this)
    }

    override fun onStart() {
        super.onStart()
        ClientBus.listener = this
        onState(ClientBus.lastStatus)
        startConnecting()
    }

    override fun onStop() {
        super.onStop()
        if (ClientBus.listener === this) ClientBus.listener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        discovery.stop()
        wsClient.stop()
        handler.removeCallbacksAndMessages(null)
    }

    // ---- Connection orchestration ----------------------------------------

    private fun startConnecting() {
        statusText.text = "CONNECTING…"
        // Connect to the baked-in phone IP immediately — works even when the Wi-Fi
        // blocks broadcast (so no discovery) and without a phone hotspot.
        onDiscovered(DEFAULT_HOST, DEFAULT_PORT)
        // Also listen for the phone's broadcast, in case its IP differs/changed.
        discovery.start { host, port ->
            handler.post { onDiscovered(host, port) }
        }
    }

    /** Discovery keeps arriving every second; only (re)connect when it's useful. */
    private fun onDiscovered(host: String, port: Int) {
        if (connected) return
        if (host == currentTarget) return
        currentTarget = host
        targetText.text = "Phone: $host:$port"
        ClientBus.log("Connecting to $host:$port")
        wsClient.start(host, port)
    }

    // ---- Rotation --------------------------------------------------------

    private fun cycleOrientation() {
        orientIndex = (orientIndex + 1) % orientations.size
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_ORIENT, orientIndex).apply()
        requestedOrientation = orientations[orientIndex]
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Touchpad tap / D-pad centre / Enter all rotate the screen.
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE -> { cycleOrientation(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ---- ClientBus.Listener (main thread) --------------------------------

    override fun onState(status: String) {
        connected = (status == "PHONE CONNECTED")
        statusText.text = status
        statusText.setTextColor(if (connected) 0xFF00E676.toInt() else 0xFFFFC107.toInt())
        if (connected) discovery.stop()
    }

    override fun onLog(line: String) {
        logText.append(line + "\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    companion object {
        private const val PREFS = "rokid_demo"
        private const val KEY_ORIENT = "orient_index"

        // Baked-in phone server address. Update this to the phone's Wi-Fi IP shown
        // on the "Rokid Phone Server" screen, then rebuild + push the glasses APK.
        private const val DEFAULT_HOST = "10.0.254.130"   // phone on Wi-Fi "SELAB-SV2L"
        private const val DEFAULT_PORT = 8080
    }
}
