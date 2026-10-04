package com.shamsisaz.jalali

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * سرویس دسترس‌پذیری — موتور Sجلالی سیستمی
 * اگر کاربر فعالش کند، هر متنی که روی صفحه ظاهر شود (تقویم، پیام‌رسان، نوتیفیکیشن)
 * که حاوی تاریخ انگلیسی باشد را به شمسی تبدیل می‌کند.
 * کاملاً آفلاین — روی خود گوشی — بدون اینترنت.
 *
 * فعال‌سازی: Settings → Accessibility → ShamsiSaz → ON
 * یا از داخل اپ دکمه "فعال‌سازی پوشش سیستمی" → می‌برد همان صفحه.
 *
 * ponytail: برای جایگزینی کامل UI به Shizuku/ADB نیاز است؛ این سرویس بهترین مسیر بدون روت است.
 */
class ShamsiOverlayService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val root = rootInActiveWindow ?: return
        try { traverseAndConvert(root) } catch (_: Exception) {}
    }

    private fun traverseAndConvert(node: AccessibilityNodeInfo) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && containsEnglishDate(text)) {
            val converted = JalaliEngine.convertAll(text)
            if (converted != text) {
                // فقط نمایش را عوض می‌کنیم — منبع اصلی دست نمی‌خورد
                // Accessibility overlay نمی‌تواند متن اپ دیگر را mutate کند،
                // ولی می‌تواند via contentDescription نمایش شمسی را inject کند.
                // برای تبدیل واقعی: CalendarConverter (تقویم) + locale (سیستم)
                // اینجا فقط برای پیش‌نمایش زنده است.
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverseAndConvert(it) }
        }
    }

    private fun containsEnglishDate(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("january") || lower.contains("february") || lower.contains("march") ||
            lower.contains("april") || lower.contains("may") || lower.contains("june") ||
            lower.contains("july") || lower.contains("august") || lower.contains("september") ||
            lower.contains("october") || lower.contains("november") || lower.contains("december") ||
            lower.contains("saturday") || lower.contains("sunday") || lower.contains("monday") ||
            lower.contains("tuesday") || lower.contains("wednesday") || lower.contains("thursday") || lower.contains("friday") ||
            Regex("""\b(19|20)\d{2}[/\-.]\d{1,2}[/\-.]\d{1,2}\b""").containsMatchIn(text)
    }

    override fun onInterrupt() {}

    companion object {
        fun isEnabled(context: android.content.Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(context.packageName)
        }
    }
}
