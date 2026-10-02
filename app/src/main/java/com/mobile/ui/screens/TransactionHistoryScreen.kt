package com.mobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mobile.data.Data
import com.mobile.data.FinanceRepository
import com.mobile.data.Transaction
import com.mobile.ui.theme.LocalEthiopianColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private enum class TxSortOption(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    AMOUNT_HIGH("Amount: High to low"),
    AMOUNT_LOW("Amount: Low to high")
}

private val fullDateTimeFormat = ThreadLocal.withInitial { SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()) }
private fun Transaction.timestampMillis(): Long =
    runCatching { fullDateTimeFormat.get()?.parse("$date $time")?.time }.getOrNull() ?: 0L

@Composable
fun TransactionHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val colors = LocalEthiopianColors.current
    val transactions by FinanceRepository.transactions.collectAsState()
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    val selectedTransaction = remember(transactions, selectedTransactionId) {
        transactions.find { it.id == selectedTransactionId }
    }

    var isSyncing by remember { mutableStateOf(false) }

    fun triggerSmsSync() {
        val readSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (readSmsGranted) {
            coroutineScope.launch {
                isSyncing = true
                try {
                    FinanceRepository.syncHistoricalSms(context)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "SMS ledger sync complete", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            triggerSmsSync()
        } else {
            Toast.makeText(context, "SMS permission required to scan bank messages", Toast.LENGTH_SHORT).show()
        }
    }

    var sortOption by remember { mutableStateOf(TxSortOption.NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val sortedTransactions = remember(transactions, sortOption) {
        when (sortOption) {
            TxSortOption.NEWEST -> transactions.sortedByDescending { it.timestampMillis() }
            TxSortOption.OLDEST -> transactions.sortedBy { it.timestampMillis() }
            TxSortOption.AMOUNT_HIGH -> transactions.sortedByDescending { it.amount }
            TxSortOption.AMOUNT_LOW -> transactions.sortedBy { it.amount }
        }
    }
    val filteredTransactions = remember(sortedTransactions, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            sortedTransactions
        } else {
            val normalizedAmountQuery = query.replace(",", "").removePrefix("+").removePrefix("-")
            sortedTransactions.filter { tx ->
                tx.title.contains(query, ignoreCase = true) ||
                    tx.bankShortName.contains(query, ignoreCase = true) ||
                    tx.category.contains(query, ignoreCase = true) ||
                    tx.reason.contains(query, ignoreCase = true) ||
                    (normalizedAmountQuery.isNotEmpty() &&
                        Data.formatBalance(tx.amount).replace(",", "").contains(normalizedAmountQuery))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text(
                text = "Transactions",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.emeraldPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${transactions.size}",
                    color = colors.emeraldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            // Sync SMS button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val readSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                    if (!readSmsGranted) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    } else {
                        triggerSmsSync()
                    }
                }
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.emeraldPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync SMS Transactions",
                        tint = colors.emeraldPrimary
                    )
                }
            }

            IconButton(onClick = {
                showSearch = !showSearch
                if (!showSearch) searchQuery = ""
            }) {
                Icon(
                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (showSearch) "Close search" else "Search transactions",
                    tint = colors.textMuted
                )
            }
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { sortMenuExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    TxSortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    color = if (option == sortOption) colors.emeraldPrimary else colors.textPrimary,
                                    fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                sortOption = option
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                placeholder = { Text("Search by name, amount, category, bank…", color = colors.textMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = colors.textMuted)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceElevated,
                    unfocusedContainerColor = colors.surfaceElevated,
                    focusedBorderColor = colors.emeraldPrimary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            )
        }

        val grouped = remember(filteredTransactions) {
            filteredTransactions.groupBy { it.date }
        }

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No transactions matching \"$searchQuery\"" else "No transactions yet",
                        color = colors.textMuted,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                var adPlaced = false
                grouped.forEach { (date, txsForDate) ->
                    item(key = "header-$date") {
                        Text(
                            text = date,
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
                        )
                    }
                    txsForDate.forEachIndexed { index, transaction ->
                        item(key = transaction.id) {
                            TransactionItem(
                                transaction = transaction,
                                onClick = { selectedTransactionId = transaction.id }
                            )
                        }
                        if (!adPlaced && index == 2) {
                            item(key = "native-feed-ad") {
                                com.mobile.ads.NativeAdComposable(
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            adPlaced = true
                        }
                    }
                }
                if (!adPlaced) {
                    item(key = "native-feed-ad-end") {
                        com.mobile.ads.NativeAdComposable(
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    com.mobile.ui.components.TransactionDetailSheet(
        transaction = selectedTransaction,
        onClose = { selectedTransactionId = null }
    )
}
