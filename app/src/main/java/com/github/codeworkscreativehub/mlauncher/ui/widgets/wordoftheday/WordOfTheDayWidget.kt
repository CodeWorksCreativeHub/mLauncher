package com.github.codeworkscreativehub.mlauncher.ui.widgets.wordoftheday

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.wordOfTheDay

class WordOfTheDayWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update the Word of the Day (once per day)
        updateWidgets(context, appWidgetManager, appWidgetIds)

        // Schedule midnight alarm for Word of the Day
        WordOfTheDayAlarm.scheduleNextUpdate(context)
    }

    companion object {
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val prefs = Prefs(context)
            val word = wordOfTheDay(prefs)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_word_of_the_day)
                // Set text
                views.setTextViewText(R.id.textViewWord, word)

                // Set text color (example: black)
                views.setTextColor(R.id.textViewWord, prefs.dailyWordColor)

                // Set text size in SP
                views.setTextViewTextSize(R.id.textViewWord, TypedValue.COMPLEX_UNIT_SP, prefs.dailyWordSize.toFloat())
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
