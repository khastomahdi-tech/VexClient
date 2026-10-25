package com.vexclient.app

import java.lang.reflect.Proxy

/**
 * Bridge to the Xray core packaged as an AAR in app/libs.
 *
 * Calls go through reflection, so the project compiles even when the AAR is absent.
 * The class and method names follow the AndroidLibXrayLite API. Confirm them
 * against the exact AAR version you put in app/libs before relying on it.
 */
object XrayCore {

    private const val CORE_CLASS = "libv2ray.Libv2ray"
    private const val HANDLER_IFACE = "libv2ray.CoreCallbackHandler"

    private var controller: Any? = null

    fun isInstalled(): Boolean = try {
        Class.forName(CORE_CLASS)
        true
    } catch (e: ClassNotFoundException) {
        false
    }

    /** Returns null on success, or a message describing the failure. */
    fun start(configJson: String, tunFd: Int, datDir: String): String? {
        if (!isInstalled()) {
            return "هسته‌ی Xray داخل این بیلد نیست. فایل libv2ray.aar را در پوشه‌ی app/libs بگذار."
        }
        return try {
            val core = Class.forName(CORE_CLASS)
            core.getMethod("initCoreEnv", String::class.java, String::class.java)
                .invoke(null, datDir, "")

            val handlerIface = Class.forName(HANDLER_IFACE)
            val handler = Proxy.newProxyInstance(handlerIface.classLoader, arrayOf(handlerIface)) { _, method, _ ->
                when (method.returnType) {
                    java.lang.Long.TYPE -> 0L
                    java.lang.Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            }

            val ctrl = core.getMethod("newCoreController", handlerIface).invoke(null, handler)
            ctrl.javaClass
                .getMethod("startLoop", String::class.java, Int::class.javaPrimitiveType)
                .invoke(ctrl, configJson, tunFd)
            controller = ctrl
            null
        } catch (e: Exception) {
            "اجرای هسته‌ی Xray ناموفق بود: ${e.cause?.message ?: e.message}"
        }
    }

    fun stop() {
        val ctrl = controller ?: return
        try {
            ctrl.javaClass.getMethod("stopLoop").invoke(ctrl)
        } catch (e: Exception) {
            // The core may already be stopped; nothing else to do.
        }
        controller = null
    }
}
