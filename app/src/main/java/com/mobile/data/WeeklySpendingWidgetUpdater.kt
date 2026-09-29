package com.mobile.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.mobile.MainActivity
import com.mobile.R
import com.mobile.data.db.AppDatabase
import com.mobile.data.db.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WeeklySpendingWidgetUpdater {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("WidgetUpdater", "Unhandled widget coroutine exception", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    private val dayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    fun updateAllWidgets(context: Context) {
        try {
            SettingsRepository.init(context)
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            if (!SettingsRepository.widgetsEnabled.value) return

            // 1. Style 1: Digest Provider
            val digestComp = ComponentName(context, WeeklySpendingWidgetProvider::class.java)
            appWidgetManager.getAppWidgetIds(digestComp)?.let { ids ->
                if (ids.isNotEmpty()) updateWidgets(context, appWidgetManager, ids)
            }

        // 2. Style 2: Net Worth Provider
        val netWorthComp = ComponentName(context, NetWorthWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(netWorthComp)?.let { ids ->
            if (ids.isNotEmpty()) updateNetWorthWidgets(context, appWidgetManager, ids)
        }

        // 3. Style 3: Quick Tracker Provider
        val quickComp = ComponentName(context, QuickTrackerWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(quickComp)?.let { ids ->
            if (ids.isNotEmpty()) updateQuickTrackerWidgets(context, appWidgetManager, ids)
        }

        // 4. Style 4: Multi-Bank Provider
        val multiComp = ComponentName(context, MultiBankWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(multiComp)?.let { ids ->
            if (ids.isNotEmpty()) updateMultiBankWidgets(context, appWidgetManager, ids)
        }

        // 5. Style 5: Budget Pulse Provider
        val pulseComp = ComponentName(context, BudgetPulseWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(pulseComp)?.let { ids ->
            if (ids.isNotEmpty()) updateBudgetPulseWidgets(context, appWidgetManager, ids)
        }
        } catch (t: Throwable) {
            Log.e("WidgetUpdater", "Error updating all widgets", t)
        }
    }

    private fun pendingOpenAppIntent(context: Context, requestCode: Int): PendingIntent {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Style 1: Daily Digest & Weekly Budget (4x2) ────────────────────────────────────
    fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val transactions = db.transactionDao().observeAll().first().map { it.toDomain() }
                val budgets = db.budgetDao().observeAll().first().map { it.toDomain() }

                val now = Calendar.getInstance()
                val todayTxs = transactions.filter { tx ->
                    if (tx.type != "debit") return@filter false
                    parseTransactionDate(tx.date)?.let { isSameCalendarDay(it, now) } ?: false
                }
                val todayOutflow = todayTxs.sumOf { it.amount }

                val weekWindowMillis = 7L * 24 * 60 * 60 * 1000L
                val weeklyTxs = transactions.filter { tx ->
                    if (tx.type != "debit") return@filter false
                    parseTransactionDate(tx.date)?.let { isWithinRollingWindow(now, it, weekWindowMillis) } ?: false
                }
                val weekOutflow = weeklyTxs.sumOf { it.amount }

                val weeklyBudget = budgets.find { it.period.equals("Weekly", ignoreCase = true) && it.category == null }?.limit
                    ?: 3500.0

                val progressFraction = budgetProgressFraction(weekOutflow, weeklyBudget)
                val progressPercent = (progressFraction * 100).toInt().coerceIn(0, 100)

                val (statusText, statusColor) = when {
                    progressFraction <= 0.5f -> "Safe Zone 😎" to Color.parseColor("#34D399")
                    progressFraction <= 0.8f -> "On Track 🎯" to Color.parseColor("#60A5FA")
                    progressFraction <= 1.0f -> "Approaching Limit ⚠️" to Color.parseColor("#FBBF24")
                    else -> "Over Budget 🚨" to Color.parseColor("#F87171")
                }

                val dateLabel = "This Week · ${dayFormat.format(now.time)}"

                appWidgetIds.forEach { widgetId ->
                    try {
                        val views = RemoteViews(appContext.packageName, R.layout.widget_weekly_spending_digest)

                        views.setTextViewText(R.id.widget_today_outflow, "ETB ${Data.formatBalance(todayOutflow)}")
                        views.setTextViewText(R.id.widget_week_outflow, "ETB ${Data.formatBalance(weekOutflow)}")
                        views.setTextViewText(
                            R.id.widget_budget_label,
                            "Budget: ETB ${Data.formatBalance(weekOutflow)} / ${Data.formatBalance(weeklyBudget)}"
                        )
                        views.setTextViewText(R.id.widget_budget_status, statusText)
                        views.setTextColor(R.id.widget_budget_status, statusColor)
                        views.setTextViewText(R.id.widget_date_label, dateLabel)
                        views.setProgressBar(R.id.widget_budget_progress, 100, progressPercent, false)

                        views.setOnClickPendingIntent(R.id.widget_root, pendingOpenAppIntent(appContext, widgetId))

                        val refreshIntent = Intent(appContext, WeeklySpendingWidgetProvider::class.java).apply {
                            action = WeeklySpendingWidgetProvider.ACTION_REFRESH_WIDGET
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            appContext, widgetId, refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                        appWidgetManager.updateAppWidget(widgetId, views)
                    } catch (t: Throwable) {
                        Log.e("WidgetUpdater", "Failed to update widget $widgetId", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e("WidgetUpdater", "Error in updateWidgets", t)
            }
        }
    }

    // ── Style 2: Net Worth & Financial Health (2x2) ──────────────────────────────────
    private fun updateNetWorthWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val bankEntities = db.bankDao().observeAllWithAccounts().first()
                val banks = bankEntities.map { it.toDomain() }
                val transactions = db.transactionDao().observeAll().first().map { it.toDomain() }

                val netWorth = Data.getTotalBalance(banks)
                val bankCount = banks.size
                val accountCount = banks.sumOf { it.accounts.size }

                val now = Calendar.getInstance()
                val monthTxs = transactions.filter { tx ->
                    parseTransactionDate(tx.date)?.let { isSameCalendarMonth(it, now) } ?: false
                }
                val monthIncome = monthTxs.filter { it.type == "credit" }.sumOf { it.amount }
                val monthExpense = monthTxs.filter { it.type == "debit" }.sumOf { it.amount }
                val monthNet = monthIncome - monthExpense
                val netSign = if (monthNet >= 0) "+" else "-"

                appWidgetIds.forEach { widgetId ->
                    try {
                        val views = RemoteViews(appContext.packageName, R.layout.widget_net_worth_card)
                        views.setTextViewText(R.id.widget_net_worth_value, "ETB ${Data.formatBalance(netWorth)}")
                        views.setTextViewText(R.id.widget_bank_stats, "$bankCount Synced Banks · $accountCount Accounts")
                        views.setTextViewText(R.id.widget_cashflow_text, "$netSign ETB ${Data.formatBalance(kotlin.math.abs(monthNet))}")
                        views.setTextColor(R.id.widget_cashflow_text, if (monthNet >= 0) Color.parseColor("#34D399") else Color.parseColor("#F87171"))

                        views.setOnClickPendingIntent(R.id.widget_root, pendingOpenAppIntent(appContext, widgetId + 2000))

                        val refreshIntent = Intent(appContext, NetWorthWidgetProvider::class.java).apply {
                            action = NetWorthWidgetProvider.ACTION_REFRESH_WIDGET
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            appContext, widgetId, refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                        appWidgetManager.updateAppWidget(widgetId, views)
                    } catch (t: Throwable) {
                        Log.e("WidgetUpdater", "Failed to update net worth widget $widgetId", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e("WidgetUpdater", "Error in updateNetWorthWidgets", t)
            }
        }
    }

    // ── Style 3: Quick Action & Transaction Tracker (4x1) ────────────────────────────
    private fun updateQuickTrackerWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val transactions = db.transactionDao().observeAll().first().map { it.toDomain() }
                val latestTx = transactions.firstOrNull()

                appWidgetIds.forEach { widgetId ->
                    try {
                        val views = RemoteViews(appContext.packageName, R.layout.widget_quick_tracker)
                        if (latestTx != null) {
                            views.setTextViewText(R.id.widget_tx_title, latestTx.title)
                            views.setTextViewText(R.id.widget_tx_sub, "${latestTx.bankShortName} · ${latestTx.date}")
                            views.setTextViewText(R.id.widget_tx_amount, "ETB ${Data.formatBalance(latestTx.amount)}")
                            val isCredit = latestTx.type == "credit"
                            views.setTextColor(R.id.widget_tx_amount, if (isCredit) Color.parseColor("#34D399") else Color.parseColor("#F87171"))
                        } else {
                            views.setTextViewText(R.id.widget_tx_title, "No Activity")
                            views.setTextViewText(R.id.widget_tx_sub, "Sync banks to see live transactions")
                            views.setTextViewText(R.id.widget_tx_amount, "ETB 0.00")
                        }

                        views.setOnClickPendingIntent(R.id.widget_root, pendingOpenAppIntent(appContext, widgetId + 3000))

                        val refreshIntent = Intent(appContext, QuickTrackerWidgetProvider::class.java).apply {
                            action = QuickTrackerWidgetProvider.ACTION_REFRESH_WIDGET
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            appContext, widgetId, refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                        appWidgetManager.updateAppWidget(widgetId, views)
                    } catch (t: Throwable) {
                        Log.e("WidgetUpdater", "Failed to update quick tracker widget $widgetId", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e("WidgetUpdater", "Error in updateQuickTrackerWidgets", t)
            }
        }
    }

    // ── Style 4: Multi-Bank Account Balances (4x3 Grid) ──────────────────────────────
    private fun updateMultiBankWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val bankEntities = db.bankDao().observeAllWithAccounts().first()
                val banks = bankEntities.map { it.toDomain() }
                val totalBalance = Data.getTotalBalance(banks)

                appWidgetIds.forEach { widgetId ->
                    try {
                        val views = RemoteViews(appContext.packageName, R.layout.widget_multi_bank)
                        views.setTextViewText(R.id.widget_total_balance_text, "Total: ETB ${Data.formatBalance(totalBalance)}")

                        val bank1 = banks.getOrNull(0)
                        val bank2 = banks.getOrNull(1)
                        val bank3 = banks.getOrNull(2)
                        val bank4 = banks.getOrNull(3)

                        views.setTextViewText(R.id.widget_bank_1_name, bank1?.shortName ?: "CBE")
                        views.setTextViewText(R.id.widget_bank_1_balance, "ETB ${Data.formatBalance(bank1?.let { Data.getBankTotal(it) } ?: 0.0)}")

                        views.setTextViewText(R.id.widget_bank_2_name, bank2?.shortName ?: "Telebirr")
                        views.setTextViewText(R.id.widget_bank_2_balance, "ETB ${Data.formatBalance(bank2?.let { Data.getBankTotal(it) } ?: 0.0)}")

                        views.setTextViewText(R.id.widget_bank_3_name, bank3?.shortName ?: "BOA")
                        views.setTextViewText(R.id.widget_bank_3_balance, "ETB ${Data.formatBalance(bank3?.let { Data.getBankTotal(it) } ?: 0.0)}")

                        views.setTextViewText(R.id.widget_bank_4_name, bank4?.shortName ?: "Dashen")
                        views.setTextViewText(R.id.widget_bank_4_balance, "ETB ${Data.formatBalance(bank4?.let { Data.getBankTotal(it) } ?: 0.0)}")

                        views.setOnClickPendingIntent(R.id.widget_root, pendingOpenAppIntent(appContext, widgetId + 4000))

                        val refreshIntent = Intent(appContext, MultiBankWidgetProvider::class.java).apply {
                            action = MultiBankWidgetProvider.ACTION_REFRESH_WIDGET
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            appContext, widgetId, refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                        appWidgetManager.updateAppWidget(widgetId, views)
                    } catch (t: Throwable) {
                        Log.e("WidgetUpdater", "Failed to update multi bank widget $widgetId", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e("WidgetUpdater", "Error in updateMultiBankWidgets", t)
            }
        }
    }

    // ── Style 5: Budget Pulse & Daily Allowance (2x1 Capsule) ────────────────────────
    private fun updateBudgetPulseWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val transactions = db.transactionDao().observeAll().first().map { it.toDomain() }
                val budgets = db.budgetDao().observeAll().first().map { it.toDomain() }

                val now = Calendar.getInstance()
                val todayTxs = transactions.filter { tx ->
                    if (tx.type != "debit") return@filter false
                    parseTransactionDate(tx.date)?.let { isSameCalendarDay(it, now) } ?: false
                }
                val todayOutflow = todayTxs.sumOf { it.amount }
                val dailyLimit = budgets.find { it.period.equals("Daily", ignoreCase = true) && it.category == null }?.limit
                    ?: 500.0

                val remainingAllowance = (dailyLimit - todayOutflow).coerceAtLeast(0.0)
                val statusText = if (remainingAllowance > 0) "Safe 😎" else "Limit Reached 🚨"
                val statusColor = if (remainingAllowance > 0) Color.parseColor("#34D399") else Color.parseColor("#F87171")

                appWidgetIds.forEach { widgetId ->
                    try {
                        val views = RemoteViews(appContext.packageName, R.layout.widget_budget_pulse)
                        views.setTextViewText(R.id.widget_allowance_text, "ETB ${Data.formatBalance(remainingAllowance)} Left")
                        views.setTextViewText(R.id.widget_pulse_badge, statusText)
                        views.setTextColor(R.id.widget_pulse_badge, statusColor)

                        views.setOnClickPendingIntent(R.id.widget_root, pendingOpenAppIntent(appContext, widgetId + 5000))
                        appWidgetManager.updateAppWidget(widgetId, views)
                    } catch (t: Throwable) {
                        Log.e("WidgetUpdater", "Failed to update budget pulse widget $widgetId", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e("WidgetUpdater", "Error in updateBudgetPulseWidgets", t)
            }
        }
    }
}
