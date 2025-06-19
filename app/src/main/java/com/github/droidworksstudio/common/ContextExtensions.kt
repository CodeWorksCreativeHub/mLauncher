package com.github.droidworksstudio.common

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log.d
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.github.droidworksstudio.mlauncher.Mlauncher
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.helper.emptyString
import com.github.droidworksstudio.mlauncher.services.ActionService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Context.inflate(resource: Int, root: ViewGroup? = null, attachToRoot: Boolean = false): View {
    return LayoutInflater.from(this).inflate(resource, root, attachToRoot)
}

fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.showShortToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.hasSoftKeyboard(): Boolean {
    val config = resources.configuration
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    // True if the device does not have physical keys AND at least one soft input method is installed
    return config.keyboard == Configuration.KEYBOARD_NOKEYS && imm.inputMethodList.isNotEmpty()
}

fun Context.openSearch(query: String? = null) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, query ?: emptyString())
    startActivity(intent)
}


fun Context.openUrl(url: String) {
    if (url.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = url.toUri()
    startActivity(intent)
}

fun isGestureNavigationEnabled(context: Context): Boolean {
    return try {
        val navigationMode = Settings.Secure.getInt(
            context.contentResolver,
            "navigation_mode"  // This is the key to check the current navigation mode
        )
        // 2 corresponds to gesture navigation mode
        navigationMode == 2
    } catch (_: Settings.SettingNotFoundException) {
        // Handle the case where the setting isn't found, assume not enabled
        false
    }
}

fun Context.launchCalendar() {
    try {
        val cal: Calendar = Calendar.getInstance()
        cal.time = Date()
        val time = cal.time.time
        val builder: Uri.Builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        builder.appendPath(time.toString())
        this.startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            this.startActivity(intent)
        } catch (e: Exception) {
            d("openCalendar", e.toString())
        }
    }
    CrashHandler.logUserAction("Calendar App Launched")
}

fun Context.openDialerApp() {
    try {
        val sendIntent = Intent(Intent.ACTION_DIAL)
        this.startActivity(sendIntent)
    } catch (e: java.lang.Exception) {
        d("openDialerApp", e.toString())
    }
    CrashHandler.logUserAction("Dialer App Launched")
}

fun Context.openTextMessagesApp() {
    try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MESSAGING)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        this.startActivity(intent)
    } catch (e: Exception) {
        d("openTextMessagesApp", e.toString())
    }
    CrashHandler.logUserAction("Text Messages App Launched")
}

fun Context.openAlarmApp() {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        this.startActivity(intent)
    } catch (e: java.lang.Exception) {
        d("openAlarmApp", e.toString())
    }
    CrashHandler.logUserAction("Alarm App Launched")
}

fun Context.openCameraApp() {
    try {
        val sendIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        this.startActivity(sendIntent)
    } catch (e: java.lang.Exception) {
        d("openCameraApp", e.toString())
    }
    CrashHandler.logUserAction("Camera App Launched")
}

fun Context.openPhotosApp() {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        this.startActivity(intent)
    } catch (e: Exception) {
        d("openPhotosApp", e.toString())
    }
    CrashHandler.logUserAction("Photos App Launched")
}

fun Context.openDeviceSettings() {
    try {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        this.startActivity(intent)
    } catch (e: Exception) {
        d("openDeviceSettings", e.toString())
    }
    CrashHandler.logUserAction("Device Settings Opened")
}

fun Context.openWebBrowser() {
    try {
        val defaultBrowserPackage = getDefaultBrowserPackageName()
        if (defaultBrowserPackage != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(defaultBrowserPackage)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(launchIntent)
            } else {
                d("openDefaultBrowserApp", "No launch intent for package $defaultBrowserPackage")
            }
        } else {
            d("openDefaultBrowserApp", "No default browser package found")
        }
    } catch (e: Exception) {
        d("openDefaultBrowserApp", e.toString())
    }
    CrashHandler.logUserAction("Default Browser App Launched")
}


fun Context.getDefaultBrowserPackageName(): String? {
    val intent = Intent(Intent.ACTION_VIEW, "https://".toUri())
    val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName
}


fun Context.openBatteryManager() {
    try {
        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        this.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        showLongToast("Battery manager settings are not available on this device.")
    }
    CrashHandler.logUserAction("Battery Manager Launched")
}

