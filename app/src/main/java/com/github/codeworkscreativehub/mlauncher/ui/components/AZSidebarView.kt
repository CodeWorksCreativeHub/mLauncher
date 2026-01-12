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
import android.view.accessibility.AccessibilityEvent
import com.github.codeworkscreativehub.mlauncher.helper.CustomFontView
import com.github.codeworkscreativehub.mlauncher.helper.FontManager
import com.github.codeworkscreativehub.mlauncher.helper.sp2px

class AZSidebarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), CustomFontView {

    var onTouchStart: (() -> Unit)? = null
    var onTouchEnd: (() -> Unit)? = null
    var onLetterSelected: ((String) -> Unit)? = null

    private val allLetters = listOf('★') + ('A'..'Z')
    private var letters: List<Char> = allLetters

    private val baseTextSizeSp = 20f
    private val selectedTextSizeSp = baseTextSizeSp + 2f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        textSize = sp2px(resources, baseTextSizeSp)
        typeface = FontManager.getTypeface(context)
    }

    private var spacingFactor = 1f
    private var selectedIndex = -1
    private var itemHeight = 0f

    // Accessibility
    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        // 🔗 Register for global font updates
        FontManager.register(this)
    }

    val topBottomPaddingPx: Float
        get() = 180f * resources.displayMetrics.density

    // ✅ FIX: Calculate spacing using ACTUAL view height
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val density = resources.displayMetrics.density
        val interLetterSpacing = (letters.size - 1) * density
        val baseTextHeight = sp2px(resources, baseTextSizeSp)

        val availableHeight = h - topBottomPaddingPx - interLetterSpacing
        spacingFactor = availableHeight / (letters.size * baseTextHeight)

        itemHeight = baseTextHeight * spacingFactor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (itemHeight <= 0f) return

        val totalHeight = itemHeight * letters.size
        val startY = (height - totalHeight) / 2f

        // ✅ FIX: Typeface resolved ONCE per draw
        paint.typeface = FontManager.getTypeface(context)

        letters.forEachIndexed { index, letter ->
            val isSelected = index == selectedIndex

            paint.isFakeBoldText = isSelected
            paint.textSize = sp2px(
                resources,
                if (isSelected) selectedTextSizeSp else baseTextSizeSp
            )
            paint.color = if (isSelected) Color.WHITE else Color.GRAY

            val x = width / 2f
            val y = startY + itemHeight * index -
                    (paint.descent() + paint.ascent()) / 2f

            canvas.drawText(letter.toString(), x, y, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (itemHeight <= 0f) return false

        val totalHeight = itemHeight * letters.size
        val startY = (height - totalHeight) / 2f

        val relativeY = event.y - startY
        val index = (relativeY / itemHeight)
            .toInt()
            .coerceIn(0, letters.size - 1)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onTouchStart?.invoke()
                handleSelection(index)
            }

            MotionEvent.ACTION_MOVE -> {
                handleSelection(index)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                onTouchEnd?.invoke()
            }
        }

        return true
    }

    private fun handleSelection(index: Int) {
        if (index == selectedIndex) return

        selectedIndex = index
        val letter = letters[index].toString()

        onLetterSelected?.invoke(letter)

        // ✅ FIX: Accessibility announcement
        announceLetterForAccessibility(letter)

        invalidate()
    }

    // ✅ FIX: Works for ★ and letters
    fun setSelectedLetter(letter: String) {
        val index = letters.indexOfFirst { it.toString() == letter }
        if (index != -1 && index != selectedIndex) {
            selectedIndex = index
            invalidate()
        }
    }

    /**
     * Update sidebar letters based on available app sections.
     *
     * Example input: setOf("★", "A", "C", "D", "M")
     */
    fun setAvailableLetters(available: Set<String>) {
        letters = allLetters.filter { it.toString() in available }

        // Reset selection safely
        if (selectedIndex >= letters.size) {
            selectedIndex = -1
        }

        requestLayout()
        invalidate()
    }

    /** 🔁 Called by FontManager to update font */
    override fun applyFont(typeface: Typeface?) {
        paint.typeface = typeface
        invalidate()
    }

    private fun announceLetterForAccessibility(text: String) {
        contentDescription = text
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
    }

}
