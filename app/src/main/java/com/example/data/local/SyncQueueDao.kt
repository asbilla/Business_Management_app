package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SyncQueueEntity>): List<Long>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    suspend fun getFailedCount(): Int

    @Query("SELECT * FROM sync_queue WHERE recordUuid = :recordUuid ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getItemByRecordUuid(recordUuid: String): SyncQueueEntity?

    @Query("UPDATE sync_queue SET status = :status, lastError = :error, lastAttemptAt = :attemptTime WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = :status, lastError = :error, lastAttemptAt = :attemptTime WHERE recordUuid = :recordUuid")
    suspend fun updateStatusByRecordUuid(recordUuid: String, status: String, error: String? = null, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error, lastAttemptAt = :attemptTime, status = 'PENDING' WHERE recordUuid = :recordUuid")
    suspend fun markRetryByRecordUuid(recordUuid: String, error: String? = null, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error, lastAttemptAt = :attemptTime, status = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long, error: String, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'FAILED' OR status = 'CONFLICT'")
    suspend fun retryAllFailed()

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE recordUuid = :recordUuid")
    suspend fun deleteByRecordUuid(recordUuid: String)

    @Query("DELETE FROM sync_queue WHERE recordUuid IN (:recordUuids)")
    suspend fun deleteByRecordUuids(recordUuids: List<String>)

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}
