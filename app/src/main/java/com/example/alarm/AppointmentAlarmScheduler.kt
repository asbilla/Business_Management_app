package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.AppointmentEntity
import com.example.receiver.AppointmentReminderReceiver
import java.text.SimpleDateFormat
import java.util.Locale

object AppointmentAlarmScheduler {

    private const val TAG = "AppointmentAlarmScheduler"

    const val ACTION_APPOINTMENT_REMINDER = "com.example.action.APPOINTMENT_REMINDER"
    const val EXTRA_APPOINTMENT_ID = "extra_appointment_id"
    const val EXTRA_CUSTOMER_NAME = "extra_customer_name"
    const val EXTRA_CUSTOMER_PHONE = "extra_customer_phone"
    const val EXTRA_SERVICE_NAME = "extra_service_name"
    const val EXTRA_DATE = "extra_date"
    const val EXTRA_TIME = "extra_time"

    /**
     * Parses the appointment date (e.g. "yyyy-MM-dd") and time (e.g. "09:30 AM") into epoch milliseconds.
     */
    fun calculateAppointmentTimestamp(dateStr: String, timeStr: String): Long? {
        val cleanDate = dateStr.trim()
        val cleanTime = timeStr.trim().uppercase(Locale.US)
        val patterns = listOf(
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd h:mm a",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd H:mm"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse("$cleanDate $cleanTime")
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    /**
     * Calculates the trigger time exactly 24 hours prior to the appointment.
     * Formula: Appointment Timestamp - 24 hours.
     */
    fun calculate24hReminderTime(dateStr: String, timeStr: String): Long? {
        val apptTimestamp = calculateAppointmentTimestamp(dateStr, timeStr) ?: return null
        return apptTimestamp - (24L * 60L * 60L * 1000L)
    }

    /**
     * Checks if the app can schedule exact alarms on Android 12+ (API 31+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    /**
     * Schedules a 24-hour pre-appointment exact alarm if the calculated reminder time is in the future.
     * Uses unique PendingIntent request codes per appointment ID so alarms can be individually updated or canceled.
     */
    fun schedule24hReminder(context: Context, appointment: AppointmentEntity): Boolean {
        if (appointment.status == "Cancelled" || appointment.status == "Completed") {
            cancelReminder(context, appointment.id)
            return false
        }

        val triggerTime = calculate24hReminderTime(appointment.appointmentDate, appointment.appointmentTime)
        if (triggerTime == null) {
            Log.w(TAG, "Failed to parse appointment time for appointment ID: ${appointment.id}")
            return false
        }

        val now = System.currentTimeMillis()
        if (triggerTime <= now) {
            Log.i(TAG, "24-hour reminder time ($triggerTime) is in the past for appointment ID ${appointment.id}. Skipping alarm.")
            return false
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AppointmentReminderReceiver::class.java).apply {
            action = ACTION_APPOINTMENT_REMINDER
            putExtra(EXTRA_APPOINTMENT_ID, appointment.id)
            putExtra(EXTRA_CUSTOMER_NAME, appointment.customerName)
            putExtra(EXTRA_CUSTOMER_PHONE, appointment.customerPhone)
            putExtra(EXTRA_SERVICE_NAME, appointment.serviceName)
            putExtra(EXTRA_DATE, appointment.appointmentDate)
            putExtra(EXTRA_TIME, appointment.appointmentTime)
        }

        val requestCode = (appointment.id % 1_000_000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "Successfully scheduled 24h reminder exact alarm for appointment ID: ${appointment.id} at $triggerTime")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm for appointment ID ${appointment.id}: ${e.message}", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                true
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback alarm scheduling also failed: ${fallbackEx.message}", fallbackEx)
                false
            }
        }
    }

    /**
     * Cancels any scheduled background alarms for the specific appointment ID.
     */
    fun cancelReminder(context: Context, appointmentId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AppointmentReminderReceiver::class.java).apply {
            action = ACTION_APPOINTMENT_REMINDER
        }
        val requestCode = (appointmentId % 1_000_000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled reminder alarm for appointment ID: $appointmentId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling reminder alarm for appointment ID $appointmentId: ${e.message}", e)
        }
    }
}
