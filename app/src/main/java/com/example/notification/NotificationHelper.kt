package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppointmentEntity

object NotificationHelper {

    const val CHANNEL_ID = "appointment_reminders_channel"
    private const val CHANNEL_NAME = "Appointment Reminders & Confirmations"
    private const val CHANNEL_DESC = "Notifications for booking confirmations, cancellations, and 24-hour reminders"

    const val INCOMING_CALL_CHANNEL_ID = "incoming_calls_channel"
    private const val INCOMING_CALL_CHANNEL_NAME = "Incoming Call Booking Alerts"
    private const val INCOMING_CALL_CHANNEL_DESC = "Heads-up notification for incoming caller appointment booking"

    const val INCOMING_CALL_NOTIFICATION_ID = 88888

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Appointment Channel
            val appointmentChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(appointmentChannel)

            // Incoming Call Channel (Heads-up)
            val callChannel = NotificationChannel(INCOMING_CALL_CHANNEL_ID, INCOMING_CALL_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = INCOMING_CALL_CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun getAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "appointments")
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Immediately displays a local push notification when an appointment is booked.
     */
    fun showBookingConfirmationNotification(context: Context, appointment: AppointmentEntity) {
        createNotificationChannel(context)
        if (!areNotificationsEnabled(context)) return

        val title = "Appointment Confirmed"
        val subtitle = "${appointment.customerName} • ${appointment.serviceName}"
        val body = "Scheduled on ${appointment.appointmentDate} at ${appointment.appointmentTime} (${appointment.durationMinutes}m)"
        val expandedText = buildString {
            append("Customer: ").append(appointment.customerName).append("\n")
            if (appointment.customerPhone.isNotBlank()) {
                append("Phone: ").append(appointment.customerPhone).append("\n")
            }
            append("Service: ").append(appointment.serviceName).append("\n")
            append("Time: ").append(appointment.appointmentDate).append(" at ").append(appointment.appointmentTime).append("\n")
            if (appointment.notes.isNotBlank()) {
                append("Notes: ").append(appointment.notes)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("$subtitle: $body")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText).setSummaryText("Booking Confirmed"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(getAppIntent(context))
            .setAutoCancel(true)

        try {
            val notificationId = (appointment.id % 100_000).toInt() + 10_000
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handled when POST_NOTIFICATIONS runtime permission is not granted
        }
    }

    /**
     * Immediately displays a local push notification when an appointment is cancelled.
     */
    fun showCancellationNotification(context: Context, appointment: AppointmentEntity) {
        createNotificationChannel(context)
        if (!areNotificationsEnabled(context)) return

        val title = "Appointment Cancelled"
        val text = "${appointment.customerName} - ${appointment.serviceName} on ${appointment.appointmentDate} at ${appointment.appointmentTime} has been cancelled."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText("Cancelled"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(getAppIntent(context))
            .setAutoCancel(true)

        try {
            val notificationId = (appointment.id % 100_000).toInt() + 20_000
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handled when POST_NOTIFICATIONS runtime permission is not granted
        }
    }

    /**
     * Displays a 24-hour pre-appointment reminder notification.
     */
    fun showReminderNotification(context: Context, appointment: AppointmentEntity) {
        createNotificationChannel(context)
        if (!areNotificationsEnabled(context)) return

        val title = "Upcoming Appointment in 24 Hours"
        val subtitle = "${appointment.customerName} is scheduled tomorrow"
        val body = "${appointment.serviceName} on ${appointment.appointmentDate} at ${appointment.appointmentTime}."

        val expandedText = buildString {
            append("Reminder for upcoming appointment tomorrow:\n")
            append("• Client: ").append(appointment.customerName).append("\n")
            if (appointment.customerPhone.isNotBlank()) {
                append("• Phone: ").append(appointment.customerPhone).append("\n")
            }
            append("• Service: ").append(appointment.serviceName).append("\n")
            append("• Time: ").append(appointment.appointmentTime).append(" (${appointment.durationMinutes}m)\n")
            if (appointment.notes.isNotBlank()) {
                append("• Note: ").append(appointment.notes)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("$subtitle: $body")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText).setSummaryText("24h Reminder"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(getAppIntent(context))
            .setAutoCancel(true)

        try {
            val notificationId = (appointment.id % 100_000).toInt() + 30_000
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handled when POST_NOTIFICATIONS runtime permission is not granted
        }
    }

    /**
     * Shows a high-priority Heads-Up Notification for an incoming caller with direct "+ Book Now" action.
     */
    fun showIncomingCallNotification(
        context: Context,
        callerName: String,
        phoneNumber: String,
        callState: String = "RINGING"
    ) {
        createNotificationChannel(context)
        if (!areNotificationsEnabled(context)) return

        val displayName = if (callerName.isNotBlank()) callerName else "Caller"
        val displayPhone = if (phoneNumber.isNotBlank()) phoneNumber else "Unknown Number"

        val title = "📞 Incoming Call: $displayName"
        val text = "$displayPhone • Tap '+ Book Now' to auto-fill appointment"

        // Direct "+ Book Now" intent launching AppointmentsScreen with auto-booking pre-filled
        val bookIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "appointments")
            putExtra("auto_book", true)
            putExtra("caller_name", displayName)
            putExtra("caller_phone", displayPhone)
        }
        val bookPendingIntent = PendingIntent.getActivity(
            context,
            2001,
            bookIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Incoming Call from $displayName ($displayPhone)\nQuickly book caller into appointment schedule without leaving phone call."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(bookPendingIntent, false)
            .setContentIntent(bookPendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.mipmap.ic_launcher,
                "+ Book Now",
                bookPendingIntent
            )

        try {
            NotificationManagerCompat.from(context).notify(INCOMING_CALL_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }

    /**
     * Cancels the incoming call heads-up notification once call ends.
     */
    fun dismissIncomingCallNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(INCOMING_CALL_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
