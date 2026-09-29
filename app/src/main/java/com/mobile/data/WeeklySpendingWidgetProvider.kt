package com.mobile.data

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class WeeklySpendingWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.mobile.ACTION_REFRESH_SPENDING_WIDGET"
        const val ACTION_UPDATE_WIDGET = "com.mobile.ACTION_UPDATE_SPENDING_WIDGET"
    }

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        try {
            if (context == null || appWidgetManager == null || appWidgetIds == null) return
            super.onUpdate(context, appWidgetManager, appWidgetIds)
            WeeklySpendingWidgetUpdater.updateWidgets(context.applicationContext, appWidgetManager, appWidgetIds)
        } catch (t: Throwable) {
            android.util.Log.e("SpendingWidget", "Error in onUpdate", t)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) return
            super.onReceive(context, intent)
            val action = intent.action
            if (action == ACTION_REFRESH_WIDGET || action == ACTION_UPDATE_WIDGET || action == Intent.ACTION_BOOT_COMPLETED) {
                WeeklySpendingWidgetUpdater.updateAllWidgets(context.applicationContext)
            }
        } catch (t: Throwable) {
            android.util.Log.e("SpendingWidget", "Error in onReceive", t)
        }
    }
}

