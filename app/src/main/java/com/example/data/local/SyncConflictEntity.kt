package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordUuid: String,
    val entityType: String, // "TRANSACTION", "APPOINTMENT", "PRODUCT", etc.
    val localVersion: Long,
    val remoteVersion: Long,
    val localData: String, // JSON
    val remoteData: String, // JSON
    val resolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val resolutionStrategy: String = "UNRESOLVED" // "UNRESOLVED", "LOCAL_WINS", "REMOTE_WINS", "MERGED"
)
