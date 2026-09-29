package com.mobile.data

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class NetWorthWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH_WIDGET = "com.mobile.ACTION_REFRESH_NET_WORTH_WIDGET"
    }

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        try {
            if (context == null || appWidgetManager == null || appWidgetIds == null) return
            super.onUpdate(context, appWidgetManager, appWidgetIds)
            WeeklySpendingWidgetUpdater.updateAllWidgets(context.applicationContext)
        } catch (t: Throwable) {
            android.util.Log.e("NetWorthWidget", "Error in onUpdate", t)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) return
            super.onReceive(context, intent)
            if (intent.action == ACTION_REFRESH_WIDGET || intent.action == Intent.ACTION_BOOT_COMPLETED) {
                WeeklySpendingWidgetUpdater.updateAllWidgets(context.applicationContext)
            }
        } catch (t: Throwable) {
            android.util.Log.e("NetWorthWidget", "Error in onReceive", t)
        }
    }
}

