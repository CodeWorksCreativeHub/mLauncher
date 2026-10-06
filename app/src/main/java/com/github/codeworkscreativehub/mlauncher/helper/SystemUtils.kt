package com.github.codeworkscreativehub.mlauncher.helper

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.UiModeManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.WindowInsets
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.ColorIconsExtensions
import com.github.codeworkscreativehub.common.CrashHandler
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.getLocalizedStringArray
import com.github.codeworkscreativehub.common.openAccessibilitySettings
import com.github.codeworkscreativehub.common.requestUsagePermission
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.BuildConfig
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Message
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.utils.packageNames
import com.github.codeworkscreativehub.mlauncher.services.ActionService
import com.github.codeworkscreativehub.mlauncher.ui.widgets.home.HomeAppsWidgetProvider
import com.github.codeworkscreativehub.mlauncher.ui.widgets.wordoftheday.WordOfTheDayWidget
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess


fun emptyString(): String {
    return ""
}

val iconPackActions = listOf(
    "app.mlauncher.THEME",
    "org.adw.launcher.THEMES",
    "com.gau.go.launcherex.theme",
    "com.novalauncher.THEME",
    "com.anddoes.launcher.THEME",
    "com.teslacoilsw.launcher.THEME",
    "app.lawnchair.icons.THEMED_ICON"
)

val iconPackBlacklist = listOf(
    "ginlemon.iconpackstudio"
)

fun hasUsageAccessPermission(context: Context): Boolean {
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOpsManager.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun hasLocationPermission(context: Context): Boolean {
    val fineLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    val coarseLocationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    return fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED
}

fun hasContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
}


fun showPermissionDialog(context: Context) {
    CrashHandler.logUserAction("Show Usage Permission Dialog")
    val builder = MaterialAlertDialogBuilder(context)
    builder.setTitle(getLocalizedString(R.string.permission_required))
    builder.setMessage(getLocalizedString(R.string.access_usage_data_permission))
    builder.setPositiveButton(getLocalizedString(R.string.goto_settings)) { dialogInterface: DialogInterface, _: Int ->
        dialogInterface.dismiss()
        context.requestUsagePermission()
    }
    builder.setNegativeButton(getLocalizedString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int ->
        dialogInterface.dismiss()
    }
    val dialog = builder.create()
    dialog.show()
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return Process.myUserHandle()
}

