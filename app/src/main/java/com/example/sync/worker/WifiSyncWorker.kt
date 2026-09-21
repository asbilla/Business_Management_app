package com.example.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.local.AppDatabase
import com.example.data.pref.AppPreferences
import com.example.sync.engine.WifiSyncEngine
import java.util.concurrent.TimeUnit

class WifiSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "WifiSyncWorker"

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val preferences = AppPreferences.getInstance(applicationContext)
            val syncEngine = WifiSyncEngine(applicationContext, database, preferences)

            val pairedDevices = database.pairedDeviceDao().getAllPairedDevicesSync()
            if (pairedDevices.none { it.status == "PAIRED" }) {
                Log.d(TAG, "WifiSyncWorker: No paired Windows devices found, skipping.")
                return Result.success()
            }

            val pendingCount = database.syncQueueDao().getPendingCount()
            Log.d(TAG, "WifiSyncWorker: Attempting background Wi-Fi sync. Pending changes: $pendingCount")

            val sessionResult = syncEngine.syncWithFirstPairedDevice()
            if (sessionResult.success) {
                Log.d(TAG, "WifiSyncWorker completed successfully: pulled=${sessionResult.pulledCount}, pushed=${sessionResult.pushedCount}")
                Result.success()
            } else {
                Log.d(TAG, "WifiSyncWorker skipped or unreached Windows PC: ${sessionResult.errorMessage}")
                // Return success so we don't spam exponential retry when PC is off
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WifiSyncWorker error: ${e.message}")
            Result.success()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "wifi_sync_periodic_work"
        const val ONE_TIME_WORK_NAME = "wifi_sync_one_time_work"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<WifiSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun enqueueOpportunisticSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<WifiSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
