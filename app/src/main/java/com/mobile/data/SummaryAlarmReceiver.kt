package com.mobile.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.mobile.data.db.AppDatabase
import com.mobile.data.db.toDomain
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires on the AlarmManager schedule set by SummaryScheduler.
 * Fast execution: reads cached Room database first in <1ms, falling back to SMS inbox only if empty.
 */
class SummaryAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SummaryAlarmReceiver"
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Unhandled exception in SummaryAlarmReceiver coroutine", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val appContext = context.applicationContext

        val frequency = try {
            intent.getStringExtra(SummaryScheduler.EXTRA_FREQUENCY) ?: "Daily"
        } catch (_: Throwable) {
            "Daily"
        }

        try {
            SettingsRepository.init(appContext)
            FinanceRepository.init(appContext)

            if (!SettingsRepository.notificationsEnabled.value) return
            val windowMillis = SummaryScheduler.intervalMillis(frequency) ?: (24 * 60 * 60 * 1000L)

            // Re-arm next alarm for this recurring frequency
            if (frequency in SettingsRepository.summaryFrequencies.value) {
                try {
                    SummaryScheduler.schedule(appContext, frequency)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error rescheduling summary for $frequency", t)
                }
            }

            val pendingResult = try { goAsync() } catch (_: Throwable) { null }
            scope.launch {
                try {
                    val transactions = if (frequency.equals("Daily", ignoreCase = true)) {
                        // Gather transactions for today based on Ethiopian Time
                        val startOfDay = EthiopianCalendar.startOfEthiopianDayMillis()
                        val dayTxs = loadTransactionsSince(appContext, startOfDay)
                        if (dayTxs.isEmpty()) {
                            // Fallback to rolling 24 hours if no transactions today yet
                            loadTransactionsSince(appContext, System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
                        } else {
                            dayTxs
                        }
                    } else {
                        val windowMillis = SummaryScheduler.intervalMillis(frequency) ?: (24 * 60 * 60 * 1000L)
                        val since = System.currentTimeMillis() - windowMillis
                        loadTransactionsSince(appContext, since)
                    }
                    SummaryNotifier.notify(appContext, frequency, transactions)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in SummaryAlarmReceiver coroutine", t)
                } finally {
                    if (pendingResult != null) {
                        runCatching { pendingResult.finish() }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in SummaryAlarmReceiver onReceive", t)
        }
    }

    private suspend fun loadTransactionsSince(context: Context, sinceMillis: Long): List<Transaction> {
        // Fast path: Query Room DB directly (instantaneous)
        try {
            val entities = AppDatabase.getInstance(context).transactionDao().getAll()
            if (entities.isNotEmpty()) {
                val domainTxs = entities.map { it.toDomain() }
                return domainTxs.filter { tx ->
                    val txTimeMillis = transactionTimestampMillis(tx) ?: parseTransactionDate(tx.date)?.timeInMillis
                    txTimeMillis != null && txTimeMillis >= sinceMillis
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed reading transactions from DB for summary", t)
        }

        // Secondary fallback: Read SMS inbox safely
        return readTransactionsFromSms(context, sinceMillis)
    }

    private fun readTransactionsFromSms(context: Context, sinceMillis: Long): List<Transaction> {
        val results = mutableListOf<Transaction>()
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date >= ?",
                arrayOf(sinceMillis.toString()),
                "date DESC"
            )
            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                if (addressIndex >= 0 && bodyIndex >= 0 && dateIndex >= 0) {
                    while (it.moveToNext()) {
                        val address = it.getString(addressIndex) ?: continue
                        val body = it.getString(bodyIndex) ?: continue
                        val date = it.getLong(dateIndex)
                        SmsParser.parseMessage(address, body, date)?.let(results::add)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Permission or content provider error reading SMS fallback", t)
        }
        return results
    }
}
