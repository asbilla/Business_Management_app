package com.example.notification

import android.content.Context
import android.util.Log
import com.example.alarm.AppointmentAlarmScheduler
import com.example.data.local.AppointmentEntity
import com.example.data.model.AppointmentSettings
import com.example.util.SmsHelper

object AppointmentNotificationManager {

    private const val TAG = "ApptNotificationManager"

    /**
     * Triggered immediately when a new appointment is booked.
     * 1. Displays an immediate local push notification.
     * 2. Sends a local SMS confirmation (if configured and permitted).
     * 3. Schedules a 24-hour pre-appointment exact alarm.
     */
    fun onAppointmentBooked(
        context: Context,
        appointment: AppointmentEntity,
        settings: AppointmentSettings
    ) {
        Log.d(TAG, "Handling onAppointmentBooked for appointment #${appointment.id}")

        // 1. Immediate Local Push Notification
        if (settings.notificationsEnabled) {
            NotificationHelper.showBookingConfirmationNotification(context, appointment)
        }

        // 2. Automated Local SMS Confirmation (if configured and permitted)
        if (settings.automatedSmsEnabled && appointment.customerPhone.isNotBlank()) {
            if (SmsHelper.canSendSms(context)) {
                val smsMessage = "Hi ${appointment.customerName}, your appointment for ${appointment.serviceName} is confirmed for ${appointment.appointmentDate} at ${appointment.appointmentTime}."
                SmsHelper.sendDirectSms(context, appointment.customerPhone, smsMessage)
            } else {
                Log.w(TAG, "Automated SMS confirmation skipped: SEND_SMS permission not granted")
            }
        }

        // 3. Schedule 24-hour pre-appointment exact alarm
        if (settings.reminder24hEnabled && appointment.status != "Cancelled" && appointment.status != "Completed") {
            AppointmentAlarmScheduler.schedule24hReminder(context, appointment)
        }
    }

    /**
     * Triggered immediately when an appointment is cancelled.
     * 1. Cancels the scheduled background alarm for this appointment.
     * 2. Displays an immediate cancellation push notification.
     * 3. Sends an automated cancellation SMS (if configured and permitted).
     */
    fun onAppointmentCancelled(
        context: Context,
        appointment: AppointmentEntity,
        settings: AppointmentSettings
    ) {
        Log.d(TAG, "Handling onAppointmentCancelled for appointment #${appointment.id}")

        // 1. Remove background alarm
        AppointmentAlarmScheduler.cancelReminder(context, appointment.id)

        // 2. Immediate Cancellation Push Notification
        if (settings.notificationsEnabled) {
            NotificationHelper.showCancellationNotification(context, appointment)
        }

        // 3. Automated Cancellation SMS
        if (settings.automatedSmsEnabled && appointment.customerPhone.isNotBlank()) {
            if (SmsHelper.canSendSms(context)) {
                val smsMessage = "Hi ${appointment.customerName}, your appointment for ${appointment.serviceName} on ${appointment.appointmentDate} at ${appointment.appointmentTime} has been cancelled."
                SmsHelper.sendDirectSms(context, appointment.customerPhone, smsMessage)
            }
        }
    }

    /**
     * Triggered when an appointment is edited/updated.
     */
    fun onAppointmentUpdated(
        context: Context,
        appointment: AppointmentEntity,
        settings: AppointmentSettings
    ) {
        Log.d(TAG, "Handling onAppointmentUpdated for appointment #${appointment.id}")

        if (appointment.status == "Cancelled") {
            onAppointmentCancelled(context, appointment, settings)
        } else if (appointment.status == "Completed") {
            AppointmentAlarmScheduler.cancelReminder(context, appointment.id)
        } else {
            // Re-schedule 24-hour alarm with updated date/time
            if (settings.reminder24hEnabled) {
                AppointmentAlarmScheduler.cancelReminder(context, appointment.id)
                AppointmentAlarmScheduler.schedule24hReminder(context, appointment)
            } else {
                AppointmentAlarmScheduler.cancelReminder(context, appointment.id)
            }
        }
    }

    /**
     * Triggered when an appointment is deleted.
     */
    fun onAppointmentDeleted(context: Context, appointmentId: Long) {
        Log.d(TAG, "Handling onAppointmentDeleted for appointment #$appointmentId")
        AppointmentAlarmScheduler.cancelReminder(context, appointmentId)
    }
}
