package com.shamsisaz.jalali

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shamsisaz.jalali.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        converter = CalendarConverter(this)

        // هدر شمسی امروز
        val today = JalaliConverter.today()
        binding.tvToday.text = "امروز: ${today.formatPersian()} — ${JalaliConverter.toPersianDigits(today.formatNumeric())}"
        binding.tvBrand.text = getString(R.string.brand_detected, DeviceHelper.brandLabel())
        binding.tvHint.text = DeviceHelper.systemHint()

        // دکمهٔ جادویی — درخواست خودکار دسترسی + تبدیل
        binding.btnMagic.setOnClickListener { onMagicClick() }
        binding.btnPreview.setOnClickListener { onPreview() }
        binding.btnUndo.setOnClickListener { onUndo() }
        binding.btnSystemSettings.setOnClickListener { DeviceHelper.openLanguageSettings(this) }
        binding.btnOpenCalendar.setOnClickListener { DeviceHelper.openCalendarApp(this) }

        // درخواست خودکار دسترسی‌ها در شروع (بدون اذیت مکرر)
        autoRequestPermissionsIfNeeded()

        refreshStats()
        updateButtons()
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

    private fun onMagicClick() {
        if (!hasCalendarPermission()) {
            autoRequestPermissionsIfNeeded()
            return
        }
        // تأیید نهایی قبل از تبدیل کل تقویم
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
                addPrefix = true,
                convertBody = true,
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
                if (result.converted > 0) {
                    binding.tvStatus.text = "✅ ${result.converted} رویداد به شمسی شد! (${result.calendars} تقویم)"
                    Toast.makeText(this@MainActivity, "تمام شد — ${result.converted} رویداد شمسی شد", Toast.LENGTH_LONG).show()
                } else if (result.total == 0) {
                    binding.tvStatus.text = getString(R.string.status_no_events)
                } else {
                    binding.tvStatus.text = "تغییری لازم نبود — همه شمسی هستند یا خالی"
                }
                // نمایش راهنمای تنظیمات سیستم برای اعمال کامل در سامسونگ/شیائومی
                binding.cardSystemHint.visibility = View.VISIBLE
                refreshStats()
                updateButtons()
            }
        }
    }

    private fun onPreview() {
        if (!hasCalendarPermission()) {
            autoRequestPermissionsIfNeeded()
            return
        }
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
        if (!hasCalendarPermission()) {
            binding.tvStats.text = "—"
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val cals = try { converter.listCalendars() } catch (_: Exception) { emptyList() }
            val events = try { converter.listEvents(1000) } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                binding.tvStats.text = "تقویم‌ها: ${cals.size}  •  رویدادها: ${events.size}"
            }
        }
    }

    private fun updateButtons() {
        val hasPerm = hasCalendarPermission()
        binding.btnPreview.isEnabled = hasPerm
        binding.btnUndo.isEnabled = hasPerm && converter.hasBackup()
        binding.btnUndo.alpha = if (binding.btnUndo.isEnabled) 1f else 0.4f
        binding.btnPreview.alpha = if (hasPerm) 1f else 0.4f
    }
}
