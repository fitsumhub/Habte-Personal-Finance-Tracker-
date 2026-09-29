package com.mobile.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Pure, unit-testable versions of the period/aggregate math that AnalyticsScreen and
 * BudgetScreen render — extracted so the actual money calculations have real test
 * coverage instead of living only inline inside `remember` blocks inside composables.
 */

fun isSameCalendarDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

fun isSameCalendarWeek(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.WEEK_OF_YEAR) == b.get(Calendar.WEEK_OF_YEAR)

fun isSameCalendarMonth(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)

fun isSameCalendarYear(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR)

/**
 * True if [tx] falls within the trailing [windowMillis] ending at [now] — used by
 * BudgetScreen's rolling "Weekly" window (unlike Analytics' calendar-week bucketing).
 * Requires [tx] to be at or before [now]; a tx dated after [now] never matches, which
 * fixes a latent bug in the original inline check (`diff <= window`, with no lower bound,
 * would have also matched a future-dated transaction).
 */
fun isWithinRollingWindow(now: Calendar, tx: Calendar, windowMillis: Long): Boolean {
    val diff = now.timeInMillis - tx.timeInMillis
    return diff in 0..windowMillis
}

fun totalCredit(transactions: List<Transaction>): Double =
    transactions.filter { it.type == "credit" }.sumOf { it.amount }

fun totalDebit(transactions: List<Transaction>): Double =
    transactions.filter { it.type == "debit" }.sumOf { it.amount }

/** Percentage of income retained as savings, or null if there was no income to measure against. */
fun savingsRatePercent(income: Double, expense: Double): Double? =
    if (income > 0) ((income - expense) / income * 100) else null

/** Fraction of [limit] spent, clamped to [0, 1] for progress-bar rendering. A non-positive limit is treated as 0% (no progress), not a divide-by-zero crash. */
fun budgetProgressFraction(spent: Double, limit: Double): Float =
    if (limit <= 0) 0f else (spent / limit).coerceIn(0.0, 1.0).toFloat()

/**
 * Debit-only category totals for [transactions], largest first — the "Top categories"
 * section of the periodic spending summary notification (see SummaryNotifier).
 */
