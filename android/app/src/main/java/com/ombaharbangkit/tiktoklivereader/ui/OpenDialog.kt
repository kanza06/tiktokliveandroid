package com.ombaharbangkit.tiktoklivereader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.ombaharbangkit.tiktoklivereader.MainActivity
import com.ombaharbangkit.tiktoklivereader.R
import com.ombaharbangkit.tiktoklivereader.data.Prefs
import com.ombaharbangkit.tiktoklivereader.data.ServerClient
import com.ombaharbangkit.tiktoklivereader.model.AppSettings
import kotlinx.coroutines.launch

/**
 * Dialog Pengaturan & Server — berisi item yang sama dengan SettingsDialog Py:
 * username TikTok, transparansi, ukuran font, jenis font, satuan kecepatan, tema.
 */
object OpenDialog {

    private val FONT_FAMILIES = listOf(
        "Segoe UI", "Roboto", "Sans Serif (default)", "Serif", "Monospace",
        "Product Sans", "Noto Sans", "Consolas"
    )

    @SuppressLint("SetTextI18n")
    fun settings(
        activity: MainActivity,
        prefs: Prefs,
        clientProvider: () -> ServerClient,
        onSaved: (AppSettings) -> Unit
    ) {
        val ctx: Context = activity
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val s = prefs.toSettings()
        Theme.apply(view, Theme.palette(s), s)

        val inputUsername = view.findViewById<EditText>(R.id.inputUsername)
        val btnConnect = view.findViewById<Button>(R.id.btnConnect)
        val labelOpacity = view.findViewById<TextView>(R.id.labelOpacity)
        val seekOpacity = view.findViewById<SeekBar>(R.id.seekOpacity)
        val labelFontSize = view.findViewById<TextView>(R.id.labelFontSize)
        val seekFontSize = view.findViewById<SeekBar>(R.id.seekFontSize)
        val spinnerFont = view.findViewById<Spinner>(R.id.spinnerFont)
        val bKbps = view.findViewById<Button>(R.id.btnKbps)
        val bMbps = view.findViewById<Button>(R.id.btnMbps)
        val bMBps = view.findViewById<Button>(R.id.btnMBps)
        val themeRow = view.findViewById<android.view.View>(R.id.themeRow)
        val themePreview = view.findViewById<android.widget.LinearLayout>(R.id.themePreview)

        // Tinggi ScrollView = 65% layar supaya dialog tidak terpotong & tetap bisa digulir.
        // (android:maxHeight tidak berlaku untuk ScrollView, jadi dihitung di sini.)
        val scroll = view.findViewById<android.widget.ScrollView>(R.id.settingsScroll)
        val dm = ctx.resources.displayMetrics
        val maxH = (dm.heightPixels * 0.72f).toInt()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (dm.widthPixels * 0.94f).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scroll.layoutParams = scroll.layoutParams.apply { height = maxH }
            scroll.requestLayout()
        }


        inputUsername.setText(prefs.username)
        val isConnected = activity.liveRunning()
        btnConnect.text = if (isConnected) "Putus" else "Hubungkan"

