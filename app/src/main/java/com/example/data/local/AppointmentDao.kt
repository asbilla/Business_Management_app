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

    @Query("SELECT * FROM appointments ORDER BY appointmentDate ASC, appointmentTime ASC")
    fun getAllAppointments(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments ORDER BY appointmentDate ASC, appointmentTime ASC")
    suspend fun getAllAppointmentsSync(): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE appointmentDate = :date ORDER BY appointmentTime ASC")
    fun getAppointmentsForDate(date: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE appointmentDate >= :fromDate ORDER BY appointmentDate ASC, appointmentTime ASC")
    fun getUpcomingAppointments(fromDate: String): Flow<List<AppointmentEntity>>

    @Query("SELECT COUNT(*) FROM appointments WHERE appointmentDate = :date AND status != 'Cancelled'")
    fun getActiveAppointmentCountForDate(date: String): Flow<Int>

    @Query("SELECT * FROM appointments WHERE isSynced = 0")
    suspend fun getUnsyncedAppointments(): List<AppointmentEntity>

    @Query("SELECT COUNT(*) FROM appointments WHERE isSynced = 0")
    fun getUnsyncedAppointmentCount(): Flow<Int>

    @Query("SELECT * FROM appointments WHERE uuid = :uuid LIMIT 1")
    suspend fun getAppointmentByUuid(uuid: String): AppointmentEntity?

    @Query("SELECT * FROM appointments WHERE customerPhone = :phone AND appointmentDate = :date AND appointmentTime = :time LIMIT 1")
    suspend fun findAppointment(phone: String, date: String, time: String): AppointmentEntity?

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

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointmentById(id: Long)

    @Query("UPDATE appointments SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)

    @Query("SELECT customerName FROM appointments WHERE customerPhone = :phone AND customerName != '' ORDER BY id DESC LIMIT 1")
    suspend fun getCustomerNameByPhone(phone: String): String?

    @Query("SELECT DISTINCT customerName, customerPhone FROM appointments WHERE customerPhone != '' AND customerPhone IS NOT NULL ORDER BY customerName ASC")
    suspend fun getAllClientContacts(): List<ClientContact>
}

data class ClientContact(
    val customerName: String,
    val customerPhone: String
)