fun Context.openDigitalWellbeing() {
    // Known Digital Wellbeing packages and their main activity
    val wellbeingMap = mapOf(
        "com.google.android.apps.wellbeing" to "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity",
        "com.samsung.android.forest" to "com.samsung.android.forest.settings.MainActivity",
        "com.samsung.android.wellbeing" to "com.samsung.android.wellbeing.SamsungWellbeingSettingsActivity"
    )

    val installedPackages = packageManager.getInstalledPackages(0).map { it.packageName }

    val wellbeingEntry = wellbeingMap.entries.firstOrNull { it.key in installedPackages }

    if (wellbeingEntry != null) {
        val (pkg, cls) = wellbeingEntry
        val intent = Intent().apply {
            component = ComponentName(pkg, cls)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(intent)
            CrashHandler.logUserAction("Digital Wellbeing Launched")
        } catch (_: ActivityNotFoundException) {
            showLongToast("Unable to launch Digital Wellbeing.")
        }
    } else {
        showLongToast("Digital Wellbeing is not available on this device.")
    }
}

fun Context.searchOnPlayStore(query: String? = null): Boolean {
    return try {
        val playStoreIntent = Intent(Intent.ACTION_VIEW)
        playStoreIntent.data = "${Constants.APP_GOOGLE_PLAY_STORE}=$query".toUri()

        // Check if the Play Store app is installed
        if (playStoreIntent.resolveActivity(packageManager) != null) {
            startActivity(playStoreIntent)
        } else {
            // If Play Store app is not installed, open Play Store website in browser
            playStoreIntent.data = "${Constants.URL_GOOGLE_PLAY_STORE}=$query".toUri()
            startActivity(playStoreIntent)
        }
        CrashHandler.logUserAction("Play Store Launched")
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.searchCustomSearchEngine(searchQuery: String? = null, prefs: Prefs): Boolean {
    val searchUrl = when (prefs.searchEngines) {
        Constants.SearchEngines.Google -> {
            CrashHandler.logUserAction("Google Search")
            Constants.URL_GOOGLE_SEARCH
        }

        Constants.SearchEngines.Yahoo -> {
            CrashHandler.logUserAction("Yahoo Search")
            Constants.URL_YAHOO_SEARCH
        }

        Constants.SearchEngines.DuckDuckGo -> {
            CrashHandler.logUserAction("DuckDuckGo Search")
            Constants.URL_DUCK_SEARCH
        }

        Constants.SearchEngines.Bing -> {
            CrashHandler.logUserAction("Bing Search")
            Constants.URL_BING_SEARCH
        }

        Constants.SearchEngines.Brave -> {
            CrashHandler.logUserAction("Brave Search")
            Constants.URL_BRAVE_SEARCH
        }

        Constants.SearchEngines.SwissCow -> {
            CrashHandler.logUserAction("SwissCow Search")
            Constants.URL_SWISSCOW_SEARCH
        }
    }
    val encodedQuery = Uri.encode(searchQuery)
    val fullUrl = "$searchUrl$encodedQuery"
    d("fullUrl", fullUrl)
    openUrl(fullUrl)
    return true
}

fun Context.isSystemApp(packageName: String): Boolean {
    if (packageName.isBlank()) return true
    return try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.isBiometricEnabled(): Boolean {
    val biometricManager = BiometricManager.from(this)

    return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
        BiometricManager.BIOMETRIC_SUCCESS -> true
        else -> false
    }
}

fun getLocalizedString(@StringRes stringResId: Int, vararg args: Any): String {
    // Get the context from Mlauncher. It's guaranteed to never be null
    val context = Mlauncher.getContext()

    val localPrefs = Prefs(context)
    val locale = Locale.forLanguageTag(localPrefs.appLanguage.locale().toString())
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    val localizedContext = context.createConfigurationContext(config)

    // Return the localized string with or without arguments
    return if (args.isEmpty()) {
        localizedContext.getString(stringResId)  // No arguments, use only the string resource
    } else {
        localizedContext.getString(stringResId, *args)  // Pass arguments to getString()
    }
}

fun getLocalizedStringArray(@ArrayRes arrayResId: Int): Array<String> {
    // Get the context from Mlauncher. It's guaranteed to never be null
    val context = Mlauncher.getContext()

    val localPrefs = Prefs(context)
    val locale = Locale.forLanguageTag(localPrefs.appLanguage.locale().toString())
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    val localizedContext = context.createConfigurationContext(config)
    // Return the localized string array
    return localizedContext.resources.getStringArray(arrayResId)
}

fun Context.openAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val cs = ComponentName(this.packageName, ActionService::class.java.name).flattenToString()
    val bundle = Bundle()
    bundle.putString(":settings:fragment_args_key", cs)
    intent.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(":settings:fragment_args_key", cs)
        putExtra(":settings:show_fragment_args", bundle)
    }
    this.startActivity(intent)
    CrashHandler.logUserAction("Accessibility Settings Opened")
}

