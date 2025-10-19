package com.github.droidworksstudio.mlauncher.data

import android.content.Context
import com.github.droidworksstudio.common.AppLogger
import com.github.droidworksstudio.mlauncher.BuildConfig
import java.io.File

class Migration(val context: Context) {
    fun migratePreferencesOnVersionUpdate(prefs: Prefs) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val savedVersionCode = prefs.appVersion

        // Define a map of version code -> preferences to clear
        val versionCleanupMap = mapOf(
            171 to listOf(
                "APP_DARK_COLORS",
                "APP_LIGHT_COLORS",
                "HOME_FOLLOW_ACCENT",
                "ALL_APPS_TEXT",
            ),
            172 to listOf(
                "TIME_ALIGNMENT",
                "SHOW_TIME",
                "SHOW_TIME_FORMAT",
                "TIME_COLOR",
            ),
            175 to listOf(
                "CLICK_APP_USAGE",
            ),
            10803 to listOf(
                "SHOW_EDGE_PANEL",
                "EDGE_APPS_NUM",
            ),
            10812 to listOf(
                "LOCK_MODE",
            ),
            1100504 to listOf(
                "SHOW_AZSIDEBAR",
            ),
            1100508 to listOf(
                "EXPERIMENTAL_OPTIONS",
            ),
            1100709 to listOf(
                "APP_TIMER",
                "SHORT_SWIPE_THRESHOLD",
                "LONG_SWIPE_THRESHOLD",
            ),
            // Add more versions and preferences to remove here
        )

        // Iterate over the versions and clear the relevant preferences
        for ((version, keys) in versionCleanupMap) {
            // Only clear preferences for versions between savedVersionCode and currentVersionCode
            if (version in (savedVersionCode + 1)..currentVersionCode) {
                // Remove the preferences for this version
                keys.forEach { key ->
                    prefs.remove(key)
                }
            }
        }

        // Update the stored version code after cleanup
        prefs.appVersion = currentVersionCode
    }

    fun migrateMessages(prefs: Prefs) {
        try {
            // Try to parse as the new format
            val messages = prefs.loadMessages()
            if (messages.isNotEmpty()) {
                // Already in correct format — no need to migrate
                return
            }
        } catch (_: Exception) {
            // If loading as new format fails, try migrating from the old format
            try {
                val wrongMessages = prefs.loadMessagesWrong()
                val correctedMessages = wrongMessages.map { wrong ->
                    Message(
                        text = wrong.a,
                        timestamp = wrong.b,
                        category = wrong.c,
                        priority = wrong.d
                    )
                }
                prefs.saveMessages(correctedMessages)
                AppLogger.d("Migration", "Migration passed")
            } catch (e: Exception) {
                // Log or handle if even legacy format is broken
                AppLogger.e("Migration", "Migration failed", e)
            }
        }
    }

    fun deleteOldCacheFiles(appContext: Context) {
        // References to the old files in filesDir
        val oldAppsCacheFile = File(appContext.filesDir, "apps_cache.json")
        val oldContactsCacheFile = File(appContext.filesDir, "contacts_cache.json")

        // Delete them if they exist
        if (oldAppsCacheFile.exists()) {
            oldAppsCacheFile.delete()
            AppLogger.d("CacheCleanup", "apps_cache.json deleted")
        }

        if (oldContactsCacheFile.exists()) {
            oldContactsCacheFile.delete()
            AppLogger.d("CacheCleanup", "contacts_cache.json deleted")
        }
    }
}