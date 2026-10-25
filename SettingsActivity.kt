package com.vexclient.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }

        content.addView(header())

        content.addView(section("عمومی"))
        content.addView(toggleRow("اتصال خودکار هنگام باز شدن", "autoConnect", false))
        content.addView(toggleRow("روشن ماندن صفحه هنگام اتصال", "keepScreenOn", true))
        content.addView(toggleRow("نمایش آمار زنده", "showStats", true))

        content.addView(section("مسیریابی و شبکه"))
        content.addView(toggleRow("دور زدن شبکه‌ی محلی (LAN)", "bypassLan", true))
        content.addView(toggleRow("اجازه‌ی اتصال از شبکه‌ی محلی", "allowLan", false))
        content.addView(toggleRow("Mux (چندمسیره)", "mux", false))

        content.addView(section("پیشرفته"))
        content.addView(tv("سرور DNS", 13f, C.GREY))
        val dnsInput = input(store.getString("dns", "1.1.1.1"), InputType.TYPE_CLASS_TEXT)
        content.addView(dnsInput)

        content.addView(tv("MTU (بین 576 و 9000)", 13f, C.GREY))
        val mtuInput = input(store.getInt("mtu", 1500).toString(), InputType.TYPE_CLASS_NUMBER)
        content.addView(mtuInput)

        content.addView(tv("پورت پروکسی محلی (بین 1024 و 65535)", 13f, C.GREY))
        val portInput = input(store.getInt("proxyPort", 10808).toString(), InputType.TYPE_CLASS_NUMBER)
        content.addView(portInput)

        content.addView(button("ذخیره‌ی مقادیر پیشرفته") {
            val dns = dnsInput.text.toString().trim()
            val mtu = mtuInput.text.toString().toIntOrNull()
            val port = portInput.text.toString().toIntOrNull()
            when {
                dns.isEmpty() || !Regex("^[A-Za-z0-9.:\\-]+$").matches(dns) ->
                    toast("DNS معتبر نیست.")
                mtu == null || mtu !in 576..9000 ->
                    toast("MTU باید بین 576 و 9000 باشد.")
                port == null || port !in 1024..65535 ->
                    toast("پورت باید بین 1024 و 65535 باشد.")
                else -> {
                    store.putString("dns", dns)
                    store.putInt("mtu", mtu)
                    store.putInt("proxyPort", port)
                    toast("ذخیره شد")
                }
            }
        })

        content.addView(tv(
            "بعضی تنظیمات پس از فعال شدن هسته‌ی اتصال اعمال می‌شوند.",
            12f, C.GREY
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        })

        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        scroll.setBackgroundDrawable(C.backgroundGradient())
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(6))
        addView(tv("تنظیمات", 22f, C.WHITE, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        addView(tv("بازگشت", 13f, C.TEAL, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rrect(C.CARD, 20f, C.TEAL)
            setOnClickListener { finish() }
        })
    }

    private fun section(label: String): TextView =
        tv(label, 13f, C.TEAL, bold = true).apply {
            setPadding(0, dp(22), 0, dp(8))
        }

    private fun toggleRow(title: String, key: String, default: Boolean): View {
        val toggle = Switch(this).apply {
            isChecked = store.getBool(key, default)
            setOnCheckedChangeListener { _, value -> store.putBool(key, value) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rrect(C.CARD, 14f, C.STROKE)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) }
            addView(tv(title, 14f, C.WHITE).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            addView(toggle)
        }
    }

    private fun input(value: String, type: Int): EditText = EditText(this).apply {
        setText(value)
        inputType = type
        setTextColor(C.WHITE)
        setHintTextColor(C.GREY)
        background = rrect(C.CARD, 12f, C.STROKE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
    }

    private fun button(label: String, onClick: () -> Unit): TextView =
        tv(label, 14f, C.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rrect(C.TEAL_DARK, 14f)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
            setOnClickListener { onClick() }
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
