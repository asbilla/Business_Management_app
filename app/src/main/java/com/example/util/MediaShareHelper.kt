package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MediaShareHelper {

    /**
     * Shares a special offer promotion with optional image/video/reel/animation and pre-filled text.
     * Can target WhatsApp directly or open the system chooser for messaging apps.
     */
    fun shareSpecialOffer(
        context: Context,
        recipientPhoneNumbers: List<String>,
        offerText: String,
        mediaUri: Uri?,
        mimeType: String?, // e.g. "image/*", "video/*", "image/gif"
        preferredApp: ShareTarget = ShareTarget.CHOOSER
    ) {
        try {
            if (recipientPhoneNumbers.size == 1 && preferredApp == ShareTarget.WHATSAPP) {
                // Single recipient WhatsApp direct share
                val phone = recipientPhoneNumbers.first().replace("[^0-9+]".toRegex(), "")
                shareToSingleWhatsApp(context, phone, offerText, mediaUri, mimeType)
                return
            }

            // Universal multi-contact or single share via Android Share Sheet
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, offerText)
                if (mediaUri != null) {
                    putExtra(Intent.EXTRA_STREAM, mediaUri)
                    type = mimeType ?: "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
            }

            val chooser = Intent.createChooser(intent, "Share Special Offer with ${recipientPhoneNumbers.size} Contact(s)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not open share app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareToSingleWhatsApp(
        context: Context,
        cleanPhone: String,
        text: String,
        mediaUri: Uri?,
        mimeType: String?
    ) {
        try {
            // Strip leading '+' if sending to wa.me or WhatsApp direct intent
            val waPhone = cleanPhone.replace("+", "")
            val intent = Intent(Intent.ACTION_SEND).apply {
                if (mediaUri != null) {
                    type = mimeType ?: "image/*"
                    putExtra(Intent.EXTRA_STREAM, mediaUri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                setPackage("com.whatsapp")
                if (waPhone.isNotBlank()) {
                    putExtra("jid", "$waPhone@s.whatsapp.net")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard WhatsApp wa.me web link or general chooser
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    val encodedText = Uri.encode(text)
                    val waPhone = cleanPhone.replace("+", "")
                    data = Uri.parse("https://wa.me/$waPhone?text=$encodedText")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                // Open general chooser
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Send Special Offer"
                )
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    }

    enum class ShareTarget {
        CHOOSER,
        WHATSAPP,
        SMS
    }
}
