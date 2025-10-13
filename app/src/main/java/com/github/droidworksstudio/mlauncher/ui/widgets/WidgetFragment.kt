package com.github.droidworksstudio.mlauncher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.droidworksstudio.common.AppLogger
import com.github.droidworksstudio.common.appWidgetManager
import com.github.droidworksstudio.common.getLocalizedString
import com.github.droidworksstudio.common.isGestureNavigationEnabled
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.data.SavedWidgetEntity
import com.github.droidworksstudio.mlauncher.data.database.WidgetDao
import com.github.droidworksstudio.mlauncher.data.database.WidgetDatabase
import com.github.droidworksstudio.mlauncher.databinding.FragmentWidgetBinding
import com.github.droidworksstudio.mlauncher.ui.components.LockedBottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.ceil

data class AppWidgetGroup(
    val appName: String,
    val appIcon: Drawable?,
    val widgets: MutableList<AppWidgetProviderInfo>
)

class WidgetFragment : Fragment() {

    private lateinit var prefs: Prefs

    private var _binding: FragmentWidgetBinding? = null
    private val binding get() = _binding!!

    private lateinit var widgetDao: WidgetDao
    lateinit var appWidgetManager: AppWidgetManager
    lateinit var appWidgetHost: AppWidgetHost
    private val widgetWrappers = mutableListOf<ResizableWidgetWrapper>()

    companion object {
        private const val TAG = "WidgetFragment"
        private val APP_WIDGET_HOST_ID = getLocalizedString(R.string.app_name).hashCode().absoluteValue
        private const val GRID_COLUMNS = 14
        private const val CELL_MARGIN = 16

        // Minimum cell count per widget
        private const val MIN_CELL_W = 2
        private const val MIN_CELL_H = 1

        var isEditingWidgets: Boolean = false
    }

    private var activeGridDialog: LockedBottomSheetDialog? = null
    private var lastWidgetInfo: AppWidgetProviderInfo? = null
    private var placeholderVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWidgetBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back press handling for exiting resize mode
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val resizeWidget = widgetWrappers.firstOrNull { it.isResizeMode }
            if (resizeWidget != null) {
                AppLogger.i(TAG, "🔄 Exiting resize mode for widgetId=${resizeWidget.hostView.appWidgetId}")
                resizeWidget.isResizeMode = false
                resizeWidget.setHandlesVisible(false)
                resizeWidget.reloadActivity()
            } else {
                // Disable this callback so the system default back behavior can run
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.apply {
            AppLogger.i(TAG, "🟢 Widget grid initialized")

            val isGestureNav = isGestureNavigationEnabled(requireContext())

            val params = widgetGrid.layoutParams as ViewGroup.MarginLayoutParams
            if (isGestureNav) {
                params.bottomMargin = resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav) // or just in px
            } else {
                params.bottomMargin = resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav) // or just in px
            }
            params.topMargin = resources.getDimensionPixelSize(R.dimen.top_margin) // or just in px
            widgetGrid.layoutParams = params

            // Setup AppWidgetManager and Host
            appWidgetManager = requireContext().appWidgetManager
            appWidgetHost = AppWidgetHost(requireContext(), APP_WIDGET_HOST_ID)
            appWidgetHost.startListening()
            AppLogger.i(TAG, "🟢 AppWidgetHost started listening")
            cleanupOrphanedWidgets()

            widgetGrid.apply {
                setOnLongClickListener {
                    // Only show grid menu if no widget is currently being resized
                    val resizing = (0 until widgetGrid.childCount)
                        .mapNotNull { widgetGrid.getChildAt(it) as? ResizableWidgetWrapper }
                        .any { it.isResizeMode }  // or activeResizeHandle != null for more precision

                    if (!resizing) {
                        showGridMenu()
                        true
                    } else {
                        // Ignore long press while resizing
                        false
                    }
                }

                // Post widget loading after layout to prevent jumps
                post {
                    (activity as? WidgetActivity)?.flushPendingWidgets()
                    AppLogger.i(TAG, "🟢 Pending widgets flushed and grid visible")
                }

                AppLogger.i(TAG, "🟢 WidgetFragment onViewCreated setup complete")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isEditingWidgets = false
    }

