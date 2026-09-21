package com.example.sync.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.*
import com.example.data.model.AppointmentSettings
import com.example.data.pref.AppPreferences
import com.example.data.pref.BusinessProfile
import com.example.sync.client.WifiSyncClient
import com.example.sync.model.*
import com.example.sync.security.DeviceIdManager
import com.example.sync.security.SecureKeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class SyncState {
    IDLE,
    CONNECTING,
    SYNCING,
    SYNCED,
    OFFLINE,
    CONFLICT,
    ERROR
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
    private val client: WifiSyncClient = WifiSyncClient()
) {
    private val TAG = "WifiSyncEngine"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

    /**
     * Optional custom network checker hook for testing.
     */
    var networkChecker: (() -> Boolean)? = null

    /**
     * Checks if the device is currently connected to Wi-Fi or Ethernet.
     */
    fun isLocalNetworkConnected(): Boolean {
        networkChecker?.let { return it() }
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks reachability and identity of the paired Windows PC.
     */
    suspend fun checkReachability(device: PairedDeviceEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalNetworkConnected()) {
            return@withContext false
        }
        val myDeviceId = DeviceIdManager.getDeviceId(context)
        val healthResult = client.health(
            host = device.ipAddress,
            port = device.port,
            request = SyncHealthRequest(
                deviceId = myDeviceId,
                clientTimestamp = System.currentTimeMillis()
            ),
            certificateFingerprint = device.certificateFingerprint
        )
        if (healthResult.isSuccess) {
            pairedDeviceDao.updateLastSeen(device.deviceId, System.currentTimeMillis())
            true
        } else {
            false
        }
    }

    suspend fun pairWithWindows(
        host: String,
        port: Int,
        pairingPin: String,
        expectedFingerprint: String = ""
    ): Result<PairedDeviceEntity> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.CONNECTING
            _lastSyncMessage.value = "Connecting securely to Windows PC over HTTPS..."

            val myDeviceId = DeviceIdManager.getDeviceId(context)
            val myDeviceName = DeviceIdManager.getDeviceName(context)

            val pairReq = SyncPairRequest(
                deviceId = myDeviceId,
                deviceName = myDeviceName,
                pairingPin = pairingPin.trim(),
                clientPort = SyncProtocol.DEFAULT_PORT,
                clientTimestamp = System.currentTimeMillis()
            )

            val pairResult = client.pair(host, port, pairReq, expectedFingerprint)
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

            // Securely store authentication token in Android Keystore
            val tokenKey = SecureKeystoreManager.getDeviceAuthTokenKey(pairResp.deviceId)
            SecureKeystoreManager.storeSecret(context, tokenKey, pairResp.authToken)

            val pairedDevice = PairedDeviceEntity(
                deviceId = pairResp.deviceId,
                deviceName = pairResp.deviceName,
                ipAddress = host,
                port = port,
                pairingSecret = "KEYSTORE_PROTECTED", // Token reference; actual secret in Keystore
                certificateFingerprint = pairResp.serverCertificateFingerprint,
                lastSyncCursor = "",
                protocolVersion = SyncProtocol.PROTOCOL_VERSION,
                pairedAt = System.currentTimeMillis(),
                lastSyncAt = 0L,
                lastSeen = System.currentTimeMillis(),
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

    suspend fun unpairDevice(deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val device = pairedDeviceDao.getDeviceById(deviceId)
            if (device != null) {
                val authToken = getAuthTokenForDevice(device)
                if (authToken.isNotBlank()) {
                    try {
                        client.unpair(
                            host = device.ipAddress,
                            port = device.port,
                            request = SyncUnpairRequest(
                                deviceId = DeviceIdManager.getDeviceId(context),
                                authToken = authToken
                            ),
                            certificateFingerprint = device.certificateFingerprint
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Unpair remote notification failed: ${e.message}")
                    }
                }
                // Wipe credentials from Keystore
                SecureKeystoreManager.removeSecret(context, SecureKeystoreManager.getDeviceAuthTokenKey(deviceId))
                pairedDeviceDao.deleteDevice(deviceId)
            }
            _syncState.value = SyncState.IDLE
            _lastSyncMessage.value = "Unpaired device"
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getAuthTokenForDevice(device: PairedDeviceEntity): String {
        val fromKeystore = SecureKeystoreManager.getSecret(
            context,
            SecureKeystoreManager.getDeviceAuthTokenKey(device.deviceId)
        )
        if (!fromKeystore.isNullOrBlank()) return fromKeystore
        // Fallback if migrated from older plaintext version
        return if (device.pairingSecret != "KEYSTORE_PROTECTED") device.pairingSecret else ""
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
        val authToken = getAuthTokenForDevice(device)

        if (authToken.isBlank()) {
            _syncState.value = SyncState.ERROR
            _lastSyncMessage.value = "Missing authentication credentials for ${device.deviceName}. Re-pairing required."
            return@withContext SyncSessionResult(false, errorMessage = "Missing auth token")
        }

        try {
            _syncState.value = SyncState.CONNECTING
            _lastSyncMessage.value = "Verifying connection with ${device.deviceName}..."

            // Step 1: Reachability / Health check
            val isReachable = checkReachability(device)
            if (!isReachable) {
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

            // Step 2: PULL changes from Windows using change cursor pagination
            var currentCursor = device.lastSyncCursor
            var hasMore = true
            val ackList = mutableListOf<String>()

            while (hasMore) {
                val getChangesResult = client.getChanges(
                    host = device.ipAddress,
                    port = device.port,
                    request = SyncGetChangesRequest(
                        deviceId = myDeviceId,
                        authToken = authToken,
                        cursor = currentCursor,
                        limit = 50,
                        sinceTimestamp = device.lastSyncAt
                    ),
                    certificateFingerprint = device.certificateFingerprint
                )

                if (getChangesResult.isFailure) {
                    val err = getChangesResult.exceptionOrNull()?.message ?: "Get changes failed"
                    Log.e(TAG, "Pull changes failed: $err")
                    break
                }

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

                currentCursor = changesResp.nextCursor
                hasMore = changesResp.hasMore && changesResp.records.isNotEmpty()
            }

            // Acknowledge received records
            if (ackList.isNotEmpty()) {
                try {
                    client.acknowledge(
                        host = device.ipAddress,
                        port = device.port,
                        request = SyncAckRequest(
                            deviceId = myDeviceId,
                            authToken = authToken,
                            acknowledgedUuids = ackList
                        ),
                        certificateFingerprint = device.certificateFingerprint
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send acknowledgement: ${e.message}")
                }
            }

            // Update change cursor and sync timestamp
            val now = System.currentTimeMillis()
            pairedDeviceDao.updateLastSyncCursor(device.deviceId, currentCursor, now)

            // Step 3: PUSH changes from Android to Windows with Per-Record ACK
            val pendingQueue = syncQueueDao.getPendingItems()
            val pushRecords = mutableListOf<SyncRecord>()

            if (pendingQueue.isNotEmpty()) {
                for (item in pendingQueue) {
                    pushRecords.add(
                        SyncRecord(
                            uuid = item.recordUuid,
                            entityType = item.entityType,
                            operation = item.operation,
                            version = item.version,
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
                    host = device.ipAddress,
                    port = device.port,
                    request = SyncPushChangesRequest(
                        deviceId = myDeviceId,
                        authToken = authToken,
                        records = pushRecords
                    ),
                    certificateFingerprint = device.certificateFingerprint
                )

                if (pushResult.isSuccess) {
                    val resp = pushResult.getOrThrow()
                    totalPushed = resp.appliedCount

                    // Per-Record ACK: Only remove items confirmed as APPLIED or DUPLICATE
                    if (resp.results.isNotEmpty()) {
                        for (result in resp.results) {
                            when (result.status) {
                                SyncProtocol.RecordAckStatus.APPLIED,
                                SyncProtocol.RecordAckStatus.DUPLICATE -> {
                                    syncQueueDao.deleteByRecordUuid(result.recordUuid)
                                }
                                SyncProtocol.RecordAckStatus.CONFLICT -> {
                                    syncQueueDao.updateStatusByRecordUuid(
                                        result.recordUuid,
                                        SyncProtocol.Status.CONFLICT,
                                        result.error
                                    )
                                    conflictsDetected++
                                }
                                SyncProtocol.RecordAckStatus.RETRY -> {
                                    syncQueueDao.markRetryByRecordUuid(result.recordUuid, result.error)
                                }
                                SyncProtocol.RecordAckStatus.REJECTED -> {
                                    syncQueueDao.updateStatusByRecordUuid(
                                        result.recordUuid,
                                        SyncProtocol.Status.FAILED,
                                        result.error
                                    )
                                }
                            }
                        }
                    } else {
                        // Fallback for legacy server response: delete items that were pushed
                        for (record in pushRecords) {
                            syncQueueDao.deleteByRecordUuid(record.uuid)
                        }
                    }
                } else {
                    Log.w(TAG, "Push changes failed: ${pushResult.exceptionOrNull()?.message}")
                }
            }

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

    suspend fun applyIncomingRecord(record: SyncRecord, localDeviceId: String): Boolean {
        if (record.uuid.isBlank() || record.entityType.isBlank() || record.dataJson.isBlank()) {
            return false
        }

        when (record.entityType) {
            SyncProtocol.EntityTypes.TRANSACTION -> {
                val incoming = json.decodeFromString(TransactionEntity.serializer(), record.dataJson)
                val existing = transactionDao.getTransactionByUuid(record.uuid)
                val isRemoteDelete = record.operation == SyncProtocol.Operations.DELETE || record.deletedAt != null || incoming.deletedAt != null

                if (existing == null) {
                    // Record does not exist locally
                    if (isRemoteDelete) {
                        // Insert tombstone so it will not be revived
                        transactionDao.insertTransaction(
                            incoming.copy(
                                id = 0,
                                isSynced = true,
                                deletedAt = incoming.deletedAt ?: record.updatedAt,
                                version = record.version
                            )
                        )
                    } else {
                        transactionDao.insertTransaction(incoming.copy(id = 0, isSynced = true, version = record.version))
                    }
                    return true
                } else {
                    // Record exists locally
                    if (existing.version == record.version) {
                        // Identical version
                        return true
                    }
                    if (record.version < existing.version) {
                        // Stale update from remote
                        return true
                    }

                    // Remote version is higher: check if local has uncommitted pending edits
                    val pendingItem = syncQueueDao.getItemByRecordUuid(record.uuid)
                    if (pendingItem != null) {
                        // Conflict: concurrent local and remote modification!
                        recordConflict(
                            recordUuid = record.uuid,
                            entityType = record.entityType,
                            localVersion = existing.version,
                            remoteVersion = record.version,
                            localData = json.encodeToString(TransactionEntity.serializer(), existing),
                            remoteData = record.dataJson
                        )
                        return false
                    }

                    // No local pending edits: safe fast-forward
                    if (isRemoteDelete) {
                        transactionDao.softDeleteById(existing.id, incoming.deletedAt ?: record.updatedAt)
                    } else {
                        transactionDao.updateTransaction(
                            incoming.copy(
                                id = existing.id,
                                isSynced = true,
                                updatedAt = record.updatedAt,
                                version = record.version
                            )
                        )
                    }
                    return true
                }
            }

            SyncProtocol.EntityTypes.APPOINTMENT -> {
                val incoming = json.decodeFromString(AppointmentEntity.serializer(), record.dataJson)
                val existing = appointmentDao.getAppointmentByUuid(record.uuid)
                val isRemoteDelete = record.operation == SyncProtocol.Operations.DELETE || record.deletedAt != null || incoming.deletedAt != null

                if (existing == null) {
                    if (isRemoteDelete) {
                        appointmentDao.insertAppointment(
                            incoming.copy(
                                id = 0,
                                isSynced = true,
                                deletedAt = incoming.deletedAt ?: record.updatedAt,
                                version = record.version
                            )
                        )
                    } else {
                        appointmentDao.insertAppointment(incoming.copy(id = 0, isSynced = true, version = record.version))
                    }
                    return true
                } else {
                    if (existing.version == record.version) {
                        return true
                    }
                    if (record.version < existing.version) {
                        return true
                    }

                    val pendingItem = syncQueueDao.getItemByRecordUuid(record.uuid)
                    if (pendingItem != null) {
                        recordConflict(
                            recordUuid = record.uuid,
                            entityType = record.entityType,
                            localVersion = existing.version,
                            remoteVersion = record.version,
                            localData = json.encodeToString(AppointmentEntity.serializer(), existing),
                            remoteData = record.dataJson
                        )
                        return false
                    }

                    if (isRemoteDelete) {
                        appointmentDao.softDeleteAppointmentById(existing.id, incoming.deletedAt ?: record.updatedAt)
                    } else {
                        appointmentDao.updateAppointment(
                            incoming.copy(
                                id = existing.id,
                                isSynced = true,
                                updatedAt = record.updatedAt,
                                version = record.version
                            )
                        )
                    }
                    return true
                }
            }

            SyncProtocol.EntityTypes.PRODUCT -> {
                val incoming = json.decodeFromString(ProductEntity.serializer(), record.dataJson)
                val existing = productDao.getProductByUuid(record.uuid)
                val isRemoteDelete = record.operation == SyncProtocol.Operations.DELETE || record.deletedAt != null || incoming.deletedAt != null

                if (existing == null) {
                    if (isRemoteDelete) {
                        productDao.insertProduct(
                            incoming.copy(
                                id = 0,
                                deletedAt = incoming.deletedAt ?: record.updatedAt,
                                version = record.version
                            )
                        )
                    } else {
                        productDao.insertProduct(incoming.copy(id = 0, version = record.version))
                    }
                    return true
                } else {
                    if (existing.version == record.version) {
                        return true
                    }
                    if (record.version < existing.version) {
                        return true
                    }

                    val pendingItem = syncQueueDao.getItemByRecordUuid(record.uuid)
                    if (pendingItem != null) {
                        recordConflict(
                            recordUuid = record.uuid,
                            entityType = record.entityType,
                            localVersion = existing.version,
                            remoteVersion = record.version,
                            localData = json.encodeToString(ProductEntity.serializer(), existing),
                            remoteData = record.dataJson
                        )
                        return false
                    }

                    if (isRemoteDelete) {
                        productDao.softDeleteByUuid(existing.uuid, incoming.deletedAt ?: record.updatedAt)
                    } else {
                        productDao.updateProduct(
                            incoming.copy(
                                id = existing.id,
                                updatedAt = record.updatedAt,
                                version = record.version
                            )
                        )
                    }
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
                createdAt = System.currentTimeMillis(),
                resolutionStrategy = "UNRESOLVED",
                conflictStatus = "PENDING"
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
                    deletedAt = t.deletedAt,
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
                    deletedAt = a.deletedAt,
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
                    deletedAt = p.deletedAt,
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
            val record = SyncRecord(
                uuid = target.recordUuid,
                entityType = target.entityType,
                operation = SyncProtocol.Operations.UPDATE,
                version = target.remoteVersion,
                dataJson = target.remoteData
            )
            applyIncomingRecord(record, DeviceIdManager.getDeviceId(context))
            syncConflictDao.markResolved(conflictId, "REMOTE_WINS")
        }
    }
}
