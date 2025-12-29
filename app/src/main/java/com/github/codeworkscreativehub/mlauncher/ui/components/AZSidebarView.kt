package com.github.codeworkscreativehub.mlauncher.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.github.codeworkscreativehub.mlauncher.helper.CustomFontView
import com.github.codeworkscreativehub.mlauncher.helper.FontManager
import com.github.codeworkscreativehub.mlauncher.helper.sp2px

class AZSidebarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), CustomFontView {

    var onTouchStart: (() -> Unit)? = null
    var onTouchEnd: (() -> Unit)? = null

    private val letters = listOf('★') + ('A'..'Z')
    private val baseTextSizeSp = 20f
    private val selectedTextSizeSp = baseTextSizeSp + 2f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = sp2px(resources, baseTextSizeSp)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        typeface = FontManager.getTypeface(context)
    }

    var onLetterSelected: ((String) -> Unit)? = null
    private var spacingFactor = 1f
    private var selectedIndex: Int = -1

    val topBottomPaddingPx: Float
        get() = 180 * resources.displayMetrics.density

    init {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val density = displayMetrics.density

        val topBottomPadding = topBottomPaddingPx
        val interLetterSpacing = (letters.size - 1) * density
        val availableHeight = screenHeight - topBottomPadding - interLetterSpacing
        val baseTextHeight = sp2px(resources, baseTextSizeSp)

        spacingFactor = availableHeight / (letters.size * baseTextHeight)

        // 🔗 Register for global font updates
        FontManager.register(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val itemHeight = sp2px(resources, baseTextSizeSp) * spacingFactor
        val totalHeight = itemHeight * letters.size
        val startY = (height - totalHeight) / 2f

        letters.forEachIndexed { i, letter ->
            val isSelected = i == selectedIndex

            paint.isFakeBoldText = isSelected
            paint.textSize = sp2px(resources, if (isSelected) selectedTextSizeSp else baseTextSizeSp)
            paint.color = if (isSelected) Color.WHITE else Color.GRAY
            paint.typeface = FontManager.getTypeface(context) // Refresh font if needed

            val x = width / 2f
            val y = startY + itemHeight * i - (paint.descent() + paint.ascent()) / 2

            canvas.drawText(letter.toString(), x, y, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val itemHeight = sp2px(resources, baseTextSizeSp) * spacingFactor
        val totalHeight = itemHeight * letters.size
        val startY = (height - totalHeight) / 2f

        val relativeY = event.y - startY
        val index = (relativeY / itemHeight).toInt().coerceIn(0, letters.size - 1)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (index != selectedIndex) {
                    selectedIndex = index
                    onLetterSelected?.invoke(letters[index].toString())
                    invalidate()
                }
                onTouchStart?.invoke()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onTouchEnd?.invoke()
            }
        }

        return true
    }

    fun setSelectedLetter(letter: String) {
        val char = letter.firstOrNull()?.uppercaseChar() ?: return
        val index = letters.indexOf(char)
        if (index != -1 && selectedIndex != index) {
            selectedIndex = index
            invalidate()
        }
    }

    /** 🔁 Called by FontManager to update font */
    override fun applyFont(typeface: Typeface?) {
        paint.typeface = typeface
        invalidate()
    }
}
