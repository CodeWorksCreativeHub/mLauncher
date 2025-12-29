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
 * @property customLabel
 * When user renames an app, we store the rename in Preferences.
 *
 * @property label
 * Use this property to render the list item.
 * It's either the original activity label (`activityLabel`) or a user-defined label (`definedLabel`).
 */
data class AppListItem(
    val activityLabel: String,
    val activityPackage: String,
    val activityClass: String,
    val user: UserHandle,
    val profileType: String = "SYSTEM",
    var customLabel: String,
    var customTag: String,
    var category: AppCategory = AppCategory.REGULAR
) : Comparable<AppListItem> {

    val label = customLabel.ifEmpty { activityLabel }
    val tag = customTag.ifEmpty { emptyString() }

    /** Speed up sort and search */
    private val collationKey = collator.getCollationKey(label)

    override fun compareTo(other: AppListItem): Int =
        collationKey.compareTo(other.collationKey)
}

enum class AppCategory {
    RECENT, PINNED, REGULAR
}
