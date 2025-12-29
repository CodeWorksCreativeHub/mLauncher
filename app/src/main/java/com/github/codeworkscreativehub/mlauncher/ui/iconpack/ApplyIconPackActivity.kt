package com.github.codeworkscreativehub.mlauncher.ui.iconpack

import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.MainViewModel
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.IconCacheTarget
import com.github.codeworkscreativehub.mlauncher.helper.IconPackHelper
import com.github.codeworkscreativehub.mlauncher.helper.utils.AppReloader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

class ApplyIconPackActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val packageName = intent.getStringExtra("packageName").toString()
        val packageClass = intent.getStringExtra("packageClass").toString()
        if (packageClass.isNotEmpty()) {
            // Create a vertical LinearLayout programmatically
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }

            // Create the CheckBoxes
            val checkBoxHome = CheckBox(this).apply {
                text = getLocalizedString(R.string.apply_to_home) // e.g., "Apply to Home"
                isChecked = true // default value
            }

            val checkBoxAppList = CheckBox(this).apply {
                text = getLocalizedString(R.string.apply_to_app_list) // e.g., "Apply to App List"
                isChecked = true // default value
            }

            // Add the CheckBoxes to the layout
            layout.addView(checkBoxHome)
            layout.addView(checkBoxAppList)

            MaterialAlertDialogBuilder(this)
                .setTitle(getLocalizedString(R.string.apply_icon_pack))
                .setMessage(getLocalizedString(R.string.apply_icon_pack_are_you_sure, packageName))
                .setView(layout)
                .setPositiveButton(getLocalizedString(R.string.apply)) { _, _ ->

                    val iconPackType = Constants.IconPacks.Custom
                    val customIconPackType = packageClass

                    if (checkBoxHome.isChecked) {
                        val executor = Executors.newSingleThreadExecutor()
                        executor.execute {
                            IconPackHelper.preloadIcons(this, customIconPackType, IconCacheTarget.HOME)
                        }
                        prefs.iconPackHome = iconPackType
                        viewModel.iconPackHome.value = iconPackType
                        prefs.customIconPackHome = customIconPackType
                        viewModel.customIconPackHome.value = customIconPackType
                    }

                    if (checkBoxAppList.isChecked) {
                        val executor = Executors.newSingleThreadExecutor()
                        executor.execute {
                            IconPackHelper.preloadIcons(this, customIconPackType, IconCacheTarget.APP_LIST)
                        }
                        prefs.iconPackAppList = iconPackType
                        viewModel.iconPackAppList.value = iconPackType
                        prefs.customIconPackAppList = customIconPackType
                        viewModel.customIconPackAppList.value = customIconPackType
                    }

                    AppReloader.restartApp(this)
                }
                .setNegativeButton(getLocalizedString(R.string.cancel)) { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()

        } else {
            finish()
        }
    }
}

