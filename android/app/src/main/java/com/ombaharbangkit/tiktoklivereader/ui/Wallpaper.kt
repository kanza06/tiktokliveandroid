package com.ombaharbangkit.tiktoklivereader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import com.ombaharbangkit.tiktoklivereader.model.AppSettings

/**
 * Latar belakang (wallpaper) aplikasi.
 *
 *  - [gradient]  : gradasi warna tema (dipakai bila tidak ada gambar) supaya latar
 *                  tidak polos/kaku.
 *  - [loadImage] : gambar pilihan user dari galeri (URI) dimuat sebagai bitmap
 *                  center-crop; pemasangan ke ImageView dilakukan MainActivity.
 */
object Wallpaper {

    fun gradient(settings: AppSettings): GradientDrawable {
        val p = Theme.palette(settings)
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                p.bg,
                Theme.mix(p.bg, p.panel2, 0.65f),
                Theme.mix(p.bg, p.accent, 0.18f)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(1, p.border)
        }
    }

    /** Lapisan gelap di atas wallpaper agar teks tetap terbaca. */
    fun scrim(alpha: Int = 120): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(alpha, 0, 0, 0), Color.argb((alpha * 0.6f).toInt(), 0, 0, 0))
    )

    /**
     * Muat gambar dari URI galeri (ContentResolver). Di-scale maksimal 1600px
     * agar hemat memori. Null bila gagal / URI kosong.
     */
    fun loadImage(resolver: android.content.ContentResolver, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            var w = 0; var h = 0
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            w = opts.outWidth; h = opts.outHeight
            var sample = 1
            while (w / sample > 1600 || h / sample > 1600) sample *= 2
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) }
        } catch (_: Exception) {
            null
        }
    }
}