fun getNextAlarm(context: Context, prefs: Prefs, showDate: Boolean = true): CharSequence {
    val is24HourFormat = DateFormat.is24HourFormat(context)
    val timezone = prefs.appLanguage.locale()

    // Try to query all alarms from the AlarmClock content provider
    val alarms = getAllAlarms(context)

    if (alarms.isNotEmpty()) {
        // Filter out sleep mode alarms and find the next upcoming alarm
        val nextAlarm = alarms
            .filter { alarm ->
                // Filter out disabled alarms
                if (!alarm.enabled) return@filter false

                // Filter out sleep mode alarms based on time
                val isSleepMode = isSleepModeAlarmByTime(alarm.hour, alarm.minute, prefs)
                if (isSleepMode) {
                    AppLogger.d(
                        "AlarmFilter",
                        "Filtered out sleep mode alarm: ${alarm.hour}:${alarm.minute}"
                    )
                    return@filter false
                }

                true
            }
            .minByOrNull { alarm ->
                alarm.getNextTriggerTime()
            }

        if (nextAlarm != null) {
            return formatAlarmDisplay(
                context,
                prefs,
                nextAlarm.getNextTriggerTime(),
                is24HourFormat,
                timezone,
                showDate
            )
        }
    }

    // Fallback to AlarmManager.nextAlarmClock if content provider fails or returns no alarms
    AppLogger.d(
        "AlarmQuery",
        "Content provider failed or returned no alarms, using AlarmManager fallback"
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val nextAlarmClock = alarmManager.nextAlarmClock ?: return "No alarm is set."

    // On devices without content provider access, we can only see the next alarm
    // We cannot distinguish sleep mode from regular alarms or access other alarms
    // So we show whatever the system reports as the next alarm
    return formatAlarmDisplay(
        context,
        prefs,
        nextAlarmClock.triggerTime,
        is24HourFormat,
        timezone,
        showDate
    )
}

/**
 * Format the alarm display with icon and text
 */
private fun formatAlarmDisplay(
    context: Context,
    prefs: Prefs,
    alarmTime: Long,
    is24HourFormat: Boolean,
    timezone: Locale,
    showDate: Boolean
): CharSequence {
    val formattedDate = DateFormat.getBestDateTimePattern(timezone, "eeeddMMM")
    val best12 = DateFormat.getBestDateTimePattern(
        timezone,
        if (prefs.showClockFormat) "hhmma" else "hhmm"
    ).let {
        if (!prefs.showClockFormat) it.removeSuffix(" a") else it
    }
    val best24 = DateFormat.getBestDateTimePattern(timezone, "HHmm")
    val formattedTime = if (is24HourFormat) best24 else best12
    val formattedAlarm = if (showDate) {
        SimpleDateFormat("$formattedDate $formattedTime", Locale.getDefault()).format(alarmTime)
    } else {
        SimpleDateFormat(formattedTime, Locale.getDefault()).format(alarmTime)
    }

    val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_alarm_clock)
    val fontSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        (prefs.alarmSize / 1.5).toFloat(),
        context.resources.displayMetrics
    ).toInt()

    drawable?.apply {
        setBounds(0, 0, fontSize, fontSize)
        val colorFilterColor: ColorFilter =
            PorterDuffColorFilter(prefs.alarmClockColor, PorterDuff.Mode.SRC_IN)
        drawable.colorFilter = colorFilterColor
    }

    val imageSpan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ImageSpan(drawable!!, ImageSpan.ALIGN_CENTER)
    } else {
        CenteredImageSpan(drawable!!)
    }

    return SpannableStringBuilder(" ").apply {
        setSpan(
            imageSpan,
            0, 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        append(" $formattedAlarm")
    }
}

/**
 * Data class representing an alarm from the AlarmClock content provider
 */
private data class AlarmInfo(
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Int,
    val enabled: Boolean,
    val label: String
) {
    /**
     * Calculate the next trigger time for this alarm
     */
    fun getNextTriggerTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val now = Calendar.getInstance()

        // If no days of week set, it's a one-time alarm
        if (daysOfWeek == 0) {
            // If the time has already passed today, schedule for tomorrow
            if (calendar.before(now)) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            return calendar.timeInMillis
        }

        // For recurring alarms, find the next matching day
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Check each day starting from today
        for (i in 0..6) {
            val checkDay = (dayOfWeek - 1 + i) % 7 + 1 // Convert to 1-7 format (Sunday=1)
            val dayBit = 1 shl (checkDay - 1)

            if ((daysOfWeek and dayBit) != 0) {
                val checkCalendar = Calendar.getInstance()
                checkCalendar.set(Calendar.HOUR_OF_DAY, hour)
                checkCalendar.set(Calendar.MINUTE, minute)
                checkCalendar.set(Calendar.SECOND, 0)
                checkCalendar.set(Calendar.MILLISECOND, 0)
                checkCalendar.add(Calendar.DAY_OF_MONTH, i)

                if (checkCalendar.after(now)) {
                    return checkCalendar.timeInMillis
                }
            }
        }

        // If no future day found (shouldn't happen), schedule for next week
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        return calendar.timeInMillis
    }
}

/**
 * Query all alarms from the AlarmClock content provider
 */
