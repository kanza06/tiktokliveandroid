package com.ombaharbangkit.tiktoklivereader.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Bingkai bubble dengan efek LED berjalan (running LED) di tepinya.
 *
 * Cara kerja: digambar sebagai CINCIN (stroke sepanjang keliling rounded-rect)
 * yang diisi LinearGradient berisi beberapa "lampu". Shader digeser terus
 * menerus sehingga cahaya tampak berputar mengelilingi bubble. Ditambah satu
 * sapuan terang yang lebih terang sebagai "kepala" LED.
 *
 * Dipakai sebagai latar `commentBubble` pada item komentar (tema dark neon).
 */
class NeonBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * d
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * d
        alpha = 70
        maskFilter = android.graphics.BlurMaskFilter(3.5f * d, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    private var colorA = Color.parseColor("#00FFC6")
    private var colorB = Color.parseColor("#FF2E97")
    private var fillColor = Color.argb(165, 11, 17, 26)
    private var radiusDp = 18f
    private var offset = 0f
    private var animator: ValueAnimator? = null

    /** Kecepatan putaran LED (piksel logis per detik). */
    var speed: Float = 130f

    fun setPalette(fillColor: Int, colorA: Int, colorB: Int, radiusDp: Float = 18f) {
        this.fillColor = fillColor
        this.colorA = colorA
        this.colorB = colorB
        this.radiusDp = radiusDp
        rebuildShader()
        invalidate()
    }

    private var shader: LinearGradient? = null

    /** Versi redup dari warna (dipakai sebagai "lampu mati" di antara LED). */
    private fun dim(color: Int): Int = Color.argb(
        70, Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun rebuildShader() {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        // panjang pola ~ 2.2x lebar bubble -> terlihat 2-3 "lampu" sekaligus
        val period = (w + h) * 0.9f
        shader = LinearGradient(
            0f, 0f, period, 0f,
            // pola "lampu": terang -> redup -> terang, agar terlihat butir LED berjalan
            intArrayOf(
                colorA, dim(colorA), dim(colorB), colorB,
                dim(colorB), dim(colorA), dim(colorA), colorA
            ),
            floatArrayOf(0f, 0.13f, 0.30f, 0.45f, 0.62f, 0.78f, 0.90f, 1f),
            Shader.TileMode.REPEAT
        )
        ring.shader = shader
        glow.shader = shader
    }

    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1000f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = (it.animatedValue as Float) / 1000f
                offset = t
                applyMatrix()
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    private fun applyMatrix() {
        val s = shader ?: return
        val span = (width + height) * 0.9f
        val m = android.graphics.Matrix()
        m.setTranslate(-offset * span, 0f)
        s.setLocalMatrix(m)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
        applyMatrix()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = 4f * d
        val r = radiusDp * d
        rect.set(pad, pad, width - pad, height - pad)
        if (rect.width() <= 0 || rect.height() <= 0) return

        // isi bubble
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        fill.color = fillColor
        canvas.drawPath(path, fill)

        // LED: bingkai berputar (+ lapisan glow lembut)
        val ringPath = Path().apply {
            addRoundRect(rect, r, r, Path.Direction.CW)
        }
        canvas.drawPath(ringPath, glow)
        canvas.drawPath(ringPath, ring)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    /** View ini hanya latar — sentuhan diteruskan ke bubble. */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}