fun Context.requestUsagePermission() {
    try {
        val context: Context = this
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()  // Open settings for YOUR app only
        }
        context.startActivity(intent)
        CrashHandler.logUserAction("Usage Permission Settings Opened")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.requestLocationPermission(requestCode: Int) {
    if (this is Activity) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            requestCode
        )
        CrashHandler.logUserAction("Location Permission Requested")
    } else {
        AppLogger.e("Permission", "Context is not an Activity. Cannot request permissions.")
    }
}


fun Context.getCurrentTimestamp(prefs: Prefs): String {
    val timezone = prefs.appLanguage.timezone()
    val is24HourFormat = DateFormat.is24HourFormat(this)
    val best12 = DateFormat.getBestDateTimePattern(
        timezone,
        if (prefs.showClockFormat) "hhmma" else "hhmm"
    ).let {
        if (!prefs.showClockFormat) it.removeSuffix(" a") else it
    }
    val best24 = DateFormat.getBestDateTimePattern(timezone, "HHmm")
    val timePattern = if (is24HourFormat) best24 else best12
    val datePattern = DateFormat.getBestDateTimePattern(timezone, "eeeddMMM")

    return SimpleDateFormat("$datePattern - $timePattern", Locale.getDefault()).format(Date())
}

fun Context.getRamInfo(): String {
    val activityManager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    val total = memoryInfo.totalMem / (1024 * 1024 * 1024f)
    val avail = memoryInfo.availMem / (1024 * 1024 * 1024f)
    val used = total - avail
    val threshold = memoryInfo.threshold / (1024 * 1024 * 1024f)
    val uptime = SystemClock.elapsedRealtime()

    val uptimeHours = uptime / (1000 * 60 * 60)
    val uptimeMinutes = (uptime / (1000 * 60)) % 60
    val uptimeSeconds = (uptime / 1000) % 60

    return """
        Total: %.3f GB
        Used: %.3f GB
        Available: %.3f GB
        Threshold: %.3f GB
        System Uptime: %02d:%02d:%02d
    """.trimIndent().format(total, used, avail, threshold, uptimeHours, uptimeMinutes, uptimeSeconds)
}

fun Context.getCpuBatteryInfo(): String {
    val batteryStatus = this.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
    val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

    val freqMin = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq").readText().trim().toIntOrNull()
    val freqMax = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toIntOrNull()

    return """
        Cpu Temp: ${temp}°C
        Usage: $level%
        Freq: ${freqMin?.div(1000f)} - ${freqMax?.div(1000f)} GHz
        Battery Temp: ${temp}°C
        Voltage: ${voltage / 1000f} V
    """.trimIndent()
}

fun Context.getStorageInfo(): String {
    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.blockCountLong * stat.blockSizeLong
    val avail = stat.availableBlocksLong * stat.blockSizeLong
    val used = total - avail

    return """
        Total: %.3f GB
        Used: %.3f GB
        Free: %.3f GB
        Root: / (internal)
    """.trimIndent().format(
        total / 1e9,
        used / 1e9,
        avail / 1e9
    )
}

@Suppress("DEPRECATION")
fun Context.getSdCardInfo(): String {
    val extStorage = ContextCompat.getExternalFilesDirs(this, null).lastOrNull()
    if (extStorage == null || Environment.isExternalStorageEmulated(extStorage)) return "No SD card"

    val stat = StatFs(extStorage.path)
    val total = stat.blockCountLong * stat.blockSizeLong
    val avail = stat.availableBlocksLong * stat.blockSizeLong
    val used = total - avail

    return """
        Total: %.3f GB
        Used: %.3f GB
        Free: %.3f GB
        Path: ${extStorage.path}
    """.trimIndent().format(
        total / 1e9,
        used / 1e9,
        avail / 1e9
    )
}

