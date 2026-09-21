package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: PairedDeviceEntity): Long

    @Query("SELECT * FROM paired_devices WHERE status = 'PAIRED' ORDER BY lastSyncAt DESC")
    fun getAllPairedDevices(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE status = 'PAIRED' ORDER BY lastSyncAt DESC")
    suspend fun getAllPairedDevicesSync(): List<PairedDeviceEntity>

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): PairedDeviceEntity?

    @Query("UPDATE paired_devices SET lastSyncAt = :timestamp, lastSyncCursor = :cursor WHERE deviceId = :deviceId")
    suspend fun updateLastSyncCursor(deviceId: String, cursor: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE paired_devices SET lastSyncAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSync(deviceId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE paired_devices SET lastSeen = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE paired_devices SET certificateFingerprint = :fingerprint WHERE deviceId = :deviceId")
    suspend fun updateCertificateFingerprint(deviceId: String, fingerprint: String)

    @Query("UPDATE paired_devices SET status = :status WHERE deviceId = :deviceId")
    suspend fun updateStatus(deviceId: String, status: String)

    @Query("DELETE FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("DELETE FROM paired_devices")
    suspend fun clearAll()
}
