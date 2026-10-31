package com.ombaharbangkit.tiktoklivereader.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ombaharbangkit.tiktoklivereader.R
import com.ombaharbangkit.tiktoklivereader.data.ServerClient
import com.ombaharbangkit.tiktoklivereader.model.AppSettings
import com.ombaharbangkit.tiktoklivereader.model.CommentItem
import com.ombaharbangkit.tiktoklivereader.model.GiftItem
import com.ombaharbangkit.tiktoklivereader.model.JoinItem

/**
 * Adapter feed — dibalik (terbaru di atas) sama seperti Feed(newest_top=True)
 * di aplikasi Py: data di-reverse sebelum ditampilkan.
 */

/**
 * Avatar huruf awal nama (lingkaran warna). Dulu ukuran lingkaran diambil dari
 * layoutParams.width yang masih -2 (wrap_content) saat bind -> bitmap 0px,
 * akibatnya huruf avatar tidak pernah muncul. Sekarang ukuran diambil dari
 * dimensi view (atau default 28dp) dan selalu set minimum size.
 */
internal fun hasEmojiText(text: String): Boolean = text.any { it.code in 0x2000..0x2BFF }

/** Avatar foto asli pengguna (fallback huruf bila URL kosong). */
internal fun styleAvatarPhoto(view: AvatarView, name: String, avatarUrl: String?, base: String) {
    view.setRing(0x33000000)
    if (avatarUrl.isNullOrBlank() || base.isBlank()) view.loadInitial(name, AppSettings())
    else view.load(name, avatarUrl, base, AppSettings())
}

internal fun styleAvatar(view: TextView, name: String) {
    val d = view.resources.displayMetrics.density
    val lp = view.layoutParams
    val sizeDp = when {
        lp != null && lp.width > 0 -> lp.width / d
        view.width > 0 -> view.width / d
        else -> 28f
    }
    view.background = Theme.circle(Theme.avatarColor(name), sizeDp, d)
    view.text = Theme.initial(name)
    view.setTextColor(Color.parseColor("#0B0B10"))
    view.typeface = Theme.emoji(AppSettings())
    view.includeFontPadding = false
    view.minWidth = (sizeDp * d).toInt()
    view.minHeight = (sizeDp * d).toInt()
    view.setPadding(0, 0, 0, 0)
}



