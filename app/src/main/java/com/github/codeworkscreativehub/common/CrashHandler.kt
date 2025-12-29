package com.github.codeworkscreativehub.common

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.github.codeworkscreativehub.mlauncher.CrashReportActivity
import com.github.codeworkscreativehub.mlauncher.helper.getDeviceInfo
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import kotlin.system.exitProcess

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private val userActions = LinkedBlockingQueue<String>(50)

        fun logUserAction(action: String) {
            val timeStamp = Date().toString()
            userActions.offer("$timeStamp - $action")
            if (userActions.size > 50) userActions.poll()
        }

        fun forceCrash() {
            throw RuntimeException("💥 Forced crash for testing CrashHandler")
        }
    }

    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("EEE, d MMM yyyy - hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun buildCrashContent(exception: Throwable): String {
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxHeapSizeInMB = runtime.maxMemory() / 1048576L

        return buildString {
            appendLine("Crash Report - ${Date()}")
            appendLine("Thread: ${Thread.currentThread().name}")
            appendLine("\n=== Device Info ===")
            appendLine(getDeviceInfo(context))
            appendLine("\n=== Memory Info ===")
            appendLine("Used Memory (MB): $usedMemInMB")
            appendLine("Max Heap Size (MB): $maxHeapSizeInMB")
            appendLine("\n=== Recent User Actions ===")
            userActions.forEach { appendLine(it) }
            appendLine("\n=== Crash LogCat ===")
            try {
                val process = Runtime.getRuntime().exec("logcat -d -t 100 AndroidRuntime:E *:S")
                BufferedReader(InputStreamReader(process.inputStream)).forEachLine { appendLine(it) }
            } catch (_: Exception) {
            }
            appendLine("\n=== Crash Stack Trace ===")
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            appendLine(sw.toString())
        }
    }

    private fun saveCrashToMediaStore(content: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val packageManager = context.packageManager
        val packageName = context.packageName
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

        val timestamp = getTimestamp()
        val displayName = "$appName Crash Report_$timestamp.log"
        val relativePath = "Download/$appName/Crash Reports"
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        // Insert new crash file
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(collection, values)

        uri?.let {
            resolver.openOutputStream(it, "w")?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
        }

        // Maintain maximum 5 files
        try {
            val cursor = resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf("$relativePath/"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )
            cursor?.use {
                var count = 0
                while (it.moveToNext()) {
                    count++
                    if (count > 5) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return uri
    }

    private fun getCrashFileForLegacy(): File {
        val crashDir = File(context.filesDir, "crash_logs")
        crashDir.mkdirs()
        val timestamp = getTimestamp()
        val file = File(crashDir, "${context.packageName}-crash-report_$timestamp.txt")

        // Remove old logs if more than 5
        val oldFiles = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        oldFiles.drop(5).forEach { it.delete() }

        return file
    }

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            val content = buildCrashContent(exception)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveCrashToMediaStore(content)
            } else {
                val file = getCrashFileForLegacy()
                file.writeText(content)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            val intent = Intent(context, CrashReportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("crash_log_uri", uri.toString())
                putExtra("exception", exception.toString())
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        } finally {
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }
}