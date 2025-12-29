package com.github.codeworkscreativehub.mlauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserHandle
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Constants.Gravity
import com.github.codeworkscreativehub.mlauncher.helper.emptyString
import com.github.codeworkscreativehub.mlauncher.helper.getUserHandleFromString
import com.github.codeworkscreativehub.mlauncher.helper.isSystemInDarkMode
import com.github.codeworkscreativehub.mlauncher.helper.receivers.LocationResult
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType

class Prefs(val context: Context) {
    // Build Moshi instance once (ideally a singleton)
    val moshi: Moshi = Moshi.Builder().build()

    // Define the type for List<Message>
    val messageListType: ParameterizedType = Types.newParameterizedType(List::class.java, Message::class.java)
    val messageAdapter: JsonAdapter<List<Message>> = moshi.adapter(messageListType)
    val messageWrongListType: ParameterizedType = Types.newParameterizedType(List::class.java, MessageWrong::class.java)
    val messageWrongAdapter: JsonAdapter<List<MessageWrong>> = moshi.adapter(messageWrongListType)

    internal val prefsNormal: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    internal val prefsOnboarding: SharedPreferences = context.getSharedPreferences(PREFS_ONBOARDING_FILENAME, Context.MODE_PRIVATE)
    internal val pinnedAppsKey = PINNED_APPS

    fun saveToString(): String {
        val allPreferences = HashMap<String, Any?>(prefsNormal.all)

        val moshi = Moshi.Builder().build()

        val type = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )

        val adapter = moshi.adapter<Map<String, Any?>>(type).indent("  ") // Pretty-print

