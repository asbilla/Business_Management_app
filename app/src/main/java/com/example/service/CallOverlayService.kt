package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.notification.NotificationHelper

class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentCallerName: String = "Caller"
    private var currentCallerPhone: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SHOW

        if (action == ACTION_DISMISS) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        currentCallerName = intent?.getStringExtra(EXTRA_CALLER_NAME)?.ifBlank { "Caller" } ?: "Caller"
        currentCallerPhone = intent?.getStringExtra(EXTRA_CALLER_PHONE) ?: ""

        // Promote to foreground service safely
        try {
            NotificationHelper.createNotificationChannel(applicationContext)
            val notification = NotificationCompat.Builder(this, NotificationHelper.INCOMING_CALL_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Incoming Call: $currentCallerName")
                .setContentText(if (currentCallerPhone.isNotBlank()) "$currentCallerPhone • Tap to book" else "Caller detected")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            startForeground(NotificationHelper.INCOMING_CALL_NOTIFICATION_ID, notification)
        } catch (_: Exception) {}

        showOverlay(currentCallerName, currentCallerPhone)

        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(callerName: String, phoneNumber: String) {
        if (!Settings.canDrawOverlays(this)) {
            // Cannot draw over apps without permission, fallback is the Heads-Up notification
            return
        }

        if (overlayView != null) {
            updateOverlayContent(callerName, phoneNumber)
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(70)
        }

        val rootLayout = createOverlayView(callerName, phoneNumber, params)
        overlayView = rootLayout

        try {
            windowManager?.addView(rootLayout, params)
        } catch (_: Exception) {
            overlayView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView(
        callerName: String,
        phoneNumber: String,
        params: WindowManager.LayoutParams
    ): View {
        val rootFrame = FrameLayout(this).apply {
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        // Card Container with soft elevation and rounded corner background
        val cardBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#FFFFFF"))
            cornerRadius = dpToPx(18).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#E0E0E0"))
        }

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            elevation = dpToPx(10).toFloat()
        }

        // Top Row: Status badge & Close button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val statusBadge = TextView(this).apply {
            text = "📞 INCOMING CALL"
            setTextColor(Color.parseColor("#6750A4")) // Purple
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeButton = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#757575"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                removeOverlay()
                stopSelf()
            }
        }

        topRow.addView(statusBadge)
        topRow.addView(closeButton)
        cardLayout.addView(topRow)

        // Middle Content Row: Caller Icon, Name & Phone, and "+ Book Now" Button
        val middleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
        }

        // Caller details column
        val detailsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            id = ID_CALLER_NAME
            text = callerName
            setTextColor(Color.parseColor("#1C1B1F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val phoneView = TextView(this).apply {
            id = ID_CALLER_PHONE
            text = if (phoneNumber.isNotBlank()) phoneNumber else "Unknown Number"
            setTextColor(Color.parseColor("#49454F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        detailsCol.addView(nameView)
        detailsCol.addView(phoneView)
        middleRow.addView(detailsCol)

        // Prominent "+ Book Now" Action Button
        val bookBtnBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#6750A4")) // Appointment Purple
            cornerRadius = dpToPx(12).toFloat()
        }

        val bookButton = TextView(this).apply {
            text = "+ Book Now"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            background = bookBtnBackground
            gravity = Gravity.CENTER
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            setOnClickListener {
                // Launch MainActivity directly with auto_book intent extras
                val launchIntent = Intent(this@CallOverlayService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("navigate_to", "appointments")
                    putExtra("auto_book", true)
                    putExtra("caller_name", currentCallerName)
                    putExtra("caller_phone", currentCallerPhone)
                }
                startActivity(launchIntent)
                removeOverlay()
                stopSelf()
            }
        }

        middleRow.addView(bookButton)
        cardLayout.addView(middleRow)
        rootFrame.addView(cardLayout)

        // Enable gentle dragging so user can reposition overlay if it obscures content
        var initialY = 0
        var initialTouchY = 0f

        rootFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(rootFrame, params)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        return rootFrame
    }

    private fun updateOverlayContent(callerName: String, phoneNumber: String) {
        val nameView = overlayView?.findViewById<TextView>(ID_CALLER_NAME)
        val phoneView = overlayView?.findViewById<TextView>(ID_CALLER_PHONE)
        nameView?.text = callerName
        phoneView?.text = if (phoneNumber.isNotBlank()) phoneNumber else "Unknown Number"
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val ACTION_SHOW = "com.example.service.ACTION_SHOW_OVERLAY"
        const val ACTION_DISMISS = "com.example.service.ACTION_DISMISS_OVERLAY"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_PHONE = "extra_caller_phone"

        private const val ID_CALLER_NAME = 1001
        private const val ID_CALLER_PHONE = 1002

        fun show(context: Context, callerName: String, phoneNumber: String) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_PHONE, phoneNumber)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                action = ACTION_DISMISS
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
