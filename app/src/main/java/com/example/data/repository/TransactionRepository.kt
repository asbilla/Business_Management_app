package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.model.AppointmentSettings
import com.example.data.model.ProductItem
import com.example.data.pref.AppPreferences
import com.example.data.pref.BusinessProfile
import com.example.data.remote.RemoteAppointment
import com.example.data.remote.RemoteTransaction
import com.example.notification.AppointmentNotificationManager
import com.example.sync.discovery.DiscoveredDevice
import com.example.sync.discovery.WifiDiscoveryManager
import com.example.sync.engine.SyncSessionResult
import com.example.sync.engine.SyncState
import com.example.sync.engine.WifiSyncEngine
import com.example.sync.model.SyncProtocol
import com.example.sync.security.DeviceIdManager
import com.example.sync.worker.WifiSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SyncResult(
    val transactionsPushed: Int = 0,
    val transactionsPulled: Int = 0,
    val appointmentsPushed: Int = 0,
    val appointmentsPulled: Int = 0,
    val message: String = ""
)

class TransactionRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val preferences: AppPreferences = AppPreferences.getInstance(context)
) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dao = database.transactionDao()
    private val appointmentDao = database.appointmentDao()
    private val productDao = database.productDao()
    private val syncQueueDao = database.syncQueueDao()
    private val pairedDeviceDao = database.pairedDeviceDao()
    private val syncConflictDao = database.syncConflictDao()

    val wifiDiscoveryManager = WifiDiscoveryManager(context)
    val wifiSyncEngine = WifiSyncEngine(context, database, preferences)

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()
    val unsyncedCount: Flow<Int> = dao.getUnsyncedCount()
    val unsyncedAppointmentCount: Flow<Int> = appointmentDao.getUnsyncedAppointmentCount()
    val businessProfile = preferences.businessProfileFlow
    val themeMode = preferences.themeModeFlow
    val appointmentSettings = preferences.appointmentSettingsFlow

    // Products mapped from Room database
    val products: Flow<List<ProductItem>> = productDao.getAllActiveProducts().map { entities ->
        entities.map { ProductItem(name = it.name, price = it.price, category = it.category) }
    }

    val allAppointments: Flow<List<AppointmentEntity>> = appointmentDao.getAllAppointments()

    // Sync State Flows
    val syncState: StateFlow<SyncState> = wifiSyncEngine.syncState
    val lastSyncTimestamp: StateFlow<Long> = wifiSyncEngine.lastSyncTimestamp
    val lastSyncMessage: StateFlow<String> = wifiSyncEngine.lastSyncMessage
    val pendingSyncCount: Flow<Int> = syncQueueDao.getPendingCountFlow()
    val pairedDevices: Flow<List<PairedDeviceEntity>> = pairedDeviceDao.getAllPairedDevices()
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = wifiDiscoveryManager.discoveredDevices
    val isDiscoveringDevices: StateFlow<Boolean> = wifiDiscoveryManager.isDiscovering
    val syncConflicts: Flow<List<SyncConflictEntity>> = syncConflictDao.getUnresolvedConflicts()

    val myDeviceId: String = DeviceIdManager.getDeviceId(context)
    val myDeviceName: String = DeviceIdManager.getDeviceName(context)

    init {
        // Seed or migrate products with deterministic UUIDs into Room
        repoScope.launch {
            try {
                DeterministicProductMigration.migrateLegacyProductsIfNecessary(
                    productDao = productDao,
                    preferences = preferences,
                    deviceId = myDeviceId
                )
            } catch (e: Exception) {
                android.util.Log.e("TransactionRepo", "Error migrating products to Room: ${e.message}")
            }
        }

        // Schedule periodic background Wi-Fi sync
        WifiSyncWorker.schedulePeriodicSync(context)
    }

    fun getAppointmentsForDate(date: String): Flow<List<AppointmentEntity>> =
        appointmentDao.getAppointmentsForDate(date)

    fun getUpcomingAppointments(fromDate: String): Flow<List<AppointmentEntity>> =
        appointmentDao.getUpcomingAppointments(fromDate)

    fun getActiveAppointmentCountForDate(date: String): Flow<Int> =
        appointmentDao.getActiveAppointmentCountForDate(date)

    suspend fun saveAppointment(appointment: AppointmentEntity, @Suppress("UNUSED_PARAMETER") syncToSheets: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val assignedUuid = if (appointment.uuid.isNotBlank()) appointment.uuid else UUID.randomUUID().toString()
        val entityToSave = appointment.copy(
            uuid = assignedUuid,
            deviceId = myDeviceId,
            createdAt = if (appointment.createdAt > 0) appointment.createdAt else now,
            updatedAt = now,
            version = appointment.version + 1,
            isSynced = false
        )
        val insertedId = appointmentDao.insertAppointment(entityToSave)
        val completeAppointment = entityToSave.copy(id = insertedId)

        // Add to Sync Queue
        enqueueSyncRecord(
            recordUuid = assignedUuid,
            entityType = SyncProtocol.EntityTypes.APPOINTMENT,
            operation = if (appointment.id > 0) SyncProtocol.Operations.UPDATE else SyncProtocol.Operations.CREATE,
            payload = json.encodeToString(AppointmentEntity.serializer(), completeAppointment)
        )

        val settings = getAppointmentSettings()
        AppointmentNotificationManager.onAppointmentBooked(context, completeAppointment, settings)
        WifiSyncWorker.enqueueOpportunisticSync(context)
        return insertedId
    }

    suspend fun updateAppointment(appointment: AppointmentEntity, @Suppress("UNUSED_PARAMETER") syncToSheets: Boolean = false) {
        val now = System.currentTimeMillis()
        val updated = appointment.copy(
            updatedAt = now,
            version = appointment.version + 1,
            deviceId = myDeviceId,
            isSynced = false
        )
        appointmentDao.updateAppointment(updated)

        // Add to Sync Queue
        enqueueSyncRecord(
            recordUuid = updated.uuid,
            entityType = SyncProtocol.EntityTypes.APPOINTMENT,
            operation = SyncProtocol.Operations.UPDATE,
            payload = json.encodeToString(AppointmentEntity.serializer(), updated)
        )

        val settings = getAppointmentSettings()
        AppointmentNotificationManager.onAppointmentUpdated(context, appointment, settings)
        WifiSyncWorker.enqueueOpportunisticSync(context)
    }

    suspend fun deleteAppointment(appointment: AppointmentEntity): Result<String> {
        val now = System.currentTimeMillis()
        AppointmentNotificationManager.onAppointmentDeleted(context, appointment.id)
        appointmentDao.softDeleteAppointmentByUuid(appointment.uuid, now)

        enqueueSyncRecord(
            recordUuid = appointment.uuid,
            entityType = SyncProtocol.EntityTypes.APPOINTMENT,
            operation = SyncProtocol.Operations.DELETE,
            payload = json.encodeToString(AppointmentEntity.serializer(), appointment.copy(deletedAt = now))
        )

        WifiSyncWorker.enqueueOpportunisticSync(context)
        return Result.success("Deleted and queued for sync")
    }

    suspend fun deleteAppointmentById(id: Long): Result<String> {
        val appt = appointmentDao.getAllAppointmentsSync().find { it.id == id }
        if (appt != null) {
            return deleteAppointment(appt)
        }
        appointmentDao.deleteAppointmentById(id)
        return Result.success("Deleted from local storage")
    }

    suspend fun updateAppointmentStatus(id: Long, newStatus: String) {
        val now = System.currentTimeMillis()
        appointmentDao.updateStatus(id, newStatus, now)
        val appt = appointmentDao.getAllAppointmentsSync().find { it.id == id }
        if (appt != null) {
            val updated = appt.copy(status = newStatus, updatedAt = now, version = appt.version + 1)
            AppointmentNotificationManager.onAppointmentUpdated(context, updated, getAppointmentSettings())

            enqueueSyncRecord(
                recordUuid = updated.uuid,
                entityType = SyncProtocol.EntityTypes.APPOINTMENT,
                operation = SyncProtocol.Operations.UPDATE,
                payload = json.encodeToString(AppointmentEntity.serializer(), updated)
            )
            WifiSyncWorker.enqueueOpportunisticSync(context)
        }
    }

    fun getAppointmentSettings(): AppointmentSettings = preferences.getAppointmentSettings()

    fun setAppointmentSettings(settings: AppointmentSettings) {
        preferences.setAppointmentSettings(settings)
        repoScope.launch {
            enqueueSyncRecord(
                recordUuid = "APPOINTMENT_SETTINGS_SINGLETON",
                entityType = SyncProtocol.EntityTypes.APPOINTMENT_SETTINGS,
                operation = SyncProtocol.Operations.UPDATE,
                payload = json.encodeToString(AppointmentSettings.serializer(), settings)
            )
            WifiSyncWorker.enqueueOpportunisticSync(context)
        }
    }

    fun generateTimeSlots(settings: AppointmentSettings = getAppointmentSettings()): List<String> =
        preferences.generateTimeSlots(settings)

    fun getThemeMode(): String = preferences.getThemeMode()
    fun setThemeMode(mode: String) = preferences.setThemeMode(mode)

    fun getBusinessProfile(): BusinessProfile = preferences.getBusinessProfile()

    suspend fun saveBusinessProfile(
        profile: BusinessProfile,
        @Suppress("UNUSED_PARAMETER") syncToSheets: Boolean = false
    ): Result<String> {
        preferences.setBusinessProfile(profile)
        enqueueSyncRecord(
            recordUuid = "BUSINESS_PROFILE_SINGLETON",
            entityType = SyncProtocol.EntityTypes.BUSINESS_PROFILE,
            operation = SyncProtocol.Operations.UPDATE,
            payload = json.encodeToString(BusinessProfile.serializer(), profile)
        )
        WifiSyncWorker.enqueueOpportunisticSync(context)
        return Result.success("Business details saved and queued for sync")
    }

    suspend fun saveTransaction(
        type: String,
        amount: Double,
        category: String,
        date: String
    ): Long {
        val now = System.currentTimeMillis()
        val assignedUuid = UUID.randomUUID().toString()
        val entity = TransactionEntity(
            uuid = assignedUuid,
            date = date,
            timestamp = now,
            type = type,
            category = category.trim(),
            amount = amount,
            isSynced = false,
            updatedAt = now,
            deletedAt = null,
            version = 1L,
            deviceId = myDeviceId
        )
        val id = dao.insertTransaction(entity)
        val complete = entity.copy(id = id)

        enqueueSyncRecord(
            recordUuid = assignedUuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.CREATE,
            payload = json.encodeToString(TransactionEntity.serializer(), complete)
        )

        WifiSyncWorker.enqueueOpportunisticSync(context)
        return id
    }

    fun triggerBackgroundSync() {
        WifiSyncWorker.enqueueOpportunisticSync(context)
    }

    suspend fun syncBothWays(): Result<SyncResult> {
        val result = wifiSyncEngine.syncWithFirstPairedDevice()
        return if (result.success) {
            Result.success(
                SyncResult(
                    transactionsPushed = result.pushedCount,
                    transactionsPulled = result.pulledCount,
                    message = "Synced successfully"
                )
            )
        } else {
            Result.failure(Exception(result.errorMessage ?: "Wi-Fi synchronization failed"))
        }
    }

    suspend fun fetchRemoteTransactions(): Result<List<RemoteTransaction>> = Result.success(emptyList())
    suspend fun fetchAppointments(): Result<List<RemoteAppointment>> = Result.success(emptyList())
    suspend fun testConnection(@Suppress("UNUSED_PARAMETER") url: String): Result<String> = Result.success("Wi-Fi Sync active")
    suspend fun fetchPdfExportUrl(): Result<String> = Result.failure(Exception("PDF export generated locally on device"))

    fun getWebAppUrl(): String = preferences.getWebAppUrl()
    fun setWebAppUrl(url: String) = preferences.setWebAppUrl(url)
    fun isConfigured(): Boolean = true
    fun clearUrl() = preferences.clearUrl()

    suspend fun deleteTransaction(entity: TransactionEntity) {
        val now = System.currentTimeMillis()
        dao.softDeleteByUuid(entity.uuid, now)
        enqueueSyncRecord(
            recordUuid = entity.uuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.DELETE,
            payload = json.encodeToString(TransactionEntity.serializer(), entity.copy(deletedAt = now))
        )
        WifiSyncWorker.enqueueOpportunisticSync(context)
    }

    suspend fun updateTransactionAmount(
        id: String,
        date: String,
        type: String,
        category: String,
        newAmount: Double
    ): Result<String> {
        val now = System.currentTimeMillis()
        val existing = dao.getTransactionByUuid(id)
        if (existing != null) {
            val updated = existing.copy(
                amount = newAmount,
                category = category.ifBlank { existing.category },
                updatedAt = now,
                version = existing.version + 1,
                isSynced = false
            )
            dao.updateTransaction(updated)
            enqueueSyncRecord(
                recordUuid = updated.uuid,
                entityType = SyncProtocol.EntityTypes.TRANSACTION,
                operation = SyncProtocol.Operations.UPDATE,
                payload = json.encodeToString(TransactionEntity.serializer(), updated)
            )
        } else {
            val byDateAndType = dao.getTransactionsByDateAndType(date, type).firstOrNull()
            if (byDateAndType != null) {
                val updated = byDateAndType.copy(
                    amount = newAmount,
                    category = category.ifBlank { byDateAndType.category },
                    updatedAt = now,
                    version = byDateAndType.version + 1,
                    isSynced = false
                )
                dao.updateTransaction(updated)
                enqueueSyncRecord(
                    recordUuid = updated.uuid,
                    entityType = SyncProtocol.EntityTypes.TRANSACTION,
                    operation = SyncProtocol.Operations.UPDATE,
                    payload = json.encodeToString(TransactionEntity.serializer(), updated)
                )
            } else {
                val assignedUuid = if (id.isNotBlank()) id else UUID.randomUUID().toString()
                val newEntity = TransactionEntity(
                    uuid = assignedUuid,
                    date = date,
                    timestamp = now,
                    type = type,
                    category = category,
                    amount = newAmount,
                    isSynced = false,
                    updatedAt = now,
                    version = 1L,
                    deviceId = myDeviceId
                )
                val newId = dao.insertTransaction(newEntity)
                enqueueSyncRecord(
                    recordUuid = assignedUuid,
                    entityType = SyncProtocol.EntityTypes.TRANSACTION,
                    operation = SyncProtocol.Operations.CREATE,
                    payload = json.encodeToString(TransactionEntity.serializer(), newEntity.copy(id = newId))
                )
            }
        }
        WifiSyncWorker.enqueueOpportunisticSync(context)
        return Result.success("Amount updated and queued for sync")
    }

    suspend fun deleteTransactionPermanently(
        id: String,
        date: String,
        type: String
    ): Result<String> {
        val now = System.currentTimeMillis()
        if (id.isNotBlank()) {
            val existing = dao.getTransactionByUuid(id)
            dao.softDeleteByUuid(id, now)
            if (existing != null) {
                enqueueSyncRecord(
                    recordUuid = id,
                    entityType = SyncProtocol.EntityTypes.TRANSACTION,
                    operation = SyncProtocol.Operations.DELETE,
                    payload = json.encodeToString(TransactionEntity.serializer(), existing.copy(deletedAt = now))
                )
            }
        } else if (date.isNotBlank() && type.isNotBlank()) {
            dao.deleteByDateAndType(date, type, now)
        }
        WifiSyncWorker.enqueueOpportunisticSync(context)
        return Result.success("Transaction deleted and queued for sync.")
    }

    fun getCachedProducts(): List<ProductItem> = preferences.getCachedProducts()

    suspend fun getProducts(@Suppress("UNUSED_PARAMETER") forceRefresh: Boolean = false): List<ProductItem> {
        val entities = productDao.getAllActiveProductsSync()
        return if (entities.isNotEmpty()) {
            entities.map { ProductItem(it.name, it.price, it.category) }
        } else {
            preferences.getCachedProducts()
        }
    }

    suspend fun refreshProductsFromSheets(): Result<List<ProductItem>> {
        return Result.success(getProducts())
    }

    suspend fun syncProductsToSheets(@Suppress("UNUSED_PARAMETER") products: List<ProductItem>) {
        // Obsolete
    }

    suspend fun saveProduct(product: ProductItem): Result<String> {
        val now = System.currentTimeMillis()
        val existing = productDao.getProductByName(product.name)

        if (existing != null) {
            val updated = existing.copy(
                price = product.price,
                category = product.category,
                updatedAt = now,
                version = existing.version + 1,
                deletedAt = null,
                active = true
            )
            productDao.updateProduct(updated)
            preferences.saveProduct(product)

            enqueueSyncRecord(
                recordUuid = updated.uuid,
                entityType = SyncProtocol.EntityTypes.PRODUCT,
                operation = SyncProtocol.Operations.UPDATE,
                payload = json.encodeToString(ProductEntity.serializer(), updated)
            )
        } else {
            val newUuid = DeterministicProductMigration.generateDeterministicProductUuid(product.name, product.category)
            val newEntity = ProductEntity(
                uuid = newUuid,
                name = product.name,
                description = "",
                category = product.category,
                price = product.price,
                type = "Service",
                active = true,
                createdAt = now,
                updatedAt = now,
                version = 1L,
                deviceId = myDeviceId
            )
            val newId = productDao.insertProduct(newEntity)
            preferences.saveProduct(product)

            enqueueSyncRecord(
                recordUuid = newUuid,
                entityType = SyncProtocol.EntityTypes.PRODUCT,
                operation = SyncProtocol.Operations.CREATE,
                payload = json.encodeToString(ProductEntity.serializer(), newEntity.copy(id = newId)),
                version = 1L
            )
        }

        WifiSyncWorker.enqueueOpportunisticSync(context)
        return Result.success("Saved product & queued for Wi-Fi sync")
    }

    suspend fun deleteProduct(productName: String): Result<String> {
        val existing = productDao.getProductByName(productName)
        val now = System.currentTimeMillis()

        if (existing != null) {
            val updatedVersion = existing.version + 1
            productDao.softDeleteByUuid(existing.uuid, now)
            preferences.deleteProduct(productName)

            enqueueSyncRecord(
                recordUuid = existing.uuid,
                entityType = SyncProtocol.EntityTypes.PRODUCT,
                operation = SyncProtocol.Operations.DELETE,
                payload = json.encodeToString(ProductEntity.serializer(), existing.copy(deletedAt = now, version = updatedVersion)),
                version = updatedVersion
            )
        } else {
            preferences.deleteProduct(productName)
        }

        WifiSyncWorker.enqueueOpportunisticSync(context)
        return Result.success("Product deleted & queued for sync")
    }

    suspend fun resetDefaultProducts(): Result<String> {
        preferences.resetDefaultProducts()
        val defaultProds = preferences.getCachedProducts()
        for (p in defaultProds) {
            saveProduct(p)
        }
        return Result.success("Reset to default menu items")
    }

    // --- SYNC QUEUE HELPER ---
    private suspend fun enqueueSyncRecord(
        recordUuid: String,
        entityType: String,
        operation: String,
        payload: String,
        version: Long = 1L
    ) {
        try {
            syncQueueDao.insert(
                SyncQueueEntity(
                    recordUuid = recordUuid,
                    entityType = entityType,
                    operation = operation,
                    payload = payload,
                    version = version,
                    createdAt = System.currentTimeMillis(),
                    status = SyncProtocol.Status.PENDING
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepo", "Failed to enqueue sync record: ${e.message}")
        }
    }

    // --- WI-FI DISCOVERY & PAIRING ACTIONS ---
    fun startWifiDiscovery() = wifiDiscoveryManager.startDiscovery()
    fun stopWifiDiscovery() = wifiDiscoveryManager.stopDiscovery()

    suspend fun pairDevice(host: String, port: Int, pairingPin: String): Result<PairedDeviceEntity> {
        return wifiSyncEngine.pairWithWindows(host, port, pairingPin)
    }

    suspend fun unpairDevice(deviceId: String) {
        wifiSyncEngine.unpairDevice(deviceId)
    }

    suspend fun triggerSyncNow(): SyncSessionResult {
        return wifiSyncEngine.syncWithFirstPairedDevice()
    }

    suspend fun resolveConflict(conflictId: Long, keepLocal: Boolean) {
        wifiSyncEngine.resolveConflict(conflictId, keepLocal)
    }

    // --- BACKUP & RESTORE (UPDATED TO V3) ---
    @Serializable
    data class BackupData(
        val version: Int = 3,
        val timestamp: Long = System.currentTimeMillis(),
        val appVersion: String = "v6.2",
        val businessProfile: BusinessProfile,
        val appointmentSettings: AppointmentSettings,
        val themeMode: String = "System",
        val webAppUrl: String = "",
        val products: List<ProductItem>,
        val transactions: List<TransactionEntity>,
        val appointments: List<AppointmentEntity>,
        val pairedDevices: List<PairedDeviceEntity> = emptyList()
    )

    suspend fun exportBackup(): Result<File> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val transactions = dao.getAllTransactionsSync()
                val appointments = appointmentDao.getAllAppointmentsSync()
                val profile = preferences.getBusinessProfile()
                val settings = preferences.getAppointmentSettings()
                val prods = getProducts()
                val theme = preferences.getThemeMode()
                val webUrl = preferences.getWebAppUrl()
                val paired = pairedDeviceDao.getAllPairedDevicesSync()

                val backup = BackupData(
                    version = 3,
                    timestamp = System.currentTimeMillis(),
                    appVersion = "v6.1",
                    businessProfile = profile,
                    appointmentSettings = settings,
                    themeMode = theme,
                    webAppUrl = webUrl,
                    products = prods,
                    transactions = transactions,
                    appointments = appointments,
                    pairedDevices = paired
                )

                val jsonString = json.encodeToString(BackupData.serializer(), backup)

                val fileName = "MyBusiness_FullBackup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
                val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                if (!documentsDir.exists()) documentsDir.mkdirs()

                val file = File(documentsDir, fileName)
                file.writeText(jsonString)

                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun restoreBackup(fileUri: android.net.Uri): Result<String> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext Result.failure(Exception("Could not read backup file"))

                val backup = json.decodeFromString(BackupData.serializer(), jsonString)

                preferences.setBusinessProfile(backup.businessProfile)
                preferences.setAppointmentSettings(backup.appointmentSettings)
                preferences.setCachedProducts(backup.products)
                if (backup.themeMode.isNotBlank()) preferences.setThemeMode(backup.themeMode)
                if (backup.webAppUrl.isNotBlank()) preferences.setWebAppUrl(backup.webAppUrl)

                database.withTransaction {
                    dao.clearAll()
                    appointmentDao.clearAll()
                    productDao.clearAll()
                    syncQueueDao.clearAll()

                    // Re-insert transactions
                    backup.transactions.forEach {
                        dao.insertTransaction(
                            it.copy(
                                id = 0,
                                uuid = if (it.uuid.isNotBlank()) it.uuid else UUID.randomUUID().toString(),
                                deviceId = myDeviceId
                            )
                        )
                    }

                    // Re-insert appointments
                    backup.appointments.forEach {
                        appointmentDao.insertAppointment(
                            it.copy(
                                id = 0,
                                uuid = if (it.uuid.isNotBlank()) it.uuid else UUID.randomUUID().toString(),
                                deviceId = myDeviceId
                            )
                        )
                    }

                    // Re-insert products into Room with deterministic UUIDs
                    backup.products.forEach { p ->
                        val pUuid = DeterministicProductMigration.generateDeterministicProductUuid(p.name, p.category)
                        productDao.insertProduct(
                            ProductEntity(
                                uuid = pUuid,
                                name = p.name,
                                description = "",
                                category = p.category,
                                price = p.price,
                                type = "Service",
                                active = true,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                version = 1L,
                                deviceId = myDeviceId
                            )
                        )
                    }

                    // Re-insert paired devices
                    backup.pairedDevices.forEach { dev ->
                        pairedDeviceDao.insertOrUpdate(dev.copy(id = 0))
                    }
                }

                Result.success("Full system backup restored successfully!\n• ${backup.transactions.size} transactions restored\n• ${backup.appointments.size} appointments restored\n• ${backup.products.size} products restored\n• Business profile & sync state configured")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TransactionRepository? = null

        fun getInstance(context: Context): TransactionRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TransactionRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