        // --- transparansi (30–100%) -> SeekBar 0..70
        seekOpacity.max = 70
        seekOpacity.progress = (s.opacity - 30).coerceIn(0, 70)
        labelOpacity.text = "${s.opacity}%"
        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                labelOpacity.text = "${p + 30}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.opacity = seekOpacity.progress + 30
                sync(activity, prefs, clientProvider())
                onSaved(prefs.toSettings())
            }
        })

        // --- ukuran font 10..24
        seekFontSize.max = 14
        seekFontSize.progress = (s.fontSize - 10).coerceIn(0, 14)
        labelFontSize.text = "${s.fontSize} px"
        seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                labelFontSize.text = "${p + 10} px"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.fontSize = seekFontSize.progress + 10
                sync(activity, prefs, clientProvider())
                onSaved(prefs.toSettings())
            }
        })

        // --- jenis font
        spinnerFont.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, FONT_FAMILIES)
        // teks spinner harus terlihat di atas latar tema (item bawaan berwarna gelap)
        spinnerFont.background = Theme.rounded(Theme.palette(s).input, 8f, Theme.palette(s).border, spinnerFont, 1)
        spinnerFont.setSelection(FONT_FAMILIES.indexOf(s.fontFamily).coerceAtLeast(0))
        spinnerFont.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.fontFamily = FONT_FAMILIES[pos]
                sync(activity, prefs, clientProvider())
                onSaved(prefs.toSettings())
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // --- satuan kecepatan
        fun markSpeed(unit: String) {
            prefs.speedUnit = unit
            listOf(bKbps to "kbps", bMbps to "mbps", bMBps to "MBps").forEach { (b, u) ->
                b.isSelected = u == unit
                val pal = Theme.palette(prefs.toSettings())
                b.background = Theme.rounded(if (u == unit) pal.accent2 else pal.panel2, 8f, 0, b, 0)
                b.setTextColor(if (u == unit) Color.WHITE else pal.text)
            }
            sync(activity, prefs, clientProvider())
        }
        bKbps.setOnClickListener { markSpeed("kbps"); onSaved(prefs.toSettings()) }
        bMbps.setOnClickListener { markSpeed("mbps"); onSaved(prefs.toSettings()) }
        bMBps.setOnClickListener { markSpeed("MBps"); onSaved(prefs.toSettings()) }
        markSpeed(s.speedUnit)

        // --- latar belakang (wallpaper dari galeri)
        view.findViewById<Button>(R.id.btnPickWallpaper).setOnClickListener {
            dialog.dismiss()
            activity.pickWallpaper()
        }
        view.findViewById<Button>(R.id.btnClearWallpaper).setOnClickListener {
            activity.clearWallpaperImage()
            Toast.makeText(ctx, "Latar kembali ke tema", Toast.LENGTH_SHORT).show()
        }

        // --- tema (semua pilihan, langsung berubah saat diketuk).
        // Warna aksen tidak lagi diatur terpisah: sepenuhnya milik tema.
        fun refreshAll() {
            val st = prefs.toSettings()
            val pal = Theme.palette(st)
            Theme.apply(view, pal, st)
            view.findViewById<TextView>(R.id.themeCount).text = "${Theme.THEMES.size} pilihan"
            val cur = Theme.themeDef(st.theme)
            view.findViewById<TextView>(R.id.spinnerThemeLabel).text = "${cur.emoji}  ${cur.label}"
            updateThemePreview(themePreview, cur)
        }

        // Dropdown tema: tombol -> dialog daftar. Lebih andal daripada Spinner
        // (Spinner sering tidak terbaca warnanya di dialog bertema) dan hemat tempat.
        view.findViewById<android.view.View>(R.id.themeRow).setOnClickListener {
            val labels = Theme.THEMES.map { "${it.emoji}  ${it.label}" }.toTypedArray()
            val checked = Theme.THEMES.indexOfFirst { it.id == prefs.theme }
            val listDialog = androidx.appcompat.app.AlertDialog.Builder(ctx, R.style.Dialog_List)
                .setTitle("Pilih tema (${Theme.THEMES.size})")
                .setSingleChoiceItems(labels, checked) { d, which ->
                    val def = Theme.THEMES[which]
                    prefs.theme = def.id
                    sync(activity, prefs, clientProvider())
                    onSaved(prefs.toSettings())
                    refreshAll()
                    d.dismiss()
                }
                .setNegativeButton("Tutup", null)
                .create()
            // Batasi tinggi daftar + teks jelas (tidak ikut transparan/biru).
            listDialog.setOnShowListener {
                val lv = listDialog.listView
                lv?.let {
                    val lp = it.layoutParams
                    lp.height = (ctx.resources.displayMetrics.heightPixels * 0.62f).toInt()
                    it.layoutParams = lp
                }
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 28f * ctx.resources.displayMetrics.density
                    setColor(android.graphics.Color.parseColor("#1C1C24"))
                }
                listDialog.window?.setBackgroundDrawable(bg)
            }
            listDialog.show()
        }
        refreshAll()

        // --- hubungkan / putus
        btnConnect.setOnClickListener {
            var name = inputUsername.text.toString().trim()
            if (name.startsWith("@")) name = name.substring(1)
            if (isConnected) {
                activity.lifecycleScope.launch {
                    try {
                        clientProvider().disconnect()
                    } catch (_: Exception) {
                    }
                    dialog.dismiss()
                }
            } else {
                if (name.isEmpty()) {
                    Toast.makeText(ctx, "Isi username TikTok dulu", Toast.LENGTH_SHORT).show()
                } else {
                    activity.lifecycleScope.launch {
                        try {
                            clientProvider().connect(name)
                            prefs.username = name
                            dialog.dismiss()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    fun server(
        activity: MainActivity,
        prefs: Prefs,
        onChanged: () -> Unit
    ) {
        val ctx: Context = activity
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_server, null)
        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val s = prefs.toSettings()
        Theme.apply(view, Theme.palette(s), s)

        val input = view.findViewById<EditText>(R.id.inputServer)
        val inputKey = view.findViewById<EditText>(R.id.inputApiKey)
        val hint = view.findViewById<TextView>(R.id.serverHint)
        input.setText(prefs.serverUrl)
        inputKey.setText(prefs.apiKey)

        // Cari server di Wi-Fi secara otomatis (tanpa mengetik IP)
        view.findViewById<Button>(R.id.btnFindServer).setOnClickListener {
            hint.setText("Mencari server di Wi-Fi... ⏳")
            hint.setTextColor(Theme.palette(s).muted)
            activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val found = com.ombaharbangkit.tiktoklivereader.data.ServerDiscovery
                    .find(activity, input.text.toString())
                withContextMain {
                    if (found != null) {
                        input.setText(found)
                        hint.text = "✔ Server ditemukan: $found"
                        hint.setTextColor(Color.parseColor("#3DDC84"))
                    } else {
                        hint.text = "✖ Server tidak ditemukan. Pastikan PC menjalankan run_server.bat " +
                            "dan HP di Wi-Fi yang sama."
                        hint.setTextColor(Color.parseColor("#E8204F"))
                    }
                }
            }
        }

        // Ambil URL publik (tunnel) dari server lokal yang sudah tersimpan
        view.findViewById<Button>(R.id.btnTunnelUrl).setOnClickListener {
            val local = ServerClient.normalize(input.text.toString())
            activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val t = ServerClient(local, inputKey.text.toString()).tunnelUrl()
                    if (t.isNullOrBlank()) throw IllegalStateException("tunnel belum siap / cloudflared tidak ada")
                    withContextMain {
                        input.setText(t)
                        hint.text = "✔ URL tunnel diambil: $t"
                        hint.setTextColor(Color.parseColor("#3DDC84"))
                    }
                } catch (e: Exception) {
                    withContextMain {
                        hint.text = "✖ ${e.message}"
                        hint.setTextColor(Color.parseColor("#E8204F"))
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.btnTestServer).setOnClickListener {
            val url = ServerClient.normalize(input.text.toString())
            activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                withContextMain { hint.text = "Menghubungi $url ..." }
                try {
                    val info = ServerClient(url, inputKey.text.toString()).health()
                    withContextMain {
                        hint.text = "✔ Terhubung. Server: ${info.optString("app")} v${info.optString("version")}"
                        hint.setTextColor(Color.parseColor("#3DDC84"))
                    }
                } catch (e: Exception) {
                    withContextMain {
                        hint.text = "✖ Gagal: ${e.message}"
                        hint.setTextColor(Color.parseColor("#E8204F"))
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.btnSaveServer).setOnClickListener {
            val raw = input.text.toString().trim()
            prefs.apiKey = inputKey.text.toString()
            if (raw.isEmpty()) {
                // kosong = biarkan aplikasi mencari server sendiri di Wi-Fi
                prefs.forgetServer()
                dialog.dismiss()
                onChanged()
                Toast.makeText(ctx, "Otomatis: mencari server di Wi-Fi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = ServerClient.normalize(raw)
            prefs.serverUrl = url
            prefs.manualServer = true
            dialog.dismiss()
            onChanged()
            Toast.makeText(ctx, "Server disimpan: $url", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    /** Pratinjau warna tema: latar, panel, aksen, + nama tema. */
    private fun updateThemePreview(box: android.widget.LinearLayout, def: Theme.ThemeDef) {
        val d = box.resources.displayMetrics.density
        box.removeAllViews()
        box.background = Theme.rounded(def.bg, 14f, def.accent, box, 2)

        // tiga bulatan warna: accent, accent2, text
        listOf(def.accent, def.accent2, def.text).forEach { color ->
            box.addView(View(box.context).apply {
                background = Theme.circle(color, 18f, d)
                layoutParams = android.widget.LinearLayout.LayoutParams((18 * d).toInt(), (18 * d).toInt())
                    .apply { marginEnd = (6 * d).toInt() }
            })
        }

        box.addView(android.widget.TextView(box.context).apply {
            text = "${def.emoji} ${def.label}"
            setTextColor(def.text)
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setPadding((8 * d).toInt(), 0, 0, 0)
        })
    }

    private suspend fun withContextMain(block: () -> Unit) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { block() }

    /** Kirim pengaturan ke server (opsional — bila server tak terjangkau, tetap lokal). */
    fun sync(activity: MainActivity, prefs: Prefs, client: ServerClient) {
        val patch = prefs.toServerPatch()
        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.saveSettings(patch)
            } catch (_: Exception) {
            }
        }
    }
}


