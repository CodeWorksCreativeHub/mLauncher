package com.github.codeworkscreativehub.mlauncher.helper.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.helper.ismlauncherDefault

class PrivateSpaceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_USER_UNLOCKED) {
            // Check if mLauncher is the default launcher
            if (ismlauncherDefault(context)) {
                // Notify the user that mLauncher is now accessible in the private space
                val message = getLocalizedString(R.string.toast_private_space_unlocked)
                context.showLongToast(message)
            }
        }
    }
}