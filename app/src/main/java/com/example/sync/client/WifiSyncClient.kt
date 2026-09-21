package com.example.sync.client

import com.example.sync.model.*
import com.example.sync.security.LocalTlsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/**
 * TLS-secured Wi-Fi Sync Client.
 *
 * Communicates with Windows My Business Sync Server using HTTPS over local Wi-Fi.
 * Enforces TLS certificate fingerprint pinning on all authenticated requests.
 */
class WifiSyncClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val customHttpClient: OkHttpClient? = null
) {
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    // Cache clients per certificate fingerprint to reuse SSL sessions
    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    private fun getClient(certificateFingerprint: String = "", onCertCaptured: ((String) -> Unit)? = null): OkHttpClient {
        if (customHttpClient != null) {
            return customHttpClient
        }
        val key = certificateFingerprint.trim().uppercase()
        if (onCertCaptured != null) {
            // New pairing client with capture callback
            return LocalTlsManager.createTlsClient(certificateFingerprint, onCertCaptured)
        }
        return clientCache.getOrPut(key) {
            LocalTlsManager.createTlsClient(certificateFingerprint)
        }
    }

    suspend fun health(
        host: String,
        port: Int,
        request: SyncHealthRequest,
        certificateFingerprint: String = ""
    ): Result<SyncHealthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/api/sync/health"
            val body = json.encodeToString(SyncHealthRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder().url(url).post(body).build()

            getClient(certificateFingerprint).newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Health check failed: HTTP ${response.code}"))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncHealthResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hello(
        host: String,
        port: Int,
        request: SyncHelloRequest,
        certificateFingerprint: String = ""
    ): Result<SyncHelloResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/api/sync/hello"
            val body = json.encodeToString(SyncHelloRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder().url(url).post(body).build()

            getClient(certificateFingerprint).newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Hello request failed: HTTP ${response.code}"))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncHelloResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pair(
        host: String,
        port: Int,
        request: SyncPairRequest,
        expectedFingerprint: String = ""
    ): Result<SyncPairResponse> = withContext(Dispatchers.IO) {
        try {
            var capturedCertFingerprint = ""
            val client = getClient(expectedFingerprint) { fingerprint ->
                capturedCertFingerprint = fingerprint
            }

            val url = "https://$host:$port/api/sync/pair"
            val body = json.encodeToString(SyncPairRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder().url(url).post(body).build()

            client.newCall(httpRequest).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = try {
                        json.decodeFromString(SyncErrorResponse.serializer(), respBody).message
                    } catch (_: Exception) {
                        "Pairing failed with HTTP ${response.code}"
                    }
                    return@withContext Result.failure(Exception(err))
                }
                var parsed = json.decodeFromString(SyncPairResponse.serializer(), respBody)
                if (parsed.serverCertificateFingerprint.isBlank() && capturedCertFingerprint.isNotBlank()) {
                    parsed = parsed.copy(serverCertificateFingerprint = capturedCertFingerprint)
                }
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChanges(
        host: String,
        port: Int,
        request: SyncGetChangesRequest,
        certificateFingerprint: String
    ): Result<SyncGetChangesResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/api/sync/changes/get"
            val body = json.encodeToString(SyncGetChangesRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${request.authToken}")
                .addHeader("X-Device-Id", request.deviceId)
                .post(body)
                .build()

            getClient(certificateFingerprint).newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val respBody = response.body?.string().orEmpty()
                    val err = try {
                        json.decodeFromString(SyncErrorResponse.serializer(), respBody).message
                    } catch (_: Exception) {
                        "Get changes failed: HTTP ${response.code}"
                    }
                    return@withContext Result.failure(Exception(err))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncGetChangesResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushChanges(
        host: String,
        port: Int,
        request: SyncPushChangesRequest,
        certificateFingerprint: String
    ): Result<SyncPushChangesResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/api/sync/changes/push"
            val body = json.encodeToString(SyncPushChangesRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${request.authToken}")
                .addHeader("X-Device-Id", request.deviceId)
                .post(body)
                .build()

            getClient(certificateFingerprint).newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val respBody = response.body?.string().orEmpty()
                    val err = try {
                        json.decodeFromString(SyncErrorResponse.serializer(), respBody).message
                    } catch (_: Exception) {
                        "Push changes failed: HTTP ${response.code}"
                    }
                    return@withContext Result.failure(Exception(err))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncPushChangesResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acknowledge(
        host: String,
        port: Int,
        request: SyncAckRequest,
        certificateFingerprint: String
    ): Result<SyncAckResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/api/sync/ack"
            val body = json.encodeToString(SyncAckRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${request.authToken}")
                .addHeader("X-Device-Id", request.deviceId)
                .post(body)
                .build()

            getClient(certificateFingerprint).newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Acknowledge failed: HTTP ${response.code}"))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncAckResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unpair(
        host: String,
        port: Int,
        request: SyncUnpairRequest,
        certificateFingerprint: String
    ): Result<SyncUnpairResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/api/sync/unpair"
            val body = json.encodeToString(SyncUnpairRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${request.authToken}")
                .addHeader("X-Device-Id", request.deviceId)
                .post(body)
                .build()

            getClient(certificateFingerprint).newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Unpair request failed: HTTP ${response.code}"))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncUnpairResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
