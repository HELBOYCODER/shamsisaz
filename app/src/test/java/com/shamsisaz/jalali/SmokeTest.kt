package com.shamsisaz.jalali

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** اجرای واقعی کد اندرویدی روی Robolectric: اگر Activity/ویجت/اعلان در زمان اجرا بترکد، این تست می‌افتد */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmokeTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun allText(v: View, out: MutableList<String>) {
        if (v is TextView) out.add(v.text.toString())
        if (v is ViewGroup) for (i in 0 until v.childCount) allText(v.getChildAt(i), out)
    }

    @Test fun mainActivityLaunchesAndShowsToday() {
        val ctrl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val act = ctrl.get()
        val texts = ArrayList<String>()
        allText(act.window.decorView, texts)
        val today = JalaliConverter.today()
        assertTrue("امروز در صفحه نیست: $texts", texts.any { it.contains(JalaliConverter.fullPersian(today)) })
        assertTrue(texts.any { it.contains("دسترسی‌ها") })
        assertTrue(texts.any { it.contains("شیزوکو") })
        ctrl.pause().stop().destroy()
    }

    @Test fun monthWidgetRenders() {
        val awm = AppWidgetManager.getInstance(ctx)
        val sh = shadowOf(awm)
        val monthId = sh.createWidget(ShamsiMonthWidgetProvider::class.java, R.layout.widget_month)
        val smallId = sh.createWidget(ShamsiWidgetProvider::class.java, R.layout.widget_shamsi)
        ShamsiMonthWidgetProvider.refreshAll(ctx)
        ShamsiWidgetProvider.refreshAll(ctx)
        // محتوای واقعی ویجت ماهانه را بررسی کن
        val view = sh.getViewFor(monthId)
        val texts = ArrayList<String>()
        allText(view, texts)
        val today = JalaliConverter.today()
        assertTrue("عنوان ماه نیست: $texts", texts.any { it.contains(today.monthName()) && it.contains(JalaliConverter.fa(today.year)) })
        val days = texts.filter { it.isNotEmpty() && it.all { c -> c in '۰'..'۹' } }
        assertEquals(JalaliConverter.monthLength(today.year, today.month), days.size)
        val small = ArrayList<String>()
        allText(sh.getViewFor(smallId), small)
        assertTrue("$small", small.contains(JalaliConverter.fa(today.day)))
    }

    @Test fun monthWidgetShiftMath() {
        val d = JalaliConverter.JalaliDate(1405, 1, 10)
        assertEquals(1404 to 12, ShamsiMonthWidgetProvider.shifted(d, -1))
        assertEquals(1406 to 1, ShamsiMonthWidgetProvider.shifted(JalaliConverter.JalaliDate(1405, 12, 1), 1))
        assertEquals(1405 to 1, ShamsiMonthWidgetProvider.shifted(d, 0))
    }

    @Test fun notificationPostedWhenEnabled() {
        shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Prefs.setStatusNotification(ctx, true)
        Refresher.refreshAll(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val posted = shadowOf(nm).allNotifications
        assertEquals(1, posted.size)
        assertTrue(posted[0].flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        Prefs.setStatusNotification(ctx, false)
        Refresher.refreshAll(ctx)
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test fun receiverHandlesWidgetButtons() {
        Prefs.setWidgetMonthOffset(ctx, 0)
        SystemReceiver().onReceive(ctx, android.content.Intent(Refresher.ACTION_WIDGET_NEXT))
        SystemReceiver().onReceive(ctx, android.content.Intent(Refresher.ACTION_WIDGET_NEXT))
        assertEquals(2, Prefs.widgetMonthOffset(ctx))
        SystemReceiver().onReceive(ctx, android.content.Intent(Refresher.ACTION_WIDGET_PREV))
        assertEquals(1, Prefs.widgetMonthOffset(ctx))
        SystemReceiver().onReceive(ctx, android.content.Intent(Refresher.ACTION_WIDGET_TODAY))
        assertEquals(0, Prefs.widgetMonthOffset(ctx))
        SystemReceiver().onReceive(ctx, android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED))
    }

    @Test fun tileService() {
        val svc = Robolectric.setupService(ShamsiTileService::class.java)
        assertNotNull(svc)
    }

    @Test fun shizukuHelpersDontCrashWithoutShizuku() {
        assertTrue(!ShizukuHelper.isBinderAlive())
        assertTrue(!ShizukuHelper.isPermissionGranted())
        assertNotNull(ShizukuHelper.statusText(ctx))
        assertTrue(!ShizukuHelper.runShell("true").ok)
    }

    @Test fun systemLocaleReportsMissingPermission() {
        val err = SystemLocale.applyPersian(ctx)
        assertNotNull(err)
    }
}
