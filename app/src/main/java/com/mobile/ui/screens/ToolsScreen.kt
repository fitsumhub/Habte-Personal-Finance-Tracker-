package com.mobile.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.data.Data
import com.mobile.data.FinanceRepository
import com.mobile.data.PaymentReminder
import com.mobile.data.PaymentReminderRepository
import com.mobile.data.SettingsRepository
import com.mobile.data.Transaction
import com.mobile.data.findPotentialDuplicates
import com.mobile.ui.theme.LocalEthiopianColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Tool(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val sub: String,
    val badge: String? = null,
    val color: Color
)

private data class ToolSection(
    val title: String,
    val subtitle: String,
    val tools: List<Tool>
)

private val TOOL_SECTIONS = listOf(
    ToolSection(
        title = "Financial Calculators",
        subtitle = "Precision computation for tax, loans, and foreign exchange",
        tools = listOf(
            Tool("tax_calc", Icons.Default.Calculate, "Income Tax (ERCA)", "Net salary & 7% pension deduction", "Ethiopian", Color(0xFF00C853)),
            Tool("loan_calc", Icons.Default.AccountBalance, "Loan & Mortgage", "Monthly EMI & interest schedule", null, Color(0xFF0EA5E9)),
            Tool("currency_fx", Icons.Default.CurrencyExchange, "Forex Converter", "ETB rates for USD, EUR, GBP, SAR", "Live Presets", Color(0xFFF59E0B)),
            Tool("compound_wealth", Icons.AutoMirrored.Filled.TrendingUp, "Wealth Forecaster", "Compound growth & future value", "Pro", Color(0xFF8B5CF6)),
            Tool("emergency_fund", Icons.Default.Shield, "Emergency Cushion", "Safety net required for living cost", null, Color(0xFFEC4899))
        )
    ),
    ToolSection(
        title = "Statements & Data Vault",
        subtitle = "Export, backup, and transaction audit tools",
        tools = listOf(
            Tool("pdf_statement", Icons.Default.PictureAsPdf, "PDF Statement", "Branded monthly financial statement", "Official", Color(0xFF00C853)),
            Tool("csv_export", Icons.Default.FileDownload, "CSV Export", "Raw spreadsheet data download", null, Color(0xFF0EA5E9)),
            Tool("json_backup", Icons.Default.CloudUpload, "JSON Backup", "Encrypted snapshot export", null, Color(0xFF8B5CF6)),
            Tool("json_import", Icons.Default.CloudDownload, "Restore Backup", "Import saved transaction archive", null, Color(0xFF10B981)),
            Tool("duplicate_audit", Icons.Default.ContentCopy, "Duplicate Audit", "Identify double SMS alerts & charges", "Audit", Color(0xFFF43F5E))
        )
    ),
    ToolSection(
        title = "Planning & Obligations",
        subtitle = "Manage bills, debt, and traditional savings",
        tools = listOf(
            Tool("payment_reminders", Icons.Default.NotificationsActive, "Bill Reminders", "Schedule recurring due dates", null, Color(0xFFEF4444)),
            Tool("budget_planner", Icons.Default.Savings, "Budget Targets", "Monthly spending limits by category", null, Color(0xFFF59E0B)),
            Tool("net_worth", Icons.Default.AccountBalanceWallet, "Net Worth", "Assets & liabilities breakdown", null, Color(0xFF7C3AED))
        )
    ),
    ToolSection(
        title = "Honors & Widgets",
        subtitle = "Bespoke milestone diplomas and Android spending widgets",
        tools = listOf(
            Tool("achievement_certs", Icons.Default.WorkspacePremium, "Milestone Honors", "Humorous achievement certificates", "Honors", Color(0xFFF59E0B)),
            Tool("spending_widget", Icons.Default.Widgets, "Spending Digest Widget", "Homescreen & lockscreen widget settings", "Widget", Color(0xFF10B981))
        )
    )
)

