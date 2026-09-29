package com.mobile.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mobile.MainActivity
import com.mobile.R
import kotlin.math.abs

/** Posts the periodic income/expense summary notification (see SummaryScheduler/SummaryAlarmReceiver). */
object SummaryNotifier {
    private const val CHANNEL_ID = "summary_alerts"
    private const val ACCENT_COLOR = 0xFF4F46E5.toInt()

    private fun periodLabel(frequency: String): String = when (frequency.trim()) {
        "Every 12 Hours", "Daily" -> "Daily"
        "Weekly" -> "Weekly"
        "Monthly" -> "Monthly"
        "6 Months", "6-Months" -> "6-Month"
        "Yearly", "Annual" -> "Yearly"
        else -> "Spending"
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Spending Summaries", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Periodic income vs. expense summary notifications"
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    fun notify(context: Context, frequency: String, transactions: List<Transaction>) {
        if (!SettingsRepository.notificationsEnabled.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return

        ensureChannel(context)
        val notificationId = SummaryScheduler.idFor(frequency)

        val ethiopiaCal = java.util.Calendar.getInstance(EthiopianCalendar.ETHIOPIA_TIME_ZONE)
        val ethTimeStr = EthiopianCalendar.formatEthiopianTime(ethiopiaCal, inAmharic = true)
        val ethDateStr = EthiopianCalendar.formatFull(ethiopiaCal)
        val isEthiopianCalendar = SettingsRepository.calendarSystem.value == "Ethiopian"

        val title = if (frequency.equals("Daily", ignoreCase = true)) {
            if (isEthiopianCalendar) "ሀብቴ የዕለት ማጠቃለያ ($ethTimeStr)" else "Habte Daily Summary ($ethTimeStr)"
        } else {
            "Habte ${periodLabel(frequency)} Summary"
        }

        val (summaryText, expandedText) = if (transactions.isEmpty()) {
            Pair(
                "No spending or transactions recorded for this period.",
                "Your account is all clear. No debit or credit activities logged today ($ethDateStr • $ethTimeStr EAT)."
            )
        } else {
            val income = transactions.filter { it.type == "credit" }.sumOf { it.amount }
            val expense = transactions.filter { it.type == "debit" }.sumOf { it.amount }
            val net = income - expense
            val netSign = if (net >= 0) "+" else "-"
            val txCount = transactions.size

            val summary = "In: ETB ${Data.formatBalance(income)} • Out: ETB ${Data.formatBalance(expense)} • " +
                "Net: $netSign${Data.formatBalance(abs(net))}"

            val topCategories = topSpendingCategories(transactions, limit = 3)
            val categoryLines = topCategories.joinToString("\n") { (category, amount) ->
                val label = if (category == "Other") "Uncategorized" else category
                "• $label — ETB ${Data.formatBalance(amount)}"
            }
            val expanded = buildString {
                append(summary)
                append("\n")
                append("$txCount transaction${if (txCount == 1) "" else "s"} • $ethDateStr ($ethTimeStr EAT)")
                if (categoryLines.isNotEmpty()) {
                    append("\n\nTop spending categories:\n")
                    append(categoryLines)
                }
            }
            Pair(summary, expanded)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(NotificationIcons.build(ACCENT_COLOR, NotificationGlyph.DOT))
            .setColor(ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (t: Throwable) {
            android.util.Log.w("SummaryNotifier", "Failed to post summary notification", t)
        }
    }
}