private fun getAllAlarms(context: Context): List<AlarmInfo> {
    val alarms = mutableListOf<AlarmInfo>()

    // Get installed clock apps
    val installedClockApps = getInstalledClockApps(context)
    AppLogger.d("AlarmQuery", "Found clock apps: ${installedClockApps.joinToString()}")

    if (installedClockApps.isEmpty()) {
        AppLogger.d("AlarmQuery", "No clock apps found, skipping content provider queries")
        return alarms
    }

    // Map package names to their content provider URIs
    val packageToUriMap = mapOf(
        "com.android.deskclock" to "content://com.android.deskclock/alarm",
        "com.google.android.deskclock" to "content://com.google.android.deskclock/alarm",
        "com.sec.android.app.clockpackage" to "content://com.sec.android.app.clockpackage/alarm",
        "com.htc.android.worldclock" to "content://com.htc.android.worldclock/alarm",
        "com.sonyericsson.organizer" to "content://com.sonyericsson.organizer/alarm",
        "com.lge.clock" to "content://com.lge.clock/alarm",
        "com.miui.weather2" to "content://com.miui.weather2/alarm",
        "com.oneplus.clock" to "content://com.oneplus.clock/alarm"
    )

    // Only try providers for installed clock apps
    for (packageName in installedClockApps) {
        val uriString = packageToUriMap[packageName] ?: continue

        try {
            // Check if provider exists before querying to avoid system errors
            val providerInfo = context.packageManager.resolveContentProvider(
                uriString.toUri().authority ?: continue,
                0
            )

            if (providerInfo == null) {
                AppLogger.d("AlarmQuery", "Provider not available: $uriString")
                continue
            }

            val uri = uriString.toUri()
            val projection = arrayOf(
                "hour",
                "minutes",
                "daysOfWeek",
                "enabled",
                "label"
            )

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val hourIndex = it.getColumnIndex("hour")
                val minutesIndex = it.getColumnIndex("minutes")
                val daysOfWeekIndex = it.getColumnIndex("daysOfWeek")
                val enabledIndex = it.getColumnIndex("enabled")
                val labelIndex = it.getColumnIndex("label")

                while (it.moveToNext()) {
                    val hour = it.getInt(hourIndex)
                    val minute = it.getInt(minutesIndex)
                    val daysOfWeek = it.getInt(daysOfWeekIndex)
                    val enabled = it.getInt(enabledIndex) == 1
                    val label = it.getString(labelIndex) ?: ""

                    alarms.add(AlarmInfo(hour, minute, daysOfWeek, enabled, label))
                }
            }

            // If we got alarms from this provider, don't try the next one
            if (alarms.isNotEmpty()) {
                AppLogger.d("AlarmQuery", "Found ${alarms.size} alarms from $uriString")
                break
            }
        } catch (e: Exception) {
            AppLogger.d("AlarmQuery", "No alarms found from $uriString: ${e.message}")
        }
    }

    return alarms
}

/**
 * Get list of installed clock app package names
 */
private fun getInstalledClockApps(context: Context): List<String> {
    val clockApps = mutableListOf<String>()
    val packageManager = context.packageManager

    val knownClockPackages = listOf(
        "com.android.deskclock",  // Google Clock
        "com.sec.android.app.clockpackage",  // Samsung Clock
        "com.google.android.deskclock",  // Alternative Google Clock
        "com.htc.android.worldclock",  // HTC Clock
        "com.sonyericsson.organizer",  // Sony Clock
        "com.lge.clock",  // LG Clock
        "com.miui.weather2",  // Xiaomi Clock
        "com.oneplus.clock",  // OnePlus Clock
    )

    for (packageName in knownClockPackages) {
        try {
            packageManager.getPackageInfo(packageName, 0)
            clockApps.add(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            // Package not installed, skip
        }
    }

    return clockApps
}

/**
 * Checks if the given alarm time is a sleep mode/bedtime alarm
 * Uses user-configured bedtime start/end times from settings
 */
private fun isSleepModeAlarmByTime(hour: Int, minute: Int, prefs: Prefs): Boolean {
    val bedtimeStartHour = prefs.bedtimeStartHour
    val bedtimeStartMinute = prefs.bedtimeStartMinute
    val bedtimeEndHour = prefs.bedtimeEndHour
    val bedtimeEndMinute = prefs.bedtimeEndMinute

    val isBedtimeStart =
        hour == bedtimeStartHour && minute == bedtimeStartMinute

    val isBedtimeEnd =
        hour == bedtimeEndHour && minute == bedtimeEndMinute

    if (isBedtimeStart || isBedtimeEnd) {
        AppLogger.d(
            "AlarmFilter",
            "Alarm matches bedtime time: $hour:$minute"
        )
        return true
    }

    return false
}

fun wordOfTheDay(prefs: Prefs): String {
    val dailyWordsArray = loadWordList(prefs)
    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val wordIndex =
        (dayOfYear - 1) % dailyWordsArray.size // Subtracting 1 to align with array indexing
    return dailyWordsArray[wordIndex]
}

fun ismlauncherDefault(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    } else {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultLauncherPackage = resolveInfo?.activityInfo?.packageName
        return context.packageName == defaultLauncherPackage
    }
}

