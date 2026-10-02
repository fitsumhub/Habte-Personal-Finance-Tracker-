package com.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mobile.data.FinanceRepository
import com.mobile.data.PdfStatementExporter
import com.mobile.data.SettingsRepository
import com.mobile.ui.theme.LocalEthiopianColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportFinancialStatementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalEthiopianColors.current
    val transactions by FinanceRepository.transactions.collectAsState()
    val userName by SettingsRepository.userName.collectAsState()

    var dateFilter by remember { mutableStateOf("All Transactions") }
    var typeFilter by remember { mutableStateOf("All") }
    var categoryFilter by remember { mutableStateOf("All Categories") }
    var sourceFilter by remember { mutableStateOf("All Sources") }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }

    val dateOptions = listOf("All Transactions", "This Month", "Last Month", "Last 3 Months", "Last 6 Months")
    val typeOptions = listOf("All", "Income", "Expense")

    val categoryOptions = remember(transactions) {
        listOf("All Categories") + transactions.map { it.category }.distinct().sorted()
    }
    val sourceOptions = remember(transactions) {
        listOf("All Sources") + transactions.map { it.bankShortName }.distinct().sorted()
    }

    var dateExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var sourceExpanded by remember { mutableStateOf(false) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            generatedFile?.let { srcFile ->
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        srcFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    Toast.makeText(context, "Statement saved successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Export Statement",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (generatedFile == null) {
                Text(
                    text = "Generate a colorful, premium PDF report of your transaction history and financial performance summaries.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // 1. Date Filter
                Column {
                    Text("Date Period", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = dateFilter,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textSecondary) },
                            modifier = Modifier.fillMaxWidth().clickable { dateExpanded = true },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = colors.border,
                                disabledTextColor = colors.textPrimary,
                                disabledContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.fillMaxSize().clickable { dateExpanded = true })
                        DropdownMenu(
                            expanded = dateExpanded,
                            onDismissRequest = { dateExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).background(colors.surface)
                        ) {
                            dateOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = colors.textPrimary) },
                                    onClick = {
                                        dateFilter = opt
                                        dateExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Type Filter
                Column {
                    Text("Transaction Type", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = typeFilter,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textSecondary) },
                            modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = colors.border,
                                disabledTextColor = colors.textPrimary,
                                disabledContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.fillMaxSize().clickable { typeExpanded = true })
                        DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).background(colors.surface)
                        ) {
                            typeOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = colors.textPrimary) },
                                    onClick = {
                                        typeFilter = opt
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Category Filter
                Column {
                    Text("Category", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = categoryFilter,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textSecondary) },
                            modifier = Modifier.fillMaxWidth().clickable { categoryExpanded = true },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = colors.border,
                                disabledTextColor = colors.textPrimary,
                                disabledContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.fillMaxSize().clickable { categoryExpanded = true })
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).background(colors.surface)
                        ) {
                            categoryOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = colors.textPrimary) },
                                    onClick = {
                                        categoryFilter = opt
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Source Filter
                Column {
                    Text("Account Source", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = sourceFilter,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textSecondary) },
                            modifier = Modifier.fillMaxWidth().clickable { sourceExpanded = true },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = colors.border,
                                disabledTextColor = colors.textPrimary,
                                disabledContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.fillMaxSize().clickable { sourceExpanded = true })
                        DropdownMenu(
                            expanded = sourceExpanded,
                            onDismissRequest = { sourceExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).background(colors.surface)
                        ) {
                            sourceOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = colors.textPrimary) },
                                    onClick = {
                                        sourceFilter = opt
                                        sourceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isGenerating) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.emeraldPrimary)
                    }
                } else {
                    Button(
                        onClick = {
                            if (transactions.isEmpty()) {
                                Toast.makeText(context, "No transactions found to export.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isGenerating = true
                            try {
                                val file = PdfStatementExporter.generatePdf(
                                    context = context,
                                    transactions = transactions,
                                    accountHolder = userName,
                                    dateFilter = dateFilter,
                                    typeFilter = typeFilter,
                                    categoryFilter = categoryFilter,
                                    sourceFilter = sourceFilter
                                )
                                generatedFile = file
                                (context as? android.app.Activity)?.let { act ->
                                    com.mobile.ads.AdMobService.showInterstitialIfLoaded(act)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isGenerating = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.emeraldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GENERATE STATEMENT", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // PDF READY STATE
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.emeraldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = colors.emeraldPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text("PDF Statement Ready!", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("The statement has been compiled successfully and is ready to save or share.", color = colors.textSecondary, fontSize = 13.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            generatedFile?.let { file ->
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.emeraldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN STATEMENT", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            generatedFile?.let { file ->
                                savePdfLauncher.launch("Habte_Statement_${System.currentTimeMillis()}.pdf")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = colors.textPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE TO DEVICE", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            generatedFile?.let { file ->
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Financial Statement"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = colors.textPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SHARE REPORT", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    TextButton(
                        onClick = {
                            generatedFile = null
                        }
                    ) {
                        Text("Create Another Report", color = colors.emeraldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
