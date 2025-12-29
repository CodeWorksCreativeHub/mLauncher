package com.github.codeworkscreativehub.mlauncher.ui.widgets.wordoftheday

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WordOfTheDayAlarm {

    fun scheduleNextUpdate(context: Context) {
        val delay = calculateDelayUntilMidnight()

        val workRequest = PeriodicWorkRequestBuilder<WordOfTheDayWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "WordOfTheDay",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun calculateDelayUntilMidnight(): Long {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return midnight.timeInMillis - now.timeInMillis
    }
}

class WordOfTheDayWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // TODO: Fetch and update your Word of the Day widget here
        // Example: send a broadcast like your receiver did
        val intent = Intent(applicationContext, WordOfTheDayUpdateReceiver::class.java).apply {
            action = "com.github.codeworkscreativehub.mlauncher.ui.widgets.wordofday.UPDATE_WIDGET"
        }
        applicationContext.sendBroadcast(intent)

        return Result.success()
    }
}
