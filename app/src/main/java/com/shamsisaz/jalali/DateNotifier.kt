package com.shamsisaz.jalali

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

/** نوتیفیکیشن دائمی «تاریخ امروز» — روی نوار وضعیت عدد روزِ شمسی نشان داده می‌شود */
object DateNotifier {
    private const val CHANNEL = "today_date"
    private const val ID = 1405

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "تاریخ امروز", NotificationManager.IMPORTANCE_LOW)
            ch.description = "نمایش دائمی تاریخ شمسی در نوار وضعیت"
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val today = JalaliConverter.today()
        val occasions = Occasions.forDay(today)
        val text = buildString {
            append(Occasions.gregorianLabel(today)).append("  •  ").append(Occasions.hijriLabel(today))
            if (occasions.isNotEmpty()) append("\n").append(occasions.joinToString(" • ") { it.title })
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(IconCompat.createWithBitmap(dayIcon(today.day)))
            .setContentTitle(JalaliConverter.fullPersian(today))
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .build()
        nm.notify(ID, n)
    }

    fun hide(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
    }

    /** آیکن نوار وضعیت: عدد روز ماه (سفید روی شفاف) */
    private fun dayIcon(day: Int): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = 80f
        }
        val s = JalaliConverter.fa(day)
        while (p.measureText(s) > size - 6 && p.textSize > 20f) p.textSize -= 2f
        c.drawText(s, size / 2f, size / 2f - (p.ascent() + p.descent()) / 2f, p)
        return bmp
    }
}