fun topSpendingCategories(transactions: List<Transaction>, limit: Int = 3): List<Pair<String, Double>> =
    transactions
        .filter { it.type == "debit" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(limit)

/** Debit-only totals per bank for [transactions], largest first — the "categorize by bank" breakdown on the Analytics screen. */
fun spendingByBank(transactions: List<Transaction>, limit: Int = Int.MAX_VALUE): List<Pair<String, Double>> =
    transactions
        .filter { it.type == "debit" }
        .groupBy { it.bankShortName }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(limit)

/**
 * Credit-only category totals, largest first — the income-side counterpart to
 * [topSpendingCategories]. Analytics switches to this (and relabels the card "Top Income
 * Categories") when the "Income only" filter is active, since [topSpendingCategories] would
 * otherwise always return empty there (it only ever looks at debits).
 */
fun topIncomeCategories(transactions: List<Transaction>, limit: Int = Int.MAX_VALUE): List<Pair<String, Double>> =
    transactions
        .filter { it.type == "credit" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(limit)

/** Credit-only totals per bank, largest first — the income-side counterpart to [spendingByBank]. */
fun incomeByBank(transactions: List<Transaction>, limit: Int = Int.MAX_VALUE): List<Pair<String, Double>> =
    transactions
        .filter { it.type == "credit" }
        .groupBy { it.bankShortName }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(limit)

// ── Income vs Expense trend buckets (week/month/year, Gregorian or Ethiopian) ──────────

/** Parses a [Transaction.date] string into a Calendar with multi-locale and multi-format fallback. */
fun parseTransactionDate(dateStr: String): Calendar? {
    if (dateStr.isBlank()) return null
    val patterns = listOf(
        "MMM dd, yyyy" to Locale.US,
        "MMM dd, yyyy" to Locale.getDefault(),
        "yyyy-MM-dd" to Locale.US,
        "dd/MM/yyyy" to Locale.US,
        "MM/dd/yyyy" to Locale.US,
        "MMM d, yyyy" to Locale.US,
        "MMM d, yyyy" to Locale.getDefault()
    )
    for ((pattern, locale) in patterns) {
        try {
            val d = SimpleDateFormat(pattern, locale).parse(dateStr.trim())
            if (d != null) {
                return Calendar.getInstance().apply { time = d }
            }
        } catch (_: Exception) {}
    }
    return null
}

private val displayDatePatterns = mapOf(
    "MM/DD/YYYY" to "MM/dd/yyyy",
    "DD/MM/YYYY" to "dd/MM/yyyy",
    "YYYY-MM-DD" to "yyyy-MM-dd"
)

/**
 * Reformats a stored [Transaction.date] string (always "MMM dd, yyyy") into the numeric
 * style the user picked in Settings > Date Format. Falls back to the original string if it
 * can't be parsed or [dateFormatSetting] doesn't match a known option.
 */
fun formatDisplayDate(dateStr: String, dateFormatSetting: String): String {
    val calendar = parseTransactionDate(dateStr) ?: return dateStr
    val pattern = displayDatePatterns[dateFormatSetting] ?: return dateStr
    return SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
}

/** One period's income/expense totals for a trend comparison chart. */
data class PeriodBucket(val label: String, val fullLabel: String, val income: Double, val expense: Double)

private fun bucketTotals(parsed: List<Pair<Transaction, Calendar>>, start: Calendar, end: Calendar): Pair<Double, Double> {
    val inRange = parsed.filter { (_, cal) -> !cal.before(start) && !cal.after(end) }.map { it.first }
    return totalCredit(inRange) to totalDebit(inRange)
}

/**
 * Buckets [transactions] into [weekCount] trailing Mon-Sun weeks ending with the week
 * containing [now], oldest first. Weeks are a universal 7-day span shared by both calendar
 * systems, so [system] only changes each bucket's label, not which transactions land in it.
 */
fun weeklyIncomeExpenseSeries(
    transactions: List<Transaction>,
    now: Calendar,
    system: CalendarSystem,
    weekCount: Int = 8
): List<PeriodBucket> {
    val parsed = transactions.mapNotNull { tx -> parseTransactionDate(tx.date)?.let { tx to it } }
    val daysSinceMonday = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val currentWeekStart = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val shortFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    return (weekCount - 1 downTo 0).map { i ->
        val start = (currentWeekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7 * i) }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7); add(Calendar.MILLISECOND, -1) }
        val (income, expense) = bucketTotals(parsed, start, end)
        val label = when (system) {
            CalendarSystem.GREGORIAN -> shortFormat.format(start.time)
            CalendarSystem.ETHIOPIAN -> {
                val e = EthiopianCalendar.fromGregorian(start)
                "${EthiopianCalendar.monthName(e.month).take(3)} ${e.day}"
            }
        }
        PeriodBucket(label, label, income, expense)
    }
}

/**
 * Buckets [transactions] into [monthCount] trailing months ending with the month containing
 * [now], oldest first. When [system] is Ethiopian, buckets follow real Ethiopian month
 * boundaries (13 months/year, 30 days each except Pagume's 5-6) rather than Gregorian ones.
 */
fun monthlyIncomeExpenseSeries(
    transactions: List<Transaction>,
    now: Calendar,
    system: CalendarSystem,
    monthCount: Int = 6
): List<PeriodBucket> {
    val parsed = transactions.mapNotNull { tx -> parseTransactionDate(tx.date)?.let { tx to it } }
    return when (system) {
        CalendarSystem.GREGORIAN -> {
            val shortFormat = SimpleDateFormat("MMM", Locale.getDefault())
            val fullFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            (monthCount - 1 downTo 0).map { i ->
                val monthCal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -i) }
                val start = (monthCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val end = (start.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }
                val (income, expense) = bucketTotals(parsed, start, end)
                PeriodBucket(shortFormat.format(monthCal.time), fullFormat.format(monthCal.time), income, expense)
            }
        }
        CalendarSystem.ETHIOPIAN -> {
            val nowE = EthiopianCalendar.fromGregorian(now)
            (monthCount - 1 downTo 0).map { i ->
                var month = nowE.month - i
                var year = nowE.year
                while (month < 1) { month += 13; year -= 1 }
                val (start, end) = EthiopianCalendar.monthRange(year, month)
                val (income, expense) = bucketTotals(parsed, start, end)
                val name = EthiopianCalendar.monthName(month)
                PeriodBucket(name.take(3), "$name $year", income, expense)
            }
        }
    }
}

