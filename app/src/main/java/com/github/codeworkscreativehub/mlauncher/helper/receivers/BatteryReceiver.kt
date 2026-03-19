package com.github.codeworkscreativehub.mlauncher.helper.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.creativecodecat.components.views.FontAppCompatTextView

class BatteryReceiver(
    private val batteryTextView: FontAppCompatTextView,
    private val prefs: Prefs
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // Try to get level/scale from the intent
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val batteryLevel: Float = if (level >= 0 && scale > 0) {
            level * 100f / scale
        } else {
            // Fallback using BatteryManager
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toFloat()
        }

        // Select battery drawable based on percentage
        val batteryDrawable = when {
            batteryLevel >= 76 -> ContextCompat.getDrawable(context, R.drawable.app_battery100)
            batteryLevel >= 51 -> ContextCompat.getDrawable(context, R.drawable.app_battery75)
            batteryLevel >= 26 -> ContextCompat.getDrawable(context, R.drawable.app_battery50)
            else -> ContextCompat.getDrawable(context, R.drawable.app_battery25)
        }

        // Update drawable
        batteryDrawable?.let {
            val textSize = batteryTextView.textSize.toInt()
            if (prefs.showBatteryIcon) {
                it.setBounds(0, 0, textSize, textSize)
                batteryTextView.setCompoundDrawables(it, null, null, null)
            } else {
                it.setBounds(0, 0, 0, 0)
                batteryTextView.setCompoundDrawables(null, null, null, null)
            }
        }

        val batteryLevelInt = batteryLevel.toInt()
        batteryTextView.text = buildString {
            append(batteryLevelInt)
            append("%")
        }
    }
}
