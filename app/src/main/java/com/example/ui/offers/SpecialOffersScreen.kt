package com.example.ui.offers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.pref.BusinessProfile
import com.example.data.repository.TransactionRepository
import com.example.util.ContactHelper
import com.example.util.MediaShareHelper
import com.example.util.SmsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OfferContentType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TEXT_ONLY("Text Only", Icons.Default.Chat),
    IMAGE("Image Offer", Icons.Default.Image),
    VIDEO_REEL("Video / Reel", Icons.Default.VideoLibrary),
    ANIMATION_GIF("Animation (GIF)", Icons.Default.Gif),
    TEXT_AND_MEDIA("All-in-One", Icons.Default.Celebration)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialOffersScreen(
    repository: TransactionRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val businessProfile by repository.businessProfile.collectAsState(initial = BusinessProfile())

    // Selected offer type
    var selectedContentType by remember { mutableStateOf(OfferContentType.TEXT_AND_MEDIA) }

    // Offer title & text message
    var offerTitle by remember { mutableStateOf("Weekend Glow Special - 20% OFF!") }
    var offerMessage by remember {
        mutableStateOf(
            "✨ Special Offer from ${if (businessProfile.businessName.isNotBlank()) businessProfile.businessName else "our salon"}! ✨\n\n" +
                    "Book any Facial, Hair Styling, or Threading package this week and get an exclusive 20% OFF! \uD83C\uDF89\n\n" +
                    "📞 Call/WhatsApp: ${if (businessProfile.phoneMobile.isNotBlank()) businessProfile.phoneMobile else "us"} to reserve your spot!\n" +
                    "📍 Location: ${if (businessProfile.businessAddress.isNotBlank()) businessProfile.businessAddress else "In store"}\n" +
                    "Limited slots available. Show this message upon arrival."
        )
    }

    // Media attachment state
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaMimeType by remember { mutableStateOf<String?>(null) }

    // Contacts state
    var allContacts by remember { mutableStateOf<List<ContactHelper.ContactItem>>(emptyList()) }
    var selectedContacts by remember { mutableStateOf<Set<ContactHelper.ContactItem>>(emptySet()) }
    var isLoadingContacts by remember { mutableStateOf(false) }
    var contactSearchQuery by remember { mutableStateOf("") }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Modal Sheet for Contact Picker
    var showContactPickerSheet by remember { mutableStateOf(false) }

    // Dispatch progress/status dialog
    var isSendingSmsBatch by remember { mutableStateOf(false) }
    var smsSentCount by remember { mutableIntStateOf(0) }
    var smsTotalCount by remember { mutableIntStateOf(0) }

    // Launchers
    val requestContactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        coroutineScope.launch {
            isLoadingContacts = true
            allContacts = ContactHelper.getAllAvailableContacts(context)
            isLoadingContacts = false
        }
    }

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSmsPermission = granted
    }

    // Modern Android Photo & Video Picker (Zero-permission)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            val cr = context.contentResolver
            mediaMimeType = cr.getType(uri) ?: "image/*"
        }
    }

    // Load initial contacts
    LaunchedEffect(Unit) {
        isLoadingContacts = true
        allContacts = ContactHelper.getAllAvailableContacts(context)
        isLoadingContacts = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Special Offers & Promos",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Text, images, reels & animations to contacts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("special_offers_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. WhatsApp / Share Sheet Action
                    Button(
                        onClick = {
                            if (selectedContacts.isEmpty()) {
                                Toast.makeText(context, "Please select at least one contact first", Toast.LENGTH_SHORT).show()
                                showContactPickerSheet = true
                                return@Button
                            }
                            val phones = selectedContacts.map { it.phoneNumber }
                            MediaShareHelper.shareSpecialOffer(
                                context = context,
                                recipientPhoneNumbers = phones,
                                offerText = offerMessage,
                                mediaUri = selectedMediaUri,
                                mimeType = mediaMimeType,
                                preferredApp = if (phones.size == 1) MediaShareHelper.ShareTarget.WHATSAPP else MediaShareHelper.ShareTarget.CHOOSER
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_share_promo"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366) // WhatsApp Green
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedContacts.size <= 1) "WhatsApp / Share" else "Share (${selectedContacts.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. Direct SMS Batch (if text only or text with link)
                    FilledTonalButton(
                        onClick = {
                            if (selectedContacts.isEmpty()) {
                                Toast.makeText(context, "Please select at least one contact", Toast.LENGTH_SHORT).show()
                                showContactPickerSheet = true
                                return@FilledTonalButton
                            }
                            if (!hasSmsPermission) {
                                requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                return@FilledTonalButton
                            }

                            // Send direct SMS to selected contacts
                            coroutineScope.launch {
                                isSendingSmsBatch = true
                                smsTotalCount = selectedContacts.size
                                smsSentCount = 0
                                withContext(Dispatchers.IO) {
                                    for (c in selectedContacts) {
                                        SmsHelper.sendDirectSms(context, c.phoneNumber, offerMessage)
                                        withContext(Dispatchers.Main) {
                                            smsSentCount++
                                        }
                                        kotlinx.coroutines.delay(150) // Small delay between network dispatches
                                    }
                                }
                                isSendingSmsBatch = false
                                Toast.makeText(context, "Direct SMS successfully sent to $smsSentCount contact(s)!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_direct_sms"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Direct SMS (${selectedContacts.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // A. OFFER FORMAT SELECTOR (Text, Image, Reel, Animation, All-in-One)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. Select Offer Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(OfferContentType.values()) { type ->
                            val isSelected = selectedContentType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedContentType = type },
                                label = { Text(type.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            // B. MEDIA ATTACHMENT CARD (Photo, Video Reel, GIF Animation)
            if (selectedContentType != OfferContentType.TEXT_ONLY) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Attach Media (Image, Reel, GIF)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedMediaUri != null) {
                                TextButton(
                                    onClick = {
                                        selectedMediaUri = null
                                        mediaMimeType = null
                                    }
                                ) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedMediaUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = selectedMediaUri,
                                    contentDescription = "Offer media",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (mediaMimeType?.startsWith("video/") == true) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Video reel",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Attached: ${mediaMimeType ?: "Media file"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            // Media upload placeholder box
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clickable {
                                        val mediaTypeRequest = when (selectedContentType) {
                                            OfferContentType.VIDEO_REEL -> ActivityResultContracts.PickVisualMedia.VideoOnly
                                            OfferContentType.ANIMATION_GIF, OfferContentType.IMAGE -> ActivityResultContracts.PickVisualMedia.ImageOnly
                                            else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                        }
                                        mediaPickerLauncher.launch(
                                            PickVisualMediaRequest(mediaTypeRequest)
                                        )
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = when (selectedContentType) {
                                            OfferContentType.VIDEO_REEL -> Icons.Default.VideoLibrary
                                            OfferContentType.ANIMATION_GIF -> Icons.Default.Gif
                                            else -> Icons.Default.AddPhotoAlternate
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = when (selectedContentType) {
                                            OfferContentType.VIDEO_REEL -> "Tap to select Video / Reel from Gallery"
                                            OfferContentType.ANIMATION_GIF -> "Tap to select GIF Animation from Gallery"
                                            else -> "Tap to choose Photo, Flyer, or Video"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "High quality • No compression loss",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // C. PROMOTIONAL TEXT & MESSAGE CARD
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Promotional Text Message",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Quick Template Dropdown
                        var showTemplatesMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { showTemplatesMenu = true }) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Templates")
                            }
                            DropdownMenu(
                                expanded = showTemplatesMenu,
                                onDismissRequest = { showTemplatesMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("🎉 20% Weekend Discount") },
                                    onClick = {
                                        offerTitle = "Weekend Special - 20% OFF!"
                                        offerMessage = "✨ Weekend Special from ${businessProfile.businessName.ifBlank { "Our Salon" }}! ✨\n\nEnjoy 20% OFF on all treatments this Saturday and Sunday. Relax, refresh, and shine!\n\n📞 Call/WhatsApp: ${businessProfile.phoneMobile}\n📍 ${businessProfile.businessAddress}\nBook today before slots fill up!"
                                        showTemplatesMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("💅 VIP Client Loyalty Offer") },
                                    onClick = {
                                        offerTitle = "VIP Customer Loyalty Reward"
                                        offerMessage = "🌟 Exclusive for our valued clients! Book your next appointment this month and receive a complimentary hair treatment or eyebrow shaping.\n\nThank you for choosing ${businessProfile.businessName.ifBlank { "us" }}!\n\nBook now: ${businessProfile.phoneMobile}"
                                        showTemplatesMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🌸 Festive / Holiday Package") },
                                    onClick = {
                                        offerTitle = "Festive Season Package Deal"
                                        offerMessage = "🌸 Get Festive Ready! Complete Makeover & Facial package now for only $49 (Regular $75)!\n\nLimited period offer at ${businessProfile.businessName.ifBlank { "our salon" }}.\n\nReserve now: ${businessProfile.phoneMobile}"
                                        showTemplatesMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = offerTitle,
                        onValueChange = { offerTitle = it },
                        label = { Text("Offer Headline") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = offerMessage,
                        onValueChange = { offerMessage = it },
                        label = { Text("Message Body (Supports Emojis & Links)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 8,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // D. RECIPIENTS & CONTACT SELECTOR CARD
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "4. Select Phone Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedContacts.size} contact(s) selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                if (!hasContactsPermission) {
                                    requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                                showContactPickerSheet = true
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Contacts")
                        }
                    }

                    if (selectedContacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selected Recipients:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { selectedContacts = emptySet() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Clear All", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(selectedContacts.toList()) { contact ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        selectedContacts = selectedContacts - contact
                                    },
                                    label = {
                                        Text(
                                            text = contact.name.ifBlank { contact.phoneNumber },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove contact",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable {
                                    if (!hasContactsPermission) {
                                        requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                    showContactPickerSheet = true
                                }
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tap to pick 1 or more phone contacts or salon clients",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Quick hint
            Text(
                text = "💡 Tip: Tap 'WhatsApp / Share' to send rich image/reel media and prefilled messages, or 'Direct SMS' for quick instant cellular SMS delivery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ==========================================
    // BOTTOM SHEET: MULTI-CONTACT PICKER
    // ==========================================
    if (showContactPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showContactPickerSheet = false },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Recipients",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedContacts.size} of ${allContacts.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        TextButton(
                            onClick = {
                                selectedContacts = if (selectedContacts.size == allContacts.size) {
                                    emptySet()
                                } else {
                                    allContacts.toSet()
                                }
                            }
                        ) {
                            Text(if (selectedContacts.size == allContacts.size) "Deselect All" else "Select All")
                        }
                        Button(
                            onClick = { showContactPickerSheet = false },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = contactSearchQuery,
                    onValueChange = { contactSearchQuery = it },
                    placeholder = { Text("Search by name or phone number...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (contactSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { contactSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (!hasContactsPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Import Phone Contacts",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Grant contact permission to select directly from your address book.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = { requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val filteredContacts = remember(allContacts, contactSearchQuery) {
                    if (contactSearchQuery.isBlank()) {
                        allContacts
                    } else {
                        val q = contactSearchQuery.lowercase().trim()
                        allContacts.filter {
                            it.name.lowercase().contains(q) || it.phoneNumber.contains(q)
                        }
                    }
                }

                if (isLoadingContacts) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (contactSearchQuery.isNotBlank()) "No matching contacts found" else "No contacts available yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredContacts, key = { it.phoneNumber }) { contact ->
                            val isChecked = selectedContacts.contains(contact)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedContacts = if (isChecked) {
                                            selectedContacts - contact
                                        } else {
                                            selectedContacts + contact
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedContacts = if (checked) {
                                            selectedContacts + contact
                                        } else {
                                            selectedContacts - contact
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = contact.name.firstOrNull()?.uppercase() ?: "#",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (contact.source == "Salon Client") Color(0xFFE0E7FF) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = contact.source,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = if (contact.source == "Salon Client") Color(0xFF3730A3) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // SENDING PROGRESS DIALOG
    // ==========================================
    if (isSendingSmsBatch) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissible while transmitting */ },
            title = { Text("Sending Direct SMS Promos") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { if (smsTotalCount > 0) smsSentCount.toFloat() / smsTotalCount.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Dispatched $smsSentCount of $smsTotalCount messages...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {}
        )
    }
}