/**
 * Buckets [transactions] into [yearCount] trailing years ending with the year containing
 * [now], oldest first. Ethiopian years run Meskerem 1 - Pagume end, roughly 7-8 years
 * behind the Gregorian year number.
 */
fun yearlyIncomeExpenseSeries(
    transactions: List<Transaction>,
    now: Calendar,
    system: CalendarSystem,
    yearCount: Int = 5
): List<PeriodBucket> {
    val parsed = transactions.mapNotNull { tx -> parseTransactionDate(tx.date)?.let { tx to it } }
    return when (system) {
        CalendarSystem.GREGORIAN -> {
            val currentYear = now.get(Calendar.YEAR)
            (yearCount - 1 downTo 0).map { i ->
                val year = currentYear - i
                val start = Calendar.getInstance().apply { clear(); set(year, Calendar.JANUARY, 1) }
                val end = Calendar.getInstance().apply { clear(); set(year, Calendar.DECEMBER, 31, 23, 59, 59) }
                val (income, expense) = bucketTotals(parsed, start, end)
                PeriodBucket(year.toString(), year.toString(), income, expense)
            }
        }
        CalendarSystem.ETHIOPIAN -> {
            val nowE = EthiopianCalendar.fromGregorian(now)
            (yearCount - 1 downTo 0).map { i ->
                val year = nowE.year - i
                val (start, end) = EthiopianCalendar.yearRange(year)
                val (income, expense) = bucketTotals(parsed, start, end)
                PeriodBucket(year.toString(), year.toString(), income, expense)
            }
        }
    }
}

// ── Net worth trend (Reports & Data > Net Worth Overview) ─────────────────────────────

