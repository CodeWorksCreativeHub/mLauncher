package com.github.creativecodecat.components.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.helper.CustomFontView
import com.github.codeworkscreativehub.mlauncher.helper.FontManager

class FontSearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SearchView(context, attrs), CustomFontView {

    private var searchEditText: EditText? = null

    init {
        try {
            // Get the internal EditText used for input
            searchEditText = findViewById(androidx.appcompat.R.id.search_src_text)
            FontManager.register(this) // Register for global font updates
            applyFont(FontManager.getTypeface(context)) // Apply font on init
        } catch (e: Exception) {
            AppLogger.e("FontSearchView", "Font init error", e)
        }
    }

    override fun applyFont(typeface: Typeface?) {
        try {
            searchEditText?.typeface = typeface
        } catch (e: Exception) {
            AppLogger.e("FontSearchView", "Failed to apply font", e)
        }
    }
}
