package com.rokiddemo.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Keeps the app process ALIVE and unfrozen so the WebSocket server (owned by [App])
 * keeps receiving frames even when the phone screen is off or the app is in the
 * background. Samsung/YodaOS aggressively freeze background processes ("Freecess"),
 * which silently drops the glasses connection — a foreground service prevents that.
 */
class ServerService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
        val channelId = "rokid_server"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Rokid Server", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rokid Phone Server")
            .setContentText("Listening for the glasses on port ${App.PORT}")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /** Keep Wi-Fi + CPU awake so the socket survives screen-off / idle. */
    private fun acquireLocks() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "rokid:wifi").apply {
                setReferenceCounted(false); acquire()
            }
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rokid:cpu").apply {
                setReferenceCounted(false); acquire()
            }
        } catch (e: Exception) {
            ServerBus.log("Lock error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { wifiLock?.release() } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
    }

    companion object {
        private const val NOTIF_ID = 1
    }
}
