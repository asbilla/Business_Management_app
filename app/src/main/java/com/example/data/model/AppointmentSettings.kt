package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppointmentSettings(
    val bookingEnabled: Boolean = true,
    val slotDurationMinutes: Int = 30, // 15, 30, 45, 60
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    val bufferMinutes: Int = 0,
    val workingDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6), // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
    val notificationsEnabled: Boolean = true,
    val reminder24hEnabled: Boolean = true,
    val automatedSmsEnabled: Boolean = false
) {
    fun formatWorkingHours(): String {
        val startFormatted = formatTime(startHour, startMinute)
        val endFormatted = formatTime(endHour, endMinute)
        return "$startFormatted - $endFormatted"
    }

    fun generateSlots(): List<String> {
        if (!bookingEnabled) return emptyList()
        val slots = mutableListOf<String>()
        var currentTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute
        val step = (slotDurationMinutes + bufferMinutes).coerceAtLeast(10)

        while (currentTotalMinutes + slotDurationMinutes <= endTotalMinutes) {
            val h = currentTotalMinutes / 60
            val m = currentTotalMinutes % 60
            slots.add(formatTime(h, m))
            currentTotalMinutes += step
        }
        return slots
    }

    companion object {
        fun formatTime(hour: Int, minute: Int): String {
            val period = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return String.format(java.util.Locale.US, "%02d:%02d %s", displayHour, minute, period)
        }
    }
}
