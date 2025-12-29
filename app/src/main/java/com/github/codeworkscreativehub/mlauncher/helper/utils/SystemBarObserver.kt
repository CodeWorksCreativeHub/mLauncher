package com.github.codeworkscreativehub.mlauncher.helper.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.hideNavigationBar
import com.github.codeworkscreativehub.mlauncher.helper.hideStatusBar
import com.github.codeworkscreativehub.mlauncher.helper.showNavigationBar
import com.github.codeworkscreativehub.mlauncher.helper.showStatusBar

class SystemBarObserver(private val prefs: Prefs) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        val activity = owner as? AppCompatActivity ?: return
        val window = activity.window
        if (prefs.showStatusBar) showStatusBar(window) else hideStatusBar(window)
        if (prefs.showNavigationBar) showNavigationBar(window) else hideNavigationBar(window)
    }
}