private fun sanitizeNumeric(raw: String, allowDecimal: Boolean): String {
    val sb = StringBuilder()
    var dotSeen = false
    for (c in raw) {
        when {
            c.isDigit() -> sb.append(c)
            c == '.' && allowDecimal && !dotSeen -> { sb.append(c); dotSeen = true }
        }
    }
    return sb.toString()
}

private fun Transaction.statementTimestampMillis(): Long =
    runCatching {
        SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).parse("$date $time")?.time
    }.getOrNull() ?: 0L

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

private fun buildStatementCsv(transactions: List<Transaction>, accountHolder: String): String {
    if (transactions.isEmpty()) return "Habte Financial Tracker\nNo transactions.\n"
    val chronological = transactions.sortedBy { it.statementTimestampMillis() }
    val generatedAt = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())
    val totalCredit = transactions.filter { it.type == "credit" }.sumOf { it.amount }
    val totalDebit = transactions.filter { it.type == "debit" }.sumOf { it.amount }

    return buildString {
        appendLine("Habte Financial Tracker - Account Statement")
        appendLine("Account Holder,${csvField(accountHolder)}")
        appendLine("Statement Period,${csvField("${chronological.first().date} - ${chronological.last().date}")}")
        appendLine("Generated,$generatedAt")
        appendLine("Currency,ETB")
        appendLine("Transactions Covered,${transactions.size}")
        appendLine("Total Inflow (ETB),${Data.formatBalance(totalCredit)}")
        appendLine("Total Outflow (ETB),${Data.formatBalance(totalDebit)}")
        appendLine("Net Balance (ETB),${Data.formatBalance(totalCredit - totalDebit)}")
        appendLine()
        appendLine("Date,Time,Description,Amount (ETB),Type,Category,Bank,Balance (ETB)")
        chronological.forEach { t ->
            val signedAmount = "${if (t.type == "credit") "+" else "-"}${Data.formatBalance(t.amount)}"
            val balanceText = t.balance?.let { Data.formatBalance(it) } ?: ""
            appendLine(
                listOf(
                    csvField(t.date),
                    csvField(t.time),
                    csvField(t.title),
                    csvField(signedAmount),
                    csvField(t.type),
                    csvField(t.category),
                    csvField(t.bankShortName),
                    csvField(balanceText)
                ).joinToString(",")
            )
        }
    }
}

