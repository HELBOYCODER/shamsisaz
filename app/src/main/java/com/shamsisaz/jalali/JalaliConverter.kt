package com.shamsisaz.jalali

import java.util.Calendar
import java.util.TimeZone

/**
 * تبدیل دقیق میلادی ↔ شمسی (جلالی)
 * الگوریتم بر اساس 33 ساله — تست شده برای 1900..2100
 * ponytail: برای دقت نجومی کامل از کتابخانه‌ی ICU4J استفاده کن؛ این پیاده‌سازی سبک و آفلاین است.
 */
object JalaliConverter {

    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    private val persianMonthsShort = arrayOf(
        "فرو", "ارد", "خرد", "تیر", "مرد", "شهر",
        "مهر", "آبا", "آذر", "دی", "بهم", "اسفن"
    )
    private val persianWeekDays = arrayOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )

    private val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        fun formatNumeric(): String = "%04d/%02d/%02d".format(year, month, day)
        fun formatPersian(): String = "$day ${persianMonths[month - 1]} $year"
        fun formatShort(): String = "%02d ${persianMonthsShort[month - 1]}".format(day)
    }

    data class GregorianDate(val year: Int, val month: Int, val day: Int)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        var gy2 = gy - 1600
        var gm2 = gm - 1
        var gd2 = gd - 1

        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += gDaysInMonth[i]
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++
        gDayNo += gd2

        var jDayNo = gDayNo - 79
        var jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        var jm = 0
        var jd = 0
        var i = 0
        while (i < 11 && jDayNo >= jDaysInMonth[i]) {
            jDayNo -= jDaysInMonth[i]
            i++
        }
        jm = i + 1
        jd = jDayNo + 1
        return JalaliDate(jy, jm, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): GregorianDate {
        var jy2 = jy - 979
        var jm2 = jm - 1
        var jd2 = jd - 1
        var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
        for (i in 0 until jm2) jDayNo += jDaysInMonth[i]
        jDayNo += jd2
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }
        var gm = 0
        var gd = 0
        var i = 0
        while (gDayNo >= gDaysInMonth[i] + if (i == 1 && leap) 1 else 0) {
            gDayNo -= gDaysInMonth[i] + if (i == 1 && leap) 1 else 0
            i++
        }
        gm = i + 1
        gd = gDayNo + 1
        return GregorianDate(gy, gm, gd)
    }

    fun fromMillis(millis: Long, tz: TimeZone = TimeZone.getDefault()): JalaliDate {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = millis
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun today(): JalaliDate {
        val c = Calendar.getInstance()
        return gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun weekdayPersian(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        // Calendar.SATURDAY =7 ... mapping to شنبه
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday
        val map = mapOf(7 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6)
        return persianWeekDays[map[dow] ?: 1]
    }

    fun toPersianDigits(input: String): String {
        val persian = arrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return input.map { if (it in '0'..'9') persian[it - '0'] else it }.joinToString("")
    }

    // تبدیل تاریخ‌های میلادی داخل متن به شمسی (مثل 2024/10/05 یا 2024-10-05)
    fun convertDatesInText(text: String): String {
        if (text.isBlank()) return text
        // الگو: YYYY/MM/DD یا YYYY-MM-DD
        val regex = Regex("""\b(19|20)\d{2}[/-](0?[1-9]|1[0-2])[/-](0?[1-9]|[12]\d|3[01])\b""")
        return regex.replace(text) { mr ->
            try {
                val parts = mr.value.split(Regex("[/-]"))
                val gy = parts[0].toInt(); val gm = parts[1].toInt(); val gd = parts[2].toInt()
                val j = gregorianToJalali(gy, gm, gd)
                j.formatNumeric()
            } catch (_: Exception) { mr.value }
        }
    }
}
