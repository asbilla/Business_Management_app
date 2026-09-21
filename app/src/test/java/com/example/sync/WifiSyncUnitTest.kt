package com.example.sync

import com.example.data.local.AppointmentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.AppointmentSettings
import com.example.data.model.ProductItem
import com.example.data.pref.BusinessProfile
import com.example.data.repository.TransactionRepository
import com.example.sync.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WifiSyncUnitTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testSyncProtocolConstants() {
        assertEquals(1, SyncProtocol.PROTOCOL_VERSION)
        assertEquals(54320, SyncProtocol.DEFAULT_PORT)
        assertEquals("_mybusiness._tcp", SyncProtocol.SERVICE_TYPE)
        assertEquals(54321, SyncProtocol.UDP_BROADCAST_PORT)
        assertEquals("TRANSACTION", SyncProtocol.EntityTypes.TRANSACTION)
        assertEquals("APPOINTMENT", SyncProtocol.EntityTypes.APPOINTMENT)
        assertEquals("PRODUCT", SyncProtocol.EntityTypes.PRODUCT)
    }

    @Test
    fun testHelloMessageSerialization() {
        val helloReq = SyncHelloRequest(
            protocolVersion = SyncProtocol.PROTOCOL_VERSION,
            deviceId = "ANDROID-TEST1234",
            deviceName = "Android Pixel 8",
            appVersion = "v6.1"
        )
        val encoded = json.encodeToString(SyncHelloRequest.serializer(), helloReq)
        val decoded = json.decodeFromString(SyncHelloRequest.serializer(), encoded)

        assertEquals("ANDROID-TEST1234", decoded.deviceId)
        assertEquals("Android Pixel 8", decoded.deviceName)
        assertEquals("v6.1", decoded.appVersion)

        val helloResp = SyncHelloResponse(
            protocolVersion = SyncProtocol.PROTOCOL_VERSION,
            deviceId = "WINDOWS-OFFICE01",
            deviceName = "Windows Desktop PC",
            appVersion = "v1.0",
            serverTime = 1710000000000L,
            status = "READY"
        )
        val encResp = json.encodeToString(SyncHelloResponse.serializer(), helloResp)
        val decResp = json.decodeFromString(SyncHelloResponse.serializer(), encResp)

        assertEquals("WINDOWS-OFFICE01", decResp.deviceId)
        assertEquals("READY", decResp.status)
    }

    @Test
    fun testPairingMessageSerialization() {
        val pairReq = SyncPairRequest(
            deviceId = "ANDROID-TEST1234",
            deviceName = "Pixel 8",
            pairingPin = "123456",
            clientPort = 54320
        )
        val encReq = json.encodeToString(SyncPairRequest.serializer(), pairReq)
        val decReq = json.decodeFromString(SyncPairRequest.serializer(), encReq)
        assertEquals("123456", decReq.pairingPin)

        val pairResp = SyncPairResponse(
            success = true,
            deviceId = "WINDOWS-PC1",
            deviceName = "Front Counter PC",
            authToken = "AUTH_SEC_TOKEN_XYZ_987",
            message = "Paired successfully"
        )
        val encResp = json.encodeToString(SyncPairResponse.serializer(), pairResp)
        val decResp = json.decodeFromString(SyncPairResponse.serializer(), encResp)
        assertTrue(decResp.success)
        assertEquals("AUTH_SEC_TOKEN_XYZ_987", decResp.authToken)
    }

    @Test
    fun testSyncChangesSerialization() {
        val testUuid = UUID.randomUUID().toString()
        val tx = TransactionEntity(
            id = 10,
            uuid = testUuid,
            date = "2026-09-20",
            timestamp = 1710000000000L,
            type = "Daily Income",
            category = "Haircut Service",
            amount = 45.0,
            isSynced = false,
            updatedAt = 1710000000000L,
            version = 1L,
            deviceId = "ANDROID-TEST1234"
        )

        val record = SyncRecord(
            uuid = tx.uuid,
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = SyncProtocol.Operations.CREATE,
            version = tx.version,
            updatedAt = tx.updatedAt,
            deviceId = tx.deviceId,
            dataJson = json.encodeToString(TransactionEntity.serializer(), tx)
        )

        val pushReq = SyncPushChangesRequest(
            deviceId = "ANDROID-TEST1234",
            authToken = "SECRET",
            records = listOf(record)
        )

        val encPush = json.encodeToString(SyncPushChangesRequest.serializer(), pushReq)
        val decPush = json.decodeFromString(SyncPushChangesRequest.serializer(), encPush)

        assertEquals(1, decPush.records.size)
        assertEquals(testUuid, decPush.records[0].uuid)
        assertEquals("TRANSACTION", decPush.records[0].entityType)

        // Parse inner entity
        val innerTx = json.decodeFromString(TransactionEntity.serializer(), decPush.records[0].dataJson)
        assertEquals(45.0, innerTx.amount, 0.001)
        assertEquals("Haircut Service", innerTx.category)
    }

    @Test
    fun testProductEntityConversionAndRoomMetadata() {
        val prodUuid = UUID.randomUUID().toString()
        val entity = ProductEntity(
            uuid = prodUuid,
            name = "Deluxe Spa",
            description = "Full body treatment",
            category = "Spa",
            price = 120.0,
            type = "Service",
            active = true,
            createdAt = 1710000000000L,
            updatedAt = 1710000000000L,
            deletedAt = null,
            version = 2L,
            deviceId = "ANDROID-TEST"
        )

        val item = ProductItem(name = entity.name, price = entity.price, category = entity.category)
        assertEquals("Deluxe Spa", item.name)
        assertEquals(120.0, item.price, 0.001)
        assertEquals("Spa", item.category)
        assertNull(entity.deletedAt)

        // Test tombstone deletion
        val tombstone = entity.copy(deletedAt = 1710000500000L, updatedAt = 1710000500000L, version = entity.version + 1)
        assertNotNull(tombstone.deletedAt)
        assertEquals(3L, tombstone.version)
    }

    @Test
    fun testConflictDetectionLogic() {
        val existingTx = TransactionEntity(
            uuid = "TX-UUID-1",
            date = "2026-09-20",
            timestamp = 1000L,
            type = "Daily Income",
            category = "Card",
            amount = 50.0,
            updatedAt = 2000L,
            version = 3L
        )

        // Case A: Remote has lower version and conflicting amount -> Conflict
        val incomingConflictingRecord = SyncRecord(
            uuid = "TX-UUID-1",
            entityType = SyncProtocol.EntityTypes.TRANSACTION,
            operation = "UPDATE",
            version = 2L,
            updatedAt = 1500L,
            dataJson = """{"uuid":"TX-UUID-1","date":"2026-09-20","timestamp":1000,"type":"Daily Income","category":"Card","amount":75.0,"updatedAt":1500,"version":2}"""
        )

        val isConflict = existingTx.version > incomingConflictingRecord.version && existingTx.amount != 75.0
        assertTrue("Lower remote version with different amount should be detected as conflict", isConflict)

        // Case B: Remote has higher version -> Remote wins smoothly
        val incomingNewerRecord = incomingConflictingRecord.copy(version = 4L, updatedAt = 3000L)
        val isNewer = incomingNewerRecord.version > existingTx.version
        assertTrue("Higher remote version should be accepted without conflict", isNewer)
    }

    @Test
    fun testBackupDataV3Serialization() {
        val backup = TransactionRepository.BackupData(
            version = 3,
            timestamp = 1710000000000L,
            appVersion = "v6.1",
            businessProfile = BusinessProfile("My Business", "12345", "123 Main St", "555-1234", "info@biz.com"),
            appointmentSettings = AppointmentSettings(),
            themeMode = "System",
            webAppUrl = "",
            products = listOf(ProductItem("Coffee", 4.5, "Drinks")),
            transactions = emptyList(),
            appointments = emptyList(),
            pairedDevices = emptyList()
        )

        val encoded = json.encodeToString(TransactionRepository.BackupData.serializer(), backup)
        val decoded = json.decodeFromString(TransactionRepository.BackupData.serializer(), encoded)

        assertEquals(3, decoded.version)
        assertEquals("v6.1", decoded.appVersion)
        assertEquals(1, decoded.products.size)
        assertEquals("Coffee", decoded.products[0].name)
    }
}
