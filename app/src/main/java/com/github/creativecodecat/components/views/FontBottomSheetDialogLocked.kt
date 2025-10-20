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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.R as MaterialR

/**
 * BottomSheetDialog that:
 * - Disables swipe-to-dismiss
 * - Keeps tap outside, back button, and programmatic `.hide()` working
 */

class FontBottomSheetDialogLocked(context: Context) : AppCompatDialog(context), CustomFontView {

    private var coordinator: CoordinatorLayout = CoordinatorLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private var sheet: FrameLayout = FrameLayout(context).apply {
        id = MaterialR.id.design_bottom_sheet
        layoutParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        elevation = 16 * context.resources.displayMetrics.density
    }

    private var behavior: BottomSheetBehavior<FrameLayout>
    private var cancelableFlag = true

    override fun setCancelable(flag: Boolean) {
        super.setCancelable(flag)
        cancelableFlag = flag
    }

    init {
        // Coordinator
        coordinator = CoordinatorLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { if (cancelableFlag) dismiss() }
        }


        // Bottom sheet layout params with behavior attached
        val lp = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            width = ViewGroup.LayoutParams.MATCH_PARENT
            behavior = BottomSheetBehavior<FrameLayout>()
            // optional: no margins
            setMargins(0, 0, 0, 0)
        }

        // Sheet
        sheet = FrameLayout(context).apply {
            id = MaterialR.id.design_bottom_sheet
            layoutParams = lp
            // background drawable (with rounded top corners)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                val radiusPx = 16 * context.resources.displayMetrics.density
                cornerRadii = floatArrayOf(
                    radiusPx, radiusPx, // top-left
                    radiusPx, radiusPx, // top-right
                    0f, 0f,             // bottom-right
                    0f, 0f              // bottom-left
                )
                setColor(ContextCompat.getColor(context, R.color.colorPrimaryBackground))
            }
            elevation = 16 * context.resources.displayMetrics.density
        }


        // Add sheet to coordinator
        coordinator.addView(sheet)
        super.setContentView(coordinator)

        // Get behavior safely
        behavior = BottomSheetBehavior.from(sheet).apply {
            isDraggable = false
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        // Window dim
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                dimAmount = 0.5f
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }

        // Font manager
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

        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // Force full width
        sheet.layoutParams = (sheet.layoutParams as CoordinatorLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.BOTTOM
        }
        sheet.requestLayout()

        // Apply font
        window?.decorView?.let { rootView ->
            FontManager.getTypeface(context)?.let { typeface ->
                applyFontRecursively(rootView, typeface)
            }
        }

        val paddingBottom = when (isGestureNavigationEnabled(context)) {
            true -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav)
            false -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav)
        }

        sheet.setPadding(
            sheet.paddingLeft,
            sheet.paddingTop,
            sheet.paddingRight,
            paddingBottom
        )

        // Determine side margins
        val sideMargin = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_side_margin)

        // Update LayoutParams for the sheet
        val lp = sheet.layoutParams as CoordinatorLayout.LayoutParams
        lp.setMargins(sideMargin, lp.topMargin, sideMargin, lp.bottomMargin)
        sheet.layoutParams = lp
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