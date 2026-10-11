package com.mobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.data.Data
import com.mobile.data.FinanceRepository
import com.mobile.data.SettingsRepository
import com.mobile.data.Transaction
import com.mobile.ui.components.WrappedStoryScreen
import com.mobile.ui.theme.LocalEthiopianColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class AnalyticsPeriod(val label: String) {
    WEEK("Week"), MONTH("Month"), YEAR("Year")
}

private enum class SortOption(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), HIGHEST("Highest amount"), LOWEST("Lowest amount")
}

private val IncomeColor  = Color(0xFF00C853)   // Emerald
private val ExpenseColor = Color(0xFFFF5252)   // Negative / Expense
private val GoldAccent   = Color(0xFFFFD54F)   // Gold accent (used sparingly)
private val txDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

// How many transactions to render up front before requiring a "See More" tap —
// this list lives inside a plain verticalScroll Column (not a LazyColumn), so an
// unbounded month with hundreds of transactions would otherwise compose every
// row at once.
private const val TRANSACTIONS_PAGE_SIZE = 100

// Same reasoning as TRANSACTIONS_PAGE_SIZE, applied to the Top Categories / Spending by Bank
// breakdown cards — a busy year can produce dozens of categories or banks, so these start
// short and grow via their own "See More" rather than ever being hard-capped at a fixed number.
private const val BREAKDOWN_PAGE_SIZE = 5

private data class ParsedTx(val tx: Transaction, val cal: Calendar)

