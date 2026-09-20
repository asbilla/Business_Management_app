package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase

object ContactHelper {

    /**
     * Resolves the caller's display name by:
     * 1. Querying the system ContactsContract using PhoneLookup (if READ_CONTACTS permission is granted)
     * 2. Querying the local Room database to match repeat business customers by phone
     * 3. Defaulting to "New Caller" or "Unknown Caller"
     */
    suspend fun resolveCallerName(context: Context, phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) {
            return "Caller"
        }

        val trimmedPhone = phoneNumber.trim()

        // 1. Try querying Android ContactsContract
        val contactName = getContactNameFromSystem(context, trimmedPhone)
        if (!contactName.isNullOrBlank()) {
            return contactName
        }

        // 2. Try querying App's internal appointments database for repeat clients
        try {
            val db = AppDatabase.getDatabase(context)
            val customerName = db.appointmentDao().getCustomerNameByPhone(trimmedPhone)
            if (!customerName.isNullOrBlank()) {
                return customerName
            }
        } catch (_: Exception) {}

        return "New Caller"
    }

    /**
     * Looks up contact display name in ContactsContract.
     */
    fun getContactNameFromSystem(context: Context, phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            return name
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Data class representing a contact item for promotional messaging.
     */
    data class ContactItem(
        val name: String,
        val phoneNumber: String,
        val source: String = "Phone Contact" // "Phone Contact" or "Salon Client"
    )

    /**
     * Fetches all available contacts from the device's ContactsContract as well as
     * previous salon clients from the internal database.
     * Deduplicates by normalized phone numbers.
     */
    suspend fun getAllAvailableContacts(context: Context): List<ContactItem> {
        val result = mutableMapOf<String, ContactItem>()

        // 1. Fetch from Phone Contacts if permission granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )
                cursor?.use {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = if (nameIdx != -1) it.getString(nameIdx) else ""
                        val number = if (numIdx != -1) it.getString(numIdx) else ""
                        val normalized = normalizePhone(number)
                        if (normalized.isNotBlank() && !result.containsKey(normalized)) {
                            result[normalized] = ContactItem(
                                name = if (name.isNotBlank()) name.trim() else normalized,
                                phoneNumber = number.trim(),
                                source = "Phone Contact"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Also merge previous business clients from AppDatabase
        try {
            val db = AppDatabase.getDatabase(context)
            val clients = db.appointmentDao().getAllClientContacts()
            clients.forEach { client ->
                val normalized = normalizePhone(client.customerPhone)
                if (normalized.isNotBlank()) {
                    val existing = result[normalized]
                    if (existing == null) {
                        result[normalized] = ContactItem(
                            name = if (client.customerName.isNotBlank()) client.customerName.trim() else normalized,
                            phoneNumber = client.customerPhone.trim(),
                            source = "Salon Client"
                        )
                    } else if (existing.source != "Salon Client" && client.customerName.isNotBlank()) {
                        result[normalized] = existing.copy(name = client.customerName.trim(), source = "Salon Client")
                    }
                }
            }
        } catch (_: Exception) {}

        return result.values.sortedBy { it.name.lowercase() }
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        // Remove spaces, hyphens, brackets
        return phone.replace("[^0-9+]".toRegex(), "")
    }
}
