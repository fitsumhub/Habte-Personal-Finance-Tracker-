package com.mobile.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobile.data.db.AppDatabase
import com.mobile.data.db.toDomain
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Fires on the alarm armed by PaymentReminderScheduler. Reads the reminder straight from
 * Room — this can run in a fresh process after the app was fully killed, so it can't rely
 * on PaymentReminderRepository's in-memory StateFlow being populated yet. Notifies unless
 * this due date was already marked paid, then either re-arms for the next occurrence
 * (recurring reminders) or disables itself (one-time reminders, mirroring how a one-time
 * alarm clock alarm turns itself off after ringing).
 */
class PaymentReminderReceiver : BroadcastReceiver() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("PaymentReminderReceiver", "Unhandled exception in payment reminder receiver", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val appContext = context.applicationContext ?: return

        val reminderId = intent.getLongExtra(PaymentReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return

        try {
            SettingsRepository.init(appContext)
            PaymentReminderRepository.init(appContext)
        } catch (t: Throwable) {
            android.util.Log.e("PaymentReminderReceiver", "Failed to init repositories", t)
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(appContext).paymentReminderDao()
                val entity = dao.getById(reminderId) ?: return@launch
                if (!entity.enabled) return@launch
                val reminder = entity.toDomain()

                if (reminder.lastPaidCycle != cycleKeyFor(reminder.dueDateMillis)) {
                    PaymentReminderNotifier.notify(appContext, reminder)
                }

                if (reminder.repeat == ReminderRepeat.ONE_TIME) {
                    dao.setEnabled(reminderId, false)
                } else {
                    val anchor = Calendar.getInstance().apply { timeInMillis = reminder.dueDateMillis }
                    val next = PaymentReminderScheduler.nextOccurrence(anchor, reminder.repeat)
                    dao.updateDueDate(reminderId, next.timeInMillis)
                    PaymentReminderScheduler.scheduleFor(appContext, reminderId, next.timeInMillis, reminder.daysBefore)
                }
            } catch (t: Throwable) {
                android.util.Log.e("PaymentReminderReceiver", "Error handling payment reminder $reminderId", t)
            } finally {
                kotlin.runCatching { pendingResult.finish() }
            }
        }
    }
}