    override fun onResume() {
        super.onResume()
        restoreWidgets()
        AppLogger.i(TAG, "🔄 WidgetFragment onResume, widgets restored")
        updateEmptyPlaceholder(widgetWrappers)
    }

    fun cleanupOrphanedWidgets() {
        CoroutineScope(Dispatchers.IO).launch {
            // Get the list of all saved widget IDs from your database
            val savedIds = widgetDao.getAll().map { it.appWidgetId }.toSet()

            val allocatedIds = appWidgetHost.appWidgetIds
            for (id in allocatedIds) {
                if (id !in savedIds) {
                    appWidgetHost.deleteAppWidgetId(id)
                    AppLogger.i(TAG, "🗑️ Deleted orphaned widgetId=$id")
                }
            }
        }
    }


    private val pendingWidgetsList = mutableListOf<Pair<AppWidgetProviderInfo, Int>>()

    fun postPendingWidgets(widgets: List<Pair<AppWidgetProviderInfo, Int>>) {
        // Add new widgets to the pending list
        pendingWidgetsList.addAll(widgets)
        AppLogger.d(TAG, "postPendingWidgets: ${widgets.size} widgets added to pending list. Total pending=${pendingWidgetsList.size}")

        // Only post if the fragment is attached and view is created
        if (!isAdded || !isViewCreated()) {
            AppLogger.w(TAG, "postPendingWidgets: Fragment not ready, pending widgets will remain queued")
            return
        }

        // Post to widgetGrid after view is laid out
        binding.widgetGrid.post {
            if (!isAdded || !isViewCreated()) {
                AppLogger.w(TAG, "postPendingWidgets: Fragment detached, aborting widget posting")
                return@post
            }

            AppLogger.i(TAG, "🟢 Posting ${pendingWidgetsList.size} pending widgets to widgetGrid")

            // Create widgets safely
            pendingWidgetsList.forEach { (info, id) ->
                AppLogger.d(TAG, "Creating widget: ${info.loadLabel(requireContext().packageManager)} (id=$id)")
                createWidgetWrapperSafe(info, id)
            }

            // Clear the pending list after posting
            pendingWidgetsList.clear()
            AppLogger.d(TAG, "Pending widgets list cleared after posting")

            AppLogger.d(TAG, "Saved widgets restored")

            // Update empty placeholder visibility
            updateEmptyPlaceholder(widgetWrappers)
            AppLogger.d(TAG, "Empty placeholder updated")
        }
    }


    /** Grid menu for adding/resetting widgets */
    private fun showGridMenu() {
        activeGridDialog?.dismiss()
        val bottomSheetDialog = LockedBottomSheetDialog(requireContext())
        activeGridDialog = bottomSheetDialog
        AppLogger.d(TAG, "🎛️ Showing widget grid menu")

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        fun addOption(title: String, action: () -> Unit) {
            val option = TextView(requireContext()).apply {
                text = title
                textSize = 16f
                setPadding(16, 16, 16, 16)
                setOnClickListener { action(); bottomSheetDialog.dismiss() }
            }
            container.addView(option)
        }

        addOption(getLocalizedString(R.string.widgets_add_widget)) { showCustomWidgetPicker() }

        if (isEditingWidgets) {
            addOption(getLocalizedString(R.string.widgets_reset_widget)) { resetAllWidgets() }
            addOption(getLocalizedString(R.string.widgets_remove_widget)) { removeAllWidgets() }
        }

        // Toggleable edit mode option
        val editTitle = if (isEditingWidgets) getLocalizedString(R.string.widgets_stop_editing_widget) else getLocalizedString(R.string.widgets_edit_widget)
        addOption(editTitle) {
            // Toggle edit mode
            isEditingWidgets = !isEditingWidgets

            if (isEditingWidgets) {
                // Add a visible border to the widget grid
                val border = GradientDrawable().apply {
                    setStroke(4, "#FFF5A97F".toColorInt())
                }
                binding.widgetGrid.background = border
            } else {
                // Remove the border
                binding.widgetGrid.background = null
            }
        }

        bottomSheetDialog.setContentView(container)
        bottomSheetDialog.show()
    }

