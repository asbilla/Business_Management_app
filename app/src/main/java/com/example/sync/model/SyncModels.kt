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
        const val CONFLICT = "CONFLICT"
    }

    object RecordAckStatus {
        const val APPLIED = "APPLIED"
        const val CONFLICT = "CONFLICT"
        const val REJECTED = "REJECTED"
        const val RETRY = "RETRY"
        const val DUPLICATE = "DUPLICATE"
    }

    object ErrorCodes {
        const val AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED"
        const val PAIRING_REQUIRED = "PAIRING_REQUIRED"
        const val PAIRING_EXPIRED = "PAIRING_EXPIRED"
        const val INVALID_PIN = "INVALID_PIN"
        const val PIN_RETRY_LIMIT_EXCEEDED = "PIN_RETRY_LIMIT_EXCEEDED"
        const val INVALID_CURSOR = "INVALID_CURSOR"
        const val CONFLICT = "CONFLICT"
        const val INVALID_VERSION = "INVALID_VERSION"
        const val DUPLICATE_RECORD = "DUPLICATE_RECORD"
        const val INVALID_REQUEST = "INVALID_REQUEST"
        const val SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE"
        const val CERTIFICATE_MISMATCH = "CERTIFICATE_MISMATCH"
    }
}

@Serializable
data class SyncRecord(
    val uuid: String,
    val entityType: String,
    val operation: String,
    val version: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val deviceId: String = "",
    val dataJson: String
)

@Serializable
data class SyncRecordResult(
    val recordUuid: String,
    val status: String, // APPLIED, CONFLICT, REJECTED, RETRY, DUPLICATE
    val error: String? = null,
    val resolvedVersion: Long? = null
)

@Serializable
data class SyncHealthRequest(
    val protocolVersion: Int = SyncProtocol.PROTOCOL_VERSION,
    val deviceId: String,
    val clientTimestamp: Long = System.currentTimeMillis(),
    val nonce: String = ""
)

@Serializable
data class SyncHealthResponse(
    val protocolVersion: Int = SyncProtocol.PROTOCOL_VERSION,
    val deviceId: String,
    val deviceName: String,
    val serverTime: Long = System.currentTimeMillis(),
    val status: String = "READY"
)

@Serializable
data class SyncHelloRequest(
    val protocolVersion: Int = SyncProtocol.PROTOCOL_VERSION,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String = "v6.2",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncHelloResponse(
    val protocolVersion: Int = SyncProtocol.PROTOCOL_VERSION,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val serverTime: Long,
    val status: String = "READY",
    val serverCertificateFingerprint: String = ""
)

@Serializable
data class SyncPairRequest(
    val deviceId: String,
    val deviceName: String,
    val pairingPin: String,
    val clientIp: String = "",
    val clientPort: Int = SyncProtocol.DEFAULT_PORT,
    val clientTimestamp: Long = System.currentTimeMillis(),
    val nonce: String = ""
)

@Serializable
data class SyncPairResponse(
    val success: Boolean,
    val deviceId: String,
    val deviceName: String,
    val authToken: String,
    val serverCertificateFingerprint: String = "",
    val message: String,
    val errorCode: String? = null
)

@Serializable
data class SyncGetChangesRequest(
    val deviceId: String,
    val authToken: String,
    val cursor: String = "",
    val limit: Int = 50,
    val sinceTimestamp: Long = 0L,
    val clientTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncGetChangesResponse(
    val records: List<SyncRecord> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false,
    val serverTimestamp: Long = System.currentTimeMillis()
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
    val results: List<SyncRecordResult> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val serverTimestamp: Long = System.currentTimeMillis(),
    val error: String? = null
)

@Serializable
data class SyncAckRequest(
    val deviceId: String,
    val authToken: String,
    val acknowledgedUuids: List<String>,
    val clientTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncAckResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class SyncUnpairRequest(
    val deviceId: String,
    val authToken: String,
    val reason: String = "USER_REQUESTED",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncUnpairResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class SyncErrorResponse(
    val errorCode: String,
    val message: String
)
