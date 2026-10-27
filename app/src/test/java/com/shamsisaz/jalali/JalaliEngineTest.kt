package com.shamsisaz.jalali

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliEngineTest {

    private fun c(s: String) = JalaliEngine.convertAll(s)

    @Test fun isoAndSlash() {
        assertEquals("۵ آبان ۱۴۰۵", c("2026-10-27".replace("27", "27")).let { c("2026-10-27") })
        assertEquals("جلسه ۵ آبان ۱۴۰۵ ساعت 10:00", c("جلسه 2026/10/27 ساعت 10:00"))
        assertEquals("۵ آبان ۱۴۰۵", c("10/27/2026"))
        assertEquals("۵ آبان ۱۴۰۵", c("27/10/2026"))
    }

    @Test fun namedDates() {
        assertEquals("سه‌شنبه ۵ آبان ۱۴۰۵", c("Tuesday, 27 October 2026"))
        assertEquals("سه‌شنبه ۵ آبان ۱۴۰۵", c("Mon, 27 Oct 2026")) // روز هفته‌ی نوشته‌شده غلط بود؛ از تاریخ محاسبه می‌شود
        assertEquals("۵ آبان ۱۴۰۵", c("October 27, 2026"))
        assertEquals("۵ آبان ۱۴۰۵", c("27th Oct 2026"))
        assertEquals("۱ فروردین ۱۴۰۵", c("March 21, 2026"))
    }

    @Test fun withoutYearUsesCurrentYear() {
        val out = c("Meeting 4 October")
        assertTrue(out, out.startsWith("Meeting ") && out.contains("مهر") && !out.contains("October"))
    }

    @Test fun invalidDatesAreLeftAlone() {
        assertEquals("2026-02-30", c("2026-02-30"))
        assertEquals("31 November 2026", c("31 November 2026"))
        assertEquals("version 1.2.3", c("version 1.2.3"))
    }

    @Test fun noFalsePositivesInProse() {
        assertEquals("You may go now", c("You may go now"))
        assertEquals("Sunday drive".replace("Sunday", "یکشنبه"), c("Sunday drive"))
        assertEquals("Sun Microsystems", c("Sun Microsystems"))
        assertEquals("call 123-456-7890", c("call 123-456-7890"))
        assertEquals("ip 192.168.1.1", c("ip 192.168.1.1"))
    }

    @Test fun detect() {
        assertTrue(JalaliEngine.containsEnglishDate("Due Oct 5, 2026"))
        assertFalse(JalaliEngine.containsEnglishDate("سلام"))
    }
}
