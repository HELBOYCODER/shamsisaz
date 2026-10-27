package com.shamsisaz.jalali

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.PersianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliConverterTest {

    @Test fun knownAnchors() {
        // امروزِ این پروژه و چند تاریخ مرجع
        assertEquals(JalaliConverter.JalaliDate(1405, 8, 5), JalaliConverter.gregorianToJalali(2026, 10, 27))
        assertEquals(JalaliConverter.JalaliDate(1405, 1, 1), JalaliConverter.gregorianToJalali(2026, 3, 21))
        assertEquals(JalaliConverter.JalaliDate(1403, 1, 1), JalaliConverter.gregorianToJalali(2024, 3, 20))
        assertEquals(JalaliConverter.JalaliDate(1357, 11, 22), JalaliConverter.gregorianToJalali(1979, 2, 11))
        assertEquals(JalaliConverter.JalaliDate(1404, 12, 29), JalaliConverter.gregorianToJalali(2026, 3, 20))
        assertEquals(JalaliConverter.GregorianDate(2026, 10, 27), JalaliConverter.jalaliToGregorian(1405, 8, 5))
    }

    @Test fun leapYears() {
        assertTrue(JalaliConverter.isLeap(1403))
        assertTrue(JalaliConverter.isLeap(1399))
        assertTrue(!JalaliConverter.isLeap(1404))
        assertEquals(30, JalaliConverter.monthLength(1403, 12))
        assertEquals(29, JalaliConverter.monthLength(1404, 12))
    }

    @Test fun weekdayMatchesJavaTime() {
        var d = LocalDate.of(1990, 1, 1)
        val end = LocalDate.of(2060, 1, 1)
        while (d.isBefore(end)) {
            val jdn = JalaliConverter.gregorianJdn(d.year, d.monthValue, d.dayOfMonth)
            // java: Monday=1..Sunday=7 → شنبه=0
            val expected = (d.dayOfWeek.value + 1) % 7
            assertEquals("weekday $d", expected, JalaliConverter.weekDayOfJdn(jdn))
            d = d.plusDays(1)
        }
    }

    /** مقایسهٔ روز‌به‌روز با ICU4J برای ۱۳۰۰ تا ۱۵۰۰ شمسی */
    @Test fun matchesIcu4jEveryDay() {
        var d = LocalDate.of(1921, 3, 21)
        val end = LocalDate.of(2121, 3, 21)
        var mismatches = 0
        val first = StringBuilder()
        val tz = com.ibm.icu.util.TimeZone.getTimeZone("UTC")
        while (d.isBefore(end)) {
            val millis = d.toEpochDay() * 86_400_000L + 43_200_000L
            val p = PersianCalendar(tz)
            p.timeInMillis = millis
            val icu = JalaliConverter.JalaliDate(p.get(Calendar.YEAR), p.get(Calendar.MONTH) + 1, p.get(Calendar.DAY_OF_MONTH))
            val mine = JalaliConverter.gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
            if (icu != mine) {
                mismatches++
                if (first.length < 400) first.append("$d icu=$icu mine=$mine\n")
            }
            // رفت‌وبرگشت
            val back = mine.toGregorian()
            assertEquals("roundtrip $d", JalaliConverter.GregorianDate(d.year, d.monthValue, d.dayOfMonth), back)
            d = d.plusDays(1)
        }
        println("ICU mismatches: $mismatches\n$first")
        assertEquals("تفاوت با ICU4J:\n$first", 0, mismatches)
    }

    @Test fun persianDigits() {
        assertEquals("۱۴۰۵/۰۸/۰۵", JalaliConverter.toPersianDigits("1405/08/05"))
        assertEquals("سه‌شنبه ۵ آبان ۱۴۰۵", JalaliConverter.fullPersian(JalaliConverter.JalaliDate(1405, 8, 5)))
    }
}
