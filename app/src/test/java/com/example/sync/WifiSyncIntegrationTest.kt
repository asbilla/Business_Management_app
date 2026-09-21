package com.example.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.model.ProductItem
import com.example.data.pref.AppPreferences
import com.example.data.pref.BusinessProfile
import com.example.data.repository.DeterministicProductMigration
import com.example.data.repository.TransactionRepository
import com.example.sync.client.WifiSyncClient
import com.example.sync.engine.SyncState
import com.example.sync.engine.WifiSyncEngine
import com.example.sync.model.*
import com.example.sync.security.LocalTlsManager
import com.example.sync.security.SecureKeystoreManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WifiSyncIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: AppPreferences

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = AppPreferences.getInstance(context)
        preferences.clearAll()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createMockSyncEngine(handler: (Request) -> Response): WifiSyncEngine {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                handler(chain.request())
            }
            .build()
        val syncClient = WifiSyncClient(json, okHttpClient)
        val engine = WifiSyncEngine(context, database, preferences, syncClient)
        engine.networkChecker = { true }
        return engine
    }

    private fun jsonResponse(request: Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    // --- TEST 1: Pairing Handshake (Valid PIN, Token, Fingerprint) ---
    @Test
    fun test01_PairingHandshakeSuccess() = runBlocking {
        val expectedFingerprint = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        val engine = createMockSyncEngine { request ->
            when (request.url.encodedPath) {
                "/api/sync/pair" -> {
                    val resp = SyncPairResponse(
                        success = true,
                        deviceId = "WIN-SRV-01",
                        deviceName = "Windows Desktop Server",
                        authToken = "AUTH_TOKEN_TEST_12345",
                        serverCertificateFingerprint = expectedFingerprint,
                        message = "Pairing successful"
                    )
                    jsonResponse(request, 200, json.encodeToString(SyncPairResponse.serializer(), resp))
                }
                "/api/sync/health" -> {
                    val health = SyncHealthResponse(deviceId = "WIN-SRV-01", deviceName = "Windows Desktop Server")
                    jsonResponse(request, 200, json.encodeToString(SyncHealthResponse.serializer(), health))
                }
                "/api/sync/changes/get" -> {
                    val getResp = SyncGetChangesResponse(records = emptyList(), nextCursor = "c1")
                    jsonResponse(request, 200, json.encodeToString(SyncGetChangesResponse.serializer(), getResp))
                }
                else -> jsonResponse(request, 404, "Not Found")
            }
        }

        val pairResult = engine.pairWithWindows("192.168.1.50", 54320, "123456")
        assertTrue("Pairing must succeed with valid PIN", pairResult.isSuccess)
        val device = pairResult.getOrThrow()
        assertEquals("WIN-SRV-01", device.deviceId)
        assertEquals(expectedFingerprint, device.certificateFingerprint)

        // Verify device saved in Room
        val saved = database.pairedDeviceDao().getDeviceById("WIN-SRV-01")
        assertNotNull(saved)
        assertEquals("PAIRED", saved?.status)

        // Verify token saved in Keystore
        val token = SecureKeystoreManager.getSecret(context, SecureKeystoreManager.getDeviceAuthTokenKey("WIN-SRV-01"))
        assertEquals("AUTH_TOKEN_TEST_12345", token)
    }

    // --- TEST 2: Pairing Rejection on Invalid PIN ---
    @Test
    fun test02_PairingRejectionOnInvalidPin() = runBlocking {
        val engine = createMockSyncEngine { request ->
            val err = SyncErrorResponse(SyncProtocol.ErrorCodes.INVALID_PIN, "Invalid pairing PIN provided")
            jsonResponse(request, 401, json.encodeToString(SyncErrorResponse.serializer(), err))
        }

        val result = engine.pairWithWindows("192.168.1.50", 54320, "000000")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid pairing PIN") == true)
    }

    // --- TEST 3: TLS Certificate Pinning Verification Logic ---
    @Test
    fun test03_TlsCertificatePinningMismatch() {
        val validFingerprint = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        val mismatchedFingerprint = "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"

        val matches = LocalTlsManager.verifyFingerprint(validFingerprint, validFingerprint)
        assertTrue("Identical fingerprints must match", matches)

        val fails = LocalTlsManager.verifyFingerprint(validFingerprint, mismatchedFingerprint)
        assertFalse("Mismatched certificate fingerprint must fail verification", fails)
    }

    // --- TEST 4: Reachability Pre-Flight Check (/api/sync/health) ---
    @Test
    fun test04_HealthCheckReachability() = runBlocking {
        val engine = createMockSyncEngine { request ->
            if (request.url.encodedPath == "/api/sync/health") {
                jsonResponse(request, 200, json.encodeToString(SyncHealthResponse.serializer(), SyncHealthResponse(deviceId = "WIN-01", deviceName = "PC")))
            } else {
                jsonResponse(request, 404, "Not Found")
            }
        }

        val paired = PairedDeviceEntity(
            deviceId = "WIN-01",
            deviceName = "PC",
            ipAddress = "192.168.1.50",
            port = 54320,
            status = "PAIRED"
        )
        database.pairedDeviceDao().insertOrUpdate(paired)

        val reachable = engine.checkReachability(paired)
        assertTrue(reachable)
    }

    // --- TEST 5: Create Transaction Synchronization ---
    @Test
    fun test05_CreateTransactionSync() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val testUuid = UUID.randomUUID().toString()
        val tx = TransactionEntity(
            uuid = testUuid,
            date = "2026-09-21",
            timestamp = System.currentTimeMillis(),
            type = "Daily Income",
            category = "VIP Treatment",
            amount = 120.0,
            version = 1L,
            updatedAt = System.currentTimeMillis()
        )

        val record = SyncRecord(
            uuid = testUuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.CREATE,
            version = 1L,
            updatedAt = tx.updatedAt,
            dataJson = json.encodeToString(TransactionEntity.serializer(), tx)
        )

        val applied = engine.applyIncomingRecord(record, "MY-ANDROID")
        assertTrue(applied)

        val saved = database.transactionDao().getTransactionByUuid(testUuid)
        assertNotNull(saved)
        assertEquals(120.0, saved?.amount ?: 0.0, 0.001)
        assertEquals("VIP Treatment", saved?.category)
        assertEquals(1L, saved?.version)
        assertNull(saved?.deletedAt)
    }

    // --- TEST 6: Update Transaction Synchronization ---
    @Test
    fun test06_UpdateTransactionSync() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val testUuid = UUID.randomUUID().toString()
        val initialTx = TransactionEntity(
            uuid = testUuid,
            date = "2026-09-21",
            timestamp = 1000L,
            type = "Daily Income",
            category = "Basic Cut",
            amount = 30.0,
            version = 1L,
            updatedAt = 1000L
        )
        database.transactionDao().insertTransaction(initialTx)

        val updatedTx = initialTx.copy(amount = 40.0, category = "Basic Cut + Shampoo", version = 2L, updatedAt = 2000L)
        val record = SyncRecord(
            uuid = testUuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.UPDATE,
            version = 2L,
            updatedAt = 2000L,
            dataJson = json.encodeToString(TransactionEntity.serializer(), updatedTx)
        )

        val applied = engine.applyIncomingRecord(record, "MY-ANDROID")
        assertTrue(applied)

        val saved = database.transactionDao().getTransactionByUuid(testUuid)
        assertEquals(40.0, saved?.amount ?: 0.0, 0.001)
        assertEquals("Basic Cut + Shampoo", saved?.category)
        assertEquals(2L, saved?.version)
    }

    // --- TEST 7: Soft-Delete Tombstone Synchronization ---
    @Test
    fun test07_SoftDeleteTombstoneSync() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val testUuid = UUID.randomUUID().toString()
        val tx = TransactionEntity(
            uuid = testUuid,
            date = "2026-09-21",
            timestamp = 1000L,
            type = "Daily Income",
            category = "Service",
            amount = 50.0,
            version = 1L
        )
        database.transactionDao().insertTransaction(tx)

        val delTime = 3000L
        val deleteRecord = SyncRecord(
            uuid = testUuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.DELETE,
            version = 2L,
            updatedAt = delTime,
            deletedAt = delTime,
            dataJson = json.encodeToString(TransactionEntity.serializer(), tx.copy(deletedAt = delTime, version = 2L))
        )

        val applied = engine.applyIncomingRecord(deleteRecord, "MY-ANDROID")
        assertTrue(applied)

        // Tombstone exists in DB with deletedAt set
        val tombstone = database.transactionDao().getTransactionByUuid(testUuid)
        assertNotNull(tombstone)
        assertNotNull(tombstone?.deletedAt)

        // Active query ignores it
        val active = database.transactionDao().getAllTransactionsSync()
        assertTrue(active.none { it.uuid == testUuid })
    }

    // --- TEST 8: Conflict Detection on Concurrent Edits ---
    @Test
    fun test08_ConflictDetectionOnConcurrentEdit() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val testUuid = UUID.randomUUID().toString()
        val baseTx = TransactionEntity(
            uuid = testUuid,
            date = "2026-09-21",
            timestamp = 1000L,
            type = "Daily Income",
            category = "Massage",
            amount = 80.0,
            version = 1L
        )
        database.transactionDao().insertTransaction(baseTx)

        // Local edit queued
        database.syncQueueDao().insert(
            SyncQueueEntity(
                recordUuid = testUuid,
                entityType = SyncProtocol.EntityTypes.TRANSACTION,
                operation = SyncProtocol.Operations.UPDATE,
                payload = json.encodeToString(TransactionEntity.serializer(), baseTx.copy(amount = 90.0, version = 2L)),
                version = 2L,
                status = "PENDING"
            )
        )

        // Remote concurrent edit arriving with identical version 2
        val remoteTx = baseTx.copy(amount = 100.0, version = 2L, updatedAt = 2000L)
        val remoteRecord = SyncRecord(
            uuid = testUuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.UPDATE,
            version = 2L,
            updatedAt = 2000L,
            dataJson = json.encodeToString(TransactionEntity.serializer(), remoteTx)
        )

        val applied = engine.applyIncomingRecord(remoteRecord, "MY-ANDROID")
        assertFalse("Concurrent modification must trigger conflict instead of overwriting!", applied)

        // Verify conflict recorded in SyncConflictEntity
        val conflicts = database.syncConflictDao().getUnresolvedConflictsSync()
        assertEquals(1, conflicts.size)
        assertEquals(testUuid, conflicts[0].recordUuid)
        assertEquals("PENDING", conflicts[0].conflictStatus)
    }

    // --- TEST 9: Conflict Resolution - Local Wins ---
    @Test
    fun test09_ConflictResolutionLocalWins() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val testUuid = UUID.randomUUID().toString()
        val conflict = SyncConflictEntity(
            recordUuid = testUuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            localVersion = 2L,
            remoteVersion = 2L,
            localData = "{}",
            remoteData = "{}",
            resolved = false,
            resolutionStrategy = "UNRESOLVED"
        )
        val conflictId = database.syncConflictDao().insertConflict(conflict)

        engine.resolveConflict(conflictId, keepLocal = true)

        val unresolved = database.syncConflictDao().getUnresolvedConflictsSync()
        assertTrue(unresolved.isEmpty())
    }

    // --- TEST 10: Conflict Resolution - Remote Wins ---
    @Test
    fun test10_ConflictResolutionRemoteWins() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val testUuid = UUID.randomUUID().toString()
        val localTx = TransactionEntity(uuid = testUuid, date = "2026-09-21", amount = 50.0, type = "Daily Income", category = "A", version = 1L)
        val remoteTx = TransactionEntity(uuid = testUuid, date = "2026-09-21", amount = 75.0, type = "Daily Income", category = "A", version = 2L)
        database.transactionDao().insertTransaction(localTx)

        val conflict = SyncConflictEntity(
            recordUuid = testUuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            localVersion = 1L,
            remoteVersion = 2L,
            localData = json.encodeToString(TransactionEntity.serializer(), localTx),
            remoteData = json.encodeToString(TransactionEntity.serializer(), remoteTx),
            resolved = false,
            resolutionStrategy = "UNRESOLVED"
        )
        val conflictId = database.syncConflictDao().insertConflict(conflict)

        engine.resolveConflict(conflictId, keepLocal = false)

        val updated = database.transactionDao().getTransactionByUuid(testUuid)
        assertEquals(75.0, updated?.amount ?: 0.0, 0.001)
        assertEquals(2L, updated?.version)
    }

    // --- TEST 11: Per-Record ACK Processing in PUSH ---
    @Test
    fun test11_PerRecordAckPush() = runBlocking {
        val uuid1 = UUID.randomUUID().toString()
        val uuid2 = UUID.randomUUID().toString()

        database.syncQueueDao().insert(SyncQueueEntity(recordUuid = uuid1, entityType = "TRANSACTION", operation = "CREATE", payload = "{}", status = "PENDING"))
        database.syncQueueDao().insert(SyncQueueEntity(recordUuid = uuid2, entityType = "TRANSACTION", operation = "CREATE", payload = "{}", status = "PENDING"))

        val engine = createMockSyncEngine { request ->
            when (request.url.encodedPath) {
                "/api/sync/health" -> jsonResponse(request, 200, json.encodeToString(SyncHealthResponse.serializer(), SyncHealthResponse(deviceId = "W1", deviceName = "W1")))
                "/api/sync/changes/get" -> jsonResponse(request, 200, json.encodeToString(SyncGetChangesResponse.serializer(), SyncGetChangesResponse(records = emptyList())))
                "/api/sync/changes/push" -> {
                    val pushResp = SyncPushChangesResponse(
                        success = true,
                        appliedCount = 1,
                        results = listOf(
                            SyncRecordResult(recordUuid = uuid1, status = SyncProtocol.RecordAckStatus.APPLIED),
                            SyncRecordResult(recordUuid = uuid2, status = SyncProtocol.RecordAckStatus.RETRY, error = "Transient database lock")
                        )
                    )
                    jsonResponse(request, 200, json.encodeToString(SyncPushChangesResponse.serializer(), pushResp))
                }
                else -> jsonResponse(request, 404, "Not Found")
            }
        }

        val paired = PairedDeviceEntity(deviceId = "W1", deviceName = "W1", ipAddress = "192.168.1.50", port = 54320, pairingSecret = "KEYSTORE_PROTECTED")
        database.pairedDeviceDao().insertOrUpdate(paired)
        SecureKeystoreManager.storeSecret(context, SecureKeystoreManager.getDeviceAuthTokenKey("W1"), "TOKEN")

        engine.performFullSync(paired)

        // Assert uuid1 was dequeued, while uuid2 remains in queue with incremented retry
        assertNull("uuid1 must be removed upon APPLIED ACK", database.syncQueueDao().getItemByRecordUuid(uuid1))
        val item2 = database.syncQueueDao().getItemByRecordUuid(uuid2)
        assertNotNull("uuid2 must NOT be deleted upon RETRY status", item2)
        assertEquals(1, item2?.retryCount)
    }

    // --- TEST 12: Product Synchronization with Deterministic UUID ---
    @Test
    fun test12_ProductSyncDeterministicUuid() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val prodName = "Deluxe Spa Pedicure"
        val category = "Foot Care"
        val deterministicUuid = DeterministicProductMigration.generateDeterministicProductUuid(prodName, category)

        val prod = ProductEntity(
            uuid = deterministicUuid,
            name = prodName,
            category = category,
            price = 55.0,
            active = true,
            version = 1L
        )

        val record = SyncRecord(
            uuid = deterministicUuid,
            entityType = SyncProtocol.EntityTypes.PRODUCT,
            operation = SyncProtocol.Operations.CREATE,
            version = 1L,
            dataJson = json.encodeToString(ProductEntity.serializer(), prod)
        )

        val applied = engine.applyIncomingRecord(record, "MY-ANDROID")
        assertTrue(applied)

        val saved = database.productDao().getProductByUuid(deterministicUuid)
        assertNotNull(saved)
        assertEquals("Deluxe Spa Pedicure", saved?.name)
        assertEquals(55.0, saved?.price ?: 0.0, 0.001)
    }

    // --- TEST 13: Appointment Synchronization ---
    @Test
    fun test13_AppointmentSync() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val aptUuid = UUID.randomUUID().toString()
        val apt = AppointmentEntity(
            uuid = aptUuid,
            customerName = "Sarah Connor",
            customerPhone = "555-9000",
            serviceName = "Cyber Cut",
            appointmentDate = "2026-09-22",
            appointmentTime = "02:00 PM",
            price = 45.0,
            version = 1L
        )

        val record = SyncRecord(
            uuid = aptUuid,
            entityType = SyncProtocol.EntityTypes.APPOINTMENT,
            operation = SyncProtocol.Operations.CREATE,
            version = 1L,
            dataJson = json.encodeToString(AppointmentEntity.serializer(), apt)
        )

        val applied = engine.applyIncomingRecord(record, "MY-ANDROID")
        assertTrue(applied)

        val saved = database.appointmentDao().getAppointmentByUuid(aptUuid)
        assertNotNull(saved)
        assertEquals("Sarah Connor", saved?.customerName)
        assertEquals("Cyber Cut", saved?.serviceName)
    }

    // --- TEST 14: Business Profile Synchronization ---
    @Test
    fun test14_BusinessProfileSync() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val profile = BusinessProfile(
            businessName = "Apex Salon & Spa",
            abnAcn = "12345678",
            businessAddress = "100 Main Street",
            phoneMobile = "555-7777",
            email = "contact@apexsalon.com"
        )

        val record = SyncRecord(
            uuid = "BUSINESS_PROFILE_SINGLETON",
            entityType = SyncProtocol.EntityTypes.BUSINESS_PROFILE,
            operation = SyncProtocol.Operations.UPDATE,
            dataJson = json.encodeToString(BusinessProfile.serializer(), profile)
        )

        val applied = engine.applyIncomingRecord(record, "MY-ANDROID")
        assertTrue(applied)

        val saved = preferences.getBusinessProfile()
        assertEquals("Apex Salon & Spa", saved.businessName)
        assertEquals("100 Main Street", saved.businessAddress)
    }

    // --- TEST 15: Unpair Device Flow (Server Notification & Credential Wipe) ---
    @Test
    fun test15_UnpairDeviceFlow() = runBlocking {
        var unpairCalled = false
        val engine = createMockSyncEngine { request ->
            if (request.url.encodedPath == "/api/sync/unpair") {
                unpairCalled = true
                jsonResponse(request, 200, json.encodeToString(SyncUnpairResponse.serializer(), SyncUnpairResponse(true, "Unpaired")))
            } else {
                jsonResponse(request, 404, "Not Found")
            }
        }

        val devId = "WIN-TO-UNPAIR"
        val paired = PairedDeviceEntity(deviceId = devId, deviceName = "PC", ipAddress = "192.168.1.50", port = 54320, pairingSecret = "KEYSTORE_PROTECTED")
        database.pairedDeviceDao().insertOrUpdate(paired)
        SecureKeystoreManager.storeSecret(context, SecureKeystoreManager.getDeviceAuthTokenKey(devId), "SECRET_TOKEN")

        val result = engine.unpairDevice(devId)
        assertTrue(result.isSuccess)
        assertTrue("Remote unpair endpoint must be notified", unpairCalled)

        // Verify device removed from Room
        assertNull(database.pairedDeviceDao().getDeviceById(devId))

        // Verify secret removed from Keystore
        assertNull(SecureKeystoreManager.getSecret(context, SecureKeystoreManager.getDeviceAuthTokenKey(devId)))
    }

    // --- TEST 16: Incremental Sync with Change Cursor Pagination ---
    @Test
    fun test16_IncrementalSyncWithPagination() = runBlocking {
        var callCount = 0
        val engine = createMockSyncEngine { request ->
            when (request.url.encodedPath) {
                "/api/sync/health" -> jsonResponse(request, 200, json.encodeToString(SyncHealthResponse.serializer(), SyncHealthResponse(deviceId = "W1", deviceName = "W1")))
                "/api/sync/changes/get" -> {
                    callCount++
                    val bodyStr = request.body?.let {
                        val buffer = okio.Buffer()
                        it.writeTo(buffer)
                        buffer.readUtf8()
                    } ?: ""
                    val getReq = json.decodeFromString(SyncGetChangesRequest.serializer(), bodyStr)
                    if (getReq.cursor == "") {
                        val tx = TransactionEntity(uuid = "p1-tx", date = "2026-09-21", amount = 10.0, type = "Daily Income", category = "A")
                        val rec = SyncRecord(uuid = "p1-tx", entityType = "TRANSACTION", operation = "CREATE", dataJson = json.encodeToString(TransactionEntity.serializer(), tx))
                        val resp = SyncGetChangesResponse(records = listOf(rec), nextCursor = "cursor-page-2", hasMore = true)
                        jsonResponse(request, 200, json.encodeToString(SyncGetChangesResponse.serializer(), resp))
                    } else {
                        val tx2 = TransactionEntity(uuid = "p2-tx", date = "2026-09-21", amount = 20.0, type = "Daily Income", category = "B")
                        val rec2 = SyncRecord(uuid = "p2-tx", entityType = "TRANSACTION", operation = "CREATE", dataJson = json.encodeToString(TransactionEntity.serializer(), tx2))
                        val resp = SyncGetChangesResponse(records = listOf(rec2), nextCursor = "cursor-page-3", hasMore = false)
                        jsonResponse(request, 200, json.encodeToString(SyncGetChangesResponse.serializer(), resp))
                    }
                }
                "/api/sync/ack" -> jsonResponse(request, 200, json.encodeToString(SyncAckResponse.serializer(), SyncAckResponse(true, "ACK")))
                else -> jsonResponse(request, 404, "Not Found")
            }
        }

        val paired = PairedDeviceEntity(deviceId = "W1", deviceName = "W1", ipAddress = "192.168.1.50", port = 54320, lastSyncCursor = "")
        database.pairedDeviceDao().insertOrUpdate(paired)
        SecureKeystoreManager.storeSecret(context, SecureKeystoreManager.getDeviceAuthTokenKey("W1"), "TOKEN")

        val res = engine.performFullSync(paired)
        assertTrue(res.success)
        assertEquals(2, res.pulledCount)
        assertEquals(2, callCount)

        val updatedDev = database.pairedDeviceDao().getDeviceById("W1")
        assertEquals("cursor-page-3", updatedDev?.lastSyncCursor)
    }

    // --- TEST 17: Backup and Restore v4 Data Integrity with Deterministic Mapping ---
    @Test
    fun test17_BackupAndRestoreProductDeterministicMapping() = runBlocking {
        val legacyProducts = listOf(
            ProductItem("Classic Shave", 25.0, "Barber"),
            ProductItem("Beard Trim", 15.0, "Barber")
        )
        preferences.setCachedProducts(legacyProducts)

        // Seed with deterministic migration
        val migrated = DeterministicProductMigration.migrateLegacyProductsIfNecessary(
            database.productDao(),
            preferences,
            "TEST-DEV"
        )
        assertEquals(2, migrated)

        val prods = database.productDao().getAllActiveProductsSync()
        assertEquals(2, prods.size)

        val expectedUuid = DeterministicProductMigration.generateDeterministicProductUuid("Classic Shave", "Barber")
        val shaveProd = database.productDao().getProductByName("Classic Shave")
        assertNotNull(shaveProd)
        assertEquals(expectedUuid, shaveProd?.uuid)
    }

    // --- TEST 18: Tombstone Creation on Local Record Delete ---
    @Test
    fun test18_LocalDeleteGeneratesTombstoneAndSyncQueueItem() = runBlocking {
        val repo = TransactionRepository.getInstance(context)
        val diskDb = AppDatabase.getDatabase(context)
        repo.saveProduct(ProductItem("Waxing Service", 35.0, "Esthetics"))

        val prod = diskDb.productDao().getProductByName("Waxing Service")
        assertNotNull(prod)

        repo.deleteProduct("Waxing Service")

        // Deleted in Room
        val tombstone = diskDb.productDao().getProductByUuid(prod!!.uuid)
        assertNotNull(tombstone)
        assertNotNull(tombstone?.deletedAt)

        // Enqueued in sync_queue as DELETE
        val queued = diskDb.syncQueueDao().getItemByRecordUuid(prod.uuid)
        assertNotNull(queued)
        assertEquals("DELETE", queued?.operation)
        assertEquals(SyncProtocol.EntityTypes.PRODUCT, queued?.entityType)
    }

    // --- TEST 19: Stale Remote Version Ignored Gracefully ---
    @Test
    fun test19_StaleRemoteVersionIgnored() = runBlocking {
        val engine = WifiSyncEngine(context, database, preferences)
        val uuid = UUID.randomUUID().toString()
        val txCurrent = TransactionEntity(uuid = uuid, date = "2026-09-21", amount = 100.0, type = "Daily Income", category = "A", version = 5L)
        database.transactionDao().insertTransaction(txCurrent)

        // Incoming record with older version 3
        val staleRecord = SyncRecord(
            uuid = uuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.UPDATE,
            version = 3L,
            dataJson = json.encodeToString(TransactionEntity.serializer(), txCurrent.copy(amount = 50.0, version = 3L))
        )

        val applied = engine.applyIncomingRecord(staleRecord, "MY-ANDROID")
        assertTrue("Stale record should be acknowledged as processed without altering current record", applied)

        val current = database.transactionDao().getTransactionByUuid(uuid)
        assertEquals(100.0, current?.amount ?: 0.0, 0.001)
        assertEquals(5L, current?.version)
    }

    // --- TEST 20: Empty Sync / Cursor No-Op ---
    @Test
    fun test20_EmptySyncNoOp() = runBlocking {
        val engine = createMockSyncEngine { request ->
            when (request.url.encodedPath) {
                "/api/sync/health" -> jsonResponse(request, 200, json.encodeToString(SyncHealthResponse.serializer(), SyncHealthResponse(deviceId = "W1", deviceName = "W1")))
                "/api/sync/changes/get" -> jsonResponse(request, 200, json.encodeToString(SyncGetChangesResponse.serializer(), SyncGetChangesResponse(records = emptyList(), nextCursor = "c-same", hasMore = false)))
                else -> jsonResponse(request, 404, "Not Found")
            }
        }

        val paired = PairedDeviceEntity(deviceId = "W1", deviceName = "W1", ipAddress = "192.168.1.50", port = 54320, lastSyncCursor = "c-same", lastSyncAt = 1000L)
        database.pairedDeviceDao().insertOrUpdate(paired)
        SecureKeystoreManager.storeSecret(context, SecureKeystoreManager.getDeviceAuthTokenKey("W1"), "TOKEN")

        val result = engine.performFullSync(paired)
        assertTrue(result.success)
        assertEquals(0, result.pulledCount)
        assertEquals(0, result.pushedCount)
        assertEquals(0, result.conflictsCount)
    }
}
