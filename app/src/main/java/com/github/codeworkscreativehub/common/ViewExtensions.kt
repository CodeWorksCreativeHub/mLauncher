package com.github.codeworkscreativehub.common

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.github.codeworkscreativehub.mlauncher.listener.GestureManager


fun View.showKeyboard() {
    if (this.requestFocus()) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Show the soft keyboard
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

fun View.hideKeyboard() {
    val imm: InputMethodManager? =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    imm?.hideSoftInputFromWindow(windowToken, 0)
    this.clearFocus()
}

@SuppressLint("ClickableViewAccessibility")
fun View.attachGestureManager(context: Context, listener: GestureManager.GestureListener) {
    val gestureManager = GestureManager(context, listener)
    this.setOnTouchListener { _, event -> gestureManager.onTouchEvent(event) }
}