package com.rokiddemo.phone

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rokiddemo.phone.util.NetworkUtils

class MainActivity : AppCompatActivity(), ServerBus.Listener {

    private lateinit var statusText: TextView
    private lateinit var clientsText: TextView
    private lateinit var portText: TextView
    private lateinit var ipText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        clientsText = findViewById(R.id.clientsText)
        portText = findViewById(R.id.portText)
        ipText = findViewById(R.id.ipText)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)

        portText.text = "Port: ${App.PORT}"
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refreshIps() }
        findViewById<Button>(R.id.clearButton).setOnClickListener { logText.text = "" }

        refreshIps()
    }

    private fun refreshIps() {
        ipText.text = "IP addresses:\n" + NetworkUtils.localIpv4Addresses().joinToString("\n")
    }

    override fun onStart() {
        super.onStart()
        ServerBus.listener = this
        // Render whatever state the server is already in.
        onState(ServerBus.lastStatus, ServerBus.lastClients)
    }

    override fun onStop() {
        super.onStop()
        if (ServerBus.listener === this) ServerBus.listener = null
    }

    // ---- ServerBus.Listener (already on main thread) ----------------------

    override fun onState(status: String, clients: Int) {
        statusText.text = status
        clientsText.text = "Rokid clients: $clients"
    }

    override fun onLog(line: String) {
        logText.append(line + "\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
