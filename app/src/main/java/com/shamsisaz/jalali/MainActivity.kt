package com.shamsisaz.jalali

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shamsisaz.jalali.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var converter: CalendarConverter

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val calOk = grants[Manifest.permission.READ_CALENDAR] == true &&
                grants[Manifest.permission.WRITE_CALENDAR] == true
        if (calOk) {
            binding.tvStatus.text = getString(R.string.status_idle)
            refreshStats()
        } else {
            binding.tvStatus.text = getString(R.string.status_permission_needed)
            Toast.makeText(this, "بدون دسترسی تقویم، تبدیل ممکن نیست", Toast.LENGTH_LONG).show()
        }
        updateButtons()
    }

    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "✅ دسترسی شیزوکو داده شد — دوباره دکمه را بزن", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "❌ بدون دسترسی شیزوکو، فارسی‌سازی سیستمی ممکن نیست", Toast.LENGTH_LONG).show()
        }
        runOnUiThread { refreshShizukuCard() }
    }

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener { refreshShizukuCard() }
    private val shizukuDeadListener = Shizuku.OnBinderDeadListener { refreshShizukuCard() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        converter = CalendarConverter(this)

        val today = JalaliConverter.today()
        binding.tvToday.text = "امروز: ${today.formatPersian()} — ${JalaliConverter.toPersianDigits(today.formatNumeric())}"
        binding.tvBrand.text = getString(R.string.brand_detected, DeviceHelper.brandLabel())
        binding.tvHint.text = DeviceHelper.systemHint()

        binding.btnMagic.setOnClickListener { onMagicClick() }
        binding.btnPreview.setOnClickListener { onPreview() }
        binding.btnUndo.setOnClickListener { onUndo() }
        binding.btnSystemSettings.setOnClickListener { onSystemSettingsClick() }
        binding.btnSystemQuickFix.setOnClickListener { onQuickFixClick() }
        binding.btnOpenCalendar.setOnClickListener { DeviceHelper.openCalendarApp(this) }
        try { binding.btnOverlay.setOnClickListener { openOverlaySettings() } } catch (_: Exception) {}

        // 👻 ارواح فارسی — دکمه اصلی فارسی‌ساز کامل
        binding.btnPersianize.setOnClickListener { onPersianizeClick() }
        binding.btnShizukuInstall.setOnClickListener { openShizukuInstall() }
        binding.btnShizukuStart.setOnClickListener { openShizukuStartGuide() }
        binding.btnShizukuPerm.setOnClickListener { requestShizukuPerm() }

        Shizuku.addRequestPermissionResultListener(shizukuPermListener)
        Shizuku.addBinderReceivedListener(shizukuBinderListener)
        Shizuku.addBinderDeadListener(shizukuDeadListener)

        autoRequestPermissionsIfNeeded()
        refreshStats()
        updateButtons()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermListener) } catch (_: Exception) {}
        try { Shizuku.removeBinderReceivedListener(shizukuBinderListener) } catch (_: Exception) {}
        try { Shizuku.removeBinderDeadListener(shizukuDeadListener) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        updateButtons()
    }

    private fun hasCalendarPermission(): Boolean {
        val r = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
        val w = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
        return r == PackageManager.PERMISSION_GRANTED && w == PackageManager.PERMISSION_GRANTED
    }

    private fun autoRequestPermissionsIfNeeded() {
        if (hasCalendarPermission()) return
        val perms = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())
    }

    // ============ 👻 ارواح فارسی: فارسی‌ساز کامل با یک دکمه ============

    private fun onPersianizeClick() {
        if (!hasCalendarPermission()) { autoRequestPermissionsIfNeeded(); return }
        if (!ShizukuHelper.isInstalled(this)) {
            showShizukuNeededDialog()
            return
        }
        if (!ShizukuHelper.isBinderAlive()) {
            showShizukuStartDialog()
            return
        }
        if (!ShizukuHelper.isPermissionGranted()) {
            ShizukuHelper.requestPermission(this)
            Toast.makeText(this, "🔑 در پنجره شیزوکو «Allow» را بزن، بعد دوباره دکمه را بزن", Toast.LENGTH_LONG).show()
            return
        }
        runPersianize()
    }

    private fun runPersianize() {
        binding.tvShizukuLog.text = "👻 ارواح فارسی شروع کردن…\n"
        binding.tvShizukuLog.visibility = View.VISIBLE
        binding.logScroll.visibility = View.VISIBLE
        binding.btnPersianize.isEnabled = false
        binding.btnPersianize.text = "👻 در حال فارسی‌سازی کل گوشی…"
        lifecycleScope.launch {
            val res = Persianizer.fullPersianize(this@MainActivity) { line ->
                runOnUiThread {
                    binding.tvShizukuLog.append(line + "\n")
                    // اسکرول به آخر
                    try {
                        binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
                    } catch (_: Exception) {}
                }
            }
            withContext(Dispatchers.Main) {
                binding.btnPersianize.isEnabled = true
                binding.btnPersianize.text = "👻 فارسی‌سازی کامل کل گوشی"
                binding.tvShizukuLog.append(if (res.ok) "\n🎉 تمام شد!" else "\n⚠️ کامل نشد — لاگ را بخوان")
                refreshStats()
                updateButtons()
                if (res.ok) {
                    Toast.makeText(this@MainActivity, "🎉 گوشی فارسی شد! تقویم را باز کن", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showShizukuNeededDialog() {
        AlertDialog.Builder(this)
            .setTitle("👻 قدم اول: نصب شیزوکو")
            .setMessage("برای تغییر واقعی کل گوشی از داخل خود گوشی (بدون کامپیوتر، بدون روت)، اول اپ «Shizuku» را نصب کن:\n\n۱) دکمه نصب را بزن\n۲) شیزوکو را نصب کن و برگرد اینجا\n۳) قدم بعدی: روشن‌کردن با دیباگ وایرلس")
            .setPositiveButton("📥 نصب شیزوکو") { _, _ -> openShizukuInstall() }
            .setNegativeButton("بعداً", null)
            .show()
    }

    private fun showShizukuStartDialog() {
        val steps = if (Build.VERSION.SDK_INT >= 30) {
            "روی همین گوشی، بدون کامپیوتر:\n\n" +
            "۱) تنظیمات → درباره گوشی → ۷ بار روی «شماره ساخت» بزن (گزینه توسعه‌دهنده باز می‌شود)\n" +
            "۲) تنظیمات → سیستم → گزینه‌های توسعه‌دهنده → «اشکال‌زدایی بی‌سیم» را روشن کن\n" +
            "۳) اپ Shizuku را باز کن → «Pairing» → کد جفت‌سازی را بزن\n" +
            "۴) در Shizuku روی «Start» بزن\n" +
            "۵) برگرد اینجا و دکمه 👻 را بزن"
        } else {
            "روی کامپیوتر یک بار:\n\n" +
            "۱) اپ Shizuku را نصب کن\n" +
            "۲) گوشی را با کابل به کامپیوتر وصل کن\n" +
            "۳) دستور: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
            "۴) برگرد اینجا و دکمه 👻 را بزن"
        }
        AlertDialog.Builder(this)
            .setTitle("🚀 روشن‌کردن شیزوکو")
            .setMessage(steps)
            .setPositiveButton("باز کردن شیزوکو") { _, _ -> openShizukuApp() }
            .setNeutralButton("آموزش تصویری") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/"))) } catch (_: Exception) {}
            }
            .setNegativeButton("باشه", null)
            .show()
    }

    private fun openShizukuInstall() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        } catch (_: Exception) {
            Toast.makeText(this, "مرورگر باز نشد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openShizukuApp() {
        try {
            val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (i != null) startActivity(i)
            else openShizukuInstall()
        } catch (_: Exception) { openShizukuInstall() }
    }

    private fun openShizukuStartGuide() {
        if (!ShizukuHelper.isInstalled(this)) { showShizukuNeededDialog(); return }
        if (!ShizukuHelper.isBinderAlive()) { showShizukuStartDialog(); return }
        Toast.makeText(this, "✅ شیزوکو روشن است", Toast.LENGTH_SHORT).show()
    }

    private fun requestShizukuPerm() {
        if (!ShizukuHelper.isInstalled(this)) { showShizukuNeededDialog(); return }
        if (!ShizukuHelper.isBinderAlive()) { showShizukuStartDialog(); return }
        if (ShizukuHelper.isPermissionGranted()) {
            Toast.makeText(this, "✅ دسترسی قبلاً داده شده", Toast.LENGTH_SHORT).show()
            return
        }
        ShizukuHelper.requestPermission(this)
    }

    private fun refreshShizukuCard() {
        runOnUiThread {
            try {
                val st = ShizukuHelper.statusText(this)
                binding.tvShizukuStatus.text = st
                binding.tvShizukuStatus.visibility = View.VISIBLE
                val ready = ShizukuHelper.isPermissionGranted()
                // دکمه اصلی همیشه فعال — خودش قدم‌به‌قدم راهنمایی می‌کند
                binding.btnPersianize.isEnabled = true
                // دکمه‌ها را هوشمند کن
                binding.btnShizukuInstall.text = if (ShizukuHelper.isInstalled(this)) "✅ شیزوکو نصب است" else "📥 قدم ۱: نصب شیزوکو"
                binding.btnShizukuStart.text = if (ShizukuHelper.isBinderAlive()) "✅ شیزوکو روشن است" else "🚀 قدم ۲: روشن‌کردن شیزوکو"
                binding.btnShizukuPerm.text = if (ready) "✅ دسترسی داده شده" else "🔑 قدم ۳: دادن دسترسی"
            } catch (_: Exception) {}
        }
    }

    // ============ تبدیل تقویم (مسیر قبلی، نگه داشته شده) ============

    private fun onMagicClick() {
        if (!hasCalendarPermission()) { autoRequestPermissionsIfNeeded(); return }
        AlertDialog.Builder(this)
            .setTitle("تبدیل کل تقویم به شمسی؟")
            .setMessage("همهٔ رویدادهای تقویم گوشی به تاریخ شمسی تبدیل می‌شوند.\nقابل بازگردانی با دکمهٔ «بازگردانی» است.")
            .setPositiveButton("بزن بریم ✨") { _, _ -> doConvert() }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun doConvert() {
        binding.tvStatus.text = getString(R.string.status_converting)
        binding.progress.visibility = View.VISIBLE
        binding.btnMagic.isEnabled = false
        binding.progress.progress = 0
        lifecycleScope.launch(Dispatchers.IO) {
            val result = converter.convertAll(
                addPrefix = true, convertBody = true,
                onProgress = { done, total ->
                    runOnUiThread {
                        if (total > 0) binding.progress.progress = (done * 100 / total)
                        binding.tvStatus.text = "در حال تبدیل… $done / $total"
                    }
                }
            )
            withContext(Dispatchers.Main) {
                binding.progress.visibility = View.GONE
                binding.btnMagic.isEnabled = true
                binding.tvStatus.text = when {
                    result.converted > 0 -> "✅ ${result.converted} رویداد به شمسی شد! (${result.calendars} تقویم)"
                    result.total == 0 -> getString(R.string.status_no_events)
                    else -> "تغییری لازم نبود — همه شمسی هستند یا خالی"
                }
                binding.cardSystemHint.visibility = View.VISIBLE
                refreshStats()
                updateButtons()
            }
        }
    }

    private fun onPreview() {
        if (!hasCalendarPermission()) { autoRequestPermissionsIfNeeded(); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val preview = converter.preview(15)
            withContext(Dispatchers.Main) {
                if (preview.isEmpty()) {
                    Toast.makeText(this@MainActivity, "موردی برای پیش‌نمایش نیست", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val msg = preview.joinToString("\n\n") { (before, after) -> "• $before\n→ $after" }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.dialog_preview_title))
                    .setMessage(msg)
                    .setPositiveButton(getString(R.string.btn_ok)) { _, _ -> }
                    .setNeutralButton("تبدیل کن ✨") { _, _ -> doConvert() }
                    .show()
            }
        }
    }

    private fun onUndo() {
        if (!converter.hasBackup()) {
            Toast.makeText(this, "بکاپی برای بازگردانی نیست", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_undo_title))
            .setMessage(getString(R.string.dialog_undo_msg) + "\n\n${converter.backupCount()} عنوان بازگردانی می‌شود.")
            .setPositiveButton(getString(R.string.btn_ok)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val n = converter.undo()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "$n عنوان بازگردانی شد", Toast.LENGTH_LONG).show()
                        refreshStats()
                        updateButtons()
                        binding.tvStatus.text = "↩️ بازگردانی انجام شد"
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun refreshStats() {
        if (!hasCalendarPermission()) { binding.tvStats.text = "—"; return }
        lifecycleScope.launch(Dispatchers.IO) {
            val cals = try { converter.listCalendars() } catch (_: Exception) { emptyList() }
            val events = try { converter.listEvents(1000) } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                binding.tvStats.text = "تقویم‌ها: ${cals.size}  •  رویدادها: ${events.size}"
            }
        }
    }

    private fun onSystemSettingsClick() {
        DeviceHelper.openLanguageSettings(this)
        Toast.makeText(this, DeviceHelper.stepsForA336E(), Toast.LENGTH_LONG).show()
    }

    private fun onQuickFixClick() {
        val okApp = SystemLocaleHelper.trySetAppLocaleToPersian(this)
        val okSys = SystemLocaleHelper.trySetSystemLocaleViaSettings(this)
        val locale = SystemLocaleHelper.currentLocaleTag()
        val msg = when {
            okSys -> "✅ لوکال سیستم به فارسی تغییر کرد ($locale) — تقویم خودکار شمسی می‌شود. اپ را ببند و باز کن."
            okApp -> "✅ لوکال اپ به فارسی شد. برای کل گوشی: دکمهٔ «باز کردن تنظیمات زبان» → فارسی را اول کن."
            else -> "برای کل گوشی:\n${DeviceHelper.stepsForA336E()}\n\nلوکال فعلی: $locale"
        }
        AlertDialog.Builder(this)
            .setTitle("وضعیت لوکال: $locale")
            .setMessage(msg)
            .setPositiveButton("باز کردن تنظیمات") { _, _ -> DeviceHelper.openLanguageSettings(this) }
            .setNegativeButton("کپی دستور ADB") { _, _ ->
                SystemLocaleHelper.copyAdbCommand(this)
                Toast.makeText(this, "دستور ADB کپی شد", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("باشه", null)
            .show()
        refreshSystemStatus()
        refreshOverlayStatus()
    }

    private fun refreshSystemStatus() {
        val locale = SystemLocaleHelper.currentLocaleTag()
        val persian = SystemLocaleHelper.isPersianLocale()
        binding.tvSystemStatus.text = if (persian) "✅ لوکال: $locale — تقویم سیستمی شمسی است"
        else "⚠️ لوکال: $locale — برای شمسی شدن کل گوشی، فارسی را اول کن"
        binding.tvSystemStatus.visibility = View.VISIBLE
    }

    private fun updateButtons() {
        val hasPerm = hasCalendarPermission()
        binding.btnPreview.isEnabled = hasPerm
        binding.btnUndo.isEnabled = hasPerm && converter.hasBackup()
        binding.btnUndo.alpha = if (binding.btnUndo.isEnabled) 1f else 0.4f
        binding.btnPreview.alpha = if (hasPerm) 1f else 0.4f
        refreshSystemStatus()
        refreshOverlayStatus()
        refreshShizukuCard()
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            Toast.makeText(this, "شمسی‌ساز را در لیست دسترس‌پذیری پیدا و فعال کنید — تاریخ پیام‌ها زنده شمسی می‌شود", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { DeviceHelper.openLanguageSettings(this) }
    }

    private fun refreshOverlayStatus() {
        val enabled = ShamsiOverlayService.isEnabled(this)
        try {
            binding.btnOverlay.text = if (enabled) "✅ پوشش پیام‌ها فعال است" else "👁️ فعال‌سازی پوشش پیام‌ها (اختیاری)"
            binding.tvOverlayStatus.text = if (enabled) "✅ پوشش سیستمی فعال — تاریخ پیام‌ها شمسی نمایش داده می‌شود" else "پوشش خاموش — فعال کنید تا تاریخ‌های داخل پیام‌ها هم زنده شمسی شود"
            binding.tvOverlayStatus.visibility = View.VISIBLE
        } catch (_: Exception) {}
    }

    private fun findTv(id: String): TextView? {
        return try {
            val rid = resources.getIdentifier(id, "id", packageName)
            if (rid != 0) findViewById(rid) else null
        } catch (_: Exception) { null }
    }
}