/** Parses transaction date + time into epoch millis with multi-locale and format fallback. */
fun transactionTimestampMillis(tx: Transaction): Long? {
    if (tx.time.isBlank() || tx.date.isBlank()) return null
    val dtString = "${tx.date.trim()} ${tx.time.trim()}"
    val patterns = listOf(
        "MMM dd, yyyy hh:mm a" to Locale.US,
        "MMM dd, yyyy hh:mm a" to Locale.getDefault(),
        "MMM dd, yyyy HH:mm" to Locale.US,
        "MMM dd, yyyy HH:mm" to Locale.getDefault(),
        "MMM d, yyyy hh:mm a" to Locale.US,
        "MMM d, yyyy hh:mm a" to Locale.getDefault(),
        "yyyy-MM-dd HH:mm:ss" to Locale.US,
        "yyyy-MM-dd HH:mm" to Locale.US,
        "dd/MM/yyyy HH:mm" to Locale.US
    )
    for ((pattern, locale) in patterns) {
        try {
            val parsed = SimpleDateFormat(pattern, locale).parse(dtString)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return null
}

/** Result of monthly financial growth / savings calculation for Home section. */
data class MonthlyGrowth(
    val percentage: Double,
    val isPositive: Boolean,
    val formattedText: String
)

/**
 * Calculates real month-over-month growth percentage or savings rate from transactions.
 * Never hardcoded.
 */
fun calculateMonthlyGrowth(transactions: List<Transaction>): MonthlyGrowth {
    if (transactions.isEmpty()) {
        return MonthlyGrowth(0.0, true, "0.0% this month")
    }

    val now = Calendar.getInstance()
    val thisMonth = now.get(Calendar.MONTH)
    val thisYear = now.get(Calendar.YEAR)

    val prevCal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val prevMonth = prevCal.get(Calendar.MONTH)
    val prevYear = prevCal.get(Calendar.YEAR)

    var thisMonthIncome = 0.0
    var thisMonthExpense = 0.0
    var prevMonthIncome = 0.0
    var prevMonthExpense = 0.0

    for (tx in transactions) {
        val cal = parseTransactionDate(tx.date) ?: continue
        val txMonth = cal.get(Calendar.MONTH)
        val txYear = cal.get(Calendar.YEAR)

        if (txMonth == thisMonth && txYear == thisYear) {
            if (tx.type == "credit") thisMonthIncome += tx.amount else thisMonthExpense += tx.amount
        } else if (txMonth == prevMonth && txYear == prevYear) {
            if (tx.type == "credit") prevMonthIncome += tx.amount else prevMonthExpense += tx.amount
        }
    }

    val thisMonthNet = thisMonthIncome - thisMonthExpense
    val prevMonthNet = prevMonthIncome - prevMonthExpense

    return if (prevMonthIncome > 0 || prevMonthExpense > 0) {
        val diff = thisMonthNet - prevMonthNet
        val base = if (abs(prevMonthNet) > 0.01) abs(prevMonthNet) else (prevMonthIncome + prevMonthExpense).coerceAtLeast(1.0)
        val pct = (diff / base) * 100.0
        val clampedPct = pct.coerceIn(-999.0, 999.0)
        val sign = if (clampedPct >= 0) "+" else ""
        MonthlyGrowth(
            percentage = clampedPct,
            isPositive = clampedPct >= 0,
            formattedText = "$sign${String.format(Locale.US, "%.1f", clampedPct)}% vs last month"
        )
    } else if (thisMonthIncome > 0) {
        val savingsRate = ((thisMonthIncome - thisMonthExpense) / thisMonthIncome * 100.0).coerceIn(-100.0, 100.0)
        val sign = if (savingsRate >= 0) "+" else ""
        MonthlyGrowth(
            percentage = savingsRate,
            isPositive = savingsRate >= 0,
            formattedText = "$sign${String.format(Locale.US, "%.1f", savingsRate)}% saved this month"
        )
    } else if (thisMonthExpense > 0) {
        MonthlyGrowth(
            percentage = -100.0,
            isPositive = false,
            formattedText = "−ETB ${Data.formatBalance(thisMonthExpense)} this month"
        )
    } else {
        MonthlyGrowth(
            percentage = 0.0,
            isPositive = true,
            formattedText = "0.0% this month"
        )
    }
}

/**
 * Reconstructs a chronological series of balance points for the Home screen sparkline graph.
 * If bank reported explicit balances, uses them and sorts chronologically.
 * Otherwise reconstructs running balance back from current total balance, and sorts from oldest to newest.
 */
fun calculateBalanceTrend(transactions: List<Transaction>, currentBalance: Double): List<Float> {
    if (transactions.isEmpty()) {
        val b = currentBalance.toFloat()
        return listOf(b, b, b, b)
    }

    val txsWithBalance = transactions.filter { it.balance != null }
    if (txsWithBalance.size >= 2) {
        val sorted = txsWithBalance.sortedWith { a, b ->
            val timeA = transactionTimestampMillis(a) ?: parseTransactionDate(a.date)?.timeInMillis ?: 0L
            val timeB = transactionTimestampMillis(b) ?: parseTransactionDate(b.date)?.timeInMillis ?: 0L
            timeA.compareTo(timeB)
        }
        val points = sorted.takeLast(14).map { it.balance!!.toFloat() }
        if (points.size >= 2) return points
    }

    val recentTxs = transactions.take(14)
    var running = currentBalance
    val points = mutableListOf<Float>()
    points.add(running.toFloat())

    for (tx in recentTxs) {
        if (tx.type == "credit") {
            running -= tx.amount
        } else {
            running += tx.amount
        }
        points.add(running.toFloat())
    }

    val chronological = points.reversed()
    return if (chronological.size < 2) {
        listOf(chronological.firstOrNull() ?: 0f, chronological.firstOrNull() ?: 0f)
    } else {
        chronological
    }
}

/** Total net worth as of one point in time, for a trend comparison chart. */
data class NetWorthPoint(val label: String, val fullLabel: String, val netWorth: Double)

/**
 * Reconstructs total net worth at each of [monthCount] trailing month-ends by replaying
 * [transactions] in order and, for each (bank, account) pair, keeping the balance the most
 * recent transaction reported for it. This only approximates history: an account contributes
 * nothing to a month-end before its first parsed transaction, since no earlier balance was
 * ever recorded. Transactions without a recorded balance (Transaction.balance == null) are
 * ignored — they can't move the running total either way.
 */
fun monthlyNetWorthSeries(
    transactions: List<Transaction>,
    now: Calendar,
    system: CalendarSystem,
    monthCount: Int = 6
): List<NetWorthPoint> {
    val parsed = transactions
        .filter { it.balance != null }
        .mapNotNull { tx -> transactionTimestampMillis(tx)?.let { Triple(tx, it, "${tx.bankShortName}|${tx.accountSuffix ?: "main"}") } }
        .sortedBy { it.second }

    fun netWorthAsOf(endMillis: Long): Double {
        val latestPerAccount = mutableMapOf<String, Double>()
        for ((tx, millis, key) in parsed) {
            if (millis > endMillis) break
            latestPerAccount[key] = tx.balance!!
        }
        return latestPerAccount.values.sum()
    }

    return when (system) {
        CalendarSystem.GREGORIAN -> {
            val shortFormat = SimpleDateFormat("MMM", Locale.getDefault())
            val fullFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            (monthCount - 1 downTo 0).map { i ->
                val monthCal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -i) }
                val end = (monthCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }
                NetWorthPoint(shortFormat.format(monthCal.time), fullFormat.format(monthCal.time), netWorthAsOf(end.timeInMillis))
            }
        }
        CalendarSystem.ETHIOPIAN -> {
            val nowE = EthiopianCalendar.fromGregorian(now)
            (monthCount - 1 downTo 0).map { i ->
                var month = nowE.month - i
                var year = nowE.year
                while (month < 1) { month += 13; year -= 1 }
                val (_, end) = EthiopianCalendar.monthRange(year, month)
                val name = EthiopianCalendar.monthName(month)
                NetWorthPoint(name.take(3), "$name $year", netWorthAsOf(end.timeInMillis))
            }
        }
    }
}

