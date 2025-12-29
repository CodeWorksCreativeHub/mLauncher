package com.github.codeworkscreativehub.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import kotlin.math.min
import kotlin.random.Random

object ColorIconsExtensions {
    fun getDominantColor(drawable: Drawable): Int {
        // Convert Drawable to Bitmap
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
                val bmp = createBitmap(width, height)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }

        // Scale down for performance
        val scaledBitmap = bitmap.scale(min(bitmap.width / 4, 1280), min(bitmap.height / 4, 1280))

        // Use Palette to get dominant colors
        val palette = Palette.from(scaledBitmap)
            .maximumColorCount(128)
            .generate()

        val colors = palette.swatches.map { it.rgb }

        return increaseColorVibrancy(combineColors(colors))
    }


    private fun combineColors(colors: List<Int>): Int {
        if (colors.isEmpty()) return Color.DKGRAY

        val (red, green, blue) = colors.fold(Triple(0, 0, 0)) { acc, color ->
            Triple(
                acc.first + Color.red(color),
                acc.second + Color.green(color),
                acc.third + Color.blue(color)
            )
        }

        val count = colors.size
        return Color.rgb(red / count, green / count, blue / count)
    }

    private fun increaseColorVibrancy(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        // Boost saturation
        hsl[1] = (hsl[1] * 15f).coerceIn(0f, 1f)

        return ColorUtils.HSLToColor(hsl)
    }

    fun recolorDrawable(drawable: Drawable, color: Int): Drawable {
        val wrapped = DrawableCompat.wrap(drawable).mutate()
        DrawableCompat.setTint(wrapped, color)
        return wrapped
    }
}

object ColorManager {
    private var cachedBaseColor: Int? = null
    private var cachedColors: List<Int> = emptyList()

    fun getRandomHueColors(baseColor: Int, count: Int): List<Int> {
        if (baseColor != cachedBaseColor) {
            cachedBaseColor = baseColor
            cachedColors = generateHueVariants(baseColor, count)
        }
        return cachedColors
    }

    private fun generateHueVariants(baseColor: Int, count: Int): List<Int> {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)

        return List(count) { index ->
            val newHue = (360f / count) * index + Random.nextFloat() * (360f / count) // randomized within a slice
            val newHSV = floatArrayOf(newHue % 360f, hsv[1], hsv[2])
            Color.HSVToColor(newHSV)
        }
    }
}