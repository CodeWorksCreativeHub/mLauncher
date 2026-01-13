package com.github.codeworkscreativehub.mlauncher.data

import android.content.Context
import androidx.core.content.edit
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.BuildConfig
import java.io.File

class Migration(val context: Context) {
    fun migratePreferencesOnVersionUpdate(prefs: Prefs) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val savedVersionCode = prefs.appVersion

        AppLogger.d("PrefsMigration", "Starting migration: savedVersion=$savedVersionCode, currentVersion=$currentVersionCode")

        // Map of version code -> preferences to clear (wildcards allowed)
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
            1110303 to listOf(
                "APP_ALIAS_*"
            )
        )

        var totalRemoved = 0

        for ((version, keys) in versionCleanupMap) {
            if (version in (savedVersionCode)..currentVersionCode) {
                val allKeys = prefs.prefsNormal.all.keys
                val removedThisVersion = mutableListOf<String>()

                prefs.prefsNormal.edit {
                    keys.forEach { keyPattern ->
                        if (keyPattern.contains("*")) {
                            val prefix = keyPattern.removeSuffix("*")
                            val matchedKeys = allKeys.filter { it.startsWith(prefix) }
                            matchedKeys.forEach { key ->
                                remove(key)
                                removedThisVersion.add(key)
                                totalRemoved++
                            }
                            if (matchedKeys.isNotEmpty()) {
                                AppLogger.d(
                                    "PrefsMigration",
                                    "Version $version wildcard pattern '$keyPattern' removed: ${matchedKeys.joinToString()}"
                                )
                            }
                        } else {
                            remove(keyPattern)
                            removedThisVersion.add(keyPattern)
                            totalRemoved++
                            AppLogger.d(
                                "PrefsMigration",
                                "Version $version removed key: $keyPattern"
                            )
                        }
                    }
                }

                if (removedThisVersion.isEmpty()) {
                    AppLogger.d("PrefsMigration", "Version $version had no keys to remove.")
                }
            }
        }

        prefs.appVersion = currentVersionCode
        AppLogger.d(
            "PrefsMigration",
            "Migration completed: updated app version to $currentVersionCode, total keys removed: $totalRemoved"
        )
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