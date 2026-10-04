package com.shamsisaz.jalali

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * ویجت تقویم شمسی برای صفحهٔ اصلی — روی سامسونگ A33 وقتی One UI
 * تاریخ سیستمی را میلادی نگه می‌دارد، این ویجت همیشه شمسی نشان می‌دهد.
 * کاربر با نگه داشتن صفحهٔ اصلی → ویجت‌ها → شمسی‌ساز آن را اضافه می‌کند.
 */
class ShamsiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    companion object {
        fun updateWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val jalali = JalaliConverter.today()
            val views = RemoteViews(context.packageName, R.layout.widget_shamsi)

            views.setTextViewText(R.id.widgetDayNumber, JalaliConverter.toPersianDigits(jalali.day.toString()))
            views.setTextViewText(R.id.widgetMonthYear, "${jalali.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(jalali.year.toString())}")
            views.setTextViewText(R.id.widgetWeekday, JalaliConverter.weekdayPersian(System.currentTimeMillis()))
            views.setTextViewText(R.id.widgetGregorian, java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))

            val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)

            awm.updateAppWidget(widgetId, views)
        }

        fun refreshAll(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(android.content.ComponentName(context, ShamsiWidgetProvider::class.java))
            for (id in ids) updateWidget(context, awm, id)
        }
    }
}
