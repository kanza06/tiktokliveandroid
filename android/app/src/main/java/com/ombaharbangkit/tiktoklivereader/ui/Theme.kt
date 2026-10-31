package com.ombaharbangkit.tiktoklivereader.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.ombaharbangkit.tiktoklivereader.R
import com.ombaharbangkit.tiktoklivereader.model.AppSettings

/**
 * Warna & gaya runtime — nilai persis sama dengan THEMES di main.py:
 *   dark : bg 18,18,23 | panel #1C1C24 | panel2 #252530 | text #EDEDF2
 *          muted #A8A8BA | accent #25F4EE | accent2 #E8204F | border #2E2E3B
 *   light: bg 245,246,250 | panel #FFFFFF | panel2 #EEF0F6 | text #1A1A22
 *          muted #5C5C6E | accent #0E8F8A | accent2 #D91E48 | border #DCDDE6
 */
object Theme {

    class Palette(
        val bg: Int, val panel: Int, val panel2: Int, val text: Int, val muted: Int,
        val accent: Int, val accent2: Int, val border: Int, val input: Int, val dark: Boolean
    )

    /* ------------------------------------------------------------------
     * Koleksi tema. "dark"/"light" = tema lama (kompatibel settings.json),
     * sisanya tema tambahan dengan warna berbeda.
     * ------------------------------------------------------------------ */
    class ThemeDef(
        val id: String, val label: String, val emoji: String,
        val bg: Int, val panel: Int, val panel2: Int, val text: Int, val muted: Int,
        val accent: Int, val accent2: Int, val border: Int, val input: Int, val dark: Boolean,
        /** buble komentar (drawable XML) + warna isinya */
        val bubbleRes: Int = 0, val bubbleColor: Int = 0, val bubbleStroke: Int = 0,
        /** style XML lengkap (Theme.KawaiiMidnight dll) */
        val styleRes: Int = 0
    )

