package com.shamsisaz.jalali

import android.Manifest
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var converter: CalendarConverter
    private lateinit var content: LinearLayout

    // هدر
    private lateinit var tvTodayBig: TextView
    private lateinit var tvTodaySub: TextView
    private lateinit var tvTodayOcc: TextView
    // تقویم
    private lateinit var monthView: MonthView
    private lateinit var tvMonthTitle: TextView
    private lateinit var tvDayDetail: TextView
    private var viewYear = 0
    private var viewMonth = 0
    // بخش‌های قابل‌به‌روزرسانی
    private lateinit var permsBox: LinearLayout
    private lateinit var tvHolidayStatus: TextView
    private lateinit var tvConvertStatus: TextView
    private lateinit var tvShizukuLog: TextView
    private lateinit var swNotification: MaterialSwitch
    private lateinit var swOverlay: MaterialSwitch
    private lateinit var btnUndo: MaterialButton
    private lateinit var btnPersianize: MaterialButton

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.POST_NOTIFICATIONS] == true && Prefs.statusNotification(this)) Refresher.refreshAll(this)
        refreshAll()
    }

    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        runOnUiThread {
            toast(if (result == PackageManager.PERMISSION_GRANTED) "✅ دسترسی شیزوکو داده شد" else "❌ دسترسی شیزوکو رد شد")
            refreshAll()
        }
    }
    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshAll() } }
    private val shizukuDeadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refreshAll() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        converter = CalendarConverter(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildUi()

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermListener)
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
            Shizuku.addBinderDeadListener(shizukuDeadListener)
        } catch (_: Throwable) {}

        val t = JalaliConverter.today()
        viewYear = t.year; viewMonth = t.month
        monthView.selected = t
        showMonth()
        showDay(t)
        Refresher.refreshAll(this)

        // بار اول: دسترسی‌های اصلی را یک‌جا بخواه
        val sp = getSharedPreferences("shamsisaz_ui", MODE_PRIVATE)
        if (!sp.getBoolean("asked_basic_perms", false)) {
            sp.edit().putBoolean("asked_basic_perms", true).apply()
            requestBasicPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        // اگر کاربر از تنظیمات برگشت یا روز عوض شده
        val t = JalaliConverter.today()
        tvTodayBig.text = JalaliConverter.fullPersian(t)
        refreshAll()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderReceivedListener(shizukuBinderListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(shizukuDeadListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    // ===================== ساخت رابط =====================

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun attr(a: Int, fallback: Int) = MaterialColors.getColor(this, a, fallback)

    private fun buildUi() {
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(32)) }
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        // ---- هدر امروز ----
        val header = card(attr(androidx.appcompat.R.attr.colorPrimary, 0xFF0F7B4A.toInt()))
        val onPrimary = attr(com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt())
        tvTodayBig = text("", 22f, onPrimary, bold = true)
        tvTodaySub = text("", 13f, onPrimary)
        tvTodayOcc = text("", 13f, onPrimary, bold = true)
        header.addView(text("☀️ شمسی‌ساز", 13f, onPrimary))
        header.addView(tvTodayBig, lpTop(6))
        header.addView(tvTodaySub, lpTop(4))
        header.addView(tvTodayOcc, lpTop(4))
        val t = JalaliConverter.today()
        tvTodayBig.text = JalaliConverter.fullPersian(t)
        tvTodaySub.text = "${Occasions.gregorianLabel(t)}  •  ${Occasions.hijriLabel(t)}"
        val occ = Occasions.forDay(t)
        tvTodayOcc.text = occ.joinToString(" • ") { it.title }
        tvTodayOcc.visibility = if (occ.isEmpty()) View.GONE else View.VISIBLE

        // ---- تقویم ماهانه ----
        val cal = card()
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val prev = iconBtn("›") { shiftMonth(-1) }
        val next = iconBtn("‹") { shiftMonth(1) }
        tvMonthTitle = text("", 18f, attr(com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt()), bold = true).apply { gravity = Gravity.CENTER }
        val todayBtn = button("امروز", outlined = true) {
            val n = JalaliConverter.today(); viewYear = n.year; viewMonth = n.month; monthView.selected = n; showMonth(); showDay(n)
        }
        nav.addView(prev, LinearLayout.LayoutParams(dp(44), dp(44)))
        nav.addView(tvMonthTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next, LinearLayout.LayoutParams(dp(44), dp(44)))
        nav.addView(todayBtn)
        cal.addView(nav)
        monthView = MonthView(this).apply { onDaySelected = { showDay(it) } }
        cal.addView(monthView, lpTop(4))
        tvDayDetail = text("", 14f, attr(com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt()))
        cal.addView(tvDayDetail, lpTop(8))

        // ---- دسترسی‌ها ----
        val perms = card()
        perms.addView(sectionTitle("🔑 دسترسی‌ها"))
        perms.addView(note("هر مجوز فقط برای یک قابلیت مشخص است؛ هیچ داده‌ای از گوشی خارج نمی‌شود (اپ اینترنت ندارد)."))
        permsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        perms.addView(permsBox, lpTop(8))

        // ---- ویجت و نوار وضعیت ----
        val show = card()
        show.addView(sectionTitle("🧩 نمایش همیشگی تاریخ شمسی"))
        val wRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        wRow.addView(button("ویجت ماهانه") { pinWidget(ShamsiMonthWidgetProvider::class.java) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wRow.addView(button("ویجت امروز", outlined = true) { pinWidget(ShamsiWidgetProvider::class.java) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        show.addView(wRow, lpTop(8))
        swNotification = MaterialSwitch(this).apply {
            text = "نمایش تاریخ شمسی در نوار وضعیت (عدد روز ماه کنار ساعت)"
            setOnCheckedChangeListener { b, on -> if (b.isPressed) onNotificationToggle(on) }
        }
        show.addView(swNotification, lpTop(8))
        show.addView(note("کاشی «تاریخ شمسی» را هم می‌توانید از ویرایش پنل تنظیمات سریع اضافه کنید."), lpTop(4))

        // ---- مناسبت‌ها در تقویم گوشی ----
        val hol = card()
        hol.addView(sectionTitle("📅 مناسبت‌ها و تعطیلات در تقویم گوشی"))
        hol.addView(note("یک تقویم محلی «مناسبت‌های ایران» می‌سازد (سال جاری و بعد) که در تقویم سامسونگ/شیائومی/گوگل دیده می‌شود. همگام‌سازی ابری ندارد و چندبار زدن، تکرار نمی‌سازد."))
        tvHolidayStatus = note("")
        hol.addView(tvHolidayStatus, lpTop(6))
        val hRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hRow.addView(button("افزودن / به‌روزرسانی") { onSyncHolidays() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hRow.addView(button("حذف", outlined = true) { onRemoveHolidays() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        hol.addView(hRow, lpTop(8))
        hol.addView(button("باز کردن تقویم گوشی", outlined = true) { openCalendarApp() }, lpTop(4))

        // ---- تبدیل تاریخ داخل رویدادها ----
        val conv = card()
        conv.addView(sectionTitle("🔁 تبدیل تاریخ‌های میلادیِ داخل رویدادها"))
        conv.addView(note("اگر داخل عنوان/توضیح/مکان رویدادهای شما تاریخ میلادی نوشته شده (مثلاً «Meeting 4 October»)، به شمسی تبدیل می‌شود. فقط متن همان تاریخ عوض می‌شود. تقویم‌های فقط‌خواندنی دست نمی‌خورند و بازگردانی کامل است."))
        tvConvertStatus = note("")
        conv.addView(tvConvertStatus, lpTop(6))
        val cRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cRow.addView(button("پیش‌نمایش", outlined = true) { onPreview() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cRow.addView(button("تبدیل") { onConvert() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        btnUndo = button("بازگردانی", outlined = true) { onUndo() }
        cRow.addView(btnUndo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        conv.addView(cRow, lpTop(8))

        // ---- پوشش شمسی ----
        val ov = card()
        ov.addView(sectionTitle("👁️ پوشش شمسی روی صفحه (آزمایشی)"))
        ov.addView(note("با سرویس دسترس‌پذیری، تاریخ‌های میلادیِ دیده‌شده روی صفحه (هدر تقویم، پیام‌ها، اعلان‌ها) با برچسب شمسی پوشانده می‌شوند. متن اپ‌های دیگر واقعاً تغییر نمی‌کند، فقط نمایش آن."))
        swOverlay = MaterialSwitch(this).apply {
            text = "فعال بودن پوشش"
            setOnCheckedChangeListener { b, on -> if (b.isPressed) { Prefs.setScreenOverlay(this@MainActivity, on) } }
        }
        ov.addView(swOverlay, lpTop(6))

        // ---- زبان سیستم ----
        val sys = card()
        sys.addView(sectionTitle("👻 فارسی‌سازی کل گوشی (شیزوکو)"))
        sys.addView(note("زبان سیستم و برنامه‌های تقویم را از داخل خود گوشی به فارسی (با تقویم شمسی در اندروید ۱۴+) تغییر می‌دهد؛ بدون روت و بدون کامپیوتر. نیاز به نصب و روشن بودن Shizuku دارد. نتیجه روی هر برند فرق می‌کند؛ لاگ زیر نشان می‌دهد چه چیزی واقعاً عوض شد."))
        sys.addView(button("باز کردن تنظیمات زبان گوشی", outlined = true) { openLanguageSettings() }, lpTop(8))
        btnPersianize = button("👻 فارسی‌سازی کامل با شیزوکو") { onPersianize() }
        sys.addView(btnPersianize, lpTop(4))
        tvShizukuLog = text("", 12f, attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF555555.toInt())).apply {
            visibility = View.GONE; setTextIsSelectable(true); textDirection = View.TEXT_DIRECTION_RTL
        }
        sys.addView(tvShizukuLog, lpTop(8))

        val foot = note("شمسی‌ساز ${versionName()} — متن‌باز (MIT) • مناسبت‌های قمری ممکن است تا یک روز با اعلام رسمی (رؤیت هلال) فرق کنند؛ جدول رسمی ۱۴۰۵ داخل اپ است.")
        foot.gravity = Gravity.CENTER

        for (v in listOf(header, cal, perms, show, hol, conv, ov, sys)) content.addView(v.parent as View, lpTop(12))
        content.addView(foot, lpTop(16))
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    // ---- ابزارهای ساخت ویو ----
    private fun lpTop(dpTop: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(dpTop) }

    /** کارت: خروجی همان LinearLayout داخلی است؛ ریشهٔ کارت `parent` آن است */
    private fun card(bg: Int? = null): LinearLayout {
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        MaterialCardView(this).apply {
            radius = dp(20).toFloat(); cardElevation = 0f
            if (bg != null) { setCardBackgroundColor(bg); strokeWidth = 0 } else strokeWidth = dp(1)
            addView(inner)
        }
        return inner
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_RTL
    }

    private fun sectionTitle(s: String) = text(s, 16f, attr(com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt()), bold = true)

    private fun note(s: String) = text(s, 12.5f, attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF555555.toInt())).apply {
        setLineSpacing(0f, 1.15f)
    }

    private fun button(label: String, outlined: Boolean = false, onClick: () -> Unit): MaterialButton {
        val style = if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle
        return MaterialButton(this, null, style).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } }
    }

    private fun iconBtn(label: String, onClick: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
        text = label; textSize = 22f; setOnClickListener { onClick() }
    }

    // ===================== تقویم =====================

    private fun shiftMonth(delta: Int) {
        val idx = viewYear * 12 + (viewMonth - 1) + delta
        viewYear = Math.floorDiv(idx, 12); viewMonth = Math.floorMod(idx, 12) + 1
        showMonth()
    }

    private fun showMonth() {
        monthView.show(viewYear, viewMonth)
        tvMonthTitle.text = "${JalaliConverter.monthNames[viewMonth - 1]} ${JalaliConverter.fa(viewYear)}"
    }

    private fun showDay(d: JalaliConverter.JalaliDate) {
        val sb = StringBuilder()
        sb.append(JalaliConverter.fullPersian(d)).append('\n')
        sb.append(Occasions.gregorianLabel(d)).append("  •  ").append(Occasions.hijriLabel(d))
        val occ = Occasions.forDay(d)
        if (d.weekDay() == 6 && occ.none { it.holiday }) sb.append("\n🔴 جمعه")
        occ.forEach { sb.append("\n").append(if (it.holiday) "🔴 " else "🟢 ").append(it.title).append(if (it.holiday) " (تعطیل)" else "") }
        if (occ.any { it.approximate }) sb.append("\n(تاریخ قمری تقریبی است)")
        tvDayDetail.text = sb.toString()
        if (hasCalendarPermission()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val ev = try { DayEvents.on(this@MainActivity, d) } catch (_: Exception) { emptyList() }
                if (ev.isNotEmpty()) withContext(Dispatchers.Main) {
                    if (monthView.selected == d) tvDayDetail.append("\n\n📌 رویدادهای تقویم شما:" + ev.joinToString("") { "\n• ${it.timeLabel} — ${it.title}" })
                }
            }
        }
    }

    // ===================== دسترسی‌ها =====================

    private fun hasCalendarPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun requestBasicPermissions() {
        val list = mutableListOf<String>()
        if (!hasCalendarPermission()) { list.add(Manifest.permission.READ_CALENDAR); list.add(Manifest.permission.WRITE_CALENDAR) }
        if (Build.VERSION.SDK_INT >= 33 && !DateNotifier.canNotify(this)) list.add(Manifest.permission.POST_NOTIFICATIONS)
        if (list.isNotEmpty()) permLauncher.launch(list.toTypedArray())
    }

    private fun isIgnoringBattery(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun permRow(ok: Boolean, label: String, actionLabel: String, action: (() -> Unit)?) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tv = text("${if (ok) "✅" else "⭕"} $label", 14f, attr(com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt()))
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!ok && action != null) row.addView(button(actionLabel, outlined = true) { action() })
        permsBox.addView(row, lpTop(4))
    }

    private fun refreshAll() {
        // ---- مجوزها ----
        permsBox.removeAllViews()
        permRow(hasCalendarPermission(), "تقویم (خواندن/نوشتن) — برای مناسبت‌ها، رویدادهای هر روز و تبدیل تاریخ‌ها", "اجازه") {
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permRow(DateNotifier.canNotify(this), "اعلان‌ها — برای تاریخ در نوار وضعیت", "اجازه") {
                permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        permRow(isIgnoringBattery(), "بدون محدودیت باتری — تا ویجت/نوار وضعیت سر نیمه‌شب حتماً به‌روز شوند", "اجازه") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        permRow(ShamsiOverlayService.isEnabled(this), "دسترس‌پذیری — برای پوشش شمسی روی صفحه", "روشن‌کردن") { enableAccessibility() }
        val shizukuOk = ShizukuHelper.isPermissionGranted()
        permRow(shizukuOk, "شیزوکو — ${ShizukuHelper.statusText(this)}", if (!ShizukuHelper.isInstalled(this)) "نصب" else if (!ShizukuHelper.isBinderAlive()) "راهنما" else "اجازه") { shizukuStep() }
        permRow(
            SystemLocale.has(this, SystemLocale.CHANGE_CONFIGURATION) && SystemLocale.has(this, SystemLocale.WRITE_SECURE_SETTINGS),
            "مجوزهای ویژه (تغییر زبان سیستم، روشن‌کردن خودکار پوشش) — با شیزوکو داده می‌شود", "راهنما"
        ) { showAdbDialog() }

        // ---- کلیدها ----
        swNotification.isChecked = Prefs.statusNotification(this) && DateNotifier.canNotify(this)
        swOverlay.isChecked = Prefs.screenOverlay(this)

        // ---- وضعیت مناسبت‌ها / تبدیل ----
        val calOk = hasCalendarPermission()
        tvHolidayStatus.text = when {
            !calOk -> "برای استفاده، دسترسی تقویم را بدهید."
            HolidayCalendar.isInstalled(this) -> "✅ تقویم «مناسبت‌های ایران» در گوشی نصب است."
            else -> "هنوز اضافه نشده."
        }
        btnUndo.isEnabled = calOk && converter.hasBackup()
        tvConvertStatus.text = if (!calOk) "برای استفاده، دسترسی تقویم را بدهید."
        else if (converter.hasBackup()) "${JalaliConverter.fa(converter.backupCount())} رویداد تبدیل شده و قابل بازگردانی است."
        else "تبدیلی انجام نشده."
        btnPersianize.text = "👻 فارسی‌سازی کامل با شیزوکو"
    }

    private fun enableAccessibility() {
        if (ShamsiOverlayService.tryEnable(this)) { toast("✅ پوشش شمسی روشن شد"); refreshAll(); return }
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("«شمسی‌ساز» را در لیست پیدا و روشن کنید")
        } catch (_: Exception) { toast("تنظیمات دسترس‌پذیری باز نشد") }
    }

    private fun showAdbDialog() {
        AlertDialog.Builder(this)
            .setTitle("مجوزهای ویژه")
            .setMessage("این دو مجوز عادی نیستند و فقط با ADB/شیزوکو داده می‌شوند:\n\n• تغییر زبان کل سیستم (CHANGE_CONFIGURATION)\n• روشن‌کردن خودکار پوشش (WRITE_SECURE_SETTINGS)\n\nساده‌ترین راه: Shizuku را نصب و روشن کنید و دکمهٔ «فارسی‌سازی کامل» را بزنید؛ خودش مجوزها را می‌دهد.\n\nیا با کامپیوتر:\n${SystemLocale.adbGrantCommand(this)}")
            .setPositiveButton("باشه", null)
            .setNeutralButton("کپی دستور") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("adb", SystemLocale.adbGrantCommand(this)))
                toast("کپی شد")
            }
            .show()
    }

    // ===================== شیزوکو =====================

    private fun shizukuStep() {
        when {
            !ShizukuHelper.isInstalled(this) -> showShizukuNeededDialog()
            !ShizukuHelper.isBinderAlive() -> showShizukuStartDialog()
            !ShizukuHelper.isPermissionGranted() -> {
                if (!ShizukuHelper.requestPermission()) {
                    toast("دسترسی را از داخل خود برنامهٔ Shizuku (بخش Authorized apps) به شمسی‌ساز بدهید")
                    openShizukuApp()
                }
            }
            else -> toast("✅ شیزوکو آماده است")
        }
    }

    private fun onPersianize() {
        if (!ShizukuHelper.isPermissionGranted()) { shizukuStep(); return }
        tvShizukuLog.text = "👻 شروع…\n"
        tvShizukuLog.visibility = View.VISIBLE
        btnPersianize.isEnabled = false
        lifecycleScope.launch {
            val res = Persianizer.fullPersianize(this@MainActivity) { line -> runOnUiThread { tvShizukuLog.append(line + "\n") } }
            btnPersianize.isEnabled = true
            Refresher.refreshAll(this@MainActivity)
            refreshAll()
            if (res.ok) toast("پایان — لاگ را بخوانید")
        }
    }

    private fun showShizukuNeededDialog() {
        AlertDialog.Builder(this)
            .setTitle("نصب Shizuku")
            .setMessage("Shizuku دسترسی ADB را از داخل خود گوشی (بدون روت) در اختیار اپ‌ها می‌گذارد.\n\n۱) Shizuku را نصب کنید\n۲) آن را با «اشکال‌زدایی بی‌سیم» روشن کنید\n۳) به اینجا برگردید")
            .setPositiveButton("دانلود Shizuku") { _, _ -> openUrl("https://shizuku.rikka.app/download/") }
            .setNegativeButton("بعداً", null).show()
    }

    private fun showShizukuStartDialog() {
        val steps = if (Build.VERSION.SDK_INT >= 30)
            "روی همین گوشی:\n\n۱) تنظیمات → دربارهٔ گوشی → ۷ بار روی «شمارهٔ ساخت»\n۲) گزینه‌های توسعه‌دهنده → «اشکال‌زدایی بی‌سیم» را روشن کنید (به Wi‑Fi وصل باشید)\n۳) Shizuku را باز کنید → «Pairing» → کد جفت‌سازی را وارد کنید\n۴) در Shizuku «Start» بزنید\n۵) برگردید و دکمه را بزنید"
        else
            "اندروید قدیمی‌تر از ۱۱ یک‌بار به کامپیوتر نیاز دارد:\n\nadb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
        AlertDialog.Builder(this)
            .setTitle("روشن‌کردن Shizuku").setMessage(steps)
            .setPositiveButton("باز کردن Shizuku") { _, _ -> openShizukuApp() }
            .setNeutralButton("آموزش") { _, _ -> openUrl("https://shizuku.rikka.app/guide/setup/") }
            .setNegativeButton("باشه", null).show()
    }

    private fun openShizukuApp() {
        val i = packageManager.getLaunchIntentForPackage(ShizukuHelper.PACKAGE)
        if (i != null) startActivity(i) else openUrl("https://shizuku.rikka.app/download/")
    }

    private fun openUrl(u: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (_: Exception) { toast("مرورگر پیدا نشد") }
    }

    // ===================== قابلیت‌ها =====================

    private fun pinWidget(cls: Class<*>) {
        val awm = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= 26 && awm.isRequestPinAppWidgetSupported) {
            awm.requestPinAppWidget(ComponentName(this, cls), null, null)
        } else toast("صفحهٔ اصلی را نگه دارید → ویجت‌ها → شمسی‌ساز")
    }

    private fun onNotificationToggle(on: Boolean) {
        if (on && !DateNotifier.canNotify(this)) {
            swNotification.isChecked = false
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            toast("ابتدا اجازهٔ اعلان را بدهید، سپس دوباره روشن کنید")
            return
        }
        Prefs.setStatusNotification(this, on)
        Refresher.refreshAll(this)
    }

    private fun openCalendarApp() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CALENDAR); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time"))) } catch (_: Exception) { toast("برنامهٔ تقویم پیدا نشد") }
        }
    }

    private fun openLanguageSettings() {
        for (a in listOf(Settings.ACTION_LOCALE_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { startActivity(Intent(a)); return } catch (_: Exception) {}
        }
    }

    private fun needCalendar(): Boolean {
        if (hasCalendarPermission()) return false
        permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        return true
    }

    private fun onSyncHolidays() {
        if (needCalendar()) return
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { HolidayCalendar.sync(this@MainActivity) } }
            r.onSuccess {
                toast("✅ ${JalaliConverter.fa(it.events)} مناسبت (${JalaliConverter.fa(it.holidays)} تعطیل) برای ${JalaliConverter.fa(it.fromYear)} و ${JalaliConverter.fa(it.toYear)} اضافه شد")
            }.onFailure { toast("خطا: ${it.message}") }
            refreshAll()
        }
    }

    private fun onRemoveHolidays() {
        if (needCalendar()) return
        lifecycleScope.launch {
            val done = withContext(Dispatchers.IO) { runCatching { HolidayCalendar.remove(this@MainActivity) }.getOrDefault(false) }
            toast(if (done) "تقویم مناسبت‌ها حذف شد" else "چیزی برای حذف نبود")
            refreshAll()
        }
    }

    private fun onPreview() {
        if (needCalendar()) return
        lifecycleScope.launch {
            val p = withContext(Dispatchers.IO) { runCatching { converter.preview(15) }.getOrDefault(emptyList()) }
            if (p.isEmpty()) { toast("هیچ رویدادی با تاریخ میلادی در متن پیدا نشد"); return@launch }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("پیش‌نمایش (${JalaliConverter.fa(p.size)} مورد اول)")
                .setMessage(p.joinToString("\n\n") { "• ${it.before}\n→ ${it.after}" })
                .setPositiveButton("تبدیل کن") { _, _ -> onConvert() }
                .setNegativeButton("بستن", null).show()
        }
    }

    private fun onConvert() {
        if (needCalendar()) return
        tvConvertStatus.text = "در حال تبدیل…"
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { converter.convertAll() } }
            r.onSuccess {
                toast("✅ ${JalaliConverter.fa(it.converted)} رویداد تبدیل شد (${JalaliConverter.fa(it.skipped)} بدون تاریخ میلادی، ${JalaliConverter.fa(it.readOnly)} فقط‌خواندنی، ${JalaliConverter.fa(it.errors)} خطا)")
            }.onFailure { toast("خطا: ${it.message}") }
            refreshAll()
        }
    }

    private fun onUndo() {
        AlertDialog.Builder(this)
            .setTitle("بازگردانی")
            .setMessage("متن ${JalaliConverter.fa(converter.backupCount())} رویداد به حالت اول برمی‌گردد. رویدادهایی که بعد از تبدیل ویرایش کرده‌اید دست نمی‌خورند.")
            .setPositiveButton("بازگردانی") { _, _ ->
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { converter.undo() } }
                    r.onSuccess { toast("↩️ ${JalaliConverter.fa(it.restored)} رویداد بازگردانده شد" + if (it.keptEdited > 0) " (${JalaliConverter.fa(it.keptEdited)} مورد ویرایش‌شده دست نخورد)" else "") }
                        .onFailure { toast("خطا: ${it.message}") }
                    refreshAll()
                }
            }
            .setNegativeButton("لغو", null).show()
    }
}
