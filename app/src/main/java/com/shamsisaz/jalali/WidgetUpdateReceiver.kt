package com.shamsisaz.jalali

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_TIME_TICK || intent.action == "com.shamsisaz.REFRESH_WIDGET") {
            ShamsiWidgetProvider.refreshAll(context)
        }
    }
}
