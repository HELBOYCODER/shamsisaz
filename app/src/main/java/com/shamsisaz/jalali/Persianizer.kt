package com.shamsisaz.jalali

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «ارواح فارسی» 👻 — فارسی‌سازی سیستمی با شیزوکو، بدون کامپیوتر و روت.
 * هر گام نتیجهٔ واقعی را در لاگ می‌نویسد؛ هیچ گامی «موفق» اعلام نمی‌شود مگر واقعاً خروجی درست داده باشد.
 */
object Persianizer {

    data class StepResult(val ok: Boolean, val log: String)

    private val knownCalendarPkgs = listOf(
        "com.samsung.android.calendar", "com.google.android.calendar", "com.android.calendar",
        "com.xiaomi.calendar", "com.miui.calendar", "com.sec.android.app.clockpackage", "com.google.android.deskclock"
    )

    suspend fun fullPersianize(ctx: Context, log: (String) -> Unit = {}): StepResult = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        fun say(s: String) { sb.appendLine(s); try { log(s) } catch (_: Exception) {} }

        when {
            !ShizukuHelper.isInstalled(ctx) -> { say("❌ شیزوکو نصب نیست."); return@withContext StepResult(false, sb.toString()) }
            !ShizukuHelper.isBinderAlive() -> { say("⚠️ شیزوکو روشن نیست."); return@withContext StepResult(false, sb.toString()) }
            !ShizukuHelper.isPermissionGranted() -> { say("🔑 دسترسی شیزوکو داده نشده."); return@withContext StepResult(false, sb.toString()) }
        }
        say("✅ شیزوکو آماده")
        var anyOk = false

        // ---- ۱) مجوزهای ویژه به خود اپ ----
        for (perm in listOf(SystemLocale.CHANGE_CONFIGURATION, SystemLocale.WRITE_SECURE_SETTINGS)) {
            val short = perm.substringAfterLast('.')
            if (SystemLocale.has(ctx, perm)) { say("✅ $short قبلاً داده شده"); continue }
            val r = ShizukuHelper.grantSelf(ctx, perm)
            if (r.ok && SystemLocale.has(ctx, perm)) { say("✅ $short داده شد"); anyOk = true }
            else say("⏭️ $short: ${r.output.take(120).ifBlank { "رد شد" }}")
        }

        // ---- ۲) روشن‌کردن خودکار پوشش شمسی (در صورت داشتن WRITE_SECURE_SETTINGS) ----
        if (ShamsiOverlayService.isEnabled(ctx)) say("✅ پوشش شمسی از قبل روشن است")
        else if (ShamsiOverlayService.tryEnable(ctx)) { say("✅ سرویس پوشش شمسی خودکار روشن شد"); anyOk = true }
        else say("⏭️ پوشش شمسی را باید از تنظیمات دسترس‌پذیری روشن کرد")

        // ---- ۳) زبان کل سیستم ----
        val err = SystemLocale.applyPersian(ctx)
        if (err == null) { say("✅ زبان سیستم → ${SystemLocale.persianTag()}"); anyOk = true }
        else say("⏭️ زبان سیستم تغییر نکرد: $err")

        // ---- ۴) زبان برنامه‌های تقویم/ساعت (اندروید ۱۳+) ----
        if (Build.VERSION.SDK_INT >= 33) {
            val installed = ShizukuHelper.runShell("pm", "list", "packages").output.lineSequence()
                .map { it.removePrefix("package:").trim() }.toSet()
            val targets = (knownCalendarPkgs + installed.filter { it.contains("calendar", true) }).distinct().filter { it in installed }
            val userId = android.os.Process.myUid() / 100000
            if (targets.isEmpty()) say("⏭️ برنامهٔ تقویمی روی گوشی پیدا نشد")
            for (pkg in targets) {
                var done = false
                for (tag in listOf(SystemLocale.persianTag(), "fa-IR").distinct()) {
                    val r = ShizukuHelper.runShell("cmd", "locale", "set-app-locales", pkg, "--user", userId.toString(), "--locales", tag)
                    if (r.ok) { done = true; break }
                }
                if (done) { say("✅ $pkg → فارسی"); anyOk = true } else say("⏭️ $pkg قابل تغییر نبود")
                ShizukuHelper.runShell("am", "force-stop", pkg)
            }
        } else say("ℹ️ تغییر زبان تکی برنامه‌ها از اندروید ۱۳ به بعد است؛ برای شما زبان سیستم کافی است")

        // ---- ۵) بررسی واقعی ----
        val persisted = ShizukuHelper.runShell("getprop", "persist.sys.locale").output.trim()
        say(if (persisted.startsWith("fa")) "📍 تأیید شد: persist.sys.locale = $persisted"
            else "📍 persist.sys.locale = ${persisted.ifBlank { "(خالی)" }} — اگر زبان عوض نشد، از تنظیمات دستی انجام بده")

        if (anyOk) say("\n🎉 تمام شد. تقویم را ببند و دوباره باز کن. اگر هنوز میلادی بود، گوشی را یک بار ری‌استارت کن.")
        else say("\n⚠️ چیزی تغییر نکرد؛ متن لاگ بالا را برای رفع اشکال بفرست.")
        StepResult(anyOk, sb.toString())
    }
}
