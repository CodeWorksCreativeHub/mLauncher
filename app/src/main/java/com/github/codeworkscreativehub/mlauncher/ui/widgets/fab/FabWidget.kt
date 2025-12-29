package com.github.codeworkscreativehub.mlauncher.ui.widgets.fab

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.ColorManager
import com.github.codeworkscreativehub.common.openCameraApp
import com.github.codeworkscreativehub.common.openDeviceSettings
import com.github.codeworkscreativehub.common.openDialerApp
import com.github.codeworkscreativehub.common.openPhotosApp
import com.github.codeworkscreativehub.common.openTextMessagesApp
import com.github.codeworkscreativehub.common.openWebBrowser
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.utils.AppReloader

class FabWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = Prefs(context)

        // Default flags as a String
        val defaultFlagsString = "0000011"

        // Get flags (already List<Boolean>)
        val fabFlags: List<Boolean> = prefs.getMenuFlags("HOME_BUTTON_FLAGS", defaultFlagsString)

        val fabIds = listOf(
            R.id.fabPhone,
            R.id.fabMessages,
            R.id.fabCamera,
            R.id.fabPhotos,
            R.id.fabBrowser,
            R.id.fabSettings,
            R.id.fabAction
        )

        // Generate colors
        val colors = ColorManager.getRandomHueColors(prefs.shortcutIconsColor, fabIds.size)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_fab)

            for (index in fabIds.indices) {
                val viewId = fabIds[index]
                val isVisible = fabFlags.getOrNull(index) ?: false
                views.setViewVisibility(viewId, if (isVisible) View.VISIBLE else View.GONE)

                // Set color filter using when
                when (viewId) {
                    R.id.fabPhone,
                    R.id.fabMessages,
                    R.id.fabCamera,
                    R.id.fabPhotos,
                    R.id.fabBrowser,
                    R.id.fabSettings -> {
                        val color = colors.getOrNull(index) ?: prefs.shortcutIconsColor
                        views.setInt(viewId, "setColorFilter", if (prefs.iconRainbowColors) color else prefs.shortcutIconsColor)
                    }

                    R.id.fabAction -> {
                        // No color filter for fabAction
                    }
                }
            }

            // Assign PendingIntents only for visible buttons
            if (fabFlags.getOrNull(0) == true) views.setOnClickPendingIntent(
                R.id.fabPhone,
                getBroadcastPendingIntent(context, "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_PHONE")
            )
            if (fabFlags.getOrNull(1) == true) views.setOnClickPendingIntent(
                R.id.fabMessages,
                getBroadcastPendingIntent(context, "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_MESSAGES")
            )

            if (fabFlags.getOrNull(2) == true) views.setOnClickPendingIntent(
                R.id.fabCamera,
                getBroadcastPendingIntent(context, "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_CAMERA")
            )

            if (fabFlags.getOrNull(3) == true) views.setOnClickPendingIntent(
                R.id.fabPhotos,
                getBroadcastPendingIntent(context, "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_PHOTOS")
            )

            if (fabFlags.getOrNull(4) == true) views.setOnClickPendingIntent(
                R.id.fabBrowser,
                getBroadcastPendingIntent(context, "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_BROWSER")
            )

            if (fabFlags.getOrNull(5) == true) views.setOnClickPendingIntent(
                R.id.fabSettings,
                getBroadcastPendingIntent(context, "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_SETTINGS")
            )
            if (fabFlags.getOrNull(6) == true) views.setOnClickPendingIntent(
                R.id.fabAction,
                getBroadcastPendingIntent(context, "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_ACTION")
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }


    fun getBroadcastPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, FabClickReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class FabClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        AppLogger.d("FabClickReceiver", "onReceive: $action")
        when (action) {
            "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_PHONE" -> context.openDialerApp()
            "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_MESSAGES" -> context.openTextMessagesApp()
            "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_CAMERA" -> context.openCameraApp()
            "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_PHOTOS" -> context.openPhotosApp()
            "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_BROWSER" -> context.openWebBrowser()
            "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_SETTINGS" -> context.openDeviceSettings()
            "com.github.codeworkscreativehub.mlauncher.ui.widgets.fab.FAB_ACTION" -> AppReloader.startApp(context)
            // handle other buttons...
        }
    }
}

