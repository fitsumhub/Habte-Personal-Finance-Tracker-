package com.mobile.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.mobile.data.db.AppDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the "Mark as Paid" action button on a PaymentReminderNotifier notification —
 * lets a reminder be dismissed for its current cycle without opening the app, the same
 * quick-action pattern as a calendar or alarm app. Reads straight from Room rather than
 * going through PaymentReminderRepository, since (like PaymentReminderReceiver) this can
 * run in a fresh process after the app was fully killed.
 */
class PaymentReminderActionReceiver : BroadcastReceiver() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("PaymentReminderAction", "Unhandled exception in payment reminder action receiver", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val appContext = context.applicationContext ?: return

        val reminderId = intent.getLongExtra(PaymentReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return

        try {
            NotificationManagerCompat.from(appContext).cancel(PaymentReminderNotifier.notificationIdFor(reminderId))
        } catch (t: Throwable) {
            android.util.Log.e("PaymentReminderAction", "Error cancelling notification", t)
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(appContext).paymentReminderDao()
                val entity = dao.getById(reminderId) ?: return@launch
                dao.markPaid(reminderId, cycleKeyFor(entity.dueDateMillis))
            } catch (t: Throwable) {
                android.util.Log.e("PaymentReminderAction", "Error marking reminder paid", t)
            } finally {
                kotlin.runCatching { pendingResult.finish() }
            }
        }

        try {
            Toast.makeText(appContext, "Marked as paid", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            // Background toast might be restricted on some OEMs
        }
    }
}
