package com.github.codeworkscreativehub.mlauncher.helper.utils

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.MainActivity
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.helper.ismlauncherDefault

class PrivateSpaceManager(private val context: Context) {

    /**
     * Checks whether the device supports private space.
     */
    fun isPrivateSpaceSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    /**
     * Get the private space user if available.
     */
    private fun getPrivateSpaceUser(): UserHandle? {
        if (!isPrivateSpaceSupported()) {
            return null
        }
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        return userManager.userProfiles.firstOrNull { u ->
            launcherApps.getLauncherUserInfo(u)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        }
    }

    /**
     * Check if the given user profile is the private space.
     */
    fun isPrivateSpaceProfile(userHandle: UserHandle): Boolean {
        if (!isPrivateSpaceSupported()) {
            return false
        }

        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try {
            val userType = launcherApps.getLauncherUserInfo(userHandle)?.userType
            return userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        } catch (e: Exception) {
            AppLogger.e("PrivateSpace", "Failed to retrieve launcher user info", e)
            return false
        }
    }

    /**
     * Check whether the user has created a private space and whether mLauncher can access it.
     */
    fun isPrivateSpaceSetUp(
        showToast: Boolean = false,
        launchSettings: Boolean = false
    ): Boolean {
        if (!isPrivateSpaceSupported()) {
            if (showToast) {
                context.showLongToast(getLocalizedString(R.string.alert_requires_android_v))
            }
            return false
        }
        val privateSpaceUser = getPrivateSpaceUser()
        if (privateSpaceUser != null) {
            return true
        }
        if (!ismlauncherDefault(context)) {
            if (showToast) {
                context.showLongToast(getLocalizedString(R.string.toast_private_space_default_home_screen))
            }
            if (launchSettings) {
                (context as MainActivity).setDefaultHomeScreen(context)
            }
        } else {
            if (showToast) {
                context.showLongToast(getLocalizedString(R.string.toast_private_space_not_available))
            }
        }
        return false
    }

    /**
     * Check if the private space is locked.
     */
    fun isPrivateSpaceLocked(): Boolean {
        if (!isPrivateSpaceSupported() || !isPrivateSpaceSetUp()) {
            return false
        }
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val privateSpaceUser = getPrivateSpaceUser() ?: return false
        return userManager.isQuietModeEnabled(privateSpaceUser)
    }

    /**
     * Lock or unlock the private space.
     */
    private fun lockPrivateSpace(lock: Boolean) {
        if (!isPrivateSpaceSupported()) {
            return
        }
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val privateSpaceUser = getPrivateSpaceUser() ?: return
        userManager.requestQuietModeEnabled(lock, privateSpaceUser)
    }

    /**
     * Toggle the lock state of the private space.
     */
    fun togglePrivateSpaceLock(showToast: Boolean, launchSettings: Boolean) {
        if (!isPrivateSpaceSetUp(showToast, launchSettings)) {
            return
        }
        if (!ismlauncherDefault(context)) {
            context.showLongToast(getLocalizedString(R.string.toast_private_space_default_home_screen))
            return
        }

        val lock = isPrivateSpaceLocked()
        lockPrivateSpace(!lock)

        if (showToast) {
            Handler(Looper.getMainLooper()).post {
                if (isPrivateSpaceLocked() == !lock) {
                    context.showLongToast(getLocalizedString(R.string.toast_private_space_locked))
                }
            }
        }
    }
}