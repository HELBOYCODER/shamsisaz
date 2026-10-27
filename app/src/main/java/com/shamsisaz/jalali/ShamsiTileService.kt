package com.shamsisaz.jalali

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** کاشی تنظیمات سریع: برچسب = تاریخ شمسی امروز؛ با لمس، اپ باز می‌شود */
class ShamsiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val t = JalaliConverter.today()
        qsTile?.apply {
            label = "${JalaliConverter.fa(t.day)} ${t.monthName()}"
            if (Build.VERSION.SDK_INT >= 29) subtitle = JalaliConverter.weekDayNames[t.weekDay()]
            contentDescription = JalaliConverter.fullPersian(t)
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            legacyCollapse(intent)
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun legacyCollapse(intent: Intent) = startActivityAndCollapse(intent)
}
