package com.shamsisaz.jalali

/** تقویم قمری جدولی (مدنی) — تقریبی؛ تقویم رسمی ایران با رؤیت هلال تا ±۱ روز فرق دارد. */
object HijriCalendar {

    data class HijriDate(val year: Int, val month: Int, val day: Int)

    val monthNames = arrayOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )

    private const val EPOCH_JDN = 1948440 // ۱ محرم ۱ هجری (مدنی/جمعه)

    fun fromJdn(jdn: Int): HijriDate {
        val days = jdn - EPOCH_JDN
        val cycle = Math.floorDiv(days, 10631)
        val dayInCycle = days - cycle * 10631
        // هر ۳۰ سال: ۱۱ کبیسه
        var year = 0
        var rem = dayInCycle
        while (true) {
            val len = if (isLeap(year + 1)) 355 else 354
            if (rem < len) break
            rem -= len
            year++
        }
        val y = cycle * 30 + year + 1
        var m = 1
        while (m < 12 && rem >= monthLength(y, m)) {
            rem -= monthLength(y, m)
            m++
        }
        return HijriDate(y, m, rem + 1)
    }

    fun isLeap(y: Int): Boolean = Math.floorMod(14 + 11 * y, 30) < 11
    fun monthLength(y: Int, m: Int): Int = if (m % 2 == 1) 30 else if (m == 12 && isLeap(y)) 30 else 29
}
