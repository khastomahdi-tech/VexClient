package com.vexclient.app

import android.content.Context

data class AppSettings(
    val autoConnect: Boolean,
    val keepScreenOn: Boolean,
    val showStats: Boolean,
    val bypassLan: Boolean,
    val allowLan: Boolean,
    val mux: Boolean,
    val dns: String,
    val mtu: Int,
    val proxyPort: Int
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getBool(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun putBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    fun load(): AppSettings = AppSettings(
        autoConnect = getBool("autoConnect", false),
        keepScreenOn = getBool("keepScreenOn", true),
        showStats = getBool("showStats", true),
        bypassLan = getBool("bypassLan", true),
        allowLan = getBool("allowLan", false),
        mux = getBool("mux", false),
        dns = getString("dns", "1.1.1.1"),
        mtu = getInt("mtu", 1500),
        proxyPort = getInt("proxyPort", 10808)
    )
}
