package com.example.sync.model

import kotlinx.serialization.Serializable

object SyncProtocol {
    const val PROTOCOL_VERSION = 1
    const val DEFAULT_PORT = 54320
    const val SERVICE_TYPE = "_mybusiness._tcp"
    const val SERVICE_NAME = "MyBusinessSync"
    const val UDP_BROADCAST_PORT = 54321

    object EntityTypes {
        const val TRANSACTION = "TRANSACTION"
        const val APPOINTMENT = "APPOINTMENT"
        const val PRODUCT = "PRODUCT"
        const val BUSINESS_PROFILE = "BUSINESS_PROFILE"
        const val APPOINTMENT_SETTINGS = "APPOINTMENT_SETTINGS"
    }

    object Operations {
        const val CREATE = "CREATE"
        const val UPDATE = "UPDATE"
        const val DELETE = "DELETE"
    }

    object Status {
        const val PENDING = "PENDING"
        const val SYNCING = "SYNCING"
        const val COMPLETED = "COMPLETED"
        const val FAILED = "FAILED"
    }
}

@Serializable
data class SyncRecord(
    val uuid: String,
    val entityType: String,
    val operation: String,
    val version: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val dataJson: String
)

@Serializable
data class SyncHelloRequest(
    val protocolVersion: Int = SyncProtocol.PROTOCOL_VERSION,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String = "v6.1",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncHelloResponse(
    val protocolVersion: Int = SyncProtocol.PROTOCOL_VERSION,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val serverTime: Long,
    val status: String = "READY"
)

@Serializable
data class SyncPairRequest(
    val deviceId: String,
    val deviceName: String,
    val pairingPin: String,
    val clientIp: String = "",
    val clientPort: Int = SyncProtocol.DEFAULT_PORT
)

@Serializable
data class SyncPairResponse(
    val success: Boolean,
    val deviceId: String,
    val deviceName: String,
    val authToken: String,
    val message: String
)

@Serializable
data class SyncGetChangesRequest(
    val deviceId: String,
    val authToken: String,
    val sinceTimestamp: Long = 0L
)

@Serializable
data class SyncGetChangesResponse(
    val records: List<SyncRecord> = emptyList(),
    val serverTimestamp: Long = System.currentTimeMillis(),
    val hasMore: Boolean = false
)

@Serializable
data class SyncPushChangesRequest(
    val deviceId: String,
    val authToken: String,
    val records: List<SyncRecord>,
    val clientTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncPushChangesResponse(
    val success: Boolean,
    val appliedCount: Int = 0,
    val conflicts: List<String> = emptyList(),
    val error: String? = null
)

@Serializable
data class SyncAckRequest(
    val deviceId: String,
    val authToken: String,
    val acknowledgedUuids: List<String>
)

@Serializable
data class SyncAckResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class SyncErrorResponse(
    val errorCode: String,
    val message: String
)
