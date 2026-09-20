package com.example.ui.balancesheet

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TransactionEntity
import com.example.data.remote.RemoteTransaction
import com.example.data.repository.TransactionRepository
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
import com.example.util.PdfExportHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DateBalanceSummary(
    val date: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalBills: Double,
    val netBalance: Double,
    val cumulativeBalance: Double,
    val previousCarriedBalance: Double,
    val transactions: List<DisplayTransaction>
)

data class DisplayTransaction(
    val id: String,
    val date: String,
    val type: String,
    val category: String,
    val amount: Double,
    val isSynced: Boolean
)

data class EditTransactionState(
    val id: String,
    val date: String,
    val type: String,
    val category: String,
    val initialAmount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheetScreen(
    repository: TransactionRepository,
    onNavigateBack: () -> Unit,
    onNavigateToStatementSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val localTransactions by repository.allTransactions.collectAsStateWithLifecycle(initialValue = emptyList())
    var isDownloadingPdf by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<EditTransactionState?>(null) }
    var isSavingEdit by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<DisplayTransaction?>(null) }
    var isDeletingTx by remember { mutableStateOf(false) }
    var selectedInvoiceTx by remember { mutableStateOf<DisplayTransaction?>(null) }
    var isGeneratingSingleInvoice by remember { mutableStateOf(false) }

    // Group local transactions by Date with continuous cumulative balance carry-over
    val dateSummaries by remember(localTransactions) {
        derivedStateOf {
            val combined = mutableListOf<DisplayTransaction>()

            // Add all local transactions
            for (local in localTransactions) {
                combined.add(
                    DisplayTransaction(
                        id = local.uuid,
                        date = normalizeDateIso(local.date),
                        type = local.type,
                        category = local.category,
                        amount = local.amount,
                        isSynced = true
                    )
                )
            }

            // Group by Date
            val grouped = combined.groupBy { it.date }

            // Sort chronologically ascending to calculate continuous balance carry-over across months & dates
            val sortedAscDates = grouped.entries.sortedBy { it.key }
            var runningBalance = 0.0
            val summariesAsc = mutableListOf<DateBalanceSummary>()

            for ((date, txList) in sortedAscDates) {
                val income = txList.filter { it.type == "Daily Income" || it.type == "Opening Balance" }.sumOf { it.amount }
                val expenses = txList.filter { it.type == "Expense" }.sumOf { it.amount }
                val bills = txList.filter { it.type == "Bill" }.sumOf { it.amount }
                val dayNet = income - (expenses + bills)

                val prevCarried = runningBalance
                runningBalance += dayNet

                summariesAsc.add(
                    DateBalanceSummary(
                        date = date,
                        totalIncome = income,
                        totalExpenses = expenses,
                        totalBills = bills,
                        netBalance = dayNet,
                        cumulativeBalance = runningBalance,
                        previousCarriedBalance = prevCarried,
                        transactions = txList.sortedByDescending { it.amount }
                    )
                )
            }

            // Return sorted descending (newest date first) for user-friendly browsing
            summariesAsc.sortedByDescending { it.date }
        }
    }

    // Overall aggregate totals across all dates
    val overallIncome = dateSummaries.sumOf { it.totalIncome }
    val overallExpenses = dateSummaries.sumOf { it.totalExpenses }
    val overallBills = dateSummaries.sumOf { it.totalBills }
    val overallNet = overallIncome - (overallExpenses + overallBills)

    // Handle PDF export of the balance sheet directly to local device storage
    fun handleDownloadPdf() {
        if (dateSummaries.isEmpty()) {
            Toast.makeText(context, "No balance sheet entries to export", Toast.LENGTH_SHORT).show()
            return
        }
        isDownloadingPdf = true
        scope.launch {
            try {
                val profile = repository.getBusinessProfile()
                val file = PdfExportHelper.exportLocalPdf(
                    context = context,
                    summaries = dateSummaries,
                    overallIncome = overallIncome,
                    overallExpenses = overallExpenses,
                    overallBills = overallBills,
                    overallNet = overallNet,
                    profile = profile
                )

                if (file != null) {
                    Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_SHORT).show()
                    PdfExportHelper.openPdfFile(context, file)
                } else {
                    Toast.makeText(context, "PDF export ready!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isDownloadingPdf = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Day-by-Day Balance Sheet",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("balancesheet_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { handleDownloadPdf() },
                        enabled = !isDownloadingPdf && dateSummaries.isNotEmpty(),
                        modifier = Modifier.testTag("balancesheet_download_pdf_button")
                    ) {
                        if (isDownloadingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Download PDF",
                                tint = BalanceBlue
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (dateSummaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(BalanceBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = BalanceBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No Transactions Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Log Daily Income, Expenses, or Bills from the Dashboard to see your balance breakdown here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Aggregate Summary Card
                item {
                    OverallSummaryCard(
                        income = overallIncome,
                        expenses = overallExpenses,
                        bills = overallBills,
                        net = overallNet
                    )
                }

                item {
                    Button(
                        onClick = { onNavigateToStatementSelection() },
                        enabled = dateSummaries.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_pdf_main_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BalanceBlue),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Statements",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Breakdowns",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${dateSummaries.size} Days Recorded",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(dateSummaries, key = { it.date }) { summary ->
                    DateBalanceCard(
                        summary = summary,
                        onEditTransaction = { targetState ->
                            editingTransaction = targetState
                        },
                        onDeleteTransaction = { tx ->
                            transactionToDelete = tx
                        },
                        onSelectTransaction = { tx ->
                            selectedInvoiceTx = tx
                        }
                    )
                }
            }
        }
    }

    // Transaction Invoice Dialog
    if (selectedInvoiceTx != null) {
        val tx = selectedInvoiceTx!!
        val profile = repository.getBusinessProfile()
        AlertDialog(
            onDismissRequest = { selectedInvoiceTx = null },
            title = { Text("Tax Invoice", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Business:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(profile.businessName.ifBlank { "My Business" }, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Date:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(tx.date, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Type:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(tx.type, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Category:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(tx.category.ifBlank { "-" }, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Amount:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("$${String.format(Locale.US, "%,.2f", tx.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BalanceBlue)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isGeneratingSingleInvoice = true
                        scope.launch {
                            val file = PdfExportHelper.exportSingleInvoice(context, tx, profile)
                            isGeneratingSingleInvoice = false
                            if (file != null) {
                                PdfExportHelper.shareFile(context, file)
                            }
                        }
                    },
                    enabled = !isGeneratingSingleInvoice
                ) {
                    if (isGeneratingSingleInvoice) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Share Invoice")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedInvoiceTx = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal Confirmation Dialog for Deleting a Transaction
    if (transactionToDelete != null) {
        val tx = transactionToDelete!!
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingTx) {
                    transactionToDelete = null
                }
            },
            title = {
                Text(
                    text = "Delete Transaction?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this ${tx.type} transaction of $${String.format(Locale.US, "%,.2f", tx.amount)} on ${tx.date}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeletingTx = true
                        scope.launch {
                            val result = repository.deleteTransactionPermanently(
                                id = tx.id,
                                date = tx.date,
                                type = tx.type
                            )
                            isDeletingTx = false
                            transactionToDelete = null
                            if (result.isSuccess) {
                                Toast.makeText(
                                    context,
                                    result.getOrDefault("Transaction deleted"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Error deleting: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isDeletingTx
                ) {
                    if (isDeletingTx) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { transactionToDelete = null },
                    enabled = !isDeletingTx
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Edit Amount Dialog for Income, Expense, or Bill
    if (editingTransaction != null) {
        val state = editingTransaction!!
        EditAmountDialog(
            state = state,
            isSaving = isSavingEdit,
            onDismiss = {
                if (!isSavingEdit) {
                    editingTransaction = null
                }
            },
            onConfirm = { newAmount, newNotes ->
                isSavingEdit = true
                scope.launch {
                    val result = repository.updateTransactionAmount(
                        id = state.id,
                        date = state.date,
                        type = state.type,
                        category = newNotes,
                        newAmount = newAmount
                    )
                    isSavingEdit = false
                    editingTransaction = null
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            result.getOrDefault("Amount updated"),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Error updating: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
fun OverallSummaryCard(
    income: Double,
    expenses: Double,
    bills: Double,
    net: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BalanceBlueContainer.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cumulative Net Balance",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BalanceBlueText
                )
                Surface(
                    shape = CircleShape,
                    color = if (net >= 0) IncomeGreenContainer else ExpenseRedContainer
                ) {
                    Text(
                        text = if (net >= 0) "SURPLUS" else "DEFICIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (net >= 0) IncomeGreenText else ExpenseRedText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${String.format(Locale.US, "%,.2f", net)}",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (net >= 0) IncomeGreen else ExpenseRed
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BalanceBlue.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "+$${String.format(Locale.US, "%,.2f", income)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen
                    )
                }
                Column {
                    Text("Total Expenses", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "-$${String.format(Locale.US, "%,.2f", expenses)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed
                    )
                }
                Column {
                    Text("Total Bills", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "-$${String.format(Locale.US, "%,.2f", bills)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BillOrange
                    )
                }
            }
        }
    }
}

@Composable
fun DateBalanceCard(
    summary: DateBalanceSummary,
    onEditTransaction: (EditTransactionState) -> Unit,
    onDeleteTransaction: (DisplayTransaction) -> Unit,
    onSelectTransaction: (DisplayTransaction) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrow_rot")

    val formattedDateHeader = remember(summary.date) {
        try {
            val parts = summary.date.trim().split("-")
            val dateObj = if (parts.size == 3) {
                val cal = java.util.Calendar.getInstance()
                if (parts[0].length == 4) {
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                } else {
                    cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                }
                cal.time
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(summary.date)
            }
            if (dateObj != null) {
                SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(dateObj)
            } else {
                summary.date
            }
        } catch (_: Exception) {
            summary.date
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("date_card_${summary.date}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date Title and Expand Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formattedDateHeader,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Net Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (summary.netBalance >= 0) IncomeGreenContainer else ExpenseRedContainer
                    ) {
                        Text(
                            text = (if (summary.netBalance >= 0) "+$" else "-$") +
                                    String.format(Locale.US, "%,.2f", kotlin.math.abs(summary.netBalance)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.netBalance >= 0) IncomeGreenText else ExpenseRedText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationState)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calculated 3 Categories: Income, Expenses, Bills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CategoryBreakdownPill(
                    label = "Income",
                    amount = summary.totalIncome,
                    color = IncomeGreen,
                    containerColor = IncomeGreenContainer
                )
                CategoryBreakdownPill(
                    label = "Expenses",
                    amount = summary.totalExpenses,
                    color = ExpenseRed,
                    containerColor = ExpenseRedContainer
                )
                CategoryBreakdownPill(
                    label = "Bills",
                    amount = summary.totalBills,
                    color = BillOrange,
                    containerColor = BillOrangeContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Continuous Balance Carry-Over Section
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BalanceBlueContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Balance (Carry-over)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BalanceBlueText
                        )
                        if (summary.previousCarriedBalance != 0.0) {
                            Text(
                                text = "From previous: $${String.format(Locale.US, "%,.2f", summary.previousCarriedBalance)} | Day: ${(if (summary.netBalance >= 0) "+$" else "-$") + String.format(Locale.US, "%,.2f", kotlin.math.abs(summary.netBalance))}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Starting period balance",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", summary.cumulativeBalance)}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (summary.cumulativeBalance >= 0) IncomeGreen else ExpenseRed
                    )
                }
            }

            // Expandable List of Individual Transactions
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Transactions on this day (${summary.transactions.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    summary.transactions.forEach { tx ->
                        TransactionItemRow(
                            tx = tx,
                            onClick = { onSelectTransaction(tx) },
                            onEditClick = {
                                onEditTransaction(
                                    EditTransactionState(
                                        id = tx.id,
                                        date = tx.date,
                                        type = tx.type,
                                        category = tx.category,
                                        initialAmount = tx.amount
                                    )
                                )
                            },
                            onDeleteClick = {
                                onDeleteTransaction(tx)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdownPill(
    label: String,
    amount: Double,
    color: Color,
    containerColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier
            .width(104.dp)
            .testTag("category_pill_${label.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$${String.format(Locale.US, "%,.2f", amount)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun TransactionItemRow(
    tx: DisplayTransaction,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val (badgeColor, badgeBg, icon) = when (tx.type) {
        "Daily Income" -> Triple(IncomeGreen, IncomeGreenContainer, Icons.Default.TrendingUp)
        "Expense" -> Triple(ExpenseRed, ExpenseRedContainer, Icons.Default.MoneyOff)
        else -> Triple(BillOrange, BillOrangeContainer, Icons.Default.ReceiptLong)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = tx.category.ifBlank { tx.type },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = tx.type,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (tx.type == "Daily Income") "+$" else "-$") +
                        String.format(Locale.US, "%,.2f", tx.amount),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Dedicated Edit Button
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("edit_tx_btn_${tx.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit ${tx.type} amount",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Dedicated Delete (X) Button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("delete_tx_btn_${tx.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete ${tx.type} transaction",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun EditAmountDialog(
    state: EditTransactionState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amountInput by remember(state) {
        mutableStateOf(if (state.initialAmount > 0.0) String.format(Locale.US, "%.2f", state.initialAmount) else "")
    }
    var notesInput by remember(state) {
        mutableStateOf(state.category)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val (badgeColor, badgeBg, icon) = when (state.type) {
        "Daily Income" -> Triple(IncomeGreen, IncomeGreenContainer, Icons.Default.TrendingUp)
        "Expense" -> Triple(ExpenseRed, ExpenseRedContainer, Icons.Default.MoneyOff)
        else -> Triple(BillOrange, BillOrangeContainer, Icons.Default.ReceiptLong)
    }

    val formattedDate = remember(state.date) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateObj = parser.parse(state.date)
            if (dateObj != null) {
                SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(dateObj)
            } else {
                state.date
            }
        } catch (_: Exception) {
            state.date
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Edit ${state.type}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Recorded:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", state.initialAmount)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Column {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it
                            errorMessage = null
                        },
                        label = { Text("New Amount ($)") },
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = "$",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = badgeColor
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = errorMessage != null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_amount_input_field")
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Notes / Category") },
                    placeholder = { Text("e.g. Sales, Rent, Supplies") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_notes_input_field")
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BalanceBlueContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = BalanceBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Updates your local database.",
                            fontSize = 11.sp,
                            color = BalanceBlueText,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountInput.trim().toDoubleOrNull()
                    if (parsed == null || parsed < 0.0) {
                        errorMessage = "Please enter a valid amount (e.g. 150.00)"
                        return@Button
                    }
                    onConfirm(parsed, notesInput.trim())
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = BalanceBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_save_edit_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Updating Sheets...", fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun normalizeDateIso(raw: String): String {
    val trimmed = raw.trim()
    val parts = trimmed.split("-")
    if (parts.size == 3) {
        return if (parts[0].length == 4) {
            trimmed
        } else {
            "${parts[2]}-${parts[1]}-${parts[0]}"
        }
    }
    return trimmed
}
