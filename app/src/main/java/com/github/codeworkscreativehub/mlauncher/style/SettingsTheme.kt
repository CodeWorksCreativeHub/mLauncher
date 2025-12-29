package com.github.codeworkscreativehub.mlauncher.style

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.codeworkscreativehub.mlauncher.R

@Immutable
data class ReplacementTypography(
    val header: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val option: TextStyle,
    val button: TextStyle,
    val buttonDisabled: TextStyle,
    val textEnabled: TextStyle = TextStyle.Default,
    val textDisabled: TextStyle = TextStyle.Default,
)

@Immutable
data class ReplacementColor(
    val settings: Color,
    val image: Color,
    val selector: Color,
    val border: Color,
)

val LocalReplacementTypography = staticCompositionLocalOf {
    ReplacementTypography(
        header = TextStyle.Default,
        title = TextStyle.Default,
        body = TextStyle.Default,
        option = TextStyle.Default,
        button = TextStyle.Default,
        buttonDisabled = TextStyle.Default,
        textEnabled = TextStyle.Default,
        textDisabled = TextStyle.Default,
    )
}
val LocalReplacementColor = staticCompositionLocalOf {
    ReplacementColor(
        settings = Color.Unspecified,
        image = Color.Unspecified,
        selector = Color.Unspecified,
        border = Color.Unspecified,
    )
}

@Composable
fun SettingsTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val replacementTypography = ReplacementTypography(
        header = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 16.sp,
            color = if (isDark) textLightHeader else textDarkHeader,
        ),
        title = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 32.sp,
            color = if (isDark) textLightTop else textDarkTop,
        ),
        body = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 16.sp,
            color = if (isDark) textLightTop else textDarkTop,
        ),
        option = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 32.sp,
            color = if (isDark) textLightBottom else textDarkBottom,
        ),
        button = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (isDark) textLightTop else textDarkTop,
        ),
        buttonDisabled = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = textGray,
        ),
        textEnabled = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 16.sp,
            color = textEnabled,
        ),
        textDisabled = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 16.sp,
            color = textDisabled,
        ),
    )
    val replacementColor = ReplacementColor(
        settings = colorResource(if (isDark) R.color.blackTrans50 else R.color.blackInverseTrans50),
        image = if (isDark) Color.LightGray else Color.DarkGray,
        selector = colorResource(if (isDark) R.color.blackTrans50 else R.color.blackInverseTrans50),
        border = colorResource(if (isDark) R.color.blackInverseTrans25 else R.color.whiteInverseTrans25),
    )
    CompositionLocalProvider(
        LocalReplacementTypography provides replacementTypography,
        LocalReplacementColor provides replacementColor,
    ) {
        MaterialTheme(
            content = content
        )
    }
}

object SettingsTheme {
    val typography: ReplacementTypography
        @Composable
        get() = LocalReplacementTypography.current

    val color: ReplacementColor
        @Composable
        get() = LocalReplacementColor.current
}