fun reloadLauncher() {
    android.os.Handler(Looper.getMainLooper()).postDelayed({
        exitProcess(0)
    }, 100)
}

fun helpFeedbackButton(context: Context) {
    val uri = "https://github.com/CodeWorksCreativeHub/mLauncher".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

fun themeDownloadButton(context: Context) {
    val uri = "https://mlauncher.5646316.xyz/themes.html#themes".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

fun wordofthedayDownloadButton(context: Context) {
    val uri = "https://mlauncher.5646316.xyz/themes.html#word-of-the-day".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

fun communitySupportButton(context: Context) {
    val uri = "https://discord.com/invite/modmydevice".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

fun checkWhoInstalled(context: Context): String {
    val appName = getLocalizedString(R.string.app_name)
    val descriptionTemplate =
        getLocalizedString(R.string.advanced_settings_share_application_description)
    val descriptionTemplate2 =
        getLocalizedString(R.string.advanced_settings_share_application_description_addon)

    val installerPackageName: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // For API level 30 and above
        val installSourceInfo = context.packageManager.getInstallSourceInfo(context.packageName)
        installSourceInfo.installingPackageName
    } else {
        // For below API level 30
        @Suppress("DEPRECATION")
        context.packageManager.getInstallerPackageName(context.packageName)
    }

    // Handle null installer package name
    val installSource = when (installerPackageName) {
        "com.android.vending" -> "Google Play Store"
        else -> installerPackageName // Default to the installer package name
    }

    val installURL = when (installerPackageName) {
        "com.android.vending" -> "https://play.google.com/store/apps/details?id=app.mlauncher"
        else -> "https://play.google.com/store/apps/details?id=app.mlauncher" // Default to the Google Play Store
    }

    // Format the description with the app name and install source
    return String.format(
        "%s %s",
        String.format(descriptionTemplate, appName),
        String.format(descriptionTemplate2, installSource, installURL)
    )
}


fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val intent: Intent? = context.packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        launcher.startAppDetailsActivity(intent.component, userHandle, null, null)
    } ?: context.showLongToast("Unable to to open app info")
}

fun initActionService(context: Context): ActionService? {
    val actionService = ActionService.instance()
    if (actionService != null) {
        return actionService
    } else {
        context.openAccessibilitySettings()
    }

    return null
}

fun showStatusBar(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insetsController = window.insetsController
        if (insetsController != null) {
            insetsController.show(WindowInsets.Type.statusBars())
        } else {
            // Postpone if insetsController not ready yet
            window.decorView.post {
                window.insetsController?.show(WindowInsets.Type.statusBars())
            }
        }
    } else {
        @Suppress("DEPRECATION", "InlinedApi")
        window.decorView.let { decorView ->
            val flags = decorView.systemUiVisibility and
                    (View.SYSTEM_UI_FLAG_FULLSCREEN.inv()) // clear fullscreen flag
            decorView.systemUiVisibility =
                flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
}

fun hideStatusBar(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insetsController = window.insetsController
        if (insetsController != null) {
            insetsController.hide(WindowInsets.Type.statusBars())
        } else {
            window.decorView.post {
                window.insetsController?.hide(WindowInsets.Type.statusBars())
            }
        }
    } else {
        @Suppress("DEPRECATION")
        (window.decorView.let { decorView ->
            val flags = decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_IMMERSIVE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
            decorView.systemUiVisibility = flags
        })
    }
}

fun showNavigationBar(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insetsController = window.insetsController
        if (insetsController != null) {
            insetsController.show(WindowInsets.Type.navigationBars())
        } else {
            window.decorView.post {
                window.insetsController?.show(WindowInsets.Type.navigationBars())
            }
        }
    } else {
        @Suppress("DEPRECATION", "InlinedApi")
        (window.decorView.let { decorView ->
            val flags = decorView.systemUiVisibility and
                    (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()) // clear hide nav flag
            decorView.systemUiVisibility =
                flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        })
    }
}

