package com.shamsisaz.jalali

import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

/** رویدادهای تقویم گوشی برای یک روز مشخص (با درنظر گرفتن رویدادهای تمام‌روز که در UTC ذخیره می‌شوند) */
object DayEvents {

    data class Item(val title: String, val timeLabel: String)

    fun on(ctx: Context, d: JalaliConverter.JalaliDate, max: Int = 8): List<Item> {
        val g = d.toGregorian()
        val localStart = Calendar.getInstance().apply { clear(); set(g.year, g.month - 1, g.day) }.timeInMillis
        val localEnd = localStart + 86_400_000L
        val utcStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(g.year, g.month - 1, g.day) }.timeInMillis
        val out = ArrayList<Item>()
        val from = minOf(localStart, utcStart) - 86_400_000L
        val to = maxOf(localEnd, utcStart + 86_400_000L) + 86_400_000L
        val ownId = HolidayCalendar.calendarId(ctx)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(from.toString()).appendPath(to.toString()).build()
        ctx.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use {
            while (it.moveToNext() && out.size < max) {
                val begin = it.getLong(1)
                val allDay = it.getInt(2) == 1
                if (ownId != null && it.getLong(3) == ownId) continue // مناسبت‌های خود اپ جدا نمایش داده می‌شوند
                val title = it.getString(0)?.takeIf { t -> t.isNotBlank() } ?: "(بدون عنوان)"
                if (allDay) {
                    if (begin == utcStart) out.add(Item(title, "تمام‌روز"))
                } else if (begin in localStart until localEnd) {
                    val c = Calendar.getInstance().apply { timeInMillis = begin }
                    out.add(Item(title, JalaliConverter.toPersianDigits("%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))))
                }
            }
        }
        return out
    }
}
