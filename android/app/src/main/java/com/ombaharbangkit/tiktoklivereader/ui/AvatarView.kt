package com.ombaharbangkit.tiktoklivereader.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import com.ombaharbangkit.tiktoklivereader.data.ServerClient
import com.ombaharbangkit.tiktoklivereader.model.AppSettings

/**
 * Avatar pengguna: menampilkan FOTO PROFIL TikTok (bulat, center-crop) yang
 * dimuat lewat proxy server.
 *
 * Sebelumnya avatar selalu berupa huruf karena view-nya TextView biasa dan URL
 * foto dari server tidak pernah dipakai. Bila foto belum ada / gagal dimuat,
 * huruf inisial tetap tampil sebagai cadangan.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private var photo: Bitmap? = null
    private var currentUrl: String? = null
    private var displayName: String = ""
    private var ringColor: Int = 0x33000000

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        setCompoundDrawables(null, null, null, null)
        maxLines = 1
    }

    /** Tampilkan foto profil (bulat). Huruf inisial dipakai bila tidak ada foto. */
    fun load(name: String, avatarUrl: String?, baseUrl: String, settings: AppSettings) {
        val url = if (avatarUrl.isNullOrBlank() || baseUrl.isBlank()) ""
        else ServerClient.proxyUrl(baseUrl, avatarUrl.trim())

        gravity = Gravity.CENTER
        textSize = (settings.fontSize - 2).coerceAtLeast(9).toFloat()
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(Theme.circleText(name))
        nonNullBg = Theme.avatarColor(name)

        if (url.isEmpty()) {
            displayName = name
            photo = null
            currentUrl = null
            text = Theme.initial(name)
            invalidate()
            return
        }
        if (url == currentUrl && photo != null) return

        currentUrl = url
        photo = null
        displayName = name
        text = Theme.initial(name)
        invalidate()

        ImageLoader.loadBitmap(url) { bmp ->
            if (currentUrl != url) return@loadBitmap
            if (bmp != null) {
                photo = bmp
                text = ""
            } else {
                // gagal: tetap tampilkan huruf awal (avatar tidak pernah kosong)
                photo = null
                text = Theme.initial(displayName)
            }
            invalidate()
        }
    }

    /** Dipakai gaya lama (tanpa foto): lingkaran warna + huruf. */
    fun loadInitial(name: String, settings: AppSettings) {
        currentUrl = null
        photo = null
        displayName = name
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(Theme.circleText(name))
        nonNullBg = Theme.avatarColor(name)
        textSize = (settings.fontSize - 2).coerceAtLeast(9).toFloat()
        text = Theme.initial(name)
        invalidate()
    }

    private var circleBgColor: Int = 0

    private var nonNullBg: Int
        get() = circleBgColor
        set(value) { circleBgColor = value }

    fun setRing(color: Int) {
        if (ringColor != color) {
            ringColor = color
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = 1.2f * resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - stroke

        // latar lingkaran warna (terlihat saat foto belum termuat / sedang dimuat)
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = circleBgColor
        canvas.drawCircle(cx, cy, radius, shapePaint)

        val bmp = photo
        if (bmp != null) {
            if (bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) {
                super.onDraw(canvas)
                return
            }
            val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(radius * 2 / bmp.width, radius * 2 / bmp.height)
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - bmp.width * scale / 2f, cy - bmp.height * scale / 2f)
            }
            shader.setLocalMatrix(m)
            bitmapPaint.shader = shader
            canvas.drawCircle(cx, cy, radius, bitmapPaint)
        } else {
            // gambar huruf inisial (dibuat oleh TextView di tengah)
            super.onDraw(canvas)
        }

        // garis tepi halus supaya avatar rapi di tema gelap & terang
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeWidth = stroke
        shapePaint.color = ringColor
        canvas.drawCircle(cx, cy, radius, shapePaint)
    }
}