private fun buildStatementPdfBytes(transactions: List<Transaction>, accountHolder: String): ByteArray {
    val totalCredit = transactions.filter { it.type == "credit" }.sumOf { it.amount }
    val totalDebit = transactions.filter { it.type == "debit" }.sumOf { it.amount }
    val text = """
        %PDF-1.4
        1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
        2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
        3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj
        5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> endobj
        4 0 obj << /Length 300 >>
        stream
        BT
        /F1 18 Tf
        50 720 Td
        (HABTE FINANCIAL STATEMENT) Tj
        /F1 12 Tf
        0 -30 Td
        (Account Holder: $accountHolder) Tj
        0 -20 Td
        (Total Inflow: ETB ${Data.formatBalance(totalCredit)}) Tj
        0 -20 Td
        (Total Outflow: ETB ${Data.formatBalance(totalDebit)}) Tj
        0 -20 Td
        (Net Balance: ETB ${Data.formatBalance(totalCredit - totalDebit)}) Tj
        0 -20 Td
        (Generated on: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}) Tj
        ET
        endstream
        endobj
        xref
        0 6
        0000000000 65535 f 
        0000000009 00000 n 
        0000000058 00000 n 
        0000000115 00000 n 
        0000000300 00000 n 
        0000000215 00000 n 
        trailer << /Size 6 /Root 1 0 R >>
        startxref
        650
        %%EOF
    """.trimIndent()
    return text.toByteArray(Charsets.ISO_8859_1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = LocalEthiopianColors.current

    val transactions by FinanceRepository.transactions.collectAsState()
    val userName by SettingsRepository.userName.collectAsState()
    val reminders by PaymentReminderRepository.reminders.collectAsState()

    var showCurrencyModal by remember { mutableStateOf(false) }
    var showLoanModal by remember { mutableStateOf(false) }
    var showTaxModal by remember { mutableStateOf(false) }
    var showWealthModal by remember { mutableStateOf(false) }
    var showEmergencyModal by remember { mutableStateOf(false) }
    var showDuplicateModal by remember { mutableStateOf(false) }
    var showWidgetModal by remember { mutableStateOf(false) }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(buildStatementPdfBytes(transactions, userName.ifBlank { "Account Holder" }))
                }
                Toast.makeText(context, "PDF Statement generated successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    OutputStreamWriter(out).use { writer ->
                        writer.write(buildStatementCsv(transactions, userName.ifBlank { "Account Holder" }))
                    }
                }
                Toast.makeText(context, "CSV Statement exported successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export CSV: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val text = context.contentResolver.openInputStream(it)?.use { input ->
                        BufferedReader(InputStreamReader(input)).readText()
                    }
                    if (text.isNullOrBlank()) {
                        Toast.makeText(context, "Backup file is empty.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val array = JSONArray(text)
                    val restored = mutableListOf<Transaction>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        restored += Transaction(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            amount = obj.getDouble("amount"),
                            date = obj.getString("date"),
                            type = obj.getString("type"),
                            bankShortName = obj.getString("bankShortName"),
                            category = obj.optString("category", "Other"),
                            balance = if (obj.isNull("balance")) null else obj.optDouble("balance"),
                            accountSuffix = if (obj.isNull("accountSuffix")) null else obj.optString("accountSuffix"),
                            time = obj.optString("time", ""),
                            reason = obj.optString("reason", "")
                        )
                    }
                    FinanceRepository.restoreTransactions(restored)
                    Toast.makeText(context, "Successfully restored ${restored.size} transactions!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val upcomingReminder = remember(reminders) {
        reminders.filter { it.enabled }.minByOrNull { it.dueDateMillis }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Executive Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Financial Workspace",
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Executive tools, calculators & data audit",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.emeraldPrimary.copy(alpha = 0.12f))
                    .border(1.dp, colors.emeraldPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${transactions.size} TXs",
                    color = colors.emeraldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 155.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(bottom = 100.dp)
        ) {
            // Hero Upcoming Reminder Banner
            item(span = { GridItemSpan(maxLineSpan) }) {
                UpcomingPaymentCard(
                    reminder = upcomingReminder,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigate("payment_reminders")
                    }
                )
            }

            TOOL_SECTIONS.forEach { section ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ToolSectionHeader(title = section.title, subtitle = section.subtitle)
                }
                items(section.tools, key = { it.id }) { tool ->
                    ToolGridCard(
                        tool = tool,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            when (tool.id) {
                                "tax_calc" -> showTaxModal = true
                                "loan_calc" -> showLoanModal = true
                                "currency_fx" -> showCurrencyModal = true
                                "compound_wealth" -> showWealthModal = true
                                "emergency_fund" -> showEmergencyModal = true
                                "pdf_statement" -> onNavigate("export_financial_statement")
                                "csv_export" -> {
                                    if (transactions.isEmpty()) {
                                        Toast.makeText(context, "No transactions found to export.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        createCsvLauncher.launch("Habte_Transactions_${System.currentTimeMillis()}.csv")
                                    }
                                }
                                "json_backup" -> onNavigate("export_data")
                                "json_import" -> openBackupLauncher.launch(arrayOf("application/json"))
                                "duplicate_audit" -> showDuplicateModal = true
                                "payment_reminders" -> onNavigate("payment_reminders")
                                "budget_planner" -> onNavigate("budget")
                                "net_worth" -> onNavigate("net_worth")
                                "achievement_certs" -> onNavigate("achievement_certificates")
                                "spending_widget" -> showWidgetModal = true
                            }
                        }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(6.dp))
                com.mobile.ads.NativeAdComposable(modifier = Modifier.padding(horizontal = 4.dp))
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(8.dp))
                QuickActionBanner {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigate("support")
                }
            }
        }
    }

    if (showTaxModal) EthiopianTaxModal(onClose = { showTaxModal = false })
    if (showLoanModal) LoanAmortizationModal(onClose = { showLoanModal = false })
    if (showCurrencyModal) ForexConverterModal(onClose = { showCurrencyModal = false })
    if (showWealthModal) CompoundWealthModal(onClose = { showWealthModal = false })
    if (showEmergencyModal) EmergencyFundModal(transactions = transactions, onClose = { showEmergencyModal = false })
    if (showDuplicateModal) DuplicateAuditModal(onClose = { showDuplicateModal = false })
    if (showWidgetModal) SpendingWidgetModal(onClose = { showWidgetModal = false })
}

