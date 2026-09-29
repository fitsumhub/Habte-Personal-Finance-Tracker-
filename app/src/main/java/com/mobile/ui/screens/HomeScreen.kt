package com.mobile.ui.screens

import android.graphics.Color.parseColor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import kotlinx.coroutines.launch


import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.data.Account
import com.mobile.data.AccountType
import com.mobile.data.Bank
import com.mobile.data.Data
import com.mobile.ui.components.*
import com.mobile.ui.theme.LocalEthiopianColors
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.mobile.data.Transaction


import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.mobile.data.FinanceRepository


// Using Tab from com.mobile.ui.components

// How many transactions "Recent Activities" shows up front before requiring a "See More"
// tap — this list lives inside a plain verticalScroll Column (not a LazyColumn), so an
// unbounded account history would otherwise compose every row at once.
private const val RECENT_ACTIVITIES_PAGE_SIZE = 10



@Composable
fun HomeScreen(onNavigateToProfile: () -> Unit, onNavigateToTransactionHistory: () -> Unit, onNavigateToAlerts: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val banks by FinanceRepository.banks.collectAsState()
    val transactions by FinanceRepository.transactions.collectAsState()
    // How many of "Recent Activities" are shown before requiring a "See More" tap. Not keyed
    // to `transactions` — a new transaction arriving via SMS shouldn't snap an already-expanded
    // list back down to 10.
    var visibleRecentCount by remember { mutableStateOf(RECENT_ACTIVITIES_PAGE_SIZE) }
    val userName by com.mobile.data.SettingsRepository.userName.collectAsState()
    val overviewPeriod by com.mobile.data.SettingsRepository.financialOverviewPeriod.collectAsState()
    val userInitials = remember(userName) {
        userName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).map { it.first().uppercaseChar() }.joinToString("").ifBlank { "?" }
    }
    
    val topTabs = remember(banks) {
        listOf(Tab("summary", "Summary"), Tab("today", "Today")) + 
        banks.map { Tab(it.shortName.lowercase(), it.shortName) }
    }

    
    var activeTab by remember { mutableStateOf("summary") }
    var selectedBank by remember { mutableStateOf<Bank?>(null) }
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    val selectedTransaction = remember(transactions, selectedTransactionId) {
        transactions.find { it.id == selectedTransactionId }
    }
    var showAddModal by remember { mutableStateOf(false) }


    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val syncSpinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncSpinAngle"
    )

    suspend fun runSync() {
        isSyncing = true
        try {
            val result = FinanceRepository.syncHistoricalSms(context, fullResync = true)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Master Refresh Complete: Scanned ${result.smsScanned} SMS messages, parsed ${result.transactionsParsed} records across ${result.bankCount} banks!",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Master Sync complete", Toast.LENGTH_SHORT).show()
            }
        } finally {
            isSyncing = false
        }
    }

    // Permission launcher for SMS (+ notifications on Android 13/API 33 and above)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            com.mobile.data.SmsObserver.register(context)
            coroutineScope.launch { runSync() }
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "SMS Permission denied. Cannot auto-categorize.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    LaunchedEffect(Unit) {
        val readSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val permissionsToRequest = mutableListOf<String>()
        if (!readSmsGranted) {
            permissionsToRequest += Manifest.permission.READ_SMS
            permissionsToRequest += Manifest.permission.RECEIVE_SMS
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissionsToRequest.isEmpty()) {
            if (readSmsGranted) {
                com.mobile.data.SmsObserver.register(context)
                launch { runSync() }
            }
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // A tapped transaction notification sets this from MainActivity; jump straight
    // to that transaction's categorize sheet once it's available, then clear it.
    val pendingTransactionId by FinanceRepository.pendingTransactionId.collectAsState()
    LaunchedEffect(pendingTransactionId, transactions) {
        val id = pendingTransactionId ?: return@LaunchedEffect
        if (transactions.any { it.id == id }) {
            selectedTransactionId = id
            FinanceRepository.setPendingTransaction(null)
        }
    }


    val hiddenAccountIds by com.mobile.data.SettingsRepository.hiddenAccountIds.collectAsState()
    val totalBalance by remember(banks, hiddenAccountIds) { derivedStateOf { Data.getTotalBalance(banks, hiddenAccountIds) } }
    val totalAccounts by remember(banks) { derivedStateOf { banks.sumOf { it.accounts.size } } }

    val displayedBanks by remember(banks, activeTab) { derivedStateOf {
        if (activeTab == "summary" || activeTab == "today") {
            banks
        } else {
            banks.filter { it.shortName.lowercase() == activeTab }
        }
    } }

    val now = remember { java.util.Calendar.getInstance() }
    val filteredTransactions by remember(transactions, activeTab) { derivedStateOf {
        when (activeTab) {
            "summary" -> transactions
            "today" -> transactions.filter { tx ->
                com.mobile.data.parseTransactionDate(tx.date)?.let { com.mobile.data.isSameCalendarDay(it, now) } ?: false
            }
            else -> transactions.filter { it.bankShortName.lowercase() == activeTab }
        }
    } }


    fun handleAddAccount(bankName: String, accountNumber: String) {
        val preset = Data.PRESET_BANKS.find { it.name.equals(bankName, ignoreCase = true) }
        val shortName = preset?.shortName ?: bankName.take(3).uppercase()
        val institution = preset?.let { p -> com.mobile.data.InstitutionCatalog.ALL.find { it.id == p.id } }
        val defaultAccountType = if (institution?.type == com.mobile.data.InstitutionType.DIGITAL_WALLET) {
            AccountType.MOBILE_WALLET
        } else {
            AccountType.SAVINGS
        }

        // Find latest balance from SMS transactions for this bank
        val initialBalance = transactions
            .filter { it.bankShortName == shortName }
            .mapNotNull { it.balance }
            .firstOrNull() ?: 0.0

        val newBank = Bank(
            id = preset?.id ?: (bankName.lowercase().replace(" ", "-") + System.currentTimeMillis()),
            name = preset?.name ?: bankName,
            shortName = shortName,
            colorFrom = preset?.colorFrom ?: "#4338CA",
            colorTo = preset?.colorTo ?: "#1E1B4B",
            logoText = preset?.logoText ?: bankName.take(3).uppercase(),
            logoResId = preset?.logoResId,
            accounts = listOf(
                Account(
                    id = "new-" + System.currentTimeMillis(),
                    bankId = preset?.id ?: bankName.lowercase(),
                    accountNumber = accountNumber,
                    label = "Main",
                    balance = initialBalance,
                    currency = "ETB",
                    type = defaultAccountType
                )
            )
        )
        FinanceRepository.addBank(newBank)
        Toast.makeText(context, "${newBank.name} Added with auto-synced balance", Toast.LENGTH_SHORT).show()
    }


    // Compute trend data dynamically or fallback to empty
    val trendData = remember(transactions) {
        if (transactions.size >= 2) {
            transactions.take(7).map { it.amount.toFloat() }.reversed()
        } else {
            listOf(0f, 0f, 0f, 0f) // flatline fallback
        }
    }


    val colors = LocalEthiopianColors.current
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning 👋"
            in 12..17 -> "Good afternoon 👋"
            else -> "Good evening 👋"
        }
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = greeting,
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Your financial overview • $overviewPeriod",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Animated Refresh Button
                HeaderIconButton(
                    icon = Icons.Default.Refresh,
                    iconModifier = if (isSyncing) Modifier.rotate(syncSpinAngle) else Modifier
                ) {
                    if (!isSyncing) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                        coroutineScope.launch {
                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                runSync()
                            } else {
                                FinanceRepository.ensureDefaultPresetsIfEmpty()
                                Toast.makeText(context, "Bank presets refreshed. Grant SMS permission to auto-sync.", Toast.LENGTH_LONG).show()
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS))
                            }
                        }
                    }
                }

                HeaderIconButton(icon = Icons.Default.Notifications) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToAlerts()
                }

                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.5.dp, colors.emeraldPrimary, CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToProfile()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(userInitials, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top Tab Bar
        TopTabBar(tabs = topTabs, activeKey = activeTab, onSelect = { activeTab = it })

        if (isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.emeraldPrimary,
                trackColor = colors.border
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .padding(bottom = 100.dp)
        ) {
            BalanceCard(
                totalBalance = totalBalance,
                bankCount = banks.size,
                accountCount = totalAccounts,
                trendData = trendData
            )

            // Quick Actions: Income, Expense, Transfer, Reminder
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.ArrowDownward,
                    label = "Income",
                    color = Color(0xFF00C853),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToTransactionHistory()
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.ArrowUpward,
                    label = "Expense",
                    color = Color(0xFFFF5252),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToTransactionHistory()
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.SyncAlt,
                    label = "Transfer",
                    color = Color(0xFF00C853),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToTransactionHistory()
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.NotificationsActive,
                    label = "Reminder",
                    color = Color(0xFFFFD54F),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToAlerts()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Bank cards grid — column count adapts to available width (phones vs tablets)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = (maxWidth / 170.dp).toInt().coerceAtLeast(2)
                val showAddCard = activeTab == "summary" || activeTab == "today"
                val chunkedBanks = displayedBanks.chunked(columns)

                Column {
                    chunkedBanks.forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEachIndexed { colIndex, bank ->
                                val index = rowIndex * columns + colIndex
                                key(bank.id) {
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn(animationSpec = tween(500, delayMillis = index * 100)) +
                                                slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = index * 100)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        BankCard(
                                            bank = bank,
                                            onPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedBank = it
                                            }
                                        )
                                    }
                                }
                            }

                            // Fill remaining slots with an Add card + spacers on the last incomplete row
                            if (row.size < columns && showAddCard) {
                                AddAccountCard(
                                    onPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAddModal = true
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                repeat(columns - row.size - 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Show Add card on its own row if the grid is full (also covers the zero-banks case)
                    if (displayedBanks.size % columns == 0 && showAddCard) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AddAccountCard(
                                onPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAddModal = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            repeat(columns - 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Activities Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Activities",
                    color = Color(0xFFFAF6EF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
                 Text(
                     "View All",
                     color = Color(0xFFD4A017),
                     fontSize = 13.sp,
                     fontWeight = FontWeight.Medium,
                     modifier = Modifier.clickable { 
                         haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                         onNavigateToTransactionHistory()
                     }
                 )
            }


            if (filteredTransactions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (activeTab == "today") "No activity today" else "No transactions yet",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isSyncing) "Syncing your SMS history…"
                        else if (activeTab == "today") "No spending or transactions recorded for today."
                        else "Transactions from your synced banks will show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val hasMoreRecent = filteredTransactions.size > visibleRecentCount
                val visibleRecent = if (hasMoreRecent) filteredTransactions.take(visibleRecentCount) else filteredTransactions

                visibleRecent.forEach { transaction ->
                    key(transaction.id) {
                        TransactionItem(
                            transaction = transaction,
                            onClick = { selectedTransactionId = transaction.id }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (hasMoreRecent) {
                    RecentActivitiesSeeMoreFooter(
                        visibleCount = visibleRecentCount,
                        totalCount = filteredTransactions.size,
                        onSeeMore = { visibleRecentCount += RECENT_ACTIVITIES_PAGE_SIZE }
                    )
                }
            }

            // Dashboard is one of the few screens allowed to show a banner ad (see
            // AdMobConfig.BANNER_ALLOWED_ROUTES) — never a financial-action screen.
            Spacer(modifier = Modifier.height(8.dp))
            com.mobile.ads.BannerAdView()

        }
    }



    AccountDetailSheet(
        bank = selectedBank, 
        onClose = { selectedBank = null },
        onDelete = { bank ->
            FinanceRepository.removeBank(bank.id)
            selectedBank = null
            Toast.makeText(context, "${bank.name} removed", Toast.LENGTH_SHORT).show()
        }
    )

    AddBankModal(
        visible = showAddModal,
        onClose = { showAddModal = false },
        onAdd = { name, acc -> handleAddAccount(name, acc) }
    )

    TransactionDetailSheet(
        transaction = selectedTransaction,
        onClose = { selectedTransactionId = null }
    )

}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEthiopianColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** "See More" pagination control shown below the capped Recent Activities list, plus a "Showing X of Y" caption. */
@Composable
private fun RecentActivitiesSeeMoreFooter(visibleCount: Int, totalCount: Int, onSeeMore: () -> Unit) {
    val colors = LocalEthiopianColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colors.emeraldPrimary.copy(alpha = 0.12f))
                .clickable(onClick = onSeeMore)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text("See More", color = colors.emeraldPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Showing $visibleCount of $totalCount transactions",
            color = colors.textMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalEthiopianColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp).then(iconModifier)
        )
    }
}

private fun categoryIconFor(category: String): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    "Bills", "Bills & Utilities", "Rent" -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.List
    "Food", "Food & Dining" -> androidx.compose.material.icons.Icons.Default.ShoppingCart
    "Transfer", "Transfers", "Lend" -> androidx.compose.material.icons.Icons.Default.Sync
    "Income", "Salary" -> androidx.compose.material.icons.Icons.Default.KeyboardArrowUp
    "Shopping", "Cosmetics" -> androidx.compose.material.icons.Icons.Default.ShoppingCart
    else -> androidx.compose.material.icons.Icons.Default.Category
}

