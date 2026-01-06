package com.github.codeworkscreativehub.common

import android.content.Context
import android.content.res.Resources
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import java.util.Locale

object LauncherLocaleManager {

    fun wrapContext(base: Context): Context {
        val prefs = Prefs(base)

        if (prefs.appLanguage == Constants.Language.System) {
            return base
        }

        val locale = prefs.appLanguage.locale()
        Locale.setDefault(locale)

        val config = base.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return base.createConfigurationContext(config)
    }

    fun updateLanguage(context: Context, language: Constants.Language) {
        val prefs = Prefs(context)
        prefs.appLanguage = language
    }
}

object LocalizedResources {

    @Volatile
    private var cachedResources: Resources? = null

    fun get(context: Context): Resources {
        if (cachedResources == null) {
            synchronized(this) {
                if (cachedResources == null) {
                    val wrapped = LauncherLocaleManager.wrapContext(
                        context.applicationContext
                    )
                    cachedResources = wrapped.resources
                }
            }
        }
        return cachedResources!!
    }

    fun invalidate() {
        cachedResources = null
    }
}




