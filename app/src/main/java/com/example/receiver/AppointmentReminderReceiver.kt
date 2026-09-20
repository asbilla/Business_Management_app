package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarm.AppointmentAlarmScheduler
import com.example.data.local.AppDatabase
import com.example.data.local.AppointmentEntity
import com.example.data.pref.AppPreferences
import com.example.notification.NotificationHelper
import com.example.util.SmsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppointmentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appointmentId = intent.getLongExtra(AppointmentAlarmScheduler.EXTRA_APPOINTMENT_ID, -1L)
        val customerName = intent.getStringExtra(AppointmentAlarmScheduler.EXTRA_CUSTOMER_NAME) ?: "Client"
        val customerPhone = intent.getStringExtra(AppointmentAlarmScheduler.EXTRA_CUSTOMER_PHONE) ?: ""
        val serviceName = intent.getStringExtra(AppointmentAlarmScheduler.EXTRA_SERVICE_NAME) ?: "Service"
        val dateStr = intent.getStringExtra(AppointmentAlarmScheduler.EXTRA_DATE) ?: ""
        val timeStr = intent.getStringExtra(AppointmentAlarmScheduler.EXTRA_TIME) ?: ""

        Log.d(TAG, "AppointmentReminderReceiver triggered for appointment ID: $appointmentId")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val allAppointments = db.appointmentDao().getAllAppointmentsSync()
                val liveAppointment = if (appointmentId != -1L) {
                    allAppointments.find { it.id == appointmentId }
                } else null

                // If appointment was marked Cancelled or Completed in the database, do not trigger reminder
                if (liveAppointment != null && (liveAppointment.status == "Cancelled" || liveAppointment.status == "Completed")) {
                    Log.i(TAG, "Appointment $appointmentId is marked ${liveAppointment.status}. Skipping reminder.")
                    return@launch
                }

                val appointmentToNotify = liveAppointment ?: AppointmentEntity(
                    id = appointmentId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    serviceName = serviceName,
                    appointmentDate = dateStr,
                    appointmentTime = timeStr
                )

                val preferences = AppPreferences.getInstance(context)
                val settings = preferences.getAppointmentSettings()

                // 1. Show local push notification
                if (settings.notificationsEnabled && settings.reminder24hEnabled) {
                    NotificationHelper.showReminderNotification(context, appointmentToNotify)
                }

                // 2. Automated SMS Reminder (if permitted & configured)
                if (settings.automatedSmsEnabled && appointmentToNotify.customerPhone.isNotBlank()) {
                    if (SmsHelper.canSendSms(context)) {
                        val smsText = "Reminder: Your appointment for ${appointmentToNotify.serviceName} is tomorrow (${appointmentToNotify.appointmentDate}) at ${appointmentToNotify.appointmentTime}."
                        SmsHelper.sendDirectSms(context, appointmentToNotify.customerPhone, smsText)
                    } else {
                        Log.w(TAG, "Automated SMS is enabled but SEND_SMS permission is not granted.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in AppointmentReminderReceiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AppointmentReminder"
    }
}
