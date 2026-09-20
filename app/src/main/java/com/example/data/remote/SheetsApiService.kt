package com.example.data.remote

import android.util.Log
import com.example.data.local.AppointmentEntity
import com.example.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteTransaction(
    val id: String,
    val date: String,
    val type: String,
    val notes: String,
    val amount: Double,
    val createdAt: String = ""
)

data class RemoteAppointment(
    val id: String,
    val date: String,
    val time: String,
    val customerName: String,
    val phone: String,
    val service: String,
    val duration: Int = 30,
    val price: Double = 0.0,
    val status: String = "Scheduled",
    val notes: String = "",
    val createdAt: String = ""
)

class SheetsApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun postTransaction(webAppUrl: String, transaction: TransactionEntity): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val payload = JSONObject().apply {
                    put("action", "add")
                    put("id", transaction.uuid)
                    put("date", transaction.date)
                    put("type", transaction.type)
                    put("notes", transaction.category)
                    put("amount", transaction.amount)
                    put("timestamp", transaction.timestamp)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully posted transaction: $responseBody")
                        Result.success(responseBody)
                    } else {
                        Log.e(TAG, "Failed posting transaction. Code: ${response.code}, body: $responseBody")
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during postTransaction", e)
                Result.failure(e)
            }
        }
    }

    suspend fun postBusinessProfile(webAppUrl: String, profile: com.example.data.pref.BusinessProfile): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val payload = JSONObject().apply {
                    put("action", "save_profile")
                    put("businessName", profile.businessName)
                    put("abnAcn", profile.abnAcn)
                    put("businessAddress", profile.businessAddress)
                    put("phoneMobile", profile.phoneMobile)
                    put("email", profile.email)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully saved business profile to Sheet1: $responseBody")
                        Result.success(responseBody)
                    } else {
                        Log.e(TAG, "Failed saving business profile. Code: ${response.code}, body: $responseBody")
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during postBusinessProfile", e)
                Result.failure(e)
            }
        }
    }

    suspend fun updateTransactionOnSheets(
        webAppUrl: String,
        id: String,
        date: String,
        type: String,
        amount: Double,
        notes: String = ""
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val payload = JSONObject().apply {
                    put("action", "update_transaction")
                    put("id", id)
                    put("date", date)
                    put("type", type)
                    put("amount", amount)
                    put("notes", notes)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully updated transaction on sheets: $responseBody")
                        Result.success(responseBody)
                    } else {
                        Log.e(TAG, "Failed updating transaction on sheets. Code: ${response.code}, body: $responseBody")
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during updateTransactionOnSheets", e)
                Result.failure(e)
            }
        }
    }

    suspend fun deleteTransactionOnSheets(
        webAppUrl: String,
        id: String,
        date: String,
        type: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val payload = JSONObject().apply {
                    put("action", "delete_transaction")
                    put("id", id)
                    put("date", date)
                    put("type", type)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully deleted transaction on sheets: $responseBody")
                        Result.success(responseBody)
                    } else {
                        Log.e(TAG, "Failed deleting transaction on sheets. Code: ${response.code}, body: $responseBody")
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during deleteTransactionOnSheets", e)
                Result.failure(e)
            }
        }
    }

    suspend fun postTransactionsBatch(webAppUrl: String, transactions: List<TransactionEntity>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }
                if (transactions.isEmpty()) {
                    return@withContext Result.success("No transactions to sync")
                }

                val array = JSONArray()
                for (tx in transactions) {
                    val item = JSONObject().apply {
                        put("id", tx.uuid)
                        put("date", tx.date)
                        put("type", tx.type)
                        put("notes", tx.category)
                        put("amount", tx.amount)
                        put("timestamp", tx.timestamp)
                    }
                    array.put(item)
                }

                val payload = JSONObject().apply {
                    put("action", "batch_add")
                    put("transactions", array)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully posted ${transactions.size} transactions in batch: $responseBody")
                        Result.success(responseBody)
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in postTransactionsBatch", e)
                Result.failure(e)
            }
        }
    }

    suspend fun postAppointment(webAppUrl: String, appointment: AppointmentEntity): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val payload = JSONObject().apply {
                    put("action", "save_appointment")
                    put("id", appointment.uuid)
                    put("date", appointment.appointmentDate)
                    put("time", appointment.appointmentTime)
                    put("customerName", appointment.customerName)
                    put("phone", appointment.customerPhone)
                    put("service", appointment.serviceName)
                    put("duration", appointment.durationMinutes)
                    put("price", appointment.price)
                    put("status", appointment.status)
                    put("notes", appointment.notes)
                    put("createdAt", appointment.createdAt)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully posted appointment: $responseBody")
                        Result.success(responseBody)
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during postAppointment", e)
                Result.failure(e)
            }
        }
    }

    suspend fun postAppointmentsBatch(webAppUrl: String, appointments: List<AppointmentEntity>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }
                if (appointments.isEmpty()) {
                    return@withContext Result.success("No appointments to sync")
                }

                val array = JSONArray()
                for (appt in appointments) {
                    val item = JSONObject().apply {
                        put("id", appt.uuid)
                        put("date", appt.appointmentDate)
                        put("time", appt.appointmentTime)
                        put("customerName", appt.customerName)
                        put("phone", appt.customerPhone)
                        put("service", appt.serviceName)
                        put("duration", appt.durationMinutes)
                        put("price", appt.price)
                        put("status", appt.status)
                        put("notes", appt.notes)
                        put("createdAt", appt.createdAt)
                    }
                    array.put(item)
                }

                val payload = JSONObject().apply {
                    put("action", "save_appointments_batch")
                    put("appointments", array)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully posted ${appointments.size} appointments in batch: $responseBody")
                        Result.success(responseBody)
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in postAppointmentsBatch", e)
                Result.failure(e)
            }
        }
    }

    suspend fun fetchAppointments(webAppUrl: String): Result<List<RemoteAppointment>> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val urlWithParam = if (webAppUrl.contains("?")) "$webAppUrl&action=get_appointments" else "$webAppUrl?action=get_appointments"
                val request = Request.Builder()
                    .url(urlWithParam)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP Error ${response.code}: $body"))
                    }
                    val appointments = parseAppointmentsJson(body)
                    Result.success(appointments)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching appointments", e)
                Result.failure(e)
            }
        }
    }

    suspend fun deleteAppointmentOnSheets(webAppUrl: String, id: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val payload = JSONObject().apply {
                    put("action", "delete_appointment")
                    put("id", id)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Result.success(body)
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: $body"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseAppointmentsJson(jsonString: String): List<RemoteAppointment> {
        val list = mutableListOf<RemoteAppointment>()
        val trimmed = jsonString.trim()
        if (trimmed.isBlank()) return list

        try {
            val jsonArray: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("appointments") && obj.get("appointments") is JSONArray -> obj.getJSONArray("appointments")
                        obj.has("data") && obj.get("data") is JSONArray -> obj.getJSONArray("data")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                val date = item.optString("date", "")
                val time = item.optString("time", "")
                val customerName = item.optString("customerName", "")
                val phone = item.optString("phone", "")
                val service = item.optString("service", "")
                val duration = item.optInt("duration", 30)
                val price = item.optDouble("price", 0.0)
                val status = item.optString("status", "Scheduled")
                val notes = item.optString("notes", "")
                val createdAt = item.optString("createdAt", "")

                if (customerName.isNotBlank() || phone.isNotBlank()) {
                    list.add(
                        RemoteAppointment(
                            id = id,
                            date = date,
                            time = time,
                            customerName = customerName,
                            phone = phone,
                            service = service,
                            duration = duration,
                            price = price,
                            status = status,
                            notes = notes,
                            createdAt = createdAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing appointments JSON: $jsonString", e)
        }
        return list
    }

    suspend fun fetchTransactions(webAppUrl: String): Result<List<RemoteTransaction>> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val request = Request.Builder()
                    .url(webAppUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }

                    val transactions = parseTransactionsJson(responseBody)
                    Result.success(transactions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching transactions", e)
                Result.failure(e)
            }
        }
    }

    suspend fun testConnection(webAppUrl: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Please enter a valid URL"))
                }

                val request = Request.Builder()
                    .url(webAppUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Result.success("Connection successful (HTTP ${response.code})")
                    } else {
                        Result.failure(Exception("Server returned HTTP ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseTransactionsJson(jsonString: String): List<RemoteTransaction> {
        val list = mutableListOf<RemoteTransaction>()
        val trimmed = jsonString.trim()

        val jsonArray: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("data") && obj.get("data") is JSONArray -> obj.getJSONArray("data")
                    obj.has("transactions") && obj.get("transactions") is JSONArray -> obj.getJSONArray("transactions")
                    else -> JSONArray()
                }
            }
            else -> JSONArray()
        }

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val date = item.optString("date", "")
            val type = item.optString("type", "Daily Income")
            val notes = when {
                item.has("notes") -> item.optString("notes", "")
                item.has("category") -> item.optString("category", "")
                else -> ""
            }
            val amount = when {
                item.has("amount") && item.optDouble("amount", 0.0) > 0.0 -> item.optDouble("amount", 0.0)
                item.has("income") && item.optDouble("income", 0.0) > 0.0 -> item.optDouble("income", 0.0)
                item.has("expense") && item.optDouble("expense", 0.0) > 0.0 -> item.optDouble("expense", 0.0)
                else -> item.optDouble("amount", 0.0)
            }
            val createdAt = item.optString("createdAt", "")

            if (date.isNotBlank() || amount > 0.0) {
                list.add(
                    RemoteTransaction(
                        id = id,
                        date = date,
                        type = type,
                        notes = notes,
                        amount = amount,
                        createdAt = createdAt
                    )
                )
            }
        }

        return list
    }

    suspend fun fetchProducts(webAppUrl: String): Result<List<com.example.data.model.ProductItem>> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }
                val urlWithParam = if (webAppUrl.contains("?")) "$webAppUrl&action=get_products" else "$webAppUrl?action=get_products"
                val request = Request.Builder()
                    .url(urlWithParam)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP Error ${response.code}: $body"))
                    }

                    val products = parseProductsJson(body)
                    Result.success(products)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching products from Sheets", e)
                Result.failure(e)
            }
        }
    }

    private fun parseProductsJson(jsonString: String): List<com.example.data.model.ProductItem> {
        val list = mutableListOf<com.example.data.model.ProductItem>()
        val trimmed = jsonString.trim()
        if (trimmed.isBlank()) return list

        try {
            val jsonArray: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("products") && obj.get("products") is JSONArray -> obj.getJSONArray("products")
                        obj.has("items") && obj.get("items") is JSONArray -> obj.getJSONArray("items")
                        obj.has("data") && obj.get("data") is JSONArray -> obj.getJSONArray("data")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val name = when {
                    item.has("name") -> item.optString("name", "")
                    item.has("itemName") -> item.optString("itemName", "")
                    item.has("title") -> item.optString("title", "")
                    else -> ""
                }.trim()

                if (name.isBlank()) continue

                val price = when {
                    item.has("price") -> item.optDouble("price", 0.0)
                    item.has("unitPrice") -> item.optDouble("unitPrice", 0.0)
                    item.has("amount") -> item.optDouble("amount", 0.0)
                    else -> 0.0
                }

                val category = item.optString("category", "").trim()
                list.add(com.example.data.model.ProductItem(name = name, price = price, category = category))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing products JSON: $jsonString", e)
        }

        return list
    }

    suspend fun postProduct(webAppUrl: String, product: com.example.data.model.ProductItem): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }

                val payload = JSONObject().apply {
                    put("action", "save_product")
                    put("name", product.name)
                    put("price", product.price)
                    put("category", product.category)
                }

                val requestBody = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Result.success(responseBody)
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during postProduct", e)
                Result.failure(e)
            }
        }
    }

    suspend fun fetchPdfExportUrl(webAppUrl: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (webAppUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Google Apps Script URL is empty"))
                }
                val urlWithParam = if (webAppUrl.contains("?")) "$webAppUrl&action=pdf" else "$webAppUrl?action=pdf"
                val request = Request.Builder()
                    .url(urlWithParam)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.isNotBlank()) {
                        val json = JSONObject(body)
                        val pdfUrl = json.optString("pdfUrl", "")
                        if (pdfUrl.isNotBlank()) {
                            return@withContext Result.success(pdfUrl)
                        }
                        val ssUrl = json.optString("spreadsheetUrl", "")
                        if (ssUrl.isNotBlank()) {
                            val derived = ssUrl.replace(Regex("""/edit.*$"""), "") + "/export?format=pdf&size=letter&portrait=true&fitw=true&gridlines=true"
                            return@withContext Result.success(derived)
                        }
                    }
                    Result.failure(Exception("PDF export URL not provided by backend"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val TAG = "SheetsApiService"
    }
}