    private fun removeAllWidgets() {
        AppLogger.w(TAG, "🧹 Removing all widgets")
        widgetWrappers.forEach { wrapper ->
            deleteWidget(wrapper.hostView.appWidgetId)
        }
        widgetWrappers.clear()
        binding.widgetGrid.apply {
            for (i in childCount - 1 downTo 0) {
                val child = getChildAt(i)
                if (child.id != binding.emptyPlaceholder.id) {
                    removeViewAt(i)
                }
            }
        }
        saveWidgets()
        updateEmptyPlaceholder(widgetWrappers)
        AppLogger.i(TAG, "🧹 All widgets cleared and placeholder shown")
    }

    fun deleteWidget(widgetId: Int) {
        // 1️⃣ Delete from AppWidgetHost
        appWidgetHost.deleteAppWidgetId(widgetId)
        AppLogger.w(TAG, "🗑️ Deleting widgetId=$widgetId")

        // 2️⃣ Remove from UI + in-memory list safely
        val iterator = widgetWrappers.iterator()
        while (iterator.hasNext()) {
            val wrapper = iterator.next()
            if (wrapper.hostView.appWidgetId == widgetId) {
                binding.widgetGrid.removeView(wrapper)
                iterator.remove()
                AppLogger.i(TAG, "🗑️ Removed wrapper for widgetId=$widgetId from grid")
                break
            }
        }

        // 3️⃣ Delete from database + log how many remain
        CoroutineScope(Dispatchers.IO).launch {
            try {
                widgetDao.deleteById(widgetId)
                AppLogger.d(TAG, "🗑️ Deleted widgetId=$widgetId from DB")

                val remainingCount = widgetDao.getAll().size
                AppLogger.i(TAG, "📊 Widgets remaining in DB: $remainingCount")
            } catch (e: Exception) {
                AppLogger.e(TAG, "⚠️ Failed to delete or count widgets", e)
            }
        }
    }


    private fun resetAllWidgets() {
        AppLogger.w(TAG, "🧹 Resetting all widgets positions")

        widgetWrappers.forEach { wrapper ->
            wrapper.currentCol = 0
            wrapper.currentRow = 0

            // Snap widget to top-left in the grid
            val parentFrame = wrapper.parent as? FrameLayout
            parentFrame?.let {
                wrapper.translationX = 0f
                wrapper.translationY = 0f

                val lp = wrapper.layoutParams as? FrameLayout.LayoutParams
                lp?.let {
                    it.leftMargin = 0
                    it.topMargin = 0
                    wrapper.layoutParams = it
                }

                wrapper.snapToGrid() // enforce grid snapping
            }
        }

        saveWidgets() // Save their reset positions
        updateEmptyPlaceholder(widgetWrappers) // refresh placeholder if needed

        AppLogger.i(TAG, "🧹 All widgets reset to top-left")
    }

