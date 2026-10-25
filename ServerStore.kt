package com.vexclient.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Server(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val rawLink: String
)

class ServerStore(context: Context) {

    private val prefs = context.getSharedPreferences("servers", Context.MODE_PRIVATE)

    fun all(): List<Server> {
        val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Server(
                o.getString("id"),
                o.getString("name"),
                o.getString("protocol"),
                o.getString("host"),
                o.getInt("port"),
                o.getString("raw")
            )
        }
    }

    fun selectedId(): String? = prefs.getString("selected", null)

    fun selected(): Server? = all().firstOrNull { it.id == selectedId() }

    fun select(id: String) {
        prefs.edit().putString("selected", id).apply()
    }

    fun add(link: ParsedLink): Server {
        val server = Server(UUID.randomUUID().toString(), link.name, link.protocol, link.host, link.port, link.raw)
        save(all() + server)
        return server
    }

    fun remove(id: String) {
        save(all().filter { it.id != id })
        if (selectedId() == id) prefs.edit().remove("selected").apply()
    }

    fun setSubscription(url: String) {
        prefs.edit().putString("subscription", url).apply()
    }

    fun subscription(): String? = prefs.getString("subscription", null)

    private fun save(list: List<Server>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("protocol", s.protocol)
                    .put("host", s.host)
                    .put("port", s.port)
                    .put("raw", s.rawLink)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