private fun formatNetShort(net: Double): String {
    val sign = if (net >= 0) "+" else "-"
    val abs = kotlin.math.abs(net)
    val body = if (abs >= 1000) String.format("%.1fk", abs / 1000) else String.format("%.0f", abs)
    return "$sign$body"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val context = LocalContext.current
    val banks by FinanceRepository.banks.collectAsState()
    val transactions by FinanceRepository.transactions.collectAsState()
    val calendarSystemSetting by com.mobile.data.SettingsRepository.calendarSystem.collectAsState()
    val calendarSystem = remember(calendarSystemSetting) {
        if (calendarSystemSetting == "Ethiopian") com.mobile.data.CalendarSystem.ETHIOPIAN else com.mobile.data.CalendarSystem.GREGORIAN
    }

    var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.MONTH) }
    var selectedBankFilter by remember { mutableStateOf<String?>(null) }
    var showIncomeOnly by remember { mutableStateOf(false) }
    var showExpenseOnly by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var displayedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var showWrapped by remember { mutableStateOf(false) }
    var showDailyList by remember { mutableStateOf(false) }
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    val selectedTransaction = remember(transactions, selectedTransactionId) {
        transactions.find { it.id == selectedTransactionId }
    }

    val now = remember { Calendar.getInstance() }
    val currentYear = now.get(Calendar.YEAR)

    LaunchedEffect(banks) {
        if (selectedBankFilter != null && banks.none { it.shortName == selectedBankFilter }) {
            selectedBankFilter = null
        }
    }

    val parsedTransactions = remember(transactions) {
        transactions.mapNotNull { tx ->
            val cal = com.mobile.data.parseTransactionDate(tx.date) ?: return@mapNotNull null
            ParsedTx(tx, cal)
        }
    }

    val bankFiltered = remember(parsedTransactions, selectedBankFilter) {
        if (selectedBankFilter == null) parsedTransactions
        else parsedTransactions.filter { it.tx.bankShortName == selectedBankFilter }
    }

    val yearTxCount = remember(parsedTransactions, currentYear) {
        parsedTransactions.count { it.cal.get(Calendar.YEAR) == currentYear }
    }

    val trendBuckets = remember(bankFiltered, selectedPeriod, calendarSystem, now) {
        val txs = bankFiltered.map { it.tx }
        when (selectedPeriod) {
            AnalyticsPeriod.WEEK -> com.mobile.data.weeklyIncomeExpenseSeries(txs, now, calendarSystem)
            AnalyticsPeriod.MONTH -> com.mobile.data.monthlyIncomeExpenseSeries(txs, now, calendarSystem)
            AnalyticsPeriod.YEAR -> com.mobile.data.yearlyIncomeExpenseSeries(txs, now, calendarSystem)
        }
    }

    var trendSelectedIndex by remember(trendBuckets) { mutableStateOf(trendBuckets.size - 1) }

    val monthDayNets = remember(bankFiltered, displayedMonth, showIncomeOnly, showExpenseOnly) {
        val year = displayedMonth.get(Calendar.YEAR)
        val month = displayedMonth.get(Calendar.MONTH)
        val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        (1..daysInMonth).associateWith { day ->
            bankFiltered.filter { p ->
                p.cal.get(Calendar.YEAR) == year && p.cal.get(Calendar.MONTH) == month &&
                    p.cal.get(Calendar.DAY_OF_MONTH) == day &&
                    (!showIncomeOnly || p.tx.type == "credit") && (!showExpenseOnly || p.tx.type == "debit")
            }.sumOf { if (it.tx.type == "credit") it.tx.amount else -it.tx.amount }
        }
    }

    val monthTransactions = remember(bankFiltered, displayedMonth, sortOption, showIncomeOnly, showExpenseOnly) {
        val year = displayedMonth.get(Calendar.YEAR)
        val month = displayedMonth.get(Calendar.MONTH)
        bankFiltered.filter { p ->
            p.cal.get(Calendar.YEAR) == year && p.cal.get(Calendar.MONTH) == month &&
                (!showIncomeOnly || p.tx.type == "credit") && (!showExpenseOnly || p.tx.type == "debit")
        }.sortedWith(
            when (sortOption) {
                SortOption.NEWEST -> compareByDescending { it.cal.timeInMillis }
                SortOption.OLDEST -> compareBy { it.cal.timeInMillis }
                SortOption.HIGHEST -> compareByDescending { it.tx.amount }
                SortOption.LOWEST -> compareBy { it.tx.amount }
            }
        )
    }

    var visibleTransactionCount by remember(bankFiltered, displayedMonth, sortOption, showIncomeOnly, showExpenseOnly) {
        mutableStateOf(TRANSACTIONS_PAGE_SIZE)
    }
    var visibleCategoryCount by remember(bankFiltered, displayedMonth, showIncomeOnly, showExpenseOnly) {
        mutableStateOf(BREAKDOWN_PAGE_SIZE)
    }
    var visibleBankCount by remember(bankFiltered, displayedMonth, showIncomeOnly, showExpenseOnly) {
        mutableStateOf(BREAKDOWN_PAGE_SIZE)
    }

    val breakdownTotal = remember(monthTransactions, showIncomeOnly) {
        if (showIncomeOnly) com.mobile.data.totalCredit(monthTransactions.map { it.tx })
        else com.mobile.data.totalDebit(monthTransactions.map { it.tx })
    }
    val topCategories = remember(monthTransactions, showIncomeOnly) {
        if (showIncomeOnly) com.mobile.data.topIncomeCategories(monthTransactions.map { it.tx })
        else com.mobile.data.topSpendingCategories(monthTransactions.map { it.tx }, limit = Int.MAX_VALUE)
    }
    val topBanks = remember(monthTransactions, showIncomeOnly) {
        if (showIncomeOnly) com.mobile.data.incomeByBank(monthTransactions.map { it.tx })
        else com.mobile.data.spendingByBank(monthTransactions.map { it.tx })
    }

    val colors = LocalEthiopianColors.current
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 12.dp)) {
            Column {
                Text("Analytics", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Financial insights & trends", color = colors.textSecondary, fontSize = 13.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 100.dp)
        ) {
            // Wrapped Promo Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable {
                        if (yearTxCount > 0) showWrapped = true
                        else Toast.makeText(context, "No transactions in $currentYear yet", Toast.LENGTH_SHORT).show()
                    }
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.goldAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.goldAccent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wrapped $currentYear", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("$yearTxCount transactions in $currentYear", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        Text("Tap to view your story", color = colors.emeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                }
            }

            // Week / Month / Year Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsPeriod.values().forEach { period ->
                    val isSelected = selectedPeriod == period
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.emeraldPrimary else Color.Transparent)
                            .clickable { selectedPeriod = period }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = period.label,
                            color = if (isSelected) onPrimaryColor else colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Bank filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BankFilterChip(label = "All", isSelected = selectedBankFilter == null) { selectedBankFilter = null }
                banks.forEach { bank ->
                    BankFilterChip(label = bank.shortName, isSelected = selectedBankFilter == bank.shortName) {
                        selectedBankFilter = bank.shortName
                    }
                }
            }

            // BUG FIX: this used to be gated behind `if (transactions.isEmpty()) showSpinner
            // else showCarousel`, meant to smooth over the brief cold-start moment before
            // FinanceRepository's Room-backed flow emits its first value. But an empty list is
            // exactly what a genuinely new user with zero transactions has too — indistinguishable
            // from "not loaded yet" — so that guard left every fresh install stuck on a permanent
            // spinner here, forever. IncomeExpenseCarousel/IncomeExpenseTrendChart already render
            // an all-zero bucket list correctly (see their own "No activity..." empty states
            // below), so there's nothing to guard: render unconditionally.
            // Income vs Expense — swipeable across the same buckets as the trend chart
            // below (last 8 weeks / 6 months / 5 years, per the Week/Month/Year toggle
            // above), sharing trendSelectedIndex so the two stay in sync either direction.
            IncomeExpenseCarousel(
                buckets = trendBuckets,
                selectedIndex = trendSelectedIndex,
                onSelectedIndexChange = { trendSelectedIndex = it }
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Trend comparison — every week / every month / every year, depending on
            // the Week/Month/Year toggle above. Labels follow the user's chosen
            // calendar system (Settings > Calendar System): Ethiopian months follow
            // real Ethiopian month boundaries (13 months/year), not Gregorian ones.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (selectedPeriod) {
                        AnalyticsPeriod.WEEK -> "Weekly Trend"
                        AnalyticsPeriod.MONTH -> "Monthly Trend"
                        AnalyticsPeriod.YEAR -> "Yearly Trend"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot("Income", IncomeColor)
                    LegendDot("Expense", ExpenseColor)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                IncomeExpenseTrendChart(
                    buckets = trendBuckets,
                    selectedIndex = trendSelectedIndex,
                    onSelect = { trendSelectedIndex = it }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // UX FIX: nothing below this point previously indicated that it's on a completely
            // separate navigation axis from the Week/Month/Year toggle above (that toggle only
            // ever affects the trend chart/carousel) — the heatmap, breakdowns, and transaction
            // list are always scoped to whichever single month the arrows further down are set
            // to. Without this header the Week/Month/Year toggle looked like it should control
            // everything on the screen and silently didn't, which read as broken rather than
            // as two independent, intentional controls.
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Monthly Breakdown",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Scoped to one month at a time — use the arrows below to change it, separately from the Week/Month/Year trend above.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Type filter toggles + heatmap label
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeFilterToggle(active = showIncomeOnly, color = IncomeColor) {
                        showIncomeOnly = !showIncomeOnly
                        if (showIncomeOnly) showExpenseOnly = false
                    }
                    TypeFilterToggle(active = showExpenseOnly, color = ExpenseColor) {
                        showExpenseOnly = !showExpenseOnly
                        if (showExpenseOnly) showIncomeOnly = false
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { showDailyList = !showDailyList }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (showDailyList) Icons.AutoMirrored.Filled.List else Icons.Default.CalendarMonth,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (showDailyList) "List" else "Heatmap",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            // Month navigator + legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) } },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = com.mobile.data.formatCalendarMonthYear(displayedMonth, calendarSystem),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) } },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot("Income", IncomeColor)
                    LegendDot("Expense", ExpenseColor)
                }
            }

            // Calendar heatmap or daily list view
            if (showDailyList) {
                DailyNetList(displayedMonth = displayedMonth, dayNets = monthDayNets)
            } else {
                CalendarHeatmap(displayedMonth = displayedMonth, dayNets = monthDayNets, now = now)
            }

            if (topCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                BreakdownCard(
                    title = if (showIncomeOnly) "Top Income Categories" else "Top Spending Categories",
                    allItems = topCategories,
                    totalForPercentage = breakdownTotal,
                    barColor = if (showIncomeOnly) IncomeColor else ExpenseColor,
                    visibleCount = visibleCategoryCount,
                    onSeeMore = { visibleCategoryCount += BREAKDOWN_PAGE_SIZE }
                )
            }

            if (topBanks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                BreakdownCard(
                    title = if (showIncomeOnly) "Income by Bank" else "Spending by Bank",
                    allItems = topBanks,
                    totalForPercentage = breakdownTotal,
                    barColor = if (showIncomeOnly) IncomeColor else ExpenseColor,
                    visibleCount = visibleBankCount,
                    onSeeMore = { visibleBankCount += BREAKDOWN_PAGE_SIZE }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transactions header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Transactions", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${monthTransactions.size}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box {
                    Row(
                        modifier = Modifier.clickable { showSortMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sort by", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption.values().forEach { opt ->
                            DropdownMenuItem(text = { Text(opt.label) }, onClick = { sortOption = opt; showSortMenu = false })
                        }
                    }
                }
            }

            if (monthTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions this month.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            } else {
                val hasMore = monthTransactions.size > visibleTransactionCount
                val visibleTransactions = if (hasMore) monthTransactions.take(visibleTransactionCount) else monthTransactions

                visibleTransactions.forEach { p ->
                    AnalyticsTransactionRow(
                        p.tx,
                        dateLabel = com.mobile.data.formatCalendarDate(p.cal, calendarSystem),
                        onClick = { selectedTransactionId = p.tx.id }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (hasMore) {
                    SeeMoreFooter(
                        visibleCount = visibleTransactionCount,
                        totalCount = monthTransactions.size,
                        onSeeMore = { visibleTransactionCount += TRANSACTIONS_PAGE_SIZE }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        com.mobile.ui.components.TransactionDetailSheet(
            transaction = selectedTransaction,
            onClose = { selectedTransactionId = null }
        )
    }

    if (showWrapped) {
        WrappedStoryScreen(
            transactions = transactions,
            year = currentYear,
            onDismiss = {
                showWrapped = false
                // Interstitials belong after a non-critical action completes — closing the
                // Wrapped year-in-review is exactly that (viewing a report), never during
                // an actual financial task.
                (context as? android.app.Activity)?.let { activity ->
                    com.mobile.ads.AdMobService.showInterstitialIfLoaded(activity)
                }
            }
        )
    }
}

@Composable
private fun BankFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TypeFilterToggle(active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (active) color else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

/**
 * "See More" pagination control shown below a capped list, plus a "Showing X of Y [itemLabel]"
 * caption. A filled pill with a chevron that flips to point up once everything is already
 * showing, rather than just a bare text link, so the control reads as an obvious, tappable
 * "load more" affordance rather than a stray line of text.
 */
@Composable
private fun SeeMoreFooter(visibleCount: Int, totalCount: Int, itemLabel: String = "transactions", onSeeMore: () -> Unit) {
    val allShown = visibleCount >= totalCount
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .clickable(enabled = !allShown, onClick = onSeeMore)
                .padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (allShown) "All caught up" else "See More",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (!allShown) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Showing ${minOf(visibleCount, totalCount)} of $totalCount $itemLabel",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

/**
 * Reusable "largest first, proportional bar, See More" breakdown — backs both Top Spending
 * Categories and Spending by Bank so a busy year's worth of categories/banks never gets
 * silently truncated to a fixed handful; [allItems] is always the *complete* list, and only
 * [visibleCount] of it is actually rendered until the user taps See More.
 */
@Composable
private fun BreakdownCard(
    title: String,
    allItems: List<Pair<String, Double>>,
    totalForPercentage: Double,
    visibleCount: Int,
    onSeeMore: () -> Unit,
    barColor: Color = ExpenseColor
) {
    val hasMore = allItems.size > visibleCount
    val visibleItems = if (hasMore) allItems.take(visibleCount) else allItems

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        visibleItems.forEachIndexed { index, (label, amount) ->
            val fraction = if (totalForPercentage > 0) (amount / totalForPercentage).toFloat().coerceIn(0f, 1f) else 0f
            val percentLabel = if (totalForPercentage > 0) "${(fraction * 100).toInt()}%" else null
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Data.formatBalance(amount)} ETB", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (percentLabel != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(percentLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barColor.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(barColor.copy(alpha = 1f - (index % 7) * 0.12f))
                    )
                }
            }
            if (index != visibleItems.lastIndex || hasMore) Spacer(modifier = Modifier.height(12.dp))
        }
        if (hasMore) {
            SeeMoreFooter(
                visibleCount = visibleCount,
                totalCount = allItems.size,
                itemLabel = if (title.contains("Bank", ignoreCase = true)) "banks" else "categories",
                onSeeMore = onSeeMore
            )
        }
    }
}

// Swipeable Income vs Expense summary — one page per trend bucket (last 8 weeks / 6
// months / 5 years, matching whichever bucket list the Week/Month/Year toggle produced).
// selectedIndex/onSelectedIndexChange are hoisted rather than owned here so this carousel
// and the tap-to-select IncomeExpenseTrendChart below it share one selection: swiping here
// moves the highlighted bar there, and tapping a bar there flips this carousel's page.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IncomeExpenseCarousel(
    buckets: List<com.mobile.data.PeriodBucket>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit
) {
    if (buckets.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = selectedIndex.coerceIn(0, buckets.size - 1)) { buckets.size }

    // External changes (a bar tap in the trend chart) animate the pager to match.
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }
    // A user swipe here propagates back out to the shared selection.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != selectedIndex) onSelectedIndexChange(page)
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Income vs Expense", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                buckets.getOrNull(pagerState.currentPage)?.fullLabel ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth(), pageSpacing = 16.dp) { page ->
            val bucket = buckets[page]
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IncomeExpenseCard(Modifier.weight(1f), "Income", bucket.income, IncomeColor, Icons.Default.SouthWest)
                    IncomeExpenseCard(Modifier.weight(1f), "Expense", bucket.expense, ExpenseColor, Icons.Default.NorthEast)
                }
                if (bucket.income > 0 || bucket.expense > 0) {
                    val incomeShare = if (bucket.income + bucket.expense > 0) (bucket.income / (bucket.income + bucket.expense)).toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(ExpenseColor.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(incomeShare)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(IncomeColor)
                        )
                    }
                    val savingsRate = com.mobile.data.savingsRatePercent(bucket.income, bucket.expense)
                    if (savingsRate != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Savings rate: ${String.format("%.1f", savingsRate)}%",
                            color = if (savingsRate >= 0) IncomeColor else ExpenseColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("No activity in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            buckets.indices.forEach { i ->
                val isActive = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isActive) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
            }
        }
    }
}

