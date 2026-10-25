package com.vexclient.app

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

data class ParsedLink(
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    val raw: String
)

sealed class ParseResult {
    data class Node(val link: ParsedLink) : ParseResult()
    data class Subscription(val url: String) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

object LinkParser {

    private val nodeSchemes = listOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "tuic", "socks")

    fun parse(input: String): ParseResult {
        val text = input.trim()
        if (text.isEmpty()) return ParseResult.Error("متنی برای ایمپورت پیدا نشد.")

        val lower = text.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            val host = Uri.parse(text).host
            return if (host.isNullOrEmpty()) {
                ParseResult.Error("لینک اشتراک آدرس معتبر ندارد.")
            } else {
                ParseResult.Subscription(text)
            }
        }

        val scheme = nodeSchemes.firstOrNull { lower.startsWith("$it://") }
            ?: return ParseResult.Error(
                "این متن لینک معتبری نیست. باید با vless، vmess، trojan، ss یا hysteria2 شروع شود، یا لینک اشتراک https باشد."
            )

        return if (scheme == "vmess") parseVmess(text) else parseUri(scheme, text)
    }

    private fun parseVmess(text: String): ParseResult {
        return try {
            val json = JSONObject(String(Base64.decode(text.substring(8), Base64.DEFAULT)))
            val host = json.optString("add")
            val port = json.optString("port").toIntOrNull() ?: 0
            if (host.isEmpty() || port !in 1..65535 || json.optString("id").isEmpty()) {
                ParseResult.Error("کانفیگ VMess ناقص است (آدرس، پورت یا UUID ندارد).")
            } else {
                val name = json.optString("ps").ifEmpty { host }
                ParseResult.Node(ParsedLink("vmess", name, host, port, text))
            }
        } catch (e: Exception) {
            ParseResult.Error("کانفیگ VMess خراب است و قابل خواندن نیست.")
        }
    }

    private fun parseUri(scheme: String, text: String): ParseResult {
        val uri = try {
            Uri.parse(text)
        } catch (e: Exception) {
            return ParseResult.Error("ساختار لینک اشتباه است.")
        }

        val host = uri.host ?: ""
        val port = uri.port
        if (host.isEmpty()) return ParseResult.Error("آدرس سرور در لینک پیدا نشد.")
        if (port !in 1..65535) return ParseResult.Error("پورت سرور معتبر نیست.")

        if (scheme == "vless" && uri.userInfo.isNullOrEmpty()) {
            return ParseResult.Error("UUID در کانفیگ VLESS پیدا نشد.")
        }
        if (scheme == "trojan" && uri.userInfo.isNullOrEmpty()) {
            return ParseResult.Error("رمز در کانفیگ Trojan پیدا نشد.")
        }

        val name = uri.fragment?.takeIf { it.isNotBlank() } ?: host
        val protocol = if (scheme == "hy2") "hysteria2" else scheme
        return ParseResult.Node(ParsedLink(protocol, name, host, port, text))
    }
}
