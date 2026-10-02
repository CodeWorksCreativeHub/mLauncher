package com.github.codeworkscreativehub.mlauncher.ui.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showShortToast
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.data.SHORTCUT_PREFIX
import com.github.codeworkscreativehub.mlauncher.data.settingsKeyOf
import com.github.codeworkscreativehub.mlauncher.helper.ShortcutHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Receives "Add to home screen" requests (ACTION_CONFIRM_PIN_SHORTCUT),
 * e.g. from a browser, and lets the user pick the name before adding it.
 */
class PinShortcutActivity : AppCompatActivity() {

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        val shortcut = request?.shortcutInfo

        if (request == null || !request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || shortcut == null) {
            AppLogger.w(TAG, "Ignoring invalid pin request")
            finish()
            return
        }

        val defaultLabel = (shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id).toString()

        val nameInput = EditText(this).apply {
            setText(defaultLabel)
            setSelection(text.length)
            hint = getLocalizedString(R.string.shortcut_name)
            isSingleLine = true
        }
        val container = FrameLayout(this).apply {
            setPadding(50, 20, 50, 0)
            addView(nameInput)
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getLocalizedString(R.string.shortcut_add_title))
            .setMessage(ShortcutHelper.describe(this, shortcut.`package`))
            .setView(container)
            .setPositiveButton(getLocalizedString(R.string.shortcut_add)) { _, _ ->
                if (request.isValid && request.accept()) {
                    val name = nameInput.text.toString().trim()
                    if (name.isNotEmpty() && name != defaultLabel) {
                        val key = settingsKeyOf(shortcut.`package`, SHORTCUT_PREFIX + shortcut.id)
                        Prefs(this).setAppAlias(key, name)
                    }
                    showShortToast(getLocalizedString(R.string.shortcut_added))
                } else {
                    AppLogger.e(TAG, "Pin request could not be accepted")
                }
                finish()
            }
            .setNegativeButton(getLocalizedString(R.string.cancel)) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onDestroy() {
        // Avoid leaking the window when the activity is recreated, e.g. on rotation
        dialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PinShortcutActivity"
    }
}
