package com.github.codeworkscreativehub.common

import android.util.Log
import com.github.codeworkscreativehub.mlauncher.BuildConfig

object AppLogger {
    private const val DEFAULT_TAG = "AppLogger"

    fun v(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    fun wtf(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.wtf(tag, message, throwable)
    }
}
