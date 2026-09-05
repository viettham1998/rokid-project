package com.rokiddemo.glasses

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rokiddemo.glasses.camera.CameraStreamer
import com.rokiddemo.glasses.net.DiscoveryClient
import com.rokiddemo.glasses.net.MessageProtocol
import com.rokiddemo.glasses.net.WebSocketClientManager
import com.rokiddemo.glasses.speech.AudioRecorder
import org.json.JSONObject

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
    private lateinit var camText: TextView
    private lateinit var detectionText: TextView
    private lateinit var speechText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    @Volatile private var currentTarget: String? = null
    @Volatile private var connected = false

    private var camera: CameraStreamer? = null
    @Volatile private var framesSent = 0
    private var lastCamUi = 0L

    private val audioRecorder = AudioRecorder()
    private var lastQuestion = ""
    private var lastAnswer = ""
    private lateinit var talkButton: Button

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
        camText = findViewById(R.id.camText)
        detectionText = findViewById(R.id.detectionText)
        speechText = findViewById(R.id.speechText)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)

        val root = findViewById<View>(R.id.root)
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener { capture() }   // tap anywhere = capture a photo
        root.requestFocus()

        // Buttons (long-press is intercepted by YodaOS, so we use tappable buttons).
        findViewById<Button>(R.id.rotateButton).setOnClickListener { cycleOrientation() }
        talkButton = findViewById(R.id.talkButton)
        talkButton.setOnClickListener { toggleTalk() }

        discovery = DiscoveryClient(this)
        ensurePermissions()
    }

    // ---- Permissions (camera + mic) --------------------------------------

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        // Start the camera immediately if allowed; ask for anything still missing.
        if (granted(Manifest.permission.CAMERA)) startCamera()

        val needed = mutableListOf<String>()
        if (!granted(Manifest.permission.CAMERA)) needed.add(Manifest.permission.CAMERA)
        if (!granted(Manifest.permission.RECORD_AUDIO)) needed.add(Manifest.permission.RECORD_AUDIO)
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            if (granted(Manifest.permission.CAMERA)) startCamera()
            else camText.text = "Camera: permission DENIED"
            if (!granted(Manifest.permission.RECORD_AUDIO)) {
                speechText.text = "🎤 mic permission DENIED"
            }
        }
    }

    // ---- Camera (Phase 2) -------------------------------------------------

    private fun startCamera() {
        if (camera != null) return
        camText.text = "Camera: ready • TAP to capture"
        camera = CameraStreamer(this, this) { jpeg -> onJpeg(jpeg) }.also { it.start() }
    }

    /** Tap handler: capture one photo and send it for detection. */
    private fun capture() {
        val cam = camera
        if (cam == null) { ensurePermissions(); return }
        if (!connected) { camText.text = "Camera: not connected to phone yet"; return }
        cam.requestCapture()
        camText.text = "Capturing…"
    }

    /** Called on the camera worker thread when a requested capture is ready. */
    private fun onJpeg(jpeg: ByteArray) {
        val sent = connected && wsClient.sendBytes(jpeg)
        if (sent) framesSent++
        val kb = jpeg.size / 1024
        handler.post {
            camText.text = if (sent) "Captured #$framesSent • ${kb}KB • TAP to capture again"
                           else "Capture failed (not connected)"
        }
    }

    // ---- Speech (Phase 4, Plan B: record on glasses, STT on phone) --------

    /** Talk button: tap to start recording, tap again to stop + send for STT. */
    private fun toggleTalk() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            speechText.text = "🎤 mic permission needed"
            ensurePermissions(); return
        }
        if (audioRecorder.isRecording) {
            val pcm = audioRecorder.stop()
            talkButton.text = "🎤 Talk"
            if (pcm.isEmpty()) { speechText.text = "🎤 No audio captured"; return }
            if (!connected) { speechText.text = "Not connected to phone"; return }
            speechText.text = "Processing…"
            sendAudioChunked(pcm)
        } else {
            if (audioRecorder.start()) {
                talkButton.text = "⏹ Stop"
                speechText.text = "🎤 Recording… tap Stop"
            } else {
                speechText.text = "🎤 Mic unavailable (see log)"
            }
        }
    }

    /** Send the utterance as AUDIO_START + small AUDIO_CHUNKs + AUDIO_END. */
    private fun sendAudioChunked(pcm: ByteArray) {
        wsClient.send(MessageProtocol.audioStart(AudioRecorder.SAMPLE_RATE))
        var off = 0
        while (off < pcm.size) {
            val end = minOf(off + MessageProtocol.CHUNK_BYTES, pcm.size)
            wsClient.send(MessageProtocol.audioChunk(pcm.copyOfRange(off, end)))
            off = end
        }
        wsClient.send(MessageProtocol.audioEnd())
    }

    private fun renderSpeech() {
        speechText.text = "You: $lastQuestion\nAI: $lastAnswer"
    }

    override fun onStart() {
        super.onStart()
        ClientBus.listener = this
        onState(ClientBus.lastStatus)
        startConnecting()
        // Make sure the camera is running (guard inside prevents double init).
        if (granted(Manifest.permission.CAMERA)) startCamera()
    }

    override fun onStop() {
        super.onStop()
        if (ClientBus.listener === this) ClientBus.listener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        camera?.stop()
        if (audioRecorder.isRecording) audioRecorder.stop()
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
        // Touchpad tap / D-pad centre / Enter = capture a photo.
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE -> { capture(); true }
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

    override fun onDetections(json: String) {
        try {
            val arr = JSONObject(json).optJSONArray("objects")
            if (arr == null || arr.length() == 0) {
                detectionText.text = "AI VISION\n(no objects)"
                return
            }
            val sb = StringBuilder("AI VISION\n")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val label = o.optString("label")
                val conf = (o.optDouble("confidence", 0.0) * 100).toInt()
                sb.append(String.format("%-12s %3d%%\n", label, conf))
            }
            detectionText.text = sb.toString().trimEnd()
        } catch (e: Exception) {
            // ignore malformed frames
        }
    }

    override fun onAssistant(json: String) {
        try {
            val o = JSONObject(json)
            val q = o.optString("question")
            if (q.isNotEmpty()) lastQuestion = q
            lastAnswer = o.optString("text")
        } catch (e: Exception) {
            lastAnswer = json
        }
        renderSpeech()
    }

    companion object {
        private const val PREFS = "rokid_demo"
        private const val KEY_ORIENT = "orient_index"
        private const val REQ_PERMS = 1001

        // Baked-in phone server address. Update this to the phone's Wi-Fi IP shown
        // on the "Rokid Phone Server" screen, then rebuild + push the glasses APK.
        private const val DEFAULT_HOST = "10.0.254.130"   // phone on Wi-Fi "SELAB-SV2L"
        private const val DEFAULT_PORT = 8080
    }
}
