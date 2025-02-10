package com.github.droidworksstudio.mlauncher

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.github.droidworksstudio.mlauncher.data.Prefs
import org.acra.ReportField
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class Mlauncher : Application() {
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()

        // Initialize prefs here
        prefs = Prefs(applicationContext)

        setCustomFont(applicationContext)

        val pkgName = getString(R.string.app_name)
        val pkgVersion = this.packageManager.getPackageInfo(
            this.packageName,
            0
        ).versionName

        initAcra {
            //core configuration:
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.KEY_VALUE_LIST
            reportContent = listOf(
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.PHONE_MODEL,
                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE,
                ReportField.LOGCAT
            )
            //each plugin you chose above can be configured in a block like this:
            dialog {
                //required
                text = getString(R.string.acra_dialog_text).format(pkgName)
                //optional, enables the dialog title
                title = getString(R.string.acra_crash)
                //defaults to android.R.string.ok
                positiveButtonText = getString(R.string.acra_send_report)
                //defaults to android.R.string.cancel
                negativeButtonText = getString(R.string.acra_dont_send)
                //optional, defaults to @android:style/Theme.Dialog
                resTheme = R.style.MaterialDialogTheme
            }

            mailSender {
                //required
                mailTo = getString(R.string.acra_email)
                //defaults to true
                reportAsFile = true
                //defaults to ACRA-report.stacktrace
                reportFileName = "$pkgName-crash-report.stacktrace"
                //defaults to "<applicationId> Crash Report"
                subject = "$pkgName $pkgVersion Crash Report"
                //defaults to empty
                body = getString(R.string.acra_mail_body)
            }
        }
    }


    private fun setCustomFont(context: Context) {
        // Load the custom font from resources
        val customFont = prefs.fontFamily.getFont(context)

        // Apply the custom font to different font families
        if (customFont != null) {
            TypefaceUtil.setDefaultFont("DEFAULT", customFont)
            TypefaceUtil.setDefaultFont("MONOSPACE", customFont)
            TypefaceUtil.setDefaultFont("SERIF", customFont)
            TypefaceUtil.setDefaultFont("SANS_SERIF", customFont)
        }
    }
}

object TypefaceUtil {

    fun setDefaultFont(staticTypefaceFieldName: String, fontAssetName: Typeface) {
        Log.e("setDefaultFont", "$staticTypefaceFieldName | $fontAssetName")
        try {
            val staticField = Typeface::class.java.getDeclaredField(staticTypefaceFieldName)
            staticField.isAccessible = true
            staticField.set(null, fontAssetName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
