package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarm.AppointmentAlarmScheduler
import com.example.data.local.AppDatabase
import com.example.data.pref.AppPreferences
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver to automatically reschedule all pending 24-hour appointment reminders
 * from the local Room database whenever the phone reboots or the app package is updated.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver received action: $action")

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (action in validActions) {
            // Ensure notification channel exists
            NotificationHelper.createNotificationChannel(context)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val appointments = db.appointmentDao().getAllAppointmentsSync()
                    val preferences = AppPreferences.getInstance(context)
                    val settings = preferences.getAppointmentSettings()

                    var scheduledCount = 0
                    if (settings.reminder24hEnabled) {
                        for (appointment in appointments) {
                            if (appointment.status != "Cancelled" && appointment.status != "Completed") {
                                val scheduled = AppointmentAlarmScheduler.schedule24hReminder(context, appointment)
                                if (scheduled) scheduledCount++
                            }
                        }
                    }
                    Log.i(TAG, "BootReceiver successfully rescheduled $scheduledCount appointment alarms.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in BootReceiver while rescheduling appointment alarms: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
