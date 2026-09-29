package com.mobile

import android.app.Application
import android.util.Log
import com.mobile.data.CertificateRepository
import com.mobile.data.CrashReporter
import com.mobile.data.FinanceRepository
import com.mobile.data.PaymentReminderRepository
import com.mobile.data.SettingsRepository

/**
 * Custom Application class for Habte - Personal Finance.
 * Guarantees that whenever the process is created (by an Activity, BroadcastReceiver,
 * AppWidgetProvider, or NotificationListenerService), global crash reporting and
 * all data repositories are safely and reliably initialized before any component runs.
 */
class HabteApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 1. Install crash reporter before anything else can execute
        try {
            CrashReporter.install(this)
        } catch (t: Throwable) {
            Log.e("HabteApplication", "Error installing crash reporter", t)
        }

        // 2. Safely initialize repositories independently so failure in one never blocks the others
        try { SettingsRepository.init(this) } catch (t: Throwable) { Log.e("HabteApplication", "Error initializing SettingsRepository", t) }
        try { FinanceRepository.init(this) } catch (t: Throwable) { Log.e("HabteApplication", "Error initializing FinanceRepository", t) }
        try { PaymentReminderRepository.init(this) } catch (t: Throwable) { Log.e("HabteApplication", "Error initializing PaymentReminderRepository", t) }
        try { CertificateRepository.init(this) } catch (t: Throwable) { Log.e("HabteApplication", "Error initializing CertificateRepository", t) }
    }
}
