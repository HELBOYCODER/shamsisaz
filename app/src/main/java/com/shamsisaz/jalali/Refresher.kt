package com.shamsisaz.jalali

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import java.util.Calendar

/** هر چیزی که باید با عوض‌شدن روز به‌روز شود: ویجت‌ها، نوتیفیکیشن، کاشی، آلارم نیمه‌شب بعدی */
object Refresher {

    const val ACTION_TICK = "com.shamsisaz.jalali.DAY_TICK"
    const val ACTION_WIDGET_PREV = "com.shamsisaz.jalali.WIDGET_PREV"
    const val ACTION_WIDGET_NEXT = "com.shamsisaz.jalali.WIDGET_NEXT"
    const val ACTION_WIDGET_TODAY = "com.shamsisaz.jalali.WIDGET_TODAY"

    fun refreshAll(ctx: Context) {
        val c = ctx.applicationContext
        try { ShamsiWidgetProvider.refreshAll(c) } catch (_: Exception) {}
        try { ShamsiMonthWidgetProvider.refreshAll(c) } catch (_: Exception) {}
        try {
            if (Prefs.statusNotification(c)) DateNotifier.show(c) else DateNotifier.hide(c)
        } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= 24)
                TileService.requestListeningState(c, ComponentName(c, ShamsiTileService::class.java))
        } catch (_: Exception) {}
        scheduleNextMidnight(c)
    }

    /** آلارم کمی بعد از نیمه‌شب محلی (بدون نیاز به دسترسی آلارم دقیق) */
    fun scheduleNextMidnight(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 77, Intent(ctx, SystemReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: Exception) {}
    }
}

/** دریافت‌کنندهٔ رویدادهای سیستمی: بوت، تغییر ساعت/تاریخ/منطقهٔ زمانی، آپدیت اپ، آلارم نیمه‌شب، و دکمه‌های ویجت */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Refresher.ACTION_WIDGET_PREV -> Prefs.setWidgetMonthOffset(context, Prefs.widgetMonthOffset(context) - 1)
            Refresher.ACTION_WIDGET_NEXT -> Prefs.setWidgetMonthOffset(context, Prefs.widgetMonthOffset(context) + 1)
            Refresher.ACTION_WIDGET_TODAY -> Prefs.setWidgetMonthOffset(context, 0)
        }
        Refresher.refreshAll(context)
    }
}
