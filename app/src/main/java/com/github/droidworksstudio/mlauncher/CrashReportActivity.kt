package com.github.droidworksstudio.mlauncher

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.github.droidworksstudio.common.getLocalizedString
import com.github.droidworksstudio.mlauncher.helper.emptyString
import com.github.droidworksstudio.mlauncher.helper.getDeviceInfo
import com.github.droidworksstudio.mlauncher.helper.getDeviceInfoJson
import com.github.droidworksstudio.mlauncher.helper.utils.SimpleEmailSender
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CrashReportActivity : AppCompatActivity() {
    private var pkgName: String = emptyString()
    private var pkgVersion: String = emptyString()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pkgName = getLocalizedString(R.string.app_name)
        pkgVersion = this.packageManager.getPackageInfo(
            this.packageName,
            0
        ).versionName.toString()

        // Check for internet connection before sending crash report
        if (isInternetAvailable()) {
            sendCrashReportNative()
        }

        // Show a dialog to ask if the user wants to report the crash
        MaterialAlertDialogBuilder(this)
            .setTitle(getLocalizedString(R.string.acra_crash))
            .setMessage(getLocalizedString(R.string.acra_dialog_text).format(pkgName))
            .setPositiveButton(getLocalizedString(R.string.acra_send_report)) { _, _ ->
                sendCrashReport(this)
            }
            .setNegativeButton(getLocalizedString(R.string.acra_dont_send)) { _, _ ->
                restartApp()
            }
            .setCancelable(false)
            .show()
    }

    // Function to check internet connectivity
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getLastSent(): Long {
        val prefs = getSharedPreferences("crash_report", MODE_PRIVATE)
        return prefs.getLong("last_sent", 0L)
    }

    private fun setLastSent(time: Long) {
        val prefs = getSharedPreferences("crash_report", MODE_PRIVATE)
        prefs.edit { putLong("last_sent", time) }
    }


    private fun sendCrashReportNative() {
        val cooldownMs = 14_400_000L // 4 hours
        val now = System.currentTimeMillis()
        val lastSent = getLastSent()
        val canSend = now - lastSent >= cooldownMs

        lifecycleScope.launch(Dispatchers.IO) {
            if (!canSend) return@launch
            try {
                val crashFileUri: Uri? = intent.getStringExtra("crash_log_uri")?.toUri()
                val crashFileUris: List<Uri> = crashFileUri?.let { listOf(it) } ?: emptyList()

                val logContent = readFirstCrashFile(this@CrashReportActivity, crashFileUris)

                // Example device info JSON string (replace with getDeviceInfoJson() if dynamic)
                val jsonDeviceInfo = getDeviceInfoJson(this@CrashReportActivity)

                // Parse device info JSON into Map<String, Any>
                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()

                val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val deviceAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(type)
                val deviceMap: Map<String, Any> = deviceAdapter.fromJson(jsonDeviceInfo) ?: emptyMap()


                val logBase64 = logContent?.toByteArray()?.let {
                    Base64.encodeToString(it, Base64.NO_WRAP)
                } ?: ""

                val exception: String = intent.getStringExtra("exception").toString()

                // Build crash JSON
                val crashJson = JSONObject().apply {
                    put("thread", "main")
                    put("message", "App crashed")
                    put("device", JSONObject(deviceMap))
                    put("timestamp", System.currentTimeMillis())
                    put("logFileBase64", logBase64)
                    put("stackTrace", exception)
                }.toString()

                val url = URL("https://crash.5646316.xyz")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true

                // Send JSON
                connection.outputStream.use { it.write(crashJson.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode

                // Read inputStream if 2xx, else errorStream
                val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseMessage = responseStream.bufferedReader().use { it.readText() }

                if (responseCode in 200..299) {
                    setLastSent(now)
                    println("Crash report sent successfully: $responseMessage")
                } else {
                    println("Failed to send crash report: $responseCode $responseMessage")
                }

                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun readFirstCrashFile(context: Context, crashFileUris: List<Uri>): String? {
        val firstUri = crashFileUris.firstOrNull() ?: return null
        return try {
            context.contentResolver.openInputStream(firstUri)?.bufferedReader().use { it?.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun sendCrashReport(context: Context) {
        // Use the latest crash log URI generated by CrashHandler
        val crashFileUri: Uri? = intent.getStringExtra("crash_log_uri")?.toUri()
        val crashFileUris: List<Uri> = crashFileUri?.let { listOf(it) } ?: emptyList()

        val emailSender = SimpleEmailSender()
        val deviceInfo = getDeviceInfo(context)
        val crashReportContent = getLocalizedString(R.string.acra_mail_body, deviceInfo)
        val subject = String.format("Crash Report %s - %s", pkgName, pkgVersion)
        val recipient = getLocalizedString(R.string.acra_email)

        emailSender.sendCrashReport(context, crashReportContent, crashFileUris, subject, recipient)
    }


    private fun restartApp() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }
}

