package com.shamsisaz.jalali

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import org.json.JSONObject
import java.io.File

/**
 * تبدیل تاریخ‌های میلادیِ نوشته‌شده «داخل متن» رویدادها (عنوان، توضیحات، مکان) به شمسی.
 * - چیزی به عنوان اضافه نمی‌شود؛ فقط تاریخ‌های میلادی داخل متن عوض می‌شوند.
 * - اصل متن‌ها در فایل پشتیبان ذخیره می‌شود و «بازگردانی» فقط فیلدهایی را برمی‌گرداند که کاربر بعد از تبدیل ویرایش نکرده.
 * - تقویم‌های فقط‌خواندنی (مثل تعطیلات گوگل) و تقویم مناسبت‌های خودِ اپ دست‌نخورده می‌مانند.
 */
class CalendarConverter(private val context: Context) {

    data class EventInfo(val id: Long, val title: String?, val description: String?, val location: String?, val calendarId: Long)

    data class ConvertResult(val total: Int, val converted: Int, val skipped: Int, val readOnly: Int, val errors: Int)

    data class Change(val before: String, val after: String)

    private val backupFile get() = File(context.filesDir, "calendar_backup.json")

    private fun writableCalendarIds(): Set<Long> {
        val out = HashSet<Long>()
        val own = HolidayCalendar.calendarId(context)
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
            null, null, null
        )?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                if (id != own && it.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) out.add(id)
            }
        }
        return out
    }

    fun calendarCount(): Int = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), null, null, null
    )?.use { it.count } ?: 0

    fun listEvents(): List<EventInfo> {
        val out = ArrayList<EventInfo>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.CALENDAR_ID
            ),
            "${CalendarContract.Events.DELETED}=0", null, "${CalendarContract.Events.DTSTART} DESC"
        )?.use {
            while (it.moveToNext()) {
                out.add(EventInfo(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getLong(4)))
            }
        }
        return out
    }

    fun eventCount(): Int = listEvents().size

    private fun convertField(s: String?): String? = if (s.isNullOrBlank()) s else JalaliEngine.convertAll(s)

    /** پیش‌نمایش بدون هیچ نوشتنی */
    fun preview(limit: Int = 15): List<Change> {
        val ids = writableCalendarIds()
        val out = ArrayList<Change>()
        for (ev in listEvents()) {
            if (ev.calendarId !in ids) continue
            for ((old, new) in listOf(ev.title to convertField(ev.title), ev.description to convertField(ev.description), ev.location to convertField(ev.location))) {
                if (old != null && new != null && old != new) {
                    out.add(Change(old.take(160), new.take(160)))
                    break
                }
            }
            if (out.size >= limit) break
        }
        return out
    }

    fun convertAll(onProgress: ((done: Int, total: Int) -> Unit)? = null): ConvertResult {
        val ids = writableCalendarIds()
        val events = listEvents()
        val backup = loadBackup()
        var converted = 0; var skipped = 0; var readOnly = 0; var errors = 0
        events.forEachIndexed { i, ev ->
            onProgress?.invoke(i, events.size)
            if (ev.calendarId !in ids) { readOnly++; return@forEachIndexed }
            val nt = convertField(ev.title); val nd = convertField(ev.description); val nl = convertField(ev.location)
            if (nt == ev.title && nd == ev.description && nl == ev.location) { skipped++; return@forEachIndexed }
            try {
                val v = ContentValues()
                if (nt != ev.title) v.put(CalendarContract.Events.TITLE, nt)
                if (nd != ev.description) v.put(CalendarContract.Events.DESCRIPTION, nd)
                if (nl != ev.location) v.put(CalendarContract.Events.EVENT_LOCATION, nl)
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.id)
                if (context.contentResolver.update(uri, v, null, null) > 0) {
                    val key = ev.id.toString()
                    val entry = backup.optJSONObject(key) ?: JSONObject()
                    // اولین اصل متن را نگه می‌داریم، حتی اگر بعداً دوباره تبدیل شود
                    fun put(name: String, original: String?, newValue: String?, changed: Boolean) {
                        if (!changed) return
                        if (!entry.has("o_$name")) entry.put("o_$name", original ?: JSONObject.NULL)
                        entry.put("n_$name", newValue ?: JSONObject.NULL)
                    }
                    put("title", ev.title, nt, nt != ev.title)
                    put("desc", ev.description, nd, nd != ev.description)
                    put("loc", ev.location, nl, nl != ev.location)
                    backup.put(key, entry)
                    converted++
                } else skipped++
            } catch (_: Exception) { errors++ }
        }
        saveBackup(backup)
        onProgress?.invoke(events.size, events.size)
        return ConvertResult(events.size, converted, skipped, readOnly, errors)
    }

    data class UndoResult(val restored: Int, val keptEdited: Int)

    fun undo(): UndoResult {
        val backup = loadBackup()
        var restored = 0; var kept = 0
        val keys = backup.keys().asSequence().toList()
        for (key in keys) {
            val id = key.toLongOrNull() ?: continue
            val entry = backup.getJSONObject(key)
            try {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                val cur = context.contentResolver.query(
                    uri, arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION, CalendarContract.Events.EVENT_LOCATION),
                    null, null, null
                )?.use { if (it.moveToFirst()) arrayOf(it.getString(0), it.getString(1), it.getString(2)) else null }
                if (cur == null) { backup.remove(key); continue } // رویداد حذف شده
                val v = ContentValues()
                var skippedAny = false
                fun restore(name: String, column: String, current: String?) {
                    if (!entry.has("o_$name")) return
                    val expected = entry.opt("n_$name").let { if (it == JSONObject.NULL) null else it as String? }
                    if (current != expected) { skippedAny = true; return }
                    val orig = entry.opt("o_$name").let { if (it == JSONObject.NULL) null else it as String? }
                    v.put(column, orig)
                }
                restore("title", CalendarContract.Events.TITLE, cur[0])
                restore("desc", CalendarContract.Events.DESCRIPTION, cur[1])
                restore("loc", CalendarContract.Events.EVENT_LOCATION, cur[2])
                if (v.size() > 0 && context.contentResolver.update(uri, v, null, null) > 0) restored++
                if (skippedAny) kept++
                backup.remove(key)
            } catch (_: Exception) { }
        }
        saveBackup(backup)
        return UndoResult(restored, kept)
    }

    fun backupCount(): Int = loadBackup().length()
    fun hasBackup(): Boolean = backupCount() > 0

    private fun loadBackup(): JSONObject = try {
        if (backupFile.exists()) JSONObject(backupFile.readText()) else JSONObject()
    } catch (_: Exception) { JSONObject() }

    private fun saveBackup(o: JSONObject) {
        try {
            if (o.length() == 0) backupFile.delete() else backupFile.writeText(o.toString())
        } catch (_: Exception) { }
    }
}
