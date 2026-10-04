package com.shamsisaz.jalali

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Locale

/** کمکی برای شمسی کردن کل سیستم — مخصوص سامسونگ A336E و شیائومی */
object SystemLocaleHelper {

    fun currentLocaleTag(): String = Locale.getDefault().toLanguageTag() // مثلا en-US
    fun isPersianLocale(): Boolean = Locale.getDefault().language == "fa"

    /** آیا بعد از تغییر لوکال، تقویم خودکار شمسی می‌شود؟ */
    fun willSystemCalendarBeJalali(): Boolean = isPersianLocale()

    /** تلاش برای ست کردن لوکال به fa-IR از داخل اپ — بدون روت
     *  روی اندروید 13+ بهترین شانس را دارد (LocaleManager per-app).
     *  برای system-wide روی سامسونگ معمولاً نیاز به تایید دستی یا ADB/Shizuku دارد.
     *  این متد حداقل اپ خودمان و هر اپی که per-app locale را ساپورت کند شمسی می‌کند.
     */
    fun trySetAppLocaleToPersian(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                val lm = context.getSystemService(android.app.LocaleManager::class.java)
                lm?.applicationLocales = android.os.LocaleList.forLanguageTags("fa-IR")
                true
            } else {
                // اندروید 7 تا 12: تغییر لوکال اپ با AppCompat
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags("fa-IR")
                )
                true
            }
        } catch (_: Exception) { false }
        }

    /** دستور ADB که کاربر می‌تواند با کامپیوتر بزند تا کل سیستم شمسی شود */
    fun adbCommand(): String = "adb shell cmd locale set -l fa-IR\n# یا دستی: Settings > General management > Language > فارسی"

    /** تلاش برای ست system-wide با WRITE_SECURE_SETTINGS (اگر با ADB/Shizuku داده شده باشد) */
    fun trySetSystemLocaleViaSettings(context: Context): Boolean {
        return try {
            // این فقط اگر با `adb shell pm grant ... WRITE_SECURE_SETTINGS` یا Shizuku داده شده باشد موفق می‌شود
            // در غیر این صورت SecurityException می‌دهد و false برمی‌گردانیم تا fallback به تنظیمات دستی برویم
            @Suppress("DEPRECATION")
            Settings.System.putString(context.contentResolver, "system_locales", "fa-IR,fa,en-US")
            true
        } catch (_: SecurityException) { false }
        catch (_: Exception) { false }
    }

    /** کپی دستور ADB در کلیپ‌بورد */
    fun copyAdbCommand(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("ADB", adbCommand()))
    }
}
