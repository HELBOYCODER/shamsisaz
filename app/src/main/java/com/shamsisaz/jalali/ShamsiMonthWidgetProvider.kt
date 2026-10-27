package com.shamsisaz.jalali

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** ویجت ماهانه — شبکهٔ کامل ماه شمسی با جمعه‌ها و تعطیلات قرمز، امروز مشخص، و دکمهٔ ماه قبل/بعد */
class ShamsiMonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
        Refresher.scheduleNextMidnight(context)
    }

    override fun onEnabled(context: Context) {
        Refresher.scheduleNextMidnight(context)
    }

    companion object {
        private const val HOLIDAY = 0xFFFF8A80.toInt()
        private const val NORMAL = 0xFFFFFFFF.toInt()
        private const val ON_TODAY = 0xFF0A5C36.toInt()

        /** (سال، ماه) پس از اعمال جابه‌جایی */
        fun shifted(base: JalaliConverter.JalaliDate, offset: Int): Pair<Int, Int> {
            val idx = base.year * 12 + (base.month - 1) + offset
            return Math.floorDiv(idx, 12) to Math.floorMod(idx, 12) + 1
        }

        fun updateWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val today = JalaliConverter.today()
            val offset = Prefs.widgetMonthOffset(context)
            val (year, month) = shifted(today, offset)
            val views = RemoteViews(context.packageName, R.layout.widget_month)

            views.setTextViewText(R.id.mwTitle, "${JalaliConverter.monthNames[month - 1]} ${JalaliConverter.fa(year)}")

            val firstJdn = JalaliConverter.jalaliToJdn(year, month, 1)
            val startCol = JalaliConverter.weekDayOfJdn(firstJdn) // ۰ = شنبه
            val len = JalaliConverter.monthLength(year, month)
            for (i in 0 until 42) {
                val id = cellId(context, i)
                val day = i - startCol + 1
                if (day < 1 || day > len) {
                    views.setTextViewText(id, "")
                    views.setInt(id, "setBackgroundResource", 0)
                    continue
                }
                val d = JalaliConverter.JalaliDate(year, month, day)
                val isToday = d == today
                views.setTextViewText(id, JalaliConverter.fa(day))
                views.setInt(id, "setBackgroundResource", if (isToday) R.drawable.bg_widget_today else 0)
                views.setTextColor(id, when {
                    isToday -> ON_TODAY
                    Occasions.isHoliday(d) -> HOLIDAY
                    else -> NORMAL
                })
            }

            // زیرنویس: مناسبت‌های امروز یا اولین مناسبت‌های ماه
            val footer = if (offset == 0) {
                Occasions.forDay(today).joinToString(" • ") { it.title }
            } else {
                (1..len).firstNotNullOfOrNull { day ->
                    Occasions.forDay(JalaliConverter.JalaliDate(year, month, day)).firstOrNull { it.holiday }
                        ?.let { "${JalaliConverter.fa(day)} ${JalaliConverter.monthNames[month - 1]}: ${it.title}" }
                } ?: ""
            }
            views.setTextViewText(R.id.mwFooter, footer)

            fun bc(action: String, req: Int) = PendingIntent.getBroadcast(
                context, req, Intent(context, SystemReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.mwPrev, bc(Refresher.ACTION_WIDGET_PREV, 1))
            views.setOnClickPendingIntent(R.id.mwNext, bc(Refresher.ACTION_WIDGET_NEXT, 2))
            views.setOnClickPendingIntent(R.id.mwTitle, bc(Refresher.ACTION_WIDGET_TODAY, 3))
            views.setOnClickPendingIntent(
                R.id.mwFooter,
                PendingIntent.getActivity(
                    context, 4, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            awm.updateAppWidget(widgetId, views)
        }

        private fun cellId(context: Context, i: Int): Int =
            context.resources.getIdentifier("wc$i", "id", context.packageName)

        fun refreshAll(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, ShamsiMonthWidgetProvider::class.java))
            for (id in ids) updateWidget(context, awm, id)
        }
    }
}
