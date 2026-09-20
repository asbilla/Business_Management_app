package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telephony.TelephonyManager
import com.example.notification.NotificationHelper
import com.example.service.CallOverlayService
import com.example.util.ContactHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver listening for TelephonyManager.ACTION_PHONE_STATE.
 * Detects incoming calls and triggers:
 * 1. Floating Call Overlay Popup (if Settings.canDrawOverlays is granted)
 * 2. High-Priority Heads-Up Notification with direct "+ Book Now" action
 * 3. Contact name resolution from system contacts and local repeat customers
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != TelephonyManager.ACTION_PHONE_STATE_CHANGED && action != "android.intent.action.PHONE_STATE") {
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?: intent.extras?.getString("incoming_number")
            ?: ""

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Trigger async resolution of caller name and launch overlay / heads-up notification
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val callerName = ContactHelper.resolveCallerName(context, incomingNumber)
                        val displayPhone = incomingNumber.ifBlank { "Private Number" }

                        // 1. Show Heads-Up Notification with "+ Book Now" action
                        NotificationHelper.showIncomingCallNotification(
                            context = context,
                            callerName = callerName,
                            phoneNumber = displayPhone,
                            callState = state
                        )

                        // 2. Show Floating Overlay Window if permission is granted
                        if (Settings.canDrawOverlays(context)) {
                            CallOverlayService.show(
                                context = context,
                                callerName = callerName,
                                phoneNumber = displayPhone
                            )
                        }
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended or missed: clean up overlay and heads-up alert
                try {
                    CallOverlayService.dismiss(context)
                    NotificationHelper.dismissIncomingCallNotification(context)
                } catch (_: Exception) {}
            }
        }
    }
}
