package com.shamsisaz.jalali

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** ویجت کوچک «امروز» — روز هفته، روز ماه، ماه، تاریخ میلادی و قمری، مناسبت امروز */
class ShamsiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
        Refresher.scheduleNextMidnight(context)
    }

    override fun onEnabled(context: Context) {
        Refresher.scheduleNextMidnight(context)
    }

    companion object {
        fun updateWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val today = JalaliConverter.today()
            val views = RemoteViews(context.packageName, R.layout.widget_shamsi)
            views.setTextViewText(R.id.widgetWeekday, JalaliConverter.weekDayNames[today.weekDay()])
            views.setTextViewText(R.id.widgetDayNumber, JalaliConverter.fa(today.day))
            views.setTextViewText(R.id.widgetMonthYear, "${today.monthName()} ${JalaliConverter.fa(today.year)}")
            views.setTextViewText(R.id.widgetGregorian, Occasions.gregorianLabel(today))
            views.setTextViewText(R.id.widgetHijri, Occasions.hijriLabel(today))
            val occ = Occasions.forDay(today)
            views.setTextViewText(R.id.widgetOccasion, occ.joinToString(" • ") { it.title })
            views.setTextColor(R.id.widgetDayNumber, if (Occasions.isHoliday(today)) 0xFFFF8A80.toInt() else 0xFFFFFFFF.toInt())

            val pi = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)
            awm.updateAppWidget(widgetId, views)
        }

        fun refreshAll(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, ShamsiWidgetProvider::class.java))
            for (id in ids) updateWidget(context, awm, id)
        }
    }
}
