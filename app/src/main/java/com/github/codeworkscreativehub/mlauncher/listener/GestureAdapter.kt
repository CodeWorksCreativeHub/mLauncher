package com.github.codeworkscreativehub.mlauncher.listener

/**
 * GestureAdapter provides empty implementations for all GestureListener methods.
 * Override only what you need.
 */
abstract class GestureAdapter : GestureManager.GestureListener {
    override fun onShortSwipeLeft() {}
    override fun onLongSwipeLeft() {}
    override fun onShortSwipeRight() {}
    override fun onLongSwipeRight() {}
    override fun onShortSwipeUp() {}
    override fun onLongSwipeUp() {}
    override fun onShortSwipeDown() {}
    override fun onLongSwipeDown() {}
    override fun onLongPress() {}
    override fun onDoubleTap() {}
    override fun onSingleTap() {}
}