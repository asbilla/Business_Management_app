package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["recordUuid"]),
        Index(value = ["status"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordUuid: String,
    val entityType: String, // "TRANSACTION", "APPOINTMENT", "PRODUCT", "BUSINESS_PROFILE", "APPOINTMENT_SETTINGS"
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    val payload: String, // JSON representation
    val version: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0L,
    val status: String = "PENDING", // "PENDING", "SYNCING", "COMPLETED", "FAILED", "CONFLICT"
    val lastError: String? = null
)