@Composable
private fun IncomeExpenseTrendChart(
    buckets: List<com.mobile.data.PeriodBucket>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    if (buckets.isEmpty() || buckets.all { it.income == 0.0 && it.expense == 0.0 }) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text("No activity in this range yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        return
    }

    val maxVal = (buckets.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0.0).coerceAtLeast(1.0)
    val selected = buckets.getOrNull(selectedIndex)
    val listState = rememberLazyListState()

    // BUG FIX: this used to be a plain Row + horizontalScroll that always opened scrolled
    // to the oldest (leftmost) bucket, while the header above already showed the newest
    // one (selectedIndex defaults to the last bucket) — the highlighted bar was off-screen
    // on first open. Scroll it into view whenever the shared selection changes.
    LaunchedEffect(selectedIndex, buckets) {
        if (selectedIndex in buckets.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column {
        if (selected != null) {
            val net = selected.income - selected.expense
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected.fullLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Net ${if (net >= 0) "+" else "-"}${Data.formatBalance(kotlin.math.abs(net))} ETB",
                    color = if (net >= 0) IncomeColor else ExpenseColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(buckets) { index, bucket ->
                val isSelected = index == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(46.dp).clickable { onSelect(index) }
                ) {
                    Row(
                        modifier = Modifier.height(110.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight(fraction = (bucket.income / maxVal).toFloat().coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (isSelected) IncomeColor else IncomeColor.copy(alpha = 0.5f))
                        )
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight(fraction = (bucket.expense / maxVal).toFloat().coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (isSelected) ExpenseColor else ExpenseColor.copy(alpha = 0.5f))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        bucket.label,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarHeatmap(displayedMonth: Calendar, dayNets: Map<Int, Double>, now: Calendar) {
    val year = displayedMonth.get(Calendar.YEAR)
    val month = displayedMonth.get(Calendar.MONTH)
    val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstWeekday = (displayedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK)
    val mondayIndex = (firstWeekday + 5) % 7
    val totalCells = mondayIndex + daysInMonth
    val weekCount = (totalCells + 6) / 7
    val isCurrentMonth = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                Text(
                    d, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        for (week in 0 until weekCount) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                for (col in 0 until 7) {
                    val cellIndex = week * 7 + col
                    val day = cellIndex - mondayIndex + 1
                    Box(modifier = Modifier.weight(1f).padding(2.dp)) {
                        if (day in 1..daysInMonth) {
                            val net = dayNets[day] ?: 0.0
                            val isToday = isCurrentMonth && day == today
                            val bg = when {
                                net > 0 -> IncomeColor.copy(alpha = 0.12f)
                                net < 0 -> ExpenseColor.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .then(
                                        if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "$day",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (net != 0.0) {
                                    Text(
                                        formatNetShort(net),
                                        color = if (net > 0) IncomeColor else ExpenseColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
private fun DailyNetList(displayedMonth: Calendar, dayNets: Map<Int, Double>) {
    val activeDays = dayNets.entries.filter { it.value != 0.0 }.sortedByDescending { it.key }
    val dayLabelFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }

    if (activeDays.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text("No activity this month.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        activeDays.forEach { (day, net) ->
            val dayCal = (displayedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$day", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dayLabelFormat.format(dayCal.time), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(
                    "${if (net >= 0) "+" else "-"}${Data.formatBalance(kotlin.math.abs(net))} ETB",
                    color = if (net >= 0) IncomeColor else ExpenseColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AnalyticsTransactionRow(tx: Transaction, dateLabel: String = tx.date, onClick: () -> Unit) {
    val isCredit = tx.type == "credit"
    val categoryLabel = if (tx.category == "Other") "Uncategorized" else tx.category
    val categoryIcon = when (tx.category) {
        "Bills", "Bills & Utilities", "Rent" -> Icons.AutoMirrored.Filled.List
        "Food", "Food & Dining" -> Icons.Default.ShoppingCart
        "Transfer", "Transfers", "Lend" -> Icons.Default.Sync
        "Income", "Salary" -> Icons.Default.KeyboardArrowUp
        "Shopping", "Cosmetics" -> Icons.Default.ShoppingCart
        else -> Icons.Default.Category
    }
    val bankFullName = remember(tx.bankShortName) {
        Data.PRESET_BANKS.find { it.shortName == tx.bankShortName }?.name ?: tx.bankShortName
    }
    val directionLabel = if (isCredit) "from" else "to"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(bankFullName, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("$directionLabel ${tx.title}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${if (isCredit) "+" else "-"}ETB ${Data.formatBalance(tx.amount)}",
                        color = if (isCredit) IncomeColor else ExpenseColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    if (tx.time.isNotBlank()) {
                        Text(tx.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(categoryLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun IncomeExpenseCard(modifier: Modifier, label: String, amount: Double, color: Color, icon: ImageVector) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(Data.formatBalance(amount), color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("ETB", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
