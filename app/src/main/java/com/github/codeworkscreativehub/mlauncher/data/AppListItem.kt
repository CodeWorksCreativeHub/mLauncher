package com.github.codeworkscreativehub.mlauncher.data

import android.os.UserHandle
import com.github.codeworkscreativehub.mlauncher.helper.emptyString
import java.text.Collator

val collator: Collator = Collator.getInstance()

/**
 * We create instances in 3 different places:
 * 1. app drawer
 * 2. recent apps
 * 3. home screen (for the list and for swipes/taps)
 *
 * @property activityLabel
 * label of the activity (`LauncherActivityInfo.label`)
 *
 * @property activityPackage
 * Package name of the activity (`LauncherActivityInfo.applicationInfo.packageName`)
 *
 * @property activityClass
 * (`LauncherActivityInfo.componentName.className`)
 *
 * @property user
 * userHandle is needed to resolve and start an activity.
 * And also we mark with a special icon the apps which belong to a managed user.
 *
 * Pinned shortcuts (e.g. "Add to home screen" from a browser) are stored as an
 * AppListItem too: [activityPackage] is the package that published the shortcut and
 * [activityClass] is [SHORTCUT_PREFIX] followed by the shortcut id.
 */
data class AppListItem(
    val activityLabel: String,
    val activityPackage: String,
    val activityClass: String,
    val user: UserHandle,
    val profileType: String = "SYSTEM",
    var customTag: String,
    var category: AppCategory = AppCategory.REGULAR
) : Comparable<AppListItem> {

    val tag = customTag.ifEmpty { emptyString() }

    val isShortcut: Boolean
        get() = activityClass.startsWith(SHORTCUT_PREFIX)

    val shortcutId: String
        get() = activityClass.removePrefix(SHORTCUT_PREFIX)

    /**
     * Key for per-item settings (alias, tag, lock, pin).
     * A shortcut shares its package with the app that created it (e.g. the browser),
     * so it needs its own key, otherwise renaming a shortcut would rename the browser.
     */
    val settingsKey: String
        get() = settingsKeyOf(activityPackage, activityClass)

    /** Speed up sort and search */
    private val collationKey = collator.getCollationKey(activityLabel)

    override fun compareTo(other: AppListItem): Int =
        collationKey.compareTo(other.collationKey)
}

const val SHORTCUT_PREFIX = "shortcut:"

/** See [AppListItem.settingsKey]. */
fun settingsKeyOf(packageName: String, activityClass: String): String =
    if (activityClass.startsWith(SHORTCUT_PREFIX)) "$packageName/$activityClass" else packageName

enum class AppCategory {
    RECENT, PINNED, REGULAR
}
