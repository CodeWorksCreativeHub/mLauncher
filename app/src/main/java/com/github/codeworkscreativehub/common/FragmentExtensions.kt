package com.github.codeworkscreativehub.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import com.github.codeworkscreativehub.mlauncher.R

fun Fragment.showLongToast(message: String) {
    showCustomToast(this, message, iconRes = R.drawable.ic_toast, delayMillis = 3000L)
}

fun Fragment.showShortToast(message: String) {
    showCustomToast(this, message, iconRes = R.drawable.ic_toast, delayMillis = 2000L)
}

fun Fragment.showInstantToast(message: String) {
    showCustomToast(this, message, iconRes = R.drawable.ic_toast)
}

fun showCustomToast(
    fragment: Fragment,
    message: String,
    @DrawableRes iconRes: Int? = null,
    delayMillis: Long = 500L // Optional delay time before hiding
) {
    val rootView = fragment.requireActivity().window.decorView as ViewGroup

    // Helper to convert dp to pixels
    fun Int.dp(): Int =
        (this * fragment.resources.displayMetrics.density).toInt()

    // Create the overlay container
    val overlay = LinearLayout(fragment.requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(Color.DKGRAY)
        setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        alpha = 0f
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 150.dp() }
    }

    // Optional icon
    iconRes?.let {
        val iconView = AppCompatImageView(fragment.requireContext()).apply {
            setImageResource(it)
            layoutParams = LinearLayout.LayoutParams(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    26f,
                    resources.displayMetrics
                ).toInt(),
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    26f,
                    resources.displayMetrics
                ).toInt()
            ).apply {
                val margin = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8f,
                    resources.displayMetrics
                ).toInt()
                setMargins(margin, 0, margin, 0)
            }

            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            contentDescription = getLocalizedString(R.string.show)
            elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                6f,
                resources.displayMetrics
            )
        }
        overlay.addView(iconView)
    }


    // Message text
    overlay.addView(TextView(fragment.requireContext()).apply {
        text = message
        setTextColor(Color.WHITE)
        textSize = 14f
    })

    // Show overlay with animation and auto-dismiss
    rootView.addView(overlay)
    overlay.animate()
        .alpha(1f)
        .setDuration(100)
        .withEndAction {
            overlay.animate()
                .alpha(0f)
                .setDuration(300)
                .setStartDelay(delayMillis) // 🕒 uses passed delay
                .withEndAction {
                    rootView.removeView(overlay)
                }
                .start()
        }
        .start()
}


@SuppressLint("ServiceCast")
fun Fragment.hideKeyboard() {
    val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val view = activity?.currentFocus ?: View(requireContext()) // Use current focus or a new view
    if (inputMethodManager.isActive) { // Check if IME is active before hiding
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
