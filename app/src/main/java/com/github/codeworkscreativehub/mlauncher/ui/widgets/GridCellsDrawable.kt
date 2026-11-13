package com.github.codeworkscreativehub.mlauncher.ui.widgets

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Outlines every cell of the widget grid, so widgets can be lined up while editing.
 * Cells are square and laid out the same way as the widgets: [columns] per row, with [margin] between cells.
 */
class GridCellsDrawable(
    private val columns: Int,
    private val margin: Int,
    color: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        this.color = color
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cellWidth = (b.width() - margin * (columns - 1)) / columns
        if (cellWidth <= 0) return

        val pitch = cellWidth + margin
        val rows = ((b.height() + margin) / pitch).coerceAtLeast(1)

        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val left = b.left + col * pitch.toFloat()
                val top = b.top + row * pitch.toFloat()
                canvas.drawRect(left, top, left + cellWidth, top + cellWidth, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