@Composable
fun TransactionItem(transaction: Transaction, onClick: () -> Unit = {}) {
    val isCredit = transaction.type == "credit"
    val categoryIcon = categoryIconFor(transaction.category)
    val categoryLabel = if (transaction.category == "Other") "Uncategorized" else transaction.category
    val bankFullName = remember(transaction.bankShortName) {
        Data.PRESET_BANKS.find { it.shortName == transaction.bankShortName }?.name ?: transaction.bankShortName
    }
    val directionLabel = if (isCredit) "from" else "to"
    val autoHide by com.mobile.data.SettingsRepository.autoHideBalances.collectAsState()
    val dateFormat by com.mobile.data.SettingsRepository.dateFormat.collectAsState()
    val colors = LocalEthiopianColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = if (isCredit) colors.income else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bankFullName,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$directionLabel ${transaction.title}",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${com.mobile.data.formatDisplayDate(transaction.date, dateFormat)}${if (transaction.time.isNotBlank()) " · ${transaction.time}" else ""}",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (autoHide) "••••" else "${if (isCredit) "+ " else "− "}ETB ${Data.formatBalance(transaction.amount)}",
                    color = if (isCredit) colors.income else colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = categoryLabel,
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun categoryColorFor(category: String): Color = when (category) {
    "Income", "Salary" -> Color(0xFF00C853)
    "Bills", "Bills & Utilities", "Rent" -> Color(0xFF8B5CF6)
    "Food", "Food & Dining" -> Color(0xFFF59E0B)
    "Transport" -> Color(0xFF3B82F6)
    "Cash" -> Color(0xFF6B7280)
    "Transfers", "Transfer", "Lend" -> Color(0xFF10B981)
    "Shopping", "Cosmetics" -> Color(0xFFEC4899)
    else -> Color(0xFF00C853)
}
