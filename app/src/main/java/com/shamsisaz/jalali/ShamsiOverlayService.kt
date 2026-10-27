package com.shamsisaz.jalali

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

/**
 * «پوشش شمسی» روی صفحه (آزمایشی).
 * اندروید اجازه نمی‌دهد اپ دیگری متن اپ‌های دیگر را تغییر دهد؛ پس این سرویس متن‌های دارای تاریخ میلادی
 * (هدر تقویم، پیام‌ها، نوتیفیکیشن‌ها …) را می‌خواند و همان‌جا یک برچسبِ شفافیت‌دارِ بدون‌لمس با تاریخ شمسی می‌کشد.
 * رنگ پس‌زمینه از اسکرین‌شات صفحه (اندروید ۱۱+) نمونه‌برداری می‌شود تا پوشش یکدست دیده شود.
 * هیچ داده‌ای ذخیره یا ارسال نمی‌شود.
 */
class ShamsiOverlayService : AccessibilityService() {

    private class Hit(val bounds: Rect, val original: String, val converted: String)

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: FrameLayout? = null
    private var lastSignature = ""
    private var busy = false

    private val refreshRunnable = Runnable { refresh() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!Prefs.screenOverlay(this)) { clear(); return }
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 350)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } } catch (_: Exception) {}
        overlay = null
        super.onDestroy()
    }

    private fun ensureOverlay(): FrameLayout? {
        overlay?.let { return it }
        return try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val v = FrameLayout(this)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            wm.addView(v, lp)
            overlay = v
            v
        } catch (_: Exception) { null }
    }

    private fun clear() {
        lastSignature = ""
        overlay?.removeAllViews()
    }

    private fun refresh() {
        if (busy) return
        val ov = ensureOverlay() ?: return
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        if (root == null || root.packageName == packageName) { clear(); return }
        val hits = ArrayList<Hit>()
        try { collect(root, hits, IntArray(1)) } catch (_: Exception) {}
        val signature = hits.joinToString("|") { "${it.bounds.flattenToString()}=${it.original}" }
        if (signature == lastSignature) return
        if (hits.isEmpty()) { clear(); lastSignature = signature; return }

        if (Build.VERSION.SDK_INT >= 30) {
            busy = true
            ov.visibility = View.INVISIBLE // تا اسکرین‌شات رنگ واقعی پشت پوشش را بگیرد
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(this), object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        var bmp: Bitmap? = null
                        try {
                            bmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                            result.hardwareBuffer.close()
                        } catch (_: Exception) {}
                        busy = false
                        render(hits, bmp, signature)
                        bmp?.recycle()
                    }
                    override fun onFailure(errorCode: Int) { busy = false; render(hits, null, signature) }
                })
            } catch (_: Exception) { busy = false; render(hits, null, signature) }
        } else render(hits, null, signature)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Hit>, counter: IntArray) {
        if (counter[0]++ > 700 || out.size >= 60) return
        if (!node.isVisibleToUser) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && !node.isEditable && text.length < 400 && JalaliEngine.containsEnglishDate(text)) {
            val converted = JalaliEngine.convertAll(text)
            if (converted != text) {
                val r = Rect(); node.getBoundsInScreen(r)
                if (r.width() > 8 && r.height() > 8) out.add(Hit(r, text, converted))
            }
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collect(c, out, counter)
        }
    }

    private fun render(hits: List<Hit>, shot: Bitmap?, signature: String) {
        val ov = overlay ?: return
        ov.removeAllViews()
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        for (h in hits) {
            var bg = if (night) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
            if (shot != null) {
                val x = (h.bounds.left + 3).coerceIn(0, shot.width - 1)
                val y = (h.bounds.top + 3).coerceIn(0, shot.height - 1)
                bg = shot.getPixel(x, y) or 0xFF000000.toInt()
            }
            val lum = (0.299 * ((bg shr 16) and 0xFF) + 0.587 * ((bg shr 8) and 0xFF) + 0.114 * (bg and 0xFF)) / 255.0
            val tv = TextView(this)
            tv.text = h.converted
            tv.setTextColor(if (lum > 0.55) 0xFF111111.toInt() else 0xFFF2F2F2.toInt())
            tv.setBackgroundColor(bg)
            tv.layoutDirection = View.LAYOUT_DIRECTION_RTL
            tv.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            tv.includeFontPadding = false
            val maxPx = (h.bounds.height() * 0.8f).coerceAtMost(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 40f, resources.displayMetrics))
            val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8f, resources.displayMetrics)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tv, minPx.toInt(), maxOf(maxPx, minPx).toInt(), 1, TypedValue.COMPLEX_UNIT_PX)
            val lp = FrameLayout.LayoutParams(h.bounds.width(), h.bounds.height())
            lp.leftMargin = h.bounds.left
            lp.topMargin = h.bounds.top
            ov.addView(tv, lp)
        }
        ov.visibility = View.VISIBLE
        lastSignature = signature
    }

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, ShamsiOverlayService::class.java)

        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val full = component(ctx).flattenToString()
            val short = component(ctx).flattenToShortString()
            return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
        }

        fun hasSecureSettings(ctx: Context): Boolean =
            ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED

        /** فقط وقتی WRITE_SECURE_SETTINGS (از طریق شیزوکو/ADB) داده شده باشد، بی‌نیاز از رفتن به تنظیمات روشن می‌شود */
        fun tryEnable(ctx: Context): Boolean {
            if (!hasSecureSettings(ctx)) return false
            return try {
                if (isEnabled(ctx)) return true
                val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                val me = component(ctx).flattenToString()
                Settings.Secure.putString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    if (cur.isNullOrEmpty()) me else "$cur:$me"
                )
                Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                true
            } catch (_: Exception) { false }
        }
    }
}
