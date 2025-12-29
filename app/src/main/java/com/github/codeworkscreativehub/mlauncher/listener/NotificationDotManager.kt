package com.github.codeworkscreativehub.mlauncher.listener

import com.github.codeworkscreativehub.common.AppLogger

private const val TAG = "NotificationDotManager"

object NotificationDotManager {

    // Stores notification counts per package
    private val counts = mutableMapOf<String, Int>()

    // Registered listeners
    private val listeners = mutableSetOf<(Map<String, Int>) -> Unit>()

    /** Add or update notification count for a package */
    fun setCount(packageName: String, count: Int) {
        val previous = counts[packageName]
        if (count > 0) {
            counts[packageName] = count
        } else {
            counts.remove(packageName)
        }
        if (previous != count) {
            notifyListeners()
            AppLogger.d(TAG, "Count updated: $packageName -> $count")
        }
    }

    /** Remove notification for a package */
    fun removeCount(packageName: String) {
        if (counts.remove(packageName) != null) {
            notifyListeners()
            AppLogger.d(TAG, "Count removed: $packageName")
        }
    }

    /** Return all current counts */
    fun getAllCounts(): Map<String, Int> = counts.toMap()

    /** Register a listener, immediately notifying it of the current counts */
    fun registerListener(listener: (Map<String, Int>) -> Unit) {
        listeners.add(listener)
        listener(getAllCounts())
        AppLogger.d(TAG, "Listener registered. Total listeners: ${listeners.size}")
    }

    /** Unregister a listener */
    fun unregisterListener(listener: (Map<String, Int>) -> Unit) {
        listeners.remove(listener)
        AppLogger.d(TAG, "Listener unregistered. Remaining: ${listeners.size}")
    }

    /** Notify all listeners with the current counts */
    private fun notifyListeners() {
        val snapshot = getAllCounts()
        listeners.forEach { it(snapshot) }
    }
}
