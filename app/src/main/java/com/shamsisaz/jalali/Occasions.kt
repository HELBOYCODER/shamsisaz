package com.shamsisaz.jalali

/**
 * مناسبت‌ها و تعطیلات رسمی ایران.
 *  - مناسبت‌های ثابت شمسی: دقیق.
 *  - مناسبت‌های قمری: برای سال ۱۴۰۵ از جدول رسمی کشور؛ برای سال‌های دیگر از تقویم قمری جدولی
 *    محاسبه می‌شود و ممکن است تا ±۱ روز با اعلام رسمی (رؤیت هلال) فرق کند.
 */
object Occasions {

    data class Occasion(val title: String, val holiday: Boolean, val approximate: Boolean = false)

    private val fixed: Map<Int, List<Pair<String, Boolean>>> by lazy {
        val m = HashMap<Int, MutableList<Pair<String, Boolean>>>()
        fun add(month: Int, day: Int, title: String, holiday: Boolean = false) {
            m.getOrPut(month * 100 + day) { mutableListOf() }.add(title to holiday)
        }
        add(1, 1, "آغاز نوروز", true); add(1, 2, "عید نوروز", true); add(1, 3, "عید نوروز", true)
        add(1, 4, "عید نوروز", true); add(1, 12, "روز جمهوری اسلامی ایران", true); add(1, 13, "روز طبیعت", true)
        add(1, 18, "روز سلامتی"); add(1, 29, "روز ارتش جمهوری اسلامی")
        add(2, 1, "روز بزرگداشت سعدی"); add(2, 10, "روز ملی خلیج فارس"); add(2, 12, "روز معلم")
        add(2, 25, "بزرگداشت فردوسی و روز زبان فارسی"); add(2, 28, "بزرگداشت خیام")
        add(3, 3, "فتح خرمشهر"); add(3, 14, "رحلت امام خمینی", true); add(3, 15, "قیام ۱۵ خرداد", true)
        add(4, 14, "روز قلم"); add(4, 30, "روز خلبان")
        add(5, 9, "روز اهدای خون")
        add(6, 2, "آغاز هفته دولت"); add(6, 4, "روز کارمند"); add(6, 17, "قیام ۱۷ شهریور")
        add(6, 21, "روز سینما"); add(6, 27, "روز شعر و ادب فارسی")
        add(7, 8, "بزرگداشت مولوی"); add(7, 13, "روز ملی کودک"); add(7, 20, "بزرگداشت حافظ")
        add(8, 13, "روز دانش‌آموز"); add(8, 30, "روز قهرمان ملی")
        add(9, 9, "روز زن"); add(9, 16, "روز دانشجو"); add(9, 30, "شب یلدا")
        add(10, 2, "روز پدر"); add(10, 20, "شهادت امیرکبیر"); add(10, 30, "روز جوان")
        add(11, 22, "پیروزی انقلاب اسلامی", true)
        add(12, 5, "بزرگداشت خواجه نصیر"); add(12, 15, "روز درختکاری"); add(12, 25, "بزرگداشت پروین اعتصامی")
        add(12, 29, "ملی شدن صنعت نفت", true)
        m
    }

