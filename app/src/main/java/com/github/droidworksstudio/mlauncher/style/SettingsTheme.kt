package com.github.droidworksstudio.mlauncher.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.droidworksstudio.mlauncher.R

@Immutable
data class ReplacementTypography(
    val body: TextStyle,
    val title: TextStyle,
    val item: TextStyle,
    val button: TextStyle,
    val buttonDisabled: TextStyle,
)

@Immutable
data class ReplacementShapes(
    val settings: Shape,
)

@Immutable
data class ReplacementColor(
    val settings: Color,
    val selector: Color,
    val border: Color,
)

val LocalReplacementTypography = staticCompositionLocalOf {
    ReplacementTypography(
        body = TextStyle.Default,
        title = TextStyle.Default,
        item = TextStyle.Default,
        button = TextStyle.Default,
        buttonDisabled = TextStyle.Default,
    )
}
val LocalReplacementShapes = staticCompositionLocalOf {
    ReplacementShapes(
        settings = RoundedCornerShape(ZeroCornerSize)
    )
}
val LocalReplacementColor = staticCompositionLocalOf {
    ReplacementColor(
        settings = Color.Unspecified,
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
        body = TextStyle(
            fontSize = 16.sp
        ),
        title = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 32.sp,
            color = if (isDark) textLight else textDark,
        ),
        item = TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 16.sp,
            color = if (isDark) textLight else textDark,
        ),
        button = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (isDark) textLight else textDark,
        ),
        buttonDisabled = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = textGray,
        ),
    )
    val replacementShapes = ReplacementShapes(
        settings = RoundedCornerShape(CORNER_RADIUS),
    )
    val replacementColor = ReplacementColor(
        settings = colorResource( if (isDark) R.color.blackTrans50 else R.color.blackInverseTrans50 ),
        selector = colorResource( if (isDark) R.color.blackTrans50 else R.color.blackInverseTrans50 ),
        border = colorResource( if (isDark) R.color.blackInverseTrans25 else R.color.whiteInverseTrans25 ),
    )
    CompositionLocalProvider(
        LocalReplacementTypography provides replacementTypography,
        LocalReplacementShapes provides replacementShapes,
        LocalReplacementColor provides replacementColor,
    ) {
        MaterialTheme(
            content = content
        )
    }
}

// Use with eg. ReplacementTheme.typography.body
object SettingsTheme {
    val typography: ReplacementTypography
        @Composable
        get() = LocalReplacementTypography.current

    val shapes: ReplacementShapes
        @Composable
        get() = LocalReplacementShapes.current

    val color: ReplacementColor
        @Composable
        get() = LocalReplacementColor.current
}
