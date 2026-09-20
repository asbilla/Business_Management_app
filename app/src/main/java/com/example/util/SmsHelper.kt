package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsHelper {

    private const val TAG = "SmsHelper"

    fun canSendSms(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sends a direct SMS to the recipient phone number.
     * Requires android.permission.SEND_SMS to be granted.
     */
    fun sendDirectSms(context: Context, phoneNumber: String, message: String): Boolean {
        val cleanPhone = phoneNumber.trim()
        if (cleanPhone.isBlank() || message.isBlank()) {
            Log.w(TAG, "Phone number or message is empty")
            return false
        }

        if (!canSendSms(context)) {
            Log.w(TAG, "SEND_SMS permission is not granted")
            return false
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, message, null, null)
            }
            Log.d(TAG, "Direct SMS sent successfully to $cleanPhone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending direct SMS to $cleanPhone: ${e.message}", e)
            false
        }
    }
}
