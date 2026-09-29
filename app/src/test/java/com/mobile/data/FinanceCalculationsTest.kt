package com.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

private fun calendarOf(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Calendar =
    Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }

private fun tx(type: String, amount: Double): Transaction =
    Transaction(id = "id-$type-$amount", title = "t", amount = amount, date = "Jan 01, 2026", type = type, bankShortName = "CBE")

private val txDateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())

private fun txOn(type: String, amount: Double, date: Calendar): Transaction =
    Transaction(
        id = "id-$type-$amount-${date.timeInMillis}",
        title = "t",
        amount = amount,
        date = txDateFormat.format(date.time),
        type = type,
        bankShortName = "CBE"
    )

private val txTimeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

private fun txAt(
    id: String,
    type: String,
    amount: Double,
    at: Calendar,
    bankShortName: String = "CBE",
    balance: Double? = null,
    accountSuffix: String? = null,
    title: String = "t"
): Transaction =
    Transaction(
        id = id,
        title = title,
        amount = amount,
        date = txDateFormat.format(at.time),
        time = txTimeFormat.format(at.time),
        type = type,
        bankShortName = bankShortName,
        balance = balance,
        accountSuffix = accountSuffix
    )

class FinanceCalculationsTest {

    // ── Calendar bucketing ──────────────────────────────────────────────────────────