    private fun showCustomWidgetPicker() {
        val widgets = appWidgetManager.installedProviders.filter { widgetInfo ->
            val configure = widgetInfo.configure
            if (configure == null) return@filter true // include widgets with no config

            val resolveInfo = try {
                requireContext().packageManager.resolveActivity(
                    Intent().apply { component = configure },
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            } catch (_: Exception) {
                null
            }

            resolveInfo?.activityInfo?.exported == true
        }

        val pm = requireContext().packageManager

        // Group widgets by package
        val grouped = widgets.groupBy { it.provider.packageName }.map { (pkg, widgetList) ->
            val appInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (_: Exception) {
                null
            }
            val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
            val appIcon = appInfo?.let { pm.getApplicationIcon(it) }
            AppWidgetGroup(appName, appIcon, widgetList.toMutableList())
        }.sortedBy { it.appName.lowercase() }

        AppLogger.d(TAG, "🧩 Showing custom widget picker with ${grouped.size} apps")

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        val scrollView = ScrollView(requireContext()).apply { addView(container) }

        activeGridDialog?.dismiss()
        val bottomSheetDialog = LockedBottomSheetDialog(requireContext())
        activeGridDialog = bottomSheetDialog
        bottomSheetDialog.setContentView(scrollView)
        bottomSheetDialog.setTitle(getLocalizedString(R.string.widgets_select_widget))
        bottomSheetDialog.show()

        grouped.forEach { group ->
            val appRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 16, 8, 16)
                gravity = Gravity.CENTER_VERTICAL
            }
            val iconView = ImageView(requireContext()).apply {
                group.appIcon?.let { setImageDrawable(it) }
                layoutParams = LinearLayout.LayoutParams(64, 64)
            }
            val labelView = TextView(requireContext()).apply {
                text = group.appName
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(16, 0, 0, 0)
            }
            val expandIcon = TextView(requireContext()).apply { text = "▼"; textSize = 16f }
            appRow.addView(iconView)
            appRow.addView(labelView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            appRow.addView(expandIcon)

            val widgetContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }

            group.widgets.forEach { widgetInfo ->
                val widgetLabel = widgetInfo.loadLabel(requireContext().packageManager)

                val cellWidth = (binding.widgetGrid.width - (GRID_COLUMNS - 1) * CELL_MARGIN) / GRID_COLUMNS
                val cellHeight = cellWidth // assuming square cells — adjust if not

                // Calculate how many cells the widget needs, rounded up
                val (defaultCellsW, defaultCellsH) = calculateWidgetCells(
                    widgetInfo,
                    cellWidth,
                    cellHeight,
                )
                val widgetSize = "${defaultCellsW}x${defaultCellsH}"

                val widgetRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(32, 12, 12, 12)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val labelView = TextView(requireContext()).apply {
                    text = getLocalizedString(R.string.pass_a_string, widgetLabel)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val sizeView = TextView(requireContext()).apply {
                    text = widgetSize
                    textSize = 14f
                    gravity = Gravity.END
                    setTypeface(null, Typeface.ITALIC)
                }

                widgetRow.addView(labelView)
                widgetRow.addView(sizeView)

                widgetRow.setOnClickListener {
                    AppLogger.i(TAG, "➕ Selected widget $widgetLabel to add")
                    addWidget(widgetInfo)
                    bottomSheetDialog.dismiss()
                }

                widgetContainer.addView(widgetRow)
            }


            appRow.setOnClickListener {
                if (widgetContainer.isVisible) {
                    widgetContainer.visibility = View.GONE
                    expandIcon.text = "▼"
                } else {
                    widgetContainer.visibility = View.VISIBLE
                    expandIcon.text = "▲"
                }
            }

            container.addView(appRow)
            container.addView(widgetContainer)
        }
    }

    /** Public entry point: add a widget */
    private fun addWidget(widgetInfo: AppWidgetProviderInfo) {
        lastWidgetInfo = widgetInfo
        val widgetId = appWidgetHost.allocateAppWidgetId()
        AppLogger.d(TAG, "🆕 Allocated appWidgetId=$widgetId for provider=${widgetInfo.provider.packageName}")

        val manager = requireContext().appWidgetManager

        // Check if binding is allowed
        val bound = manager.bindAppWidgetIdIfAllowed(widgetId, widgetInfo.provider)
        if (bound) {
            AppLogger.i(TAG, "✅ Bound widget immediately: widgetId=$widgetId")
            maybeConfigureOrCreate(widgetInfo, widgetId)
        } else {
            AppLogger.w(TAG, "🔒 Widget bind not allowed, requesting permission")
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widgetInfo.provider)
            }