fun hideNavigationBar(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insetsController = window.insetsController
        if (insetsController != null) {
            insetsController.hide(WindowInsets.Type.navigationBars())
        } else {
            window.decorView.post {
                window.insetsController?.hide(WindowInsets.Type.navigationBars())
            }
        }
    } else {
        @Suppress("DEPRECATION")
        (window.decorView.let { decorView ->
            val flags = decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_IMMERSIVE or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
            decorView.systemUiVisibility = flags
        })
    }
}

fun dp2px(resources: Resources, dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()
}

fun sp2px(resources: Resources, sp: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        resources.displayMetrics
    )
}


fun loadWordList(prefs: Prefs): List<String> {
    val customWordListString = prefs.wordList
    // If the user has imported their own list, use it
    return if (customWordListString != emptyString()) {
        prefs.wordList.split("||")
    } else {
        getLocalizedStringArray(R.array.word_of_the_day).toList()
    }
}


fun getHexForOpacity(prefs: Prefs): Int {
    // Convert the opacity percentage (0-100) to a reversed decimal (0.0-1.0)
    val setOpacity = ((100 - prefs.opacityNum.coerceIn(
        0,
        100
    )) / 100.0).toFloat() // Reverse the opacity, (0% = full opacity, 100% = transparent)

    val backgroundColor = prefs.backgroundColor // This is already an Int

    // Extract RGB from background color
    val red = android.graphics.Color.red(backgroundColor)
    val green = android.graphics.Color.green(backgroundColor)
    val blue = android.graphics.Color.blue(backgroundColor)

    // Combine opacity with RGB and return final color
    return if (prefs.showBackground) {
        // Apply a minimum opacity constant for the background
        android.graphics.Color.argb((Constants.MIN_OPACITY * 255), red, green, blue)
    } else {
        // Use the reversed opacity as a percentage (0-100%) converted to a float (0.0-1.0)
        android.graphics.Color.argb((setOpacity * 255).toInt(), red, green, blue)
    }
}

fun isSystemInDarkMode(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
}

fun setThemeMode(context: Context, isDark: Boolean, view: View) {
    // Retrieve background color based on the theme
    val backgroundAttr = if (isDark) R.attr.backgroundDark else R.attr.backgroundLight

    val typedValue = TypedValue()
    val theme: Resources.Theme = context.theme
    theme.resolveAttribute(backgroundAttr, typedValue, true)

    // Apply the background color from styles.xml
    view.setBackgroundResource(typedValue.resourceId)
}

fun parseBlacklistXML(context: Context): List<String> {
    // Obtain an XmlPullParser for the blacklist.xml file
    context.resources.getXml(R.xml.blacklist).use { parser ->
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "app") {
                val packageName = parser.getAttributeValue(null, "packageName")
                packageNames.add(packageName)
            }
            parser.next()
        }
    }

    return packageNames
}

fun getTrueSystemFont(): Typeface {
    val possibleSystemFonts = listOf(
        "/system/fonts/Roboto-Regular.ttf",      // Stock Android (Pixel, AOSP)
        "/system/fonts/NotoSans-Regular.ttf",    // Some Android One devices
        "/system/fonts/SamsungOne-Regular.ttf",  // Samsung
        "/system/fonts/MiSans-Regular.ttf",      // Xiaomi MIUI
        "/system/fonts/OPSans-Regular.ttf"       // OnePlus
    )

    for (fontPath in possibleSystemFonts) {
        val fontFile = File(fontPath)
        if (fontFile.exists()) {
            return Typeface.createFromFile(fontFile)
        }
    }

    // Fallback to Roboto as a default if no system font is found
    return Typeface.DEFAULT
}

fun sortMessages(messages: List<Message>): List<Message> {
    return messages.sortedWith(
        compareBy<Message> {
            when (it.priority) {
                "High" -> 0
                "Medium" -> 1
                "Low" -> 2
                else -> 3
            }
        }.thenByDescending { it.timestamp }
    )
}

