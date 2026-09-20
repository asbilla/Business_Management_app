package com.example.ui.balancesheet

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.TransactionRepository
import com.example.ui.theme.BalanceBlue
import com.example.util.PdfExportHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementSelectionScreen(
    repository: TransactionRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localTransactions by repository.allTransactions.collectAsState(initial = emptyList())
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var showCustomRangePicker by remember { mutableStateOf(false) }

    val dateRangePickerState = rememberDateRangePickerState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Statements", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("Monthly Statements")
            }

            // Monthly Options: Current and Last 2 months
            val months = getRecentMonths(3)
            items(months) { month ->
                StatementOptionCard(
                    title = month.displayName,
                    subtitle = "Full monthly report",
                    icon = Icons.Default.CalendarMonth,
                    isGenerating = isGeneratingPdf,
                    onClick = {
                        generateStatement(
                            context, scope, repository, localTransactions,
                            month.startDate, month.endDate,
                            periodTitle = month.displayName,
                            onProgress = { isGeneratingPdf = it }
                        )
                    }
                )
            }

            item {
                SectionHeader("Quarterly Statements (FY starts Jun 1st)")
            }

            // Quarterly Options
            val quarters = getRecentQuarters(2)
            items(quarters) { quarter ->
                StatementOptionCard(
                    title = quarter.displayName,
                    subtitle = "${quarter.dateRangeDisplay}",
                    icon = Icons.Default.DateRange,
                    isGenerating = isGeneratingPdf,
                    onClick = {
                        generateStatement(
                            context, scope, repository, localTransactions,
                            quarter.startDate, quarter.endDate,
                            periodTitle = quarter.displayName,
                            onProgress = { isGeneratingPdf = it }
                        )
                    }
                )
            }

            item {
                SectionHeader("Annual Statements")
            }

            // Annual Options
            val years = getRecentFinancialYears(2)
            items(years) { year ->
                StatementOptionCard(
                    title = year.displayName,
                    subtitle = "${year.dateRangeDisplay}",
                    icon = Icons.Default.CalendarToday,
                    isGenerating = isGeneratingPdf,
                    onClick = {
                        generateStatement(
                            context, scope, repository, localTransactions,
                            year.startDate, year.endDate,
                            periodTitle = year.displayName,
                            onProgress = { isGeneratingPdf = it }
                        )
                    }
                )
            }

            item {
                SectionHeader("Custom Selection")
                StatementOptionCard(
                    title = "Manual Date Range",
                    subtitle = "Select start and end dates",
                    icon = Icons.Default.DateRange,
                    isGenerating = isGeneratingPdf,
                    onClick = { showCustomRangePicker = true }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showCustomRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showCustomRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        showCustomRangePicker = false
                        val dfCustom = SimpleDateFormat("dd MMM yyyy", Locale.US)
                        val customTitle = "${dfCustom.format(Date(start))} - ${dfCustom.format(Date(end))}"
                        generateStatement(
                            context, scope, repository, localTransactions,
                            Date(start), Date(end),
                            periodTitle = customTitle,
                            onProgress = { isGeneratingPdf = it }
                        )
                    } else {
                        Toast.makeText(context, "Please select a range", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Statement Period", modifier = Modifier.padding(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
    )
}

@Composable
fun StatementOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGenerating: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = if (isGenerating) ({}) else onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = BalanceBlue.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = BalanceBlue)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun generateStatement(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    repository: TransactionRepository,
    allTransactions: List<com.example.data.local.TransactionEntity>,
    startDate: Date,
    endDate: Date,
    periodTitle: String = "",
    onProgress: (Boolean) -> Unit
) {
    onProgress(true)
    scope.launch {
        val dfIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Filter transactions within range
        val filtered = allTransactions.filter {
            val txDate = dfIso.parse(it.date) ?: Date(0)
            !txDate.before(startDate) && !txDate.after(endDate)
        }

        if (filtered.isEmpty()) {
            Toast.makeText(context, "No transactions found for this period", Toast.LENGTH_LONG).show()
            onProgress(false)
            return@launch
        }

        // Group into summaries for PDF helper
        val summariesByDate = filtered.groupBy { it.date }.map { (date, txs) ->
            val totalInc = txs.filter { it.type == "Daily Income" }.sumOf { it.amount }
            val totalExp = txs.filter { it.type == "Expense" }.sumOf { it.amount }
            val totalBill = txs.filter { it.type == "Bill" }.sumOf { it.amount }
            
            val displayTxs = txs.map { 
                com.example.ui.balancesheet.DisplayTransaction(
                    id = it.uuid,
                    date = it.date,
                    type = it.type,
                    category = it.category,
                    amount = it.amount,
                    isSynced = it.isSynced
                )
            }.sortedByDescending { it.id } // Approximating order

            com.example.ui.balancesheet.DateBalanceSummary(
                date = date,
                totalIncome = totalInc,
                totalExpenses = totalExp,
                totalBills = totalBill,
                netBalance = totalInc - (totalExp + totalBill),
                cumulativeBalance = 0.0,
                previousCarriedBalance = 0.0,
                transactions = displayTxs
            )
        }.sortedBy { it.date }

        // Calculate cumulative totals
        var currentCumulative = 0.0
        var overallInc = 0.0
        var overallExp = 0.0
        var overallBill = 0.0
        
        val finalSummaries = summariesByDate.map { summary ->
            currentCumulative += summary.netBalance
            overallInc += summary.totalIncome
            overallExp += summary.totalExpenses
            overallBill += summary.totalBills
            summary.copy(cumulativeBalance = currentCumulative)
        }

        val file = PdfExportHelper.exportLocalPdf(
            context = context,
            summaries = finalSummaries,
            overallIncome = overallInc,
            overallExpenses = overallExp,
            overallBills = overallBill,
            overallNet = currentCumulative,
            profile = repository.getBusinessProfile(),
            periodTitle = periodTitle
        )
        
        onProgress(false)
        if (file != null) {
            PdfExportHelper.openPdfFile(context, file)
        } else {
            Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
        }
    }
}

