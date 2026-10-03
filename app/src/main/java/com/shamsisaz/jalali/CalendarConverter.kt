package com.shamsisaz.jalali

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract

/**
 * هستهٔ تبدیل تقویم — همهٔ رویدادهای میلادی را به شمسی می‌برد.
 * - تاریخ‌های داخل title/description/location را شمسی می‌کند
 * - یک بکاپ سبک در SharedPreferences نگه می‌دارد برای بازگردانی
 * - روی سامسونگ (One UI Calendar) و شیائومی (MIUI Calendar) تست‌شده — هر دو از CalendarProvider استاندارد استفاده می‌کنند
 */
class CalendarConverter(private val context: Context) {

    data class EventInfo(
        val id: Long,
        val title: String?,
        val description: String?,
        val location: String?,
        val dtStart: Long,
        val dtEnd: Long?,
        val calendarId: Long
    )

    data class ConvertResult(
        val total: Int,
        val converted: Int,
        val skipped: Int,
        val calendars: Int,
        val errors: Int
    )

    private val prefs get() = context.getSharedPreferences("shamsisaz_backup", Context.MODE_PRIVATE)

    fun listCalendars(): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        val cr = context.contentResolver ?: return out
        val cur: Cursor? = try {
            cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                null, null, null
            )
        } catch (_: Exception) { null }
        cur?.use {
            val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            while (it.moveToNext()) {
                val id = if (idIdx >= 0) it.getLong(idIdx) else -1
                val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "تقویم $id" else "تقویم $id"
                out.add(id to name)
            }
        }
        return out
    }

    fun listEvents(limit: Int = 200): List<EventInfo> {
        val out = mutableListOf<EventInfo>()
        val cr = context.contentResolver ?: return out
        val cur: Cursor? = try {
            cr.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.CALENDAR_ID
                ),
                null, null, "${CalendarContract.Events.DTSTART} DESC LIMIT $limit"
            )
        } catch (_: Exception) { null }
        cur?.use {
            val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
            val tIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val dIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val lIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val sIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
            val eIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
            val cIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            while (it.moveToNext()) {
                out.add(
                    EventInfo(
                        id = if (idIdx >= 0) it.getLong(idIdx) else -1,
                        title = if (tIdx >= 0) it.getString(tIdx) else null,
                        description = if (dIdx >= 0) it.getString(dIdx) else null,
                        location = if (lIdx >= 0) it.getString(lIdx) else null,
                        dtStart = if (sIdx >= 0) it.getLong(sIdx) else 0L,
                        dtEnd = if (eIdx >= 0 && !it.isNull(eIdx)) it.getLong(eIdx) else null,
                        calendarId = if (cIdx >= 0) it.getLong(cIdx) else -1
                    )
                )
            }
        }
        return out
    }

    /** پیش‌نمایش: بدون نوشتن، فقط بگو چه می‌شود — برای dialog */
    fun preview(limit: Int = 20): List<Pair<String, String>> {
        val events = listEvents(limit)
        return events.mapNotNull { ev ->
            val original = ev.title ?: return@mapNotNull null
            val converted = convertText(original)
            if (converted != original) (original to converted) else null
        }
    }

    fun convertText(text: String?): String? {
        if (text.isNullOrBlank()) return text
        // 1) تاریخ‌های میلادی داخل متن → شمسی
        var out = JalaliConverter.convertDatesInText(text)
        // 2) اگر متن فقط تاریخ میلادی isolated دارد، تاریخ شروع رویداد را هم به شمسی اضافه کن
        // (این بخش در convertAll برای هر رویداد جداگانه اعمال می‌شود)
        return out
    }

    private fun needsConversion(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        // اگر قبلاً نشان [شمسی] دارد، رد کن
        if (title.contains("شمسی") || title.contains("جلالی")) return false
        return true
    }

    /**
     * تبدیل اصلی — همهٔ رویدادها
     * @param addPrefix اگر true، تاریخ شمسی را به ابتدای عنوان اضافه می‌کند
     * @param convertBody اگر true، توضیحات و مکان را هم تبدیل می‌کند
     */
    fun convertAll(
        addPrefix: Boolean = true,
        convertBody: Boolean = true,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): ConvertResult {
        val events = listEvents(limit = 1000)
        val calendars = listCalendars().size
        var converted = 0
        var skipped = 0
        var errors = 0

        // بکاپ عنوان‌های اصلی برای undo
        val backup = mutableMapOf<String, String>()

        events.forEachIndexed { idx, ev ->
            try {
                onProgress?.invoke(idx, events.size)
                if (!needsConversion(ev.title)) { skipped++; return@forEachIndexed }

                val jDate = JalaliConverter.fromMillis(ev.dtStart)
                val jStr = jDate.formatNumeric() // 1403/07/12
                val jPretty = jDate.formatPersian() // 12 مهر 1403

                var newTitle = ev.title ?: ""
                // تاریخ‌های میلادی داخل عنوان را شمسی کن
                newTitle = JalaliConverter.convertDatesInText(newTitle)

                // اگر عنوان هنوز تاریخ نداشت، تاریخ شمسی رویداد را اضافه کن
                if (addPrefix && !newTitle.contains(jStr) && !newTitle.contains(jPretty)) {
                    // اگر عنوان خالی بود، فقط تاریخ
                    newTitle = if (newTitle.isBlank()) jPretty else "$newTitle — $jPretty [$jStr]"
                }

                var newDesc = ev.description
                if (convertBody) newDesc = convertText(newDesc)
                var newLoc = ev.location
                if (convertBody) newLoc = convertText(newLoc)

                // اگر هیچ تغییری نکرد، رد کن
                if (newTitle == ev.title && newDesc == ev.description && newLoc == ev.location) {
                    skipped++; return@forEachIndexed
                }

                backup[ev.id.toString()] = ev.title ?: ""

                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, newTitle)
                    if (newDesc != ev.description) put(CalendarContract.Events.DESCRIPTION, newDesc)
                    if (newLoc != ev.location) put(CalendarContract.Events.EVENT_LOCATION, newLoc)
                }
                val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.id)
                val rows = context.contentResolver.update(uri, values, null, null)
                if (rows > 0) converted++ else skipped++
            } catch (_: Exception) {
                errors++
            }
        }
        // ذخیره بکاپ برای بازگردانی
        if (backup.isNotEmpty()) {
            val ed = prefs.edit()
            backup.forEach { (k, v) -> ed.putString("bk_$k", v) }
            ed.putLong("bk_time", System.currentTimeMillis())
            ed.putInt("bk_count", backup.size)
            ed.apply()
        }
        onProgress?.invoke(events.size, events.size)
        return ConvertResult(events.size, converted, skipped, calendars, errors)
    }

    fun undo(): Int {
        val all = prefs.all
        var restored = 0
        for ((k, v) in all) {
            if (!k.startsWith("bk_") || k == "bk_time" || k == "bk_count") continue
            val idStr = k.removePrefix("bk_")
            val id = idStr.toLongOrNull() ?: continue
            val originalTitle = v as? String ?: continue
            try {
                val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                val values = ContentValues().apply { put(CalendarContract.Events.TITLE, originalTitle) }
                val rows = context.contentResolver.update(uri, values, null, null)
                if (rows > 0) restored++
            } catch (_: Exception) { }
        }
        // پاک کردن بکاپ بعد از بازگردانی
        val ed = prefs.edit()
        for (k in all.keys.toList()) if (k.startsWith("bk_")) ed.remove(k)
        ed.apply()
        return restored
    }

    fun hasBackup(): Boolean = prefs.getInt("bk_count", 0) > 0
    fun backupCount(): Int = prefs.getInt("bk_count", 0)
}
