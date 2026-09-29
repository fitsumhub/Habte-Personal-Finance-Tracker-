package com.mobile.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Automatically schedules the periodic "spending summary" notification via AlarmManager.
 * Calculates exact evening briefing times (e.g., 8:00 PM daily) and re-arms automatically.
 */
object SummaryScheduler {
    const val EXTRA_FREQUENCY = "extra_frequency"

    val ALL_FREQUENCIES = listOf("Daily", "Weekly", "Monthly", "6 Months", "Yearly")

    private const val BASE_REQUEST_CODE = 9000

    private fun normalize(frequency: String): String = when (frequency.trim()) {
        "Every 12 Hours" -> "Daily"
        "Every 15 Days" -> "Monthly"
        "6-Months", "6 Months" -> "6 Months"
        "Annual", "Yearly" -> "Yearly"
        else -> frequency.trim()
    }

    /** Also used by SummaryNotifier so each frequency's notification has a stable, distinct ID. */
    fun idFor(frequency: String): Int {
        val normalized = normalize(frequency)
        val index = ALL_FREQUENCIES.indexOf(normalized)
        return BASE_REQUEST_CODE + if (index >= 0) index + 1 else 1
    }

    fun intervalMillis(frequency: String): Long? = when (normalize(frequency)) {
        "Daily" -> 24 * 60 * 60 * 1000L
        "Weekly" -> 7 * 24 * 60 * 60 * 1000L
        "Monthly" -> 30 * 24 * 60 * 60 * 1000L
        "6 Months" -> 180 * 24 * 60 * 60 * 1000L
        "Yearly" -> 365 * 24 * 60 * 60 * 1000L
        else -> null
    }

    /** Calculates the next calendar trigger time based on Ethiopian Time (8:00 PM EAT / 2:00 ምሽት evening digest). */
    fun nextScheduledTimeMillis(frequency: String): Long {
        val tz = EthiopianCalendar.ETHIOPIA_TIME_ZONE
        val now = Calendar.getInstance(tz)
        val target = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 20) // 8:00 PM EAT (2:00 ምሽት Ethiopian Evening)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (normalize(frequency)) {
            "Daily" -> {
                if (target.timeInMillis <= now.timeInMillis) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "Weekly" -> {
                target.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                if (target.timeInMillis <= now.timeInMillis) {
                    target.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            "Monthly" -> {
                target.set(Calendar.DAY_OF_MONTH, target.getActualMaximum(Calendar.DAY_OF_MONTH))
                if (target.timeInMillis <= now.timeInMillis) {
                    target.add(Calendar.MONTH, 1)
                    target.set(Calendar.DAY_OF_MONTH, target.getActualMaximum(Calendar.DAY_OF_MONTH))
                }
            }
            "6 Months" -> {
                if (target.timeInMillis <= now.timeInMillis) {
                    target.add(Calendar.MONTH, 6)
                }
            }
            "Yearly" -> {
                target.set(Calendar.MONTH, Calendar.DECEMBER)
                target.set(Calendar.DAY_OF_MONTH, 31)
                if (target.timeInMillis <= now.timeInMillis) {
                    target.add(Calendar.YEAR, 1)
                }
            }
            else -> {
                if (target.timeInMillis <= now.timeInMillis) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        return target.timeInMillis
    }

    private fun pendingIntent(context: Context, frequency: String, createIfMissing: Boolean): PendingIntent? {
        val intent = Intent(context, SummaryAlarmReceiver::class.java).putExtra(EXTRA_FREQUENCY, frequency)
        val flags = (if (createIfMissing) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, idFor(frequency), intent, flags)
    }

    /** Cancels [frequency]'s alarm, if any. */
    fun cancel(context: Context, frequency: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            pendingIntent(context, frequency, createIfMissing = false)?.let { alarmManager.cancel(it) }
        } catch (t: Throwable) {
            android.util.Log.w("SummaryScheduler", "Failed to cancel alarm for $frequency", t)
        }
    }

    /** Automatically arms an exact evening alarm for [frequency] and starts recurring schedule. */
    fun schedule(context: Context, frequency: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancel(context, frequency)
        val pending = pendingIntent(context, frequency, createIfMissing = true) ?: return
        val triggerAt = nextScheduledTimeMillis(frequency)
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pending
            )
        } catch (e: SecurityException) {
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            } catch (t: Throwable) {
                android.util.Log.e("SummaryScheduler", "Failed to schedule alarm for $frequency", t)
            }
        } catch (t: Throwable) {
            android.util.Log.e("SummaryScheduler", "Failed to schedule alarm for $frequency", t)
        }
    }

    /** Immediately triggers a summary notification for the given frequency (0s delay, instant delivery). */
    fun triggerNow(context: Context, frequency: String = "Daily") {
        val intent = Intent(context, SummaryAlarmReceiver::class.java).apply {
            putExtra(EXTRA_FREQUENCY, frequency)
        }
        context.sendBroadcast(intent)
    }

    /** Enables or disables one frequency without touching any of the others. */
    fun toggle(context: Context, frequency: String, enabled: Boolean) {
        if (enabled) schedule(context, frequency) else cancel(context, frequency)
    }

    /**
     * Reconciles all alarms against [activeFrequencies] from scratch — used after a
     * reboot (every alarm is guaranteed gone, so a fresh schedule is always correct)
     * and to clean up any frequency that's no longer selected.
     */
    fun rescheduleAll(context: Context, activeFrequencies: Set<String>) {
        ALL_FREQUENCIES.forEach { frequency ->
            if (frequency in activeFrequencies) schedule(context, frequency) else cancel(context, frequency)
        }
    }

    /** Schedules only the frequencies in [activeFrequencies] that aren't already running — avoids resetting an existing cadence on every app open. */
    fun ensureScheduled(context: Context, activeFrequencies: Set<String>) {
        activeFrequencies.forEach { frequency ->
            if (pendingIntent(context, frequency, createIfMissing = false) == null) {
                schedule(context, frequency)
            }
        }
    }
}
