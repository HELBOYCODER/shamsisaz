package com.shamsisaz.jalali

import java.util.Calendar

/**
 * تبدیل تاریخ‌های میلادیِ داخل یک متن به شمسی — کاملاً آفلاین و بدون وابستگی به اندروید.
 *
 * الگوهای پشتیبانی‌شده (با یا بدون روز هفته در ابتدا):
 *  - 2026-10-27 · 2026/10/27 · 2026.10.27
 *  - 10/27/2026 (آمریکایی) · 27/10/2026 (اگر عدد اول بزرگ‌تر از ۱۲ باشد)
 *  - 27 October 2026 · Tuesday, 27 Oct 2026
 *  - October 27, 2026 · Oct 27
 *  - 27 October (بدون سال → سال جاری)
 * روز هفته‌ی نوشته‌شده در متن نادیده گرفته می‌شود و از خود تاریخ محاسبه می‌شود.
 * نام کامل روزهای هفته که تنها آمده باشند (Monday) به فارسی برمی‌گردند.
 */
object JalaliEngine {

    private val months = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sept" to 9, "sep" to 9,
        "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12
    )
    private val weekdays = mapOf(
        "saturday" to 0, "sunday" to 1, "monday" to 2, "tuesday" to 3,
        "wednesday" to 4, "thursday" to 5, "friday" to 6
    )

    private const val MONTH_RE =
        "(?:January|February|March|April|May|June|July|August|September|October|November|December|" +
            "Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec)\\.?"
    private const val WD_RE =
        "(?:Saturday|Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Sat|Sun|Mon|Tues|Tue|Wed|Thurs|Thu|Fri)\\.?"
    private const val YEAR_RE = "(?:19|20)\\d{2}"
    private const val DAY_RE = "\\d{1,2}(?:st|nd|rd|th)?"
    private val opts = setOf(RegexOption.IGNORE_CASE)

    // 2026-10-27 / 2026/10/27 / 2026.10.27
    private val isoRe = Regex("(?<![\\d/.\\-])($YEAR_RE)([/\\-.])(\\d{1,2})\\2(\\d{1,2})(?![\\d/.\\-]\\d)")
    // 10/27/2026 یا 27/10/2026
    private val slashRe = Regex("(?<![\\d/.\\-])(\\d{1,2})/(\\d{1,2})/($YEAR_RE)(?![\\d/])")
    // [Weekday,] 27 October [2026]
    private val dmyRe = Regex("(?:($WD_RE),?\\s+)?($DAY_RE)\\s+($MONTH_RE)(?:,?\\s+($YEAR_RE))?(?![\\w])", opts)
    // [Weekday,] October 27[, 2026]
    private val mdyRe = Regex("(?:($WD_RE),?\\s+)?($MONTH_RE)\\s+($DAY_RE)(?:,?\\s+($YEAR_RE))?(?![\\w\\d])", opts)
    private val weekdayRe = Regex("\\b(Saturday|Sunday|Monday|Tuesday|Wednesday|Thursday|Friday)\\b", opts)

    fun containsEnglishDate(text: String): Boolean =
        isoRe.containsMatchIn(text) || slashRe.containsMatchIn(text) ||
            dmyRe.containsMatchIn(text) || mdyRe.containsMatchIn(text) || weekdayRe.containsMatchIn(text)

    fun convertAll(text: String): String {
        if (text.isBlank()) return text
        var out = text
        out = isoRe.replace(out) { m ->
            val y = m.groupValues[1].toInt(); val mo = m.groupValues[3].toInt(); val d = m.groupValues[4].toInt()
            render(y, mo, d, withYear = true, withWeekday = false) ?: m.value
        }
        out = slashRe.replace(out) { m ->
            var a = m.groupValues[1].toInt(); var b = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt()
            // پیش‌فرض آمریکایی (ماه/روز)؛ اگر نشدنی بود جابه‌جا
            if (a > 12) { val t = a; a = b; b = t }
            render(y, a, b, withYear = true, withWeekday = false) ?: m.value
        }
        out = dmyRe.replace(out) { m ->
            val mo = months[m.groupValues[3].trim('.').lowercase()] ?: return@replace m.value
            val d = m.groupValues[2].filter { it.isDigit() }.toInt()
            val yStr = m.groupValues[4]
            val y = if (yStr.isEmpty()) currentYear() else yStr.toInt()
            render(y, mo, d, withYear = yStr.isNotEmpty(), withWeekday = m.groupValues[1].isNotEmpty()) ?: m.value
        }
        out = mdyRe.replace(out) { m ->
            val mo = months[m.groupValues[2].trim('.').lowercase()] ?: return@replace m.value
            val d = m.groupValues[3].filter { it.isDigit() }.toInt()
            val yStr = m.groupValues[4]
            val y = if (yStr.isEmpty()) currentYear() else yStr.toInt()
            render(y, mo, d, withYear = yStr.isNotEmpty(), withWeekday = m.groupValues[1].isNotEmpty()) ?: m.value
        }
        out = weekdayRe.replace(out) { JalaliConverter.weekDayNames[weekdays[it.value.lowercase()]!!] }
        return out
    }

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    private fun render(gy: Int, gm: Int, gd: Int, withYear: Boolean, withWeekday: Boolean): String? {
        if (gm !in 1..12 || gd < 1 || gd > JalaliConverter.gregorianMonthLength(gy, gm)) return null
        val jdn = JalaliConverter.gregorianJdn(gy, gm, gd)
        val j = JalaliConverter.fromJdn(jdn)
        val sb = StringBuilder()
        if (withWeekday) sb.append(JalaliConverter.weekDayNames[JalaliConverter.weekDayOfJdn(jdn)]).append(' ')
        sb.append(JalaliConverter.fa(j.day)).append(' ').append(j.monthName())
        if (withYear) sb.append(' ').append(JalaliConverter.fa(j.year))
        return sb.toString()
    }

    fun todayFullPersian(): String = JalaliConverter.fullPersian(JalaliConverter.today())
}