    val THEMES: List<ThemeDef> = listOf(
        ThemeDef("dark", "Gelap", "🌙", Color.rgb(18, 18, 23), c("#1C1C24"), c("#252530"),
            c("#EDEDF2"), c("#A8A8BA"), c("#25F4EE"), c("#E8204F"), c("#2E2E3B"), c("#22222C"), true),
        ThemeDef("light", "Terang", "☀", Color.rgb(245, 246, 250), c("#FFFFFF"), c("#EEF0F6"),
            c("#1A1A22"), c("#5C5C6E"), c("#0E8F8A"), c("#D91E48"), c("#DCDDE6"), c("#FFFFFF"), false),
        ThemeDef("amoled", "AMOLED", "🖤", c("#000000"), c("#0B0B0F"), c("#15151C"),
            c("#F4F4F8"), c("#8E8EA0"), c("#00E5FF"), c("#FF2D55"), c("#1F1F28"), c("#101017"), true),
        ThemeDef("tokyo", "Tokyo", "🌃", c("#10121B"), c("#171A26"), c("#232741"),
            c("#E4E8FF"), c("#9AA3C7"), c("#7AA2F7"), c("#BB9AF7"), c("#2C3350"), c("#1B1F2E"), true),
        ThemeDef("tiktok", "TikTok", "🎵", c("#0A0A0C"), c("#141418"), c("#1E1E24"),
            c("#FFFFFF"), c("#9B9BA8"), c("#25F4EE"), c("#FE2C55"), c("#2A2A33"), c("#17171C"), true),
        ThemeDef("sunset", "Sunset", "🌇", c("#1C1017"), c("#25151E"), c("#351D29"),
            c("#FFEDE4"), c("#C9A79A"), c("#FF9F5A"), c("#FF5E7E"), c("#452736"), c("#2A1720"), true),
        ThemeDef("forest", "Hutan", "🌿", c("#0E1712"), c("#14201A"), c("#1D2E25"),
            c("#E6F5EC"), c("#96B5A4"), c("#3DDC84"), c("#FFB86B"), c("#263A2F"), c("#16211B"), true),
        ThemeDef("paper", "Kertas", "📄", c("#F7F4EC"), c("#FFFFFF"), c("#EFEAE0"),
            c("#2B2A26"), c("#6E6A60"), c("#0F766E"), c("#B4232C"), c("#DED8CA"), c("#FFFFFF"), false),
        ThemeDef("mint", "Mint", "🍃", c("#EFFBF6"), c("#FFFFFF"), c("#DFF3EA"),
            c("#12302A"), c("#5A7A70"), c("#0E9F6E"), c("#D6336C"), c("#CBE7DC"), c("#FFFFFF"), false),
        ThemeDef("sky", "Langit", "☁", c("#EEF4FF"), c("#FFFFFF"), c("#E1EAFB"),
            c("#16233A"), c("#5B6B85"), c("#2563EB"), c("#DB2777"), c("#CFDCF2"), c("#FFFFFF"), false),

        // ---- 4 tema XML (nilai warna ada di res/values/colors_themes.xml,
        //      drawable di bg_<kode>_frame/_panel/_bubble, style di styles_themes.xml)
        ThemeDef("kawaii", "Kawaii Midnight", "🌸", c("#141019"), c("#1D1726"), c("#2A2136"),
            c("#F6EFFF"), c("#B7A7CE"), c("#FFA6D5"), c("#C8A2FF"), c("#3A2F4A"), c("#2A2136"), true,
            R.drawable.bg_km_bubble, c("#241C30"), c("#453856"), R.style.Theme_KawaiiMidnight),
        ThemeDef("cyberneon", "Cyber Neon", "⚡", c("#05070C"), c("#0B1118"), c("#101B26"),
            c("#E6FBFF"), c("#7E93A3"), c("#00FFC6"), c("#FF2E97"), c("#16303A"), c("#101B26"), true,
            R.drawable.bg_cn_bubble, c("#0E1A22"), c("#1B3E4C"), R.style.Theme_CyberNeon),
        ThemeDef("matrik", "Matrik", "🟩", c("#000000"), c("#050A05"), c("#0B140B"),
            c("#B9FFC4"), c("#5F9E6B"), c("#3BFF6F"), c("#9BFF3B"), c("#12351C"), c("#0B140B"), true,
            R.drawable.bg_mx_bubble, c("#07120A"), c("#1C4E2A"), R.style.Theme_Matrik),
        ThemeDef("neonkawaii", "Neon Kawaii Dark", "💜", c("#120A17"), c("#1B0F25"), c("#281438"),
            c("#FFF0FB"), c("#C79BD8"), c("#FF5FD2"), c("#5FF3FF"), c("#3A1F4D"), c("#281438"), true,
            R.drawable.bg_nk_bubble, c("#22112E"), c("#4A2A60"), R.style.Theme_NeonKawaiiDark),

        // Bubble ber-LED berjalan (NeonBubbleView): aksen cyan-magenta berputar
        ThemeDef("neondark", "Dark Neon", "🔮", c("#06070C"), c("#0A0D16"), c("#121A2B"),
            c("#E9F5FF"), c("#7C8DA6"), c("#00E5FF"), c("#FF00C8"), c("#16233A"), c("#0F1521"), true),

        /* ------------------------------------------------------------------
         * Tambahan tema populer (referensi palet: Dracula, Nord, Synthwave,
         * Vaporwave, Cyberpunk 2077, Rosé Pine, Gruvbox, One Dark, Monokai,
         * Solarized, Sakura, Lavender, Coffee, Ocean).
         * Urutan argumen: id, label, emoji, bg, panel, panel2, text, muted,
         *                 accent, accent2, border, input, dark
         * ------------------------------------------------------------------ */
        // 🧛 Dracula — ungu gelap + pink/cyan ikonik
        ThemeDef("dracula", "Dracula", "🧛", c("#282A36"), c("#2E3140"), c("#3A3D50"),
            c("#F8F8F2"), c("#A8A6C0"), c("#BD93F9"), c("#FF79C6"), c("#44475A"), c("#343746"), true),
        // 🌌 Nord — biru arktik dingin, tenang
        ThemeDef("nord", "Nord", "🌌", c("#2E3440"), c("#3B4252"), c("#434C5E"),
            c("#ECEFF4"), c("#A6B0C0"), c("#88C0D0"), c("#BF616A"), c("#4C566A"), c("#3B4252"), true),
        // 🌅 Synthwave — ungu tua + neon magenta/oranye (retro 80-an)
        ThemeDef("synthwave", "Synthwave", "🌅", c("#1A0B2E"), c("#241041"), c("#331A57"),
            c("#FFE9FF"), c("#B79AD1"), c("#FF2E88"), c("#FFB03B"), c("#4A2A72"), c("#2B1349"), true),
        // 🌴 Vaporwave — pastel pink/mint + ungu
        ThemeDef("vaporwave", "Vaporwave", "🌴", c("#1B1035"), c("#24184A"), c("#33235F"),
            c("#FFEAF7"), c("#BCA8DA"), c("#FF71CE"), c("#01CDFE"), c("#4B3580"), c("#2A1C52"), true),
        // 🤖 Cyberpunk — kuning racun di atas hitam biru
        ThemeDef("cyberpunk", "Cyberpunk", "🤖", c("#0A0E14"), c("#111721"), c("#1A2330"),
            c("#F4FA6A"), c("#8695A5"), c("#FCEE0A"), c("#00E1FF"), c("#25313F"), c("#141B25"), true),
        // 🥀 Rosé Pine — gelap lembut, mawar & pinus
        ThemeDef("rosepine", "Rosé Pine", "🥀", c("#191724"), c("#1F1D2E"), c("#26233A"),
            c("#E0DEF4"), c("#908CAA"), c("#EBBCBA"), c("#C4A7E7"), c("#403D52"), c("#21202E"), true),
        // 🍂 Gruvbox — hangat tanah, retro terminal
        ThemeDef("gruvbox", "Gruvbox", "🍂", c("#282828"), c("#32302F"), c("#3C3836"),
            c("#EBDBB2"), c("#A89984"), c("#FABD2F"), c("#FB4934"), c("#504945"), c("#32302F"), true),
        // 💻 One Dark — tema editor klasik
        ThemeDef("onedark", "One Dark", "💻", c("#282C34"), c("#2F343D"), c("#3A4049"),
            c("#ABB2BF"), c("#7F848E"), c("#61AFEF"), c("#E06C75"), c("#3E4451"), c("#2F343D"), true),
        // 🎨 Monokai — kuning-hijau khas editor
        ThemeDef("monokai", "Monokai", "🎨", c("#272822"), c("#2E2F28"), c("#3B3C33"),
            c("#F8F8F2"), c("#A6A68A"), c("#A6E22E"), c("#F92672"), c("#49483E"), c("#2E2F28"), true),
        // 🌊 Ocean — biru laut dalam
        ThemeDef("ocean", "Ocean", "🌊", c("#0B1E2D"), c("#12293B"), c("#1B3A50"),
            c("#E3F4FF"), c("#8FB3C7"), c("#2FB7E0"), c("#FF8A65"), c("#204760"), c("#12293B"), true),
        // 🌸 Sakura — pastel terang merah muda
        ThemeDef("sakura", "Sakura", "🌸", c("#FFF5F8"), c("#FFFFFF"), c("#FDE7EF"),
            c("#3A2230"), c("#8A6676"), c("#E84B7A"), c("#7C5CFF"), c("#F3CFDC"), c("#FFFFFF"), false),
        // 💜 Lavender — terang ungu lembut
        ThemeDef("lavender", "Lavender", "💜", c("#F6F3FF"), c("#FFFFFF"), c("#EDE7FC"),
            c("#221B36"), c("#6E6490"), c("#7C5CFF"), c("#D64B8A"), c("#DCD4F2"), c("#FFFFFF"), false),
        // ☕ Coffee — krem hangat & cokelat
        ThemeDef("coffee", "Coffee", "☕", c("#F5EFE6"), c("#FFFBF5"), c("#EBE0D0"),
            c("#2E241C"), c("#7A6A58"), c("#B5651D"), c("#8C4A3C"), c("#DCCDB8"), c("#FFFBF5"), false),
        // 🌤 Solarized Light — beige kebiruan
        ThemeDef("solarized", "Solarized", "🌤", c("#FDF6E3"), c("#FFFCF3"), c("#EEE8D5"),
            c("#073642"), c("#657B83"), c("#268BD2"), c("#D33682"), c("#DDD6C1"), c("#FFFCF3"), false),
    )