// ── Duplicate transaction detection (Reports & Data > Duplicate Check) ────────────────

private data class DuplicateKey(val bank: String, val type: String, val amount: Double, val account: String?)

/**
 * Groups transactions that share bank, type, amount and account, and land within
 * [windowMillis] of each other — the signature of a bank resending the same alert with
 * slightly reworded text (a different reference number, say). That gives the resend a
 * different content hash, so it slips past the id-based insert-ignore dedupe in
 * FinanceRepository and lands as a second, separate row. Only groups with 2+ transactions
 * are returned; each group is sorted oldest-first internally, and the returned list is
 * sorted by the group's transaction amount, largest first.
 */
fun findPotentialDuplicates(
    transactions: List<Transaction>,
    windowMillis: Long = 10 * 60 * 1000L
): List<List<Transaction>> {
    val withTime = transactions.mapNotNull { tx -> transactionTimestampMillis(tx)?.let { tx to it } }
    val groups = withTime.groupBy { (tx, _) -> DuplicateKey(tx.bankShortName, tx.type, tx.amount, tx.accountSuffix) }

    val result = mutableListOf<List<Transaction>>()
    groups.values.forEach { entries ->
        val sorted = entries.sortedBy { it.second }
        var clusterStart = 0
        for (i in 1..sorted.size) {
            val clusterBroken = i == sorted.size || sorted[i].second - sorted[i - 1].second > windowMillis
            if (clusterBroken) {
                if (i - clusterStart > 1) {
                    result.add(sorted.subList(clusterStart, i).map { it.first })
                }
                clusterStart = i
            }
        }
    }
    return result.sortedByDescending { group -> group.maxOf { it.amount } }
}