fun Context.openFirstWeatherApp() {
    val pm = this.packageManager
    AppLogger.d("WeatherAppLauncher", "Starting search for weather apps...")

    // --- Option 1: Known weather apps ---
    val knownWeatherPackages = listOf(
        "com.accuweather.android",             // AccuWeather
        "com.weather.Weather",                 // The Weather Channel
        "com.windyty.android",                 // Windy.com
        "co.windyapp.android",                 // Windy.app
        "com.aws.android",                     // WeatherBug
        "com.wunderground.android.weather",   // Weather Underground
        "com.handmark.expressweather",        // 1Weather
        "net.darksky.darksky",                 // Dark Sky (if still installed)
        "com.luckycatlabs.sunrisesunset",     // (example: some weather apps embed this)
        "de.mdiener.rain.usa",                 // Rain Alarm (example)
        "com.noaa.weather",                    // Hypothetical NOAA-based app
        "com.yahoo.mobile.client.android.weather", // Yahoo Weather
        "org.breezyweather"                    // Breezy Weather
    )

    val installedKnownApps = knownWeatherPackages.filter {
        try {
            pm.getPackageInfo(it, 0)
            AppLogger.d("WeatherAppLauncher", "Found known weather app: $it")
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    if (installedKnownApps.isNotEmpty()) {
        val intent = pm.getLaunchIntentForPackage(installedKnownApps.first())
        if (intent != null) {
            AppLogger.d(
                "WeatherAppLauncher",
                "Launching known weather app: ${installedKnownApps.first()}"
            )
            this.startActivity(intent)
            return
        } else {
            AppLogger.d(
                "WeatherAppLauncher",
                "Launch intent null for: ${installedKnownApps.first()}"
            )
        }
    } else {
        AppLogger.d("WeatherAppLauncher", "No known weather apps installed.")
    }

    // --- Option 2: Try generic weather intent (rarely works) ---
    val genericIntent = Intent(Intent.ACTION_VIEW, "weather://".toUri())
    val resolvedApps = pm.queryIntentActivities(genericIntent, PackageManager.MATCH_DEFAULT_ONLY)
    if (resolvedApps.isNotEmpty()) {
        val packageName = resolvedApps.first().activityInfo.packageName
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            AppLogger.d(
                "WeatherAppLauncher",
                "Launching app via generic weather intent: $packageName"
            )
            this.startActivity(intent)
            return
        } else {
            AppLogger.d(
                "WeatherAppLauncher",
                "Launch intent null for generic weather app: $packageName"
            )
        }
    } else {
        AppLogger.d("WeatherAppLauncher", "No apps found via generic weather intent.")
    }

    // --- Option 3: Scan all installed apps for "weather" in name ---
    val weatherAppsByName = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { app ->
            val name = pm.getApplicationLabel(app).toString().lowercase()
            val containsWeather = name.contains("weather")
            if (containsWeather) AppLogger.d(
                "WeatherAppLauncher",
                "Found app by name: ${app.packageName} ($name)"
            )
            containsWeather
        }

    if (weatherAppsByName.isNotEmpty()) {
        val intent = pm.getLaunchIntentForPackage(weatherAppsByName.first().packageName)
        if (intent != null) {
            AppLogger.d(
                "WeatherAppLauncher",
                "Launching first app found by name: ${weatherAppsByName.first().packageName}"
            )
            this.startActivity(intent)
            return
        } else {
            AppLogger.d(
                "WeatherAppLauncher",
                "Launch intent null for: ${weatherAppsByName.first().packageName}"
            )
        }
    } else {
        AppLogger.d("WeatherAppLauncher", "No apps found by name containing 'weather'.")
    }

    val prefs = Prefs(this)

    // --- Fallback if no weather app is found ---
    val coords = prefs.loadLocation()
    if (coords != null) {
        val (lat, lon) = coords

        // Use prefs.tempUnit to determine unit parameter for weather.com
        val unitParam = when (prefs.tempUnit) {
            Constants.TempUnits.Celsius -> "c"  // Metric
            Constants.TempUnits.Fahrenheit -> "f"  // Fahrenheit
        }

        // Construct the URL with lat/lon and unit
        val url = "https://weather.com/weather/today/l/$lat,$lon?unit=$unitParam"

        // Open in browser
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        this.startActivity(intent)

        AppLogger.d(
            "WeatherAppLauncher",
            "Opened weather.com for coordinates: $lat,$lon with unit: $unitParam"
        )
    } else {
        AppLogger.d("WeatherAppLauncher", "No coordinates found in prefs.")
    }

}


fun formatLongToCalendar(longTimestamp: Long): String {
    // Create a Calendar instance and set it's time to the given timestamp (in milliseconds)
    val calendar = Calendar.getInstance().apply {
        timeInMillis = longTimestamp
    }

    // Format the calendar object to a readable string
    val dateFormat = SimpleDateFormat(
        "MMMM dd, yyyy, HH:mm:ss",
        Locale.getDefault()
    ) // You can modify the format
    return dateFormat.format(calendar.time) // Return the formatted date string
}

fun formatMillisToHMS(millis: Long, showSeconds: Boolean): String {
    val hours = millis / (1000 * 60 * 60)
    val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
    val seconds = (millis % (1000 * 60)) / 1000

    val formattedString = StringBuilder()
    if (hours > 0) {
        formattedString.append("$hours h ")
    }
    if (minutes > 0 || hours > 0) {
        formattedString.append("$minutes m ")
    }
    // Only append seconds if showSeconds is true
    if (showSeconds) {
        formattedString.append("$seconds s")
    }

    return formattedString.toString().trim()
}


fun logActivitiesFromPackage(context: Context, packageName: String) {
    val packageManager = context.packageManager

    try {
        val packageInfo: PackageInfo = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_ACTIVITIES
        )

        val activities: Array<ActivityInfo>? = packageInfo.activities

        activities?.forEach { activityInfo ->
            val componentInfoString =
                "ComponentInfo{${activityInfo.packageName}/${activityInfo.name}}"
            AppLogger.d("ComponentInfoLog", componentInfoString)
        } ?: AppLogger.d("ComponentInfoLog", "No activities found in package $packageName")

    } catch (e: PackageManager.NameNotFoundException) {
        AppLogger.e("ComponentInfoLog", "Package not found: $packageName", e)
    }
}