class CommentAdapter :
    RecyclerView.Adapter<CommentAdapter.Holder>() {

    var settings: AppSettings = AppSettings()
    private val items = ArrayList<CommentItem>()

    var serverBase: String = ""
    /** id komentar baru → di-highlight sekali (bubble berwarna aksen) */
    var highlightIds: Set<Long> = emptySet()

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: AvatarView = v.findViewById(R.id.avatar)
        val user: TextView = v.findViewById(R.id.user)
        val text: TextView = v.findViewById(R.id.text)
        val bubble: FrameLayout = v.findViewById(R.id.commentBubble)
        val neon: NeonBubbleView = v.findViewById(R.id.neonFrame)
    }

    /** Lama sorotan komentar baru (ms) — sesudah ini bubble kembali normal. */
    var highlightMs: Long = 2000
    private val highlightUntil = HashMap<Long, Long>()

    fun submit(list: List<CommentItem>, fresh: Set<Long> = emptySet()) {
        val incoming = list.reversed()
        // catat batas waktu sorotan untuk komentar yang BARU masuk
        val now = System.currentTimeMillis()
        fresh.forEach { highlightUntil[it] = now + highlightMs }
        highlightIds = highlightUntil.filterValues { it > now }.keys
        if (items.size == incoming.size && items.map { it.id } == incoming.map { it.id }) {
            if (fresh.isNotEmpty()) notifyDataSetChanged()
            return
        }
        items.clear()
        items.addAll(incoming)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    /** Warna bubble komentar baru: campuran 18% warna aksen di atas warna panel tema. */
    private fun highlightColor(pal: Theme.Palette): Int = Theme.mix(pal.panel, pal.accent, 0.18f)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val c = items[position]
        val s = settings
        h.itemView.setBackgroundColor(Color.TRANSPARENT)
        // animasi masuk untuk komentar baru: memudar + naik sedikit
        if (highlightIds.contains(c.id)) {
            h.itemView.alpha = 0f
            h.itemView.translationY = 18f * h.itemView.resources.displayMetrics.density
            h.itemView.animate().alpha(1f).translationY(0f).setDuration(260).start()
        } else {
            h.itemView.alpha = 1f
            h.itemView.translationY = 0f
        }
        // FOTO PROFIL asli dari TikTok (lewat proxy server)
        styleAvatarPhoto(h.avatar, c.user, c.avatar, serverBase)

        val pal = Theme.palette(s)
        val isNew = highlightIds.contains(c.id)

        // Bubble: memakai drawable XML tema; komentar baru diberi sorotan aksen
        // BUBLE KACA: isi transparan (wallpaper tetap terlihat) + garis tipis.
        //  - baru   : garis & isi aksen (disorot)
        //  - biasa  : isi gelap/terang transparan + garis tipis aksen samar
        // Kontras: isi lebih pekat dari sebelumnya (92 -> 165) supaya teks tetap
        // terbaca walau wallpaper terang, tapi masih tembus pandang.
        val alpha = if (pal.dark) 165 else 190           // 0..255
        val glassFill = Color.argb(
            alpha, Color.red(pal.panel), Color.green(pal.panel), Color.blue(pal.panel)
        )
        val glassStroke = Color.argb(150, Color.red(pal.accent), Color.green(pal.accent), Color.blue(pal.accent))
        // LED berjalan di tepi bubble: aktif untuk SEMUA tema gelap (tema adalah
        // penentu, bukan setting terpisah). Tema terang tetap bubble kaca biasa.
        val neonOn = pal.dark
        // warna isi saat sorotan vs normal (dipakai juga oleh callback 2 detik)
        val hiFill = Color.argb(215, Color.red(pal.panel), Color.green(pal.panel), Color.blue(pal.panel))
        if (neonOn) {
            h.bubble.background = null
            h.neon.visibility = View.VISIBLE
            h.neon.setPalette(if (isNew) hiFill else glassFill, pal.accent, pal.accent2, 18f)
            h.neon.speed = if (isNew) 260f else 130f
        } else {
            h.neon.visibility = View.GONE
            if (isNew) {
                // sorotan lebih terlihat: isi gelap pekat + garis aksen tebal
                h.bubble.background = Theme.rounded(hiFill, 18f, pal.accent, h.bubble, 2)
            } else {
                h.bubble.background = Theme.rounded(glassFill, 18f, glassStroke, h.bubble, 1)
            }
        }

        // Sorotan HANYA sebentar (2 detik) lalu balik ke bubble kaca biasa.
        // Dijadwalkan per baris agar tidak menunggu polling berikutnya.
        if (isNew) {
            h.bubble.postDelayed({
                // pastikan baris ini belum di-rebind ke komentar lain
                if (h.itemView.isAttachedToWindow) {
                    if (neonOn) {
                        // JANGAN timpa latar: bubble tetap memakai LED, hanya
                        // kecepatan & kepadatan yang kembali normal
                        h.neon.setPalette(glassFill, pal.accent, pal.accent2, 18f)
                        h.neon.speed = 130f
                    } else {
                        h.bubble.background = Theme.rounded(glassFill, 18f, glassStroke, h.bubble, 1)
                    }
                    h.user.setTextColor(pal.accent2)
                }
            }, highlightMs)
        }

        // Nama di baris atas buble (aksen merah, bold)
        h.user.visibility = View.VISIBLE
        h.user.text = c.user
        h.user.setTextColor(if (isNew) pal.accent else pal.accent2)
        h.user.textSize = ((s.fontSize - 3).coerceAtLeast(9)).toFloat()
        h.user.typeface = if (hasEmojiText(c.user)) Theme.emoji(s) else Theme.bold(s)

        // Isi komentar: teks valid & terbaca (emoji tetap tampil)
        val sb = SpannableStringBuilder(c.text)
        sb.setSpan(ForegroundColorSpan(pal.text), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        h.text.text = sb
        h.text.setTextColor(pal.text)
        h.text.textSize = (s.fontSize + 1).toFloat()
        h.text.typeface = if (hasEmojiText(c.text)) Theme.emoji(s) else Theme.contentFont(s)
        h.text.maxLines = 6
        h.text.ellipsize = android.text.TextUtils.TruncateAt.END
    }
}

