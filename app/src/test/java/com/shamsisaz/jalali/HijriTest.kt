package com.shamsisaz.jalali

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.IslamicCalendar
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HijriTest {
    /** تقویم جدولی ما باید دقیقاً با «اسلامی مدنی» ICU4J یکی باشد */
    @Test fun matchesIcuCivilEveryDay() {
        val tz = com.ibm.icu.util.TimeZone.getTimeZone("UTC")
        var d = LocalDate.of(1950, 1, 1)
        val end = LocalDate.of(2100, 1, 1)
        var bad = 0
        val sb = StringBuilder()
        while (d.isBefore(end)) {
            val ic = IslamicCalendar(tz); ic.calculationType = IslamicCalendar.CalculationType.ISLAMIC_CIVIL
            ic.timeInMillis = d.toEpochDay() * 86_400_000L + 43_200_000L
            val icu = Triple(ic.get(Calendar.YEAR), ic.get(Calendar.MONTH) + 1, ic.get(Calendar.DAY_OF_MONTH))
            val h = HijriCalendar.fromJdn(JalaliConverter.gregorianJdn(d.year, d.monthValue, d.dayOfMonth))
            if (icu != Triple(h.year, h.month, h.day)) { bad++; if (sb.length < 300) sb.append("$d icu=$icu mine=$h\n") }
            d = d.plusDays(1)
        }
        assertEquals(sb.toString(), 0, bad)
    }
}
