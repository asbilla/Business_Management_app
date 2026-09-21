package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String, // e.g. "WINDOWS-A1B2C3D4"
    val deviceName: String,
    val ipAddress: String,
    val port: Int = 54320,
    val pairingSecret: String, // Shared authenticated token
    val pairedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = 0L,
    val status: String = "PAIRED" // "PAIRED", "REVOKED"
)
