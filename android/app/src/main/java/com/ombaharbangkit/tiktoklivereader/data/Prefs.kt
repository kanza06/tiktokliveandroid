package com.ombaharbangkit.tiktoklivereader.data

import android.content.Context
import com.ombaharbangkit.tiktoklivereader.model.AppSettings

/**
 * Pengaturan tersimpan di HP (SharedPreferences).
 * Nilai yang sama dipakai server lewat POST /api/settings, jadi hasil akhirnya
 * selalu identik dengan versi Py (settings.json).
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("tt_live_reader", Context.MODE_PRIVATE)

    /**
     * Alamat server terakhir.
     * Default = server Fly.io, jadi APK langsung tersambung tanpa setting IP.
     * Kosong = paksa app mencari server di Wi-Fi.
     */
    var serverUrl: String
        get() = sp.getString("server_url", ServerClient.FLY_URL) ?: ServerClient.FLY_URL
        set(value) = sp.edit().putString("server_url", value.trim()).apply()

    /** true bila user mengisi alamat server secara manual (tidak ditimpa pencarian otomatis). */
    var manualServer: Boolean
        get() = sp.getBoolean("server_manual", false)
        set(value) = sp.edit().putBoolean("server_manual", value).apply()

    /** Lupakan server tersimpan supaya app mencari ulang di jaringan. */
    fun forgetServer() {
        sp.edit().remove("server_url").putBoolean("server_manual", false).apply()
    }

    /** Opsional — hanya bila server dijalankan dengan TTL_API_KEY. */
    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(value) = sp.edit().putString("api_key", value.trim()).apply()

    var theme: String
        get() = sp.getString("theme", "dark") ?: "dark"
        set(value) = sp.edit().putString("theme", value).apply()

    var fontSize: Int
        get() = sp.getInt("font_size", 14)
        set(value) = sp.edit().putInt("font_size", value).apply()

    var fontFamily: String
        get() = sp.getString("font_family", "Segoe UI") ?: "Segoe UI"
        set(value) = sp.edit().putString("font_family", value).apply()

    /** Username terakhir yang dipakai (dikirim server lewat /api/status). */
    var username: String
        get() = sp.getString("username", "") ?: ""
        set(value) = sp.edit().putString("username", value).apply()

    var opacity: Int
        get() = sp.getInt("opacity", 90)
        set(value) = sp.edit().putInt("opacity", value).apply()

    var speedUnit: String
        get() = sp.getString("speed_unit", "kbps") ?: "kbps"
        set(value) = sp.edit().putString("speed_unit", value).apply()

    /** Gambar latar pilihan user (URI galeri); kosong = gradasi tema. */
    var wallpaperUri: String
        get() = sp.getString("wallpaper_uri", "") ?: ""
        set(value) = sp.edit().putString("wallpaper_uri", value).apply()

    /** Warna aksen kustom "#RRGGBB"; kosong = ikut tema. */
    var accentColor: String
        get() = sp.getString("accent_color", "") ?: ""
        set(value) = sp.edit().putString("accent_color", value.trim()).apply()



    /** Konversi ke model AppSettings untuk dipakai UI. */
    fun toSettings(): AppSettings = AppSettings(
        username = username,
        theme = theme,
        fontSize = fontSize,
        fontFamily = fontFamily,
        opacity = opacity,
        speedUnit = speedUnit,
        serverUrl = serverUrl,
        accentColor = accentColor
    )

    fun applyServer(settings: AppSettings) {
        // Server adalah sumber kebenaran setelah terhubung (sama dengan settings.json Py)
        theme = settings.theme
        fontSize = settings.fontSize
        fontFamily = settings.fontFamily
        opacity = settings.opacity
        speedUnit = settings.speedUnit
        if (settings.username.isNotEmpty()) username = settings.username
        // Warna aksen kustom tetap milik HP (server tidak mengenal setting ini)
    }

    /** Patch yang dikirim ke POST /api/settings. */
    fun toServerPatch(): Map<String, Any> = mapOf(
        "theme" to theme,
        "font_size" to fontSize,
        "font_family" to fontFamily,
        "opacity" to opacity,
        "speed_unit" to speedUnit
    )
}