// Helper models for period generation
data class MonthPeriod(val displayName: String, val startDate: Date, val endDate: Date)
data class QuarterPeriod(val displayName: String, val dateRangeDisplay: String, val startDate: Date, val endDate: Date)
data class YearPeriod(val displayName: String, val dateRangeDisplay: String, val startDate: Date, val endDate: Date)

private fun getRecentMonths(count: Int): List<MonthPeriod> {
    val list = mutableListOf<MonthPeriod>()
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    
    val df = SimpleDateFormat("MMMM yyyy", Locale.US)
    
    for (i in 0 until count) {
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.time
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val end = cal.time
        
        list.add(MonthPeriod(df.format(start), start, end))
        cal.add(Calendar.MONTH, -1)
    }
    return list
}

private fun getRecentQuarters(count: Int): List<QuarterPeriod> {
    val list = mutableListOf<QuarterPeriod>()
    val today = Calendar.getInstance()
    
    // Financial Year Start: June 1st.
    val qStartMonths = intArrayOf(Calendar.JUNE, Calendar.SEPTEMBER, Calendar.DECEMBER, Calendar.MARCH)
    val qNames = arrayOf("Q1 (Jun - Aug)", "Q2 (Sep - Nov)", "Q3 (Dec - Feb)", "Q4 (Mar - May)")
    
    var checkCal = Calendar.getInstance()
    checkCal.set(Calendar.DAY_OF_MONTH, 1)
    
    var quartersGenerated = 0
    while (quartersGenerated < count) {
        val month = checkCal.get(Calendar.MONTH)
        
        // Find which quarter the current checkCal month belongs to
        val qIndex = when (month) {
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> 0
            Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> 1
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> 2
            else -> 3
        }
        
        val targetMonth = qStartMonths[qIndex]
        var targetYear = checkCal.get(Calendar.YEAR)
        if (month < targetMonth && targetMonth != Calendar.MARCH) targetYear--
        if (month >= Calendar.JANUARY && month < Calendar.MARCH && targetMonth > Calendar.FEBRUARY) targetYear--

        val startCal = Calendar.getInstance()
        startCal.set(targetYear, targetMonth, 1, 0, 0, 0)
        val start = startCal.time
        
        val endCal = Calendar.getInstance()
        endCal.time = start
        endCal.add(Calendar.MONTH, 2)
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        val end = endCal.time
        
        val df = SimpleDateFormat("MMM yyyy", Locale.US)
        val startYr = if (targetMonth >= Calendar.JUNE) targetYear else targetYear - 1
        val endYrShort = ((startYr + 1) % 100).toString().padStart(2, '0')
        val fyDisplay = "$startYr-$endYrShort"
        
        list.add(QuarterPeriod(
            displayName = "${qNames[qIndex]} FY $fyDisplay",
            dateRangeDisplay = "${df.format(start)} - ${df.format(end)}",
            startDate = start,
            endDate = end
        ))
        
        quartersGenerated++
        checkCal.time = start
        checkCal.add(Calendar.MONTH, -1) // Move to previous quarter
    }
    
    return list
}

private fun getRecentFinancialYears(count: Int): List<YearPeriod> {
    val list = mutableListOf<YearPeriod>()
    val today = Calendar.getInstance()
    
    var currentStartYear = today.get(Calendar.YEAR)
    if (today.get(Calendar.MONTH) < Calendar.JUNE) {
        currentStartYear--
    }
    
    for (i in 0 until count) {
        val start = Calendar.getInstance()
        start.set(currentStartYear - i, Calendar.JUNE, 1, 0, 0, 0)
        
        val end = Calendar.getInstance()
        end.set(currentStartYear - i + 1, Calendar.MAY, 31, 23, 59, 59)
        
        val df = SimpleDateFormat("MMM yyyy", Locale.US)
        val sYear = currentStartYear - i
        val eYearShort = ((sYear + 1) % 100).toString().padStart(2, '0')
        list.add(YearPeriod(
            displayName = "FY $sYear-$eYearShort",
            dateRangeDisplay = "${df.format(start.time)} - ${df.format(end.time)}",
            startDate = start.time,
            endDate = end.time
        ))
    }
    return list
}
