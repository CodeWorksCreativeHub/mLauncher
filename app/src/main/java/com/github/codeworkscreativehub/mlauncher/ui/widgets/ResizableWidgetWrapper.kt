package com.github.codeworkscreativehub.mlauncher.ui.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.github.creativecodecat.components.views.FontBottomSheetDialogLocked
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.helper.getInstallSource
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class ResizableWidgetWrapper(
    context: Context,
    val hostView: AppWidgetHostView,
    val widgetInfo: AppWidgetProviderInfo,
    val appWidgetHost: AppWidgetHost,
    val onUpdate: () -> Unit,
    val onDelete: () -> Unit,
    private val gridColumns: Int,
    private val cellMargin: Int,
    val defaultCellsW: Int = 1,
    val defaultCellsH: Int = 1
) : FrameLayout(context) {

    companion object {
        private const val TAG = "ResizableWidgetWrapper"
    }

    var currentCol: Int = 0
    var currentRow: Int = 0

    private var lastX = 0f
    private var lastY = 0f
    private val minSize = 100

    var isResizeMode = false
    private val handleSize = 50

    private val topHandle = createHandle()
    private val bottomHandle = createHandle()
    private val leftHandle = createHandle()
    private val rightHandle = createHandle()

    private val topLeftHandle = createHandle()
    private val topRightHandle = createHandle()
    private val bottomLeftHandle = createHandle()
    private val bottomRightHandle = createHandle()

    private var activeDialog: FontBottomSheetDialogLocked? = null
    private var ghostView: View? = null

    init {

        AppLogger.d(TAG, "🧩 Initializing wrapper for widget: ${widgetInfo.provider.packageName}")

        // Calculate pixel width/height from cells
        post {
            val parentFrame = parent as? FrameLayout
            val parentWidth = parentFrame?.width ?: context.resources.displayMetrics.widthPixels

            // ✅ Calculate consistent grid cell size (same as in WidgetFragment)
            val cellWidth = (parentWidth - (cellMargin * (gridColumns - 1))) / gridColumns
            val cellHeight = cellWidth // assuming square cells

            // ✅ Calculate widget dimensions using the same logic as saving/loading
            val widthPx = (defaultCellsW * (cellWidth + cellMargin)) - cellMargin
            val heightPx = (defaultCellsH * (cellHeight + cellMargin)) - cellMargin

            layoutParams = LayoutParams(widthPx, heightPx)
            AppLogger.d(TAG, "📐 layoutParams set to ${widthPx}x${heightPx} for ${defaultCellsW}x${defaultCellsH} cells")

            // ✅ Ensure hostView fills wrapper and updates provider with current size
            fillHostView(widthPx, heightPx)
        }

        addView(
            hostView, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
        AppLogger.d(TAG, "✅ HostView added to wrapper")

        topHandle.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, handleSize).apply { gravity = Gravity.TOP }
        bottomHandle.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, handleSize).apply { gravity = Gravity.BOTTOM }
        leftHandle.layoutParams = LayoutParams(handleSize, LayoutParams.MATCH_PARENT).apply { gravity = Gravity.START }
        rightHandle.layoutParams = LayoutParams(handleSize, LayoutParams.MATCH_PARENT).apply { gravity = Gravity.END }

        topLeftHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.TOP or Gravity.START }
        topRightHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.TOP or Gravity.END }
        bottomLeftHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.BOTTOM or Gravity.START }
        bottomRightHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.BOTTOM or Gravity.END }


        addView(topHandle)
        addView(bottomHandle)
        addView(leftHandle)
        addView(rightHandle)

        addView(topLeftHandle)
        addView(topRightHandle)
        addView(bottomLeftHandle)
        addView(bottomRightHandle)

        topHandle.bringToFront()
        bottomHandle.bringToFront()
        leftHandle.bringToFront()
        rightHandle.bringToFront()

        topLeftHandle.bringToFront()
        topRightHandle.bringToFront()
        bottomLeftHandle.bringToFront()
        bottomRightHandle.bringToFront()


        setHandlesVisible(false)
        attachResizeAndDragHandlers()
    }

    private fun fillHostView(parentWidth: Int = width, parentHeight: Int = height) {
        AppLogger.d(TAG, "fillHostView($parentWidth x $parentHeight) called")
        // 1. Force hostView to fill THIS wrapper
        hostView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        hostView.requestLayout()

        // 2. Use parent’s width/height for widget sizing
        post {
            if (parentWidth <= 0 || parentHeight <= 0) {
                AppLogger.w(TAG, "⚠️ Skipping fillHostView — invalid size ($parentWidth x $parentHeight)")
                return@post
            }

            try {
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, parentWidth / 4)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, parentWidth)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, parentHeight / 4)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, parentHeight)
                }

                val appWidgetManager = AppWidgetManager.getInstance(context)
                appWidgetManager.updateAppWidgetOptions(hostView.appWidgetId, options)

                AppLogger.i(TAG, "✅ fillHostView: using parent size width=$parentWidth, height=$parentHeight")
            } catch (e: Exception) {
                AppLogger.e(TAG, "❌ Failed to update widget options: ${e.message}")
            }
        }
    }

    private fun createHandle(): View = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor("#26A6DA95".toColorInt()) // semi-transparent fill
            setStroke(4, "#FFA6DA95".toColorInt()) // optional outline
        }
        visibility = GONE
    }

    fun setHandlesVisible(visible: Boolean) {
        AppLogger.d(TAG, "setHandlesVisible($visible)")
        val state = if (visible) VISIBLE else GONE
        topHandle.visibility = state
        bottomHandle.visibility = state
        leftHandle.visibility = state
        rightHandle.visibility = state

        topLeftHandle.visibility = state
        topRightHandle.visibility = state
        bottomLeftHandle.visibility = state
        bottomRightHandle.visibility = state


        if (visible) {
            // 🔥 Bring handles on top again after adding overlay
            topHandle.bringToFront()
            bottomHandle.bringToFront()
            leftHandle.bringToFront()
            rightHandle.bringToFront()

            topLeftHandle.bringToFront()
            topRightHandle.bringToFront()
            bottomLeftHandle.bringToFront()
            bottomRightHandle.bringToFront()
        }
    }

    private var activeResizeHandle: String? = null

    private fun attachResizeAndDragHandlers() {
        val handles = listOf(
            topHandle, bottomHandle, leftHandle, rightHandle,
            topLeftHandle, topRightHandle, bottomLeftHandle, bottomRightHandle
        )
        val sides = listOf(
            "TOP", "BOTTOM", "LEFT", "RIGHT",
            "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
        )

        val parentView = parent as? View
        val parentWidth = parentView?.width ?: Int.MAX_VALUE
        val parentHeight = parentView?.height ?: Int.MAX_VALUE

        // --- Attach resize listeners to handles ---
        handles.zip(sides).forEach { (handle, side) ->
            handle.setOnTouchListener { _, event ->
                if (!isResizeMode) return@setOnTouchListener false

                val lp = layoutParams as? LayoutParams ?: return@setOnTouchListener false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activeResizeHandle = side
                        lastX = event.rawX
                        lastY = event.rawY
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        activeResizeHandle?.let { resizeSide ->
                            val dx = event.rawX - lastX
                            val dy = event.rawY - lastY

                            when (resizeSide) {
                                // --- Edge handles ---
                                "TOP" -> {
                                    val maxHeight = lp.height + translationY
                                    val newHeight = (lp.height - dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight.coerceAtMost(parentHeight.toFloat()).toInt())
                                    val actualDy = lp.height - newHeight
                                    translationY += actualDy
                                    lp.height = newHeight
                                }

                                "BOTTOM" -> {
                                    val maxHeight = parentHeight - top
                                    lp.height = (lp.height + dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight)
                                }

                                "LEFT" -> {
                                    val maxWidth = lp.width + translationX
                                    val newWidth = (lp.width - dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth.coerceAtMost(parentWidth.toFloat()).toInt())
                                    val actualDx = lp.width - newWidth
                                    translationX += actualDx
                                    lp.width = newWidth
                                }

                                "RIGHT" -> {
                                    val maxWidth = parentWidth - left
                                    lp.width = (lp.width + dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth)
                                }

                                // --- Corner handles ---
                                "TOP_LEFT" -> {
                                    val maxHeight = lp.height + translationY
                                    val maxWidth = lp.width + translationX
                                    val newHeight = (lp.height - dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight.coerceAtMost(parentHeight.toFloat()).toInt())
                                    val newWidth = (lp.width - dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth.coerceAtMost(parentWidth.toFloat()).toInt())
                                    val actualDy = lp.height - newHeight
                                    val actualDx = lp.width - newWidth
                                    translationY += actualDy
                                    translationX += actualDx
                                    lp.height = newHeight
                                    lp.width = newWidth
                                }

                                "TOP_RIGHT" -> {
                                    val maxHeight = lp.height + translationY
                                    val maxWidth = parentWidth - left
                                    val newHeight = (lp.height - dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight.coerceAtMost(parentHeight.toFloat()).toInt())
                                    val newWidth = (lp.width + dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth)
                                    translationY += lp.height - newHeight
                                    lp.height = newHeight
                                    lp.width = newWidth
                                }

                                "BOTTOM_LEFT" -> {
                                    val maxHeight = parentHeight - top
                                    val maxWidth = lp.width + translationX
                                    val newHeight = (lp.height + dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight)
                                    val newWidth = (lp.width - dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth.coerceAtMost(parentWidth.toFloat()).toInt())
                                    translationX += lp.width - newWidth
                                    lp.height = newHeight
                                    lp.width = newWidth
                                }

                                "BOTTOM_RIGHT" -> {
                                    val maxHeight = parentHeight - top
                                    val maxWidth = parentWidth - left
                                    lp.height = (lp.height + dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight)
                                    lp.width = (lp.width + dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth)
                                }
                            }

                            layoutParams = lp
                            fillHostView(lp.width, lp.height)
                            lastX = event.rawX
                            lastY = event.rawY
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeResizeHandle?.let { snapResizeToGrid(it) }
                        activeResizeHandle = null
                        onUpdate()
                    }
                }
                true
            }
        }

        // --- Attach drag + long-press only to non-handle areas ---
        if (!isResizeMode) attachDragToWrapperAndChildren(this, skipViews = handles)
    }


    private fun attachDragToWrapperAndChildren(root: View, skipViews: List<View> = emptyList()) {

        fun attachDrag(view: View) {
            // Attach long-press menu and drag to this view
            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (!isResizeMode && WidgetFragment.isEditingWidgets) {
                        showWidgetMenu()
                    }
                }
            })

            var dialogDismissed = false

            view.setOnTouchListener { v, event ->
                // Skip handles
                if (v in skipViews) return@setOnTouchListener false
                gestureDetector.onTouchEvent(event)
                if (isResizeMode) return@setOnTouchListener false

                // 🟡 If not in global edit mode, don't consume — allow normal widget touch behavior
                if (!WidgetFragment.isEditingWidgets) return@setOnTouchListener false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dialogDismissed = false

                        lastX = event.rawX
                        lastY = event.rawY

                        val parentFrame = parent as? FrameLayout
                        if (parentFrame != null) {
                            ghostView = View(context).apply {
                                setBackgroundColor("#26C6A0F6".toColorInt())
                                layoutParams = LayoutParams(width, height).apply {
                                    if (layoutParams is LayoutParams) {
                                        leftMargin = (layoutParams as LayoutParams).leftMargin
                                        topMargin = (layoutParams as LayoutParams).topMargin
                                    }
                                }
                            }
                            parentFrame.addView(ghostView)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY

                        translationX += dx
                        translationY += dy

                        lastX = event.rawX
                        lastY = event.rawY

                        updateGhostPosition()

                        // Dismiss only once if movement exceeds 10 pixels in any direction
                        if (!dialogDismissed && (abs(dx) > 5 || abs(dy) > 5)) {
                            activeDialog?.dismiss()
                            dialogDismissed = true
                        }
                    }


                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dialogDismissed = false
                        snapToGrid()
                        (ghostView?.parent as? ViewGroup)?.removeView(ghostView)
                        ghostView = null
                        onUpdate()
                    }
                }

                true
            }
        }

        attachDrag(root)
    }

    private fun updateGhostPosition() {
        val parentFrame = parent as? FrameLayout ?: return

        val parentWidth = parentFrame.width.coerceAtLeast(1)
        val parentHeight = parentFrame.height.coerceAtLeast(1)

        val cellWidth = ((parentWidth - (cellMargin * (gridColumns - 1))) / gridColumns).coerceAtLeast(1)
        val cellHeight = cellWidth

        val maxX = (parentWidth - width).coerceAtLeast(0)
        val maxY = (parentHeight - height).coerceAtLeast(0)

        val col = ((translationX + cellWidth / 2) / (cellWidth + cellMargin))
            .toInt()
            .coerceIn(0, gridColumns - 1)
        val row = ((translationY + cellHeight / 2) / (cellHeight + cellMargin))
            .toInt()
            .coerceAtLeast(0)

        val newX = (col * (cellWidth + cellMargin)).coerceIn(0, maxX)
        val newY = (row * (cellHeight + cellMargin)).coerceIn(0, maxY)

        ghostView?.layoutParams = (ghostView?.layoutParams as LayoutParams).apply {
            leftMargin = newX
            topMargin = newY
            width = this@ResizableWidgetWrapper.width
            height = this@ResizableWidgetWrapper.height
        }
        ghostView?.requestLayout()
    }

    fun snapToGrid() {
        val parentFrame = parent as? FrameLayout ?: return

        val parentWidth = parentFrame.width.coerceAtLeast(1)
        val parentHeight = parentFrame.height.coerceAtLeast(1)

        val cellWidth = ((parentWidth - (cellMargin * (gridColumns - 1))) / gridColumns).coerceAtLeast(1)
        val cellHeight = cellWidth

        // Max translation ensures widget never leaves parent
        val maxX = (parentWidth - width).coerceAtLeast(0)
        val maxY = (parentHeight - height).coerceAtLeast(0)

        val col = ((translationX + cellWidth / 2) / (cellWidth + cellMargin))
            .toInt()
            .coerceIn(0, gridColumns - 1)

        val row = ((translationY + cellHeight / 2) / (cellHeight + cellMargin))
            .toInt()
            .coerceAtLeast(0) // row may expand beyond grid if needed

        translationX = (col * (cellWidth + cellMargin)).toFloat().coerceIn(0f, maxX.toFloat())
        translationY = (row * (cellHeight + cellMargin)).toFloat().coerceIn(0f, maxY.toFloat())

        currentCol = col
        currentRow = row
    }

    fun snapResizeToGrid(side: String) {
        val parentFrame = parent as? FrameLayout ?: return
        val lp = layoutParams as? LayoutParams ?: return

        val parentWidth = parentFrame.width.coerceAtLeast(1)
        val parentHeight = parentFrame.height.coerceAtLeast(1)
        val cellSize = ((parentWidth - (cellMargin * (gridColumns - 1))) / gridColumns).coerceAtLeast(1)

        val maxWidth = (parentWidth - lp.leftMargin).coerceAtLeast(minSize)
        val maxHeight = (parentHeight - lp.topMargin).coerceAtLeast(minSize)

        // Helper to snap a float coordinate to the nearest cell
        fun snapToCell(value: Float): Int {
            return ((value + cellSize / 2f) / (cellSize + cellMargin)).toInt() * (cellSize + cellMargin)
        }

        // Compute the "visible" top and left by combining margin and translation
        val currentLeft = lp.leftMargin + translationX
        val currentTop = lp.topMargin + translationY
        val right = currentLeft + lp.width
        val bottom = currentTop + lp.height

        // Snap positions depending on which side was resized
        when (side) {
            "TOP" -> {
                val snappedTop = snapToCell(currentTop)
                val newHeight = (bottom - snappedTop).toInt().coerceAtLeast(minSize).coerceAtMost(maxHeight)
                translationY += (snappedTop - currentTop)
                lp.height = newHeight
            }

            "BOTTOM" -> {
                val snappedBottom = snapToCell(bottom)
                lp.height = (snappedBottom - currentTop).toInt().coerceAtLeast(minSize).coerceAtMost(maxHeight)
            }

            "LEFT" -> {
                val snappedLeft = snapToCell(currentLeft)
                val newWidth = (right - snappedLeft).toInt().coerceAtLeast(minSize).coerceAtMost(maxWidth)
                translationX += (snappedLeft - currentLeft)
                lp.width = newWidth
            }

            "RIGHT" -> {
                val snappedRight = snapToCell(right)
                lp.width = (snappedRight - currentLeft).toInt().coerceAtLeast(minSize).coerceAtMost(maxWidth)
            }

            "TOP_LEFT" -> {
                val snappedTop = snapToCell(currentTop)
                val snappedLeft = snapToCell(currentLeft)
                val newWidth = (right - snappedLeft).toInt().coerceAtLeast(minSize).coerceAtMost(maxWidth)
                val newHeight = (bottom - snappedTop).toInt().coerceAtLeast(minSize).coerceAtMost(maxHeight)
                translationX += (snappedLeft - currentLeft)
                translationY += (snappedTop - currentTop)
                lp.width = newWidth
                lp.height = newHeight
            }

            "TOP_RIGHT" -> {
                val snappedTop = snapToCell(currentTop)
                val snappedRight = snapToCell(right)
                val newHeight = (bottom - snappedTop).toInt().coerceAtLeast(minSize).coerceAtMost(maxHeight)
                val newWidth = (snappedRight - currentLeft).toInt().coerceAtLeast(minSize).coerceAtMost(maxWidth)
                translationY += (snappedTop - currentTop)
                lp.width = newWidth
                lp.height = newHeight
            }

            "BOTTOM_LEFT" -> {
                val snappedBottom = snapToCell(bottom)
                val snappedLeft = snapToCell(currentLeft)
                val newWidth = (right - snappedLeft).toInt().coerceAtLeast(minSize).coerceAtMost(maxWidth)
                val newHeight = (snappedBottom - currentTop).toInt().coerceAtLeast(minSize).coerceAtMost(maxHeight)
                translationX += (snappedLeft - currentLeft)
                lp.width = newWidth
                lp.height = newHeight
            }

            "BOTTOM_RIGHT" -> {
                val snappedBottom = snapToCell(bottom)
                val snappedRight = snapToCell(right)
                lp.width = (snappedRight - currentLeft).toInt().coerceAtLeast(minSize).coerceAtMost(maxWidth)
                lp.height = (snappedBottom - currentTop).toInt().coerceAtLeast(minSize).coerceAtMost(maxHeight)
            }
        }

        layoutParams = lp
        fillHostView(lp.width, lp.height)
    }


    fun showWidgetMenu() {
        val dialog = FontBottomSheetDialogLocked(context)
        activeDialog = dialog
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        fun addMenuItem(title: String, onClick: () -> Unit) {
            val item = TextView(context).apply {
                text = title
                textSize = 16f
                setPadding(16, 32, 16, 32)
                setOnClickListener {
                    onClick()
                    dialog.dismiss()
                }
            }
            container.addView(item)
        }

        if (isResizeMode) {
            addMenuItem(getLocalizedString(R.string.widgets_exit_resize)) {
                isResizeMode = false
                setHandlesVisible(false)
                reloadActivity()
            }
        } else {
            addMenuItem(getLocalizedString(R.string.widgets_resize)) {
                isResizeMode = true
                setHandlesVisible(true)
            }
        }

        addMenuItem(getLocalizedString(R.string.widgets_remove)) {
            appWidgetHost.deleteAppWidgetId(hostView.appWidgetId)
            (parent as? ViewGroup)?.removeView(this)
            onDelete()
        }

        addMenuItem(getLocalizedString(R.string.widgets_open)) {
            context.packageManager.getLaunchIntentForPackage(widgetInfo.provider.packageName)?.let {
                context.startActivity(it)
            }
        }

        // Settings (only if widget has a config activity)
        widgetInfo.configure?.let { configureComponent ->
            addMenuItem(getLocalizedString(R.string.widgets_settings)) {

                val intent = Intent().apply {
                    component = configureComponent
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, hostView.appWidgetId)
                }
                context.startActivity(intent)
            }
        }

        // View in Store (only if installed from Google Play)
        val packageManager = context.packageManager
        val installerPackage = getInstallSource(packageManager, widgetInfo.provider.packageName)
        when (installerPackage) {
            "Google Play Store" -> { // Google Play
                addMenuItem(getLocalizedString(R.string.widgets_view_in_store)) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "market://details?id=${widgetInfo.provider.packageName}".toUri()
                        )
                    )
                }
            }

            "Amazon Appstore" -> { // Amazon Appstore
                addMenuItem(getLocalizedString(R.string.widgets_view_in_store)) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "amzn://apps/android?p=${widgetInfo.provider.packageName}".toUri()
                        )
                    )
                }
            }

            else -> {
                // Debug / unknown installer, do not show "View in Store"
                AppLogger.d(TAG, "WidgetMenu: Skipping '${getLocalizedString(R.string.widgets_view_in_store)}': unrecognized installer package='$installerPackage'")
            }
        }

        dialog.setOnDismissListener { activeDialog = null }
        dialog.setContentView(container)
        dialog.show()
    }

    fun reloadActivity() {
        val activity = context as? Activity ?: return

        val intent = activity.intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        activity.finish()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }

        activity.startActivity(intent)

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.onInterceptTouchEvent(ev)

        // ✅ Bypass all if global edit mode is enabled
        if (!isResizeMode && WidgetFragment.isEditingWidgets) {
            // Track initial touch for dragging if needed
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                lastX = ev.rawX
                lastY = ev.rawY
            }
            return true
        }

        // ✅ Normal resize logic
        if (!isResizeMode) return super.onInterceptTouchEvent(ev)

        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            activeResizeHandle = getHandleAt(ev.rawX, ev.rawY)
            lastX = ev.rawX
            lastY = ev.rawY
        }

        // Intercept only if touch is outside a resize handle
        return activeResizeHandle == null
    }

    private fun getHandleAt(x: Float, y: Float): String? {
        val handles = mapOf(
            topHandle to "TOP",
            bottomHandle to "BOTTOM",
            leftHandle to "LEFT",
            rightHandle to "RIGHT",
            topLeftHandle to "TOP_LEFT",
            topRightHandle to "TOP_RIGHT",
            bottomLeftHandle to "BOTTOM_LEFT",
            bottomRightHandle to "BOTTOM_RIGHT"
        )
        val location = IntArray(2)
        for ((view, name) in handles) {
            view.getLocationOnScreen(location)
            val left = location[0].toFloat()
            val top = location[1].toFloat()
            val right = left + view.width
            val bottom = top + view.height
            if (x in left..right && y in top..bottom) return name
        }
        return null
    }

}