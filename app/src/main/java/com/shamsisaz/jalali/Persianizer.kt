package com.shamsisaz.jalali

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * فارسی‌ساز کامل سیستم با شیزوکو («ارواح فارسی») 👻🇮🇷
 * تغییر لوکال کل گوشی از داخل خود گوشی، بدون کامپیوتر، بدون روت.
 *
 * چون سینتکس `cmd locale` بین اندروید ۷ تا ۱۶ فرق می‌کند، هر گام با چند
 * حالت مختلف امتحان می‌شود و اولین حالتی که exit code صفر بدهد قبول است.
 * همه‌چیز در لاگ زنده نمایش داده می‌شود تا اگر جایی نگرفت معلوم باشد.
 */
object Persianizer {

    data class StepResult(val ok: Boolean, val log: String)

    suspend fun fullPersianize(ctx: Context, log: (String) -> Unit = {}): StepResult =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            fun say(s: String) { sb.appendLine(s); try { log(s) } catch (_: Exception) {} }

            if (!ShizukuHelper.isInstalled(ctx)) {
                say("❌ شیزوکو نصب نیست — بدون آن تغییر سیستمی ممکن نیست.")
                return@withContext StepResult(false, sb.toString())
            }
            if (!ShizukuHelper.isBinderAlive()) {
                say("⚠️ شیزوکو روشن نیست — راهنمای روشن‌کردن را باز کنید.")
                return@withContext StepResult(false, sb.toString())
            }
            if (!ShizukuHelper.isPermissionGranted()) {
                say("🔑 دسترسی شیزوکو داده نشده.")
                return@withContext StepResult(false, sb.toString())
            }
            say("✅ شیزوکو آماده — شروع فارسی‌سازی…")

            var anyOk = false
            // userId مالک گوشی = appUid / 100000 (معمولاً ۰)
            val userId = try { android.os.Process.myUid() / 100000 } catch (_: Exception) { 0 }

            // ---- گام ۱: لوکال اپ‌های تقویم/ساعت به fa-IR ----
            val calendarPkgs = listOf(
                "com.samsung.android.calendar",
                "com.google.android.calendar",
                "com.android.calendar",
                "com.xiaomi.calendar",
                "com.miui.calendar",
                "com.sec.android.app.clockpackage",
                "com.google.android.deskclock"
            )
            for (pkg in calendarPkgs) {
                val variants = listOf(
                    arrayOf("cmd", "locale", "set-app-locales", "--user", userId.toString(), pkg, "fa-IR"),
                    arrayOf("cmd", "locale", "set-app-locales", pkg, "fa-IR"),
                    arrayOf("cmd", "locale", "set-app-locales", pkg, "--user", userId.toString(), "fa-IR")
                )
                var done = false
                var lastOut = ""
                for (v in variants) {
                    val (code, out) = ShizukuHelper.runShell(*v)
                    lastOut = out
                    if (code == 0) { done = true; break }
                    if (out.contains("Unknown command", true) || out.contains("Unknown cmd", true)) break
                }
                if (done) { say("✅ $pkg → فارسی شد"); anyOk = true }
                else say("⏭️ $pkg روی این گوشی نیست (${lastOut.take(80)})")
            }

            // ---- گام ۲: لوکال سیستمی ----
            run {
                val variants = listOf(
                    arrayOf("cmd", "locale", "set-system-locales", "fa-IR"),
                    arrayOf("cmd", "locale", "set-system-locales", "--user", userId.toString(), "fa-IR")
                )
                for (v in variants) {
                    val (code, out) = ShizukuHelper.runShell(*v)
                    if (code == 0) { say("✅ لوکال کل سیستم → fa-IR"); anyOk = true; break }
                    else say("⏭️ set-system-locales: ${out.take(120)}")
                }
            }

            // ---- گام ۳: settings put (اندروید ۷ تا ۱۲ + One UI/MIUI) ----
            val settingsTries = listOf(
                arrayOf("settings", "put", "system", "system_locales", "fa-IR,fa,en-US"),
                arrayOf("settings", "put", "global", "device_locales", "fa-IR"),
                arrayOf("settings", "put", "secure", "system_locales", "fa-IR")
            )
            for (t in settingsTries) {
                val (code, out) = ShizukuHelper.runShell(*t)
                if (code == 0) { say("✅ ${t[2]}:${t[3]} ست شد"); anyOk = true }
                else say("⏭️ ${t[3]}: ${out.take(100)}")
            }

            // ---- گام ۴: اعلام تغییر لوکال به سیستم + بستن تقویم تا دوباره با لوکال جدید باز شود ----
            run {
                ShizukuHelper.runShell("am", "broadcast", "-a", "android.intent.action.LOCALE_CHANGED")
                for (pkg in calendarPkgs) {
                    ShizukuHelper.runShell("am", "force-stop", pkg)
                }
                say("🔄 تقویم‌ها بسته شدند — با باز کردن دوباره، فارسی می‌آیند")
            }

            // ---- گام ۵: بررسی نهایی ----
            run {
                val (code, out) = ShizukuHelper.runShell("cmd", "locale", "get-system-locales")
                if (code == 0 && out.isNotBlank()) say("📍 لوکال سیستم الان: ${out.trim()}")
                else say("📍 لوکال اپ: ${java.util.Locale.getDefault().toLanguageTag()}")
            }

            // ---- گام ۶: تبدیل رویدادهای تقویم ----
            try {
                val conv = CalendarConverter(ctx)
                val res = conv.convertAll(addPrefix = true, convertBody = true, onProgress = null)
                say("📅 تقویم: ${res.converted} رویداد شمسی شد (${res.calendars} تقویم)")
                if (res.converted > 0) anyOk = true
            } catch (e: Exception) {
                say("⏭️ تبدیل تقویم: ${e.message}")
            }

            if (anyOk) say("\n🎉 تمام شد! تقویم را باز کنید — شمسی است. (اگر هدر بالا هنوز انگلیسی بود، گوشی را یک بار ری‌استارت کنید)")
            else say("\n⚠️ چیزی تغییر نکرد — از لاگ بالا اسکرین‌شات بفرستید.")
            StepResult(anyOk, sb.toString())
        }

    /** بررسی واقعی لوکال سیستم */
    suspend fun verifySystemLocale(): String = withContext(Dispatchers.IO) {
        val (code, out) = ShizukuHelper.runShell("cmd", "locale", "get-system-locales")
        if (code == 0 && out.isNotBlank()) out.trim()
        else java.util.Locale.getDefault().toLanguageTag()
    }
}