            (requireActivity() as WidgetActivity).launchWidgetPermission(intent) { resultCode, returnedId, _ ->
                handleWidgetResult(resultCode, returnedId)
            }
        }
    }

    /** Handle result from binding or configuration */
    private fun handleWidgetResult(resultCode: Int, appWidgetId: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                AppLogger.i(TAG, "✅ Widget bind/config OK for appWidgetId=$appWidgetId")
                lastWidgetInfo?.let { maybeConfigureOrCreate(it, appWidgetId) }
                lastWidgetInfo = null
            }

            Activity.RESULT_CANCELED -> {
                AppLogger.w(TAG, "❌ Widget bind/config canceled for appWidgetId=$appWidgetId")
                safeRemoveWidget(appWidgetId)
            }
        }
    }

    /** Check if widget has configuration, then create wrapper safely */
    private fun maybeConfigureOrCreate(widgetInfo: AppWidgetProviderInfo, widgetId: Int) {
        if (widgetInfo.configure != null) {
            AppLogger.i(TAG, "⚙️ Widget has configuration, launching config activity")
            val intent = Intent().apply {
                component = widgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }

            (activity as? WidgetActivity)?.let { widgetActivity ->
                widgetActivity.launchWidgetPermission(intent) { resultCode, returnedId, _ ->
                    if (resultCode == Activity.RESULT_OK) {
                        AppLogger.i(TAG, "✅ Widget configured, creating wrapper: $returnedId")
                        // Ensure widgetInfo is captured properly in the lambda
                        widgetActivity.safeCreateWidget(widgetInfo, returnedId)
                    } else {
                        AppLogger.w(TAG, "❌ Widget config canceled, removing: $returnedId")
                        safeRemoveWidget(returnedId)
                    }
                }
            }
        } else {
            AppLogger.i(TAG, "📦 No configuration needed, creating wrapper immediately")
            createWidgetWrapperSafe(widgetInfo, widgetId)
        }
    }

    private fun calculateWidgetCells(
        widgetInfo: AppWidgetProviderInfo,
        cellWidth: Int,
        cellHeight: Int
    ): Pair<Int, Int> {
        val cellsW = ceil(widgetInfo.minWidth.toDouble() / (cellWidth + CELL_MARGIN))
            .toInt()
            .coerceAtLeast(MIN_CELL_W)

        val cellsH = ceil(widgetInfo.minHeight.toDouble() / (cellHeight + CELL_MARGIN))
            .toInt()
            .coerceAtLeast(MIN_CELL_H)

        return cellsW to cellsH
    }


    fun createWidgetWrapperSafe(widgetInfo: AppWidgetProviderInfo, appWidgetId: Int) {
        if (!isAdded) {
            AppLogger.w(TAG, "⚠️ Skipping widget creation, fragment not attached")
            return
        }
        binding.widgetGrid.post {
            createWidgetWrapper(widgetInfo, appWidgetId)
        }
    }

    fun createWidgetWrapper(widgetInfo: AppWidgetProviderInfo, appWidgetId: Int) {
        val hostView = try {
            val widgetContext = try {
                requireContext().createPackageContext(
                    widgetInfo.provider.packageName,
                    Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
                )
            } catch (_: Exception) {
                requireContext()
            }

            // Use the existing widget ID if it's valid, otherwise allocate a new one
            val appWidgetManager = AppWidgetManager.getInstance(requireContext())
            val widgetIdToUse = if (isWidgetIdValid(appWidgetId, appWidgetManager)) {
                appWidgetId
            } else {
                val newWidgetId = appWidgetHost.allocateAppWidgetId()

                // Bind the new ID to the provider
                if (!appWidgetManager.bindAppWidgetIdIfAllowed(newWidgetId, widgetInfo.provider)) {
                    AppLogger.e(TAG, "⚠️ Failed to bind new widgetId=$newWidgetId")
                    safeRemoveWidget(newWidgetId)
                    return
                }
                newWidgetId
            }

            // Now create the host view
            appWidgetHost.createView(widgetContext, widgetIdToUse, widgetInfo)

        } catch (e: Exception) {
            AppLogger.e(TAG, "⚠️ Failed to create widgetId=$appWidgetId, removing", e)
            safeRemoveWidget(appWidgetId)
            return
        }

        AppLogger.d(TAG, "🖼️ Creating wrapper for widgetId=$appWidgetId, provider=${widgetInfo.provider.packageName}")

        val cellWidth = (binding.widgetGrid.width - (GRID_COLUMNS - 1) * CELL_MARGIN) / GRID_COLUMNS
        val cellHeight = cellWidth // assuming square cells — adjust if not

        // Calculate how many cells the widget needs, rounded up
        val (defaultCellsW, defaultCellsH) = calculateWidgetCells(
            widgetInfo,
            cellWidth,
            cellHeight,
        )


        AppLogger.v(TAG, "📐 Default size for widgetId=$appWidgetId: ${widgetInfo.minWidth}x${widgetInfo.minHeight} → $defaultCellsW x $defaultCellsH cells")

        val wrapper = ResizableWidgetWrapper(
            requireContext(),
            hostView,
            widgetInfo,
            appWidgetHost,
            { saveWidgets() },
            { deleteWidget(appWidgetId) },
            GRID_COLUMNS,
            CELL_MARGIN,
            defaultCellsW,
            defaultCellsH
        )

        addWrapperToGrid(wrapper)
        AppLogger.i(TAG, "✅ Wrapper created for widgetId=$appWidgetId")
        updateEmptyPlaceholder(widgetWrappers)
        saveWidgets()
        logGridSnapshot()
    }

    fun isWidgetIdValid(widgetId: Int, appWidgetManager: AppWidgetManager): Boolean {
        val info = try {
            appWidgetManager.getAppWidgetInfo(widgetId)
        } catch (_: Exception) {
            null
        }
        return info != null
    }


    private fun safeRemoveWidget(widgetId: Int) {
        try {
            AppLogger.w(TAG, "🗑️ Removing widgetId=$widgetId due to error")
            deleteWidget(widgetId)
            saveWidgets()
            updateEmptyPlaceholder(widgetWrappers)
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ Failed to remove widgetId=$widgetId", e)
        }
    }

    private fun addWrapperToGrid(wrapper: ResizableWidgetWrapper) {
        val id = wrapper.hostView.appWidgetId
        AppLogger.d(TAG, "➕ Adding wrapper to grid for widgetId=$id")

        // Calculate grid cell dimensions consistently
        val parentWidth = binding.widgetGrid.width.coerceAtLeast(1)
        val cellWidth = (parentWidth - (GRID_COLUMNS - 1) * CELL_MARGIN) / GRID_COLUMNS
        val cellHeight = cellWidth // assuming square grid cells

        // Compute actual pixel size based on grid cells + margin
        val wrapperWidth = (wrapper.defaultCellsW * (cellWidth + CELL_MARGIN)) - CELL_MARGIN
        val wrapperHeight = (wrapper.defaultCellsH * (cellHeight + CELL_MARGIN)) - CELL_MARGIN

        wrapper.layoutParams = FrameLayout.LayoutParams(wrapperWidth, wrapperHeight)

        // Build list of occupied cells
        val occupied = widgetWrappers.map { w ->
            val wCol = ((w.translationX + cellWidth / 2) / (cellWidth + CELL_MARGIN)).toInt()
            val wRow = ((w.translationY + cellHeight / 2) / (cellHeight + CELL_MARGIN)).toInt()
            Pair(wCol, wRow)
        }

        AppLogger.v(TAG, "📊 Occupied cells: $occupied")

        // Find the first available grid position
        var placed = false
        var row = 1
        var col = 1
        loop@ for (r in 0..1000) { // Arbitrary large number of rows
            for (c in 0 until GRID_COLUMNS) {
                if (occupied.none { it.first == c && it.second == r }) {
                    col = c
                    row = r
                    placed = true
                    AppLogger.d(TAG, "📍 Empty cell found at row=$row col=$col for widgetId=$id")
                    break@loop
                }
            }
        }

        if (!placed) {
            AppLogger.w(TAG, "⚠️ No free cell found, placing widget at top-left")
            col = 1
            row = 1
        }

        // Snap the widget to the calculated grid position
        wrapper.translationX = col * (cellWidth + CELL_MARGIN).toFloat()
        wrapper.translationY = row * (cellHeight + CELL_MARGIN).toFloat()

        addWrapperSafely(wrapper)
        AppLogger.i(TAG, "✅ Placed widgetId=$id at row=$row col=$col | size=${wrapperWidth}x${wrapperHeight}")
    }

    private fun addWrapperSafely(wrapper: ResizableWidgetWrapper) {
        val id = wrapper.hostView.appWidgetId

        val existing = widgetWrappers.find { it.hostView.appWidgetId == id }
        if (existing != null) {
            AppLogger.w(TAG, "♻️ Replacing existing wrapper for appWidgetId=$id")
            binding.widgetGrid.removeView(existing)
            widgetWrappers.remove(existing)
        }

        binding.widgetGrid.addView(wrapper)
        widgetWrappers.add(wrapper)

        AppLogger.i(
            TAG,
            "🟩 Added #${widgetWrappers.size} → id=${wrapper.hostView.appWidgetId} | Pinned -> col=${wrapper.currentCol}, row=${wrapper.currentRow} | Size -> width=${wrapper.width}, height=${wrapper.height} | Cells -> width=${wrapper.defaultCellsW}, height=${wrapper.defaultCellsH}"
        )

        updateEmptyPlaceholder(widgetWrappers)
    }

    /** Save widgets state to JSON */
    private fun saveWidgets() {
        val parentWidth = binding.widgetGrid.width.coerceAtLeast(1)
        val cellWidth = (parentWidth - CELL_MARGIN * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val cellHeight = cellWidth.coerceAtLeast(1)

        val savedList = widgetWrappers.mapIndexed { index, wrapper ->
            val col = ((wrapper.translationX + cellWidth / 2) / (cellWidth + CELL_MARGIN)).toInt().coerceIn(0, GRID_COLUMNS - 1)
            val row = ((wrapper.translationY + cellHeight / 2) / (cellHeight + CELL_MARGIN)).toInt().coerceAtLeast(0)
            val cellsW = ((wrapper.width + CELL_MARGIN) / (cellWidth + CELL_MARGIN)).coerceAtLeast(wrapper.defaultCellsW)
            val cellsH = ((wrapper.height + CELL_MARGIN) / (cellHeight + CELL_MARGIN)).coerceAtLeast(wrapper.defaultCellsH)
            val widgetWidth = (cellWidth * cellsW).coerceAtLeast(cellWidth)
            val widgetHeight = (cellHeight * cellsH).coerceAtLeast(cellHeight)

            AppLogger.i(
                TAG,
                "💾 SAVE #$index → id=${wrapper.hostView.appWidgetId} | Pinned -> col=${col}, row=${row} | Size -> width=${wrapper.width}, height=${wrapper.height} | Cells -> width=${cellsW}, height=${cellsH}"
            )

            SavedWidgetEntity(wrapper.hostView.appWidgetId, col, row, widgetWidth, widgetHeight, cellsW, cellsH)
        }


        // Save asynchronously
        lifecycleScope.launch {
            widgetDao.insertAll(savedList)
            AppLogger.i(TAG, "💾 Widgets saved to Room: ${savedList.size}")
        }
    }

    /** Restore widgets from JSON */
    private fun restoreWidgets() {
        lifecycleScope.launch {
            val savedWidgets = widgetDao.getAll()
            if (savedWidgets.isEmpty()) {
                AppLogger.w(TAG, "⚠️ No saved widgets found in Room")
                return@launch
            }

            AppLogger.i(TAG, "📥 Restoring ${savedWidgets.size} widgets from Room")

            binding.apply {
                widgetGrid.post {
                    val parentWidth = widgetGrid.width.coerceAtLeast(1)
                    val cellWidth = (parentWidth - CELL_MARGIN * (GRID_COLUMNS - 1)) / GRID_COLUMNS
                    val cellHeight = cellWidth.coerceAtLeast(1)

                    savedWidgets.forEach { saved ->
                        val info = appWidgetManager.getAppWidgetInfo(saved.appWidgetId)
                        if (info == null) {
                            AppLogger.e(TAG, "❌ No AppWidgetInfo for id=${saved.appWidgetId}, removing")
                            safeRemoveWidget(saved.appWidgetId)
                            return@forEach
                        }

                        val hostView = try {
                            val widgetContext = try {
                                requireContext().createPackageContext(
                                    info.provider.packageName,
                                    Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
                                )
                            } catch (_: Exception) {
                                requireContext()
                            }

                            appWidgetHost.createView(widgetContext, saved.appWidgetId, info)
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "⚠️ Failed to restore widgetId=${saved.appWidgetId}, removing", e)
                            safeRemoveWidget(saved.appWidgetId)
                            return@forEach
                        }

                        val wrapper = ResizableWidgetWrapper(
                            requireContext(),
                            hostView,
                            info,
                            appWidgetHost,
                            { saveWidgets() },
                            { deleteWidget(saved.appWidgetId) },
                            GRID_COLUMNS,
                            CELL_MARGIN,
                            saved.cellsW.coerceAtLeast(MIN_CELL_W),
                            saved.cellsH.coerceAtLeast(MIN_CELL_H)
                        )

                        wrapper.translationX = saved.col * (cellWidth + CELL_MARGIN).toFloat()
                        wrapper.translationY = saved.row * (cellHeight + CELL_MARGIN).toFloat()
                        wrapper.layoutParams = FrameLayout.LayoutParams(saved.width, saved.height)

                        addWrapperSafely(wrapper)

                        logWidgetRestored(saved)
                    }
                }
                logGridSnapshot()
            }
        }
    }

    private fun logWidgetRestored(saved: SavedWidgetEntity) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val info = appWidgetManager.getAppWidgetInfo(saved.appWidgetId)

        val packageManager = requireContext().packageManager
        val widgetName = info?.loadLabel(packageManager) ?: "Unknown Widget"

        val appName = info?.provider?.packageName?.let { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName // fallback if app name can't be resolved
            }
        } ?: "Unknown"

        AppLogger.i(
            TAG,
            "🔄 RESTORED → id=${saved.appWidgetId} | App=$appName | Widget=$widgetName | Pinned -> col=${saved.col}, row=${saved.row} | Size -> width=${saved.width}, height=${saved.height} | Cells -> width=${saved.cellsW}, height=${saved.cellsH}"
        )
    }

    private fun logGridSnapshot() {
        lifecycleScope.launch {
            val savedWidgets = widgetDao.getAll()
            if (savedWidgets.isEmpty()) {
                AppLogger.i(TAG, "⚠️ No widgets in database, grid empty")
                return@launch
            }

            val maxRow = (savedWidgets.maxOfOrNull { it.row + it.cellsH } ?: 0)
            val grid = Array(maxRow) { Array(GRID_COLUMNS) { "□" } }

            savedWidgets.forEach { w ->
                for (r in w.row until w.row + w.cellsH) {
                    for (c in w.col until w.col + w.cellsW) {
                        if (r in grid.indices && c in 0 until GRID_COLUMNS) {
                            grid[r][c] = "■"
                        }
                    }
                }
            }

            val snapshot = grid.joinToString("\n") { it.joinToString(" ") }
            AppLogger.i(TAG, "📐 Grid Snapshot:\n$snapshot")
        }
    }

    private fun updateEmptyPlaceholder(wrappers: List<ResizableWidgetWrapper>) {
        val shouldBeVisible = wrappers.isEmpty()

        // Only update if visibility changed
        if (placeholderVisible == shouldBeVisible) {
            AppLogger.v(TAG, "updateEmptyPlaceholder: no change (visible=$placeholderVisible)")
            return
        }

        placeholderVisible = shouldBeVisible

        binding.emptyPlaceholder.isVisible = shouldBeVisible

        AppLogger.i(TAG, if (shouldBeVisible) "🟨 Showing empty placeholder" else "🟩 Hiding empty placeholder")
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        AppLogger.i(TAG, "🔗 WidgetFragment onAttach called, context=$context")
        widgetDao = WidgetDatabase.getDatabase(requireContext()).widgetDao()
        if (!isViewCreated()) {
            appWidgetHost = AppWidgetHost(context, APP_WIDGET_HOST_ID)
            appWidgetHost.startListening()
            AppLogger.i(TAG, "🟢 Initialized AppWidgetHost")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appWidgetHost.stopListening()
        AppLogger.i(TAG, "🛑 AppWidgetHost stopped listening")
    }

    fun isViewCreated(): Boolean = _binding?.widgetGrid != null

}