package com.github.codeworkscreativehub.mlauncher.ui.widgets.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.github.codeworkscreativehub.common.AppLogger

class HomeAppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d("HomeAppUpdateReceiver", "Received intent: action=${intent.action}, extras=${intent.extras}")

        if (intent.action == "HOME_APP_CLICK") {
            val extras = intent.extras
            if (extras == null) {
                AppLogger.d("HomeAppUpdateReceiver", "No extras found in intent")
                return
            }
            val packageName = extras.getString("PACKAGE_NAME")
            AppLogger.d("HomeAppUpdateReceiver", "PACKAGE_NAME extra: $packageName")

            if (packageName.isNullOrEmpty()) {
                AppLogger.d("HomeAppUpdateReceiver", "No package name provided, aborting launch")
                return
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                AppLogger.d("HomeAppUpdateReceiver", "Launched app: $packageName")
            } else {
                AppLogger.d("HomeAppUpdateReceiver", "Cannot find app: $packageName")
                Toast.makeText(context, "Cannot find app: $packageName", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
