package com.vexclient.app

import java.util.concurrent.CopyOnWriteArrayList

/** Shared connection state between the VPN service and the UI. */
object VpnState {

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val listeners = CopyOnWriteArrayList<(Boolean, String?) -> Unit>()

    fun set(on: Boolean, error: String?) {
        connected = on
        lastError = error
        listeners.forEach { it(on, error) }
    }

    fun addListener(listener: (Boolean, String?) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean, String?) -> Unit) {
        listeners.remove(listener)
    }
}
