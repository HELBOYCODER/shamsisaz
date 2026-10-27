package com.shamsisaz.jalali

import java.util.Calendar
import java.util.TimeZone

/**
 * تبدیل دقیق میلادی ↔ شمسی (جلالی) — پورت الگوریتم jalaali-js (معتبر برای سال‌های ۱۱۷۸ تا ۱۶۳۳ شمسی).
 * کاملاً بدون وابستگی به اندروید تا روی JVM هم تست شود.
 * خروجی در تست با ICU4J (PersianCalendar) برای ۱۳۰۰..۱۵۰۰ روز‌به‌روز مقایسه می‌شود.
 */
object JalaliConverter {

    val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    /** ایندکس ۰ = شنبه */
    val weekDayNames = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        fun monthName(): String = monthNames[month - 1]
        fun formatNumeric(): String = "%04d/%02d/%02d".format(year, month, day)
        fun formatPersian(): String = "$day ${monthName()} $year"
        fun formatShort(): String = "$day ${monthName()}"
        fun toJdn(): Int = jalaliToJdn(year, month, day)
        fun toGregorian(): GregorianDate = jdnToGregorian(toJdn())
        /** ۰ = شنبه … ۶ = جمعه */
        fun weekDay(): Int = weekDayOfJdn(toJdn())
    }

    data class GregorianDate(val year: Int, val month: Int, val day: Int)

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int, withoutLeap: Boolean = false): JalCal {
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        require(jy >= jp && jy < breaks[bl - 1]) { "Invalid Jalaali year $jy" }
        var jump = 0
        for (i in 1 until bl) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        var leap = 0
        if (!withoutLeap) {
            if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
            leap = ((n + 1) % 33 - 1) % 4
            if (leap == -1) leap = 4
        }
        return JalCal(leap, gy, march)
    }

    fun isLeap(jy: Int): Boolean = jalCal(jy).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeap(jy)) 30 else 29
    }

    private fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 + (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
        d = d - (((gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4 + 752
        return d
    }

    private fun jdnToGregorian(jdn: Int): GregorianDate {
        var j = 4 * jdn + 139361631
        j += (((4 * jdn + 183187720) / 146097) * 3) / 4 * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gd = (i % 153) / 5 + 1
        val gm = (i / 153) % 12 + 1
        val gy = j / 1461 - 100100 + (8 - gm) / 6
        return GregorianDate(gy, gm, gd)
    }

    fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy, true)
        return gregorianToJdn(r.gy, 3, r.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    }

    private fun jdnToJalali(jdn: Int): JalaliDate {
        val gy = jdnToGregorian(jdn).year
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = gregorianToJdn(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return JalaliDate(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + k / 30, k % 30 + 1)
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate = jdnToJalali(gregorianToJdn(gy, gm, gd))

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): GregorianDate = jdnToGregorian(jalaliToJdn(jy, jm, jd))

    fun gregorianJdn(gy: Int, gm: Int, gd: Int): Int = gregorianToJdn(gy, gm, gd)
    fun fromJdn(jdn: Int): JalaliDate = jdnToJalali(jdn)
    fun gregorianFromJdn(jdn: Int): GregorianDate = jdnToGregorian(jdn)

    /** ۰ = شنبه … ۶ = جمعه */
    fun weekDayOfJdn(jdn: Int): Int = ((jdn + 2) % 7 + 7) % 7

    fun isGregorianLeap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    fun gregorianMonthLength(y: Int, m: Int): Int = when (m) {
        2 -> if (isGregorianLeap(y)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun fromMillis(millis: Long, tz: TimeZone = TimeZone.getDefault()): JalaliDate {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = millis
        return gregorianToJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    fun today(): JalaliDate = fromMillis(System.currentTimeMillis())

    fun todayJdn(): Int = today().toJdn()

    fun weekdayPersian(millis: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = millis
        val jdn = gregorianToJdn(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return weekDayNames[weekDayOfJdn(jdn)]
    }

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) sb.append(if (c in '0'..'9') '۰' + (c - '0') else c)
        return sb.toString()
    }

    fun fa(n: Int): String = toPersianDigits(n.toString())

    /** «شنبه ۱۲ مهر ۱۴۰۵» با ارقام فارسی */
    fun fullPersian(d: JalaliDate): String =
        "${weekDayNames[d.weekDay()]} ${fa(d.day)} ${d.monthName()} ${fa(d.year)}"
}
