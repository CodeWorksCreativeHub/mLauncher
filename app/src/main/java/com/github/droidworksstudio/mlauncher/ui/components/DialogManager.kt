package com.github.droidworksstudio.mlauncher.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.github.creativecodecat.components.views.FontBottomSheetDialogLocked
import com.github.droidworksstudio.common.getCpuBatteryInfo
import com.github.droidworksstudio.common.getLocalizedString
import com.github.droidworksstudio.common.getRamInfo
import com.github.droidworksstudio.common.getSdCardInfo
import com.github.droidworksstudio.common.getStorageInfo
import com.github.droidworksstudio.common.requestRuntimePermission
import com.github.droidworksstudio.mlauncher.MainActivity
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.helper.getDeviceInfo
import com.github.droidworksstudio.mlauncher.helper.hasContactsPermission
import com.github.droidworksstudio.mlauncher.helper.themeDownloadButton
import com.github.droidworksstudio.mlauncher.helper.utils.AppReloader
import com.github.droidworksstudio.mlauncher.helper.wordofthedayDownloadButton
import com.github.droidworksstudio.mlauncher.services.HapticFeedbackService
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DialogManager(val context: Context, val activity: Activity) {

    private lateinit var prefs: Prefs
    val selectedColor: Int = ContextCompat.getColor(context, R.color.colorSelected)

    var backupRestoreBottomSheet: FontBottomSheetDialogLocked? = null

    fun showBackupRestoreBottomSheet() {
        // Dismiss existing bottom sheet if it's showing
        backupRestoreBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        // Create layout programmatically
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Utility function to create a clickable item
        fun createItem(text: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 16f
                setPadding(0, 32, 0, 32)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onClick()
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SAVE
                    )
                    backupRestoreBottomSheet?.dismiss()
                }
            }
        }

        // Add the three items
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_backup_restore_backup)) {
            (activity as MainActivity).createFullBackup()
        })

        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_backup_restore_restore)) {
            (activity as MainActivity).restoreFullBackup()
        })

        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_backup_restore_clear)) {
            confirmClearData()
        })

        // Create and show the bottom sheet
        backupRestoreBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
        }
        backupRestoreBottomSheet?.show() // ✅ Correct method call
    }

    var saveLoadThemeBottomSheet: FontBottomSheetDialogLocked? = null

    fun showSaveLoadThemeBottomSheet() {
        // Dismiss any existing bottom sheet
        saveLoadThemeBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        // Create vertical layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Utility function to create clickable items
        fun createItem(text: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 16f
                setPadding(0, 32, 0, 32)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onClick()
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SAVE
                    )
                    saveLoadThemeBottomSheet?.dismiss()
                }
            }
        }

        // Add Download Option
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_theme_download)) {
            themeDownloadButton(context)
        })

        // Add Export and Import options
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_theme_export)) {
            (activity as MainActivity).createThemeBackup()
        })

        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_theme_import)) {
            (activity as MainActivity).restoreThemeBackup()
        })

        // Create and show the LockedBottomSheetDialog
        saveLoadThemeBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
        }
        saveLoadThemeBottomSheet?.show()
    }

    var saveDownloadWOTDBottomSheet: FontBottomSheetDialogLocked? = null

    fun showSaveDownloadWOTDBottomSheet() {
        // Dismiss any existing bottom sheet
        saveDownloadWOTDBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        // Create vertical layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Utility function to create clickable items
        fun createItem(text: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 16f
                setPadding(0, 32, 0, 32)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onClick()
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SAVE
                    )
                    saveDownloadWOTDBottomSheet?.dismiss()
                }
            }
        }

        // Add Download Option
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_wotd_download)) {
            wordofthedayDownloadButton(context)
        })

        // Add Import options
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_wotd_import)) {
            (activity as MainActivity).restoreWordsBackup()
        })

        // Create and show the LockedBottomSheetDialog
        saveDownloadWOTDBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
        }
        saveDownloadWOTDBottomSheet?.show()
    }


    // Function to handle the Clear Data action, with a confirmation dialog
    private fun confirmClearData() {
        MaterialAlertDialogBuilder(context)
            .setTitle(getLocalizedString(R.string.advanced_settings_backup_restore_clear_title))
            .setMessage(getLocalizedString(R.string.advanced_settings_backup_restore_clear_description))
            .setPositiveButton(getLocalizedString(R.string.advanced_settings_backup_restore_clear_yes)) { _, _ ->
                clearData()
            }
            .setNegativeButton(getLocalizedString(R.string.advanced_settings_backup_restore_clear_no), null)
            .show()
    }

    private fun clearData() {
        prefs = Prefs(context)
        prefs.clear()

        AppReloader.restartApp(context)
    }

    var sliderBottomSheet: FontBottomSheetDialogLocked? = null

    fun showSliderBottomSheet(
        context: Context,
        title: String,
        minValue: Number,
        maxValue: Number,
        currentValue: Number,
        steps: Int = 100,
        onValueSelected: (Number) -> Unit
    ) {
        // Dismiss any existing sheet
        sliderBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        // Determine float mode
        val isFloat = minValue is Float || maxValue is Float || currentValue is Float
        val scaleFactor = if (isFloat) steps else 1

        val scaledMin = (minValue.toFloat() * scaleFactor).toInt()
        val scaledMax = (maxValue.toFloat() * scaleFactor).toInt()
        var scaledCurrent = (currentValue.toFloat() * scaleFactor).toInt()

        // Outer layout
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        val titleText = TextView(context).apply {
            text = title
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        // Value display
        val valueText = TextView(context).apply {
            text = if (isFloat) currentValue.toFloat().toString() else currentValue.toInt().toString()
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        fun commitValue() {
            val value = if (isFloat) {
                scaledCurrent.toFloat() / scaleFactor
            } else {
                scaledCurrent
            }
            valueText.text = value.toString()
            onValueSelected(value)
        }

        // SeekBar
        val seekBar = SeekBar(context).apply {
            min = scaledMin
            max = scaledMax
            progress = scaledCurrent
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    scaledCurrent = progress
                    val value = if (isFloat) scaledCurrent.toFloat() / scaleFactor else scaledCurrent
                    valueText.text = value.toString()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    commitValue()
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SELECT
                    )
                }
            })
        }

        fun createCircleButton(
            context: Context,
            iconRes: Int
        ): AppCompatImageButton {

            val sizeDp = 20
            val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
            val paddingDp = 2
            val paddingPx = (paddingDp * context.resources.displayMetrics.density).toInt()

            return AppCompatImageButton(context).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE

                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginStart = 4
                    marginEnd = 4
                }

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(context, R.color.buttonTextPrimary))
                }

                // 👇 space around the icon
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

                // 👇 required for tiny buttons
                minimumWidth = 0
                minimumHeight = 0
            }
        }

        // Usage
        val minusButton = createCircleButton(context, R.drawable.ic_minus).apply {
            setOnClickListener {
                if (scaledCurrent > scaledMin) {
                    scaledCurrent--
                    seekBar.progress = scaledCurrent
                    commitValue()
                }
            }
        }

        val plusButton = createCircleButton(context, R.drawable.ic_plus).apply {
            setOnClickListener {
                if (scaledCurrent < scaledMax) {
                    scaledCurrent++
                    seekBar.progress = scaledCurrent
                    commitValue()
                }
            }
        }


        // Horizontal layout for buttons + slider
        val sliderLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(minusButton)
            addView(seekBar)
            addView(plusButton)
        }

        // Build layout
        container.addView(titleText)
        container.addView(valueText)
        container.addView(sliderLayout)

        // Show bottom sheet
        sliderBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(container)
            show()
        }
    }

    var singleChoiceBottomSheet: FontBottomSheetDialogLocked? = null

    fun <T> showSingleChoiceBottomSheet(
        context: Context,
        options: Array<T>,
        title: String,
        fonts: List<Typeface>? = null,
        fontSize: Float = 18f,
        selectedIndex: Int = -1,            // <- NEW
        onItemSelected: (T) -> Unit
    ) {
        singleChoiceBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        val itemStrings = options.map {
            when (it) {
                is Enum<*> -> it.name
                else -> it.toString()
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 20f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        val listView = ListView(context).apply {
            divider = null
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            choiceMode = ListView.CHOICE_MODE_SINGLE
        }

        // Mutable var so we can update highlight after a tap (even though we dismiss right away)
        var currentSelected = selectedIndex
        val defaultColor = TypedValue().let { value ->
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, value, true)
            ContextCompat.getColor(context, value.resourceId)
        }

        val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, itemStrings) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: TextView(context).apply {
                    setPadding(32, 24, 32, 24)
                    layoutParams = AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                (view as TextView).apply {
                    text = itemStrings[position]
                    typeface = fonts?.getOrNull(position) ?: Typeface.DEFAULT
                    textSize = fontSize
                    setTextColor(if (position == currentSelected) selectedColor else defaultColor)
                }
                return view
            }
        }

        listView.adapter = adapter
        if (currentSelected in itemStrings.indices) listView.setItemChecked(currentSelected, true)

        container.addView(titleView)
        container.addView(listView)

        singleChoiceBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(container)
            show()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            currentSelected = position          // update highlight if you keep dialog open
            adapter.notifyDataSetChanged()
            onItemSelected(options[position])   // callback
            HapticFeedbackService.trigger(
                context,
                HapticFeedbackService.EffectType.SELECT
            )
            singleChoiceBottomSheet?.dismiss()  // close sheet
        }

        // Limit visible height to 7 items
        listView.post {
            val itemHeight = listView.getChildAt(0)?.height ?: 0
            if (itemHeight > 0) {
                val maxHeight = itemHeight * 7
                listView.layoutParams.height = minOf(maxHeight, itemHeight * options.size)
                listView.requestLayout()
            }
        }
    }

    var singleChoiceBottomSheetPill: FontBottomSheetDialogLocked? = null

    fun <T> showSingleChoiceBottomSheetPill(
        context: Context,
        options: Array<T>,
        title: String,
        fonts: List<Typeface>? = null,
        fontSize: Float = 18f,
        selectedIndex: Int = -1, // None selected
        onItemSelected: (T) -> Unit
    ) {
        singleChoiceBottomSheetPill?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        val itemStrings = options.map {
            when (it) {
                is Enum<*> -> it.name
                else -> it.toString()
            }
        }

        var selected = selectedIndex

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 20f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        val pillGroup = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,  // changed here
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val strokeColor = R.color.colorAccent
        val textColor = Color.WHITE

        val updateStyles: () -> Unit = {
            for (i in 0 until pillGroup.childCount) {
                val pill = pillGroup.getChildAt(i) as TextView
                val isSelected = i == selected
                pill.setTextColor(if (isSelected) textColor else context.resources.getColor(strokeColor, null))

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = when (i) {
                        0 -> floatArrayOf(48f, 48f, 0f, 0f, 0f, 0f, 48f, 48f)
                        itemStrings.lastIndex -> floatArrayOf(0f, 0f, 48f, 48f, 48f, 48f, 0f, 0f)
                        else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                    }
                    setColor(if (isSelected) selectedColor else Color.TRANSPARENT)
                    setStroke(3, context.resources.getColor(strokeColor, null))
                }

                pill.background = drawable
            }
        }

        itemStrings.forEachIndexed { index, item ->
            val pill = TextView(context).apply {
                text = item
                textSize = fontSize
                gravity = Gravity.CENTER
                typeface = fonts?.getOrNull(index) ?: Typeface.DEFAULT
                setPadding(48, 24, 48, 24)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index != 0) setMargins(-3, 0, 0, 0) // eliminate gap between borders
                }

                setOnClickListener {
                    selected = index
                    updateStyles()
                    onItemSelected(options[index])
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SELECT
                    )
                    singleChoiceBottomSheetPill?.dismiss()
                }
            }

            pillGroup.addView(pill)
        }

        updateStyles() // Set initial styles

        container.addView(titleView)
        container.addView(pillGroup)

        singleChoiceBottomSheetPill = FontBottomSheetDialogLocked(context).apply {
            setContentView(container)
            show()
        }
    }

    var flagSettingsBottomSheet: FontBottomSheetDialogLocked? = null

    fun showFlagSettingsBottomSheet(
        context: Context,
        optionLabels: List<String>,
        settingFlags: String,
        default: String = "0",
        onFlagsChanged: (List<Boolean>) -> Unit
    ) {
        flagSettingsBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        val prefs = Prefs(context)
        val currentFlags = prefs.getMenuFlags(settingFlags, default).toMutableList()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        // Adjust the size of currentFlags to match optionLabels
        if (currentFlags.size < optionLabels.size) {
            currentFlags.addAll(List(optionLabels.size - currentFlags.size) { false })
        } else if (currentFlags.size > optionLabels.size) {
            currentFlags.subList(optionLabels.size, currentFlags.size).clear()
        }

        optionLabels.forEachIndexed { index, label ->
            val checkBox = CheckBox(context).apply {
                text = label
                isChecked = currentFlags[index]
                setOnCheckedChangeListener { _, isChecked ->
                    currentFlags[index] = isChecked
                    prefs.saveMenuFlags(settingFlags, currentFlags)
                    onFlagsChanged(currentFlags.toList()) // ✅ notify Compose with new data
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SELECT
                    )
                    if (label == context.getString(R.string.applist_button_contacts) && isChecked) {
                        if (!hasContactsPermission(context)) {
                            context.requestRuntimePermission(
                                arrayOf(Manifest.permission.READ_CONTACTS),
                                Constants.READ_CONTACTS,
                                "Contacts"
                            )
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                }
            }
            layout.addView(checkBox)
        }


        flagSettingsBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
        }
        flagSettingsBottomSheet?.show()
    }


    var colorPickerBottomSheet: FontBottomSheetDialogLocked? = null

    fun showColorPickerBottomSheet(
        context: Context,
        title: String,
        color: Int,
        onItemSelected: (Int) -> Unit
    ) {
        colorPickerBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        var isUpdatingText = false

        // Title
        val titleTextView = TextView(context).apply {
            text = title
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 16)
        }

        // SeekBars
        val redSeekBar = createColorSeekBar(context, red)
        val greenSeekBar = createColorSeekBar(context, green)
        val blueSeekBar = createColorSeekBar(context, blue)

        // Color preview
        val colorPreviewBox = createColorPreviewBox(context, color)

        // Hex input
        val rgbText = createRgbTextField(context, red, green, blue)

        // Layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Horizontal layout for text and preview
        val horizontalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 32)

            val rgbParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 16
            }
            rgbText.layoutParams = rgbParams
            addView(rgbText)

            val previewParams = LinearLayout.LayoutParams(150, 50)
            colorPreviewBox.layoutParams = previewParams
            addView(colorPreviewBox)
        }

        // Add all views to layout
        layout.addView(titleTextView)
        layout.addView(redSeekBar)
        layout.addView(greenSeekBar)
        layout.addView(blueSeekBar)
        layout.addView(horizontalLayout)

        // Shared update function
        val updateColor = {
            val updatedColor = Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
            colorPreviewBox.setBackgroundColor(updatedColor)

            if (!isUpdatingText) {
                isUpdatingText = true
                rgbText.setText(String.format("#%02X%02X%02X", redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress))
                isUpdatingText = false
            }

            // Auto-save
            onItemSelected(updatedColor)
            HapticFeedbackService.trigger(
                context,
                HapticFeedbackService.EffectType.SELECT
            )
        }

        // Listeners
        redSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColor))
        greenSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColor))
        blueSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColor))

        rgbText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingText) return
                s?.toString()?.trim()?.let { colorString ->
                    if (colorString.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                        val hexColor = try {
                            colorString.toColorInt()
                        } catch (_: Exception) {
                            return
                        }
                        redSeekBar.progress = Color.red(hexColor)
                        greenSeekBar.progress = Color.green(hexColor)
                        blueSeekBar.progress = Color.blue(hexColor)
                        updateColor()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Show bottom sheet
        colorPickerBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
            show()
        }
    }

    private fun createColorSeekBar(context: Context, initialValue: Int): SeekBar {
        return SeekBar(context).apply {
            max = 255
            progress = initialValue
        }
    }

    private fun createColorPreviewBox(context: Context, color: Int): View {
        return View(context).apply {
            setBackgroundColor(color)
        }
    }

    private fun createRgbTextField(context: Context, red: Int, green: Int, blue: Int): EditText {
        return EditText(context).apply {
            setText(String.format("#%02X%02X%02X", red, green, blue))
            inputType = InputType.TYPE_CLASS_TEXT

            // Remove the bottom line (underline) from the EditText
            background = null
        }
    }

    private fun createSeekBarChangeListener(updateColorPreview: () -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColorPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    var showDeviceBottomSheet: FontBottomSheetDialogLocked? = null
    fun showDeviceStatsBottomSheet(context: Context) {
        showDeviceBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.DEFAULT
        )

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val infoCards = listOf(
            "Device Info" to getDeviceInfo(context),
            "Storage" to getStorageInfo(),
            "RAM" to context.getRamInfo(),
            "CPU & Battery" to context.getCpuBatteryInfo(),
            "SD Card" to context.getSdCardInfo()
        )

        var i = 0
        while (i < infoCards.size) {
            val remaining = infoCards.size - i

            if (remaining >= 3) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 24
                    }
                }

                // Left big card container
                val leftContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 24, 24, 24)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        rightMargin = 16
                    }
                }
                val (leftTitle, leftDetails) = infoCards[i]
                leftContainer.addView(TextView(context).apply {
                    text = leftTitle
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    textSize = 18f
                })
                leftContainer.addView(TextView(context).apply {
                    text = leftDetails
                    setTextColor(Color.LTGRAY)
                    textSize = 14f
                    setPadding(0, 8, 0, 0)
                })

                // Right stacked cards container
                val rightContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                for (j in 1..2) {
                    val (title, details) = infoCards[i + j]
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 24, 24, 24)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (j == 1) bottomMargin = 16
                        }
                    }
                    card.addView(TextView(context).apply {
                        text = title
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        textSize = 18f
                    })
                    card.addView(TextView(context).apply {
                        text = details
                        setTextColor(Color.LTGRAY)
                        textSize = 14f
                        setPadding(0, 8, 0, 0)
                    })

                    rightContainer.addView(card)
                }

                row.addView(leftContainer)
                row.addView(rightContainer)
                rootLayout.addView(row)

                // Equalize heights of left and right containers
                row.post {
                    val leftHeight = leftContainer.height
                    val rightHeight = rightContainer.height
                    val maxHeight = maxOf(leftHeight, rightHeight)

                    if (leftHeight != maxHeight || rightHeight != maxHeight) {
                        leftContainer.layoutParams.height = maxHeight
                        rightContainer.layoutParams.height = maxHeight
                        row.requestLayout()
                    }
                }

                i += 3
            } else {
                // Handle fewer than 3 cards normally
                val rowItems = infoCards.subList(i, infoCards.size)
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 24
                    }
                }

                rowItems.forEachIndexed { index, (title, details) ->
                    val isSingleCard = rowItems.size == 1

                    val cardLayoutParams = if (isSingleCard) {
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            if (index == 0) rightMargin = 16
                        }
                    }

                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 24, 24, 24)
                        layoutParams = cardLayoutParams
                    }

                    card.addView(TextView(context).apply {
                        text = title
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        textSize = 18f
                    })

                    card.addView(TextView(context).apply {
                        text = details
                        setTextColor(Color.LTGRAY)
                        textSize = 14f
                        setPadding(0, 8, 0, 0)
                    })

                    row.addView(card)
                }

                rootLayout.addView(row)
                break
            }
        }

        showDeviceBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(rootLayout)
            show()
        }
    }
}