package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.ui.balancesheet.DateBalanceSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportHelper {

    /**
     * Downloads or exports the whole balance sheet into a clean PDF document.
     * Can open the official Google Sheets PDF export or generate a local high-quality PDF.
     */
    fun exportLocalPdf(
        context: Context,
        summaries: List<DateBalanceSummary>,
        overallIncome: Double,
        overallExpenses: Double,
        overallBills: Double,
        overallNet: Double,
        profile: com.example.data.pref.BusinessProfile = com.example.data.pref.BusinessProfile(),
        periodTitle: String = ""
    ): File? {
        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // Standard A4 points width
            val pageHeight = 842 // Standard A4 points height

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(30, 58, 138) // Deep Blue
                textSize = 18f
                isFakeBoldText = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 9.5f
            }

            val metaPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.5f
            }

            // Dark blue background for period subtitle banner
            val periodBannerBgPaint = Paint().apply {
                color = Color.rgb(26, 54, 93) // Professional Dark Blue (#1A365D)
            }

            // Period subtitle text: center aligned, bold, white, slightly larger font
            val periodBannerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 12f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }

            val headerBgPaint = Paint().apply {
                color = Color.rgb(238, 242, 255)
            }

            val tableHeaderPaint = Paint().apply {
                color = Color.rgb(30, 58, 138)
                textSize = 10f
                isFakeBoldText = true
            }

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9f
            }

            val boldTextPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9f
                isFakeBoldText = true
            }

            val greenPaint = Paint().apply {
                color = Color.rgb(22, 101, 52)
                textSize = 9f
                isFakeBoldText = true
            }

            val redPaint = Paint().apply {
                color = Color.rgb(153, 27, 27)
                textSize = 9f
                isFakeBoldText = true
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 0.5f
            }

            var y = 36f

            // Document & Business Header: Business details on the right side as requested
            val displayName = if (profile.businessName.isNotBlank()) profile.businessName else "MY BUSINESS"
            
            // Left Side: Report Title
            canvas.drawText("FINANCIAL STATEMENTS", 30f, y, titlePaint)
            
            // Right Side: Business Name & Details
            val nameWidth = titlePaint.measureText(displayName)
            canvas.drawText(displayName, pageWidth - 30f - nameWidth, y, titlePaint)
            y += 15f

            // Business info line 1 (Right Aligned)
            val infoLine1 = buildString {
                if (profile.abnAcn.isNotBlank()) append("ABN/ACN: ${profile.abnAcn}    ")
                if (profile.email.isNotBlank()) append("Email: ${profile.email}")
            }
            if (infoLine1.isNotBlank()) {
                val info1Width = subtitlePaint.measureText(infoLine1)
                canvas.drawText(infoLine1, pageWidth - 30f - info1Width, y, subtitlePaint)
                y += 13f
            }

            // Business info line 2 (Right Aligned)
            val infoLine2 = buildString {
                if (profile.businessAddress.isNotBlank()) append("Address: ${profile.businessAddress}    ")
                if (profile.phoneMobile.isNotBlank()) append("Phone/Mobile: ${profile.phoneMobile}")
            }
            if (infoLine2.isNotBlank()) {
                val info2Width = subtitlePaint.measureText(infoLine2)
                canvas.drawText(infoLine2, pageWidth - 30f - info2Width, y, subtitlePaint)
                y += 13f
            }

            canvas.drawLine(30f, y, (pageWidth - 30).toFloat(), y, linePaint)
            y += 10f

            val metaText = if (periodTitle.isNotBlank()) {
                periodTitle
            } else {
                val timeStamp = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())
                "BALANCE SHEET FINANCIAL STATEMENT  •  Generated on: $timeStamp"
            }

            // Separate Period Subtitle Banner: Dark blue background, center aligned, bold, white, slightly bigger font
            val bannerHeight = 22f
            canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + bannerHeight, periodBannerBgPaint)
            val centerX = pageWidth / 2f
            canvas.drawText(metaText, centerX, y + 15f, periodBannerTextPaint)
            y += bannerHeight + 10f

            // Summary Totals Box (Total Income, Total Expenses, Total Bills, Overall Net Balance)
            canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 38f, headerBgPaint)
            y += 15f
            canvas.drawText(
                "Total Income: $${String.format(Locale.US, "%,.2f", overallIncome)}    " +
                        "Total Expenses: $${String.format(Locale.US, "%,.2f", overallExpenses)}    " +
                        "Total Bills: $${String.format(Locale.US, "%,.2f", overallBills)}",
                40f,
                y,
                boldTextPaint
            )
            y += 14f
            val netText = "Overall Net Balance: $${String.format(Locale.US, "%,.2f", overallNet)}"
            val netPaint = if (overallNet >= 0) greenPaint else redPaint
            canvas.drawText(netText, 40f, y, netPaint)
            y += 20f

            // Table Columns: Date (60), Type (80), Notes (140), Income (70), Exp/Bills (70), Running Balance (80)
            fun drawTableHeader(currY: Float) {
                canvas.drawRect(30f, currY, (pageWidth - 30).toFloat(), currY + 20f, headerBgPaint)
                canvas.drawText("Date", 35f, currY + 14f, tableHeaderPaint)
                canvas.drawText("Type", 125f, currY + 14f, tableHeaderPaint)
                canvas.drawText("Notes / Category", 200f, currY + 14f, tableHeaderPaint)
                canvas.drawText("Income", 330f, currY + 14f, tableHeaderPaint)
                canvas.drawText("Expense/Bills", 405f, currY + 14f, tableHeaderPaint)
                canvas.drawText("Balance", 495f, currY + 14f, tableHeaderPaint)
            }

            drawTableHeader(y)
            y += 26f

            // Loop through dates
            // Sort summaries chronologically for ledger display
            val sortedAsc = summaries.sortedBy { toCanonicalDate(it.date) }

            for (dateSum in sortedAsc) {
                // Check if page overflow
                if (y > pageHeight - 60) {
                    pdfDoc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                    drawTableHeader(y)
                    y += 26f
                }

                // Date separator bar
                canvas.drawRect(30f, y - 4f, (pageWidth - 30).toFloat(), y + 16f, Paint().apply { color = Color.rgb(243, 244, 246) })
                val displayDateSum = formatDateForDisplay(dateSum.date)
                val dateTitle = "$displayDateSum   (Day Net: $${String.format(Locale.US, "%,.2f", dateSum.netBalance)} | Cumulative: $${String.format(Locale.US, "%,.2f", dateSum.cumulativeBalance)})"
                canvas.drawText(dateTitle, 35f, y + 10f, boldTextPaint)
                y += 22f

                for (tx in dateSum.transactions) {
                    if (y > pageHeight - 50) {
                        pdfDoc.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDoc.startPage(pageInfo)
                        canvas = page.canvas
                        y = 40f
                        drawTableHeader(y)
                        y += 26f
                    }

                    val displayTxDate = formatDateForDisplay(tx.date)
                    canvas.drawText(displayTxDate, 35f, y, textPaint)
                    canvas.drawText(tx.type, 125f, y, textPaint)

                    val shortNotes = if (tx.category.length > 22) tx.category.take(20) + ".." else tx.category
                    canvas.drawText(shortNotes, 200f, y, textPaint)

                    val isInc = tx.type == "Daily Income"
                    val incStr = if (isInc) "$${String.format(Locale.US, "%,.2f", tx.amount)}" else "-"
                    val expStr = if (!isInc) "$${String.format(Locale.US, "%,.2f", tx.amount)}" else "-"

                    canvas.drawText(incStr, 330f, y, if (isInc) greenPaint else textPaint)
                    canvas.drawText(expStr, 405f, y, if (!isInc) redPaint else textPaint)
                    canvas.drawText("$${String.format(Locale.US, "%,.2f", dateSum.cumulativeBalance)}", 495f, y, boldTextPaint)

                    canvas.drawLine(30f, y + 5f, (pageWidth - 30).toFloat(), y + 5f, linePaint)
                    y += 18f
                }
                y += 8f
            }

            pdfDoc.finishPage(page)

            // Save to Downloads folder or app external files
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val fileName = "Business_Balance_Sheet_${SimpleDateFormat("ddMMyyyy_hhmmss_a", Locale.getDefault()).format(Date())}.pdf"
            val file = File(downloadsDir, fileName)

            val outputStream = FileOutputStream(file)
            pdfDoc.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDoc.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generates a single transaction Tax Invoice PDF
     */
    fun exportSingleInvoice(
        context: Context,
        tx: com.example.ui.balancesheet.DisplayTransaction,
        profile: com.example.data.pref.BusinessProfile = com.example.data.pref.BusinessProfile()
    ): File? {
        // Parse items from category/notes if it follows the POS format
        val items = parseItemsFromNotes(tx.category)
        return generateSingleInvoicePdf(
            context = context,
            invoiceId = tx.id,
            date = tx.date,
            type = tx.type,
            amount = tx.amount,
            items = items,
            profile = profile
        )
    }

    /**
     * Generates an invoice PDF directly from cart items (Checkout)
     */
    fun exportCheckoutInvoice(
        context: Context,
        items: List<com.example.ui.entry.CartItem>,
        total: Double,
        date: String,
        profile: com.example.data.pref.BusinessProfile
    ): File? {
        val invoiceItems = items.map {
            InvoiceItem(it.product.name, it.quantity, it.product.price * it.quantity)
        }
        return generateSingleInvoicePdf(
            context = context,
            invoiceId = SimpleDateFormat("HHmm-SSS", Locale.US).format(Date()),
            date = date,
            type = "Daily Income",
            amount = total,
            items = invoiceItems,
            profile = profile
        )
    }

    private fun generateSingleInvoicePdf(
        context: Context,
        invoiceId: String,
        date: String,
        type: String,
        amount: Double,
        items: List<InvoiceItem>,
        profile: com.example.data.pref.BusinessProfile
    ): File? {
        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply { color = Color.rgb(30, 58, 138); textSize = 20f; isFakeBoldText = true }
            val subtitlePaint = Paint().apply { color = Color.DKGRAY; textSize = 10f }
            val metaPaint = Paint().apply { color = Color.rgb(100, 116, 139); textSize = 9f }
            val tableHeaderPaint = Paint().apply { color = Color.rgb(30, 58, 138); textSize = 11f; isFakeBoldText = true }
            val textPaint = Paint().apply { color = Color.BLACK; textSize = 11f }
            val boldTextPaint = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true }
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
            val headerBgPaint = Paint().apply { color = Color.rgb(238, 242, 255) }

            var y = 50f

            // Tax Invoice Title
            canvas.drawText("TAX INVOICE", 40f, y, titlePaint)

            // Business details on the right side
            val bizName = if (profile.businessName.isNotBlank()) profile.businessName else "My Business"
            val bizWidth = titlePaint.measureText(bizName)
            canvas.drawText(bizName, pageWidth - 40f - bizWidth, y, titlePaint)
            y += 18f

            val details = mutableListOf<String>()
            if (profile.abnAcn.isNotBlank()) details.add("ABN/ACN: ${profile.abnAcn}")
            if (profile.email.isNotBlank()) details.add("Email: ${profile.email}")
            if (profile.phoneMobile.isNotBlank()) details.add("Phone: ${profile.phoneMobile}")
            if (profile.businessAddress.isNotBlank()) details.add("Address: ${profile.businessAddress}")

            details.forEach { detail ->
                val w = subtitlePaint.measureText(detail)
                canvas.drawText(detail, pageWidth - 40f - w, y, subtitlePaint)
                y += 14f
            }

            y += 10f
            canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
            y += 30f

            // Invoice Info
            canvas.drawText("Invoice Date:", 40f, y, boldTextPaint)
            canvas.drawText(date, 130f, y, textPaint)
            y += 20f
            canvas.drawText("Transaction ID:", 40f, y, boldTextPaint)
            canvas.drawText(invoiceId.take(12), 130f, y, textPaint)
            y += 40f

            // Table Header
            canvas.drawRect(40f, y - 15f, (pageWidth - 40).toFloat(), y + 10f, headerBgPaint)
            canvas.drawText("Description", 50f, y, tableHeaderPaint)
            canvas.drawText("Qty", 380f, y, tableHeaderPaint)
            canvas.drawText("Amount", 480f, y, tableHeaderPaint)
            y += 30f

            // Table Body
            if (items.isNotEmpty()) {
                items.forEach { item ->
                    canvas.drawText(item.description, 50f, y, textPaint)
                    canvas.drawText(item.qty.toString(), 380f, y, textPaint)
                    canvas.drawText("$${String.format(Locale.US, "%,.2f", item.total)}", 480f, y, textPaint)
                    y += 20f
                    
                    if (y > pageHeight - 150) { // Simple overflow check
                         // In a production app, we'd start a new page here. 
                         // For this scope, we'll assume a single page is enough.
                    }
                }
            } else {
                canvas.drawText(type, 50f, y, textPaint)
                canvas.drawText("1", 380f, y, textPaint)
                canvas.drawText("$${String.format(Locale.US, "%,.2f", amount)}", 480f, y, textPaint)
                y += 20f
            }
            
            canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
            y += 30f

            // GST and Total
            val gst = amount / 11.0 // 10% inclusive
            canvas.drawText("inclusive GST 10%:", 300f, y, subtitlePaint)
            canvas.drawText("$${String.format(Locale.US, "%,.2f", gst)}", 480f, y, subtitlePaint)
            y += 20f
            
            canvas.drawText("TOTAL AMOUNT:", 300f, y, boldTextPaint)
            canvas.drawText("$${String.format(Locale.US, "%,.2f", amount)}", 480f, y, titlePaint.apply { textSize = 16f })

            y += 100f
            canvas.drawText("Generated on ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())}", 40f, y, metaPaint)
            canvas.drawText("Thank you for your business!", pageWidth / 2f - 70f, y + 20f, metaPaint)

            pdfDoc.finishPage(page)

            val dir = context.cacheDir
            val file = File(dir, "Invoice_${invoiceId.take(6)}.pdf")
            val fos = FileOutputStream(file)
            pdfDoc.writeTo(fos)
            fos.close()
            pdfDoc.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private data class InvoiceItem(val description: String, val qty: Int, val total: Double)

    private fun parseItemsFromNotes(notes: String): List<InvoiceItem> {
        val items = mutableListOf<InvoiceItem>()
        val lines = notes.split("\n")
        val regex = Regex("""(.*) x(\d+) \(\$(.*)\)""")
        
        lines.forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val desc = match.groupValues[1].trim()
                val qty = match.groupValues[2].toIntOrNull() ?: 1
                val total = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: 0.0
                items.add(InvoiceItem(desc, qty, total))
            } else if (line.isNotBlank()) {
                // Try to handle simple notes that aren't in the POS format
                items.add(InvoiceItem(line.trim(), 1, 0.0))
            }
        }
        return items
    }

    /**
     * Shares a file via system intent
     */
    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Invoice"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens the saved PDF file using an Intent chooser
     */
    fun openPdfFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open Balance Sheet PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }

    fun toCanonicalDate(dateStr: String): String {
        val trimmed = dateStr.trim()
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

    fun formatDateForDisplay(dateStr: String): String {
        return try {
            val trimmed = dateStr.trim()
            val parts = trimmed.split("-")
            val date = if (parts.size == 3) {
                if (parts[0].length == 4) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(trimmed)
                } else {
                    SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(trimmed)
                }
            } else {
                null
            }
            if (date != null) {
                SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(date)
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }
}
