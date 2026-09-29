package com.mobile.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mobile.data.db.AppDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-registers every active alarm after a device reboot — AlarmManager alarms don't
 * survive a restart, so without this the "Spending Summary" schedule and any enabled
 * payment reminders would silently stop firing until the user happened to reopen the
 * relevant screen.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Unhandled exception in BootReceiver coroutine", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = try { intent.action } catch (_: Throwable) { null }
        if (action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        try {
            SettingsRepository.init(appContext)
            FinanceRepository.init(appContext)
            PaymentReminderRepository.init(appContext)
            CertificateRepository.init(appContext)
            SummaryScheduler.rescheduleAll(appContext, SettingsRepository.summaryFrequencies.value)
        } catch (t: Throwable) {
            Log.e(TAG, "Error synchronizing state on boot", t)
        }

        val pendingResult = try { goAsync() } catch (_: Throwable) { null }
        scope.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                db.paymentReminderDao().getEnabled().forEach { entity ->
                    try {
                        PaymentReminderScheduler.scheduleFor(appContext, entity.id, entity.dueDateMillis, entity.daysBefore)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error rescheduling payment reminder ${entity.id}", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error rescheduling payment reminders on boot", t)
            } finally {
                if (pendingResult != null) {
                    runCatching { pendingResult.finish() }
                }
            }
        }
    }
}