    @Test
    fun `same calendar day is true only within the same day`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15, hour = 18)
        assertTrue(isSameCalendarDay(calendarOf(2026, Calendar.JANUARY, 15, hour = 1), now))
        assertFalse(isSameCalendarDay(calendarOf(2026, Calendar.JANUARY, 14, hour = 23), now))
        assertFalse(isSameCalendarDay(calendarOf(2025, Calendar.JANUARY, 15), now))
    }

    @Test
    fun `same calendar month respects year boundary`() {
        val now = calendarOf(2026, Calendar.MARCH, 10)
        assertTrue(isSameCalendarMonth(calendarOf(2026, Calendar.MARCH, 1), now))
        assertFalse(
            "same month/day but a year earlier must not count",
            isSameCalendarMonth(calendarOf(2025, Calendar.MARCH, 10), now)
        )
    }

    @Test
    fun `same calendar year`() {
        val now = calendarOf(2026, Calendar.JUNE, 1)
        assertTrue(isSameCalendarYear(calendarOf(2026, Calendar.DECEMBER, 31), now))
        assertFalse(isSameCalendarYear(calendarOf(2025, Calendar.DECEMBER, 31), now))
    }

    @Test
    fun `same calendar week`() {
        // Jan 15 2026 is a Thursday; Jan 12 (Monday) is the same ISO week, Jan 5 is not.
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        assertTrue(isSameCalendarWeek(calendarOf(2026, Calendar.JANUARY, 12), now))
        assertFalse(isSameCalendarWeek(calendarOf(2026, Calendar.JANUARY, 5), now))
    }

    // ── Rolling window (BudgetScreen's "Weekly") ────────────────────────────────────

    @Test
    fun `rolling window includes a transaction exactly at the boundary`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val sevenDaysAgo = calendarOf(2026, Calendar.JANUARY, 8)
        assertTrue(isWithinRollingWindow(now, sevenDaysAgo, 7L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun `rolling window excludes a transaction just past the boundary`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val eightDaysAgo = calendarOf(2026, Calendar.JANUARY, 7)
        assertFalse(isWithinRollingWindow(now, eightDaysAgo, 7L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun `rolling window excludes a future-dated transaction`() {
        // Regression: the original inline check only tested `diff <= window`, with no
        // lower bound, so a transaction dated after "now" (diff negative) would have
        // incorrectly counted as within the last 7 days.
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val tomorrow = calendarOf(2026, Calendar.JANUARY, 16)
        assertFalse(isWithinRollingWindow(now, tomorrow, 7L * 24 * 60 * 60 * 1000))
    }

    // ── Aggregates ───────────────────────────────────────────────────────────────────

    @Test
    fun `totalCredit and totalDebit sum only their own type`() {
        val transactions = listOf(tx("credit", 100.0), tx("debit", 40.0), tx("credit", 25.0), tx("debit", 10.0))
        assertEquals(125.0, totalCredit(transactions), 0.0001)
        assertEquals(50.0, totalDebit(transactions), 0.0001)
    }

    @Test
    fun `totalCredit and totalDebit are zero for an empty list`() {
        assertEquals(0.0, totalCredit(emptyList()), 0.0001)
        assertEquals(0.0, totalDebit(emptyList()), 0.0001)
    }

    @Test
    fun `savings rate is null when there is no income`() {
        assertNull(savingsRatePercent(income = 0.0, expense = 50.0))
    }

    @Test
    fun `savings rate can go negative when spending exceeds income`() {
        val rate = savingsRatePercent(income = 100.0, expense = 150.0)
        assertEquals(-50.0, rate!!, 0.0001)
    }

    @Test
    fun `savings rate for typical case`() {
        val rate = savingsRatePercent(income = 1000.0, expense = 400.0)
        assertEquals(60.0, rate!!, 0.0001)
    }

    // ── Budget progress ──────────────────────────────────────────────────────────────

    @Test
    fun `budget progress is a plain fraction under the limit`() {
        assertEquals(0.5f, budgetProgressFraction(spent = 250.0, limit = 500.0), 0.0001f)
    }

    @Test
    fun `budget progress clamps at 100 percent when over budget`() {
        assertEquals(1.0f, budgetProgressFraction(spent = 900.0, limit = 500.0), 0.0001f)
    }

    @Test
    fun `budget progress is zero for a non-positive limit instead of dividing by zero`() {
        assertEquals(0f, budgetProgressFraction(spent = 100.0, limit = 0.0), 0.0001f)
        assertEquals(0f, budgetProgressFraction(spent = 100.0, limit = -10.0), 0.0001f)
    }

    // ── Top spending categories (SummaryNotifier's "Top spending categories" section) ──

    private fun txCat(type: String, amount: Double, category: String): Transaction =
        Transaction(id = "id-$type-$amount-$category-${System.nanoTime()}", title = "t", amount = amount, date = "Jan 01, 2026", type = type, bankShortName = "CBE", category = category)

    @Test
    fun `topSpendingCategories sums debits per category and sorts largest first`() {
        val transactions = listOf(
            txCat("debit", 100.0, "Food"),
            txCat("debit", 50.0, "Food"),
            txCat("debit", 200.0, "Bills"),
            txCat("debit", 10.0, "Transport")
        )
        val top = topSpendingCategories(transactions, limit = 3)

        assertEquals(listOf("Bills" to 200.0, "Food" to 150.0, "Transport" to 10.0), top)
    }

    @Test
    fun `topSpendingCategories ignores credits`() {
        val transactions = listOf(txCat("credit", 500.0, "Salary"), txCat("debit", 20.0, "Food"))
        val top = topSpendingCategories(transactions)

        assertEquals(listOf("Food" to 20.0), top)
    }

    @Test
    fun `topSpendingCategories respects the limit`() {
        val transactions = listOf(
            txCat("debit", 30.0, "Food"),
            txCat("debit", 20.0, "Bills"),
            txCat("debit", 10.0, "Transport")
        )
        val top = topSpendingCategories(transactions, limit = 2)

        assertEquals(2, top.size)
        assertEquals("Food", top[0].first)
        assertEquals("Bills", top[1].first)
    }

    @Test
    fun `topSpendingCategories is empty for no debits`() {
        assertTrue(topSpendingCategories(listOf(txCat("credit", 100.0, "Salary"))).isEmpty())
        assertTrue(topSpendingCategories(emptyList()).isEmpty())
    }

    private fun txBank(type: String, amount: Double, bankShortName: String): Transaction =
        Transaction(id = "id-$type-$amount-$bankShortName-${System.nanoTime()}", title = "t", amount = amount, date = "Jan 01, 2026", type = type, bankShortName = bankShortName)

    @Test
    fun `spendingByBank sums debits per bank and sorts largest first`() {
        val transactions = listOf(
            txBank("debit", 100.0, "CBE"),
            txBank("debit", 50.0, "CBE"),
            txBank("debit", 200.0, "BOA"),
            txBank("debit", 10.0, "Awash")
        )
        val result = spendingByBank(transactions)

        assertEquals(listOf("BOA" to 200.0, "CBE" to 150.0, "Awash" to 10.0), result)
    }

    @Test
    fun `spendingByBank ignores credits and has no default limit`() {
        val transactions = (1..10).map { txBank("debit", it.toDouble(), "Bank$it") } +
            txBank("credit", 999.0, "CBE")
        val result = spendingByBank(transactions)

        assertEquals(10, result.size)
    }

    @Test
    fun `spendingByBank respects an explicit limit`() {
        val transactions = listOf(
            txBank("debit", 30.0, "CBE"),
            txBank("debit", 20.0, "BOA"),
            txBank("debit", 10.0, "Awash")
        )
        assertEquals(2, spendingByBank(transactions, limit = 2).size)
    }

    @Test
    fun `spendingByBank is empty for no debits`() {
        assertTrue(spendingByBank(listOf(txBank("credit", 100.0, "CBE"))).isEmpty())
        assertTrue(spendingByBank(emptyList()).isEmpty())
    }

    @Test
    fun `topIncomeCategories sums credits per category and sorts largest first`() {
        val transactions = listOf(
            txCat("credit", 5000.0, "Salary"),
            txCat("credit", 300.0, "Salary"),
            txCat("credit", 1000.0, "Freelance"),
            txCat("debit", 999.0, "Food") // ignored — not a credit
        )
        val result = topIncomeCategories(transactions)
        assertEquals(listOf("Salary" to 5300.0, "Freelance" to 1000.0), result)
    }

    @Test
    fun `topIncomeCategories is empty for no credits`() {
        assertTrue(topIncomeCategories(listOf(txCat("debit", 100.0, "Food"))).isEmpty())
        assertTrue(topIncomeCategories(emptyList()).isEmpty())
    }

    @Test
    fun `incomeByBank sums credits per bank and sorts largest first`() {
        val transactions = listOf(
            txBank("credit", 5000.0, "CBE"),
            txBank("credit", 300.0, "CBE"),
            txBank("credit", 1000.0, "BOA"),
            txBank("debit", 999.0, "CBE") // ignored — not a credit
        )
        val result = incomeByBank(transactions)
        assertEquals(listOf("CBE" to 5300.0, "BOA" to 1000.0), result)
    }

    @Test
    fun `incomeByBank is empty for no credits`() {
        assertTrue(incomeByBank(listOf(txBank("debit", 100.0, "CBE"))).isEmpty())
        assertTrue(incomeByBank(emptyList()).isEmpty())
    }

    // ── Trend buckets (Income vs Expense comparison) ────────────────────────────────

    @Test
    fun `weekly series buckets oldest to newest with correct per-week totals`() {
        // Jan 15 2026 is a Thursday, so the current week runs Mon Jan 12 - Sun Jan 18,
        // and the prior week runs Mon Jan 5 - Sun Jan 11.
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val transactions = listOf(
            txOn("credit", 100.0, calendarOf(2026, Calendar.JANUARY, 8)),
            txOn("debit", 40.0, calendarOf(2026, Calendar.JANUARY, 15))
        )
        val buckets = weeklyIncomeExpenseSeries(transactions, now, CalendarSystem.GREGORIAN, weekCount = 2)

        assertEquals(2, buckets.size)
        assertEquals(100.0, buckets[0].income, 0.0001)
        assertEquals(0.0, buckets[0].expense, 0.0001)
        assertEquals(0.0, buckets[1].income, 0.0001)
        assertEquals(40.0, buckets[1].expense, 0.0001)
        assertEquals("Jan 5", buckets[0].label)
        assertEquals("Jan 12", buckets[1].label)
    }

    @Test
    fun `weekly series in Ethiopian mode labels weeks with the Ethiopian date of the week start`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val buckets = weeklyIncomeExpenseSeries(emptyList(), now, CalendarSystem.ETHIOPIAN, weekCount = 1)
        val expectedStart = calendarOf(2026, Calendar.JANUARY, 12)
        val e = EthiopianCalendar.fromGregorian(expectedStart)
        assertEquals("${EthiopianCalendar.monthName(e.month).take(3)} ${e.day}", buckets.single().label)
    }

    @Test
    fun `monthly series in Gregorian mode buckets by calendar month`() {
        val now = calendarOf(2026, Calendar.MARCH, 10)
        val transactions = listOf(
            txOn("credit", 50.0, calendarOf(2026, Calendar.JANUARY, 20)),
            txOn("debit", 20.0, calendarOf(2026, Calendar.FEBRUARY, 5)),
            txOn("credit", 200.0, calendarOf(2026, Calendar.MARCH, 1))
        )
        val buckets = monthlyIncomeExpenseSeries(transactions, now, CalendarSystem.GREGORIAN, monthCount = 3)

        assertEquals(listOf("Jan", "Feb", "Mar"), buckets.map { it.label })
        assertEquals(50.0, buckets[0].income, 0.0001)
        assertEquals(20.0, buckets[1].expense, 0.0001)
        assertEquals(200.0, buckets[2].income, 0.0001)
        assertEquals("January 2026", buckets[0].fullLabel)
    }

    @Test
    fun `monthly series in Ethiopian mode buckets by real Ethiopian month boundaries`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val nowE = EthiopianCalendar.fromGregorian(now)
        val (currentMonthStart, _) = EthiopianCalendar.monthRange(nowE.year, nowE.month)
        var prevMonth = nowE.month - 1
        var prevYear = nowE.year
        if (prevMonth < 1) { prevMonth += 13; prevYear -= 1 }
        val (prevMonthStart, _) = EthiopianCalendar.monthRange(prevYear, prevMonth)

        val transactions = listOf(
            txOn("credit", 300.0, currentMonthStart),
            txOn("debit", 75.0, prevMonthStart)
        )
        val buckets = monthlyIncomeExpenseSeries(transactions, now, CalendarSystem.ETHIOPIAN, monthCount = 2)

        assertEquals(2, buckets.size)
        assertEquals(75.0, buckets[0].expense, 0.0001)
        assertEquals(300.0, buckets[1].income, 0.0001)
        assertEquals(EthiopianCalendar.monthName(prevMonth).take(3), buckets[0].label)
        assertEquals(EthiopianCalendar.monthName(nowE.month).take(3), buckets[1].label)
    }

    @Test
    fun `yearly series in Gregorian mode buckets by calendar year`() {
        val now = calendarOf(2026, Calendar.JUNE, 1)
        val transactions = listOf(
            txOn("credit", 10.0, calendarOf(2024, Calendar.MAY, 1)),
            txOn("credit", 20.0, calendarOf(2025, Calendar.MAY, 1)),
            txOn("credit", 30.0, calendarOf(2026, Calendar.MAY, 1))
        )
        val buckets = yearlyIncomeExpenseSeries(transactions, now, CalendarSystem.GREGORIAN, yearCount = 3)

        assertEquals(listOf("2024", "2025", "2026"), buckets.map { it.label })
        assertEquals(10.0, buckets[0].income, 0.0001)
        assertEquals(20.0, buckets[1].income, 0.0001)
        assertEquals(30.0, buckets[2].income, 0.0001)
    }

    @Test
    fun `yearly series in Ethiopian mode buckets by Meskerem-to-Pagume Ethiopian year`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val nowE = EthiopianCalendar.fromGregorian(now)
        val (thisYearStart, _) = EthiopianCalendar.yearRange(nowE.year)
        val (lastYearStart, _) = EthiopianCalendar.yearRange(nowE.year - 1)

        val transactions = listOf(
            txOn("debit", 15.0, lastYearStart),
            txOn("credit", 500.0, thisYearStart)
        )
        val buckets = yearlyIncomeExpenseSeries(transactions, now, CalendarSystem.ETHIOPIAN, yearCount = 2)

        assertEquals(listOf((nowE.year - 1).toString(), nowE.year.toString()), buckets.map { it.label })
        assertEquals(15.0, buckets[0].expense, 0.0001)
        assertEquals(500.0, buckets[1].income, 0.0001)
    }

    @Test
    fun `parseTransactionDate rejects malformed dates instead of throwing`() {
        assertNull(parseTransactionDate("not a date"))
    }

    // ── transactionTimestampMillis ──────────────────────────────────────────────────

    @Test
    fun `transactionTimestampMillis parses a combined date and time`() {
        val tx = txAt("a", "debit", 10.0, calendarOf(2026, Calendar.JANUARY, 15, hour = 14, minute = 30))
        assertEquals(true, transactionTimestampMillis(tx) != null)
    }

    @Test
    fun `transactionTimestampMillis returns null when time is blank`() {
        val tx = Transaction(id = "id", title = "t", amount = 10.0, date = "Jan 15, 2026", type = "debit", bankShortName = "CBE", time = "")
        assertNull(transactionTimestampMillis(tx))
    }

    // ── Net worth trend (Net Worth Overview) ─────────────────────────────────────────

    @Test
    fun `monthly net worth series is zero for every bucket when there are no transactions`() {
        val now = calendarOf(2026, Calendar.MARCH, 10)
        val points = monthlyNetWorthSeries(emptyList(), now, CalendarSystem.GREGORIAN, monthCount = 3)

        assertEquals(listOf("Jan", "Feb", "Mar"), points.map { it.label })
        assertTrue(points.all { it.netWorth == 0.0 })
    }

    @Test
    fun `monthly net worth series carries the latest known balance forward and sums across accounts`() {
        val now = calendarOf(2026, Calendar.MARCH, 10)
        val transactions = listOf(
            txAt("cbe-1", "credit", 100.0, calendarOf(2026, Calendar.JANUARY, 20), bankShortName = "CBE", balance = 1000.0),
            txAt("boa-1", "credit", 50.0, calendarOf(2026, Calendar.FEBRUARY, 5), bankShortName = "BOA", balance = 300.0),
            txAt("cbe-2", "debit", 40.0, calendarOf(2026, Calendar.MARCH, 1), bankShortName = "CBE", balance = 960.0)
        )
        val points = monthlyNetWorthSeries(transactions, now, CalendarSystem.GREGORIAN, monthCount = 3)

        // Jan end: only CBE has reported a balance yet.
        assertEquals(1000.0, points[0].netWorth, 0.0001)
        // Feb end: CBE unchanged since Jan, BOA now known too.
        assertEquals(1300.0, points[1].netWorth, 0.0001)
        // Mar end: CBE updated by the Mar 1 transaction, BOA still 300.
        assertEquals(1260.0, points[2].netWorth, 0.0001)
    }

    @Test
    fun `monthly net worth series ignores transactions without a recorded balance`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val transactions = listOf(txAt("a", "debit", 40.0, now, bankShortName = "CBE", balance = null))
        val points = monthlyNetWorthSeries(transactions, now, CalendarSystem.GREGORIAN, monthCount = 1)

        assertEquals(0.0, points.single().netWorth, 0.0001)
    }

    @Test
    fun `monthly net worth series in Ethiopian mode labels months by Ethiopian name`() {
        val now = calendarOf(2026, Calendar.JANUARY, 15)
        val nowE = EthiopianCalendar.fromGregorian(now)
        val points = monthlyNetWorthSeries(emptyList(), now, CalendarSystem.ETHIOPIAN, monthCount = 1)

        assertEquals(EthiopianCalendar.monthName(nowE.month).take(3), points.single().label)
    }

    // ── Duplicate transaction detection (Duplicate Check) ────────────────────────────

    @Test
    fun `findPotentialDuplicates groups same bank type amount and account within the time window`() {
        val base = calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 0)
        val resend = calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 4)
        val t1 = txAt("a", "debit", 250.0, base, bankShortName = "CBE", accountSuffix = "1234")
        val t2 = txAt("b", "debit", 250.0, resend, bankShortName = "CBE", accountSuffix = "1234")

        val groups = findPotentialDuplicates(listOf(t1, t2), windowMillis = 10 * 60 * 1000L)

        assertEquals(1, groups.size)
        assertEquals(listOf("a", "b"), groups.single().map { it.id })
    }

    @Test
    fun `findPotentialDuplicates does not group transactions outside the time window`() {
        val t1 = txAt("a", "debit", 250.0, calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 0), bankShortName = "CBE")
        val t2 = txAt("b", "debit", 250.0, calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 30), bankShortName = "CBE")

        assertTrue(findPotentialDuplicates(listOf(t1, t2), windowMillis = 10 * 60 * 1000L).isEmpty())
    }

    @Test
    fun `findPotentialDuplicates does not group transactions with different amount type or bank`() {
        val at = calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 0)
        val different = listOf(
            txAt("a", "debit", 250.0, at, bankShortName = "CBE"),
            txAt("b", "debit", 300.0, at, bankShortName = "CBE"),
            txAt("c", "credit", 250.0, at, bankShortName = "CBE"),
            txAt("d", "debit", 250.0, at, bankShortName = "BOA")
        )

        assertTrue(findPotentialDuplicates(different).isEmpty())
    }

    @Test
    fun `findPotentialDuplicates sorts groups by amount descending`() {
        val at = calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 0)
        val near = calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 1)
        val transactions = listOf(
            txAt("small-1", "debit", 50.0, at, bankShortName = "CBE"),
            txAt("small-2", "debit", 50.0, near, bankShortName = "CBE"),
            txAt("big-1", "debit", 500.0, at, bankShortName = "BOA"),
            txAt("big-2", "debit", 500.0, near, bankShortName = "BOA")
        )

        val groups = findPotentialDuplicates(transactions)

        assertEquals(2, groups.size)
        assertEquals(500.0, groups[0].first().amount, 0.0001)
        assertEquals(50.0, groups[1].first().amount, 0.0001)
    }

    @Test
    fun `findPotentialDuplicates ignores a lone transaction`() {
        val t1 = txAt("a", "debit", 250.0, calendarOf(2026, Calendar.JANUARY, 15, hour = 10, minute = 0), bankShortName = "CBE")
        assertTrue(findPotentialDuplicates(listOf(t1)).isEmpty())
    }

    @Test
    fun `formatDisplayDate reformats to MM DD YYYY`() {
        assertEquals("01/15/2026", formatDisplayDate("Jan 15, 2026", "MM/DD/YYYY"))
    }

    @Test
    fun `formatDisplayDate reformats to DD MM YYYY`() {
        assertEquals("15/01/2026", formatDisplayDate("Jan 15, 2026", "DD/MM/YYYY"))
    }

    @Test
    fun `formatDisplayDate reformats to YYYY-MM-DD`() {
        assertEquals("2026-01-15", formatDisplayDate("Jan 15, 2026", "YYYY-MM-DD"))
    }

    @Test
    fun `formatDisplayDate falls back to the original string for an unparseable date`() {
        assertEquals("not-a-date", formatDisplayDate("not-a-date", "MM/DD/YYYY"))
    }

    @Test
    fun `formatDisplayDate falls back to the original string for an unknown format setting`() {
        assertEquals("Jan 15, 2026", formatDisplayDate("Jan 15, 2026", "Some Unknown Setting"))
    }

    // ── Daily Summary & Locale Independence Tests ────────────────────────────────────

    @Test
    fun `parseTransactionDate succeeds under non-US default locale`() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            val parsed = parseTransactionDate("Sep 03, 2026")
            assertTrue(parsed != null)
            assertEquals(2026, parsed!!.get(Calendar.YEAR))
            assertEquals(Calendar.SEPTEMBER, parsed.get(Calendar.MONTH))
            assertEquals(3, parsed.get(Calendar.DAY_OF_MONTH))
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `daily summary filtering correctly excludes transactions older than sinceMillis`() {
        val nowMillis = calendarOf(2026, Calendar.SEPTEMBER, 3, hour = 20).timeInMillis
        val sinceMillis = nowMillis - (24 * 60 * 60 * 1000L) // 24h ago

        val todayTx = txAt("1", "debit", 100.0, calendarOf(2026, Calendar.SEPTEMBER, 3, hour = 10))
        val oldTx = txAt("2", "debit", 500.0, calendarOf(2026, Calendar.AUGUST, 20, hour = 10))

        val transactions = listOf(todayTx, oldTx)

        val filtered = transactions.filter { tx ->
            val txMillis = transactionTimestampMillis(tx) ?: parseTransactionDate(tx.date)?.timeInMillis
            txMillis != null && txMillis >= sinceMillis
        }

        assertEquals(1, filtered.size)
        assertEquals("1", filtered.single().id)
    }

    @Test
    fun `daily summary filtering returns empty list when no transactions occur in 24h window`() {
        val nowMillis = calendarOf(2026, Calendar.SEPTEMBER, 3, hour = 20).timeInMillis
        val sinceMillis = nowMillis - (24 * 60 * 60 * 1000L) // 24h ago

        val oldTx = txAt("old", "debit", 500.0, calendarOf(2026, Calendar.AUGUST, 20, hour = 10))
        val transactions = listOf(oldTx)

        val filtered = transactions.filter { tx ->
            val txMillis = transactionTimestampMillis(tx) ?: parseTransactionDate(tx.date)?.timeInMillis
            txMillis != null && txMillis >= sinceMillis
        }

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `getTotalBalance correctly subtracts hidden account balances`() {
        val acc1 = Account("acc1", "cbe", "1000", "Savings", 50000.0, "ETB", AccountType.SAVINGS)
        val acc2 = Account("acc2", "cbe", "2000", "Salary", 20000.0, "ETB", AccountType.SALARY)
        val bank = Bank(
            id = "cbe",
            name = "Commercial Bank of Ethiopia",
            shortName = "CBE",
            accounts = listOf(acc1, acc2),
            colorFrom = "#000000",
            colorTo = "#111111",
            logoText = "CBE"
        )

        val fullTotal = Data.getTotalBalance(listOf(bank))
        assertEquals(70000.0, fullTotal, 0.01)

        val hiddenTotal = Data.getTotalBalance(listOf(bank), setOf("acc2"))
        assertEquals(50000.0, hiddenTotal, 0.01)

        val bankTotal = Data.getBankTotal(bank, setOf("acc1"))
        assertEquals(20000.0, bankTotal, 0.01)
    }

    // ── Monthly growth and Balance trend tests ─────────────────────────────────────

    @Test
    fun `calculateMonthlyGrowth returns 0 percent when empty`() {
        val growth = calculateMonthlyGrowth(emptyList())
        assertEquals(0.0, growth.percentage, 0.01)
        assertEquals(true, growth.isPositive)
        assertEquals("0.0% this month", growth.formattedText)
    }

    @Test
    fun `calculateBalanceTrend returns valid points and chronological order`() {
        val txs = listOf(
            Transaction("1", "kaldi", 100.0, "Jan 01, 2026", "debit", "CBE", balance = 900.0),
            Transaction("2", "salary", 1000.0, "Jan 02, 2026", "credit", "CBE", balance = 1900.0)
        )
        val trend = calculateBalanceTrend(txs, 1900.0)
        assertTrue(trend.size >= 2)
        // Earliest balance was 900, latest was 1900
        assertEquals(900f, trend.first(), 0.01f)
    }

    @Test
    fun `formatEthiopianTime correctly converts 24h to Ethiopian traditional 12h clock`() {
        val cal8pm = Calendar.getInstance(EthiopianCalendar.ETHIOPIA_TIME_ZONE).apply {
            set(Calendar.HOUR_OF_DAY, 20) // 8:00 PM EAT
            set(Calendar.MINUTE, 0)
        }
        val ethTime8pm = EthiopianCalendar.formatEthiopianTime(cal8pm, inAmharic = true)
        assertEquals("2:00 ምሽት", ethTime8pm)

        val cal6am = Calendar.getInstance(EthiopianCalendar.ETHIOPIA_TIME_ZONE).apply {
            set(Calendar.HOUR_OF_DAY, 6) // 6:00 AM EAT
            set(Calendar.MINUTE, 30)
        }
        val ethTime6am = EthiopianCalendar.formatEthiopianTime(cal6am, inAmharic = true)
        assertEquals("12:30 ጠዋት", ethTime6am)

        val cal12pm = Calendar.getInstance(EthiopianCalendar.ETHIOPIA_TIME_ZONE).apply {
            set(Calendar.HOUR_OF_DAY, 12) // 12:00 PM EAT
            set(Calendar.MINUTE, 15)
        }
        val ethTime12pm = EthiopianCalendar.formatEthiopianTime(cal12pm, inAmharic = true)
        assertEquals("6:15 ቀን", ethTime12pm)
    }

    @Test
    fun `parseTransactionDate handles multiple date formats`() {
        val usCal = parseTransactionDate("Sep 29, 2026")
        assertNotNull(usCal)
        assertEquals(2026, usCal!!.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, usCal.get(Calendar.MONTH))
        assertEquals(29, usCal.get(Calendar.DAY_OF_MONTH))

        val isoCal = parseTransactionDate("2026-09-29")
        assertNotNull(isoCal)
        assertEquals(2026, isoCal!!.get(Calendar.YEAR))
        assertEquals(29, isoCal.get(Calendar.DAY_OF_MONTH))
    }
}
