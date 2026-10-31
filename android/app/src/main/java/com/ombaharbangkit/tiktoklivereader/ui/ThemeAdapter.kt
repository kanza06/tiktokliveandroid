package com.ombaharbangkit.tiktoklivereader.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Grid pilihan tema di dialog Pengaturan: setiap kotak memakai warna asli
 * tema tersebut (latar, panel, aksen) sehingga user melihat pratinjaunya.
 */
class ThemeAdapter(
    private val ctx: Context,
    private val themes: List<Theme.ThemeDef>,
    @Volatile var selectedId: String,
    private val onPick: (Theme.ThemeDef) -> Unit
) : BaseAdapter() {

    private val d = ctx.resources.displayMetrics.density

    override fun getCount() = themes.size

    override fun getItem(position: Int) = themes[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val t = themes[position]
        val selected = t.id == selectedId
        val row = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
        }
        row.removeAllViews()

        row.background = Theme.rounded(t.bg, 12f, if (selected) t.accent else t.border, row, if (selected) 2 else 1)

        // titik aksen
        val dot = View(ctx).apply {
            background = Theme.circle(t.accent, 12f, d)
            layoutParams = LinearLayout.LayoutParams((12 * d).toInt(), (12 * d).toInt())
        }
        row.addView(dot)

        val label = TextView(ctx).apply {
            text = "${t.emoji} ${t.label}"
            setTextColor(t.text)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setPadding((8 * d).toInt(), 0, 0, 0)
            maxLines = 1
        }
        row.addView(label)

        if (selected) {
            row.addView(TextView(ctx).apply {
                text = "✓"
                setTextColor(t.accent)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding((6 * d).toInt(), 0, 0, 0)
            })
        }

        row.setOnClickListener { onPick(t) }
        return row
    }
}
