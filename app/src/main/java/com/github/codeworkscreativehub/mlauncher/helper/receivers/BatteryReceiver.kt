package com.github.codeworkscreativehub.mlauncher.helper.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.github.creativecodecat.components.views.FontAppCompatTextView
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs

class BatteryReceiver : BroadcastReceiver() {

    private lateinit var prefs: Prefs

    override fun onReceive(context: Context, intent: Intent) {
        prefs = Prefs(context)
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val contextBattery = context as? Activity
        val batteryTextView = (contextBattery)?.findViewById<FontAppCompatTextView>(R.id.battery)

        val batteryLevel = level * 100 / scale.toFloat()

        val batteryDrawable = when {
            batteryLevel >= 76 -> ContextCompat.getDrawable(
                context,
                R.drawable.app_battery100
            )

            batteryLevel >= 51 -> ContextCompat.getDrawable(
                context,
                R.drawable.app_battery75
            )

            batteryLevel >= 26 -> ContextCompat.getDrawable(
                context,
                R.drawable.app_battery50
            )

            else -> ContextCompat.getDrawable(context, R.drawable.app_battery25)
        }

        batteryDrawable?.let {
            // Resize the drawable to match the text size
            val textSize = batteryTextView?.textSize?.toInt()
            if (prefs.showBatteryIcon) {
                textSize?.let { bottom -> it.setBounds(0, 0, textSize, bottom) }
                batteryTextView?.setCompoundDrawables(it, null, null, null)
            } else {
                it.setBounds(0, 0, 0, 0)
                batteryTextView?.setCompoundDrawables(null, null, null, null)
            }
        }

        val batteryLevelInt = batteryLevel.toInt()
        batteryTextView?.text = buildString {
            append(batteryLevelInt)
            append("%")
        }

    }
}

