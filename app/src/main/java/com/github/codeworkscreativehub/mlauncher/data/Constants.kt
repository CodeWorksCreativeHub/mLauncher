package com.github.codeworkscreativehub.mlauncher.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.core.content.res.ResourcesCompat
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.Mlauncher
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.helper.IconCacheTarget
import com.github.codeworkscreativehub.mlauncher.helper.getTrueSystemFont
import java.io.File
import java.util.Locale

interface EnumOption {
    @Composable
    fun string(): String
}


object Constants {
    const val MIN_HOME_APPS = 0

    const val MIN_HOME_PAGES = 1

    const val MIN_TEXT_SIZE = 10
    const val MAX_TEXT_SIZE = 100

    const val MIN_CLOCK_DATE_SIZE = 10
    const val MAX_CLOCK_DATE_SIZE = 120

    const val MIN_ALARM_SIZE = 10
    const val MAX_ALARM_SIZE = 120

    const val MIN_DAILY_WORD_SIZE = 10
    const val MAX_DAILY_WORD_SIZE = 120

    const val MIN_BATTERY_SIZE = 10
    const val MAX_BATTERY_SIZE = 75

    const val MIN_TEXT_PADDING = 0
    const val MAX_TEXT_PADDING = 50

    const val MIN_RECENT_COUNTER = 1
    const val MAX_RECENT_COUNTER = 35

    const val MIN_FILTER_STRENGTH = 0
    const val MAX_FILTER_STRENGTH = 100

    const val MIN_OPACITY = 0
    const val MAX_OPACITY = 100

    const val DOUBLE_CLICK_TIME_DELTA = 300L // Adjust as needed
    const val SWIPE_VELOCITY_THRESHOLD = 450f // Adjust as needed

    // Update SWIPE_DISTANCE_THRESHOLD dynamically based on screen dimensions
    const val MIN_THRESHOLD = 0f
    const val MAX_THRESHOLD = 1f
    var SHORT_SWIPE_THRESHOLD = 0f  // pixels
    var LONG_SWIPE_THRESHOLD = 0f // pixels
    var USR_DPIX = 0f
    var USR_DPIY = 0f


    // Update MAX_HOME_PAGES dynamically based on MAX_HOME_APPS
    var MAX_HOME_APPS = 20
    var MAX_HOME_PAGES = 10

    const val ACCESS_FINE_LOCATION = 666
    const val READ_CONTACTS = 777

    fun updateMaxHomePages(context: Context) {
        val prefs = Prefs(context)

        MAX_HOME_PAGES = if (prefs.homeAppsNum < MAX_HOME_PAGES) {
            prefs.homeAppsNum
        } else {
            MAX_HOME_PAGES
        }
    }

    fun updateMaxAppsBasedOnPages(context: Context) {
        val prefs = Prefs(context)

        // Define maximum apps per page
        val maxAppsPerPage = 20

        // Set MAX_HOME_APPS to the number of apps based on pages and apps per page
        MAX_HOME_APPS = maxAppsPerPage * prefs.homePagesNum
    }


    fun updateSwipeDistanceThreshold(context: Context, direction: String) {
        val prefs = Prefs(context)
        val metrics = context.resources.displayMetrics

        USR_DPIX = metrics.xdpi
        USR_DPIY = metrics.ydpi

        val screenWidthInches = metrics.widthPixels / USR_DPIX
        val screenHeightInches = metrics.heightPixels / USR_DPIY

        if (direction.equals("left", true) || direction.equals("right", true)) {
            LONG_SWIPE_THRESHOLD = screenWidthInches * prefs.longSwipeThreshold
            SHORT_SWIPE_THRESHOLD = screenWidthInches * prefs.shortSwipeThreshold
        } else {
            LONG_SWIPE_THRESHOLD = screenHeightInches * prefs.longSwipeThreshold
            SHORT_SWIPE_THRESHOLD = screenHeightInches * prefs.shortSwipeThreshold
        }

        AppLogger.d(
            "GestureThresholds",
            "Updated thresholds for $direction: SHORT = $SHORT_SWIPE_THRESHOLD inches, LONG = $LONG_SWIPE_THRESHOLD inches"
        )
    }

