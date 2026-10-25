package com.vexclient.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private companion object {
        const val REQ_VPN = 1001
    }

    private lateinit var servers: ServerStore
    private lateinit var settingsStore: SettingsStore
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var power: PowerButton
    private lateinit var statusText: TextView
    private lateinit var subText: TextView
    private lateinit var statsBox: LinearLayout
    private lateinit var downText: TextView
    private lateinit var upText: TextView
    private lateinit var timeText: TextView
    private lateinit var serverList: LinearLayout

    private var connected = false
    private var connectedAt = 0L
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTick = 0L
    private var pendingLink: String? = null

    private val vpnListener: (Boolean, String?) -> Unit = { on, error ->
        handler.post { onVpnStateChanged(on, error) }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!connected) return
            val now = System.currentTimeMillis()
            val uid = applicationInfo.uid
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            val seconds = (now - lastTick).coerceAtLeast(1L) / 1000.0

            downText.text = if (rx >= 0 && lastRx >= 0) Format.speed((rx - lastRx) / seconds) else "--"
            upText.text = if (tx >= 0 && lastTx >= 0) Format.speed((tx - lastTx) / seconds) else "--"

            lastRx = rx
            lastTx = tx
            lastTick = now
            timeText.text = Format.duration(now - connectedAt)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        servers = ServerStore(this)
        settingsStore = SettingsStore(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(buildUi())
        renderServers()
        applySelectedState()
    }

    override fun onResume() {
        super.onResume()
        VpnState.addListener(vpnListener)
        if (VpnState.connected) {
            markConnected()
        } else {
            markDisconnected(null)
        }
    }

    override fun onPause() {
        VpnState.removeListener(vpnListener)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            val link = pendingLink
            pendingLink = null
            if (resultCode == RESULT_OK && link != null) {
                startTunnel(link)
            } else {
                showStatusError("اجازه‌ی VPN داده نشد.")
            }
        }
    }

    private fun onVpnStateChanged(on: Boolean, error: String?) {
        if (on) markConnected() else markDisconnected(error)
    }

    private fun updateScreenFlag() {
        if (connected && settingsStore.getBool("keepScreenOn", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            background = C.backgroundGradient()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(tv("VexClient", 22f, C.WHITE, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        header.addView(pillButton("تنظیمات") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, dp(12))
        }
        power = PowerButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(180))
            setOnClickListener { onPowerClicked() }
        }
        hero.addView(power)

        statusText = tv("قطع است", 18f, C.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        subText = tv("سروری انتخاب نشده", 13f, C.GREY).apply { gravity = Gravity.CENTER }
        hero.addView(statusText)
        hero.addView(subText)
        root.addView(hero)

        downText = TextView(this)
        upText = TextView(this)
        timeText = TextView(this)
        statsBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rrect(C.CARD, 18f, C.STROKE)
            setPadding(dp(8), dp(14), dp(8), dp(14))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        statsBox.addView(statCell("دانلود", downText))
        statsBox.addView(statCell("آپلود", upText))
        statsBox.addView(statCell("مدت اتصال", timeText))
        root.addView(statsBox)

        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        listHeader.addView(tv("سرورها", 15f, C.WHITE, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        listHeader.addView(pillButton("افزودن") { showAddDialog() })
        root.addView(listHeader)

        serverList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            addView(serverList)
        }
        root.addView(scroll)

        return root
    }

    private fun statCell(label: String, value: TextView): View {
        value.text = "--"
        value.textSize = 15f
        value.setTextColor(C.WHITE)
        value.gravity = Gravity.CENTER
        value.typeface = Typeface.DEFAULT_BOLD
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            addView(tv(label, 11f, C.GREY).apply { gravity = Gravity.CENTER })
            addView(value)
        }
    }

    private fun pillButton(label: String, onClick: () -> Unit): TextView =
        tv(label, 13f, C.TEAL, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rrect(C.CARD, 20f, C.TEAL)
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
            setOnClickListener { onClick() }
        }

    private fun onPowerClicked() {
        if (connected) {
            stopTunnel()
            return
        }
        val server = servers.selected()
        if (server == null) {
            showStatusError("ابتدا یک سرور اضافه و انتخاب کن.")
            return
        }
        if (!XrayCore.isInstalled()) {
            showStatusError("هسته‌ی Xray داخل این بیلد نیست.")
            return
        }
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            pendingLink = server.rawLink
            startActivityForResult(permissionIntent, REQ_VPN)
        } else {
            startTunnel(server.rawLink)
        }
    }

    private fun startTunnel(link: String) {
        statusText.text = "در حال اتصال..."
        statusText.setTextColor(C.GREY)
        val intent = Intent(this, VpnTunnelService::class.java).putExtra(VpnTunnelService.EXTRA_LINK, link)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun stopTunnel() {
        val intent = Intent(this, VpnTunnelService::class.java).setAction(VpnTunnelService.ACTION_STOP)
        startService(intent)
    }

    private fun markConnected() {
        if (!connected) {
            connected = true
            connectedAt = System.currentTimeMillis()
            lastTick = connectedAt
            lastRx = TrafficStats.getUidRxBytes(applicationInfo.uid)
            lastTx = TrafficStats.getUidTxBytes(applicationInfo.uid)
            handler.post(ticker)
        }
        power.on = true
        statusText.text = "متصل است"
        statusText.setTextColor(C.TEAL)
        subText.text = servers.selected()?.name ?: ""
        subText.setTextColor(C.GREY)
        statsBox.visibility = if (settingsStore.getBool("showStats", true)) View.VISIBLE else View.GONE
        updateScreenFlag()
    }

    private fun markDisconnected(error: String?) {
        connected = false
        handler.removeCallbacks(ticker)
        power.on = false
        statsBox.visibility = View.GONE
        downText.text = "--"
        upText.text = "--"
        timeText.text = "00:00:00"
        updateScreenFlag()
        if (error != null) showStatusError(error) else applySelectedState()
    }

    private fun applySelectedState() {
        if (connected) return
        statusText.text = "قطع است"
        statusText.setTextColor(C.WHITE)
        subText.text = servers.selected()?.let {
            "${it.name}  ·  ${it.protocol.uppercase(Locale.US)}"
        } ?: "سروری انتخاب نشده"
        subText.setTextColor(C.GREY)
    }

    private fun showStatusError(message: String) {
        statusText.text = "اتصال برقرار نشد"
        statusText.setTextColor(C.DANGER)
        subText.text = message
        subText.setTextColor(C.DANGER)
    }

    private fun renderServers() {
        serverList.removeAllViews()
        val list = servers.all()
        if (list.isEmpty()) {
            serverList.addView(tv("هنوز سروری نداری. با دکمه‌ی افزودن لینک کانفیگ یا اشتراک را اضافه کن.", 13f, C.GREY).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(30), dp(8), 0)
            })
            return
        }
        val selectedId = servers.selectedId()
        list.forEach { serverList.addView(serverRow(it, it.id == selectedId)) }
    }

    private fun serverRow(s: Server, selected: Boolean): View {
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        info.addView(tv(s.name, 15f, C.WHITE, bold = true))
        info.addView(tv("${s.host}:${s.port}", 12f, C.GREY))

        val tag = tv(s.protocol.uppercase(Locale.US), 11f, C.TEAL, bold = true).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rrect(C.TEAL_SOFT, 8f)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rrect(if (selected) C.CARD_SEL else C.CARD, 16f, if (selected) C.TEAL else C.STROKE)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
            setOnClickListener {
                servers.select(s.id)
                renderServers()
                applySelectedState()
            }
            setOnLongClickListener {
                confirmDelete(s)
                true
            }
            addView(info)
            addView(tag)
        }
    }

    private fun confirmDelete(s: Server) {
        AlertDialog.Builder(this)
            .setTitle("حذف سرور")
            .setMessage("«${s.name}» حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                servers.remove(s.id)
                renderServers()
                applySelectedState()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = "لینک کانفیگ یا لینک اشتراک"
            setTextColor(C.WHITE)
            setHintTextColor(C.GREY)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }
        clipboardText()?.let { input.setText(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle("افزودن سرور")
            .setView(input)
            .setPositiveButton("افزودن", null)
            .setNegativeButton("انصراف", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (handleInput(input.text.toString(), input)) dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun handleInput(raw: String, input: EditText): Boolean {
        return when (val result = LinkParser.parse(raw)) {
            is ParseResult.Error -> {
                input.error = result.message
                false
            }
            is ParseResult.Subscription -> {
                servers.setSubscription(result.url)
                Toast.makeText(this, "لینک اشتراک ذخیره شد", Toast.LENGTH_SHORT).show()
                true
            }
            is ParseResult.Node -> {
                val added = servers.add(result.link)
                servers.select(added.id)
                renderServers()
                applySelectedState()
                Toast.makeText(this, "سرور اضافه شد", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun clipboardText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }
}
