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

    /** باز کردن تنظیمات زبان سیستم — برای تبدیل کل گوشی */
    fun openLanguageSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_LOCALE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            Intent("android.settings.LOCALE_SETTINGS"),
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) { }
        }
        // fallback: تنظیمات عمومی
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) { }
    }

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