    enum class AppDrawerFlag {
        None,
        LaunchApp,
        HiddenApps,
        PrivateApps,
        SetHomeApp,
        SetShortSwipeUp,
        SetShortSwipeDown,
        SetShortSwipeLeft,
        SetShortSwipeRight,
        SetLongSwipeUp,
        SetLongSwipeDown,
        SetLongSwipeLeft,
        SetLongSwipeRight,
        SetClickClock,
        SetAppUsage,
        SetFloating,
        SetClickDate,
        SetDoubleTap,
    }

    enum class Language : EnumOption {
        System,
        Arabic,
        Czech,
        Dutch,
        English,
        French,
        German,
        Hebrew,
        Italian,
        Japanese,
        Korean,
        Lithuanian,
        Polish,
        Portuguese,
        Romanian,
        Russian,
        Slovak,
        Spanish,
        Thai,
        Turkish;

        // Function to get a string from a context (for non-Composable use)
        fun getString(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Arabic -> getLocalizedString(R.string.lang_arabic)
                Czech -> getLocalizedString(R.string.lang_czech)
                Dutch -> getLocalizedString(R.string.lang_dutch)
                English -> getLocalizedString(R.string.lang_english)
                French -> getLocalizedString(R.string.lang_french)
                German -> getLocalizedString(R.string.lang_german)
                Hebrew -> getLocalizedString(R.string.lang_hebrew)
                Italian -> getLocalizedString(R.string.lang_italian)
                Japanese -> getLocalizedString(R.string.lang_japanese)
                Korean -> getLocalizedString(R.string.lang_korean)
                Lithuanian -> getLocalizedString(R.string.lang_lithuanian)
                Polish -> getLocalizedString(R.string.lang_polish)
                Portuguese -> getLocalizedString(R.string.lang_portuguese)
                Romanian -> getLocalizedString(R.string.lang_romanian)
                Russian -> getLocalizedString(R.string.lang_russian)
                Slovak -> getLocalizedString(R.string.lang_slovak)
                Spanish -> getLocalizedString(R.string.lang_spanish)
                Thai -> getLocalizedString(R.string.lang_thai)
                Turkish -> getLocalizedString(R.string.lang_turkish)
            }
        }

        @Composable
        override fun string(): String = getString()


        fun locale(): Locale {
            return Locale.forLanguageTag(zone())
        }

