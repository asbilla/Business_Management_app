package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    @Query("SELECT * FROM appointments WHERE deletedAt IS NULL ORDER BY appointmentDate ASC, appointmentTime ASC")
    fun getAllAppointments(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE deletedAt IS NULL ORDER BY appointmentDate ASC, appointmentTime ASC")
    suspend fun getAllAppointmentsSync(): List<AppointmentEntity>

    @Query("SELECT * FROM appointments ORDER BY appointmentDate ASC, appointmentTime ASC")
    suspend fun getAllAppointmentsIncludingDeletedSync(): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE appointmentDate = :date AND deletedAt IS NULL ORDER BY appointmentTime ASC")
    fun getAppointmentsForDate(date: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE appointmentDate >= :fromDate AND deletedAt IS NULL ORDER BY appointmentDate ASC, appointmentTime ASC")
    fun getUpcomingAppointments(fromDate: String): Flow<List<AppointmentEntity>>

    @Query("SELECT COUNT(*) FROM appointments WHERE appointmentDate = :date AND status != 'Cancelled' AND deletedAt IS NULL")
    fun getActiveAppointmentCountForDate(date: String): Flow<Int>

    @Query("SELECT * FROM appointments WHERE isSynced = 0 AND deletedAt IS NULL")
    suspend fun getUnsyncedAppointments(): List<AppointmentEntity>

    @Query("SELECT COUNT(*) FROM appointments WHERE isSynced = 0 AND deletedAt IS NULL")
    fun getUnsyncedAppointmentCount(): Flow<Int>

    @Query("SELECT * FROM appointments WHERE uuid = :uuid LIMIT 1")
    suspend fun getAppointmentByUuid(uuid: String): AppointmentEntity?

    @Query("SELECT * FROM appointments WHERE customerPhone = :phone AND appointmentDate = :date AND appointmentTime = :time AND deletedAt IS NULL LIMIT 1")
    suspend fun findAppointment(phone: String, date: String, time: String): AppointmentEntity?

    @Query("SELECT * FROM appointments WHERE updatedAt >= :sinceTimestamp")
    suspend fun getAppointmentsModifiedSince(sinceTimestamp: Long): List<AppointmentEntity>

    @Query("UPDATE appointments SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSyncedByIds(ids: List<Long>)

    @Query("UPDATE appointments SET isSynced = 1 WHERE uuid IN (:uuids)")
    suspend fun markAsSyncedByUuid(uuids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointments(appointments: List<AppointmentEntity>): List<Long>

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity)

    @Delete
    suspend fun deleteAppointment(appointment: AppointmentEntity)

    @Query("UPDATE appointments SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE id = :id")
    suspend fun softDeleteAppointmentById(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE appointments SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE id = :id")
    suspend fun deleteAppointmentById(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE appointments SET deletedAt = :deletedAt, updatedAt = :deletedAt, version = version + 1 WHERE uuid = :uuid")
    suspend fun softDeleteAppointmentByUuid(uuid: String, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointmentByIdPermanently(id: Long)

    @Query("UPDATE appointments SET status = :newStatus, updatedAt = :timestamp, version = version + 1 WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT customerName FROM appointments WHERE customerPhone = :phone AND customerName != '' AND deletedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getCustomerNameByPhone(phone: String): String?

    @Query("SELECT DISTINCT customerName, customerPhone FROM appointments WHERE customerPhone != '' AND customerPhone IS NOT NULL AND deletedAt IS NULL ORDER BY customerName ASC")
    suspend fun getAllClientContacts(): List<ClientContact>

    @Query("DELETE FROM appointments")
    suspend fun clearAll()
}

data class ClientContact(
    val customerName: String,
    val customerPhone: String
)
