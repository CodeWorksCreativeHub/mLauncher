package com.github.droidworksstudio.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocalizedResourcesProvider {

    @Volatile
    private var cachedLocale: Locale? = null

    @Volatile
    private var cachedResources: Resources? = null

    fun getResources(context: Context, locale: Locale): Resources {
        if (cachedLocale == locale && cachedResources != null) {
            return cachedResources!!
        }

        synchronized(this) {
            if (cachedLocale != locale || cachedResources == null) {
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)

                val localizedContext = context.createConfigurationContext(config)
                cachedResources = localizedContext.resources
                cachedLocale = locale
            }
            return cachedResources!!
        }
    }

    fun clearCache() {
        synchronized(this) {
            cachedLocale = null
            cachedResources = null
        }
    }
}