        return adapter.toJson(allPreferences)
    }

    fun loadFromString(json: String) {
        val moshi = Moshi.Builder().build()

        val type = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )

        val adapter = moshi.adapter<Map<String, Any?>>(type)

        val all = adapter.fromJson(json) ?: emptyMap()

        prefsNormal.edit {
            for ((key, value) in all) {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Double -> {
                        if (value % 1 == 0.0) {
                            putInt(key, value.toInt())
                        } else {
                            putFloat(key, value.toFloat())
                        }
                    }

                    is List<*> -> {
                        // Moshi deserializes sets as lists
                        val stringSet = value.filterIsInstance<String>().toSet()
                        putStringSet(key, stringSet)
                    }

                    else -> {
                        AppLogger.d("backup error", "Unsupported type for key '$key': $value")
                    }
                }
            }
        }
    }

    fun saveToTheme(colorNames: List<String>): String {
        val allPrefs = prefsNormal.all
        val filteredPrefs = mutableMapOf<String, String>()

        for (colorName in colorNames) {
            if (allPrefs.containsKey(colorName)) {
                val colorInt = allPrefs[colorName] as? Int
                if (colorInt != null) {
                    val hexColor = String.format("#%08X", colorInt)
                    filteredPrefs[colorName] = hexColor
                }
            }
        }

        val moshi = Moshi.Builder().build()

        val type = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            String::class.java
        )
        val adapter = moshi.adapter<Map<String, String>>(type).indent("  ") // pretty-print

        return adapter.toJson(filteredPrefs)
    }

    fun loadFromTheme(json: String) {
        val moshi = Moshi.Builder().build()

        val type = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )

        val adapter = moshi.adapter<Map<String, Any?>>(type)

        val all = try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            AppLogger.e("Theme Import", "Failed to parse JSON", e)
            context.showLongToast("Failed to parse theme JSON.")
            return
        } ?: emptyMap()

        prefsNormal.edit {
            for ((key, value) in all) {
                try {
                    when (value) {
                        is String -> {
                            if (value.matches(Regex("^#([A-Fa-f0-9]{8})$"))) {
                                try {
                                    putInt(key, value.toColorInt())
                                } catch (e: IllegalArgumentException) {
                                    context.showLongToast("Invalid color format for key: $key, value: $value")
                                    AppLogger.e("Theme Import", "Invalid color format for key: $key, value: $value", e)
                                    continue
                                }
                            } else {
                                context.showLongToast("Unsupported HEX format for key: $key, value: $value")
                                AppLogger.e("Theme Import", "Unsupported HEX format for key: $key, value: $value")
                            }
                        }

                        null -> {
                            context.showLongToast("Null value found for key: $key")
                            AppLogger.e("Theme Import", "Null value found for key: $key")
                            continue
                        }

                        else -> {
                            context.showLongToast("Unsupported value type for key: $key, value: $value")
                            AppLogger.e("Theme Import", "Unsupported value type for key: $key, value: $value")
                            continue
                        }
                    }
                } catch (e: Exception) {
                    context.showLongToast("Error processing key: $key, value: $value")
                    AppLogger.e("Theme Import", "Error processing key: $key, value: $value", e)
                }
            }
        }
    }

    var appVersion: Int
        get() = prefsNormal.getInt(APP_VERSION, -1)
        set(value) = prefsNormal.edit { putInt(APP_VERSION, value) }

    var firstOpen: Boolean
        get() = prefsNormal.getBoolean(FIRST_OPEN, true)
        set(value) = prefsNormal.edit { putBoolean(FIRST_OPEN, value) }

    var firstSettingsOpen: Boolean
        get() = prefsNormal.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefsNormal.edit { putBoolean(FIRST_SETTINGS_OPEN, value) }

    var autoOpenApp: Boolean
        get() = getSetting(AUTO_OPEN_APP, false)
        set(value) = prefsNormal.edit { putBoolean(AUTO_OPEN_APP, value) }

    var forceWallpaper: Boolean
        get() = getSetting(FORCE_COLORED_WALLPAPER, false)
        set(value) = prefsNormal.edit { putBoolean(FORCE_COLORED_WALLPAPER, value) }

    var openAppOnEnter: Boolean
        get() = getSetting(OPEN_APP_ON_ENTER, false)
        set(value) = prefsNormal.edit { putBoolean(OPEN_APP_ON_ENTER, value) }

    var homePager: Boolean
        get() = getSetting(HOME_PAGES_PAGER, false)
        set(value) = prefsNormal.edit { putBoolean(HOME_PAGES_PAGER, value) }

    var recentAppsDisplayed: Boolean
        get() = getSetting(RECENT_APPS_DISPLAYED, false)
        set(value) = prefsNormal.edit { putBoolean(RECENT_APPS_DISPLAYED, value) }

    var iconRainbowColors: Boolean
        get() = getSetting(ICON_RAINBOW_COLORS, false)
        set(value) = prefsNormal.edit { putBoolean(ICON_RAINBOW_COLORS, value) }

    var recentCounter: Int
        get() = getSetting(RECENT_COUNTER, 10)
        set(value) = prefsNormal.edit { putInt(RECENT_COUNTER, value) }

    var enableFilterStrength: Boolean
        get() = getSetting(ENABLE_FILTER_STRENGTH, true)
        set(value) = prefsNormal.edit { putBoolean(ENABLE_FILTER_STRENGTH, value) }

    var filterStrength: Int
        get() = getSetting(FILTER_STRENGTH, 25)
        set(value) = prefsNormal.edit { putInt(FILTER_STRENGTH, value) }

    var shortSwipeThreshold: Float
        get() = getSetting(SHORT_SWIPE_THRESHOLD, 0.15f)
        set(value) = prefsNormal.edit { putFloat(SHORT_SWIPE_THRESHOLD, value) }

    var longSwipeThreshold: Float
        get() = getSetting(LONG_SWIPE_THRESHOLD, 0.4f)
        set(value) = prefsNormal.edit { putFloat(LONG_SWIPE_THRESHOLD, value) }

    var searchFromStart: Boolean
        get() = getSetting(SEARCH_START, false)
        set(value) = prefsNormal.edit { putBoolean(SEARCH_START, value) }

    var autoShowKeyboard: Boolean
        get() = getSetting(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefsNormal.edit { putBoolean(AUTO_SHOW_KEYBOARD, value) }

    var homeAppsNum: Int
        get() = getSetting(HOME_APPS_NUM, 4)
        set(value) = prefsNormal.edit { putInt(HOME_APPS_NUM, value) }

    var homePagesNum: Int
        get() = getSetting(HOME_PAGES_NUM, 1)
        set(value) = prefsNormal.edit { putInt(HOME_PAGES_NUM, value) }

    var backgroundColor: Int
        get() = getSetting(BACKGROUND_COLOR, getColor(context, getColorInt("bg")))
        set(value) = prefsNormal.edit { putInt(BACKGROUND_COLOR, value) }

    var appColor: Int
        get() = getSetting(APP_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefsNormal.edit { putInt(APP_COLOR, value) }

    var dateColor: Int
        get() = getSetting(DATE_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefsNormal.edit { putInt(DATE_COLOR, value) }

    var clockColor: Int
        get() = getSetting(CLOCK_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefsNormal.edit { putInt(CLOCK_COLOR, value) }

    var batteryColor: Int
        get() = getSetting(BATTERY_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefsNormal.edit { putInt(BATTERY_COLOR, value) }

    var dailyWordColor: Int
        get() = getSetting(DAILY_WORD_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefsNormal.edit { putInt(DAILY_WORD_COLOR, value) }

    var shortcutIconsColor: Int
        get() = getSetting(SHORTCUT_ICONS_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefsNormal.edit { putInt(SHORTCUT_ICONS_COLOR, value) }

    var alarmClockColor: Int
        get() = getSetting(ALARM_CLOCK_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefsNormal.edit { putInt(ALARM_CLOCK_COLOR, value) }

    var notesBackgroundColor: Int
        get() = getSetting(NOTES_BACKGROUND_COLOR, getColor(context, getColorInt("bg_notes")))
        set(value) = prefsNormal.edit { putInt(NOTES_BACKGROUND_COLOR, value) }

    var bubbleBackgroundColor: Int
        get() = getSetting(BUBBLE_BACKGROUND_COLOR, getColor(context, getColorInt("bg_bubble")))
        set(value) = prefsNormal.edit { putInt(BUBBLE_BACKGROUND_COLOR, value) }

    var bubbleMessageTextColor: Int
        get() = getSetting(
            BUBBLE_MESSAGE_COLOR,
            getColor(context, getColorInt("bg_bubble_message"))
        )
        set(value) = prefsNormal.edit { putInt(BUBBLE_MESSAGE_COLOR, value) }

    var bubbleTimeDateColor: Int
        get() = getSetting(
            BUBBLE_TIMEDATE_COLOR,
            getColor(context, getColorInt("bg_bubble_time_date"))
        )
        set(value) = prefsNormal.edit { putInt(BUBBLE_TIMEDATE_COLOR, value) }

    var bubbleCategoryColor: Int
        get() = getSetting(
            BUBBLE_CATEGORY_COLOR,
            getColor(context, getColorInt("bg_bubble_category"))
        )
        set(value) = prefsNormal.edit { putInt(BUBBLE_CATEGORY_COLOR, value) }

    var inputMessageColor: Int
        get() = getSetting(INPUT_MESSAGE_COLOR, getColor(context, getColorInt("input_text")))
        set(value) = prefsNormal.edit { putInt(INPUT_MESSAGE_COLOR, value) }

    var inputMessageHintColor: Int
        get() = getSetting(
            INPUT_MESSAGEHINT_COLOR,
            getColor(context, getColorInt("input_text_hint"))
        )
        set(value) = prefsNormal.edit { putInt(INPUT_MESSAGEHINT_COLOR, value) }

    var opacityNum: Int
        get() = getSetting(APP_OPACITY, 15)
        set(value) = prefsNormal.edit { putInt(APP_OPACITY, value) }

    var appUsageStats: Boolean
        get() = getSetting(APP_USAGE_STATS, false)
        set(value) = prefsNormal.edit { putBoolean(APP_USAGE_STATS, value) }

    var homeAlignment: Gravity
        get() {
            return getEnumSetting(HOME_ALIGNMENT, Gravity.Left)
        }
        set(value) = prefsNormal.edit { putString(HOME_ALIGNMENT, value.toString()) }

    var homeAlignmentBottom: Boolean
        get() = getSetting(HOME_ALIGNMENT_BOTTOM, true)
        set(value) = prefsNormal.edit { putBoolean(HOME_ALIGNMENT_BOTTOM, value) }

    var extendHomeAppsArea: Boolean
        get() = getSetting(HOME_CLICK_AREA, false)
        set(value) = prefsNormal.edit { putBoolean(HOME_CLICK_AREA, value) }

    var clockAlignment: Gravity
        get() {
            return getEnumSetting(CLOCK_ALIGNMENT, Gravity.Left)
        }
        set(value) = prefsNormal.edit { putString(CLOCK_ALIGNMENT, value.toString()) }

    var dateAlignment: Gravity
        get() {
            return getEnumSetting(DATE_ALIGNMENT, Gravity.Left)
        }
        set(value) = prefsNormal.edit { putString(DATE_ALIGNMENT, value.toString()) }

    var alarmAlignment: Gravity
        get() {
            return getEnumSetting(ALARM_ALIGNMENT, Gravity.Left)
        }
        set(value) = prefsNormal.edit { putString(ALARM_ALIGNMENT, value.toString()) }

    var dailyWordAlignment: Gravity
        get() {
            return getEnumSetting(DAILY_WORD_ALIGNMENT, Gravity.Left)
        }
        set(value) = prefsNormal.edit { putString(DAILY_WORD_ALIGNMENT, value.toString()) }

    var drawerAlignment: Gravity
        get() {
            return getEnumSetting(DRAWER_ALIGNMENT, Gravity.Right)
        }
        set(value) = prefsNormal.edit { putString(DRAWER_ALIGNMENT, value.name) }

    var showBackground: Boolean
        get() = getSetting(SHOW_BACKGROUND, false)
        set(value) = prefsNormal.edit { putBoolean(SHOW_BACKGROUND, value) }

    var showStatusBar: Boolean
        get() = getSetting(STATUS_BAR, true)
        set(value) = prefsNormal.edit { putBoolean(STATUS_BAR, value) }

    var showNavigationBar: Boolean
        get() = getSetting(NAVIGATION_BAR, true)
        set(value) = prefsNormal.edit { putBoolean(NAVIGATION_BAR, value) }

    var showDate: Boolean
        get() = getSetting(SHOW_DATE, true)
        set(value) = prefsNormal.edit { putBoolean(SHOW_DATE, value) }

    var showClock: Boolean
        get() = getSetting(SHOW_CLOCK, true)
        set(value) = prefsNormal.edit { putBoolean(SHOW_CLOCK, value) }

    var showClockFormat: Boolean
        get() = getSetting(SHOW_CLOCK_FORMAT, true)
        set(value) = prefsNormal.edit { putBoolean(SHOW_CLOCK_FORMAT, value) }

    var showAlarm: Boolean
        get() = getSetting(SHOW_ALARM, false)
        set(value) = prefsNormal.edit { putBoolean(SHOW_ALARM, value) }

    var showDailyWord: Boolean
        get() = getSetting(SHOW_DAILY_WORD, false)
        set(value) = prefsNormal.edit { putBoolean(SHOW_DAILY_WORD, value) }

    var showFloating: Boolean
        get() = getSetting(SHOW_FLOATING, true)
        set(value) = prefsNormal.edit { putBoolean(SHOW_FLOATING, value) }

    var showBattery: Boolean
        get() = getSetting(SHOW_BATTERY, true)
        set(value) = prefsNormal.edit { putBoolean(SHOW_BATTERY, value) }

    var showWeather: Boolean
        get() = getSetting(SHOW_WEATHER, true)
        set(value) = prefsNormal.edit { putBoolean(SHOW_WEATHER, value) }

    var gpsLocation: Boolean
        get() = getSetting(GPS_LOCATION, true)
        set(value) = prefsNormal.edit { putBoolean(GPS_LOCATION, value) }

    var showBatteryIcon: Boolean
        get() = getSetting(SHOW_BATTERY_ICON, true)
        set(value) = prefsNormal.edit { putBoolean(SHOW_BATTERY_ICON, value) }

    var lockOrientation: Boolean
        get() = getSetting(LOCK_ORIENTATION, false)
        set(value) = prefsNormal.edit { putBoolean(LOCK_ORIENTATION, value) }

    var lockOrientationPortrait: Boolean
        get() = getSetting(LOCK_ORIENTATION_PORTRAIT, true)
        set(value) = prefsNormal.edit { putBoolean(LOCK_ORIENTATION_PORTRAIT, value) }

    var hapticFeedback: Boolean
        get() = getSetting(HAPTIC_FEEDBACK, true)
        set(value) = prefsNormal.edit { putBoolean(HAPTIC_FEEDBACK, value) }

    var showAZSidebar: Boolean
        get() = getSetting(SHOW_AZSIDEBAR, false)
        set(value) = prefsNormal.edit { putBoolean(SHOW_AZSIDEBAR, value) }

    var iconPackHome: Constants.IconPacks
        get() {
            return getEnumSetting(ICON_PACK_HOME, Constants.IconPacks.Disabled)
        }
        set(value) = prefsNormal.edit { putString(ICON_PACK_HOME, value.name) }

    var customIconPackHome: String
        get() = prefsNormal.getString(CUSTOM_ICON_PACK_HOME, emptyString()).toString()
        set(value) = prefsNormal.edit { putString(CUSTOM_ICON_PACK_HOME, value) }

    var iconPackAppList: Constants.IconPacks
        get() {
            return getEnumSetting(ICON_PACK_APP_LIST, Constants.IconPacks.Disabled)
        }
        set(value) = prefsNormal.edit { putString(ICON_PACK_APP_LIST, value.name) }

    var customIconPackAppList: String
        get() = prefsNormal.getString(CUSTOM_ICON_PACK_APP_LIST, emptyString()).toString()
        set(value) = prefsNormal.edit { putString(CUSTOM_ICON_PACK_APP_LIST, value) }

    var wordList: String
        get() = prefsNormal.getString(WORD_LIST, emptyString()).toString()
        set(value) = prefsNormal.edit { putString(WORD_LIST, value) }

    var homeLocked: Boolean
        get() = getSetting(HOME_LOCKED, false)
        set(value) = prefsNormal.edit { putBoolean(HOME_LOCKED, value) }

    var settingsLocked: Boolean
        get() = getSetting(SETTINGS_LOCKED, false)
        set(value) = prefsNormal.edit { putBoolean(SETTINGS_LOCKED, value) }

    var hideSearchView: Boolean
        get() = getSetting(HIDE_SEARCH_VIEW, false)
        set(value) = prefsNormal.edit { putBoolean(HIDE_SEARCH_VIEW, value) }

    var autoExpandNotes: Boolean
        get() = getSetting(AUTO_EXPAND_NOTES, false)
        set(value) = prefsNormal.edit { putBoolean(AUTO_EXPAND_NOTES, value) }

    var clickToEditDelete: Boolean
        get() = getSetting(CLICK_EDIT_DELETE, true)
        set(value) = prefsNormal.edit { putBoolean(CLICK_EDIT_DELETE, value) }

    var shortSwipeUpAction: Constants.Action
        get() {
            return getEnumSetting(SWIPE_UP_ACTION, Constants.Action.ShowAppList)
        }
        set(value) = prefsNormal.edit { putString(SWIPE_UP_ACTION, value.name) }

    var shortSwipeDownAction: Constants.Action
        get() {
            return getEnumSetting(SWIPE_DOWN_ACTION, Constants.Action.ShowNotification)
        }
        set(value) = prefsNormal.edit { putString(SWIPE_DOWN_ACTION, value.name) }

    var shortSwipeLeftAction: Constants.Action
        get() {
            return getEnumSetting(SWIPE_LEFT_ACTION, Constants.Action.OpenApp)
        }
        set(value) = prefsNormal.edit { putString(SWIPE_LEFT_ACTION, value.name) }

    var shortSwipeRightAction: Constants.Action
        get() {
            return getEnumSetting(SWIPE_RIGHT_ACTION, Constants.Action.OpenApp)
        }
        set(value) = prefsNormal.edit { putString(SWIPE_RIGHT_ACTION, value.name) }

    var longSwipeUpAction: Constants.Action
        get() {
            return getEnumSetting(LONG_SWIPE_UP_ACTION, Constants.Action.ShowAppList)
        }
        set(value) = prefsNormal.edit { putString(LONG_SWIPE_UP_ACTION, value.name) }

    var longSwipeDownAction: Constants.Action
        get() {
            return getEnumSetting(LONG_SWIPE_DOWN_ACTION, Constants.Action.ShowNotification)
        }
        set(value) = prefsNormal.edit { putString(LONG_SWIPE_DOWN_ACTION, value.name) }

    var longSwipeLeftAction: Constants.Action
        get() {
            return getEnumSetting(LONG_SWIPE_LEFT_ACTION, Constants.Action.PreviousPage)
        }
        set(value) = prefsNormal.edit { putString(LONG_SWIPE_LEFT_ACTION, value.name) }

    var longSwipeRightAction: Constants.Action
        get() {
            return getEnumSetting(LONG_SWIPE_RIGHT_ACTION, Constants.Action.NextPage)
        }
        set(value) = prefsNormal.edit { putString(LONG_SWIPE_RIGHT_ACTION, value.name) }

    var clickClockAction: Constants.Action
        get() {
            return getEnumSetting(CLICK_CLOCK_ACTION, Constants.Action.OpenApp)
        }
        set(value) = prefsNormal.edit { putString(CLICK_CLOCK_ACTION, value.name) }

    var clickAppUsageAction: Constants.Action
        get() {
            return getEnumSetting(CLICK_APP_USAGE_ACTION, Constants.Action.ShowDigitalWellbeing)
        }
        set(value) = prefsNormal.edit { putString(CLICK_APP_USAGE_ACTION, value.name) }

    var clickFloatingAction: Constants.Action
        get() {
            return getEnumSetting(CLICK_FLOATING_ACTION, Constants.Action.ShowNotesManager)
        }
        set(value) = prefsNormal.edit { putString(CLICK_FLOATING_ACTION, value.name) }

    var clickDateAction: Constants.Action
        get() {
            return getEnumSetting(CLICK_DATE_ACTION, Constants.Action.OpenApp)
        }
        set(value) = prefsNormal.edit { putString(CLICK_DATE_ACTION, value.name) }

    var doubleTapAction: Constants.Action
        get() {
            return getEnumSetting(DOUBLE_TAP_ACTION, Constants.Action.LockScreen)
        }
        set(value) = prefsNormal.edit { putString(DOUBLE_TAP_ACTION, value.name) }

    var appTheme: Constants.Theme
        get() {
            return getEnumSetting(APP_THEME, Constants.Theme.System)
        }
        set(value) = prefsNormal.edit { putString(APP_THEME, value.name) }

    var tempUnit: Constants.TempUnits
        get() {
            return getEnumSetting(TEMP_UNIT, Constants.TempUnits.Celsius)
        }
        set(value) = prefsNormal.edit { putString(TEMP_UNIT, value.name) }

    var appLanguage: Constants.Language
        get() {
            return getEnumSetting(APP_LANGUAGE, Constants.Language.System)
        }
        set(value) = prefsNormal.edit { putString(APP_LANGUAGE, value.name) }

    var searchEngines: Constants.SearchEngines
        get() {
            return getEnumSetting(SEARCH_ENGINE, Constants.SearchEngines.Google)
        }
        set(value) = prefsNormal.edit { putString(SEARCH_ENGINE, value.name) }

    var fontFamily: Constants.FontFamily
        get() {
            return getEnumSetting(LAUNCHER_FONT, Constants.FontFamily.System)
        }
        set(value) = prefsNormal.edit { putString(LAUNCHER_FONT, value.name) }

    var hiddenApps: MutableSet<String>
        get() = prefsNormal.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefsNormal.edit { putStringSet(HIDDEN_APPS, value) }

    var lockedApps: MutableSet<String>
        get() = prefsNormal.getStringSet(LOCKED_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefsNormal.edit { putStringSet(LOCKED_APPS, value) }

    var pinnedApps: Set<String>
        get() = prefsNormal.getStringSet(PINNED_APPS, emptySet()) as Set<String>
        set(value) = prefsNormal.edit { putStringSet(PINNED_APPS, value) }


    var hiddenContacts: MutableSet<String>
        get() = prefsNormal.getStringSet(HIDDEN_CONTACTS, mutableSetOf()) as MutableSet<String>
        set(value) = prefsNormal.edit { putStringSet(HIDDEN_CONTACTS, value) }

    var pinnedContacts: Set<String>
        get() = prefsNormal.getStringSet(PINNED_CONTACTS, emptySet()) as Set<String>
        set(value) = prefsNormal.edit { putStringSet(PINNED_CONTACTS, value) }

    var enableExpertOptions: Boolean
        get() = getSetting(EXPERT_OPTIONS, false)
        set(value) = prefsNormal.edit { putBoolean(EXPERT_OPTIONS, value) }

    /**
     * By the number in home app list, get the list item.
     * TODO why not just save it as a list?
     */
    fun getHomeAppModel(i: Int): AppListItem {
        return loadApp("$i")
    }

    fun setHomeAppModel(i: Int, appListItem: AppListItem) {
        storeApp("$i", appListItem)
    }

    fun setHomeAppName(i: Int, name: String) {
        val nameId = "${APP_NAME}_$i"
        prefsNormal.edit { putString(nameId, name) }
    }

    var appShortSwipeUp: AppListItem
        get() = loadApp(SHORT_SWIPE_UP)
        set(appModel) = storeApp(SHORT_SWIPE_UP, appModel)
    var appShortSwipeDown: AppListItem
        get() = loadApp(SHORT_SWIPE_DOWN)
        set(appModel) = storeApp(SHORT_SWIPE_DOWN, appModel)
    var appShortSwipeLeft: AppListItem
        get() = loadApp(SHORT_SWIPE_LEFT)
        set(appModel) = storeApp(SHORT_SWIPE_LEFT, appModel)
    var appShortSwipeRight: AppListItem
        get() = loadApp(SHORT_SWIPE_RIGHT)
        set(appModel) = storeApp(SHORT_SWIPE_RIGHT, appModel)

    var appLongSwipeUp: AppListItem
        get() = loadApp(LONG_SWIPE_UP)
        set(appModel) = storeApp(LONG_SWIPE_UP, appModel)
    var appLongSwipeDown: AppListItem
        get() = loadApp(LONG_SWIPE_DOWN)
        set(appModel) = storeApp(LONG_SWIPE_DOWN, appModel)
    var appLongSwipeLeft: AppListItem
        get() = loadApp(LONG_SWIPE_LEFT)
        set(appModel) = storeApp(LONG_SWIPE_LEFT, appModel)
    var appLongSwipeRight: AppListItem
        get() = loadApp(LONG_SWIPE_RIGHT)
        set(appModel) = storeApp(LONG_SWIPE_RIGHT, appModel)

    var appClickClock: AppListItem
        get() = loadApp(CLICK_CLOCK)
        set(appModel) = storeApp(CLICK_CLOCK, appModel)
    var appClickUsage: AppListItem
        get() = loadApp(CLICK_USAGE)
        set(appModel) = storeApp(CLICK_USAGE, appModel)
    var appFloating: AppListItem
        get() = loadApp(CLICK_FLOATING)
        set(appModel) = storeApp(CLICK_FLOATING, appModel)
    var appClickDate: AppListItem
        get() = loadApp(CLICK_DATE)
        set(appModel) = storeApp(CLICK_DATE, appModel)
    var appDoubleTap: AppListItem
        get() = loadApp(DOUBLE_TAP)
        set(appModel) = storeApp(DOUBLE_TAP, appModel)

    /**
     *  Restore an `AppListItem` from preferences.
     *
     *  We store not only application name, but everything needed to start the item.
     *  Because thus we save time to query the system about it?
     *
     *  TODO store with protobuf instead of serializing manually.
     */
    private fun loadApp(id: String): AppListItem {
        val appName = prefsNormal.getString("${APP_NAME}_$id", emptyString()).toString()
        val appPackage = prefsNormal.getString("${APP_PACKAGE}_$id", emptyString()).toString()
        val appAlias = prefsNormal.getString("${APP_ALIAS}_$id", emptyString()).toString()
        val appActivityName = prefsNormal.getString("${APP_ACTIVITY}_$id", emptyString()).toString()

        val userHandleString = try {
            prefsNormal.getString("${APP_USER}_$id", emptyString()).toString()
        } catch (_: Exception) {
            emptyString()
        }
        val userHandle: UserHandle = getUserHandleFromString(context, userHandleString)

        return AppListItem(
            activityLabel = appName,
            activityPackage = appPackage,
            customLabel = appAlias,
            customTag = emptyString(),
            activityClass = appActivityName,
            user = userHandle,
        )
    }

    private fun storeApp(id: String, app: AppListItem) {
        prefsNormal.edit {
            if (app.activityPackage.isNotEmpty() && app.activityClass.isNotEmpty()) {
                putString("${APP_NAME}_$id", app.label)
                putString("${APP_PACKAGE}_$id", app.activityPackage)
                putString("${APP_ACTIVITY}_$id", app.activityClass)
                putString("${APP_ALIAS}_$id", app.customLabel)
                putString("${APP_USER}_$id", app.user.toString())
            } else {
                remove("${APP_NAME}_$id")
                remove("${APP_PACKAGE}_$id")
                remove("${APP_ACTIVITY}_$id")
                remove("${APP_ALIAS}_$id")
                remove("${APP_USER}_$id")
            }
        }
    }

    var appSize: Int
        get() {
            return getSetting(APP_SIZE_TEXT, 18)
        }
        set(value) = prefsNormal.edit { putInt(APP_SIZE_TEXT, value) }

    var dateSize: Int
        get() {
            return getSetting(DATE_SIZE_TEXT, 22)
        }
        set(value) = prefsNormal.edit { putInt(DATE_SIZE_TEXT, value) }

    var clockSize: Int
        get() {
            return getSetting(CLOCK_SIZE_TEXT, 42)
        }
        set(value) = prefsNormal.edit { putInt(CLOCK_SIZE_TEXT, value) }

    var alarmSize: Int
        get() {
            return getSetting(ALARM_SIZE_TEXT, 20)
        }
        set(value) = prefsNormal.edit { putInt(ALARM_SIZE_TEXT, value) }

    var dailyWordSize: Int
        get() {
            return getSetting(DAILY_WORD_SIZE_TEXT, 20)
        }
        set(value) = prefsNormal.edit { putInt(DAILY_WORD_SIZE_TEXT, value) }


    var batterySize: Int
        get() {
            return getSetting(BATTERY_SIZE_TEXT, 14)
        }
        set(value) = prefsNormal.edit { putInt(BATTERY_SIZE_TEXT, value) }

    var settingsSize: Int
        get() {
            return getSetting(TEXT_SIZE_SETTINGS, 12)
        }
        set(value) = prefsNormal.edit { putInt(TEXT_SIZE_SETTINGS, value) }

    var textPaddingSize: Int
        get() {
            return getSetting(TEXT_PADDING_SIZE, 10)
        }
        set(value) = prefsNormal.edit { putInt(TEXT_PADDING_SIZE, value) }

    // Save flags as a string of 6 bits
    fun saveMenuFlags(settingFlags: String, flags: List<Boolean>) {
        val flagString = flags.joinToString("") { if (it) "1" else "0" }
        prefsNormal.edit { putString(settingFlags, flagString) }
    }

    // Get flags as list of booleans
    fun getMenuFlags(settingFlags: String, default: String = "0"): List<Boolean> {
        val flagString = prefsNormal.getString(settingFlags, default) ?: default
        return flagString.map { it == '1' }
    }

    private fun getColorInt(type: String): Int {
        val isDarkMode = isSystemInDarkMode(context)

        val lightModeColors = mapOf(
            "bg" to R.color.white,
            "bg_notes" to R.color.light_gray_light,
            "bg_bubble" to R.color.light_gray_medium,
            "bg_bubble_message" to R.color.black,
            "bg_bubble_time_date" to R.color.dark_gray_very_dark,
            "bg_bubble_category" to R.color.dark_gray_dark,
            "input_text" to R.color.dark_gray_very_dark,
            "input_text_hint" to R.color.dark_gray_dark,
        )

        val darkModeColors = mapOf(
            "bg" to R.color.black,
            "bg_notes" to R.color.dark_gray_very_dark,
            "bg_bubble" to R.color.dark_gray_dark,
            "bg_bubble_message" to R.color.white,
            "bg_bubble_time_date" to R.color.light_gray_very_light,
            "bg_bubble_category" to R.color.light_gray_light,
            "input_text" to R.color.light_gray_very_light,
            "input_text_hint" to R.color.light_gray_light,
        )

        val defaultLight = R.color.black
        val defaultDark = R.color.white

        return when (appTheme) {
            Constants.Theme.System -> {
                if (isDarkMode) darkModeColors[type] ?: defaultDark
                else lightModeColors[type] ?: defaultLight
            }

            Constants.Theme.Dark -> darkModeColors[type] ?: defaultDark
            Constants.Theme.Light -> lightModeColors[type] ?: defaultLight
        }
    }

    // return app label
    fun getAppName(location: Int): String {
        return getHomeAppModel(location).label
    }

    fun getAppAlias(appPackage: String): String {
        return prefsNormal.getString("${appPackage}_ALIAS", emptyString()).toString()
    }

    fun setAppAlias(appPackage: String, appAlias: String) {
        prefsNormal.edit { putString("${appPackage}_ALIAS", appAlias) }
    }

    fun setProfileCounter(profile: String, counter: Int) {
        prefsNormal.edit { putInt(profile, counter) }
    }

    fun getProfileCounter(profile: String): Int {
        return prefsNormal.getInt(profile, 0)
    }

    fun getAppTag(appPackage: String, userHandle: UserHandle? = null): String {
        val baseKey = "${appPackage}_TAG"
        val userKey = userHandle?.let { "${baseKey}_${it.hashCode()}" }

        return prefsNormal.getString(
            userKey ?: baseKey, ""  // Try the user-specific key first if available
        )?.also { value ->
            // Migrate base key to user-specific key if needed
            if (userHandle != null && prefsNormal.contains(baseKey)) {
                prefsNormal.edit {
                    remove(baseKey)           // Remove old base key
                    putString(userKey, value) // Save under user-specific key
                }
            }
        } ?: ""
    }


    fun setAppTag(appPackage: String, appTag: String, userHandle: UserHandle? = null) {
        prefsNormal.edit {
            // Remove base key
            remove("${appPackage}_TAG")

            userHandle?.let {
                // Remove key using the UserHandle object itself
                remove("${appPackage}_TAG_${it}")

                // Set new TAG with hashCode()
                putString("${appPackage}_TAG_${it.hashCode()}", appTag)
            }
        }
    }

    /** 🔹 Save selected location into SharedPreferences */
    fun saveLocation(results: LocationResult) {
        results.let {
            prefsNormal.edit {
                putString(WEATHER_LOCATION, it.region)
                putFloat(WEATHER_LATITUDE, it.latitude.toFloat())
                putFloat(WEATHER_LONGITUDE, it.longitude.toFloat())
            }
        }
    }

    /** 🔹 Load saved location */
    fun loadLocation(): Pair<Double, Double>? {
        val lat = prefsNormal.getFloat(WEATHER_LATITUDE, Float.NaN)
        val lon = prefsNormal.getFloat(WEATHER_LONGITUDE, Float.NaN)

        return if (!lat.isNaN() && !lon.isNaN()) {
            Pair(lat.toDouble(), lon.toDouble())
        } else {
            null
        }
    }

    fun loadLocationName(): String {
        return prefsNormal.getString(WEATHER_LOCATION, "Select Location").toString()
    }

    fun remove(prefName: String) {
        prefsNormal.edit { remove(prefName) }
    }

    fun clear() {
        prefsNormal.edit { clear() }
    }

    fun saveMessages(messages: List<Message>) {
        prefsNormal.edit {
            val json = messageAdapter.toJson(messages)
            putString(NOTES_MESSAGES, json)
        }
    }

    fun loadMessagesWrong(): List<MessageWrong> {
        val json = prefsNormal.getString(NOTES_MESSAGES, "[]") ?: return emptyList()
        return messageWrongAdapter.fromJson(json) ?: emptyList()
    }

    fun loadMessages(): List<Message> {
        val json = prefsNormal.getString(NOTES_MESSAGES, "[]") ?: return emptyList()
        return messageAdapter.fromJson(json) ?: emptyList()
    }


    fun saveSettings(category: String, priority: String) {
        prefsNormal.edit {
            putString(NOTES_CATEGORY, category)
            putString(NOTES_PRIORITY, priority)
        }
    }

    fun loadSettings(): Pair<String, String> {
        val category = prefsNormal.getString(NOTES_CATEGORY, "None") ?: "None"
        val priority = prefsNormal.getString(NOTES_PRIORITY, "None") ?: "None"
        return Pair(category, priority)
    }

    // Function to fetch enum value from SharedPreferences
    private inline fun <reified T : Enum<T>> getEnumSetting(key: String, defaultValue: T): T {
        val enumName = prefsNormal.getString(key, defaultValue.name)
        return try {
            enumValueOf<T>(enumName ?: defaultValue.name)
        } catch (_: IllegalArgumentException) {
            defaultValue
        }
    }

    private inline fun <reified T> getSetting(key: String, defaultValue: T): T {
        // Otherwise, fetch from SharedPreferences
        val result = when (defaultValue) {
            is Int -> prefsNormal.getInt(key, defaultValue)
            is Boolean -> prefsNormal.getBoolean(key, defaultValue)
            is String -> prefsNormal.getString(key, defaultValue) ?: defaultValue
            is Float -> prefsNormal.getFloat(key, defaultValue)
            else -> throw IllegalArgumentException("Unsupported type")
        }

        return result as T
    }


    fun isOnboardingCompleted(): Boolean {
        return prefsOnboarding.getBoolean(ONBOARDING_COMPLETED, false)
    }

    // Function to mark onboarding as completed
    fun setOnboardingCompleted(isCompleted: Boolean) {
        prefsOnboarding.edit { putBoolean(ONBOARDING_COMPLETED, isCompleted) }
    }
}
