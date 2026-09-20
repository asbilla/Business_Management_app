package com.example.ui.appointments

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.alarm.AppointmentAlarmScheduler
import com.example.notification.NotificationHelper
import com.example.util.SmsHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppointmentEntity
import com.example.data.repository.TransactionRepository
import com.example.ui.theme.AppointmentPurple
import com.example.ui.theme.AppointmentPurpleContainer
import com.example.ui.theme.AppointmentPurpleText
import com.example.ui.theme.BalanceBlue
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    repository: TransactionRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialCallerName: String? = null,
    initialCallerPhone: String? = null,
    autoOpenBooking: Boolean = false,
    onClearAutoBooking: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allAppointments by repository.allAppointments.collectAsStateWithLifecycle(initialValue = emptyList())
    val appointmentSettings by repository.appointmentSettings.collectAsStateWithLifecycle(initialValue = repository.getAppointmentSettings())
    val availableTimeSlots = remember(appointmentSettings) {
        repository.generateTimeSlots(appointmentSettings)
    }

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var selectedDate by remember { mutableStateOf(todayIso) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") } // "All", "Scheduled", "Confirmed", "Completed", "Cancelled"

    // Dialog / Bottom Sheet state
    var showBookingSheet by remember { mutableStateOf(false) }
    var appointmentToEdit by remember { mutableStateOf<AppointmentEntity?>(null) }
    var appointmentToDelete by remember { mutableStateOf<AppointmentEntity?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var showReminderSettingsDialog by remember { mutableStateOf(false) }

    // Auto-fill and launch booking sheet when arriving from incoming call notification or overlay
    LaunchedEffect(autoOpenBooking, initialCallerPhone, initialCallerName) {
        if (autoOpenBooking && (!initialCallerPhone.isNullOrBlank() || !initialCallerName.isNullOrBlank())) {
            appointmentToEdit = AppointmentEntity(
                id = 0L,
                customerName = initialCallerName.orEmpty(),
                customerPhone = initialCallerPhone.orEmpty(),
                serviceName = "Consultation",
                appointmentDate = todayIso,
                appointmentTime = availableTimeSlots.firstOrNull() ?: "09:00 AM",
                status = "Scheduled"
            )
            showBookingSheet = true
            Toast.makeText(context, "📞 Caller details auto-filled!", Toast.LENGTH_SHORT).show()
            onClearAutoBooking()
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationHelper.areNotificationsEnabled(context)
            }
        )
    }

    var canScheduleExactAlarms by remember {
        mutableStateOf(AppointmentAlarmScheduler.canScheduleExactAlarms(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            repository.setAppointmentSettings(appointmentSettings.copy(automatedSmsEnabled = true))
            Toast.makeText(context, "SMS permission granted! Automated SMS enabled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "SMS permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Filter appointments
    val filteredAppointments = remember(allAppointments, selectedDate, selectedStatusFilter, searchQuery) {
        allAppointments.filter { appt ->
            val dateMatches = selectedStatusFilter == "All" || appt.appointmentDate == selectedDate
            val statusMatches = selectedStatusFilter == "All" || appt.status.equals(selectedStatusFilter, ignoreCase = true)
            val searchMatches = searchQuery.isBlank() ||
                    appt.customerName.contains(searchQuery, ignoreCase = true) ||
                    appt.customerPhone.contains(searchQuery, ignoreCase = true) ||
                    appt.serviceName.contains(searchQuery, ignoreCase = true) ||
                    appt.notes.contains(searchQuery, ignoreCase = true)
            dateMatches && statusMatches && searchMatches
        }
    }

    // Active booked slots for the selected date
    val bookedSlotsForSelectedDate = remember(allAppointments, selectedDate) {
        allAppointments
            .filter { it.appointmentDate == selectedDate && it.status != "Cancelled" }
            .map { it.appointmentTime }
            .toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Appointments & Bookings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (appointmentSettings.bookingEnabled) {
                                "${appointmentSettings.slotDurationMinutes}m slots • ${appointmentSettings.formatWorkingHours()}"
                            } else {
                                "Calendar Booking Disabled in Settings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (appointmentSettings.bookingEnabled) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseRed
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("appointments_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showReminderSettingsDialog = true },
                        modifier = Modifier.testTag("appointments_notifications_button")
                    ) {
                        Icon(
                            imageVector = if (appointmentSettings.notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Notification & Reminder Settings",
                            tint = if (appointmentSettings.notificationsEnabled) AppointmentPurple else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("appointments_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Calendar Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    appointmentToEdit = null
                    showBookingSheet = true
                },
                containerColor = AppointmentPurple,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_book_appointment")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneCallback,
                        contentDescription = "Book Appointment"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Book Now / Book Caller",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Notification Permission Banner (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifications Disabled",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Enable to receive immediate booking alerts & 24h reminders.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Exact Alarm Permission Banner (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppointmentPurpleContainer.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = AppointmentPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exact Alarms Permission",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AppointmentPurpleText
                            )
                            Text(
                                text = "Required for precise 24-hour pre-appointment background alarms.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppointmentPurple),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // DATE SELECTOR HORIZONTAL STRIP
            DateStripSection(
                selectedDate = selectedDate,
                onSelectDate = {
                    selectedDate = it
                },
                allAppointments = allAppointments,
                context = context
            )

            // SEARCH & STATUS FILTERS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search caller name, phone, service...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Status Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusList = listOf("All", "Scheduled", "Confirmed", "Completed", "Cancelled")
                    statusList.forEach { status ->
                        val isSelected = selectedStatusFilter == status
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStatusFilter = status },
                            label = {
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppointmentPurple,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = AppointmentPurple
                            )
                        )
                    }
                }
            }

            // APPOINTMENTS LIST OR EMPTY STATE
            if (filteredAppointments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = if (searchQuery.isNotBlank()) "No appointments match your search" else "No appointments for this date",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap 'Book Now / Book Caller' to assign an available time slot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                appointmentToEdit = null
                                showBookingSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppointmentPurple)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Book First Appointment")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = if (selectedStatusFilter == "All") "All Appointments (${filteredAppointments.size})" else "$selectedStatusFilter Bookings on $selectedDate (${filteredAppointments.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(filteredAppointments, key = { it.id }) { appointment ->
                        AppointmentItemCard(
                            appointment = appointment,
                            onCall = { phone ->
                                dialPhone(context, phone)
                            },
                            onSms = { phone ->
                                sendSms(context, phone, appointment)
                            },
                            onEdit = {
                                appointmentToEdit = appointment
                                showBookingSheet = true
                            },
                            onDelete = {
                                appointmentToDelete = appointment
                            },
                            onStatusChange = { newStatus ->
                                scope.launch {
                                    repository.updateAppointmentStatus(appointment.id, newStatus)
                                    Toast.makeText(context, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
                    }
                }
            }
        }
    }

    // BOOKING / EDIT BOTTOM SHEET
    if (showBookingSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBookingSheet = false
                appointmentToEdit = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            BookAppointmentSheetContent(
                initialAppointment = appointmentToEdit,
                preselectedDate = selectedDate,
                availableTimeSlots = availableTimeSlots,
                bookedSlots = bookedSlotsForSelectedDate,
                cachedProducts = repository.getCachedProducts(),
                onDismiss = {
                    showBookingSheet = false
                    appointmentToEdit = null
                },
                onSave = { newOrUpdated ->
                    scope.launch {
                        if (newOrUpdated.id == 0L) {
                            repository.saveAppointment(newOrUpdated)
                            Toast.makeText(context, "Appointment booked successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            repository.updateAppointment(newOrUpdated)
                            Toast.makeText(context, "Appointment updated!", Toast.LENGTH_SHORT).show()
                        }
                        showBookingSheet = false
                        appointmentToEdit = null
                    }
                },
                context = context
            )
        }
    }

    // DELETE CONFIRMATION DIALOG
    appointmentToDelete?.let { appt ->
        AlertDialog(
            onDismissRequest = { appointmentToDelete = null },
            title = { Text("Delete Appointment") },
            text = { Text("Are you sure you want to delete the appointment for ${appt.customerName} at ${appt.appointmentTime}?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteAppointment(appt)
                            Toast.makeText(context, "Appointment deleted", Toast.LENGTH_SHORT).show()
                            appointmentToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { appointmentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // NOTIFICATION & REMINDER SETTINGS DIALOG
    if (showReminderSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showReminderSettingsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = AppointmentPurple,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Notification & Reminder Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Push notifications toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Push Notifications", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Immediate booking alerts and cancellation notices", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = appointmentSettings.notificationsEnabled,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                repository.setAppointmentSettings(appointmentSettings.copy(notificationsEnabled = checked))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppointmentPurple, checkedTrackColor = AppointmentPurpleContainer)
                        )
                    }

                    // 24-Hour Alarm Reminder toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("24-Hour Pre-Appointment Alarm", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Exact AlarmManager reminder 24h prior to appointment", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = appointmentSettings.reminder24hEnabled,
                            onCheckedChange = { checked ->
                                repository.setAppointmentSettings(appointmentSettings.copy(reminder24hEnabled = checked))
                                if (checked) {
                                    // Reschedule alarms for upcoming active appointments
                                    scope.launch {
                                        for (appt in allAppointments) {
                                            if (appt.status != "Cancelled" && appt.status != "Completed") {
                                                AppointmentAlarmScheduler.schedule24hReminder(context, appt)
                                            }
                                        }
                                    }
                                } else {
                                    // Cancel all active reminder alarms
                                    for (appt in allAppointments) {
                                        AppointmentAlarmScheduler.cancelReminder(context, appt.id)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppointmentPurple, checkedTrackColor = AppointmentPurpleContainer)
                        )
                    }

                    // Automated Client SMS toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automated Client SMS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically dispatch confirmation and 24h SMS to clients", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = appointmentSettings.automatedSmsEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (SmsHelper.canSendSms(context)) {
                                        repository.setAppointmentSettings(appointmentSettings.copy(automatedSmsEnabled = true))
                                    } else {
                                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                    }
                                } else {
                                    repository.setAppointmentSettings(appointmentSettings.copy(automatedSmsEnabled = false))
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppointmentPurple, checkedTrackColor = AppointmentPurpleContainer)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReminderSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AppointmentPurple)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun DateStripSection(
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    allAppointments: List<AppointmentEntity>,
    context: Context
) {
    val calendar = Calendar.getInstance()
    val dateFormatIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val dayNameFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val dayNumberFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("MMM", Locale.getDefault()) }

    val daysList = remember {
        val list = mutableListOf<DateInfo>()
        val cal = Calendar.getInstance()
        for (i in 0 until 14) {
            val d = cal.time
            list.add(
                DateInfo(
                    iso = dateFormatIso.format(d),
                    dayName = if (i == 0) "Today" else if (i == 1) "Tmrw" else dayNameFormat.format(d),
                    dayNum = dayNumberFormat.format(d),
                    month = monthFormat.format(d)
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Horizontal Days List
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            daysList.forEach { day ->
                val isSelected = day.iso == selectedDate
                val countForDay = allAppointments.count { it.appointmentDate == day.iso && it.status != "Cancelled" }

                Surface(
                    onClick = { onSelectDate(day.iso) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) AppointmentPurple else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) AppointmentPurple else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .width(52.dp)
                        .height(60.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = day.dayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = day.dayNum,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        if (countForDay > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else AppointmentPurple)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                    }
                }
            }
        }

        // Pick Custom Date button
        IconButton(
            onClick = {
                val cal = Calendar.getInstance()
                val dpd = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val picked = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth)
                        }
                        onSelectDate(dateFormatIso.format(picked.time))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                dpd.show()
            },
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = "Pick Date",
                tint = AppointmentPurple
            )
        }
    }
}

data class DateInfo(
    val iso: String,
    val dayName: String,
    val dayNum: String,
    val month: String
)

@Composable
fun AppointmentItemCard(
    appointment: AppointmentEntity,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val statusColor = when (appointment.status.lowercase()) {
        "confirmed" -> IncomeGreen
        "scheduled" -> BalanceBlue
        "completed" -> Color(0xFF64748B)
        "cancelled" -> ExpenseRed
        else -> MaterialTheme.colorScheme.primary
    }

    val statusBg = when (appointment.status.lowercase()) {
        "confirmed" -> Color(0xFFDCFCE7)
        "scheduled" -> Color(0xFFDBEAFE)
        "completed" -> Color(0xFFF1F5F9)
        "cancelled" -> Color(0xFFFEE2E2)
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Avatar, Name, Time, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(statusBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = appointment.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${appointment.appointmentTime} (${appointment.durationMinutes}m)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${appointment.appointmentDate}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Status Badge & Menu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBg,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = appointment.status.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mark as Confirmed") },
                                onClick = {
                                    onStatusChange("Confirmed")
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreen)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mark as Completed") },
                                onClick = {
                                    onStatusChange("Completed")
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mark as Cancelled") },
                                onClick = {
                                    onStatusChange("Cancelled")
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = ExpenseRed)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Edit Details") },
                                onClick = {
                                    onEdit()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = ExpenseRed) },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = ExpenseRed)
                                }
                            )
                        }
                    }
                }
            }

            // Service & Price details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Service: ${appointment.serviceName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (appointment.price > 0) {
                    Text(
                        text = String.format(Locale.US, "$%.2f", appointment.price),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = IncomeGreen
                    )
                }
            }

            // Notes if present
            if (appointment.notes.isNotBlank()) {
                Text(
                    text = "📝 ${appointment.notes}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            // 24-Hour Alarm Reminder Status Indicator
            val isCancelled = appointment.status.equals("Cancelled", ignoreCase = true)
            val isCompleted = appointment.status.equals("Completed", ignoreCase = true)
            val reminderTriggerTime = AppointmentAlarmScheduler.calculate24hReminderTime(appointment.appointmentDate, appointment.appointmentTime)
            val hasScheduledReminder = !isCancelled && !isCompleted && reminderTriggerTime != null && reminderTriggerTime > System.currentTimeMillis()

            if (isCancelled) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ExpenseRed.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reminder Alarm Cancelled", fontSize = 11.sp, color = ExpenseRed, fontWeight = FontWeight.Medium)
                    }
                }
            } else if (hasScheduledReminder && reminderTriggerTime != null) {
                val formattedReminder = SimpleDateFormat("MMM d 'at' hh:mm a", Locale.getDefault()).format(Date(reminderTriggerTime))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppointmentPurpleContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = AppointmentPurple, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("24h Alarm Set: $formattedReminder", fontSize = 11.sp, color = AppointmentPurpleText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // CALLER PHONE ACTIONS ROW: One-tap Call & One-tap SMS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (appointment.customerPhone.isNotBlank()) appointment.customerPhone else "No phone provided",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (appointment.customerPhone.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Quick SMS
                        OutlinedButton(
                            onClick = { onSms(appointment.customerPhone) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Message, contentDescription = "SMS", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "SMS", fontSize = 12.sp)
                        }

                        // Quick Call
                        Button(
                            onClick = { onCall(appointment.customerPhone) },
                            colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookAppointmentSheetContent(
    initialAppointment: AppointmentEntity?,
    preselectedDate: String,
    availableTimeSlots: List<String>,
    bookedSlots: Set<String>,
    cachedProducts: List<com.example.data.model.ProductItem>,
    onDismiss: () -> Unit,
    onSave: (AppointmentEntity) -> Unit,
    context: Context
) {
    var customerName by remember { mutableStateOf(initialAppointment?.customerName ?: "") }
    var customerPhone by remember { mutableStateOf(initialAppointment?.customerPhone ?: "") }
    var serviceName by remember { mutableStateOf(initialAppointment?.serviceName ?: "") }
    var appointmentDate by remember { mutableStateOf(initialAppointment?.appointmentDate ?: preselectedDate) }
    var appointmentTime by remember {
        mutableStateOf(initialAppointment?.appointmentTime ?: (availableTimeSlots.firstOrNull() ?: "09:00 AM"))
    }
    var durationMinutes by remember { mutableStateOf(initialAppointment?.durationMinutes ?: 30) }
    var status by remember { mutableStateOf(initialAppointment?.status ?: "Scheduled") }
    var notes by remember { mutableStateOf(initialAppointment?.notes ?: "") }
    var priceText by remember { mutableStateOf(if (initialAppointment != null && initialAppointment.price > 0) initialAppointment.price.toString() else "") }

    var customTimeInput by remember { mutableStateOf(false) }

    val isEditing = initialAppointment != null && initialAppointment.id != 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isEditing) "Edit Appointment" else "Book Now / Book Caller",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fill client & schedule booking details",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
        }

        HorizontalDivider()

        // 1. Caller Name & Phone
        OutlinedTextField(
            value = customerName,
            onValueChange = { customerName = it },
            label = { Text("Caller / Customer Name *") },
            placeholder = { Text("e.g. Sarah Jenkins") },
            leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_appointment_customer_name")
        )

        OutlinedTextField(
            value = customerPhone,
            onValueChange = { customerPhone = it },
            label = { Text("Caller Phone Number *") },
            placeholder = { Text("e.g. +61 400 123 456") },
            leadingIcon = { Icon(imageVector = Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_appointment_customer_phone")
        )

        // 2. Service Selection (Quick chips from business catalog or manual typing)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Service Requested:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                placeholder = { Text("e.g. Eyebrow Threading, Full Face, Tinting") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_appointment_service")
            )

            // Popular services quick chips
            if (cachedProducts.isNotEmpty()) {
                Text(
                    text = "Quick Select from Catalog:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quickServices = cachedProducts.take(8)
                    quickServices.forEach { item ->
                        val isSelected = serviceName.equals(item.name, ignoreCase = true)
                        Surface(
                            onClick = {
                                serviceName = item.name
                                if (priceText.isBlank() || priceText == "0.0") {
                                    priceText = String.format(Locale.US, "%.2f", item.price)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AppointmentPurpleContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppointmentPurple else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = "${item.name} ($${item.price.toInt()})",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppointmentPurpleText else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Date Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Appointment Date:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = appointmentDate,
                    onValueChange = { appointmentDate = it },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val parts = appointmentDate.split("-")
                            val y = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                            val m = (parts.getOrNull(1)?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)) - 1
                            val d = parts.getOrNull(2)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

                            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                val picked = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                                appointmentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(picked.time)
                            }, y, m, d).show()
                        }) {
                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. Time Slot Selection
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Time Slot (From Settings):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { customTimeInput = !customTimeInput },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (customTimeInput) "Choose from Slots" else "Custom Time",
                        fontSize = 12.sp,
                        color = AppointmentPurple
                    )
                }
            }

            if (customTimeInput) {
                OutlinedTextField(
                    value = appointmentTime,
                    onValueChange = { appointmentTime = it },
                    label = { Text("Custom Time (e.g. 10:15 AM)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                if (availableTimeSlots.isEmpty()) {
                    Text(
                        text = "No time slots configured. Set hours in Settings.",
                        fontSize = 12.sp,
                        color = ExpenseRed
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableTimeSlots.forEach { slot ->
                            val isSelected = appointmentTime.equals(slot, ignoreCase = true)
                            val isAlreadyBooked = bookedSlots.contains(slot) && !(isEditing && initialAppointment?.appointmentTime == slot)

                            Surface(
                                onClick = {
                                    appointmentTime = slot
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isSelected -> AppointmentPurple
                                    isAlreadyBooked -> Color(0xFFFEE2E2)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> AppointmentPurple
                                        isAlreadyBooked -> ExpenseRed.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = slot,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.White
                                            isAlreadyBooked -> ExpenseRed
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (isAlreadyBooked && !isSelected) {
                                        Text(
                                            text = " (Booked)",
                                            fontSize = 9.sp,
                                            color = ExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Duration & Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Duration:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { dur ->
                        val isSelected = durationMinutes == dur
                        Surface(
                            onClick = { durationMinutes = dur },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AppointmentPurple else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${dur}m",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Status:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Scheduled", "Confirmed").forEach { st ->
                        val isSelected = status == st
                        Surface(
                            onClick = { status = st },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) (if (st == "Confirmed") IncomeGreen else BalanceBlue) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = st,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Price (Optional)
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            label = { Text("Price ($ AUD, Optional)") },
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // 7. Caller Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Caller Notes / Requests") },
            placeholder = { Text("e.g. First-time client, prefers window seat, allergic to latex") },
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // 8. Notifications & 24h Reminder Summary Card
        val reminderTime = remember(appointmentDate, appointmentTime) {
            AppointmentAlarmScheduler.calculate24hReminderTime(appointmentDate, appointmentTime)
        }
        val isFutureReminder = reminderTime != null && reminderTime > System.currentTimeMillis()

        Card(
            colors = CardDefaults.cardColors(containerColor = AppointmentPurpleContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = AppointmentPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Immediate Notification: Active on confirm",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppointmentPurpleText
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = if (isFutureReminder) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFutureReminder && reminderTime != null) {
                            "24h Alarm: Scheduled for ${SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(reminderTime))}"
                        } else {
                            "24h Alarm: Within 24h (Immediate confirmation will fire)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isFutureReminder) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Save Button
        Button(
            onClick = {
                if (customerName.isBlank()) {
                    Toast.makeText(context, "Please enter customer / caller name", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (customerPhone.isBlank()) {
                    Toast.makeText(context, "Please enter customer phone number", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
                val entity = AppointmentEntity(
                    id = initialAppointment?.id ?: 0L,
                    customerName = customerName.trim(),
                    customerPhone = customerPhone.trim(),
                    serviceName = if (serviceName.isNotBlank()) serviceName.trim() else "Appointment",
                    appointmentDate = appointmentDate,
                    appointmentTime = appointmentTime.trim(),
                    durationMinutes = durationMinutes,
                    status = status,
                    notes = notes.trim(),
                    price = parsedPrice,
                    createdAt = initialAppointment?.createdAt ?: System.currentTimeMillis()
                )
                onSave(entity)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppointmentPurple),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_appointment_button")
        ) {
            Text(
                text = if (isEditing) "Save Changes" else "Confirm & Book Appointment",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun dialPhone(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to launch dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun sendSms(context: Context, phone: String, appointment: AppointmentEntity) {
    try {
        val message = "Hi ${appointment.customerName}, your appointment for ${appointment.serviceName} is scheduled on ${appointment.appointmentDate} at ${appointment.appointmentTime}."
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to launch SMS app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
