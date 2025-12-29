package com.github.codeworkscreativehub.mlauncher.ui.widgets.wordoftheday

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.github.codeworkscreativehub.mlauncher.ui.widgets.wordoftheday.WordOfTheDayWidget.Companion.updateWidgets

class WordOfTheDayUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, WordOfTheDayWidget::class.java))

        // Reschedule midnight alarm if updating Word of the Day
        if (intent.action == "com.github.codeworkscreativehub.mlauncher.ui.widgets.wordofday.UPDATE_WIDGET") {
            updateWidgets(context, appWidgetManager, appWidgetIds)
            WordOfTheDayAlarm.scheduleNextUpdate(context)
        }
    }
}
