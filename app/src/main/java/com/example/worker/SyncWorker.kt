package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.pref.AppPreferences
import com.example.data.remote.SheetsApiService

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val database = AppDatabase.getDatabase(appContext)
    private val preferences = AppPreferences.getInstance(appContext)
    private val apiService = SheetsApiService()

    override suspend fun doWork(): Result {
        val webAppUrl = preferences.getWebAppUrl()
        if (webAppUrl.isBlank()) {
            Log.w(TAG, "SyncWorker skipped: Google Apps Script Web App URL is not configured.")
            return Result.failure()
        }

        val unsyncedList = database.transactionDao().getUnsyncedTransactions()
        val unsyncedAppointments = database.appointmentDao().getUnsyncedAppointments()

        if (unsyncedList.isEmpty() && unsyncedAppointments.isEmpty()) {
            Log.d(TAG, "SyncWorker: No unsynced transactions or appointments found.")
            return Result.success()
        }

        var anyFailures = false

        // Sync Transactions
        if (unsyncedList.isNotEmpty()) {
            Log.d(TAG, "SyncWorker: Syncing ${unsyncedList.size} transactions to Sheets...")
            val batchResult = apiService.postTransactionsBatch(webAppUrl, unsyncedList)
            if (batchResult.isSuccess) {
                database.transactionDao().markAsSynced(unsyncedList.map { it.id })
                Log.d(TAG, "SyncWorker: Batch marked ${unsyncedList.size} transactions as synced.")
            } else {
                for (transaction in unsyncedList) {
                    val result = apiService.postTransaction(webAppUrl, transaction)
                    if (result.isSuccess) {
                        database.transactionDao().markAsSynced(listOf(transaction.id))
                    } else {
                        Log.e(TAG, "Failed syncing transaction #${transaction.id}: ${result.exceptionOrNull()?.message}")
                        anyFailures = true
                    }
                }
            }
        }

        // Sync Appointments
        if (unsyncedAppointments.isNotEmpty()) {
            Log.d(TAG, "SyncWorker: Syncing ${unsyncedAppointments.size} appointments to Sheets...")
            val batchApptResult = apiService.postAppointmentsBatch(webAppUrl, unsyncedAppointments)
            if (batchApptResult.isSuccess) {
                database.appointmentDao().markAsSyncedByIds(unsyncedAppointments.map { it.id })
                Log.d(TAG, "SyncWorker: Batch marked ${unsyncedAppointments.size} appointments as synced.")
            } else {
                for (appt in unsyncedAppointments) {
                    val result = apiService.postAppointment(webAppUrl, appt)
                    if (result.isSuccess) {
                        database.appointmentDao().markAsSyncedByIds(listOf(appt.id))
                    } else {
                        Log.e(TAG, "Failed syncing appointment #${appt.id}: ${result.exceptionOrNull()?.message}")
                        anyFailures = true
                    }
                }
            }
        }

        return if (anyFailures) {
            Log.w(TAG, "SyncWorker completed with some errors, requesting retry.")
            Result.retry()
        } else {
            Log.d(TAG, "SyncWorker successfully synced all pending records.")
            Result.success()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "google_sheets_sync_work"

        fun enqueueSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                syncRequest
            )
            Log.d(TAG, "SyncWorker request enqueued.")
        }
    }
}