    /** Terapkan style XML tema ke Activity (status bar, latar window, warna sistem). */
    fun applyTheme(activity: Activity, themeId: String) {
        val res = themeDef(themeId).styleRes
        if (res != 0) runCatching { activity.setTheme(res) }
    }

    fun themeDef(id: String): ThemeDef =
        THEMES.firstOrNull { it.id == id } ?: THEMES.first { it.id == "dark" }

    /** Palet tema + warna aksen kustom (settings.accentColor) bila diisi. */
    fun palette(settings: AppSettings): Palette {
        val t = themeDef(settings.theme)
        val accent = settings.accentColor
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { c(it) }.getOrNull() }
            ?: t.accent
        // teks di atas aksen: hitam untuk aksen terang, putih untuk aksen gelap
        return Palette(
            bg = t.bg, panel = t.panel, panel2 = t.panel2, text = t.text, muted = t.muted,
            accent = accent, accent2 = t.accent2, border = t.border, input = t.input,
            dark = t.dark
        )
    }

    /** Warna teks yang aman di atas warna aksen (kontras). */
    fun onAccent(accent: Int): Int {
        val lum = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255.0
        return if (lum > 0.6) Color.parseColor("#14141A") else Color.WHITE
    }

    fun c(hex: String): Int = Color.parseColor(hex)

    /** Warna avatar bulat (sama dengan AVATAR_COLORS di main.py). */
    val AVATAR_COLORS = listOf("#25F4EE", "#FE2C55", "#F5C451", "#9B8CFF", "#3DDC84", "#FF9F5A")

    fun avatarColor(name: String): Int {
        val sum = name.toCharArray().sumOf { it.code }
        return c(AVATAR_COLORS[Math.floorMod(sum, AVATAR_COLORS.size)])
    }

    fun initial(name: String): String = name.trim().take(1).ifEmpty { "?" }.uppercase()

    /** Warna teks huruf inisial agar kontras di atas warna avatar. */
    fun circleText(name: String): Int = c("#0B0B10")

    /**
     * Font yang pasti punya glyph ❤ 👁 👥 🎁 ⚙ dkk.
     * Tema Material3 memakai font sendiri (Roboto) yang tidak berisi emoji,
     * sehingga ikon tampil sebagai kotak kosong / tidak terlihat di sebagian HP.
     */
    private var emojiFont: Typeface? = null
    private var textFont: Typeface? = null

    /**
     * Font teks UI. Selalu "sans-serif" sistem (Roboto di Android) — JANGAN pakai
     * Typeface.DEFAULT, karena di banyak HP (Samsung/OPPO/Xiaomi) DEFAULT = font
     * dekoratif pengguna (mis. Choco Cooky) sehingga chat terlihat seperti tulisan tangan.
     */
    fun uiFont(): Typeface = textFont ?: run {
        /*
         * PENTING: di Samsung "sans-serif"/SANS_SERIF tetap mengikuti font display
         * pengguna (Choco Cooky = cursive). Satu-satunya cara pasti adalah memuat
         * berkas Roboto SISTEM langsung dari /system/fonts, lalu bertingkat ke
         * family lain bila tidak ada.
         */
        val files = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/RobotoStatic-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf"
        )
        var tf: Typeface? = null
        for (f in files) {
            try {
                val file = java.io.File(f)
                if (file.exists()) {
                    tf = Typeface.createFromFile(file)
                    break
                }
            } catch (_: Exception) {
            }
        }
        if (tf == null) {
            for (name in listOf("sans-serif-condensed", "sans-serif-medium", "serif")) {
                val t = runCatching { Typeface.create(name, Typeface.NORMAL) }.getOrNull()
                if (t != null && !t.isItalic) { tf = t; break }
            }
        }
        (tf ?: Typeface.SANS_SERIF).also { textFont = it }
    }

    fun emoji(settings: AppSettings): Typeface = emojiFont ?: uiFont().also {
        emojiFont = it
    }

    private fun hasEmoji(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return false
        for (ch in text) {
            val cp = ch.code
            if (cp in 0x2000..0x2BFF || cp in 0xD800..0xDFFF || cp >= 0xFE00) return true
        }
        return false
    }

    /**
     * Font teks: SELALU sans-serif sistem Roboto.
     * Dulu font dari server ("Segoe UI", "Product Sans", ...) dipetakan ke
     * "sans-serif" string, yang di sebagian HP tetap jatuh ke font dekoratif
     * pengguna -> chat terlihat seperti tulisan tangan. Sekarang dipaksa memakai
     * [uiFont] (Roboto) kecuali user memang memilih Serif / Monospace.
     */
    fun typefaceFor(settings: AppSettings): Typeface? {
        val family = settings.fontFamily
        // "Monospace"/"Consolas" hanya untuk isi chat, BUKAN untuk label & tombol
        // dialog — monospace membuat UI terlihat kaku. UI selalu memakai uiFont().
        return when {
            family.contains("Serif", true) -> Typeface.create("serif", Typeface.NORMAL)
            else -> uiFont()
        }
    }

    /** Font isi chat/gift sesuai pilihan user (boleh monospace/serif). */
    fun contentFont(settings: AppSettings): Typeface {
        val family = settings.fontFamily
        return when {
            family.contains("Mono", true) || family.contains("Consolas", true) ->
                Typeface.create("monospace", Typeface.NORMAL)
            family.contains("Serif", true) -> Typeface.create("serif", Typeface.NORMAL)
            else -> uiFont()
        }
    }

    /** Font tebal — selalu mengembalikan sesuatu (tidak pernah null). */
    /** Font "pemanis" (italik) untuk label komentar — terlihat lembut, tidak kaku. */
    fun soft(settings: AppSettings): Typeface =
        Typeface.create(uiFont(), Typeface.ITALIC)

    fun bold(settings: AppSettings): Typeface = typefaceFor(settings)?.let {
        Typeface.create(it, Typeface.BOLD)
    } ?: uiBold()

    private var boldFile: Typeface? = null

    /** Roboto Bold dari berkas sistem (Typeface.create(uiFont,BOLD) bisa tetap cursive). */
    fun uiBold(): Typeface = boldFile ?: run {
        var tf: Typeface? = null
        for (f in listOf("/system/fonts/Roboto-Bold.ttf", "/system/fonts/RobotoStatic-Bold.ttf",
                         "/system/fonts/NotoSans-Bold.ttf", "/system/fonts/DroidSans-Bold.ttf")) {
            try {
                val file = java.io.File(f)
                if (file.exists()) { tf = Typeface.createFromFile(file); break }
            } catch (_: Exception) { }
        }
        (tf ?: Typeface.create(uiFont(), Typeface.BOLD)).also { boldFile = it }
    }

    /**
     * Terapkan tema ke seluruh view. Peran diambil dari tag view
     * ("tv", "muted", "accent", "input", "iconbn", "panel", "frame", "primary", "seg", ...)
     */
    fun apply(root: View, p: Palette, settings: AppSettings) {
        // Latar diatur MainActivity (wallpaper/gradasi), jadi jangan ditimpa warna solid
        walk(root, p, settings, header = true)
        (root.context as? Activity)?.window?.let { w ->
            w.statusBarColor = p.bg
            w.navigationBarColor = p.bg
            if (Build.VERSION.SDK_INT >= 23) {
                w.decorView.systemUiVisibility =
                    if (p.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun walk(v: View, p: Palette, settings: AppSettings, header: Boolean = false) {
        val isPanel = (v.tag as? String) == "panel"
        when (v.tag as? String) {
            // "frame" sekarang transparan: latar digambar wallpaper + gradasi (MainActivity)
            "frame" -> v.background = null
            // pil kaca: isi sangat transparan + garis tipis (wallpaper terlihat menembus)
            "glasspill" -> v.background = rounded(
                Color.argb(105, Color.red(p.panel), Color.green(p.panel), Color.blue(p.panel)),
                26f, Color.argb(70, 255, 255, 255), v, 1
            )
            "ghoststrip" -> v.background = rounded(
                Color.argb(55, Color.red(p.bg), Color.green(p.bg), Color.blue(p.bg)),
                24f, Color.argb(45, 255, 255, 255), v, 1
            )
            "glass" -> v.background = rounded(mix(p.panel, p.bg, 0.45f), 18f, mix(p.border, Color.WHITE, 0.10f), v, 1)
            "pill" -> v.background = rounded(mix(p.bg, p.accent, 0.22f), 20f, p.accent, v, 1)
            "panel" -> v.background = rounded(p.panel, 12f, 0, v, 0)
            // daftar komentar harus transparan supaya wallpaper terlihat
            "transparentlist" -> v.background = null
            "chip" -> v.background = rounded(p.panel2, 10f, 0, v, 0)
            "dialog" -> v.background = rounded(p.panel, 16f, p.border, v, 1)
            "input" -> if (v is EditText) {
                v.setTextColor(p.text)
                v.setHintTextColor(p.muted)
                v.background = rounded(p.input, 8f, p.border, v, 1)
                v.textSize = settings.fontSize.toFloat()
            }
            "primary" -> if (v is Button) {
                // tombol aksi utama: pakai warna aksen tema (bukan merah keras)
                v.backgroundTintList = null
                v.background = rounded(p.accent, 10f, 0, v, 0)
                v.setTextColor(onAccent(p.accent))
                v.textSize = 13f
                v.minWidth = 0
                v.isAllCaps = false
                v.typeface = if (hasEmoji(v.text)) emoji(settings) else bold(settings)
            }
            "seg" -> if (v is Button) {
                val checked = v is android.widget.CheckBox || v.isSelected
                v.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                v.background = rounded(
                    if (checked) p.accent else p.panel2, 10f, 0, v, 0
                )
                v.setTextColor(if (checked) onAccent(p.accent) else p.text)
                v.textSize = 13f
                v.minWidth = 0
                v.minHeight = 0
                v.isAllCaps = false
                v.typeface = if (hasEmoji(v.text)) emoji(settings) else uiFont()
            }
            "iconbtn" -> if (v is Button) {
                // Tombol ikon: tanpa latar merah, ukuran pas dengan layout (tidak membesar)
                v.background = rounded(Color.TRANSPARENT, 8f, 0, v, 0)
                v.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                v.stateListAnimator = null
                v.setTextColor(if (v.isSelected) p.accent else p.text)
                v.typeface = emoji(settings)
                v.includeFontPadding = false
                v.minWidth = 0
                v.minimumWidth = 0
                v.minHeight = 0
                v.minimumHeight = 0
                v.setPadding(0, 0, 0, 0)
                v.isAllCaps = false
            }
            "trail" -> if (v is Button) {
                val on = v.isSelected
                // backgroundTint AppCompat menimpa drawable -> kosongkan dulu
                v.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                v.background = rounded(if (on) p.accent else p.panel2, 12f, 0, v, 0)
                v.setTextColor(if (on) onAccent(p.accent) else p.muted)
                v.typeface = if (hasEmoji(v.text)) emoji(settings) else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                v.minWidth = 0
                v.minimumWidth = 0
                v.isAllCaps = false
            }
            "muted" -> if (v is TextView) {
                v.setTextColor(p.muted)
                v.textSize = (settings.fontSize - 2).coerceAtLeast(9).toFloat()
                typefaceFor(settings)?.let { v.typeface = it }
            }
            "accent" -> if (v is TextView) {
                v.setTextColor(p.accent)
                v.textSize = settings.fontSize.toFloat()
                v.typeface = bold(settings)
            }
            "tv" -> if (v is TextView) {
                v.setTextColor(p.text)
                v.textSize = settings.fontSize.toFloat()
                typefaceFor(settings)?.let { v.typeface = it }
            }
        }
        // Teks berisi emoji (❤ 👍 🔍 ...) selalu pakai font sistem yang punya glyph emoji
        if (v is TextView && v !is EditText && v.tag != "iconbtn" && v.tag != "trail" && hasEmoji(v.text)) {
            v.typeface = emoji(settings)
        }
        if (v is ImageButton) v.imageTintList = ColorStateList.valueOf(p.text)
        // "💬 Komentar", "🎁 Rasa", "👋 Bergabung" di area komentar tetap terlihat
        val tagStr = v.tag as? String
        if (v is TextView && (v.id == com.ombaharbangkit.tiktoklivereader.R.id.commentHeader || tagStr == "headerlabel")) {
            v.setTextColor(p.text)
            v.textSize = (settings.fontSize - 1).coerceAtLeast(10).toFloat()
            v.typeface = bold(settings)
            v.background = rounded(mix(p.bg, p.accent, 0.30f), 20f, p.accent, v, 1)
        }
        // Tombol pil (follow) — warna aksen dengan bentuk kapsul
        if (v is TextView && tagStr == "pill") {
            v.setTextColor(p.accent)
            v.textSize = (settings.fontSize - 1).coerceAtLeast(10).toFloat()
            v.typeface = Typeface.create(uiFont(), Typeface.BOLD)
            v.background = rounded(mix(p.bg, p.accent, 0.20f), 20f, p.accent, v, 1)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), p, settings, header || isPanel)
    }

    fun rounded(color: Int, radiusDp: Float, stroke: Int, v: View, strokeDp: Int): GradientDrawable {
        val d = v.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * d
            if (strokeDp > 0) setStroke((strokeDp * d).toInt().coerceAtLeast(1), stroke)
        }
    }

    /** Campur dua warna (t=0 -> a, t=1 -> b) — dipakai untuk sorotan komentar baru. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
            (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
            (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt()
        )
    }

    /** Warna isi bubble komentar sesuai tema (gelap: panel gelap, terang: putih). */
    fun bubbleFill(p: Palette): Int = if (p.dark) p.panel2 else Color.WHITE

    /** Warna garis bubble — kontras cukup di kedua tema. */
    fun bubbleStroke(p: Palette): Int = if (p.dark) p.border else mix(p.border, Color.BLACK, 0.25f)

    fun circle(color: Int, sizeDp: Float, d: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            val px = (sizeDp * d).toInt()
            setSize(px, px)
        }
}
