package com.github.creativecodecat.components.views

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.droidworksstudio.mlauncher.helper.CustomFontView
import com.github.droidworksstudio.mlauncher.helper.FontManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * BottomSheetDialog that:
 * - Disables swipe-to-dismiss
 * - Keeps tap outside, back button, and programmatic `.hide()` working
 */

class FontBottomSheetDialogLocked(context: Context) : BottomSheetDialog(context), CustomFontView {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        behavior.apply {
            isDraggable = false         // ❌ Prevent swipe-to-dismiss
            skipCollapsed = true        // ✅ Always start expanded
            // DO NOT set isHideable = false — this allows `.hide()` to still work
        }

        FontManager.register(this)
    }

    override fun onStart() {
        super.onStart()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // Apply font after views are attached
        window?.decorView?.let { rootView ->
            FontManager.getTypeface(context)?.let { typeface ->
                applyFontRecursively(rootView, typeface)
            }
        }
    }

    override fun applyFont(typeface: Typeface?) {
        window?.decorView?.let { rootView ->
            applyFontRecursively(rootView, typeface)
        }
    }

    private fun applyFontRecursively(view: View, typeface: Typeface?) {
        if (typeface == null) return

        when (view) {
            is TextView -> view.typeface = typeface
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyFontRecursively(view.getChildAt(i), typeface)
                }
            }
        }
    }
}