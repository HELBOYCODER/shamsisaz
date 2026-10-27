package com.shamsisaz.jalali

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.InvocationTargetException
import java.util.Locale

/**
 * تغییر زبان کل سیستم به فارسی — همان روشی که اپ‌های «انتخاب زبان» با مجوز CHANGE_CONFIGURATION انجام می‌دهند.
 * این مجوز عادی نیست؛ فقط با `pm grant` (ADB یا شیزوکو) به اپ داده می‌شود.
 */
object SystemLocale {

    const val CHANGE_CONFIGURATION = "android.permission.CHANGE_CONFIGURATION"
    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

    fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    fun currentTags(): String = LocaleList.getDefault().toLanguageTags()
    fun isPersian(): Boolean = Locale.getDefault().language == "fa"

    /** لوکال با تقویم شمسی؛ روی اندروید ۱۴+ افزونهٔ -u-ca-persian پذیرفته می‌شود */
    fun persianTag(): String = if (Build.VERSION.SDK_INT >= 34) "fa-IR-u-ca-persian" else "fa-IR"

    /** دستوری که کاربر می‌تواند با کامپیوتر بزند تا مجوز لازم را بدهد */
    fun adbGrantCommand(ctx: Context): String =
        "adb shell pm grant ${ctx.packageName} $CHANGE_CONFIGURATION\n" +
            "adb shell pm grant ${ctx.packageName} $WRITE_SECURE_SETTINGS"

    /** @return null اگر موفق بود، وگرنه متن خطا */
    fun applyPersian(ctx: Context): String? {
        if (!has(ctx, CHANGE_CONFIGURATION)) return "مجوز CHANGE_CONFIGURATION داده نشده"
        return try {
            if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("")
            val am: Any = if (Build.VERSION.SDK_INT >= 26)
                ActivityManager::class.java.getMethod("getService").invoke(null)!!
            else
                Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null)!!
            val iam = Class.forName("android.app.IActivityManager")

            val config = iam.getMethod("getConfiguration").invoke(am) as Configuration
            val fa = Locale.forLanguageTag(persianTag())
            val rest = (0 until LocaleList.getDefault().size()).map { LocaleList.getDefault()[it] }
                .filter { it.language != "fa" }
            config.setLocales(LocaleList(*(listOf(fa) + rest).toTypedArray()))
            try { Configuration::class.java.getField("userSetLocale").setBoolean(config, true) } catch (_: Throwable) {}

            try {
                iam.getMethod("updatePersistentConfiguration", Configuration::class.java).invoke(am, config)
            } catch (e: NoSuchMethodException) {
                iam.getMethod("updatePersistentConfigurationWithAttribution", Configuration::class.java, String::class.java, String::class.java)
                    .invoke(am, config, ctx.packageName, null)
            }
            null
        } catch (e: InvocationTargetException) {
            e.targetException?.message ?: e.targetException?.javaClass?.simpleName ?: "خطای نامشخص"
        } catch (e: Throwable) {
            e.message ?: e.javaClass.simpleName
        }
    }
}
