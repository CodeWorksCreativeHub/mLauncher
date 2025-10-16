package com.github.droidworksstudio.mlauncher.ui

import android.app.WallpaperManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.helper.updateAllWidgets
import com.github.droidworksstudio.mlauncher.helper.utils.SystemBarObserver

open class BaseFragment : Fragment() {

    private val prefs: Prefs by lazy { Prefs(requireContext()) }

    private val systemBarObserver: SystemBarObserver by lazy { SystemBarObserver(prefs) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Attach the observer; lazy properties will initialize here
        lifecycle.addObserver(systemBarObserver)

        updateAllWidgets(requireContext())

        val themeMode = when (prefs.appTheme) {
            Constants.Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
            Constants.Theme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            Constants.Theme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    override fun onStart() {
        super.onStart()

        if (prefs.forceWallpaper) {
            val wallpaperManager = WallpaperManager.getInstance(requireContext())
            val backgroundColor = prefs.backgroundColor

            // Create a solid color bitmap
            val bitmap = createBitmap(1, 1).apply {
                eraseColor(backgroundColor)
            }

            // Set the wallpaper
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM) // home
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)   // lock
        }
    }
}

