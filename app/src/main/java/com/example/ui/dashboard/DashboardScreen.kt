package com.example.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.TransactionRepository
import com.example.ui.theme.AppointmentPurple
import com.example.ui.theme.AppointmentPurpleContainer
import com.example.ui.theme.AppointmentPurpleText
import com.example.ui.theme.BalanceBlue
import com.example.ui.theme.BalanceBlueContainer
import com.example.ui.theme.BalanceBlueText
import com.example.ui.theme.BillOrange
import com.example.ui.theme.BillOrangeContainer
import com.example.ui.theme.BillOrangeText
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedContainer
import com.example.ui.theme.ExpenseRedText
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenContainer
import com.example.ui.theme.IncomeGreenText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: TransactionRepository,
    onNavigateToEntry: (entryType: String) -> Unit,
    onNavigateToBalanceSheet: () -> Unit,
    onNavigateToAppointments: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMenuManagement: () -> Unit,
    onNavigateToSpecialOffers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transactions by repository.allTransactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val unsyncedCount by repository.unsyncedCount.collectAsStateWithLifecycle(initialValue = 0)
    val businessProfile by repository.businessProfile.collectAsStateWithLifecycle(initialValue = repository.getBusinessProfile())
    val allAppointments by repository.allAppointments.collectAsStateWithLifecycle(initialValue = emptyList())

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    val todayDisplay = remember {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
    }

    val todayAppointments = remember(allAppointments, todayIso) {
        allAppointments.filter { it.appointmentDate == todayIso && it.status != "Cancelled" }
    }

    // Calculate today's summary supporting both date formats
    val todayTransactions = transactions.filter { it.date == todayIso || it.date == todayDisplay }
    val todayIncome = todayTransactions.filter { it.type == "Daily Income" }.sumOf { it.amount }
    val todayExpense = todayTransactions.filter { it.type == "Expense" }.sumOf { it.amount }
    val todayBills = todayTransactions.filter { it.type == "Bill" }.sumOf { it.amount }
    val todayNet = todayIncome - (todayExpense + todayBills)

    val headerTitle = if (businessProfile.businessName.isNotBlank()) {
        businessProfile.businessName
    } else {
        "My Business"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = buildString {
                                append(SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.getDefault()).format(Date()))
                                if (businessProfile.abnAcn.isNotBlank()) {
                                    append(" • ")
                                    append(businessProfile.abnAcn)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. QUICK ACTIONS SECTION (ON TOP)
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 1. Daily Income (Green Accent) -> Opens EntryScreen configured for "Daily Income"
            ActionButtonCard(
                title = "Daily Income",
                subtitle = "Log daily store sales, revenue & collections",
                icon = Icons.Default.TrendingUp,
                accentColor = IncomeGreen,
                containerColor = IncomeGreenContainer,
                textColor = IncomeGreenText,
                testTag = "action_daily_income",
                onClick = { onNavigateToEntry("Daily Income") }
            )

            // 2. Expenses (Red Accent) -> Opens EntryScreen configured for "Expense"
            ActionButtonCard(
                title = "Expenses",
                subtitle = "Record daily operational expenses & supplies",
                icon = Icons.Default.MoneyOff,
                accentColor = ExpenseRed,
                containerColor = ExpenseRedContainer,
                textColor = ExpenseRedText,
                testTag = "action_expenses",
                onClick = { onNavigateToEntry("Expense") }
            )

            // 3. Bills (Orange Accent) -> Opens EntryScreen configured for "Bill"
            ActionButtonCard(
                title = "Bills",
                subtitle = "Track utility, rent, tax & vendor invoices",
                icon = Icons.Default.ReceiptLong,
                accentColor = BillOrange,
                containerColor = BillOrangeContainer,
                textColor = BillOrangeText,
                testTag = "action_bills",
                onClick = { onNavigateToEntry("Bill") }
            )

            // 4. Day-by-Day Balance Sheet (Blue Accent) -> Opens BalanceSheetScreen
            ActionButtonCard(
                title = "Day-by-Day Balance Sheet",
                subtitle = "View full chronological balance breakdowns",
                icon = Icons.Default.AccountBalance,
                accentColor = BalanceBlue,
                containerColor = BalanceBlueContainer,
                textColor = BalanceBlueText,
                testTag = "action_balance_sheet",
                onClick = onNavigateToBalanceSheet
            )

            // 5. Calendar Appointments & Phone Bookings (Purple Accent) -> Opens AppointmentsScreen
            ActionButtonCard(
                title = "Appointments & Bookings",
                subtitle = if (todayAppointments.isNotEmpty()) "${todayAppointments.size} appointments today • Tap to book & manage" else "Book caller appointments & manage time slots",
                icon = Icons.Default.CalendarMonth,
                accentColor = AppointmentPurple,
                containerColor = AppointmentPurpleContainer,
                textColor = AppointmentPurpleText,
                testTag = "action_appointments",
                onClick = onNavigateToAppointments
            )

            // 6. Menu & Services Manager (Indigo / Slate Accent) -> Opens Menu & Services in Settings
            ActionButtonCard(
                title = "Menu & Services",
                subtitle = "Manage local products, service items, prices & categories",
                icon = Icons.Default.Category,
                accentColor = Color(0xFF4F46E5),
                containerColor = Color(0xFFEEF2FF),
                textColor = Color(0xFF312E81),
                testTag = "action_menu_services",
                onClick = onNavigateToMenuManagement
            )

            // 7. Special Offers & Promo Broadcasts (Teal / Emerald Accent) -> Opens SpecialOffersScreen
            ActionButtonCard(
                title = "Special Offers & Promos",
                subtitle = "Send text, flyers, reels & animations to phone contacts & salon clients",
                icon = Icons.Default.Campaign,
                accentColor = Color(0xFF0D9488), // Teal
                containerColor = Color(0xFFCCFBF1), // Teal Container
                textColor = Color(0xFF115E59), // Teal Text
                testTag = "action_special_offers",
                onClick = onNavigateToSpecialOffers
            )

            // 2. TODAY'S NET BALANCE SECTION (BELOW QUICK ACTIONS)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Net Balance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (todayNet >= 0) IncomeGreenContainer else ExpenseRedContainer,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = if (todayNet >= 0) "SURPLUS" else "DEFICIT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (todayNet >= 0) IncomeGreenText else ExpenseRedText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", todayNet)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (todayNet >= 0) IncomeGreen else ExpenseRed
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3 columns: Income, Expense, Bills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TodayMetricItem(
                            title = "Income",
                            amount = todayIncome,
                            color = IncomeGreen
                        )
                        TodayMetricItem(
                            title = "Expenses",
                            amount = todayExpense,
                            color = ExpenseRed
                        )
                        TodayMetricItem(
                            title = "Bills",
                            amount = todayBills,
                            color = BillOrange
                        )
                    }
                }
            }

            // TODAY'S APPOINTMENTS PREVIEW
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AppointmentPurple.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AppointmentPurpleContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = AppointmentPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Today's Appointments",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (todayAppointments.isEmpty()) "No bookings scheduled today" else "${todayAppointments.size} booking(s) scheduled",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToAppointments,
                            modifier = Modifier.testTag("dashboard_view_all_appointments_button")
                        ) {
                            Text(
                                text = "View All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppointmentPurple
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = AppointmentPurple,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (todayAppointments.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        todayAppointments.take(3).forEach { appt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .clickable { onNavigateToAppointments() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appt.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${appt.appointmentTime} • ${appt.serviceName}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppointmentPurpleContainer
                                ) {
                                    Text(
                                        text = appt.status,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppointmentPurpleText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. LOCAL STORAGE STATUS (AT BOTTOM)
            LocalStorageStatusBanner(
                onManageClick = onNavigateToSettings
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ActionButtonCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    textColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.8f)
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Open",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun TodayMetricItem(
    title: String,
    amount: Double,
    color: Color
) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$${String.format(Locale.US, "%,.2f", amount)}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun LocalStorageStatusBanner(
    onManageClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onManageClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFDCFCE7)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Local Storage",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Local Storage Active",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = "Business, Menu, Sales & Appointments saved on device",
                        fontSize = 11.sp,
                        color = Color(0xFF15803D)
                    )
                }
            }
            Text(
                text = "Settings",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF15803D)
            )
        }
    }
}
