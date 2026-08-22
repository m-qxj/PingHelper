package com.example.pinghelper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetAddress

class PingVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        try {
            val builder = Builder()
                .addAddress("10.1.10.1", 24)
                .addDnsServer("1.1.1.1") // Cloudflare DNS Gaming
                .addDnsServer("8.8.8.8")
                .setSession("PingHelper Secure DNS")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            startNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startNotification() {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "PingHelper Gaming VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("مُسرّع البينج والجدار الناري مفعّل")
            .setContentText("التطبيق يعمل على حماية وتحسين اتصالات الألعاب الآن")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

class FloatingPingService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: TextView? = null
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = TextView(this).apply {
            text = "Ping: -- ms"
            setTextColor(0xFF00E676.toInt())
            setBackgroundColor(0xCC000000.toInt())
            setPadding(20, 10, 20, 10)
            textSize = 14f
        }

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        try {
            windowManager?.addView(floatingView, params)
            startPingLoop()
        } catch (_: Exception) {}
    }

    private fun startPingLoop() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val ping = executePing("8.8.8.8")
                withContext(Dispatchers.Main) {
                    floatingView?.text = "🎮 Ping: ${ping}ms"
                }
                delay(2000)
            }
        }
    }

    private fun executePing(host: String): Long {
        return try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(host)
            if (address.isReachable(1000)) {
                System.currentTimeMillis() - startTime
            } else -1
        } catch (_: Exception) { -1 }
    }

    override fun onDestroy() {
        job?.cancel()
        floatingView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }
}
