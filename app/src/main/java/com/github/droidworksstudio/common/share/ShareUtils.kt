package com.github.droidworksstudio.common.share

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.creativecodecat.components.views.FontBottomSheetDialogLocked
import com.github.droidworksstudio.mlauncher.R

class ShareUtils(val context: Context, val activity: Activity) {


    var shareDialog: FontBottomSheetDialogLocked? = null

    @SuppressLint("InflateParams")
    fun showMaterialShareDialog(
        context: Context,
        dialogTitle: String,
        textToShare: String
    ) {
        // Dismiss existing dialog if already showing
        shareDialog?.dismiss()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textToShare)
        }

        val packageManager = context.packageManager
        val resolveInfoList = packageManager.queryIntentActivities(sendIntent, 0)

        val apps = resolveInfoList.map {
            val label = it.loadLabel(packageManager).toString()
            val icon = it.loadIcon(packageManager)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textToShare)
                setClassName(it.activityInfo.packageName, it.activityInfo.name)
            }
            ShareAppInfo(label, icon, intent)
        }

        val themedContext = ContextThemeWrapper(context, R.style.AppTheme_MaterialDialog)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.dialog_share_bottom_sheet, null)

        val titleView = view.findViewById<TextView>(R.id.share_title)
        val textView = view.findViewById<TextView>(R.id.share_text)
        val copyButton = view.findViewById<ImageButton>(R.id.copy_button)
        val recyclerView = view.findViewById<RecyclerView>(R.id.share_recycler)

        titleView.text = dialogTitle
        textView.text = textToShare

        copyButton.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Share Text", textToShare))
        }

        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recyclerView.adapter = ShareAppAdapter(apps) { selectedApp ->
            context.startActivity(selectedApp.launchIntent)
            shareDialog?.dismiss()
        }

        shareDialog = FontBottomSheetDialogLocked(themedContext)
        shareDialog?.setContentView(view)
        shareDialog?.show()
    }
}
