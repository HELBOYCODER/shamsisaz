package com.shamsisaz.jalali

import android.content.Context

/** تنظیمات ساده اپ */
object Prefs {
    private const val FILE = "shamsisaz_prefs"
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun statusNotification(c: Context) = sp(c).getBoolean("status_notification", false)
    fun setStatusNotification(c: Context, v: Boolean) = sp(c).edit().putBoolean("status_notification", v).apply()

    /** نمایش تاریخ شمسی روی صفحه با سرویس دسترس‌پذیری */
    fun screenOverlay(c: Context) = sp(c).getBoolean("screen_overlay", true)
    fun setScreenOverlay(c: Context, v: Boolean) = sp(c).edit().putBoolean("screen_overlay", v).apply()

    /** ماهِ نمایش‌داده‌شده در ویجت ماهانه، به‌صورت جابه‌جایی نسبت به ماه جاری */
    fun widgetMonthOffset(c: Context) = sp(c).getInt("widget_month_offset", 0)
    fun setWidgetMonthOffset(c: Context, v: Int) = sp(c).edit().putInt("widget_month_offset", v).apply()
}
