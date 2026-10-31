package com.ombaharbangkit.tiktoklivereader

import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ombaharbangkit.tiktoklivereader.data.Prefs
import com.ombaharbangkit.tiktoklivereader.data.ServerClient
import com.ombaharbangkit.tiktoklivereader.data.ServerDiscovery
import com.ombaharbangkit.tiktoklivereader.model.AppSettings
import com.ombaharbangkit.tiktoklivereader.model.GiftItem
import com.ombaharbangkit.tiktoklivereader.model.JoinItem
import com.ombaharbangkit.tiktoklivereader.model.LiveSnapshot
import com.ombaharbangkit.tiktoklivereader.model.PersonInfo
import com.ombaharbangkit.tiktoklivereader.ui.CommentAdapter
import com.ombaharbangkit.tiktoklivereader.ui.GiftAdapter
import com.ombaharbangkit.tiktoklivereader.ui.JoinAdapter
import com.ombaharbangkit.tiktoklivereader.ui.LiveOverlay
import com.ombaharbangkit.tiktoklivereader.ui.OpenDialog
import com.ombaharbangkit.tiktoklivereader.ui.SparkView
import com.ombaharbangkit.tiktoklivereader.ui.Theme
import com.ombaharbangkit.tiktoklivereader.ui.fmtDur
import com.ombaharbangkit.tiktoklivereader.ui.fmtSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dashboard — meniru MainWindow di main.py:
 *   title bar (status + gear + server + demo), baris counter 👁 👥 🎁 + kecepatan,
 *   panel gift / bergabung / komentar, footer follow, popup daftar orang.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var client: ServerClient

    private lateinit var adapters: Triple<CommentAdapter, GiftAdapter, JoinAdapter>
    private lateinit var liveOverlay: LiveOverlay
    private lateinit var sparkLayer: SparkView
    private lateinit var bgPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private var lastBurst = 0L
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private lateinit var statusProgress: ProgressBar
    private lateinit var countViewers: TextView
    private lateinit var countVisitors: TextView
    private lateinit var countGifters: TextView
    private lateinit var netLabel: TextView
    private lateinit var footerText: TextView

    private var since = 0L
    private var lastFullSync = 0L
    private var serverSettings: String = ""
    private var lastActiveList: List<PersonInfo> = emptyList()
    private var lastVisitorsList: List<PersonInfo> = emptyList()
    private var lastGiftersList: List<PersonInfo> = emptyList()
    private var lastSnapshot: LiveSnapshot? = null
    /** true bila polling terakhir berhasil (dipakai estimasi kecepatan jaringan) */
    @Volatile private var pollOk = false
    @Volatile private var searching = false
    private var lastDiscoverTry = 0L
    private var shownComments = 0
    private var seenComments: MutableSet<Long> = mutableSetOf()
    private var seenGifts: MutableSet<Long> = mutableSetOf()
    private var seenJoins: MutableSet<Long> = mutableSetOf()
    /** Polling pertama = data lama, jangan dianimasikan semua sekaligus. */
    private var overlayPrimed = false
    /** true = tampilkan overlay gift/join (klien ini yang menentukan, server tetap kompatibel) */
    private val overlayEnabled: Boolean = true
    /** true saat mengimpor riwayat komentar (jangan paksa gulir ke atas) */
    private var loadingHistory = false

    private val listComments get() = findViewById<RecyclerView>(R.id.listComments)

    /** Beri padding notch/status bar & area gesture agar isi tidak tertutup sistem bar. */
    private fun applyInsets() {
        val root = findViewById<View>(R.id.frameRoot)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val cut = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                maxOf(sys.left, cut.left),
                maxOf(sys.top, cut.top),
                maxOf(sys.right, cut.right),
                maxOf(sys.bottom, cut.bottom)
            )
            insets
        }
    }
    private val netSamples = ArrayDeque<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Jangan sampai app "keluar sendiri" tanpa jejak: catat error terakhir
        // ke log & tampilkan pesan, bukan crash diam-diam.
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            android.util.Log.e("TTL_CRASH", "crash: ${e.message}", e)
            prev?.uncaughtException(t, e)
        }
        // Tema XML (Kawaii Midnight / Cyber Neon / Matrik / Neon Kawaii Dark ...)
        // diterapkan SEBELUM layout agar status bar, window background dan warna
        // sistem langsung mengikuti tema pilihan.
        prefs = Prefs(this)
        Theme.applyTheme(this, prefs.theme)
        // Font UI dipaksa Roboto (bukan font display pengguna seperti Choco Cooky),
        // sehingga chat tidak tampil seperti tulisan tangan di HP tertentu.
        theme.applyStyle(R.style.AppFont, true)

        // Full-screen seperti game: gambar menutupi status bar & navigation bar
        // (edge-to-edge), sistem bar transparan dan disembunyikan saat tidak disentuh.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )

        // Sembunyikan ActionBar (judul "TikTok Live Reader") agar area komentar maksimal
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
        applyInsets()

        // Hiasan bintang/hati melayang + efek ketuk
        sparkLayer = findViewById(R.id.sparkLayer)
        sparkLayer.start()

        // Pemilih gambar latar (galeri) + hapus gambar
        // OpenDocument memberi izin baca PERMANEN (bisa dipertahankan setelah app ditutup),
        // dan gambar juga DISALIN ke folder app supaya pasti tampil walau izin URI hilang.
        bgPicker = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    android.util.Log.w("TTL_WALL", "izin uri: ${e.message}")
                }
                prefs.wallpaperUri = copyWallpaperToApp(uri) ?: uri.toString()
                applyWallpaper()
                Toast.makeText(this, "Latar belakang diganti ✨", Toast.LENGTH_SHORT).show()
            }
        }

        client = ServerClient(prefs.serverUrl, prefs.apiKey)

        savedInstanceState?.let { since = it.getLong("since", 0L) }

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        statusProgress = findViewById(R.id.statusProgress)
        countViewers = findViewById(R.id.countViewers)
        countVisitors = findViewById(R.id.countVisitors)
        countGifters = findViewById(R.id.countGifters)
        netLabel = findViewById(R.id.netLabel)
        footerText = findViewById(R.id.footerText)

        // Komentar memakai kolom penuh; gift & bergabung tampil sebagai overlay animasi
        val comments = CommentAdapter()
        adapters = Triple(comments, GiftAdapter { currentClient().baseUrl }, JoinAdapter())
        bindList(R.id.listComments, comments)
        liveOverlay = LiveOverlay(
            findViewById(R.id.giftOverlay),
            findViewById(R.id.joinOverlay)
        )

        findViewById<Button>(R.id.btnSettings).setOnClickListener { OpenDialog.settings(this, prefs, { currentClient() }, ::onSettingsSaved) }
        findViewById<Button>(R.id.btnServer).setOnClickListener { OpenDialog.server(this, prefs, ::onServerChanged) }
        findViewById<Button>(R.id.btnWallpaper).setOnClickListener {
            Toast.makeText(this, "Pilih gambar dari galeri", Toast.LENGTH_SHORT).show()
            pickWallpaper()
        }
        footerText.setOnClickListener { maybeAutoDiscover() }

        findViewById<Button>(R.id.btnViewers).setOnClickListener {
            showPeople("👁 Penonton aktif", lastActiveList, byGift = false, watching = true)
        }
        findViewById<Button>(R.id.btnVisitors).setOnClickListener {
            showPeople("👥 Pernah mampir", lastVisitorsList, byGift = false, watching = false)
        }
        findViewById<Button>(R.id.btnGifters).setOnClickListener {
            showPeople("🎁 Pemberi gift", lastGiftersList, byGift = true, watching = false)
        }
        findViewById<TextView>(R.id.followLink).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.tiktok.com/@ombaharbangkit350jt")))
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak bisa membuka browser", Toast.LENGTH_SHORT).show()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MainActivity, "Aplikasi tetap berjalan di server", Toast.LENGTH_SHORT).show()
            }
        })

        applyTheme(prefs.toSettings())
        applyWallpaper()
        startPolling()
        // Langsung cari server bila alamat belum diisi manual (tidak perlu setting IP).
        if (!prefs.manualServer) maybeAutoDiscover()
    }

    /** Set pengaturan ke semua adapter (dipakai sebelum bind pertama & saat tema berubah). */
    private fun adaptersSettings(s: AppSettings) {
        adapters.first.settings = s
        adapters.second.settings = s
        adapters.third.settings = s
    }

    private fun bindList(id: Int, adapter: RecyclerView.Adapter<*>) {
        findViewById<RecyclerView>(id).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
            itemAnimator = null
        }
    }

    // ------------------------------------------------------------------ loop
    private fun startPolling() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    // saat pencarian otomatis berjalan, loop ini menunggu dulu
                    while (searching && isActive) delay(300)
                    pollOnce()
                    delay(1500)
                }
            }
        }
        // kecepatan jaringan: diturunkan dari ukuran respons polling tiap detik
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    tickNetwork()
                    delay(1000)
                }
            }
        }
    }

    private suspend fun pollOnce() {
        val now = System.currentTimeMillis()
        // Daftar penonton/pengaturan cukup diambil tiap 5 detik (hemat data di HP)
        val full = lastSnapshot == null || now - lastFullSync > 5000
        try {
            val snap = client.status(since, meta = full)
            since = snap.seq
            pollOk = true
            if (full) lastFullSync = now
            render(snap, full)
        } catch (e: Exception) {
            showOffline(e.message ?: "Tidak bisa menghubungi server")
        }
    }

    private fun render(snap: LiveSnapshot, full: Boolean = false) {
        lastSnapshot = snap
        // Sinkronkan pengaturan dari server (sama seperti settings.json Py).
        // Hanya dilakukan saat sync penuh, supaya tidak menimpa pilihan lokal
        // ketika pengaturan baru saja diubah dari HP.

        if (full && (snap.settings.theme.isNotEmpty() || snap.settings.fontSize > 0)) {
            if (snap.settings.toString() != serverSettings) {
                serverSettings = snap.settings.toString()
                prefs.applyServer(snap.settings)
                applyTheme(prefs.toSettings())
            }
        }
        val s = prefs.toSettings()
        // Jangan ganti settings adapter di tengah siklus polling: notifyDataSetChanged
        // saat RecyclerView sedang layout bisa memicu "Inconsistency detected" / crash.
        adapters.first.serverBase = client.baseUrl
        adapters.second.serverBase = client.baseUrl
        adapters.third.serverBase = client.baseUrl

        val pal = Theme.palette(s)
        statusDot.setTextColor(if (snap.ok) Color.parseColor("#3DDC84") else pal.accent2)
        statusText.text = snap.status
        statusText.setTextColor(pal.text)
        statusText.typeface = if (statusHasEmoji(snap.status)) Theme.emoji(s) else Theme.bold(s)
        (findViewById<Button>(R.id.btnSettings)).typeface = Theme.emoji(s)
        (findViewById<Button>(R.id.btnServer)).typeface = Theme.emoji(s)
        (findViewById<Button>(R.id.btnWallpaper)).typeface = Theme.emoji(s)
        footerText.text = buildString {
            append(snap.status)
            if (snap.ok && snap.liveSeconds > 0) append(" · ").append(fmtDur(snap.liveSeconds))
            if (snap.likes > 0) append(" · ❤ ").append(snap.likes)
            if (snap.follows > 0) append(" · ➕ ").append(snap.follows)
        }
        statusProgress.visibility =
            if (snap.mode == "connecting") View.VISIBLE else View.GONE
        netLabel.setTextColor(pal.muted)

        runCatching {
            countViewers.text = snap.active.toString()
            countVisitors.text = snap.visitors.toString()
            countGifters.text = snap.gifters.toString()
        }

        // Saat meta=0, daftar per-orang tidak dikirim server -> pakai data terakhir.
        if (snap.activeList.isNotEmpty()) lastActiveList = snap.activeList
        if (snap.visitorsList.isNotEmpty()) lastVisitorsList = snap.visitorsList
        if (snap.giftersList.isNotEmpty()) lastGiftersList = snap.giftersList

        // Semua perubahan RecyclerView dilakukan setelah layout selesai.
        // Memanggil notifyDataSetChanged/scroll saat RecyclerView sedang
        // menghitung layout adalah penyebab umum crash instan "keluar sendiri".
        runOnUiThread {
            try {
                // komentar BARU disorot (bubble aksen) sekali
                val fresh = snap.comments.filter { !seenComments.contains(it.id) }
                    .map { it.id }.toSet()
                val history = seenComments.isEmpty()
                snap.comments.forEach { seenComments.add(it.id) }
                if (seenComments.size > 600) seenComments = seenComments.toList().takeLast(300).toMutableSet()

                adapters.first.submit(snap.comments, if (history) emptySet() else fresh)
                if (snap.comments.size > shownComments) {
                    shownComments = snap.comments.size
                    if (!loadingHistory) listComments.scrollToPosition(0)
                }
                // Gift & bergabung: animate hanya item BARU (id belum pernah tampil)
                pushNewGifts(snap.gifts)
                pushNewJoins(snap.joins)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "render list gagal: ${e.message}")
            }
        }
    }

    // --------------------------------------------------- overlay gift / bergabung
    private fun pushNewGifts(list: List<GiftItem>) {
        // Sinkron pertama hanya mencatat id (data lama = tidak perlu dianimasikan)
        if (seenGifts.isEmpty() || !overlayPrimed) {
            list.forEach { seenGifts.add(it.id) }
            if (list.isNotEmpty()) overlayPrimed = true
            return
        }
        run {
            list.filter { !seenGifts.contains(it.id) }
                .takeLast(3)                       // burst gift: jangan spam layar
                .forEach { liveOverlay.showGift(it, client.baseUrl, prefs.toSettings()) }
        }
        list.forEach { seenGifts.add(it.id) }
        if (seenGifts.size > 400) seenGifts = seenGifts.toList().takeLast(200).toMutableSet()
    }

    private fun pushNewJoins(list: List<JoinItem>) {
        if (seenJoins.isEmpty() || !overlayPrimed) {
            list.forEach { seenJoins.add(it.id) }
            if (list.isNotEmpty()) overlayPrimed = true
            return
        }
        run {
            list.filter { !seenJoins.contains(it.id) }
                .takeLast(3)
                .forEach { liveOverlay.showJoin(it, client.baseUrl, prefs.toSettings()) }
        }
        list.forEach { seenJoins.add(it.id) }
        if (seenJoins.size > 400) seenJoins = seenJoins.toList().takeLast(200).toMutableSet()
    }

    private fun statusHasEmoji(text: String): Boolean = text.any { it.code in 0x2000..0x2BFF }

    private fun showOffline(reason: String) {
        pollOk = false
        val pal = Theme.palette(prefs.toSettings())
        statusDot.setTextColor(pal.accent2)
        val cloud = ServerClient.isHttps(prefs.serverUrl)
        statusText.text = when {
            searching -> "Menghubungkan ke server..."
            else -> "Server tidak terjangkau"
        }
        footerText.text = when {
            searching && cloud -> "Menghubungi tiktokliveandroid.fly.dev"
            searching -> "Mencari server di Wi-Fi..."
            else -> "$reason · tekan 🌐 atau ketuk di sini untuk mencari ulang"
        }
        if (!searching) maybeAutoDiscover()
    }

    // --------------------------------------------------------- auto discovery
    /** Cari server sendiri (tiap kali gagal, dengan jeda), tanpa user mengetik IP. */
    private fun maybeAutoDiscover() {
        val now = System.currentTimeMillis()
        if (searching || now - lastDiscoverTry < 5000) return
        lastDiscoverTry = now
        searching = true
        lifecycleScope.launch {
            val found = try {
                ServerDiscovery.find(this@MainActivity, prefs.serverUrl)
            } catch (_: Exception) {
                null
            }
            searching = false
            // Server Fly (https) yang tersimpan tidak boleh diganti oleh hasil scan LAN:
            // APK harus tetap menunjuk ke https://tiktokliveandroid.fly.dev.
            val current = ServerClient.normalize(prefs.serverUrl)
            val keepCloud = ServerClient.isHttps(current) && current == ServerClient.FLY_URL
            if (found != null && keepCloud) {
                client = ServerClient(current, prefs.apiKey)
                since = 0
                pollOnce()
            } else if (found != null) {
                prefs.serverUrl = found
                client = ServerClient(found, prefs.apiKey)
                since = 0
                pollOnce()
            } else {
                val pal = Theme.palette(prefs.toSettings())
                statusText.text = "Server tidak terjangkau"
                    footerText.text = "Tidak ada server di Wi-Fi ini"
                // Status lengkap cukup di title bar; footer diringkas agar bottom bar rapi
                footerText.visibility = View.GONE
                statusDot.setTextColor(pal.accent2)
            }
        }
    }

    // -------------------------------------------------------- jaringan (⬆⬇)
    private fun tickNetwork() {
        @Suppress("DEPRECATION")
        val online = getSystemService(ConnectivityManager::class.java)
            ?.activeNetworkInfo?.isConnected == true
        val now = System.currentTimeMillis()
        // perkiraan trafik dari jumlah polling yang berhasil dalam 20 detik terakhir
        if (pollOk) {
            netSamples.addLast(now)
            while (netSamples.isNotEmpty() && now - netSamples.first() > 20_000) netSamples.removeFirst()
        }
        if (!online) {
            netLabel.text = "⬆ --  ⬇ --"
            return
        }
        if (netSamples.size >= 2) {
            val dt = ((now - netSamples.first()) / 1000.0).coerceAtLeast(1.0)
            val bps = (netSamples.size * 1500.0) / dt   // ~1,5 KB tiap respons JSON
            val unit = prefs.speedUnit
            netLabel.text = "⬆ ${fmtSpeed(bps * 0.15, unit)}  ⬇ ${fmtSpeed(bps, unit)}"
        }
    }

    private fun ImageLoaderClear() = com.ombaharbangkit.tiktoklivereader.ui.ImageLoader.clear()

    // ------------------------------------------------------------- popup 👁
    private fun showPeople(title: String, items: List<PersonInfo>, byGift: Boolean, watching: Boolean) {
        val lines = if (items.isEmpty()) "(belum ada)" else items.joinToString("\n") { p ->
            if (byGift) "${p.name} — x${p.total}"
            else "${p.name} · ${if (watching) "Menonton" else "Mampir"} ${fmtDur(p.seconds)}"
        }
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Dialog_List)
            .setTitle("$title (${items.size})")
            .setMessage(lines)
            .setPositiveButton("Tutup", null)
            .show()
    }

    /** Dipanggil dialog pengaturan agar loop polling terbaru tetap memakai klien aktif. */
    fun currentClient(): ServerClient = client

    // --------------------------------------------------------------- theme
    private fun applyTheme(settings: AppSettings) {
        val s = settings
        // Samakan font seluruh window (dialog, tombol, spinner) dengan Roboto
        runCatching { theme.applyStyle(R.style.AppFont, true) }
        findViewById<View>(R.id.frameRoot).let { Theme.apply(it, Theme.palette(s), s) }
        runCatching { sparkLayer.setColors(s) }
        adaptersSettings(s)
        // notify pada main thread; re-render snapshot agar warna/font ikut berubah
        runOnUiThread {
            adapters.first.notifyDataSetChanged()
            adapters.second.notifyDataSetChanged()
            adapters.third.notifyDataSetChanged()
        }
    }

    private fun onSettingsSaved(s: AppSettings) {
        applyTheme(s)
        applyWallpaper()
    }

    /** Dipakai dialog: tombol Hubungkan/Putus membaca status berjalan. */
    fun liveRunning(): Boolean = lastSnapshot?.mode == "live" || lastSnapshot?.mode == "demo"

    // ------------------------------------------------------------ wallpaper
    /** Gambar latar pilihan user + gradasi tema + lapisan gelap. */
    private fun applyWallpaper() {
        try {
            applyWallpaperInner()
        } catch (e: Exception) {
            android.util.Log.e("TTL_WALL", "wallpaper gagal", e)
        }
    }

    private fun applyWallpaperInner() {
        val s = prefs.toSettings()
        val pal = Theme.palette(s)

        val grad = findViewById<View>(R.id.wallpaperGradient)
        val scrim = findViewById<View>(R.id.wallpaperScrim)
        val img = findViewById<android.widget.ImageView>(R.id.wallpaperImage)

        // Gradasi tema SELALU di lapisan paling bawah (juga terlihat bila gambar
        // tidak muat penuh), lalu gambar, lalu lapisan gelap tipis.
        grad.background = com.ombaharbangkit.tiktoklivereader.ui.Wallpaper.gradient(s)
        grad.bringToFront()
        img.bringToFront()

        val bmp = com.ombaharbangkit.tiktoklivereader.ui.Wallpaper.loadImage(contentResolver, prefs.wallpaperUri)
        android.util.Log.i("TTL_WALL", "uri=${prefs.wallpaperUri} bmp=${bmp?.width}x${bmp?.height}")
        if (bmp != null) {
            img.setImageBitmap(bmp)
            img.visibility = View.VISIBLE
            // Gambar ADA: scrim dibuat sangat ringan supaya wallpaper tetap jelas
            scrim.background = com.ombaharbangkit.tiktoklivereader.ui.Wallpaper.scrim(45)
        } else {
            img.setImageDrawable(null)
            img.visibility = View.GONE
            scrim.background = com.ombaharbangkit.tiktoklivereader.ui.Wallpaper.scrim(if (pal.dark) 95 else 150)
        }
        scrim.bringToFront()
        sparkLayer.bringToFront()
        findViewById<View>(R.id.frameRoot).bringToFront()
        sparkLayer.setColors(s)
    }

    /** Buka galeri untuk memilih gambar latar (dipanggil dari dialog Pengaturan). */
    fun pickWallpaper() = bgPicker.launch(arrayOf("image/*"))

    /** Salin gambar pilihan ke file app (wallpaper.jpg) -> tidak bergantung izin URI galeri. */
    private fun copyWallpaperToApp(uri: android.net.Uri): String? {
        return try {
            val out = java.io.File(filesDir, "wallpaper.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            android.net.Uri.fromFile(out).toString()
        } catch (e: Exception) {
            android.util.Log.w("TTL_WALL", "copy gagal: ${e.message}")
            null
        }
    }

    fun clearWallpaperImage() {
        runCatching { java.io.File(filesDir, "wallpaper.jpg").delete() }
        prefs.wallpaperUri = ""
        applyWallpaper()
    }

    /** Efek hati mekar di posisi ketukan (tidak mengganggu klik view lain). */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
            val now = System.currentTimeMillis()
            if (now - lastBurst > 260) {
                lastBurst = now
                runCatching { sparkLayer.burst(ev.x + 40f, ev.y + 40f) }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Kembalikan mode full-screen bila sistem bar muncul lagi (mis. setelah keyboard). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("since", since)
        super.onSaveInstanceState(outState)
    }

    private fun onServerChanged() {
        client = ServerClient(prefs.serverUrl, prefs.apiKey)
        since = 0
        lastSnapshot = null
        shownComments = 0
        seenComments = mutableSetOf()
        seenGifts = mutableSetOf()
        seenJoins = mutableSetOf()
        overlayPrimed = false
        ImageLoaderClear()
        lifecycleScope.launch {
            if (prefs.serverUrl.isEmpty()) {
                maybeAutoDiscover()
            } else {
                pollOnce()
            }
        }
    }
}
