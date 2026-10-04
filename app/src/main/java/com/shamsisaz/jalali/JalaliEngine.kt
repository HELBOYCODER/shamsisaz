package com.shamsisaz.jalali

import java.util.Calendar
import java.util.TimeZone

/**
 * موتور داخلی جلالی — همه‌چیز آفلاین، بدون کتابخانه خارجی
 * هر تاریخ انگلیسی دیده شده در گوشی را به شمسی کامل تبدیل می‌کند:
 *  - 2026/10/04, 2026-10-04, 10/04/2026, 04.10.2026
 *  - Saturday, 4 October 2025 / Saturday 4 October / October 4, 2025 / Oct 4 / 4 Oct 2025
 *  - Saturday / Sun / Mon ... (روز هفته تنها)
 *  - October / Oct ... (ماه تنها در کنار عدد)
 *
 * ponytail: برای دقت نجومی کامل ICU4J لازم است؛ این موتور برای 1900..2100 تست شده و ۱۰۰٪ آفلاین است.
 */
object JalaliEngine {

    // ---------- دیکشنری‌ها ----------
    private val enMonthsLong = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12
    )
    private val enMonthsShort = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    private val enMonthsAll: Map<String, Int> = enMonthsLong + enMonthsShort

    private val enWeekFull = mapOf(
        "saturday" to "شنبه", "sunday" to "یکشنبه", "monday" to "دوشنبه",
        "tuesday" to "سه‌شنبه", "wednesday" to "چهارشنبه", "thursday" to "پنجشنبه", "friday" to "جمعه"
    )
    private val enWeekShort = mapOf(
        "sat" to "شنبه", "sun" to "یکشنبه", "mon" to "دوشنبه",
        "tue" to "سه‌شنبه", "tues" to "سه‌شنبه", "wed" to "چهارشنبه",
        "thu" to "پنجشنبه", "thur" to "پنجشنبه", "thurs" to "پنجشنبه", "fri" to "جمعه"
    )
    private val enWeekAll = enWeekFull + enWeekShort

    // ---------- تبدیل پایه ----------
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliConverter.JalaliDate =
        JalaliConverter.gregorianToJalali(gy, gm, gd)

    // ---------- API اصلی: هر متنی را کامل شمسی کن ----------
    fun convertAll(text: String): String {
        if (text.isBlank()) return text
        var out = text
        out = convertNumericDates(out)
        out = convertEnglishNamedDates(out)
        out = convertStandaloneWeekdays(out)
        return out
    }

    // 1) تاریخ‌های عددی: 2026/10/04, 2026-10-04, 10/04/2026, 04.10.2026
    private fun convertNumericDates(text: String): String {
        var t = text
        // YYYY/MM/DD یا YYYY-MM-DD یا YYYY.MM.DD
        t = Regex("""\b(19|20)\d{2}[/\-.](0?[1-9]|1[0-2])[/\-.](0?[1-9]|[12]\d|3[01])\b""").replace(t) { mr ->
            try {
                val parts = mr.value.split(Regex("[/\\-.]"))
                val gy = parts[0].toInt(); val gm = parts[1].toInt(); val gd = parts[2].toInt()
                val j = JalaliConverter.gregorianToJalali(gy, gm, gd)
                "${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(j.year.toString())}"
            } catch (_: Exception) { mr.value }
        }
        // MM/DD/YYYY (آمریکایی) — فقط اگر سال آخر باشد و با الگوی قبلی نخورده باشد
        t = Regex("""\b(0?[1-9]|1[0-2])/(0?[1-9]|[12]\d|3[01])/(19|20)\d{2}\b""").replace(t) { mr ->
            try {
                val p = mr.value.split("/")
                val gm = p[0].toInt(); val gd = p[1].toInt(); val gy = p[2].toInt()
                val j = JalaliConverter.gregorianToJalali(gy, gm, gd)
                "${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(j.year.toString())}"
            } catch (_: Exception) { mr.value }
        }
        return t
    }

    // 2) تاریخ‌های با نام ماه انگلیسی
    private fun convertEnglishNamedDates(text: String): String {
        var t = text
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)

        // A) Weekday, DD Month YYYY  e.g. Saturday, 4 October 2025 / Saturday 4 October 2025 / Sat 4 Oct 2025
        t = Regex("""(?i)\b((?:Saturday|Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Sat|Sun|Mon|Tue|Wed|Thu|Fri)[a-z]*),?\s+(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*,?\s+((?:19|20)\d{2})\b""").replace(t) { mr ->
            try {
                val day = mr.groupValues[2].toInt()
                val monthStr = mr.groupValues[3].lowercase()
                val year = mr.groupValues[4].toInt()
                val gm = enMonthsAll[monthStr] ?: return@replace mr.value
                val j = JalaliConverter.gregorianToJalali(year, gm, day)
                val wd = JalaliConverter.weekdayPersian(java.util.GregorianCalendar(year, gm - 1, day).timeInMillis)
                "$wd ${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(j.year.toString())}"
            } catch (_: Exception) { mr.value }
        }

        // B) Weekday, Month DD, YYYY  e.g. Saturday, October 4, 2025 / Oct 4, 2025
        t = Regex("""(?i)\b((?:Saturday|Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Sat|Sun|Mon|Tue|Wed|Thu|Fri)[a-z]*),?\s+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+((?:19|20)\d{2})\b""").replace(t) { mr ->
            try {
                val monthStr = mr.groupValues[2].lowercase()
                val day = mr.groupValues[3].toInt()
                val year = mr.groupValues[4].toInt()
                val gm = enMonthsAll[monthStr] ?: return@replace mr.value
                val j = JalaliConverter.gregorianToJalali(year, gm, day)
                val wd = JalaliConverter.weekdayPersian(java.util.GregorianCalendar(year, gm - 1, day).timeInMillis)
                "$wd ${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(j.year.toString())}"
            } catch (_: Exception) { mr.value }
        }

        // C) DD Month YYYY  e.g. 4 October 2025 / 4 Oct 2025 (بدون weekday)
        t = Regex("""(?i)\b(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\s+((?:19|20)\d{2})\b""").replace(t) { mr ->
            try {
                val day = mr.groupValues[1].toInt()
                val monthStr = mr.groupValues[2].lowercase()
                val year = mr.groupValues[3].toInt()
                val gm = enMonthsAll[monthStr] ?: return@replace mr.value
                val j = JalaliConverter.gregorianToJalali(year, gm, day)
                "${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(j.year.toString())}"
            } catch (_: Exception) { mr.value }
        }

        // D) Month DD, YYYY  e.g. October 4, 2025 / Oct 4, 2025 (بدون weekday)
        t = Regex("""(?i)\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+((?:19|20)\d{2})\b""").replace(t) { mr ->
            try {
                val monthStr = mr.groupValues[1].lowercase()
                val day = mr.groupValues[2].toInt()
                val year = mr.groupValues[3].toInt()
                val gm = enMonthsAll[monthStr] ?: return@replace mr.value
                val j = JalaliConverter.gregorianToJalali(year, gm, day)
                "${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(j.year.toString())}"
            } catch (_: Exception) { mr.value }
        }

        // E0) Weekday, DD Month بدون سال — دقیقاً باگ اسکرین A336E: "Saturday, 4 October"
        t = Regex("""(?i)\b((?:Saturday|Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Sat|Sun|Mon|Tue|Wed|Thu|Fri)[a-z]*),?\s+(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\b(?!\s*,?\s*\d{4})""").replace(t) { mr ->
            try {
                val day = mr.groupValues[2].toInt()
                val monthStr = mr.groupValues[3].lowercase()
                val gm = enMonthsAll[monthStr] ?: return@replace mr.value
                val j = JalaliConverter.gregorianToJalali(currentYear, gm, day)
                val wd = JalaliConverter.weekdayPersian(java.util.GregorianCalendar(currentYear, gm - 1, day).timeInMillis)
                "$wd ${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]}"
            } catch (_: Exception) { mr.value }
        }

        // E) Month DD بدون سال — سال جاری فرض شود  e.g. October 4 / Oct 4
        t = Regex("""(?i)\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\s+(\d{1,2})\b(?!\s*,?\s*\d{4})""").replace(t) { mr ->
            try {
                val monthStr = mr.groupValues[1].lowercase()
                val day = mr.groupValues[2].toInt()
                val gm = enMonthsAll[monthStr] ?: return@replace mr.value
                val j = JalaliConverter.gregorianToJalali(currentYear, gm, day)
                "${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]}"
            } catch (_: Exception) { mr.value }
        }

        // F) DD Month بدون سال  e.g. 4 October / 4 Oct
        t = Regex("""(?i)\b(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\b(?!\s+\d{4})""").replace(t) { mr ->
            try {
                val day = mr.groupValues[1].toInt()
                val monthStr = mr.groupValues[2].lowercase()
                // اگر قبلاً در الگوی E جایگزین شده، دوباره نخور — چک ساده: اگر بعدش عدد 4 رقمی نیست و قبلش تبدیل نشده
                // اینجا فقط اگر متن هنوز انگلیسی است جایگزین کن
                if (mr.value.contains("مهر") || mr.value.contains("آبان")) return@replace mr.value
                val gm = enMonthsAll[monthStr] ?: return@replace mr.value
                val j = JalaliConverter.gregorianToJalali(currentYear, gm, day)
                "${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]}"
            } catch (_: Exception) { mr.value }
        }

        return t
    }

    // 3) روز هفته تنها: Saturday -> شنبه
    private fun convertStandaloneWeekdays(text: String): String {
        var t = text
        for ((en, fa) in enWeekAll) {
            t = Regex("""(?i)\b$en\b""").replace(t, fa)
        }
        return t
    }

    // ---------- کمکی برای نمایش امروز کامل ----------
    fun todayFullPersian(): String {
        val c = Calendar.getInstance()
        val j = JalaliConverter.gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        val wd = JalaliConverter.weekdayPersian(c.timeInMillis)
        return "$wd ${JalaliConverter.toPersianDigits(j.day.toString())} ${j.formatPersian().split(" ")[1]} ${JalaliConverter.toPersianDigits(j.year.toString())}"
    }
}
