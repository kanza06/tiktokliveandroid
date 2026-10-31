package com.ombaharbangkit.tiktoklivereader.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model data yang dibuat dari respons JSON server Python (/api/status).
 * Mengikuti bentuk data di server/live_engine.py.
 */

data class CommentItem(
    val id: Long,
    val user: String,
    val text: String,
    val avatar: String,
    val time: Long
)

data class GiftItem(
    val id: Long,
    val user: String,
    val gift: String,
    val count: Int,
    val image: String,
    val diamond: Int,
    val avatar: String,
    val time: Long
)

data class JoinItem(
    val id: Long,
    val names: List<String>,
    val avatars: List<String>,
    val time: Long
)

data class LiveEvent(
    val seq: Long,
    val type: String,
    val time: Long,
    val user: String = "",
    val text: String = "",
    val gift: String = "",
    val count: Int = 0,
    val image: String = "",
    val avatar: String = "",
    val names: List<String> = emptyList()
)

data class PersonInfo(val name: String, val seconds: Long, val total: Int)

data class LiveSnapshot(
    val username: String = "",
    val status: String = "Belum terhubung",
    val ok: Boolean = false,
    val mode: String = "idle",
    val error: String = "",
    val liveSeconds: Long = 0,
    val active: Int = 0,
    val visitors: Int = 0,
    val gifters: Int = 0,
    val likes: Int = 0,
    val follows: Int = 0,
    val shares: Int = 0,
    val comments: List<CommentItem> = emptyList(),
    val gifts: List<GiftItem> = emptyList(),
    val joins: List<JoinItem> = emptyList(),
    val activeList: List<PersonInfo> = emptyList(),
    val visitorsList: List<PersonInfo> = emptyList(),
    val giftersList: List<PersonInfo> = emptyList(),
    val events: List<LiveEvent> = emptyList(),
    val seq: Long = 0,
    val settings: AppSettings = AppSettings()
)

/** Pengaturan aplikasi — sama persis dengan settings.json versi Py. */
data class AppSettings(
    val username: String = "",
    val opacity: Int = 90,
    val fontSize: Int = 14,
    val fontFamily: String = "Segoe UI",
    val theme: String = "dark",
    val speedUnit: String = "kbps",
    val autoConnect: Boolean = true,
    val autoReconnect: Boolean = true,
    val reconnectInterval: Int = 15,
    val serverUrl: String = "",
    /** Warna aksen kustom ("#RRGGBB"); kosong = ikut tema. */
    val accentColor: String = ""
) {
    /** Gelap/terang ditentukan oleh definisi tema (lihat Theme.THEMES). */
    val isDark: Boolean get() = com.ombaharbangkit.tiktoklivereader.ui.Theme.themeDef(theme).dark
}

// ---------------------------------------------------------------------------
object JsonParse {

    private fun JSONObject.s(key: String, def: String = ""): String =
        if (isNull(key)) def else optString(key, def)

    private fun JSONObject.l(key: String, def: Long = 0L): Long = optLong(key, def)

    private fun JSONObject.i(key: String, def: Int = 0): Int = optInt(key, def)

    fun settings(o: JSONObject?): AppSettings {
        if (o == null) return AppSettings()
        return AppSettings(
            username = o.s("username"),
            opacity = o.i("opacity", 90),
            fontSize = o.i("font_size", 14),
            fontFamily = o.s("font_family", "Segoe UI"),
            theme = o.s("theme", "dark"),
            speedUnit = o.s("speed_unit", "kbps"),
            autoConnect = o.optBoolean("auto_connect", true),
            autoReconnect = o.optBoolean("auto_reconnect", true),
            reconnectInterval = o.i("reconnect_interval", 15),
            serverUrl = o.s("server_url")
        )
    }

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it) }
    }

    private fun people(a: JSONArray?): List<PersonInfo> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            PersonInfo(o.s("name"), o.l("seconds"), o.i("total"))
        }
    }

    fun snapshot(o: JSONObject): LiveSnapshot {
        fun arr(key: String): JSONArray? = o.optJSONArray(key)

        val comments = (0 until (arr("comments")?.length() ?: 0)).mapNotNull { i ->
            val c = arr("comments")?.optJSONObject(i) ?: return@mapNotNull null
            CommentItem(c.l("id"), c.s("user"), c.s("text"), c.s("avatar"), c.l("t"))
        }
        val gifts = (0 until (arr("gifts")?.length() ?: 0)).mapNotNull { i ->
            val g = arr("gifts")?.optJSONObject(i) ?: return@mapNotNull null
            GiftItem(g.l("id"), g.s("user"), g.s("gift"), g.i("count", 1), g.s("image"),
                g.i("diamond"), g.s("avatar"), g.l("t"))
        }
        val joins = (0 until (arr("joins")?.length() ?: 0)).mapNotNull { i ->
            val j = arr("joins")?.optJSONObject(i) ?: return@mapNotNull null
            JoinItem(j.l("id"), strings(j.optJSONArray("names")),
                strings(j.optJSONArray("avatars")), j.l("t"))
        }
        val events = (0 until (arr("events")?.length() ?: 0)).mapNotNull { i ->
            val e = arr("events")?.optJSONObject(i) ?: return@mapNotNull null
            LiveEvent(
                seq = e.l("seq"), type = e.s("type"), time = e.l("t"),
                user = e.s("user"), text = e.s("text"), gift = e.s("gift"),
                count = e.i("count"), image = e.s("image"), avatar = e.s("avatar"),
                names = strings(e.optJSONArray("names"))
            )
        }
        return LiveSnapshot(
            username = o.s("username"),
            status = o.s("status", "Belum terhubung"),
            ok = o.optBoolean("ok", false),
            mode = o.s("mode", "idle"),
            error = o.s("error"),
            liveSeconds = o.l("live_seconds"),
            active = o.i("active"),
            visitors = o.i("visitors"),
            gifters = o.i("gifters"),
            likes = o.i("likes"),
            follows = o.i("follows"),
            shares = o.i("shares"),
            comments = comments,
            gifts = gifts,
            joins = joins,
            activeList = people(o.optJSONArray("active_list")),
            visitorsList = people(o.optJSONArray("visitors_list")),
            giftersList = people(o.optJSONArray("gifters_list")),
            events = events,
            seq = o.l("seq"),
            settings = settings(o.optJSONObject("settings"))
        )
    }
}
