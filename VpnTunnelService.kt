package com.vexclient.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

class VpnTunnelService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        val link = intent?.getStringExtra(EXTRA_LINK)
        if (link.isNullOrEmpty()) {
            fail("تنظیمات سرور دریافت نشد.")
            return START_NOT_STICKY
        }
        startTunnel(link)
        return START_NOT_STICKY
    }

    private fun startTunnel(link: String) {
        startForegroundNotification()
        val settings = SettingsStore(this).load()

        val config = try {
            XrayConfigBuilder.build(link, settings)
        } catch (e: IllegalArgumentException) {
            fail(e.message ?: "کانفیگ نامعتبر است.")
            return
        }

        val builder = Builder()
            .setSession("VexClient")
            .setMtu(settings.mtu)
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
        try {
            builder.addDnsServer(settings.dns)
        } catch (e: Exception) {
            // Invalid DNS value: keep the system default.
        }
        try {
            // The core's own sockets must bypass the tunnel, otherwise traffic loops.
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            // Package not found: nothing to exclude.
        }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
        if (pfd == null) {
            fail("ساخت اینترفیس VPN ناموفق بود.")
            return
        }
        tun = pfd

        val error = XrayCore.start(config, pfd.fd, filesDir.absolutePath)
        if (error != null) {
            fail(error)
            return
        }
        VpnState.set(true, null)
    }

    private fun stopTunnel() {
        XrayCore.stop()
        tun?.close()
        tun = null
        VpnState.set(false, null)
        stopForeground(true)
        stopSelf()
    }

    private fun fail(message: String) {
        XrayCore.stop()
        tun?.close()
        tun = null
        VpnState.set(false, message)
        stopForeground(true)
        stopSelf()
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VexClient", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("VexClient")
            .setContentText("اتصال در حال اجراست")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    companion object {
        const val ACTION_STOP = "com.vexclient.app.STOP"
        const val EXTRA_LINK = "link"
        private const val CHANNEL_ID = "vex_vpn"
        private const val NOTIF_ID = 1
    }
}
