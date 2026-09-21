package com.example.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PairedDeviceEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.repository.TransactionRepository
import com.example.sync.discovery.DiscoveredDevice
import com.example.sync.engine.SyncState
import com.example.sync.model.SyncProtocol
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSyncScreen(
    repository: TransactionRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val syncState by repository.syncState.collectAsState()
    val lastSyncTimestamp by repository.lastSyncTimestamp.collectAsState()
    val lastSyncMessage by repository.lastSyncMessage.collectAsState()
    val pendingCount by repository.pendingSyncCount.collectAsState(initial = 0)
    val pairedDevices by repository.pairedDevices.collectAsState(initial = emptyList())
    val discoveredDevices by repository.discoveredDevices.collectAsState()
    val isDiscovering by repository.isDiscoveringDevices.collectAsState()
    val conflicts by repository.syncConflicts.collectAsState(initial = emptyList())

    var showManualPairDialog by remember { mutableStateOf(false) }
    var selectedDeviceToPair by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showConflictDialog by remember { mutableStateOf<SyncConflictEntity?>(null) }
    var snackbarHostState = remember { SnackbarHostState() }

    // Start discovery automatically when screen is opened
    DisposableEffect(Unit) {
        repository.startWifiDiscovery()
        onDispose {
            repository.stopWifiDiscovery()
        }
    }

    Scaffold(
        modifier = modifier.testTag("wifi_sync_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Wi-Fi Synchronization", fontWeight = FontWeight.Bold)
                        Text(
                            "Android ↔ Windows Local Sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("sync_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isDiscovering) {
                                repository.stopWifiDiscovery()
                            } else {
                                repository.startWifiDiscovery()
                            }
                        },
                        modifier = Modifier.testTag("scan_network_icon_button")
                    ) {
                        Icon(
                            imageVector = if (isDiscovering) Icons.Default.WifiTethering else Icons.Default.WifiFind,
                            contentDescription = "Scan Wi-Fi",
                            tint = if (isDiscovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 1. Status Overview Card
            item {
                SyncStatusOverviewCard(
                    deviceId = repository.myDeviceId,
                    deviceName = repository.myDeviceName,
                    syncState = syncState,
                    lastSyncTimestamp = lastSyncTimestamp,
                    lastSyncMessage = lastSyncMessage,
                    pendingCount = pendingCount,
                    onSyncNow = {
                        scope.launch {
                            val result = repository.triggerSyncNow()
                            if (result.success) {
                                snackbarHostState.showSnackbar(
                                    "Synced! Pulled: ${result.pulledCount}, Pushed: ${result.pushedCount}"
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    result.errorMessage ?: "Sync failed"
                                )
                            }
                        }
                    },
                    onScanToggle = {
                        if (isDiscovering) repository.stopWifiDiscovery() else repository.startWifiDiscovery()
                    },
                    isScanning = isDiscovering
                )
            }

            // 2. Unresolved Conflicts Card (if any)
            if (conflicts.isNotEmpty()) {
                item {
                    ConflictAlertCard(
                        conflicts = conflicts,
                        onReviewConflict = { showConflictDialog = it }
                    )
                }
            }

            // 3. Paired Windows Computers Section
            item {
                Text(
                    text = "Paired Windows Computers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (pairedDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LaptopMac,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No Windows PC Paired",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Pair with your Windows desktop running My Business to enable two-way offline synchronization.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showManualPairDialog = true },
                                modifier = Modifier.testTag("manual_pair_button")
                            ) {
                                Icon(Icons.Default.AddLink, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pair Windows PC Manually")
                            }
                        }
                    }
                }
            } else {
                items(pairedDevices) { device ->
                    PairedDeviceCard(
                        device = device,
                        onUnpair = {
                            scope.launch {
                                repository.unpairDevice(device.deviceId)
                                snackbarHostState.showSnackbar("Unpaired ${device.deviceName}")
                            }
                        },
                        onSyncNow = {
                            scope.launch {
                                val res = repository.triggerSyncNow()
                                if (res.success) {
                                    snackbarHostState.showSnackbar("Synchronized with ${device.deviceName}")
                                } else {
                                    snackbarHostState.showSnackbar(res.errorMessage ?: "Failed to sync")
                                }
                            }
                        }
                    )
                }
            }

            // 4. Discovered Computers on Wi-Fi
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered on Wi-Fi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isDiscovering) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Scanning...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (discoveredDevices.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Searching local Wi-Fi for My Business on Windows...\nMake sure both devices are on the same Wi-Fi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(discoveredDevices) { discDevice ->
                    DiscoveredDeviceItem(
                        device = discDevice,
                        isAlreadyPaired = pairedDevices.any { it.deviceId == discDevice.deviceId || it.ipAddress == discDevice.host },
                        onPair = {
                            selectedDeviceToPair = discDevice
                        }
                    )
                }
            }

            // 5. Offline Architecture Info Card
            item {
                OfflineArchitectureCard()
            }
        }
    }

    // Dialog: Pair with Discovered Device
    selectedDeviceToPair?.let { discDevice ->
        PairDeviceDialog(
            device = discDevice,
            onDismiss = { selectedDeviceToPair = null },
            onConfirmPair = { pin ->
                selectedDeviceToPair = null
                scope.launch {
                    val result = repository.pairDevice(discDevice.host, discDevice.port, pin)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Successfully paired with ${discDevice.deviceName}!")
                    } else {
                        snackbarHostState.showSnackbar("Pairing failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }

    // Dialog: Manual Pair
    if (showManualPairDialog) {
        ManualPairDialog(
            onDismiss = { showManualPairDialog = false },
            onConfirmPair = { host, port, pin ->
                showManualPairDialog = false
                scope.launch {
                    val result = repository.pairDevice(host, port, pin)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Successfully paired with Windows desktop!")
                    } else {
                        snackbarHostState.showSnackbar("Pairing failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }

    // Dialog: Conflict Resolution
    showConflictDialog?.let { conflict ->
        ConflictResolutionDialog(
            conflict = conflict,
            onDismiss = { showConflictDialog = null },
            onResolve = { keepLocal ->
                scope.launch {
                    repository.resolveConflict(conflict.id, keepLocal)
                    showConflictDialog = null
                    snackbarHostState.showSnackbar("Conflict resolved (${if (keepLocal) "Kept Android" else "Kept Windows"})")
                }
            }
        )
    }
}

@Composable
private fun SyncStatusOverviewCard(
    deviceId: String,
    deviceName: String,
    syncState: SyncState,
    lastSyncTimestamp: Long,
    lastSyncMessage: String,
    pendingCount: Int,
    onSyncNow: () -> Unit,
    onScanToggle: () -> Unit,
    isScanning: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: $deviceId",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sync State Badge
                SyncStateBadge(syncState)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Last Synchronized",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (lastSyncTimestamp > 0) {
                            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp))
                        } else {
                            "Never"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Pending Queue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (pendingCount > 0) "$pendingCount record(s) queued" else "All records up to date",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (pendingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (lastSyncMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = lastSyncMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_now_button"),
                    enabled = syncState != SyncState.SYNCING
                ) {
                    if (syncState == SyncState.SYNCING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Now")
                    }
                }

                OutlinedButton(
                    onClick = onScanToggle,
                    modifier = Modifier.testTag("scan_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isScanning) "Stop" else "Scan")
                }
            }
        }
    }
}

@Composable
private fun SyncStateBadge(state: SyncState) {
    val (bgColor, textColor, label) = when (state) {
        SyncState.IDLE -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Ready")
        SyncState.CONNECTING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Connecting")
        SyncState.SYNCING -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Syncing")
        SyncState.SYNCED -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Synced")
        SyncState.OFFLINE -> Triple(Color(0xFFECEFF1), Color(0xFF546E7A), "Offline")
        SyncState.ERROR -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Error")
        SyncState.CONFLICT -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "Conflict")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ConflictAlertCard(
    conflicts: List<SyncConflictEntity>,
    onReviewConflict: (SyncConflictEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF57F17)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "${conflicts.size} Record Conflict(s)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        "Changes occurred on both Android & Windows",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBF360C)
                    )
                }
            }

            TextButton(
                onClick = { onReviewConflict(conflicts.first()) }
            ) {
                Text("Review", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
            }
        }
    }
}

@Composable
private fun PairedDeviceCard(
    device: PairedDeviceEntity,
    onUnpair: () -> Unit,
    onSyncNow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("paired_device_card"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            device.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${device.ipAddress}:${device.port}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (device.certificateFingerprint.isNotBlank()) {
                            Text(
                                "🔒 TLS SHA-256: ${device.certificateFingerprint.take(17)}...",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Paired",
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (device.lastSyncAt > 0) {
                        "Last sync: " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(device.lastSyncAt))
                    } else {
                        "Not synced yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    TextButton(onClick = onSyncNow) {
                        Text("Sync")
                    }
                    TextButton(
                        onClick = onUnpair,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Unpair")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceItem(
    device: DiscoveredDevice,
    isAlreadyPaired: Boolean,
    onPair: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        device.deviceName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "${device.host}:${device.port} • ${device.discoveryMethod}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAlreadyPaired) {
                Text(
                    "Connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Button(
                    onClick = onPair,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Pair")
                }
            }
        }
    }
}

@Composable
private fun OfflineArchitectureCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Offline-First & Local Network",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "• Complete offline operation: Transactions and appointments work 100% without Wi-Fi or Internet.\n" +
                "• Local peer-to-peer: Data syncs directly over your local Wi-Fi router. No cloud account or external internet needed.\n" +
                "• Record-level sync with automatic change queue and conflict resolution.\n" +
                "• Products & menu items are stored locally in Room database with cross-device sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun PairDeviceDialog(
    device: DiscoveredDevice,
    onDismiss: () -> Unit,
    onConfirmPair: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("123456") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with ${device.deviceName}") },
        text = {
            Column {
                Text(
                    "Enter the pairing PIN displayed on your Windows computer:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("6-Digit PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmPair(pin.trim()) }) {
                Text("Confirm Pairing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ManualPairDialog(
    onDismiss: () -> Unit,
    onConfirmPair: (host: String, port: Int, pin: String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var portStr by remember { mutableStateOf("${SyncProtocol.DEFAULT_PORT}") }
    var pin by remember { mutableStateOf("123456") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Windows Connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the IP Address shown in the My Business Windows app:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Windows IP (e.g. 192.168.1.100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portStr,
                    onValueChange = { portStr = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Pairing PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portStr.toIntOrNull() ?: SyncProtocol.DEFAULT_PORT
                    onConfirmPair(host.trim(), port, pin.trim())
                },
                enabled = host.isNotBlank()
            ) {
                Text("Pair Device")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConflictResolutionDialog(
    conflict: SyncConflictEntity,
    onDismiss: () -> Unit,
    onResolve: (keepLocal: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve Conflict") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Conflict detected for ${conflict.entityType} (UUID: ${conflict.recordUuid.take(8)}...)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Android Version: v${conflict.localVersion}", fontWeight = FontWeight.Bold)
                        Text(conflict.localData.take(120) + "...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Windows Version: v${conflict.remoteVersion}", fontWeight = FontWeight.Bold)
                        Text(conflict.remoteData.take(120) + "...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onResolve(true) }) {
                Text("Keep Android")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onResolve(false) }) {
                Text("Keep Windows")
            }
        }
    )
}
