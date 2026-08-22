package com.example.pinghelper

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class PingVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        val dnsIp = intent?.getStringExtra("DNS_IP") ?: "1.1.1.1"
        startVpn(dnsIp)
        return START_STICKY
    }

    private fun startVpn(dnsIp: String) {
        if (isRunning) return
        try {
            val builder = Builder()
                .setSession("PingHelper DNS Booster")
                .addAddress("10.0.0.2", 24)
                .addDnsServer(dnsIp)
                .addRoute("0.0.0.0", 0)
            
            vpnInterface = builder.establish()
            isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
