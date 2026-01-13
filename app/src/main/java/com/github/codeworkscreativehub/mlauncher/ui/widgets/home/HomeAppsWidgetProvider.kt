package com.github.codeworkscreativehub.mlauncher.ui.widgets.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.IconCacheTarget
import com.github.codeworkscreativehub.mlauncher.helper.IconPackHelper.getSafeAppIcon
import com.github.codeworkscreativehub.mlauncher.helper.getSystemIcons

class HomeAppsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = Prefs(context)
        val numApps = prefs.homeAppsNum

        for (appWidgetId in appWidgetIds) {
            val rv = RemoteViews(context.packageName, R.layout.widget_home_apps)
            rv.removeAllViews(R.id.homeAppsLayout)

            for (i in 0 until numApps) {
                val appModel = prefs.getHomeAppModel(i)
                val packageName = appModel.activityPackage
                if (packageName.isEmpty()) continue

                val itemRv = RemoteViews(context.packageName, R.layout.item_home_app)

                // --- App label ---
                val appLabel = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(appModel.activityLabel, 0)
                    ).toString()
                } catch (_: Exception) {
                    appModel.activityLabel
                }

                // --- Get icon safely ---
                val iconPackPackage = prefs.customIconPackHome

                val nonNullDrawable: Drawable = getSafeAppIcon(
                    context = context,
                    packageName = packageName,
                    useIconPack = (iconPackPackage.isNotEmpty() && prefs.iconPackHome == Constants.IconPacks.Custom),
                    iconPackTarget = IconCacheTarget.HOME
                )

                val recoloredDrawable: Drawable? = getSystemIcons(
                    context,
                    prefs,
                    IconCacheTarget.HOME,
                    nonNullDrawable
                )

                // --- Set TextView text ---
                itemRv.setTextViewText(R.id.appLabel, appLabel)

                // Optional: text size/color from prefs
                itemRv.setTextViewTextSize(R.id.appLabel, TypedValue.COMPLEX_UNIT_SP, prefs.appSize.toFloat())
                itemRv.setTextColor(R.id.appLabel, prefs.appColor)

                // --- Set gravity based on prefs.homeAlignment ---
                itemRv.setInt(R.id.appLabel, "setGravity", prefs.homeAlignment.value())

                // --- Set icon in ImageView ---
                // Calculate icon size and padding
                var iconSize = (prefs.appSize * 1.4f).toInt()
                if (prefs.iconPackHome == Constants.IconPacks.System || prefs.iconPackHome == Constants.IconPacks.Custom) {
                    iconSize *= 2
                }
                val iconPadding = (iconSize / 1.2f).toInt() // padding next to icon

                val drawableToUse = recoloredDrawable ?: nonNullDrawable

                // Convert drawable to bitmap
                val bitmap = drawableToBitmap(drawableToUse, iconSize)

                // Apply alignment
                when (prefs.homeAlignment) {
                    Constants.Gravity.Left -> {
                        // Left icon visible, right icon gone
                        itemRv.setImageViewBitmap(R.id.appIconLeft, bitmap)
                        itemRv.setViewVisibility(R.id.appIconLeft, android.view.View.VISIBLE)
                        itemRv.setViewVisibility(R.id.appIconRight, android.view.View.GONE)

                        // Padding on right of icon
                        itemRv.setViewPadding(R.id.appIconLeft, 0, 0, iconPadding, 0)
                    }

                    Constants.Gravity.Right -> {
                        // Right icon visible, left icon gone
                        itemRv.setImageViewBitmap(R.id.appIconRight, bitmap)
                        itemRv.setViewVisibility(R.id.appIconRight, android.view.View.VISIBLE)
                        itemRv.setViewVisibility(R.id.appIconLeft, android.view.View.GONE)

                        // Padding on left of icon
                        itemRv.setViewPadding(R.id.appIconRight, iconPadding, 0, 0, 0)
                    }

                    else -> {
                        itemRv.setViewVisibility(R.id.appIconLeft, android.view.View.GONE)
                        itemRv.setViewVisibility(R.id.appIconRight, android.view.View.GONE)
                    }
                }

                val textPaddingSize = prefs.textPaddingSize // pixels, or convert dp to px
                itemRv.setViewPadding(R.id.appLabel, 0, textPaddingSize, 0, textPaddingSize)


                // --- Click intent ---
                val clickIntent = Intent(context, HomeAppUpdateReceiver::class.java).apply {
                    action = "HOME_APP_CLICK"
                    putExtra("PACKAGE_NAME", packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, i, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                itemRv.setOnClickPendingIntent(R.id.appIconLeft, pendingIntent)
                itemRv.setOnClickPendingIntent(R.id.appLabel, pendingIntent) // Optional: click text too

                rv.addView(R.id.homeAppsLayout, itemRv)
            }

            appWidgetManager.updateAppWidget(appWidgetId, rv)
        }
    }

    fun drawableToBitmap(drawable: Drawable, maxSizePx: Int): Bitmap {
        // Clamp width and height to maxSizePx to avoid RemoteViews exceptions
        val intrinsicWidth = drawable.intrinsicWidth.coerceAtMost(maxSizePx)
        val intrinsicHeight = drawable.intrinsicHeight.coerceAtMost(maxSizePx)

        // Create bitmap with ARGB_8888 (safe for RemoteViews)
        val bitmap = createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1))

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }


}

