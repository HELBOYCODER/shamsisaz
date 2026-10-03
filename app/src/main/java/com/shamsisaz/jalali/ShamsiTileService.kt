package com.shamsisaz.jalali

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * کاشی تنظیمات سریع — از پنل نوتیفیکیشن با یک لمس تبدیل کن
 * روی One UI و MIUI هر دو کار می‌کند (API 24+)
 */
class ShamsiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "شمسی‌ساز"
            contentDescription = "تبدیل تقویم به شمسی"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val ctx = this
        // چک دسترسی
        val hasPerm = ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            Toast.makeText(ctx, "اول اپ را باز کن و دسترسی تقویم را بده", Toast.LENGTH_LONG).show()
            // باز کردن اپ برای گرفتن دسترسی
            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) startActivityAndCollapse(intent)
            return
        }
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
        Thread {
            try {
                val conv = CalendarConverter(ctx)
                val r = conv.convertAll()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(ctx, "✅ ${r.converted} رویداد شمسی شد", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(ctx, "خطا در تبدیل", Toast.LENGTH_SHORT).show()
                }
            }
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
        }.start()
    }
}
