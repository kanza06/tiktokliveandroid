package com.ombaharbangkit.tiktoklivereader.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.ombaharbangkit.tiktoklivereader.model.AppSettings
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.math.min

/**
 * Hiasan latar: bintang ✦ dan hati ♥ kecil melayang naik perlahan (seperti
 * halaman tema di referensi), + ["burst"] untuk efek hati saat layar diketuk.
 *
 * Catatan: view ini TIDAK menghalangi sentuhan (onTouchEvent tidak menangkap),
 * jadi komentar & tombol tetap bisa ditekan.
 */
class SparkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Star(
        var x: Float, var y: Float, var r: Float, var speed: Float,
        var phase: Float, var alpha: Float, val heart: Boolean
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heartPath = Path()
    private val stars = ArrayList<Star>()
    private val rnd = Random(System.nanoTime())
    private var colorA = Color.parseColor("#FFA6D5")
    private var colorB = Color.parseColor("#C8A2FF")
    private var animator: ValueAnimator? = null
    private var lastT = 0L
    private val d = resources.displayMetrics.density

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Warna hiasan mengikuti warna aksen tema. */
    fun setColors(settings: AppSettings) {
        val p = Theme.palette(settings)
        colorA = p.accent
        colorB = p.accent2
        invalidate()
    }

    fun start() {
        if (animator?.isRunning == true) return
        lastT = System.currentTimeMillis()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = System.currentTimeMillis()
                val dt = ((now - lastT) / 1000f).coerceIn(0f, 0.1f)
                lastT = now
                step(dt)
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    private val maxStars = 64

    private fun trim() {
        while (stars.size > maxStars) stars.removeAt(0)
    }

    private fun spawn(w: Int, h: Int, anywhere: Boolean = false) {
        // sebar merata: posisi vertikal acak di seluruh layar (bukan menumpuk di bawah)
        stars.add(
            Star(
                x = rnd.nextFloat() * w,
                y = if (anywhere) rnd.nextFloat() * h else h + rnd.nextFloat() * 80 * d,
                r = (1.2f + rnd.nextFloat() * 2.6f) * d,
                speed = (6f + rnd.nextFloat() * 18f) * d,
                phase = rnd.nextFloat() * 6.28f,
                alpha = 0.20f + rnd.nextFloat() * 0.55f,
                heart = rnd.nextFloat() < 0.4f
            )
        )
        trim()
    }

    private fun step(dt: Float) {
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        while (stars.size < maxStars) spawn(w, h, anywhere = true)
        val it = stars.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.y -= s.speed * dt
            s.phase += dt * 1.6f
            s.x += sin(s.phase) * 6 * d * dt
            if (s.y < -20 * d) it.remove()
        }
    }

    /** View hiasan tidak boleh menahan sentuhan (ketukan diteruskan ke komentar). */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

    /** Efek hati mekar saat layar diketuk. */
    fun burst(x: Float, y: Float) {
        val n = 8
        for (i in 0 until n) {
            val ang = (i.toFloat() / n) * 6.283f
            val s = Star(
                x = x + cos(ang) * 6 * d,
                y = y + sin(ang) * 6 * d,
                r = (2.5f + rnd.nextFloat() * 2.5f) * d,
                speed = (26f + rnd.nextFloat() * 18f) * d,
                phase = ang,
                alpha = 0.95f,
                heart = true
            )
            stars.add(s)
        }
        trim()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        stars.forEachIndexed { i, s ->
            paint.color = if (i % 2 == 0) colorA else colorB
            // hiasan lebih terlihat (sebelumnya terlalu pudar di atas wallpaper)
            paint.alpha = ((s.alpha * 0.55f + 0.45f) * 255).toInt().coerceIn(0, 255)
            val twinkle = 0.75f + 0.25f * sin(s.phase * 1.7f)
            val r = s.r * twinkle
            if (!s.heart) {
                drawStar(canvas, s.x, s.y, r * 2.4f)
            } else {
                drawHeart(canvas, s.x, s.y, r * 2.6f)
            }
        }
    }

    /** Bintang 4 sudut (✦) — lebih kecil & manis daripada bintang 8 sudut. */
    private fun drawStar(c: Canvas, cx: Float, cy: Float, radius: Float) {
        val path = Path()
        val inner = radius * 0.22f
        for (i in 0 until 8) {
            val ang = (i * 3.14159f / 4f) - 1.5708f
            val rr = if (i % 2 == 0) radius else inner
            val px = cx + cos(ang) * rr
            val py = cy + sin(ang) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, paint)
    }

    private fun drawHeart(c: Canvas, cx: Float, cy: Float, size: Float) {
        val w = size
        val h = size
        heartPath.reset()
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        heartPath.moveTo(r.centerX(), r.bottom)
        heartPath.cubicTo(r.left - r.width() * 0.2f, r.centerY(), r.left + r.width() * 0.1f, r.top,
            r.centerX(), r.top + r.height() * 0.28f)
        heartPath.cubicTo(r.right - r.width() * 0.1f, r.top, r.right + r.width() * 0.2f, r.centerY(),
            r.centerX(), r.bottom)
        heartPath.close()
        c.drawPath(heartPath, paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stars.clear()
        // langsung penuhi seluruh layar saat pertama tampil
        repeat(maxStars) { spawn(w, h, anywhere = true) }
    }
}
