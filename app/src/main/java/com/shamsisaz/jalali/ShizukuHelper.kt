package com.shamsisaz.jalali

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.InputStream

/**
 * پل شیزوکو — دسترسی ADB از داخل خود گوشی (بدون روت و بدون کامپیوتر).
 * در API نسخهٔ ۱۳ متد newProcess خصوصی است؛ با رفلکشن روی کلاس خود شیزوکو صدا زده می‌شود
 * (محدودیت Hidden API اندروید شامل کلاس‌های خود اپ/کتابخانه نمی‌شود).
 */
object ShizukuHelper {

    const val REQ_CODE = 0x5A5A
    const val PACKAGE = "moe.shizuku.privileged.api"

    fun isInstalled(ctx: Context): Boolean = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (_: Exception) { false }

    fun isBinderAlive(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun isPermissionGranted(): Boolean = try {
        isBinderAlive() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    /** false یعنی کاربر «دیگر نپرس» را زده و باید از داخل خود شیزوکو دسترسی بدهد */
    fun requestPermission(): Boolean = try {
        if (Shizuku.shouldShowRequestPermissionRationale()) false
        else { Shizuku.requestPermission(REQ_CODE); true }
    } catch (_: Throwable) { false }

    data class ShellResult(val code: Int, val output: String) {
        val ok get() = code == 0
    }

    /** اجرای یک دستور شل با هویت ADB (shell) */
    fun runShell(vararg cmd: String): ShellResult {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf(*cmd), null, null) as Process
            // خروجی و خطا را همزمان بخوان تا بافر پر نشود و گیر نکند
            val out = StringBuilder(); val err = StringBuilder()
            val t1 = Thread { out.append(read(process.inputStream)) }.apply { start() }
            val t2 = Thread { err.append(read(process.errorStream)) }.apply { start() }
            val code = process.waitFor()
            t1.join(3000); t2.join(3000)
            try { process.destroy() } catch (_: Exception) {}
            ShellResult(code, (out.toString() + err.toString()).trim())
        } catch (e: Throwable) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            ShellResult(-1, cause.message ?: cause.javaClass.simpleName)
        }
    }

    private fun read(s: InputStream): String = try { s.bufferedReader().readText() } catch (_: Exception) { "" }

    /** دادن یک مجوز runtime به خود اپ از طریق شل — مثل `adb shell pm grant` */
    fun grantSelf(ctx: Context, permission: String): ShellResult =
        runShell("pm", "grant", ctx.packageName, permission)

    fun statusText(ctx: Context): String = when {
        !isInstalled(ctx) -> "❌ شیزوکو نصب نیست"
        !isBinderAlive() -> "⚠️ شیزوکو نصب است ولی روشن نیست"
        !isPermissionGranted() -> "🔑 شیزوکو روشن است — دسترسی بده"
        else -> "✅ شیزوکو آماده — دسترسی ADB فعال"
    }
}
