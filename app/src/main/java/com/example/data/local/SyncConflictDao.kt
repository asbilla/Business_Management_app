package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity): Long

    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY createdAt DESC")
    fun getUnresolvedConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY createdAt DESC")
    suspend fun getUnresolvedConflictsSync(): List<SyncConflictEntity>

    @Query("UPDATE sync_conflicts SET resolved = 1, resolutionStrategy = :strategy WHERE id = :id")
    suspend fun markResolved(id: Long, strategy: String)

    @Query("DELETE FROM sync_conflicts")
    suspend fun clearAll()
}
