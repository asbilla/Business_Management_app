package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY date DESC, timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY date DESC, timestamp DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY date DESC, timestamp DESC")
    suspend fun getAllTransactionsIncludingDeletedSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE isSynced = 0 AND deletedAt IS NULL ORDER BY timestamp ASC")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE isSynced = 0 AND deletedAt IS NULL")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE date = :date AND deletedAt IS NULL ORDER BY timestamp DESC")
    fun getTransactionsByDate(date: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun getTransactionByUuid(uuid: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE date = :date AND type = :type AND deletedAt IS NULL ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateAndType(date: String, type: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE updatedAt >= :sinceTimestamp")
    suspend fun getTransactionsModifiedSince(sinceTimestamp: Long): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("UPDATE transactions SET isSynced = 1 WHERE uuid IN (:uuids)")
    suspend fun markAsSyncedByUuid(uuids: List<String>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE uuid = :uuid")
    suspend fun softDeleteByUuid(uuid: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE id = :id")
    suspend fun deleteById(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE date = :date AND type = :type")
    suspend fun deleteByDateAndType(date: String, type: String, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteByIdPermanently(id: Long)

    @Query("DELETE FROM transactions WHERE uuid = :uuid")
    suspend fun deleteByUuidPermanently(uuid: String)

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE date = :date AND type = :type")
    suspend fun softDeleteByDateAndType(date: String, type: String, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
