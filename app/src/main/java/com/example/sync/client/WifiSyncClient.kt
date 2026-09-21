package com.example.sync.client

import com.example.sync.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WifiSyncClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    suspend fun hello(host: String, port: Int, request: SyncHelloRequest): Result<SyncHelloResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/sync/hello"
            val body = json.encodeToString(SyncHelloRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder().url(url).post(body).build()

            client.newCall(httpRequest).execute().use { response ->
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

    suspend fun pair(host: String, port: Int, request: SyncPairRequest): Result<SyncPairResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/sync/pair"
            val body = json.encodeToString(SyncPairRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder().url(url).post(body).build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Pairing failed: HTTP ${response.code}"))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncPairResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChanges(host: String, port: Int, request: SyncGetChangesRequest): Result<SyncGetChangesResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/sync/changes/get"
            val body = json.encodeToString(SyncGetChangesRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${request.authToken}")
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Get changes failed: HTTP ${response.code}"))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncGetChangesResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushChanges(host: String, port: Int, request: SyncPushChangesRequest): Result<SyncPushChangesResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/sync/changes/push"
            val body = json.encodeToString(SyncPushChangesRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${request.authToken}")
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Push changes failed: HTTP ${response.code}"))
                }
                val respBody = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(SyncPushChangesResponse.serializer(), respBody)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acknowledge(host: String, port: Int, request: SyncAckRequest): Result<SyncAckResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/api/sync/ack"
            val body = json.encodeToString(SyncAckRequest.serializer(), request).toRequestBody(mediaTypeJson)
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${request.authToken}")
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
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
}
