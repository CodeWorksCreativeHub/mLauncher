package com.github.droidworksstudio.mlauncher.ui.compose

import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.github.creativecodecat.components.views.FontAppCompatTextView
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.services.HapticFeedbackService
import com.github.droidworksstudio.mlauncher.style.SettingsTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SettingsComposable {

    @Composable
    fun PageHeader(
        @DrawableRes iconRes: Int,
        title: String,
        onClick: () -> Unit = {},
        iconSize: Dp = 24.dp,
        fontColor: Color = SettingsTheme.typography.title.color,
        titleFontSize: TextUnit = TextUnit.Unspecified
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                colorFilter = ColorFilter.tint(SettingsTheme.color.image),
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .size(iconSize)
            )

            Spacer(modifier = Modifier.weight(1f))

            AndroidView(
                factory = {
                    FontAppCompatTextView(it).apply {
                        text = title
                        setTextSize(
                            TypedValue.COMPLEX_UNIT_SP,
                            if (titleFontSize != TextUnit.Unspecified) titleFontSize.value else 18f
                        )
                        setTextColor(fontColor.toArgb()) // Optional
                    }
                },
                modifier = Modifier.wrapContentSize()
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    @Composable
    fun TopMainHeader(
        @DrawableRes iconRes: Int,
        title: String,
        description: String? = null,
        iconSize: Dp = 96.dp,
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        fontColor: Color = SettingsTheme.typography.title.color,
        onIconClick: (() -> Unit)? = null // Optional click callback
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clickable Image
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier
                    .size(iconSize)
                    .padding(bottom = 16.dp)
                    .let { if (onIconClick != null) it.clickable { onIconClick() } else it }
            )

            // FontAppCompatTextView instead of Text
            AndroidView(
                factory = { context ->
                    FontAppCompatTextView(context).apply {
                        text = title
                        setTextSize(
                            TypedValue.COMPLEX_UNIT_SP,
                            if (titleFontSize != TextUnit.Unspecified) titleFontSize.value else 18f
                        )
                        setTextColor(fontColor.toArgb())
                        setPadding(0, 0, 0, 24)
                    }
                },
                modifier = Modifier.wrapContentSize()
            )

            description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                AndroidView(
                    factory = { context ->
                        FontAppCompatTextView(context).apply {
                            text = Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                            setTextColor(fontColor.toArgb())
                            setTextSize(
                                TypedValue.COMPLEX_UNIT_SP,
                                if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize.value else 12f
                            )
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                    },
                    modifier = Modifier.wrapContentHeight()
                )
            }
        }
    }

    @Composable
    fun SettingsHomeItem(
        title: String,
        description: String? = null,
        @DrawableRes iconRes: Int,
        onClick: () -> Unit = {},
        onMultiClick: (Int) -> Unit = {}, // now takes tap count
        enableMultiClick: Boolean = false,
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        fontColor: Color = SettingsTheme.typography.title.color,
        iconSize: Dp = 18.dp,
        multiClickCount: Int = 5,
        multiClickInterval: Long = 2000L
    ) {
        var tapCount by remember { mutableIntStateOf(0) }
        var lastTapTime by remember { mutableLongStateOf(0L) }

        val scope = rememberCoroutineScope()
        var clickJob by remember { mutableStateOf<Job?>(null) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!enableMultiClick) {
                        onClick()
                        return@clickable
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime > multiClickInterval) {
                        tapCount = 0
                    }

                    tapCount++
                    lastTapTime = currentTime
                    clickJob?.cancel()

                    if (tapCount >= multiClickCount) {
                        tapCount = 0
                        onMultiClick(multiClickCount)
                    } else {
                        onMultiClick(tapCount)
                        clickJob = scope.launch {
                            delay(multiClickInterval)
                            if (tapCount == 1) {
                                onClick()
                            }
                            tapCount = 0
                        }
                    }
                }
                .padding(vertical = 16.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier.size(iconSize)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                AndroidView(
                    factory = { context ->
                        FontAppCompatTextView(context).apply {
                            text = title
                            setTextColor(fontColor.toArgb())
                            setTextSize(
                                TypedValue.COMPLEX_UNIT_SP,
                                if (titleFontSize != TextUnit.Unspecified) titleFontSize.value else 18f
                            )
                        }
                    },
                    modifier = Modifier.wrapContentHeight()
                )

                description?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    AndroidView(
                        factory = { context ->
                            FontAppCompatTextView(context).apply {
                                text = it
                                setTextColor(fontColor.toArgb())
                                setTextSize(
                                    TypedValue.COMPLEX_UNIT_SP,
                                    if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize.value else 12f
                                )
                            }
                        },
                        modifier = Modifier.wrapContentHeight()
                    )
                }
            }
        }
    }

    @Composable
    fun TitleWithHtmlLink(
        title: String,
        description: String? = null,
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        titleColor: Color = SettingsTheme.typography.title.color,
        descriptionColor: Color = SettingsTheme.typography.title.color
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { ctx ->
                    FontAppCompatTextView(ctx).apply {
                        text = title
                        setTextColor(titleColor.toArgb())
                        setTextSize(
                            TypedValue.COMPLEX_UNIT_SP,
                            if (titleFontSize != TextUnit.Unspecified) titleFontSize.value else 18f
                        )
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                modifier = Modifier.wrapContentHeight()
            )

            description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                AndroidView(
                    factory = { context ->
                        FontAppCompatTextView(context).apply {
                            text = Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                            setTextColor(descriptionColor.toArgb())
                            setTextSize(
                                TypedValue.COMPLEX_UNIT_SP,
                                if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize.value else 12f
                            )
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                    },
                    modifier = Modifier.wrapContentHeight()
                )
            }
        }
    }

    @Composable
    fun TitleWithHtmlLinks(
        title: String,
        descriptions: List<String> = emptyList(),
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        titleColor: Color = SettingsTheme.typography.title.color,
        descriptionColor: Color = SettingsTheme.typography.title.color,
        columns: Boolean = false // if true, use Column for links
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            AndroidView(
                factory = { ctx ->
                    FontAppCompatTextView(ctx).apply {
                        text = title
                        setTextColor(titleColor.toArgb())
                        setTextSize(
                            TypedValue.COMPLEX_UNIT_SP,
                            if (titleFontSize != TextUnit.Unspecified) titleFontSize.value else 18f
                        )
                    }
                },
                modifier = Modifier.wrapContentHeight()
            )

            if (descriptions.isNotEmpty()) {
//                Spacer(modifier = Modifier.height(4.dp))

                if (columns) {
                    // Use Column layout for links
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        descriptions.forEach { htmlLink ->
                            AndroidView(
                                factory = { ctx ->
                                    FontAppCompatTextView(ctx).apply {
                                        text = Html.fromHtml(htmlLink, Html.FROM_HTML_MODE_LEGACY)
                                        setTextColor(descriptionColor.toArgb())
                                        setTextSize(
                                            TypedValue.COMPLEX_UNIT_SP,
                                            if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize.value else 12f
                                        )
                                        movementMethod = LinkMovementMethod.getInstance()
                                    }
                                },
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    // Default: Row layout for links
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        descriptions.forEach { htmlLink ->
                            AndroidView(
                                factory = { ctx ->
                                    FontAppCompatTextView(ctx).apply {
                                        text = Html.fromHtml(htmlLink, Html.FROM_HTML_MODE_LEGACY)
                                        setTextColor(descriptionColor.toArgb())
                                        setTextSize(
                                            TypedValue.COMPLEX_UNIT_SP,
                                            if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize.value else 12f
                                        )
                                        movementMethod = LinkMovementMethod.getInstance()
                                    }
                                },
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsTitle(
        text: String,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = TextUnit.Unspecified,
        onClick: () -> Unit = {}
    ) {
        val resolvedFontSizeSp = if (fontSize != TextUnit.Unspecified) fontSize.value else 18f
        val fontColor = SettingsTheme.typography.header.color

        AndroidView(
            factory = { context ->
                FontAppCompatTextView(context).apply {
                    this.text = text
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, resolvedFontSizeSp)
                    setTextColor(fontColor.toArgb())

                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick() }

                    // Optional: touch ripple effect
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground, typedValue, true
                    )
                    setBackgroundResource(typedValue.resourceId)
                }
            },
            modifier = modifier
                .padding(start = 16.dp, top = 16.dp)
                .wrapContentSize()
        )
    }

    @Composable
    fun SettingsSwitch(
        text: String,
        fontSize: TextUnit = TextUnit.Unspecified,
        defaultState: Boolean = false,
        onCheckedChange: (Boolean) -> Unit
    ) {
        var isChecked by remember { mutableStateOf(defaultState) }

        // Extract font size and color from theme safely in composable scope
        val resolvedFontSizeSp = if (fontSize != TextUnit.Unspecified) fontSize.value else 14f
        val fontColor = SettingsTheme.typography.title.color

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom font AndroidView
            AndroidView(
                factory = { context ->
                    FontAppCompatTextView(context).apply {
                        this.text = text
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, resolvedFontSizeSp)
                        setTextColor(fontColor.toArgb())
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
            )

            AndroidView(
                factory = { context ->
                    SwitchCompat(context).apply {
                        scaleX = 0.7f
                        scaleY = 0.7f
                        thumbDrawable = ContextCompat.getDrawable(context, R.drawable.shape_switch_thumb)
                        trackDrawable = ContextCompat.getDrawable(context, R.drawable.selector_switch)
                        this.isChecked = isChecked
                        setOnCheckedChangeListener { _, checked ->
                            isChecked = checked
                            onCheckedChange(checked)
                            HapticFeedbackService.trigger(
                                context,
                                if (checked) HapticFeedbackService.EffectType.ON else HapticFeedbackService.EffectType.OFF
                            )
                        }
                    }
                },
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }

    @Composable
    fun SettingsSelect(
        title: String,
        option: String,
        fontSize: TextUnit = 24.sp,
        fontColor: Color = SettingsTheme.typography.title.color,
        onClick: () -> Unit = {},
    ) {
        val fontSizeSp = fontSize.value
        val fontColorInt = fontColor.toArgb()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                factory = { context ->
                    FontAppCompatTextView(context).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                        setTextColor(fontColorInt)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                update = { textView ->
                    textView.text = title
                }
            )

            AndroidView(
                factory = { context ->
                    FontAppCompatTextView(context).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                        setTextColor(fontColorInt)
                        setOnClickListener { onClick() }
                    }
                },
                modifier = Modifier.wrapContentSize(),
                update = { textView ->
                    textView.text = option  // update text on recomposition
                }
            )

        }
    }
}