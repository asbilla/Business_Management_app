package com.example.sync.engine

import android.content.Context
import android.util.Log
import com.example.data.local.*
import com.example.data.model.AppointmentSettings
import com.example.data.model.ProductItem
import com.example.data.pref.AppPreferences
import com.example.data.pref.BusinessProfile
import com.example.sync.client.WifiSyncClient
import com.example.sync.model.*
import com.example.sync.security.DeviceIdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

enum class SyncState {
    IDLE,
    CONNECTING,
    SYNCING,
    SYNCED,
    OFFLINE,
    ERROR,
    CONFLICT
}

data class SyncSessionResult(
    val success: Boolean,
    val pulledCount: Int = 0,
    val pushedCount: Int = 0,
    val conflictsCount: Int = 0,
    val errorMessage: String? = null
)

class WifiSyncEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val client: WifiSyncClient = WifiSyncClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    private val TAG = "WifiSyncEngine"

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow("Ready")
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage.asStateFlow()

    private val transactionDao = database.transactionDao()
    private val appointmentDao = database.appointmentDao()
    private val productDao = database.productDao()
    private val syncQueueDao = database.syncQueueDao()
    private val pairedDeviceDao = database.pairedDeviceDao()
    private val syncConflictDao = database.syncConflictDao()

    suspend fun pairWithWindows(
        host: String,
        port: Int,
        pairingPin: String
    ): Result<PairedDeviceEntity> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.CONNECTING
            _lastSyncMessage.value = "Connecting to Windows PC..."

            val myDeviceId = DeviceIdManager.getDeviceId(context)
            val myDeviceName = DeviceIdManager.getDeviceName(context)

            val pairReq = SyncPairRequest(
                deviceId = myDeviceId,
                deviceName = myDeviceName,
                pairingPin = pairingPin,
                clientPort = SyncProtocol.DEFAULT_PORT
            )

            val pairResult = client.pair(host, port, pairReq)
            if (pairResult.isFailure) {
                _syncState.value = SyncState.ERROR
                val err = pairResult.exceptionOrNull()?.message ?: "Pairing request failed"
                _lastSyncMessage.value = err
                return@withContext Result.failure(Exception(err))
            }

            val pairResp = pairResult.getOrThrow()
            if (!pairResp.success) {
                _syncState.value = SyncState.ERROR
                _lastSyncMessage.value = pairResp.message
                return@withContext Result.failure(Exception(pairResp.message))
            }

            val pairedDevice = PairedDeviceEntity(
                deviceId = pairResp.deviceId,
                deviceName = pairResp.deviceName,
                ipAddress = host,
                port = port,
                pairingSecret = pairResp.authToken,
                pairedAt = System.currentTimeMillis(),
                lastSyncAt = 0L,
                status = "PAIRED"
            )

            pairedDeviceDao.insertOrUpdate(pairedDevice)
            _syncState.value = SyncState.SYNCED
            _lastSyncMessage.value = "Paired with ${pairResp.deviceName}"

            // Perform initial synchronization immediately
            performFullSync(pairedDevice)

            Result.success(pairedDevice)
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
            _lastSyncMessage.value = "Pairing failed: ${e.message}"
            Result.failure(e)
        }
    }

    suspend fun syncWithFirstPairedDevice(): SyncSessionResult = withContext(Dispatchers.IO) {
        val paired = pairedDeviceDao.getAllPairedDevicesSync().firstOrNull { it.status == "PAIRED" }
        if (paired == null) {
            _syncState.value = SyncState.OFFLINE
            _lastSyncMessage.value = "No paired Windows PC configured"
            return@withContext SyncSessionResult(false, errorMessage = "No paired Windows device")
        }
        performFullSync(paired)
    }

    suspend fun performFullSync(device: PairedDeviceEntity): SyncSessionResult = withContext(Dispatchers.IO) {
        val myDeviceId = DeviceIdManager.getDeviceId(context)
        val myDeviceName = DeviceIdManager.getDeviceName(context)

        try {
            _syncState.value = SyncState.CONNECTING
            _lastSyncMessage.value = "Checking connection with ${device.deviceName}..."

            // Step 1: Handshake
            val helloResp = client.hello(
                device.ipAddress,
                device.port,
                SyncHelloRequest(
                    deviceId = myDeviceId,
                    deviceName = myDeviceName
                )
            )

            if (helloResp.isFailure) {
                _syncState.value = SyncState.OFFLINE
                val msg = "Windows PC unavailable (${device.ipAddress})"
                _lastSyncMessage.value = msg
                return@withContext SyncSessionResult(false, errorMessage = msg)
            }

            _syncState.value = SyncState.SYNCING
            _lastSyncMessage.value = "Synchronizing data..."

            var totalPulled = 0
            var totalPushed = 0
            var conflictsDetected = 0

            // Step 2: PULL changes from Windows
            val getChangesResult = client.getChanges(
                device.ipAddress,
                device.port,
                SyncGetChangesRequest(
                    deviceId = myDeviceId,
                    authToken = device.pairingSecret,
                    sinceTimestamp = device.lastSyncAt
                )
            )

            val ackList = mutableListOf<String>()
            if (getChangesResult.isSuccess) {
                val changesResp = getChangesResult.getOrThrow()
                for (record in changesResp.records) {
                    try {
                        val applied = applyIncomingRecord(record, myDeviceId)
                        if (applied) {
                            ackList.add(record.uuid)
                            totalPulled++
                        } else {
                            conflictsDetected++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying incoming record ${record.uuid}: ${e.message}")
                    }
                }

                // Acknowledge received records
                if (ackList.isNotEmpty()) {
                    client.acknowledge(
                        device.ipAddress,
                        device.port,
                        SyncAckRequest(
                            deviceId = myDeviceId,
                            authToken = device.pairingSecret,
                            acknowledgedUuids = ackList
                        )
                    )
                }
            }

            // Step 3: PUSH changes from Android to Windows
            val pendingQueue = syncQueueDao.getPendingItems()
            val pushRecords = mutableListOf<SyncRecord>()

            if (pendingQueue.isNotEmpty()) {
                for (item in pendingQueue) {
                    pushRecords.add(
                        SyncRecord(
                            uuid = item.recordUuid,
                            entityType = item.entityType,
                            operation = item.operation,
                            dataJson = item.payload,
                            deviceId = myDeviceId
                        )
                    )
                }
            } else if (device.lastSyncAt == 0L) {
                // Initial Sync: push all local records
                pushRecords.addAll(collectAllLocalRecordsForInitialSync(myDeviceId))
            }

            if (pushRecords.isNotEmpty()) {
                val pushResult = client.pushChanges(
                    device.ipAddress,
                    device.port,
                    SyncPushChangesRequest(
                        deviceId = myDeviceId,
                        authToken = device.pairingSecret,
                        records = pushRecords
                    )
                )

                if (pushResult.isSuccess) {
                    val resp = pushResult.getOrThrow()
                    totalPushed = resp.appliedCount
                    // Mark completed in queue
                    syncQueueDao.deleteCompleted()
                    for (q in pendingQueue) {
                        syncQueueDao.deleteById(q.id)
                    }
                } else {
                    Log.w(TAG, "Push changes failed: ${pushResult.exceptionOrNull()?.message}")
                }
            }

            // Step 4: Update last sync timestamp
            val now = System.currentTimeMillis()
            pairedDeviceDao.updateLastSync(device.deviceId, now)
            _lastSyncTimestamp.value = now

            if (conflictsDetected > 0) {
                _syncState.value = SyncState.CONFLICT
                _lastSyncMessage.value = "Synced with $conflictsDetected conflict(s) for review"
            } else {
                _syncState.value = SyncState.SYNCED
                _lastSyncMessage.value = "Successfully synchronized (Pulled $totalPulled, Pushed $totalPushed)"
            }

            SyncSessionResult(
                success = true,
                pulledCount = totalPulled,
                pushedCount = totalPushed,
                conflictsCount = conflictsDetected
            )
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
            val msg = "Sync failed: ${e.message}"
            _lastSyncMessage.value = msg
            SyncSessionResult(false, errorMessage = msg)
        }
    }

    private suspend fun applyIncomingRecord(record: SyncRecord, localDeviceId: String): Boolean {
        // Validation
        if (record.uuid.isBlank() || record.entityType.isBlank() || record.dataJson.isBlank()) {
            return false
        }

        when (record.entityType) {
            SyncProtocol.EntityTypes.TRANSACTION -> {
                val incoming = json.decodeFromString(TransactionEntity.serializer(), record.dataJson)
                val existing = transactionDao.getTransactionByUuid(record.uuid)

                if (existing == null) {
                    transactionDao.insertTransaction(incoming.copy(id = 0, isSynced = true))
                    return true
                } else {
                    // Conflict detection
                    if (existing.version > record.version && existing.amount != incoming.amount) {
                        recordConflict(
                            recordUuid = record.uuid,
                            entityType = record.entityType,
                            localVersion = existing.version,
                            remoteVersion = record.version,
                            localData = json.encodeToString(TransactionEntity.serializer(), existing),
                            remoteData = record.dataJson
                        )
                        return false
                    } else {
                        // Apply remote update or tombstone
                        transactionDao.updateTransaction(
                            incoming.copy(
                                id = existing.id,
                                isSynced = true,
                                updatedAt = record.updatedAt
                            )
                        )
                        return true
                    }
                }
            }

            SyncProtocol.EntityTypes.APPOINTMENT -> {
                val incoming = json.decodeFromString(AppointmentEntity.serializer(), record.dataJson)
                val existing = appointmentDao.getAppointmentByUuid(record.uuid)

                if (existing == null) {
                    appointmentDao.insertAppointment(incoming.copy(id = 0, isSynced = true))
                    return true
                } else {
                    if (existing.version > record.version && existing.status != incoming.status) {
                        recordConflict(
                            recordUuid = record.uuid,
                            entityType = record.entityType,
                            localVersion = existing.version,
                            remoteVersion = record.version,
                            localData = json.encodeToString(AppointmentEntity.serializer(), existing),
                            remoteData = record.dataJson
                        )
                        return false
                    } else {
                        appointmentDao.updateAppointment(
                            incoming.copy(
                                id = existing.id,
                                isSynced = true,
                                updatedAt = record.updatedAt
                            )
                        )
                        return true
                    }
                }
            }

            SyncProtocol.EntityTypes.PRODUCT -> {
                val incoming = json.decodeFromString(ProductEntity.serializer(), record.dataJson)
                val existing = productDao.getProductByUuid(record.uuid)

                if (existing == null) {
                    productDao.insertProduct(incoming.copy(id = 0))
                    // Also update in cached products for immediate UI compatibility
                    preferences.saveProduct(ProductItem(incoming.name, incoming.price, incoming.category))
                    return true
                } else {
                    productDao.updateProduct(incoming.copy(id = existing.id, updatedAt = record.updatedAt))
                    preferences.saveProduct(ProductItem(incoming.name, incoming.price, incoming.category))
                    return true
                }
            }

            SyncProtocol.EntityTypes.BUSINESS_PROFILE -> {
                val incoming = json.decodeFromString(BusinessProfile.serializer(), record.dataJson)
                preferences.setBusinessProfile(incoming)
                return true
            }

            SyncProtocol.EntityTypes.APPOINTMENT_SETTINGS -> {
                val incoming = json.decodeFromString(AppointmentSettings.serializer(), record.dataJson)
                preferences.setAppointmentSettings(incoming)
                return true
            }
        }
        return true
    }

    private suspend fun recordConflict(
        recordUuid: String,
        entityType: String,
        localVersion: Long,
        remoteVersion: Long,
        localData: String,
        remoteData: String
    ) {
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                recordUuid = recordUuid,
                entityType = entityType,
                localVersion = localVersion,
                remoteVersion = remoteVersion,
                localData = localData,
                remoteData = remoteData,
                resolved = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun collectAllLocalRecordsForInitialSync(deviceId: String): List<SyncRecord> {
        val records = mutableListOf<SyncRecord>()

        // 1. Transactions
        val transactions = transactionDao.getAllTransactionsIncludingDeletedSync()
        for (t in transactions) {
            records.add(
                SyncRecord(
                    uuid = t.uuid,
                    entityType = SyncProtocol.EntityTypes.TRANSACTION,
                    operation = if (t.deletedAt != null) SyncProtocol.Operations.DELETE else SyncProtocol.Operations.CREATE,
                    version = t.version,
                    updatedAt = t.updatedAt,
                    deviceId = deviceId,
                    dataJson = json.encodeToString(TransactionEntity.serializer(), t)
                )
            )
        }

        // 2. Appointments
        val appointments = appointmentDao.getAllAppointmentsIncludingDeletedSync()
        for (a in appointments) {
            records.add(
                SyncRecord(
                    uuid = a.uuid,
                    entityType = SyncProtocol.EntityTypes.APPOINTMENT,
                    operation = if (a.deletedAt != null) SyncProtocol.Operations.DELETE else SyncProtocol.Operations.CREATE,
                    version = a.version,
                    updatedAt = a.updatedAt,
                    deviceId = deviceId,
                    dataJson = json.encodeToString(AppointmentEntity.serializer(), a)
                )
            )
        }

        // 3. Products
        val products = productDao.getAllProductsSync()
        for (p in products) {
            records.add(
                SyncRecord(
                    uuid = p.uuid,
                    entityType = SyncProtocol.EntityTypes.PRODUCT,
                    operation = if (p.deletedAt != null) SyncProtocol.Operations.DELETE else SyncProtocol.Operations.CREATE,
                    version = p.version,
                    updatedAt = p.updatedAt,
                    deviceId = deviceId,
                    dataJson = json.encodeToString(ProductEntity.serializer(), p)
                )
            )
        }

        // 4. Business Profile
        val profile = preferences.getBusinessProfile()
        records.add(
            SyncRecord(
                uuid = "BUSINESS_PROFILE_SINGLETON",
                entityType = SyncProtocol.EntityTypes.BUSINESS_PROFILE,
                operation = SyncProtocol.Operations.UPDATE,
                version = 1L,
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId,
                dataJson = json.encodeToString(BusinessProfile.serializer(), profile)
            )
        )

        // 5. Appointment Settings
        val settings = preferences.getAppointmentSettings()
        records.add(
            SyncRecord(
                uuid = "APPOINTMENT_SETTINGS_SINGLETON",
                entityType = SyncProtocol.EntityTypes.APPOINTMENT_SETTINGS,
                operation = SyncProtocol.Operations.UPDATE,
                version = 1L,
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId,
                dataJson = json.encodeToString(AppointmentSettings.serializer(), settings)
            )
        )

        return records
    }

    suspend fun resolveConflict(conflictId: Long, keepLocal: Boolean) = withContext(Dispatchers.IO) {
        val conflicts = syncConflictDao.getUnresolvedConflictsSync()
        val target = conflicts.find { it.id == conflictId } ?: return@withContext
        if (keepLocal) {
            syncConflictDao.markResolved(conflictId, "LOCAL_WINS")
        } else {
            // Remote wins: apply remote data
            val record = SyncRecord(
                uuid = target.recordUuid,
                entityType = target.entityType,
                operation = SyncProtocol.Operations.UPDATE,
                dataJson = target.remoteData
            )
            applyIncomingRecord(record, DeviceIdManager.getDeviceId(context))
            syncConflictDao.markResolved(conflictId, "REMOTE_WINS")
        }
    }
}
