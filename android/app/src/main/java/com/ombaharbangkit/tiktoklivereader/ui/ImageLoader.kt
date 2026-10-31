package com.ombaharbangkit.tiktoklivereader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

/**
 * Pemuat gambar avatar & gift (non-blocking, cache memori).
 * Gambar diambil lewat proxy server (/api/proxy) karena CDN TikTok bisa
 * menolak permintaan langsung dari HP.
 */
object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(200) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap.newKeySet<String>()

    fun clear() {
        cache.evictAll()
        pending.clear()
    }

    /** Muat bitmap dari proxy (avatar / gift) dan serahkan ke callback di main thread. */
    fun loadBitmap(url: String?, onResult: (Bitmap?) -> Unit) {
        if (url.isNullOrBlank()) {
            onResult(null)
            return
        }
        cache.get(url)?.let {
            main.post { onResult(it) }
            return
        }
        pool.execute {
            val bmp = fetch(url)
            if (bmp != null) cache.put(url, bmp)
            main.post { onResult(bmp) }
        }
    }

    fun load(view: ImageView, url: String?) {
        view.tag = url
        view.setImageDrawable(null)
        if (url.isNullOrBlank()) return
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        if (!pending.add(url)) return
        pool.execute {
            val bmp = fetch(url)
            pending.remove(url)
            if (bmp != null) {
                cache.put(url, bmp)
                main.post {
                    if (view.tag == url) view.setImageBitmap(bmp)
                }
            } else {
                // dicoba lagi sekali setelah jeda (CDN TikTok kadang lambat)
                main.postDelayed({
                    if (view.tag == url && cache.get(url) == null) load(view, url)
                }, 2500)
            }
        }
    }

    private fun fetch(url: String): Bitmap? {
        for (attempt in 0..1) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "TikTokLiveReader-Android")
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code == 200) {
                    conn.inputStream.use { return BitmapFactory.decodeStream(it) }
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
            if (attempt == 0) Thread.sleep(400)
        }
        return null
    }
}