@Composable
private fun ToolSectionHeader(title: String, subtitle: String) {
    val colors = LocalEthiopianColors.current
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.emeraldPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = colors.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 11.dp)
        )
    }
}

@Composable
private fun ToolGridCard(tool: Tool, onClick: () -> Unit) {
    val colors = LocalEthiopianColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(120),
        label = "toolCardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tool.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(22.dp))
                }

                if (tool.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tool.color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tool.badge, color = tool.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = tool.label,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = tool.sub,
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UpcomingPaymentCard(reminder: PaymentReminder?, onClick: () -> Unit) {
    val colors = LocalEthiopianColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.emeraldPrimary, colors.emeraldDark)
                )
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (reminder == null) {
                Text("All Bills Up to Date", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Tap to schedule future payment reminders", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            } else {
                val daysLeft = ((reminder.dueDateMillis - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt()
                Text(
                    text = "${reminder.label} Â· ETB ${Data.formatBalance(reminder.amount)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (daysLeft <= 0) "Due today" else "Due in $daysLeft day${if (daysLeft == 1) "" else "s"}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun QuickActionBanner(onClick: () -> Unit) {
    val colors = LocalEthiopianColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.emeraldPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = colors.emeraldPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Habte Support & Guides", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Learn financial shortcuts and SMS pairing", color = colors.textSecondary, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary)
    }
}

// â”€â”€ 1. ETHIOPIAN INCOME TAX & PENSION CALCULATOR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EthiopianTaxModal(onClose: () -> Unit) {
    val colors = LocalEthiopianColors.current
    var grossInput by remember { mutableStateOf("") }
    var deductPension by remember { mutableStateOf(true) }

    val gross = grossInput.toDoubleOrNull() ?: 0.0
    val pension = if (deductPension) gross * 0.07 else 0.0
    val taxableIncome = Math.max(0.0, gross - pension)

    val incomeTax = when {
        taxableIncome <= 600 -> 0.0
        taxableIncome <= 1650 -> (taxableIncome * 0.10) - 60.0
        taxableIncome <= 3200 -> (taxableIncome * 0.15) - 142.5
        taxableIncome <= 5250 -> (taxableIncome * 0.20) - 302.5
        taxableIncome <= 7800 -> (taxableIncome * 0.25) - 565.0
        taxableIncome <= 10900 -> (taxableIncome * 0.30) - 955.0
        else -> (taxableIncome * 0.35) - 1500.0
    }.coerceAtLeast(0.0)

    val totalDeductions = pension + incomeTax
    val netSalary = Math.max(0.0, gross - totalDeductions)

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Ethiopian Income Tax (ERCA)", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Calculate gross-to-net salary according to ERCA tax brackets", color = colors.textSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = grossInput,
                onValueChange = { grossInput = sanitizeNumeric(it, true) },
                label = { Text("Gross Monthly Salary (ETB)", color = colors.textSecondary) },
                placeholder = { Text("e.g. 25,000", color = colors.textMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceElevated,
                    unfocusedContainerColor = colors.surfaceElevated,
                    focusedBorderColor = colors.emeraldPrimary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = deductPension,
                    onCheckedChange = { deductPension = it },
                    colors = CheckboxDefaults.colors(checkedColor = colors.emeraldPrimary)
                )
                Text("Deduct 7% Employee Pension Contribution", color = colors.textPrimary, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gross Salary", color = colors.textSecondary, fontSize = 13.sp)
                    Text("ETB ${Data.formatBalance(gross)}", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (deductPension) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Employee Pension (7%)", color = colors.textSecondary, fontSize = 13.sp)
                        Text("- ETB ${Data.formatBalance(pension)}", color = colors.expense, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Income Tax (ERCA)", color = colors.textSecondary, fontSize = 13.sp)
                    Text("- ETB ${Data.formatBalance(incomeTax)}", color = colors.expense, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = colors.border)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Take-Home Net Salary", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("ETB ${Data.formatBalance(netSalary)}", color = colors.income, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// â”€â”€ 2. LOAN & MORTGAGE AMORTIZATION CALCULATOR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanAmortizationModal(onClose: () -> Unit) {
    val colors = LocalEthiopianColors.current
    var principalInput by remember { mutableStateOf("") }
    var rateInput by remember { mutableStateOf("14.5") }
    var termMonthsInput by remember { mutableStateOf("24") }

    val principal = principalInput.toDoubleOrNull() ?: 0.0
    val annualRate = rateInput.toDoubleOrNull() ?: 0.0
    val monthlyRate = annualRate / 12.0 / 100.0
    val months = termMonthsInput.toDoubleOrNull() ?: 1.0

    val monthlyEmi = if (principal > 0 && months > 0) {
        if (monthlyRate > 0) {
            (principal * monthlyRate * Math.pow(1 + monthlyRate, months)) / (Math.pow(1 + monthlyRate, months) - 1)
        } else {
            principal / months
        }
    } else 0.0

    val totalPayment = monthlyEmi * months
    val totalInterest = Math.max(0.0, totalPayment - principal)

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Loan & Mortgage Calculator", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Simulate monthly installment, interest cost, and payoff totals", color = colors.textSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = principalInput,
                onValueChange = { principalInput = sanitizeNumeric(it, true) },
                label = { Text("Loan Principal (ETB)", color = colors.textSecondary) },
                placeholder = { Text("e.g. 500,000", color = colors.textMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceElevated,
                    unfocusedContainerColor = colors.surfaceElevated,
                    focusedBorderColor = colors.emeraldPrimary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = sanitizeNumeric(it, true) },
                    label = { Text("Annual Rate (%)", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = termMonthsInput,
                    onValueChange = { termMonthsInput = sanitizeNumeric(it, false) },
                    label = { Text("Term (Months)", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly EMI Payment", color = colors.textSecondary, fontSize = 13.sp)
                    Text("ETB ${Data.formatBalance(monthlyEmi)}", color = colors.emeraldPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Interest Cost", color = colors.textSecondary, fontSize = 13.sp)
                    Text("ETB ${Data.formatBalance(totalInterest)}", color = colors.expense, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Amount Payable", color = colors.textSecondary, fontSize = 13.sp)
                    Text("ETB ${Data.formatBalance(totalPayment)}", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// â”€â”€ 3. FOREX CONVERTER WITH PRESET CURRENCIES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForexConverterModal(onClose: () -> Unit) {
    val colors = LocalEthiopianColors.current
    var foreignAmountInput by remember { mutableStateOf("100") }
    var selectedCurrency by remember { mutableStateOf("USD") }
    var customRateInput by remember { mutableStateOf("128.5") }
    var isForeignToEtb by remember { mutableStateOf(true) }

    val presetRates = mapOf(
        "USD" to "128.5",
        "EUR" to "138.2",
        "GBP" to "162.4",
        "AED" to "35.0",
        "SAR" to "34.2",
        "CAD" to "93.1"
    )

    val amount = foreignAmountInput.toDoubleOrNull() ?: 0.0
    val rate = customRateInput.toDoubleOrNull() ?: 0.0
    val converted = if (isForeignToEtb) amount * rate else if (rate > 0) amount / rate else 0.0

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Foreign Exchange Converter", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Convert Diaspora remittances and foreign currencies to ETB", color = colors.textSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = { isForeignToEtb = !isForeignToEtb }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", tint = colors.emeraldPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetRates.keys.forEach { cur ->
                    val isSel = cur == selectedCurrency
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) colors.emeraldPrimary else colors.surfaceElevated)
                            .border(1.dp, if (isSel) colors.emeraldPrimary else colors.border, RoundedCornerShape(10.dp))
                            .clickable {
                                selectedCurrency = cur
                                customRateInput = presetRates[cur] ?: "1.0"
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(cur, color = if (isSel) MaterialTheme.colorScheme.onPrimary else colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = foreignAmountInput,
                    onValueChange = { foreignAmountInput = sanitizeNumeric(it, true) },
                    label = { Text(if (isForeignToEtb) "Amount ($selectedCurrency)" else "Amount (ETB)", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = customRateInput,
                    onValueChange = { customRateInput = sanitizeNumeric(it, true) },
                    label = { Text("Rate (ETB/$selectedCurrency)", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isForeignToEtb) "Converted to ETB" else "Converted to $selectedCurrency", color = colors.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${if (isForeignToEtb) "ETB " else "$selectedCurrency "}${Data.formatBalance(converted)}",
                        color = colors.emeraldPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// â”€â”€ 4. COMPOUND WEALTH & SAVINGS FORECASTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompoundWealthModal(onClose: () -> Unit) {
    val colors = LocalEthiopianColors.current
    var initialInput by remember { mutableStateOf("10000") }
    var monthlyInput by remember { mutableStateOf("2000") }
    var annualReturnInput by remember { mutableStateOf("12") }
    var yearsInput by remember { mutableStateOf("5") }

    val initial = initialInput.toDoubleOrNull() ?: 0.0
    val monthly = monthlyInput.toDoubleOrNull() ?: 0.0
    val annualReturn = (annualReturnInput.toDoubleOrNull() ?: 0.0) / 100.0
    val years = yearsInput.toDoubleOrNull() ?: 1.0
    val totalMonths = (years * 12).toInt()
    val monthlyReturn = annualReturn / 12.0

    var futureValue = initial * Math.pow(1 + monthlyReturn, totalMonths.toDouble())
    if (monthlyReturn > 0) {
        futureValue += monthly * ((Math.pow(1 + monthlyReturn, totalMonths.toDouble()) - 1) / monthlyReturn)
    } else {
        futureValue += monthly * totalMonths
    }

    val totalDeposited = initial + (monthly * totalMonths)
    val totalGain = Math.max(0.0, futureValue - totalDeposited)

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Wealth & Compound Forecaster", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Simulate long-term wealth growth through recurring investments", color = colors.textSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = initialInput,
                    onValueChange = { initialInput = sanitizeNumeric(it, true) },
                    label = { Text("Initial Deposit", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = monthlyInput,
                    onValueChange = { monthlyInput = sanitizeNumeric(it, true) },
                    label = { Text("Monthly Contribution", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = annualReturnInput,
                    onValueChange = { annualReturnInput = sanitizeNumeric(it, true) },
                    label = { Text("Annual Return (%)", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = yearsInput,
                    onValueChange = { yearsInput = sanitizeNumeric(it, false) },
                    label = { Text("Timeline (Years)", color = colors.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedBorderColor = colors.emeraldPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Projected Future Value", color = colors.textSecondary, fontSize = 13.sp)
                    Text("ETB ${Data.formatBalance(futureValue)}", color = colors.emeraldPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Out-of-Pocket Deposits", color = colors.textSecondary, fontSize = 13.sp)
                    Text("ETB ${Data.formatBalance(totalDeposited)}", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Compounded Growth Profit", color = colors.textSecondary, fontSize = 13.sp)
                    Text("+ ETB ${Data.formatBalance(totalGain)}", color = colors.income, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// â”€â”€ 5. EMERGENCY SAFETY FUND CALCULATOR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyFundModal(transactions: List<Transaction>, onClose: () -> Unit) {
    val colors = LocalEthiopianColors.current
    val averageMonthlyExpense = remember(transactions) {
        val totalDebits = transactions.filter { it.type == "debit" }.sumOf { it.amount }
        if (totalDebits > 0) totalDebits / 3.0 else 12000.0
    }
    var monthlyExpenseInput by remember { mutableStateOf(Data.formatBalance(averageMonthlyExpense, true)) }
    val monthlyExp = monthlyExpenseInput.toDoubleOrNull() ?: 10000.0

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Emergency Fund Cushion", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Calculate safety reserves to shield against job loss or medical emergencies", color = colors.textSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = monthlyExpenseInput,
                onValueChange = { monthlyExpenseInput = sanitizeNumeric(it, true) },
                label = { Text("Estimated Monthly Expenses (ETB)", color = colors.textSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceElevated,
                    unfocusedContainerColor = colors.surfaceElevated,
                    focusedBorderColor = colors.emeraldPrimary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                EmergencyTierCard(tier = "3 Months", desc = "Basic Cushion", target = monthlyExp * 3, color = colors.warning, modifier = Modifier.weight(1f))
                EmergencyTierCard(tier = "6 Months", desc = "Recommended", target = monthlyExp * 6, color = colors.emeraldPrimary, modifier = Modifier.weight(1f))
                EmergencyTierCard(tier = "12 Months", desc = "Fortress", target = monthlyExp * 12, color = colors.income, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmergencyTierCard(tier: String, desc: String, target: Double, color: Color, modifier: Modifier = Modifier) {
    val colors = LocalEthiopianColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(tier, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = colors.textMuted, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("ETB ${Data.formatBalance(target)}", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// â”€â”€ 6. TRANSACTION DUPLICATE AUDIT MODAL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateAuditModal(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = LocalEthiopianColors.current
    val transactions by FinanceRepository.transactions.collectAsState()
    val duplicateGroups = remember(transactions) { findPotentialDuplicates(transactions) }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Transaction Duplicate Audit", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Detects duplicated SMS bank alerts recorded within short intervals", color = colors.textSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(16.dp))

            if (duplicateGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.emeraldPrimary, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Duplicates Detected", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("All transaction entries are verified unique.", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    duplicateGroups.forEach { group ->
                        val first = group.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.expense.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.expense, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${first.bankShortName} Â· ETB ${Data.formatBalance(first.amount)}", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${group.size} copies", color = colors.expense, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                group.forEach { tx ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${tx.date} ${tx.time}", color = colors.textSecondary, fontSize = 12.sp)
                                        TextButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                FinanceRepository.deleteTransaction(tx.id)
                                                Toast.makeText(context, "Deleted duplicate entry", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("Delete", color = colors.expense, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingWidgetModal(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = LocalEthiopianColors.current

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = colors.emeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Weekly Spending Widget",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Track daily outflow and weekly budget limits live directly on your device's Home Screen or Lock Screen.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "HOW TO ADD THE WIDGET:",
                            color = colors.goldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("1. Touch and hold an empty area on your Home Screen.", color = colors.textPrimary, fontSize = 12.sp)
                        Text("2. Tap Widgets.", color = colors.textPrimary, fontSize = 12.sp)
                        Text("3. Find 'Habte - Personal Finance'.", color = colors.textPrimary, fontSize = 12.sp)
                        Text("4. Touch and hold 'Weekly Spending Digest' and drag it to your screen.", color = colors.textPrimary, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        com.mobile.data.WeeklySpendingWidgetUpdater.updateAllWidgets(context)
                        Toast.makeText(context, "Widgets refreshed with live data!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.emeraldPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh Active Widgets Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close", color = colors.emeraldPrimary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
