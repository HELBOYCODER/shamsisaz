package com.shamsisaz.jalali

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * پل شیزوکو — «ارواح فارسی» 👻
 * با Shizuku (دسترسی ADB از داخل خود گوشی، بدون روت و بدون کامپیوتر)
 * می‌توانیم لوکال کل سیستم را عوض کنیم — همان کاری که ADB با کابل می‌کند.
 *
 * اجرای دستور: متد newProcess در API 13 خصوصی است، پس با رفلکشن صدا می‌زنیم
 * (رفلکشن روی کلاس خود شیزوکو — نه API مخفی اندروید — پس محدودیت Hidden API شاملش نمی‌شود).
 */
object ShizukuHelper {

    const val REQ_CODE = 0x5A5A

    fun isInstalled(ctx: Context): Boolean {
        return try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Exception) { false }
    }

    fun isBinderAlive(): Boolean {
        return try { Shizuku.pingBinder() } catch (_: Exception) { false }
    }

    fun isPermissionGranted(): Boolean {
        return try {
            if (!isBinderAlive()) return false
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }

    fun requestPermission(activity: android.app.Activity) {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(REQ_CODE)
        } catch (_: Exception) {}
    }

    /** اجرای یک دستور شل با هویت ADB — خروجی واقعی برمی‌گرداند */
    fun runShell(vararg cmd: String): Pair<Int, String> {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null, cmd.toList().toTypedArray(), null, null
            ) as Process
            val sb = StringBuilder()
            try {
                val r = BufferedReader(InputStreamReader(process.inputStream))
                val e = BufferedReader(InputStreamReader(process.errorStream))
                var line: String?
                while (r.readLine().also { line = it } != null) sb.appendLine(line)
                while (e.readLine().also { line = it } != null) sb.appendLine(line)
            } catch (_: Exception) {}
            val code = try { process.waitFor() } catch (_: Exception) { -1 }
            try { process.destroy() } catch (_: Exception) {}
            code to sb.toString().trim()
        } catch (e: Exception) {
            -1 to (e.message ?: "shizuku error")
        }
    }

    fun statusText(ctx: Context): String {
        if (!isInstalled(ctx)) return "❌ شیزوکو نصب نیست"
        if (!isBinderAlive()) return "⚠️ شیزوکو نصب است ولی روشن نیست"
        if (!isPermissionGranted()) return "🔑 شیزوکو روشن است — دسترسی بده"
        return "✅ شیزوکو آماده — دسترسی ADB فعال"
    }
}
