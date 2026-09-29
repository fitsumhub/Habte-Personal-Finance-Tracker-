package com.mobile.data

import android.content.Context
import android.util.Log
import com.mobile.data.db.AppDatabase
import com.mobile.data.db.toDomain
import com.mobile.data.db.toEntity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Persists payment reminders (Room-backed) and keeps their AlarmManager schedules in sync with the stored state. */
object PaymentReminderRepository {
    private const val TAG = "PaymentReminderRepo"

    @Volatile
    private var dbInstance: AppDatabase? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Unhandled coroutine exception in PaymentReminderRepository", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    private var initialized = false

    private val _reminders = MutableStateFlow<List<PaymentReminder>>(emptyList())
    val reminders: StateFlow<List<PaymentReminder>> = _reminders.asStateFlow()

    private fun getDb(context: Context? = null): AppDatabase? {
        return dbInstance ?: synchronized(this) {
            dbInstance ?: context?.let { ctx ->
                try {
                    AppDatabase.getInstance(ctx.applicationContext).also { dbInstance = it }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to obtain AppDatabase instance", t)
                    null
                }
            }
        }
    }

    @Synchronized
    fun init(context: Context) {
        val database = getDb(context) ?: return
        if (initialized) return
        initialized = true
        scope.launch {
            try {
                database.paymentReminderDao().observeAll().collect { entities ->
                    _reminders.value = entities.map { it.toDomain() }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error observing payment reminders", t)
            }
        }
    }

    /** Inserts a new reminder or replaces an existing one (by id), then (re)arms or cancels its alarm to match [reminder]'s enabled state. */
    fun save(context: Context, reminder: PaymentReminder) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val database = getDb(appContext) ?: return@launch
                val newId = database.paymentReminderDao().upsert(reminder.toEntity())
                val resolvedId = if (reminder.id == 0L) newId else reminder.id
                if (reminder.enabled) {
                    PaymentReminderScheduler.scheduleFor(appContext, resolvedId, reminder.dueDateMillis, reminder.daysBefore)
                } else {
                    PaymentReminderScheduler.cancel(appContext, resolvedId)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error saving payment reminder: ${reminder.label}", t)
            }
        }
    }

    fun setEnabled(context: Context, reminder: PaymentReminder, enabled: Boolean) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val database = getDb(appContext) ?: return@launch
                database.paymentReminderDao().setEnabled(reminder.id, enabled)
                if (enabled) {
                    PaymentReminderScheduler.scheduleFor(appContext, reminder.id, reminder.dueDateMillis, reminder.daysBefore)
                } else {
                    PaymentReminderScheduler.cancel(appContext, reminder.id)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error toggling payment reminder ${reminder.id}", t)
            }
        }
    }

    /** Suppresses this cycle's notification without disturbing the recurring schedule itself. */
    fun markPaid(reminder: PaymentReminder) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.paymentReminderDao().markPaid(reminder.id, cycleKeyFor(reminder.dueDateMillis))
            } catch (t: Throwable) {
                Log.e(TAG, "Error marking payment reminder paid ${reminder.id}", t)
            }
        }
    }

    fun delete(context: Context, reminder: PaymentReminder) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                PaymentReminderScheduler.cancel(appContext, reminder.id)
                val database = getDb(appContext) ?: return@launch
                database.paymentReminderDao().delete(reminder.id)
            } catch (t: Throwable) {
                Log.e(TAG, "Error deleting payment reminder ${reminder.id}", t)
            }
        }
    }
}
