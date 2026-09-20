package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val customerName: String,
    val customerPhone: String,
    val serviceName: String,
    val appointmentDate: String, // Format: yyyy-MM-dd
    val appointmentTime: String, // Format: e.g. "09:30 AM"
    val durationMinutes: Int = 30,
    val status: String = "Scheduled", // "Scheduled", "Confirmed", "Completed", "Cancelled"
    val notes: String = "",
    val price: Double = 0.0,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
