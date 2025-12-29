package com.github.codeworkscreativehub.mlauncher.helper.analytics

import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.formatLongToCalendar
import com.github.codeworkscreativehub.mlauncher.helper.formatMillisToHMS
import com.github.codeworkscreativehub.mlauncher.helper.parseBlacklistXML
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class AppUsageMonitor private constructor(context: Context) {
    // TODO looks like a singleton? Shall we just make a top-level object in the file?
    companion object {
        private var instance: AppUsageMonitor? = null

        fun createInstance(context: Context): AppUsageMonitor {
            if (instance == null) {
                instance = AppUsageMonitor(context.applicationContext)
            }
            return instance!!
        }

        fun getInstance(context: Context): AppUsageMonitor {
            return instance ?: synchronized(this) {
                instance ?: AppUsageMonitor(context).also { instance = it }
            }
        }
    }

    private val appLastUsedMap: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    private val packageManager: PackageManager = context.packageManager

    @SuppressLint("NewApi")
    fun getUsageStats(context: Context, targetPackageName: String): Long {
        val packageManager = context.packageManager
        val blacklist = parseBlacklistXML(context)

        // Get start of today in the correct timezone
        val startOfToday = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val currentTime = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
        }.timeInMillis

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0L // Return 0 if service is unavailable

        // Get launcher apps (home screen apps)
        val launcherApps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toSet()

        val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfToday, currentTime)
            ?: return 0L // Return 0 if no data is available

        return usageStatsList
            .filter { usageStats ->
                val packageNameToCheck = usageStats.packageName

                // Only keep the stats of the specific package
                val isValid = packageNameToCheck == targetPackageName &&
                        !launcherApps.contains(packageNameToCheck) &&
                        !blacklist.contains(packageNameToCheck) && // Check against the blacklist
                        usageStats.totalTimeInForeground > 0 && // Ensure there's usage time
                        startOfToday <= usageStats.lastTimeUsed  // Ensure usage happened today
                // Log the valid app's stats if it's valid
                if (isValid) {
                    AppLogger.d(
                        "getUsageStats",
                        "${usageStats.packageName} : ${formatMillisToHMS(usageStats.totalTimeInForeground, true)} " +
                                "${formatLongToCalendar(startOfToday)} ${formatLongToCalendar(usageStats.lastTimeUsed)}"
                    )
                }

                isValid
            }
            .sumOf { it.totalTimeInForeground } // Sum the total time for valid apps
    }


    @SuppressLint("NewApi")
    fun getTotalScreenTime(context: Context): Long {
        val packageManager = context.packageManager
        val blacklist = parseBlacklistXML(context)
        val currentPackageName = context.packageName // Get current app's package name

        // Get start of today in the correct timezone
        val startOfToday = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val currentTime = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
        }.timeInMillis
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Get launcher apps (home screen apps)
        val launcherApps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toSet()

        // Query usage stats for today
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startOfToday, currentTime
        ) ?: return 0L

        return usageStatsList
            .filter { usageStats ->
                val packageName = usageStats.packageName
                // Exclude launcher apps, blacklisted apps, and current app
                val isValid = packageName != currentPackageName &&
                        !launcherApps.contains(packageName) &&
                        !blacklist.contains(packageName) &&
                        usageStats.totalTimeInForeground > 0 &&
                        // Ensure usage happened today
                        startOfToday <= usageStats.lastTimeUsed
                if (isValid) {
                    AppLogger.d(
                        "getTotalScreenTime",
                        "${usageStats.packageName} : ${formatMillisToHMS(usageStats.totalTimeVisible, true)} ${formatLongToCalendar(startOfToday)} ${
                            formatLongToCalendar(
                                usageStats
                                    .lastTimeUsed
                            )
                        }"
                    )
                }
                isValid
            }
            .sumOf { it.totalTimeInForeground }
    }

    fun updateLastUsedTimestamp(packageName: String) {
        val currentTime = System.currentTimeMillis()
        appLastUsedMap[packageName] = currentTime
    }

    fun getLastTenAppsUsed(context: Context): List<Triple<String, String, String>> {
        // Set is to store unique package names
        val result = mutableListOf<Triple<String, String, String>>() // List to store recent apps
        val recentApps = mutableSetOf<String>()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 24 * 60 * 60 * 1000 // 24 hours ago
        val blacklist = parseBlacklistXML(context)
        val prefs = Prefs(context)

        usageStatsManager?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)?.let { usageStatsList ->
            val sortedList = usageStatsList
                .filter { isPackageLaunchable(context, it.packageName, blacklist) }
                .sortedByDescending { it.lastTimeUsed }

            sortedList.forEach { usageStats ->
                val packageName = usageStats.packageName
                if (packageName != context.packageName && !recentApps.contains(packageName)) {
                    val appName = getAppNameFromPackage(packageName)
                    val className = getComponentNameFromPackage(context, packageName)
                    val appActivityName = className.toString()
                    AppLogger.d("appActivityName", appActivityName)
                    if (appName != null) {
                        recentApps.add(packageName)
                        result.add(Triple(packageName, appName, appActivityName))
                    }
                }
            }
        }

        return result.take(prefs.recentCounter) // Return up to 10 recent apps
    }


    private fun isPackageLaunchable(context: Context, packageName: String, blacklist: List<String>): Boolean {
        if (isAppInBlacklist(packageName, blacklist)) {
            return false
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return launchIntent != null
    }

    private fun isAppInBlacklist(appPackageName: String, blacklist: List<String>): Boolean {
        return blacklist.contains(appPackageName)
    }

    private fun getComponentNameFromPackage(context: Context, packageName: String): String? {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            val componentName = launchIntent.component
            componentName?.className
        } else {
            null
        }
    }


    private fun getAppNameFromPackage(packageName: String): String? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
