package com.github.codeworkscreativehub.mlauncher.ui.compose

import android.content.Intent
import android.text.Html
import android.text.style.URLSpan
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.helper.FontManager
import com.github.codeworkscreativehub.mlauncher.services.HapticFeedbackService
import com.github.codeworkscreativehub.mlauncher.style.SettingsTheme
import com.github.creativecodecat.components.views.FontAppCompatTextView
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
        titleFontSize: TextUnit = 18.sp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon on the left
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                colorFilter = ColorFilter.tint(SettingsTheme.color.image),
                modifier = Modifier
                    .size(iconSize)
                    .clickable(onClick = onClick)
            )

            Spacer(modifier = Modifier.width(12.dp)) // small spacing between icon and title

            // Title text
            FontText(
                text = title,
                fontSize = titleFontSize,
                color = fontColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f) // take remaining space
            )
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

            FontText(
                text = title,
                fontSize = if (titleFontSize != TextUnit.Unspecified) titleFontSize else 18.sp,
                color = fontColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .wrapContentSize()
            )


            description?.let {
                Spacer(modifier = Modifier.height(2.dp))

                ClickableHtmlText(
                    html = it,
                    color = fontColor,
                    fontSize = if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize else 12.sp,
                    modifier = Modifier.wrapContentHeight(),
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
        onMultiClick: (Int) -> Unit = {},
        enableMultiClick: Boolean = false,
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        headerColor: Color = SettingsTheme.typography.title.color,
        optionColor: Color = SettingsTheme.typography.option.color,
        iconSize: Dp = 18.dp,
        multiClickCount: Int = 5,
        multiClickInterval: Long = 2000L
    ) {
        val scope = rememberCoroutineScope()
        val multiClickState = remember {
            object {
                var tapCount = 0
                var lastTapTime = 0L
                var clickJob: Job? = null
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!enableMultiClick) {
                        onClick()
                        return@clickable
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - multiClickState.lastTapTime > multiClickInterval) {
                        multiClickState.tapCount = 0
                    }

                    multiClickState.tapCount++
                    multiClickState.lastTapTime = currentTime
                    multiClickState.clickJob?.cancel()

                    if (multiClickState.tapCount >= multiClickCount) {
                        multiClickState.tapCount = 0
                        onMultiClick(multiClickCount)
                    } else {
                        onMultiClick(multiClickState.tapCount)
                        multiClickState.clickJob = scope.launch {
                            delay(multiClickInterval)
                            if (multiClickState.tapCount == 1) onClick()
                            multiClickState.tapCount = 0
                        }
                    }
                }
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier.size(iconSize)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                FontText(
                    text = title,
                    color = headerColor,
                    fontSize = if (titleFontSize != TextUnit.Unspecified) titleFontSize else 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.wrapContentHeight()
                )

                description?.let {
                    Spacer(modifier = Modifier.height(1.dp))
                    FontText(
                        text = it,
                        color = optionColor,
                        fontSize = if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize else 12.sp,
                        modifier = Modifier.wrapContentHeight()
                    )
                }
            }
        }
    }

    @Composable
    fun TitleWithHtmlLinks(
        title: String,
        descriptions: List<String> = emptyList(),
        titleFontSize: TextUnit = 18.sp,
        descriptionFontSize: TextUnit = 12.sp,
        titleColor: Color = SettingsTheme.typography.title.color,
        descriptionColor: Color = SettingsTheme.typography.title.color,
        columns: Boolean = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            FontText(
                text = title,
                color = titleColor,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.wrapContentHeight()
            )

            if (descriptions.isNotEmpty()) {
                val layoutModifier = Modifier
                    .wrapContentHeight()
                    .padding(vertical = if (columns) 2.dp else 0.dp, horizontal = if (!columns) 12.dp else 0.dp)

                if (columns) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(4.dp))
                        descriptions.forEach { htmlString ->
                            ClickableHtmlText(
                                html = htmlString,
                                color = descriptionColor,
                                fontSize = descriptionFontSize,
                                modifier = layoutModifier
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        descriptions.forEach { htmlString ->
                            ClickableHtmlText(
                                html = htmlString,
                                color = descriptionColor,
                                fontSize = descriptionFontSize,
                                modifier = layoutModifier
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun ClickableHtmlText(
        html: String,
        color: Color,
        fontSize: TextUnit,
        modifier: Modifier = Modifier,
        underlineLinks: Boolean = true // optional parameter
    ) {
        val context = LocalContext.current
        val spanned = remember(html) { Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) }

        val annotatedString = buildAnnotatedString {
            append(spanned.toString())
            val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
            urlSpans.forEach { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)

                addStyle(
                    SpanStyle(
                        color = color, // same as normal text
                        textDecoration = if (underlineLinks) TextDecoration.Underline else TextDecoration.None
                    ),
                    start,
                    end
                )
                addStringAnnotation(tag = "URL", annotation = span.url, start = start, end = end)
            }
        }

        val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

        FontText(
            text = annotatedString,
            style = TextStyle(color = color, fontSize = fontSize),
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val result = layoutResult.value ?: return@detectTapGestures
                    val position = result.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations(tag = "URL", start = position, end = position)
                        .firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                            context.startActivity(intent)
                        }
                }
            },
            onTextLayout = { layoutResult.value = it }
        )
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
                        R.attr.selectableItemBackground, typedValue, true
                    )
                    setBackgroundResource(typedValue.resourceId)
                }
            },
            modifier = modifier
                .padding(horizontal = 16.dp)
                .wrapContentSize()
        )
    }

    @Composable
    fun SettingsSwitch(
        text: String,
        fontSize: TextUnit = 14.sp,
        titleColor: Color = SettingsTheme.typography.title.color,
        defaultState: Boolean = false,
        onCheckedChange: (Boolean) -> Unit
    ) {
        var isChecked by remember { mutableStateOf(defaultState) }
        // Extract font size and color from theme safely in composable scope
        val resolvedFontSizeSp = if (fontSize != TextUnit.Unspecified) fontSize.value else 16f
        val context = LocalContext.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isChecked = !isChecked
                    onCheckedChange(isChecked)

                    HapticFeedbackService.trigger(
                        context,
                        if (isChecked)
                            HapticFeedbackService.EffectType.ON
                        else
                            HapticFeedbackService.EffectType.OFF
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Label
            FontText(
                text = text,
                fontSize = resolvedFontSizeSp.sp,
                color = titleColor,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
            )

            // Custom switch
            CustomSwitch(
                checked = isChecked
            )
        }
    }


    @Composable
    fun CustomSwitch(
        checked: Boolean,
        modifier: Modifier = Modifier
    ) {
        val thumbSize = 14.dp
        val trackWidth = 32.dp
        val trackHeight = 16.dp

        val thumbOffset by animateDpAsState(
            targetValue = if (checked) trackWidth - thumbSize - 2.dp else 2.dp,
            label = "thumbOffset"
        )

        Box(
            modifier = modifier
                .size(width = trackWidth, height = trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(
                    if (checked)
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF4CAF73),
                                Color(0xFF2E8B57)
                            )
                        )
                    else
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFD6D6D6),
                                Color(0xFFD6D6D6)
                            )
                        )

                )
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            )
        }
    }


    @Composable
    fun SettingsSelect(
        title: String,
        option: String,
        fontSize: TextUnit = 24.sp,
        titleColor: Color = SettingsTheme.typography.title.color,
        optionColor: Color = SettingsTheme.typography.option.color,
        onClick: () -> Unit = {},
    ) {
        val fontSizeSp = fontSize.value

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp)
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Title
            FontText(
                text = title,
                fontSize = fontSizeSp.sp,
                color = titleColor,
                modifier = Modifier.wrapContentHeight()
            )

            // Option / secondary text
            FontText(
                text = option,
                fontSize = (fontSizeSp / 1.3f).sp,
                color = optionColor,
                modifier = Modifier.wrapContentHeight()
            )
        }
    }

    @Composable
    fun FontText(
        text: Any, // String or AnnotatedString
        modifier: Modifier = Modifier,
        fontSize: TextUnit = 16.sp,
        color: Color = SettingsTheme.typography.title.color,
        fontWeight: FontWeight? = null,
        style: TextStyle? = null, // Optional additional style
        textAlign: TextAlign? = null,
        onClick: (() -> Unit)? = null,
        onTextLayout: ((TextLayoutResult) -> Unit)? = null
    ) {
        val context = LocalContext.current

        // Get Typeface from FontManager (like your FontEditText)
        val typeface = remember { FontManager.getTypeface(context) }

        // Convert Typeface to Compose FontFamily
        val fontFamily: FontFamily = remember(typeface) {
            typeface?.let { FontFamily(it) } ?: FontFamily.Default
        }

        val finalStyle = (style ?: TextStyle()).copy(
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        )

        val clickableModifier = if (onClick != null) {
            Modifier.clickable(
                onClick = onClick,
            )
        } else Modifier

        when (text) {
            is String -> Text(
                text = text,
                modifier = modifier.then(clickableModifier),
                style = finalStyle,
                textAlign = textAlign,
                onTextLayout = onTextLayout // nullable is fine for String
            )

            is AnnotatedString -> Text(
                text = text,
                modifier = modifier.then(clickableModifier),
                style = finalStyle,
                textAlign = textAlign,
                onTextLayout = onTextLayout ?: {} // must be non-null for AnnotatedString
            )

            else -> throw IllegalArgumentException("FontText supports only String or AnnotatedString")
        }
    }

}