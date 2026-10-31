package com.ombaharbangkit.tiktoklivereader.data

import com.ombaharbangkit.tiktoklivereader.model.AppSettings
import com.ombaharbangkit.tiktoklivereader.model.JsonParse
import com.ombaharbangkit.tiktoklivereader.model.LiveSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Klien HTTP ke Python Engine (server_app.py).
 *
 * Endpoint yang dipakai sama dengan yang didokumentasikan di server/app.py:
 *   GET /api/status?since=SEQ   GET /api/settings   POST /api/settings
 *   GET /api/connect?username=  GET /api/disconnect GET /api/demo
 */
class ServerClient(baseUrl: String, apiKey: String = "") {

    @Volatile
    var baseUrl: String = normalize(baseUrl)

    @Volatile
    var apiKey: String = apiKey.trim()

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Server publik di Fly.io (dipakai otomatis sebelum pencarian di Wi-Fi). */
        const val FLY_URL = "https://tiktokliveandroid.fly.dev"

        /** true bila URL memakai skema https (server publik / Fly). */
        fun isHttps(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

        /** Terima "192.168.0.104", "192.168.0.104:8000", "http://...", "https://..." */
        fun normalize(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return ""
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            s = s.trimEnd('/')
            val afterScheme = s.substringAfter("://")
            // https (Fly/domain) sudah memakai port 443 — JANGAN tambah :8000.
            // Hanya alamat LAN biasa (http tanpa port) yang memakai port 8000.
            if (!afterScheme.contains(':') && !isHttps(s)) s += ":8000"
            return s
        }

        /**
         * URL gambar lewat proxy server: "/api/proxy?url=<encoded>"
         *
         * Bentuk query dipakai (bukan /img/<url>) karena rute path merusak URL
         * bertanda tangan CDN TikTok (protokol tergandakan + query terpotong)
         * sehingga server membalas 403. Bentuk query sudah diuji 200 OK.
         *  - "http(s)://cdn.../x.png"  -> gambar dari internet
         *  - "/imgc/x.png"             -> gambar cache lokal di server
         */
        fun proxyUrl(baseUrl: String, imageUrl: String): String {
            if (imageUrl.isBlank()) return ""
            val base = baseUrl.trimEnd('/')
            return "$base/api/proxy?url=" + URLEncoder.encode(imageUrl, "UTF-8")
        }
    }

    private fun Request.Builder.authed(): Request.Builder {
        if (apiKey.isNotEmpty()) header("X-API-Key", apiKey)
        return this
    }

    private fun get(path: String): JSONObject {
        val req = Request.Builder().url(baseUrl + path).authed().get().build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ServerException("HTTP ${res.code}: ${body.take(120)}")
            return JSONObject(body)
        }
    }

    class ServerException(message: String) : Exception(message)

    suspend fun status(since: Long, meta: Boolean = false): LiveSnapshot = withContext(Dispatchers.IO) {
        JsonParse.snapshot(get("/api/status?since=$since&meta=" + if (meta) 1 else 0))
    }

    suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        JsonParse.settings(get("/api/settings"))
    }

    suspend fun saveSettings(patch: Map<String, Any>): AppSettings = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { patch.forEach { (k, v) -> put(k, v) } }
            .toString().toRequestBody(JSON)
        val req = Request.Builder().url(baseUrl + "/api/settings").authed().post(body).build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ServerException("HTTP ${res.code}: ${text.take(120)}")
            JsonParse.settings(JSONObject(text).optJSONObject("settings"))
        }
    }

    suspend fun connect(username: String): String = withContext(Dispatchers.IO) {
        val o = get("/api/connect?username=" + URLEncoder.encode(username, "UTF-8"))
        o.optString("message", o.optString("status"))
    }

    suspend fun disconnect(): String = withContext(Dispatchers.IO) {
        get("/api/disconnect").optString("status", "Terputus")
    }



    suspend fun health(): JSONObject = withContext(Dispatchers.IO) { get("/api/health") }

    /** URL https publik (tunnel) bila server mengaktifkannya. */
    suspend fun tunnelUrl(): String? = withContext(Dispatchers.IO) {
        val o = get("/api/tunnel")
        if (o.isNull("url")) null else o.optString("url").ifBlank { null }
    }
}
