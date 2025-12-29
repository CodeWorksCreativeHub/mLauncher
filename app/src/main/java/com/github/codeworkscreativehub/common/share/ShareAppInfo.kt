package com.github.codeworkscreativehub.common.share

import android.content.Intent
import android.graphics.drawable.Drawable

data class ShareAppInfo(
    val label: String,
    val icon: Drawable,
    val launchIntent: Intent
)
