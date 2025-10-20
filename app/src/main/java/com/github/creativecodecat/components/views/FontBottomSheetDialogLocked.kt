package com.github.creativecodecat.components.views

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.github.droidworksstudio.common.isGestureNavigationEnabled
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.helper.CustomFontView
import com.github.droidworksstudio.mlauncher.helper.FontManager
import com.google.android.material.card.MaterialCardView

/**
 * BottomSheetDialog using CardView with a grab line at the top.
 */
class FontBottomSheetDialogLocked(context: Context) : AppCompatDialog(context), CustomFontView {

    private val coordinator: CoordinatorLayout
    private val sheet: MaterialCardView
    private val contentWrapper: FrameLayout
    private var cancelableFlag = true

    override fun setCancelable(flag: Boolean) {
        super.setCancelable(flag)
        cancelableFlag = flag
    }

    init {
        val density = context.resources.displayMetrics.density

        // Coordinator layout
        coordinator = CoordinatorLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { if (cancelableFlag) dismiss() }
        }

        // Sheet wrapped in MaterialCardView
        sheet = MaterialCardView(context).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }

            cardElevation = 16 * density
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimaryBackground))
        }

        // Grab line at top
        val grabLine = View(context).apply {
            val widthPx = (40 * density).toInt()
            val heightPx = (4 * density).toInt()
            val topMarginPx = (10 * density).toInt()
            val bottomMarginPx = (8 * density).toInt()

            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = topMarginPx
                bottomMargin = bottomMarginPx
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2 * density
                setColor(ContextCompat.getColor(context, R.color.colorAccent))
            }
        }

        // Content wrapper inside sheet
        contentWrapper = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(grabLine) // always at index 0
        }

        sheet.addView(contentWrapper)
        coordinator.addView(sheet)
        super.setContentView(coordinator)

        // Window setup
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                dimAmount = 0.5f
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }

        // Register font manager
        FontManager.register(this)
    }

    override fun setContentView(view: View) {
        // Remove old content except the grab line (index 0)
        for (i in contentWrapper.childCount - 1 downTo 1) {
            contentWrapper.removeViewAt(i)
        }

        // Remove parent if the view already has one
        (view.parent as? ViewGroup)?.removeView(view)

        val density = context.resources.displayMetrics.density
        contentWrapper.addView(
            view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (22 * density).toInt() // push below grab line
            })
    }

    override fun onStart() {
        super.onStart()

        // Apply font recursively
        window?.decorView?.let { rootView ->
            FontManager.getTypeface(context)?.let { applyFontRecursively(rootView, it) }
        }

        // Add padding for gesture navigation
        val bottomMargin = when (isGestureNavigationEnabled(context)) {
            true -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav)
            false -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav)
        }

        val sideMargin = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_side_margin)
        val lp = sheet.layoutParams as CoordinatorLayout.LayoutParams
        lp.setMargins(sideMargin, lp.topMargin, sideMargin, bottomMargin)
        sheet.layoutParams = lp

        // Slide up animation from bottom
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
