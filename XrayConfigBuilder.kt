package com.vexclient.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/** Converts a share link (vless, vmess, trojan, ss) into an Xray JSON configuration. */
object XrayConfigBuilder {

    private val LAN_CIDRS = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8")

    private class Parts(
        val userInfo: String,
        val host: String,
        val port: Int,
        val params: Map<String, String>
    )

    fun build(rawLink: String, settings: AppSettings): String {
        val proxy = parseOutbound(rawLink.trim(), settings)

        val inbound = JSONObject()
            .put("tag", "socks")
            .put("listen", "127.0.0.1")
            .put("port", settings.proxyPort)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true))
            .put("sniffing", JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls")))

        val outbounds = JSONArray()
            .put(proxy)
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))

        val rules = JSONArray()
        if (settings.bypassLan) {
            rules.put(JSONObject()
                .put("type", "field")
                .put("ip", JSONArray(LAN_CIDRS))
                .put("outboundTag", "direct"))
        }

        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", outbounds)
            .put("dns", JSONObject().put("servers", JSONArray().put(settings.dns)))
            .put("routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", rules))
        return root.toString()
    }

    private fun parseOutbound(link: String, s: AppSettings): JSONObject {
        val lower = link.lowercase()
        return when {
            lower.startsWith("vless://") -> vless(link, s)
            lower.startsWith("vmess://") -> vmess(link, s)
            lower.startsWith("trojan://") -> trojan(link, s)
            lower.startsWith("ss://") -> shadowsocks(link, s)
            else -> throw IllegalArgumentException(
                "این پروتکل در هسته‌ی Xray پشتیبانی نمی‌شود. از VLESS، VMess، Trojan یا Shadowsocks استفاده کن."
            )
        }
    }

    private fun vless(link: String, s: AppSettings): JSONObject {
        val p = splitUri(link)
        if (p.userInfo.isEmpty()) throw IllegalArgumentException("UUID در کانفیگ VLESS پیدا نشد.")
        val user = JSONObject().put("id", p.userInfo).put("encryption", "none")
        p.params["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        val server = JSONObject()
            .put("address", p.host)
            .put("port", p.port)
            .put("users", JSONArray().put(user))
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            .put("streamSettings", stream(p.params, "none"))
            .put("mux", JSONObject().put("enabled", s.mux))
    }

    private fun trojan(link: String, s: AppSettings): JSONObject {
        val p = splitUri(link)
        if (p.userInfo.isEmpty()) throw IllegalArgumentException("رمز در کانفیگ Trojan پیدا نشد.")
        val server = JSONObject()
            .put("address", p.host)
            .put("port", p.port)
            .put("password", p.userInfo)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", stream(p.params, "tls"))
            .put("mux", JSONObject().put("enabled", s.mux))
    }

    private fun vmess(link: String, s: AppSettings): JSONObject {
        val json = try {
            JSONObject(String(Base64.decode(link.substring(8), Base64.DEFAULT)))
        } catch (e: Exception) {
            throw IllegalArgumentException("کانفیگ VMess خراب است و قابل خواندن نیست.")
        }
        val host = json.optString("add")
        val port = json.optString("port").toIntOrNull() ?: 0
        val id = json.optString("id")
        if (host.isEmpty() || port !in 1..65535 || id.isEmpty()) {
            throw IllegalArgumentException("کانفیگ VMess ناقص است (آدرس، پورت یا UUID ندارد).")
        }
        val user = JSONObject()
            .put("id", id)
            .put("alterId", json.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", "auto")
        val server = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("users", JSONArray().put(user))
        val params = mapOf(
            "type" to json.optString("net", "tcp"),
            "security" to if (json.optString("tls") == "tls") "tls" else "none",
            "host" to json.optString("host"),
            "path" to json.optString("path"),
            "sni" to json.optString("sni")
        )
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            .put("streamSettings", stream(params, "none"))
            .put("mux", JSONObject().put("enabled", s.mux))
    }

    private fun shadowsocks(link: String, s: AppSettings): JSONObject {
        val body = link.substring(5).substringBefore('#')
        val methodPass: String
        val hostPort: String
        if (body.contains('@')) {
            val userPart = body.substringBeforeLast('@')
            hostPort = body.substringAfterLast('@')
            methodPass = decodeBase64(userPart) ?: userPart
        } else {
            val decoded = decodeBase64(body)
                ?: throw IllegalArgumentException("کانفیگ Shadowsocks خراب است.")
            methodPass = decoded.substringBeforeLast('@')
            hostPort = decoded.substringAfterLast('@')
        }
        val method = methodPass.substringBefore(':')
        val password = methodPass.substringAfter(':')
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: 0
        if (method.isEmpty() || host.isEmpty() || port !in 1..65535) {
            throw IllegalArgumentException("کانفیگ Shadowsocks ناقص است.")
        }
        val server = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("method", method)
            .put("password", password)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("mux", JSONObject().put("enabled", s.mux))
    }

    private fun stream(p: Map<String, String>, defaultSecurity: String): JSONObject {
        val network = when (p["type"]?.lowercase()) {
            "ws" -> "ws"
            "grpc" -> "grpc"
            "h2", "http" -> "http"
            "httpupgrade" -> "httpupgrade"
            else -> "tcp"
        }
        val so = JSONObject().put("network", network)
        when (network) {
            "ws" -> so.put("wsSettings", JSONObject()
                .put("path", p["path"] ?: "/")
                .put("headers", JSONObject().put("Host", p["host"] ?: "")))
            "grpc" -> so.put("grpcSettings", JSONObject()
                .put("serviceName", p["serviceName"] ?: p["path"] ?: ""))
            "http" -> so.put("httpSettings", JSONObject()
                .put("host", JSONArray().put(p["host"] ?: ""))
                .put("path", p["path"] ?: "/"))
            "httpupgrade" -> so.put("httpupgradeSettings", JSONObject()
                .put("path", p["path"] ?: "/")
                .put("host", p["host"] ?: ""))
        }

        val security = (p["security"] ?: defaultSecurity).lowercase()
        when (security) {
            "tls" -> so.put("security", "tls").put("tlsSettings", JSONObject()
                .put("serverName", p["sni"] ?: p["host"] ?: "")
                .put("allowInsecure", false)
                .put("fingerprint", p["fp"] ?: "chrome")
                .put("alpn", JSONArray().put("h2").put("http/1.1")))
            "reality" -> {
                val publicKey = p["pbk"] ?: ""
                if (publicKey.isEmpty()) throw IllegalArgumentException("کلید عمومی (pbk) در کانفیگ Reality پیدا نشد.")
                so.put("security", "reality").put("realitySettings", JSONObject()
                    .put("serverName", p["sni"] ?: "")
                    .put("fingerprint", p["fp"] ?: "chrome")
                    .put("publicKey", publicKey)
                    .put("shortId", p["sid"] ?: "")
                    .put("spiderX", p["spx"] ?: "/"))
            }
            else -> so.put("security", "none")
        }
        return so
    }

    private fun splitUri(link: String): Parts {
        val noScheme = link.substringAfter("://")
        val main = noScheme.substringBefore('#')
        val authority = main.substringBefore('?')
        val query = main.substringAfter('?', "")

        val userInfo = authority.substringBeforeLast('@', "")
        val hostPort = authority.substringAfterLast('@')
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').toIntOrNull()
            ?: throw IllegalArgumentException("پورت سرور معتبر نیست.")
        if (host.isEmpty()) throw IllegalArgumentException("آدرس سرور در لینک پیدا نشد.")
        if (port !in 1..65535) throw IllegalArgumentException("پورت سرور معتبر نیست.")

        val params = query.split('&')
            .filter { it.contains('=') }
            .associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                    URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
        return Parts(URLDecoder.decode(userInfo, "UTF-8"), host, port, params)
    }

    private fun decodeBase64(value: String): String? {
        return try {
            String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        } catch (e: Exception) {
            try {
                String(Base64.decode(value, Base64.DEFAULT))
            } catch (e2: Exception) {
                null
            }
        }
    }
}
