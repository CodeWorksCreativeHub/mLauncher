package com.github.droidworksstudio.mlauncher.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Header colors
val textLightHeader = Color(0xFFB388FF)
val textDarkHeader = Color(0xFF651FFF)

// Base colors
val textLightTop = Color(0xFFFFFFFF) // white
val textDarkTop = Color(0xFF000000)  // black

// Adjusted bottom colors
val textLightBottom = lerp(textLightTop, textDarkTop, 0.2f) // slightly darker
val textDarkBottom = lerp(textDarkTop, textLightTop, 0.2f)   // slightly lighter

// Gray
val textGray = Color(0xFF858585)

// Enabled / Disabled colors
val textEnabled = Color(0xFF98DC9A)  // green
val textDisabled = Color(0xFFFF837D) // red