class GiftAdapter(private val baseUrlProvider: () -> String) :
    RecyclerView.Adapter<GiftAdapter.Holder>() {

    var settings: AppSettings = AppSettings()
    var serverBase: String = ""
    private val items = ArrayList<GiftItem>()

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: AvatarView = v.findViewById(R.id.avatar)
        val image: ImageView = v.findViewById(R.id.giftImage)
        val user: TextView = v.findViewById(R.id.user)
        val giftName: TextView = v.findViewById(R.id.giftName)
        val count: TextView = v.findViewById(R.id.count)
    }

    fun submit(list: List<GiftItem>) {
        // tampung lebih banyak supaya baris lama tidak "hilang" begitu ada gift baru
        val incoming = list.reversed()
        if (items.size == incoming.size && items.map { it.id } == incoming.map { it.id }) return
        items.clear()
        items.addAll(incoming)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_gift, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val g = items[position]
        val s = settings
        val pal = Theme.palette(s)
        val d = h.itemView.resources.displayMetrics.density
        h.itemView.background = Theme.rounded(pal.panel2, 20f, 0, h.itemView, 0)
        // base harus SELALU dari klien aktif (server bisa ditemukan otomatis / berubah)
        val baseAvatar = baseUrlProvider().ifBlank { serverBase }
        styleAvatarPhoto(h.avatar, g.user, g.avatar, baseAvatar)
        h.user.text = g.user
        h.giftName.text = "mengirim ${g.gift}"
        h.count.text = "x${g.count}"
        h.user.setTextColor(pal.text)
        h.user.textSize = s.fontSize.toFloat()
        h.giftName.setTextColor(pal.muted)
        h.giftName.textSize = (s.fontSize - 2).coerceAtLeast(9).toFloat()
        h.count.setTextColor(pal.accent)
        h.count.textSize = s.fontSize.toFloat()
        Theme.bold(s)?.let { h.count.typeface = it }
        // base harus SELALU dari klien aktif (server bisa ditemukan otomatis / berubah)
        val base = baseUrlProvider().ifBlank { serverBase }
        val url = if (g.image.isBlank()) "" else ServerClient.proxyUrl(base, g.image)
        h.image.visibility = if (url.isBlank()) View.GONE else View.VISIBLE
        h.image.layoutParams = h.image.layoutParams.apply {
            width = (32 * d).toInt()
            height = (32 * d).toInt()
        }
        // kalau gambar gagal/berubah, muat ulang saat baris ini muncul lagi
        h.image.tag = url
        if (url.isNotBlank()) ImageLoader.load(h.image, url)
        else h.image.setImageDrawable(null)
    }
}

class JoinAdapter :
    RecyclerView.Adapter<JoinAdapter.Holder>() {

    var settings: AppSettings = AppSettings()
    var serverBase: String = ""
    private val items = ArrayList<JoinItem>()

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val a1: AvatarView = v.findViewById(R.id.avatar1)
        val n1: TextView = v.findViewById(R.id.name1)
        val sep: TextView = v.findViewById(R.id.sep)
        val a2: AvatarView = v.findViewById(R.id.avatar2)
        val n2: TextView = v.findViewById(R.id.name2)
    }

    fun submit(list: List<JoinItem>) {
        val incoming = list.reversed()
        if (items.size == incoming.size && items.map { it.id } == incoming.map { it.id }) return
        items.clear()
        items.addAll(incoming)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_join, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val j = items[position]
        val dd = h.itemView.resources.displayMetrics.density
        h.itemView.background = Theme.rounded(Theme.palette(settings).panel2, 20f, 0, h.itemView, 0)
        h.a1.layoutParams = h.a1.layoutParams.apply { width = (24 * dd).toInt(); height = (24 * dd).toInt() }
        h.a2.layoutParams = h.a2.layoutParams.apply { width = (24 * dd).toInt(); height = (24 * dd).toInt() }
        val s = settings
        val pal = Theme.palette(s)
        val first = j.names.getOrNull(0) ?: ""
        val second = j.names.getOrNull(1)
        styleAvatarPhoto(h.a1, first, j.avatars.getOrNull(0), serverBase)
        h.n1.text = "👋 $first bergabung"
        h.n1.setTextColor(pal.text)
        h.n1.textSize = s.fontSize.toFloat()
        h.n1.typeface = if (hasEmojiText(h.n1.text.toString())) Theme.emoji(s) else Theme.typefaceFor(s)
        val showSecond = !second.isNullOrBlank()
        h.sep.visibility = if (showSecond) View.VISIBLE else View.GONE
        h.a2.visibility = if (showSecond) View.VISIBLE else View.GONE
        h.n2.visibility = if (showSecond) View.VISIBLE else View.GONE
        if (showSecond) {
            styleAvatarPhoto(h.a2, second!!, j.avatars.getOrNull(1), serverBase)
            h.n2.text = "$second bergabung"
            h.n2.setTextColor(pal.text)
            h.n2.textSize = s.fontSize.toFloat()
            h.n2.typeface = if (hasEmojiText(h.n2.text.toString())) Theme.emoji(s) else Theme.typefaceFor(s)
        }
        h.sep.setTextColor(pal.muted)
    }
}
