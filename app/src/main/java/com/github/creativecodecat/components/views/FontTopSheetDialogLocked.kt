package com.github.creativecodecat.components.views

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.helper.CustomFontView
import com.github.droidworksstudio.mlauncher.helper.FontManager
import com.google.android.material.card.MaterialCardView

/**
 * TopSheetDialog using CardView for reliable rounded bottom corners.
 */
class FontTopSheetDialogLocked(context: Context) : AppCompatDialog(context), CustomFontView {

    private val coordinator: CoordinatorLayout
    private val sheet: CardView
    private var cancelableFlag = true

    override fun setCancelable(flag: Boolean) {
        super.setCancelable(flag)
        cancelableFlag = flag
    }

    init {
        // Coordinator layout
        coordinator = CoordinatorLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { if (cancelableFlag) dismiss() }
        }

        // Sheet wrapped in CardView
        sheet = MaterialCardView(context).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }

            cardElevation = 16 * context.resources.displayMetrics.density
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimaryBackground))
        }


        coordinator.addView(sheet)
        super.setContentView(coordinator)

        // Window setup
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP)
            attributes = attributes.apply {
                dimAmount = 0.5f
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }

        // Register font manager
        FontManager.register(this)
    }

    override fun setContentView(view: View) {
        sheet.removeAllViews()
        sheet.addView(
            view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    override fun onStart() {
        super.onStart()

        // Apply font recursively
        window?.decorView?.let { rootView ->
            FontManager.getTypeface(context)?.let { typeface ->
                applyFontRecursively(rootView, typeface)
            }
        }

        // Add padding for gesture/status bar
        val paddingBottom = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_side_margin)

        sheet.setContentPadding(
            sheet.paddingLeft,
            sheet.paddingTop,
            sheet.paddingRight,
            paddingBottom
        )

        // Apply side margins
        val sideMargin = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_side_margin)
        val lp = sheet.layoutParams as CoordinatorLayout.LayoutParams
        lp.setMargins(sideMargin, lp.topMargin, sideMargin, lp.bottomMargin)
        sheet.layoutParams = lp

        // Slide down from top
        sheet.translationY = -sheet.height.toFloat()
        sheet.animate().translationY(0f).setDuration(250).start()
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
            is ViewGroup -> (0 until view.childCount).forEach {
                applyFontRecursively(view.getChildAt(it), typeface)
            }
        }
    }
}