fun getDeviceInfo(context: Context): String {
    return try {
        val packageManager = context.packageManager
        val installSource = getInstallSource(packageManager, context.packageName)

        """
            Manufacturer: ${Build.MANUFACTURER}
            Model: ${Build.MODEL}
            Brand: ${Build.BRAND}
            Device: ${Build.DEVICE}
            Product: ${Build.PRODUCT}
            Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            ABI: ${Build.SUPPORTED_ABIS.joinToString()}
            App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            Locale: ${Locale.getDefault()}
            Timezone: ${TimeZone.getDefault().id}
            Installed From: $installSource
            """.trimIndent()
    } catch (e: Exception) {
        "Device Info Unavailable: ${e.message}"
    }
}

// 1. Define the data class
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abi: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val locale: String,
    val timezone: String,
    val installedFrom: String
)

// 2. Function to get DeviceInfo object
fun getDeviceInfoObject(context: Context): DeviceInfo {
    val packageManager = context.packageManager
    val installSource = getInstallSource(packageManager, context.packageName)

    return DeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        brand = Build.BRAND,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        abi = Build.SUPPORTED_ABIS.joinToString(),
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        locale = Locale.getDefault().toString(),
        timezone = TimeZone.getDefault().id,
        installedFrom = installSource
    )
}

// 3. Function to convert DeviceInfo to JSON using Moshi
fun getDeviceInfoJson(context: Context): String {
    return try {
        val deviceInfo = getDeviceInfoObject(context)

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val jsonAdapter = moshi.adapter(DeviceInfo::class.java).indent("    ") // pretty print

        jsonAdapter.toJson(deviceInfo)
    } catch (e: Exception) {
        """{"error": "Device Info Unavailable: ${e.message}"}"""
    }
}

