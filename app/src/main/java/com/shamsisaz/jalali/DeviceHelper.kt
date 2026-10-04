package com.shamsisaz.jalali

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Locale

object DeviceHelper {

    fun brandLabel(): String {
        val b = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val m = Build.MODEL
        return when {
            b.contains("samsung") -> "سامسونگ — $m"
            b.contains("xiaomi") || b.contains("redmi") || b.contains("poco") -> "شیائومی — $m"
            else -> "${Build.MANUFACTURER} $m"
        }
    }

    fun isSamsung(): Boolean = Build.MANUFACTURER.lowercase().contains("samsung")
    fun isXiaomi(): Boolean {
        val b = Build.MANUFACTURER.lowercase()
        return b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
    }

    fun systemHint(): String = when {
        isXiaomi() -> "شیائومی: تنظیمات → زبان و منطقه → فارسی را انتخاب کنید تا تقویم خودکار شمسی شود."
        isSamsung() -> "سامسونگ: تنظیمات → مدیریت عمومی → زبان → فارسی — سپس تقویم را باز کنید."
        else -> "پیشنهاد: زبان سیستم را فارسی کنید تا تاریخ‌ها در تقویم خودکار فارسی شوند."
    }

    /** باز کردن تنظیمات زبان سیستم — مخصوص A336E سامسونگ: اول Samsung language picker، بعد Locale */
    fun openLanguageSettings(context: Context) {
        // 1) تلاش: Samsung One UI language screen (کارترین روی A336E)
        val samsungIntents = listOf(
            Intent("com.android.settings.LANGUAGE_SETTINGS"),
            Intent("com.samsung.android.settings.LANGUAGE_SETTINGS"),
        )
        for (intent in samsungIntents) {
            try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); return } catch (_: Exception) {}
        }
        // 2) استاندارد اندروید
        val intents = listOf(
            Intent(Settings.ACTION_LOCALE_SETTINGS),
            Intent("android.settings.LOCALE_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); return } catch (_: Exception) {}
        }
        try { context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
    }

    /** راهنمای دقیق قدم‌به‌قدم برای A336E */
    fun stepsForA336E(): String = if (isSamsung()) {
        "سامسونگ Galaxy A33 5G (SM-A336E):\n" +
        "1) تنظیمات → مدیریت عمومی → زبان\n" +
        "2) افزودن زبان → فارسی\n" +
        "3) فارسی را به بالای لیست بکشید (اول)\n" +
        "4) اعمال / Done → گوشی خودکار شمسی می‌شود\n\n" +
        "میانبر بالا مستقیم شما را به همین صفحه می‌برد."
    } else systemHint()

    fun openCalendarApp(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("content://com.android.calendar/time")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { }
        }
    }
}
