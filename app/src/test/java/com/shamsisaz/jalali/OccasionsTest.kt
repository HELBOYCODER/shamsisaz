package com.shamsisaz.jalali

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccasionsTest {

    @Test fun nowruzAndFixedHolidays() {
        assertTrue(Occasions.isOfficialHoliday(JalaliConverter.JalaliDate(1404, 1, 1)))
        assertTrue(Occasions.isOfficialHoliday(JalaliConverter.JalaliDate(1404, 11, 22)))
        assertTrue(Occasions.isOfficialHoliday(JalaliConverter.JalaliDate(1404, 3, 14)))
        assertTrue(!Occasions.isOfficialHoliday(JalaliConverter.JalaliDate(1404, 5, 5)))
    }

    /** جدول رسمی ۱۴۰۵ (منبع: تقویم رسمی کشور) — ۲۳ روز تعطیل مناسبتی + ۱۴ خرداد دوگانه */
    @Test fun official1405() {
        val expected = listOf(
            1 to 1, 1 to 2, 1 to 3, 1 to 4, 1 to 12, 1 to 13, 1 to 25, 3 to 6, 3 to 14, 3 to 15, 4 to 3, 4 to 4,
            5 to 13, 5 to 21, 5 to 22, 5 to 30, 6 to 8, 8 to 22, 10 to 2, 10 to 16, 11 to 4, 11 to 22, 12 to 9, 12 to 19, 12 to 20, 12 to 29
        )
        val actual = ArrayList<Pair<Int, Int>>()
        for (m in 1..12) for (d in 1..JalaliConverter.monthLength(1405, m)) {
            if (Occasions.isOfficialHoliday(JalaliConverter.JalaliDate(1405, m, d))) actual.add(m to d)
        }
        assertEquals(expected, actual)
    }

    /**
     * محاسبهٔ قمری جدولی برای سال‌های دیگر باید حداکثر ±۱ روز با جدول رسمی ۱۴۰۵ فاصله داشته باشد
     * (همین معیار برای اعتبارسنجی صحت تقویم جدولی استفاده می‌شود).
     */
    @Test fun tabularLunarWithinOneDayOfOfficial1405() {
        // (ماه قمری، روز) → روز شمسی رسمی ۱۴۰۵ (ماه*100+روز)
        val official = mapOf(
            (1 to 9) to 403, (1 to 10) to 404, (2 to 20) to 513, (2 to 28) to 521,
            (3 to 17) to 608, (7 to 13) to 1002, (7 to 27) to 1016, (8 to 15) to 1104,
            (9 to 21) to 1209, (10 to 1) to 1219, (10 to 25) to 125, (12 to 10) to 306, (12 to 18) to 314
        )
        val found = HashMap<Pair<Int, Int>, Int>()
        for (m in 1..12) for (d in 1..JalaliConverter.monthLength(1405, m)) {
            val j = JalaliConverter.JalaliDate(1405, m, d)
            val h = HijriCalendar.fromJdn(j.toJdn())
            // ۱۴۰۵/۱/۱ = ۱ شوال و ۱۴۰۵/۱/۲۵ = ۲۵ شوال ۱۴۴۷؛ ۱۴۴۷ و ۱۴۴۸ را قبول کن
            val key = h.month to h.day
            if (key in official && key !in found) found[key] = m * 100 + d
        }
        val jdn = { code: Int -> JalaliConverter.jalaliToJdn(1405, code / 100, code % 100) }
        for ((k, code) in official) {
            val got = found[k] ?: continue
            val diff = Math.abs(jdn(got) - jdn(code))
            // اولین تطابق ممکن است متعلق به چرخهٔ قبلی/بعدی سال قمری باشد؛ فقط فاصلهٔ کمتر از ۱۵ روز را بسنجیم
            if (diff < 15) assertTrue("$k tabular=$got official=$code diff=$diff", diff <= 1)
        }
    }

    @Test fun hijriKnownDates() {
        // ۱ محرم ۱۴۴۸ در تقویم مدنی ۱۷ ژوئن ۲۰۲۶ است (رسمی ایران ۱۶ ژوئن)؛ عاشورا = ۴ تیر ۱۴۰۵
        val h = HijriCalendar.fromJdn(JalaliConverter.gregorianJdn(2026, 6, 17))
        assertEquals(Triple(1448, 1, 1), Triple(h.year, h.month, h.day))
    }
}