fun getInstallSource(
    packageManager: PackageManager,
    packageName: String
): String {
    return try {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager
                .getInstallSourceInfo(packageName)
                .installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }

        when (installer) {
            "com.android.vending" ->
                "Google Play Store"

            "com.android.shell" ->
                "ADB / Android Studio"

            "org.fdroid.fdroid" ->
                "F-Droid"

            "dev.imranr.obtainium" ->
                "Obtainium"

            "com.amazon.venezia" ->
                "Amazon Appstore"

            "com.sec.android.app.samsungapps" ->
                "Samsung Galaxy Store"

            "com.huawei.appmarket" ->
                "Huawei AppGallery"

            "com.xiaomi.market" ->
                "Xiaomi GetApps"

            "com.oppo.market" ->
                "OPPO App Market"

            "com.vivo.appstore" ->
                "Vivo App Store"

            else ->
                installer ?: "Unknown"
        }

    } catch (_: Exception) {
        "Unknown"
    }
}

fun getSystemIcons(
    context: Context,
    prefs: Prefs,
    target: IconCacheTarget,
    nonNullDrawable: Drawable
): Drawable? {
    return when (target) {
        IconCacheTarget.APP_LIST -> getAppListIcons(context, prefs, nonNullDrawable)
        IconCacheTarget.HOME -> getHomeIcons(context, prefs, nonNullDrawable)
    }
}

fun updateAllWidgets(context: Context) {
    updateHomeWidget(context)
    updateWordWidget(context)
    updateFabWidget(context)
}

fun updateHomeWidget(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val componentName = ComponentName(context, HomeAppsWidgetProvider::class.java)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

    val intent = Intent(context, HomeAppsWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
    }
    context.sendBroadcast(intent)
}

fun updateWordWidget(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val componentName = ComponentName(context, WordOfTheDayWidget::class.java)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

    val intent = Intent(context, WordOfTheDayWidget::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
    }
    context.sendBroadcast(intent)
}

fun updateFabWidget(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val componentName = ComponentName(
        context,
        HomeAppsWidgetProvider::class.java
    ) // Replace with your FAB widget class
    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

    val intent = Intent(context, HomeAppsWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
    }

    context.sendBroadcast(intent)
}


private fun getAppListIcons(context: Context, prefs: Prefs, nonNullDrawable: Drawable): Drawable? {
    return when (prefs.iconPackAppList) {
        Constants.IconPacks.CloudDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.cloud_dots_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        Constants.IconPacks.LauncherDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.launcher_dot_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        Constants.IconPacks.NiagaraDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.niagara_dot_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        Constants.IconPacks.SpinnerDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.spinner_dots_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        else -> {
            null
        }
    }
}

private fun getHomeIcons(context: Context, prefs: Prefs, nonNullDrawable: Drawable): Drawable? {
    return when (prefs.iconPackHome) {
        Constants.IconPacks.CloudDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.cloud_dots_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        Constants.IconPacks.LauncherDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.launcher_dot_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        Constants.IconPacks.NiagaraDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.niagara_dot_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        Constants.IconPacks.SpinnerDots -> {
            val newIcon = ContextCompat.getDrawable(
                context,
                R.drawable.spinner_dots_icon
            )!!
            val dominantColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
            ColorIconsExtensions.recolorDrawable(newIcon, dominantColor)
        }

        else -> {
            null
        }
    }
}

fun setTopPadding(view: View, isSettings: Boolean = false) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->

        val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val displayCutout = insets.displayCutout

        // Start with the top inset from status bar / notch area
        val topInset = systemBarsInsets.top

        // If there's a cutout (notch), its safeInsetTop should already be included in systemBarsInsets.top,
        // but to be safe, you could add it explicitly if needed
        val cutoutTopInset = displayCutout?.safeInsetTop ?: 0

        // Combine topInset and cutout if you want to make sure notch space is accounted for:
        val totalTopInset = maxOf(topInset, cutoutTopInset)

        // Apply your fallback & settings logic
        val finalTopPadding = when {
            totalTopInset == 0 -> (24 * v.resources.displayMetrics.density).toInt()
            isSettings -> totalTopInset
            else -> totalTopInset / 2
        }

        v.updatePadding(top = finalTopPadding)

        insets
    }

    ViewCompat.requestApplyInsets(view)
}

class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val drawable = drawable
        canvas.withSave {
            val transY = top + ((bottom - top) - drawable.bounds.height()) / 2
            translate(x, transY.toFloat())
            drawable.draw(this)
        }
    }
}