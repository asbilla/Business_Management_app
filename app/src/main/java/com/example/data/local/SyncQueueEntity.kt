package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordUuid: String,
    val entityType: String, // "TRANSACTION", "APPOINTMENT", "PRODUCT", "BUSINESS_PROFILE", "APPOINTMENT_SETTINGS"
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    val payload: String, // JSON representation
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = "PENDING", // "PENDING", "SYNCING", "COMPLETED", "FAILED"
    val lastError: String? = null
)
