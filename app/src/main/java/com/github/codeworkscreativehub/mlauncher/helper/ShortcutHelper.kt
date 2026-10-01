package com.github.codeworkscreativehub.mlauncher.helper

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import androidx.core.net.toUri
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Access to pinned shortcuts (e.g. "Add to home screen" from a browser).
 *
 * Only the default launcher may read or start shortcuts, so every call
 * fails soft and returns an empty / false result otherwise.
 */
object ShortcutHelper {
    private const val TAG = "ShortcutHelper"

    /** Icons need a binder call and a bitmap decode, so keep them once loaded. */
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private fun iconCacheKey(item: AppListItem) = "${item.settingsKey}|${item.user.hashCode()}"

    private fun launcherApps(context: Context) =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    fun hasPermission(context: Context): Boolean =
        runCatching { launcherApps(context).hasShortcutHostPermission() }.getOrDefault(false)

    /** Enabled shortcuts pinned to this launcher for [user]. */
    fun getPinnedShortcuts(context: Context, user: UserHandle): List<ShortcutInfo> =
        queryPinnedShortcuts(context, user).orEmpty().filter { it.isEnabled }

    /**
     * All shortcuts pinned to this launcher for [user], including disabled ones.
     * Returns null when the query is not possible (not the default launcher, profile locked or
     * paused), so callers can tell "no shortcuts" apart from "unknown".
     */
    fun queryPinnedShortcuts(context: Context, user: UserHandle): List<ShortcutInfo>? {
        if (!hasPermission(context)) return null

        val query = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        return runCatching { launcherApps(context).getShortcuts(query, user) }
            .onFailure { AppLogger.e(TAG, "Failed to get pinned shortcuts for $user: ${it.message}", it) }
            .getOrNull()
    }

    private fun findShortcut(context: Context, item: AppListItem): ShortcutInfo? {
        if (!hasPermission(context)) return null

        val query = LauncherApps.ShortcutQuery()
            .setPackage(item.activityPackage)
            .setShortcutIds(listOf(item.shortcutId))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        return runCatching { launcherApps(context).getShortcuts(query, item.user) }
            .getOrNull()
            ?.firstOrNull()
    }

    fun startShortcut(context: Context, item: AppListItem): Boolean {
        // The cached app list does not keep the profile, so fall back to the other profiles
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val users = listOf(item.user) + userManager.userProfiles.filter { it != item.user }

        return users.any { user ->
            runCatching {
                launcherApps(context).startShortcut(item.activityPackage, item.shortcutId, null, null, user)
            }.onFailure {
                AppLogger.e(TAG, "Failed to start shortcut ${item.settingsKey} for $user: ${it.message}", it)
            }.isSuccess
        }
    }

    fun getIcon(context: Context, item: AppListItem): Drawable? {
        val icon = iconCache[iconCacheKey(item)] ?: run {
            val shortcut = findShortcut(context, item) ?: return null
            runCatching {
                launcherApps(context).getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
            }.getOrNull()?.also { iconCache[iconCacheKey(item)] = it } ?: return null
        }
        // Callers change bounds, so hand out a separate copy
        return icon.constantState?.newDrawable()?.mutate() ?: icon
    }

    /** Browsers can open http links; used to tell web shortcuts apart from app shortcuts. */
    fun isBrowser(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).setPackage(packageName)
        return runCatching { context.packageManager.queryIntentActivities(intent, 0).isNotEmpty() }
            .getOrDefault(false)
    }

    /** e.g. "Web shortcut · Firefox" or "Shortcut · Maps". */
    fun describe(context: Context, packageName: String): String {
        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)

        return if (isBrowser(context, packageName)) getLocalizedString(R.string.shortcut_type_web, appLabel)
        else getLocalizedString(R.string.shortcut_type_app, appLabel)
    }

    /** Unpins the shortcut, which removes it from this launcher. */
    fun removeShortcut(context: Context, item: AppListItem): Boolean {
        // Include disabled shortcuts, otherwise they would be unpinned too
        val remainingIds = (queryPinnedShortcuts(context, item.user) ?: return false)
            .filter { it.`package` == item.activityPackage && it.id != item.shortcutId }
            .map { it.id }

        iconCache.remove(iconCacheKey(item))
        return runCatching {
            launcherApps(context).pinShortcuts(item.activityPackage, remainingIds, item.user)
        }.onFailure {
            AppLogger.e(TAG, "Failed to remove shortcut ${item.settingsKey}: ${it.message}", it)
        }.isSuccess
    }
}