        private fun zone(): String {
            return when (this) {
                System -> Locale.getDefault().toLanguageTag()
                Arabic -> "ar-SA"
                Czech -> "cs-Cz"
                Dutch -> "nl-NL"
                English -> "en-US"
                French -> "fr-FR"
                German -> "de-DE"
                Hebrew -> "he-IL"
                Italian -> "it-IT"
                Japanese -> "ja-JP"
                Korean -> "ko-KR"
                Lithuanian -> "lt-LT"
                Polish -> "pl-PL"
                Portuguese -> "pt-PT"
                Romanian -> "ro-RO"
                Russian -> "ru-RU"
                Slovak -> "sk-SK"
                Spanish -> "es-ES"
                Thai -> "th-TH"
                Turkish -> "tr-TR"
            }
        }
    }

    enum class Gravity : EnumOption {
        Left,
        Center,
        Right;

        @Composable
        override fun string(): String {
            return when (this) {
                Left -> getLocalizedString(R.string.left)
                Center -> getLocalizedString(R.string.center)
                Right -> getLocalizedString(R.string.right)
            }
        }

        @SuppressLint("RtlHardcoded")
        fun value(): Int {
            return when (this) {
                Left -> android.view.Gravity.LEFT
                Center -> android.view.Gravity.CENTER
                Right -> android.view.Gravity.RIGHT
            }
        }
    }

    fun getCustomIconPackName(target: String): String {
        return when (target) {
            IconCacheTarget.APP_LIST.name -> {
                val customPackageName = Prefs(Mlauncher.getContext()).customIconPackAppList
                if (customPackageName.isEmpty()) {
                    getLocalizedString(R.string.system_custom)
                }
                try {
                    val pm = Mlauncher.getContext().packageManager
                    val appInfo = pm.getApplicationInfo(customPackageName, 0)
                    val customName = pm.getApplicationLabel(appInfo).toString()
                    getLocalizedString(R.string.system_custom_plus, customName)
                } catch (_: NameNotFoundException) {
                    getLocalizedString(R.string.system_custom)
                }
            }

            IconCacheTarget.HOME.name -> {
                val customPackageName = Prefs(Mlauncher.getContext()).customIconPackHome
                if (customPackageName.isEmpty()) {
                    getLocalizedString(R.string.system_custom)
                }
                try {
                    val pm = Mlauncher.getContext().packageManager
                    val appInfo = pm.getApplicationInfo(customPackageName, 0)
                    val customName = pm.getApplicationLabel(appInfo).toString()
                    getLocalizedString(R.string.system_custom_plus, customName)
                } catch (_: NameNotFoundException) {
                    getLocalizedString(R.string.system_custom)
                }
            }

            else -> getLocalizedString(R.string.system_custom)
        }
    }

    enum class IconPacks : EnumOption {
        System,
        Custom,
        CloudDots,
        LauncherDots,
        NiagaraDots,
        SpinnerDots,
        Disabled;

        fun getString(target: String): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Custom -> getCustomIconPackName(target)
                CloudDots -> getLocalizedString(R.string.app_icons_cloud_dots)
                LauncherDots -> getLocalizedString(R.string.app_icons_launcher_dots)
                NiagaraDots -> getLocalizedString(R.string.app_icons_niagara_dots)
                SpinnerDots -> getLocalizedString(R.string.app_icons_spinner_dots)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Custom -> getLocalizedString(R.string.system_custom)
                CloudDots -> getLocalizedString(R.string.app_icons_cloud_dots)
                LauncherDots -> getLocalizedString(R.string.app_icons_launcher_dots)
                NiagaraDots -> getLocalizedString(R.string.app_icons_niagara_dots)
                SpinnerDots -> getLocalizedString(R.string.app_icons_spinner_dots)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }
    }

    enum class Action : EnumOption {
        OpenApp,
        TogglePrivateSpace,
        LockScreen,
        ShowNotification,
        ShowAppList,
        ShowWidgetPage,
        ShowNotesManager,
        ShowDigitalWellbeing,
        OpenQuickSettings,
        ShowRecents,
        OpenPowerDialog,
        TakeScreenShot,
        PreviousPage,
        NextPage,
        RestartApp,
        Disabled;

        fun getString(): String {
            return when (this) {
                OpenApp -> getLocalizedString(R.string.open_app)
                LockScreen -> getLocalizedString(R.string.lock_screen)
                TogglePrivateSpace -> getLocalizedString(R.string.private_space, "Toggle")
                ShowNotification -> getLocalizedString(R.string.show_notifications)
                ShowAppList -> getLocalizedString(R.string.show_app_list)
                ShowWidgetPage -> getLocalizedString(R.string.show_widget_page)
                ShowNotesManager -> getLocalizedString(R.string.show_notes_manager)
                ShowDigitalWellbeing -> getLocalizedString(R.string.show_digital_wellbeing)
                OpenQuickSettings -> getLocalizedString(R.string.open_quick_settings)
                ShowRecents -> getLocalizedString(R.string.show_recents)
                OpenPowerDialog -> getLocalizedString(R.string.open_power_dialog)
                TakeScreenShot -> getLocalizedString(R.string.take_a_screenshot)
                PreviousPage -> getLocalizedString(R.string.previous_page)
                NextPage -> getLocalizedString(R.string.next_page)
                RestartApp -> getLocalizedString(R.string.restart_launcher)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                OpenApp -> getLocalizedString(R.string.open_app)
                LockScreen -> getLocalizedString(R.string.lock_screen)
                TogglePrivateSpace -> getLocalizedString(R.string.private_space)
                ShowNotification -> getLocalizedString(R.string.show_notifications)
                ShowAppList -> getLocalizedString(R.string.show_app_list)
                ShowWidgetPage -> getLocalizedString(R.string.show_widget_page)
                ShowNotesManager -> getLocalizedString(R.string.show_notes_manager)
                ShowDigitalWellbeing -> getLocalizedString(R.string.show_digital_wellbeing)
                OpenQuickSettings -> getLocalizedString(R.string.open_quick_settings)
                ShowRecents -> getLocalizedString(R.string.show_recents)
                OpenPowerDialog -> getLocalizedString(R.string.open_power_dialog)
                TakeScreenShot -> getLocalizedString(R.string.take_a_screenshot)
                PreviousPage -> getLocalizedString(R.string.previous_page)
                NextPage -> getLocalizedString(R.string.next_page)
                RestartApp -> getLocalizedString(R.string.restart_launcher)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }
    }

    enum class SearchEngines : EnumOption {
        Bing,
        Brave,
        DuckDuckGo,
        Google,
        Mojeek,
        Qwant,
        Seznam,
        StartPage,
        SwissCow,
        Yahoo,
        Yandex;

        fun getString(): String {
            return when (this) {
                Bing -> getLocalizedString(R.string.search_bing)
                Brave -> getLocalizedString(R.string.search_brave)
                DuckDuckGo -> getLocalizedString(R.string.search_duckduckgo)
                Google -> getLocalizedString(R.string.search_google)
                Mojeek -> getLocalizedString(R.string.search_mojeek)
                Qwant -> getLocalizedString(R.string.search_qwant)
                Seznam -> getLocalizedString(R.string.search_seznam)
                StartPage -> getLocalizedString(R.string.search_startpage)
                SwissCow -> getLocalizedString(R.string.search_swisscow)
                Yahoo -> getLocalizedString(R.string.search_yahoo)
                Yandex -> getLocalizedString(R.string.search_yandex)
            }
        }

        fun getURL(): String {
            return when (this) {
                Bing -> "https://bing.com/search?q="
                Brave -> "https://search.brave.com/search?q="
                DuckDuckGo -> "https://duckduckgo.com/?q="
                Google -> "https://google.com/search?q="
                Mojeek -> "https://www.mojeek.com/search?q="
                Qwant -> "https://www.qwant.com/?q="
                Seznam -> "https://search.seznam.cz/?q="
                StartPage -> "https://www.startpage.com/sp/search?q="
                SwissCow -> "https://swisscows.com/web?query="
                Yahoo -> "https://search.yahoo.com/search?p="
                Yandex -> "https://yandex.com/search/?text="
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                Bing -> getLocalizedString(R.string.search_bing)
                Brave -> getLocalizedString(R.string.search_brave)
                DuckDuckGo -> getLocalizedString(R.string.search_duckduckgo)
                Google -> getLocalizedString(R.string.search_google)
                Mojeek -> getLocalizedString(R.string.search_mojeek)
                Qwant -> getLocalizedString(R.string.search_qwant)
                Seznam -> getLocalizedString(R.string.search_seznam)
                StartPage -> getLocalizedString(R.string.search_startpage)
                SwissCow -> getLocalizedString(R.string.search_swisscow)
                Yahoo -> getLocalizedString(R.string.search_yahoo)
                Yandex -> getLocalizedString(R.string.search_yandex)
            }
        }
    }


    enum class Theme : EnumOption {
        System,
        Dark,
        Light;

        fun getString(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Dark -> getLocalizedString(R.string.dark)
                Light -> getLocalizedString(R.string.light)
            }
        }

        // Keep this for Composable usage
        @Composable
        override fun string(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Dark -> getLocalizedString(R.string.dark)
                Light -> getLocalizedString(R.string.light)
            }
        }
    }

    enum class TempUnits : EnumOption {
        Celsius,
        Fahrenheit;

        // Keep this for Composable usage
        @Composable
        override fun string(): String {
            return when (this) {
                Celsius -> getLocalizedString(R.string.celsius)
                Fahrenheit -> getLocalizedString(R.string.fahrenheit)
            }
        }
    }


    enum class FontFamily : EnumOption {
        System,
        Custom,
        Bitter,
        Doto,
        FiraCode,
        Hack,
        Lato,
        Merriweather,
        Montserrat,
        Quicksand,
        Raleway,
        Roboto,
        SourceCodePro;

        fun getFont(context: Context): Typeface? {
            return when (this) {
                System -> getTrueSystemFont()
                Bitter -> ResourcesCompat.getFont(context, R.font.bitter)
                Doto -> ResourcesCompat.getFont(context, R.font.doto)
                FiraCode -> ResourcesCompat.getFont(context, R.font.fira_code)
                Hack -> ResourcesCompat.getFont(context, R.font.hack)
                Lato -> ResourcesCompat.getFont(context, R.font.lato)
                Merriweather -> ResourcesCompat.getFont(context, R.font.merriweather)
                Montserrat -> ResourcesCompat.getFont(context, R.font.montserrat)
                Quicksand -> ResourcesCompat.getFont(context, R.font.quicksand)
                Raleway -> ResourcesCompat.getFont(context, R.font.raleway)
                Roboto -> ResourcesCompat.getFont(context, R.font.roboto)
                SourceCodePro -> ResourcesCompat.getFont(context, R.font.source_code_pro)
                Custom -> {
                    val customFontFile = File(context.filesDir, "CustomFont.ttf")
                    if (customFontFile.exists()) {
                        try {
                            Typeface.createFromFile(customFontFile)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            getTrueSystemFont()
                        }
                    } else getTrueSystemFont()
                }
            }
        }

        fun getString(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Bitter -> getLocalizedString(R.string.settings_font_bitter)
                Doto -> getLocalizedString(R.string.settings_font_doto)
                FiraCode -> getLocalizedString(R.string.settings_font_firacode)
                Hack -> getLocalizedString(R.string.settings_font_hack)
                Lato -> getLocalizedString(R.string.settings_font_lato)
                Merriweather -> getLocalizedString(R.string.settings_font_merriweather)
                Montserrat -> getLocalizedString(R.string.settings_font_montserrat)
                Quicksand -> getLocalizedString(R.string.settings_font_quicksand)
                Raleway -> getLocalizedString(R.string.settings_font_raleway)
                Roboto -> getLocalizedString(R.string.settings_font_roboto)
                SourceCodePro -> getLocalizedString(R.string.settings_font_sourcecodepro)
                Custom -> getLocalizedString(R.string.system_custom)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Bitter -> getLocalizedString(R.string.settings_font_bitter)
                Doto -> getLocalizedString(R.string.settings_font_doto)
                FiraCode -> getLocalizedString(R.string.settings_font_firacode)
                Hack -> getLocalizedString(R.string.settings_font_hack)
                Lato -> getLocalizedString(R.string.settings_font_lato)
                Merriweather -> getLocalizedString(R.string.settings_font_merriweather)
                Montserrat -> getLocalizedString(R.string.settings_font_montserrat)
                Quicksand -> getLocalizedString(R.string.settings_font_quicksand)
                Raleway -> getLocalizedString(R.string.settings_font_raleway)
                Roboto -> getLocalizedString(R.string.settings_font_roboto)
                SourceCodePro -> getLocalizedString(R.string.settings_font_sourcecodepro)
                Custom -> getLocalizedString(R.string.system_custom)
            }
        }
    }

    const val URL_GOOGLE_PLAY_STORE = "https://play.google.com/store/search?c=apps&q"
    const val APP_GOOGLE_PLAY_STORE = "market://search?c=apps&q"
}