    /** رویدادهای قمری: (ماه، روز) → (عنوان، تعطیل؟). روز ۰ = آخر ماه */
    private val lunar: Map<Int, List<Pair<String, Boolean>>> by lazy {
        val m = HashMap<Int, MutableList<Pair<String, Boolean>>>()
        fun add(month: Int, day: Int, title: String, holiday: Boolean = false) {
            m.getOrPut(month * 100 + day) { mutableListOf() }.add(title to holiday)
        }
        add(1, 1, "آغاز ماه محرم"); add(1, 9, "تاسوعای حسینی", true); add(1, 10, "عاشورای حسینی", true)
        add(2, 20, "اربعین حسینی", true); add(2, 28, "رحلت پیامبر اکرم (ص) و شهادت امام حسن (ع)", true)
        add(2, 0, "شهادت امام رضا (ع)", true)
        add(3, 8, "شهادت امام حسن عسکری (ع)", true); add(3, 17, "ولادت پیامبر اکرم (ص) و امام صادق (ع)", true)
        add(6, 3, "شهادت حضرت فاطمه زهرا (س)", true)
        add(7, 13, "ولادت امام علی (ع) و روز پدر", true); add(7, 27, "مبعث پیامبر اکرم (ص)", true)
        add(8, 15, "ولادت حضرت مهدی (عج)", true)
        add(9, 21, "شهادت امام علی (ع)", true)
        add(10, 1, "عید سعید فطر", true); add(10, 2, "تعطیل به مناسبت عید فطر", true)
        add(10, 25, "شهادت امام صادق (ع)", true)
        add(12, 10, "عید سعید قربان", true); add(12, 18, "عید سعید غدیر خم", true)
        m
    }

    /** جدول رسمی تعطیلات قمری ۱۴۰۵ (ماه*100+روز شمسی → عنوان) */
    private val official1405: Map<Int, String> = mapOf(
        101 to "عید سعید فطر", 102 to "تعطیل به مناسبت عید فطر",
        125 to "شهادت امام جعفر صادق (ع)",
        306 to "عید سعید قربان", 314 to "عید سعید غدیر خم",
        403 to "تاسوعای حسینی", 404 to "عاشورای حسینی",
        513 to "اربعین حسینی", 521 to "رحلت پیامبر اکرم (ص) و شهادت امام حسن (ع)",
        522 to "شهادت امام رضا (ع)", 530 to "شهادت امام حسن عسکری (ع)",
        608 to "ولادت پیامبر اکرم (ص) و امام صادق (ع)",
        822 to "شهادت حضرت فاطمه زهرا (س)",
        1002 to "ولادت امام علی (ع) و روز پدر", 1016 to "مبعث پیامبر اکرم (ص)",
        1104 to "ولادت حضرت مهدی (عج)",
        1209 to "شهادت امام علی (ع)", 1219 to "عید سعید فطر", 1220 to "تعطیل به مناسبت عید فطر"
    )

    fun forDay(j: JalaliConverter.JalaliDate): List<Occasion> {
        val out = ArrayList<Occasion>(3)
        fixed[j.month * 100 + j.day]?.forEach { out.add(Occasion(it.first, it.second)) }
        if (j.year == 1405) {
            official1405[j.month * 100 + j.day]?.let { title ->
                if (out.none { it.title == title }) out.add(Occasion(title, true))
            }
        } else {
            val h = HijriCalendar.fromJdn(j.toJdn())
            val last = HijriCalendar.monthLength(h.year, h.month)
            val key = if (h.day == last) h.month * 100 else h.month * 100 + h.day
            lunar[key]?.forEach { out.add(Occasion(it.first, it.second, approximate = true)) }
            if (key != h.month * 100 + h.day) lunar[h.month * 100 + h.day]?.forEach { out.add(Occasion(it.first, it.second, true)) }
        }
        return out
    }

    /** جمعه‌ها هم تعطیل‌اند */
    fun isHoliday(j: JalaliConverter.JalaliDate): Boolean =
        j.weekDay() == 6 || forDay(j).any { it.holiday }

    fun isOfficialHoliday(j: JalaliConverter.JalaliDate): Boolean = forDay(j).any { it.holiday }

    fun hijriLabel(j: JalaliConverter.JalaliDate): String {
        val h = HijriCalendar.fromJdn(j.toJdn())
        return "${JalaliConverter.fa(h.day)} ${HijriCalendar.monthNames[h.month - 1]} ${JalaliConverter.fa(h.year)}"
    }

    fun gregorianLabel(j: JalaliConverter.JalaliDate): String {
        val g = j.toGregorian()
        val names = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        return "${g.day} ${names[g.month - 1]} ${g.year}"
    }
}
