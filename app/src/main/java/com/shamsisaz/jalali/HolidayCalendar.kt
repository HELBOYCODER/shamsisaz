package com.shamsisaz.jalali

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * تقویمِ محلیِ «مناسبت‌های ایران» داخل تقویم خود گوشی (سامسونگ، شیائومی، گوگل و …).
 * رویدادها تمام‌روز هستند و به حساب محلی گوشی تعلق دارند (به هیچ سرویس ابری همگام نمی‌شوند).
 */
object HolidayCalendar {

    private const val ACCOUNT_NAME = "ShamsiSaz"
    private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
    private const val DAY_MS = 86_400_000L

    data class SyncResult(val events: Int, val holidays: Int, val fromYear: Int, val toYear: Int)

    private fun syncUri(base: Uri): Uri = base.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()

    fun calendarId(ctx: Context): Long? {
        ctx.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE), null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        return null
    }

    fun isInstalled(ctx: Context): Boolean = try { calendarId(ctx) != null } catch (_: Exception) { false }

    private fun ensureCalendar(ctx: Context): Long {
        calendarId(ctx)?.let { return it }
        val v = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, "shamsisaz_occasions")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "مناسبت‌های ایران (شمسی‌ساز)")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0F7B4A.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = ctx.contentResolver.insert(syncUri(CalendarContract.Calendars.CONTENT_URI), v)
            ?: error("ساخت تقویم ناموفق بود")
        return ContentUris.parseId(uri)
    }

    /** مناسبت‌های سال جاری و سال بعد شمسی را (دوباره) می‌نویسد — چندبار زدن تکرار ایجاد نمی‌کند */
    fun sync(ctx: Context): SyncResult {
        val cr = ctx.contentResolver
        val id = ensureCalendar(ctx)
        cr.delete(syncUri(CalendarContract.Events.CONTENT_URI), "${CalendarContract.Events.CALENDAR_ID}=?", arrayOf(id.toString()))

        val thisYear = JalaliConverter.today().year
        val ops = ArrayList<ContentProviderOperation>()
        var holidays = 0
        for (year in thisYear..thisYear + 1) {
            for (month in 1..12) for (day in 1..JalaliConverter.monthLength(year, month)) {
                val j = JalaliConverter.JalaliDate(year, month, day)
                val occasions = Occasions.forDay(j)
                if (occasions.isEmpty()) continue
                val g = j.toGregorian()
                val startUtc = java.util.GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                    clear(); set(g.year, g.month - 1, g.day)
                }.timeInMillis
                for (o in occasions) {
                    if (o.holiday) holidays++
                    val desc = buildString {
                        append(JalaliConverter.fullPersian(j)).append("\n")
                        append(Occasions.gregorianLabel(j)).append(" • ").append(Occasions.hijriLabel(j))
                        if (o.holiday) append("\nتعطیل رسمی")
                        if (o.approximate) append("\nتاریخ مناسبت‌های قمری ممکن است تا یک روز با اعلام رسمی فرق کند.")
                    }
                    ops.add(
                        ContentProviderOperation.newInsert(syncUri(CalendarContract.Events.CONTENT_URI))
                            .withValue(CalendarContract.Events.CALENDAR_ID, id)
                            .withValue(CalendarContract.Events.TITLE, if (o.holiday) "${o.title} (تعطیل)" else o.title)
                            .withValue(CalendarContract.Events.DESCRIPTION, desc)
                            .withValue(CalendarContract.Events.DTSTART, startUtc)
                            .withValue(CalendarContract.Events.DTEND, startUtc + DAY_MS)
                            .withValue(CalendarContract.Events.ALL_DAY, 1)
                            .withValue(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                            .withValue(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
                            .withValue(CalendarContract.Events.EVENT_COLOR, if (o.holiday) 0xFFD32F2F.toInt() else 0xFF0F7B4A.toInt())
                            .build()
                    )
                }
            }
        }
        // دسته‌های ۴۰۰تایی تا از محدودیت اندازهٔ تراکنش Binder رد نشویم
        ops.chunked(400).forEach { cr.applyBatch(CalendarContract.AUTHORITY, ArrayList(it)) }
        return SyncResult(ops.size, holidays, thisYear, thisYear + 1)
    }

    fun remove(ctx: Context): Boolean {
        val id = calendarId(ctx) ?: return false
        ctx.contentResolver.delete(syncUri(CalendarContract.Calendars.CONTENT_URI), "${CalendarContract.Calendars._ID}=?", arrayOf(id.toString()))
        return true
    }
}
