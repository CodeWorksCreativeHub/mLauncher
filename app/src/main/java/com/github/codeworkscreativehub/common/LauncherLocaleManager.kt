package com.github.codeworkscreativehub.common

import androidx.appcompat.app.AppCompatDelegate
import com.github.codeworkscreativehub.mlauncher.Mlauncher
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Prefs

object LauncherLocaleManager {
    fun applyAppLanguage() {
        val prefs = Prefs(Mlauncher.getContext())

        val primaryLocale = prefs.appLanguage.locale()

        val localeList = if (prefs.appLanguage == Constants.Language.System) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.create(primaryLocale)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}


