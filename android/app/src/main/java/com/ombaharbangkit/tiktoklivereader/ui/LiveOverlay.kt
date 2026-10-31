package com.ombaharbangkit.tiktoklivereader.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ombaharbangkit.tiktoklivereader.data.ServerClient
import com.ombaharbangkit.tiktoklivereader.model.AppSettings
import com.ombaharbangkit.tiktoklivereader.model.GiftItem
import com.ombaharbangkit.tiktoklivereader.model.JoinItem

/**
 * Tampilan "nyawa" feed TikTok Live di HP:
 *  - [showGift]  : kartu gift melayang dari bawah ke atas sambil memudar (transparan).
 *  - [showJoin]  : teks "X bergabung" mengambang, muncul lalu hilang (bukan kolom).
 *
 * Kedua overlay menggunakan layout `giftOverlay` / `joinOverlay` di activity_main.xml
 * (LinearLayout gravitasi bawah, klik-dilewati ke komentar di belakangnya).
 */
class LiveOverlay(
    private val giftLayer: LinearLayout,
    private val joinLayer: LinearLayout
) {
    private val ctx: Context = giftLayer.context
    private val d = ctx.resources.displayMetrics.density

    /** Animasi gift: naik ±120dp, alpha 0 -> 1 -> 0, lalu view dibuang. */
    fun showGift(gift: GiftItem, baseUrl: String, settings: AppSettings, onDone: () -> Unit = {}) {
        if (!giftLayer.isAttachedToWindow) return
        val pal = Theme.palette(settings)
        val card = buildGiftCard(gift, baseUrl, settings, pal)

        card.alpha = 0f
        card.translationY = 60 * d
        giftLayer.addView(card)
        trim(giftLayer, 3)

        // Naik hanya ±62dp supaya tidak pernah masuk ke area teks komentar
        val rise = ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, 60 * d, -62 * d)
        val fadeIn = ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f)
        fadeIn.duration = 380
        fadeIn.interpolator = DecelerateInterpolator()
        val fadeOut = ObjectAnimator.ofFloat(card, View.ALPHA, 1f, 0f)
        fadeOut.startDelay = 1600
        fadeOut.duration = 900

        AnimatorSet().apply {
            play(fadeIn).with(rise)
            play(fadeOut).after(fadeIn)
            duration = 2900
            interpolator = AccelerateDecelerateInterpolator()
            doOnEndCompat { safeRemove(giftLayer, card); onDone() }
            start()
        }
    }

    /** Teks mengambang "X bergabung" — tampil 2 detik lalu memudar. */
    fun showJoin(join: JoinItem, baseUrl: String, settings: AppSettings) {
        if (!joinLayer.isAttachedToWindow) return
        val pal = Theme.palette(settings)
        val names = join.names.filter { it.isNotBlank() }
        if (names.isEmpty()) return

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 22 * d
                setColor(Color.argb(230, Color.red(pal.panel2), Color.green(pal.panel2), Color.blue(pal.panel2)))
                setStroke((1 * d).toInt().coerceAtLeast(1), Color.argb(90, 255, 255, 255))
            }
            setPadding((8 * d).toInt(), (5 * d).toInt(), (14 * d).toInt(), (5 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (3 * d).toInt() }
        }

        names.take(2).forEachIndexed { i, name ->
            if (i > 0) {
                row.addView(TextView(ctx).apply {
                    text = "  ·  "
                    setTextColor(pal.muted)
                    textSize = 11f
                })
            }
            val avatar = AvatarView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt())
            }
            avatar.load(name, join.avatars.getOrNull(i), baseUrl, settings)
            row.addView(avatar)

            row.addView(TextView(ctx).apply {
                text = "👋 $name bergabung"
                setTextColor(pal.text)
                textSize = (settings.fontSize - 1).coerceAtLeast(10).toFloat()
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding((6 * d).toInt(), 0, 0, 0)
                maxLines = 1
            })
        }

        row.alpha = 0f
        row.translationX = -30 * d
        joinLayer.addView(row)
        trim(joinLayer, 4)

        val enter = ObjectAnimator.ofPropertyValuesHolder(row,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -30 * d, 0f))
        enter.duration = 320
        enter.interpolator = DecelerateInterpolator()

        val exit = ObjectAnimator.ofPropertyValuesHolder(row,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -16 * d))
        exit.startDelay = 2000
        exit.duration = 650

        AnimatorSet().apply {
            playSequentially(enter, exit)
            doOnEndCompat { safeRemove(joinLayer, row) }
            start()
        }
    }

    // ------------------------------------------------------------------ isi kartu
    private fun buildGiftCard(
        gift: GiftItem, baseUrl: String, settings: AppSettings,
        pal: Theme.Palette
    ): LinearLayout {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24 * d
                setColor(Color.argb(235, Color.red(pal.panel), Color.green(pal.panel), Color.blue(pal.panel)))
                setStroke((1 * d).toInt().coerceAtLeast(1), Color.argb(120, 255, 255, 255))
            }
            setPadding((6 * d).toInt(), (5 * d).toInt(), (12 * d).toInt(), (5 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (5 * d).toInt() }
            elevation = 6 * d
        }

        val avatar = AvatarView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt())
        }
        avatar.load(gift.user, gift.avatar, baseUrl, settings)
        card.addView(avatar)

        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()).apply {
                marginStart = (6 * d).toInt()
            }
        }
        val url = if (gift.image.isBlank()) "" else ServerClient.proxyUrl(baseUrl, gift.image)
        if (url.isNotBlank()) {
            ImageLoader.load(img, url)
            card.addView(img)
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * d).toInt() }
        }
        texts.addView(TextView(ctx).apply {
            text = gift.user
            setTextColor(pal.accent2)
            textSize = (settings.fontSize - 1).coerceAtLeast(10).toFloat()
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1
        })
        texts.addView(TextView(ctx).apply {
            text = "mengirim ${gift.gift}  x${gift.count}"
            setTextColor(pal.text)
            textSize = (settings.fontSize - 2).coerceAtLeast(9).toFloat()
            maxLines = 1
        })
        card.addView(texts)
        return card
    }

    // ------------------------------------------------------------------ bantu
    /** Batasi jumlah kartu di layar agar tidak menumpuk (yang tertua dibuang). */
    private fun trim(layer: LinearLayout, max: Int) {
        while (layer.childCount > max) {
            layer.removeViewAt(0)
        }
    }

    private fun safeRemove(layer: LinearLayout, v: View) {
        if (v.parent === layer) layer.removeView(v)
    }

    private fun android.animation.Animator.doOnEndCompat(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
            override fun onAnimationCancel(animation: android.animation.Animator) = block()
        })
    }
}
