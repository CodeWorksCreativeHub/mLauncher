package com.github.codeworkscreativehub.mlauncher.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.databinding.ActivityWidgetBinding
import com.github.codeworkscreativehub.mlauncher.helper.getHexForOpacity
import com.github.codeworkscreativehub.mlauncher.helper.utils.SystemBarObserver

class WidgetActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var binding: ActivityWidgetBinding

    companion object {
        private const val TAG = "WidgetActivity"
    }

    private lateinit var widgetPermissionLauncher: ActivityResultLauncher<Intent>
    private var widgetResultCallback: ((Int, Int, Intent?) -> Unit)? = null
    private val pendingWidgets = mutableListOf<Pair<AppWidgetProviderInfo, Int>>()
    private var awaitingViewForFlush = false

    // ───────────────────────────────────────────────
    // Lifecycle
    // ───────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        AppLogger.d(TAG, "🟢 onCreate() called — savedInstanceState=$savedInstanceState")
        super.onCreate(savedInstanceState)

        // Initialize preferences
        prefs = Prefs(this)

        // Enable edge-to-edge layout
        enableEdgeToEdge()

        // Lock orientation if user preference is set (mirroring MainActivity behavior)
        val currentOrientation = resources.configuration.orientation
        requestedOrientation = if (prefs.lockOrientation) {
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // Observe and handle system bars (like MainActivity)
        val systemBarObserver = SystemBarObserver(prefs)
        lifecycle.addObserver(systemBarObserver)

        // Setup result launcher
        AppLogger.v(TAG, "Initializing ActivityResult launcher for widget permissions")
        widgetPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                AppLogger.v(TAG, "🎬 Received ActivityResult: resultCode=${result.resultCode}, data=${result.data}")
                // Clear before invoking: the callback may launch another step that sets a new callback
                val callback = widgetResultCallback
                widgetResultCallback = null
                callback?.invoke(
                    result.resultCode,
                    result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1,
                    result.data
                )
            }

        binding = ActivityWidgetBinding.inflate(layoutInflater)

        binding.mainActivityLayout.apply {
            setBackgroundColor(getHexForOpacity(prefs))
        }
        val view = binding.root
        setContentView(view)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainActivityLayout, WidgetFragment())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        AppLogger.v(TAG, "🟢 onStart()")
    }

    override fun onResume() {
        super.onResume()
        AppLogger.v(TAG, "🟢 onResume()")
    }


    override fun onPause() {
        super.onPause()
        AppLogger.v(TAG, "🟠 onPause()")
    }

    override fun onStop() {
        super.onStop()
        AppLogger.v(TAG, "🔴 onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.v(TAG, "⚫ onDestroy() — clearing callbacks and pending list")
        widgetResultCallback = null
        pendingWidgets.clear()
    }

    // ───────────────────────────────────────────────
    // Widget management
    // ───────────────────────────────────────────────
    fun launchWidgetPermission(intent: Intent, callback: (Int, Int, Intent?) -> Unit) {
        AppLogger.d(TAG, "Launching widget permission intent: $intent")
        widgetResultCallback = callback
        widgetPermissionLauncher.launch(intent)
    }

    fun safeCreateWidget(widgetInfo: AppWidgetProviderInfo, appWidgetId: Int) {
        val widgetLabel = widgetInfo.loadLabel(packageManager).toString()
        AppLogger.d(TAG, "Attempting to create widget: $widgetLabel (id=$appWidgetId)")

        val fragment = supportFragmentManager.findFragmentById(R.id.mainActivityLayout) as? WidgetFragment

        // Queue unless the view exists: a widget created while the view is gone would otherwise be dropped
        if (fragment != null && fragment.isAdded && fragment.isViewCreated()) {
            AppLogger.i(TAG, "✅ WidgetFragment is attached, creating widget wrapper immediately")
            fragment.createWidgetWrapperSafe(widgetInfo, appWidgetId)
        } else {
            pendingWidgets.add(widgetInfo to appWidgetId)
            AppLogger.w(
                TAG,
                "⚠️ WidgetFragment not attached, queued widget (id=$appWidgetId, label=$widgetLabel)"
            )
        }
    }

    //
    fun flushPendingWidgets() {
        val fragment = supportFragmentManager
            .findFragmentById(R.id.mainActivityLayout) as? WidgetFragment

        if (fragment == null) {
            AppLogger.w(TAG, "❌ WidgetFragment not found, cannot flush widgets")
            return
        }

        AppLogger.d(TAG, "Found fragment. isAdded=${fragment.isAdded}, isViewCreated=${fragment.isViewCreated()}")

        if (!fragment.isAdded || !fragment.isViewCreated()) {
            AppLogger.w(TAG, "⚠️ Fragment not ready, will retry when view is created")

            // Observe the fragment's view lifecycle once; repeated calls would stack observers
            if (awaitingViewForFlush) return
            awaitingViewForFlush = true
            fragment.viewLifecycleOwnerLiveData.observe(this) { owner ->
                owner?.lifecycle?.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
                    override fun onCreate(owner: androidx.lifecycle.LifecycleOwner) {
                        owner.lifecycle.removeObserver(this)
                        AppLogger.i(TAG, "✅ Fragment view is now created, retrying flush")
                        awaitingViewForFlush = false
                        flushPendingWidgets() // Retry
                    }
                })
            }

            return
        }

        if (pendingWidgets.isEmpty()) {
            AppLogger.i(TAG, "⚪ No pending widgets to flush")
            return
        }

        // Build a descriptive summary of pending widgets
        val widgetSummary = pendingWidgets.joinToString(separator = ", ") { (info, id) ->
            "${info.loadLabel(fragment.requireContext().packageManager)}(id=$id)"
        }

        AppLogger.i(TAG, "✅ Fragment ready, flushing ${pendingWidgets.size} pending widget(s): $widgetSummary")

        // Post and clear
        fragment.postPendingWidgets(pendingWidgets.toList())
        pendingWidgets.clear()
        AppLogger.d(TAG, "🔴 Pending widgets cleared after posting")
    }
}
