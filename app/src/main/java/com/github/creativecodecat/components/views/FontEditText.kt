package com.github.creativecodecat.components.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.helper.CustomFontView
import com.github.codeworkscreativehub.mlauncher.helper.FontManager

class FontEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs), CustomFontView {

    init {
        try {
            FontManager.register(this)
            applyFont(FontManager.getTypeface(context))
        } catch (e: Exception) {
            AppLogger.e("FontEditText", "Font init failed", e)
        }
    }

    override fun applyFont(typeface: Typeface?) {
        try {
            if (typeface != null) {
                setTypeface(typeface, Typeface.NORMAL)
            }
        } catch (e: Exception) {
            AppLogger.e("FontEditText", "applyFont failed", e)
        }
    }
}