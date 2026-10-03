package com.shamsisaz.jalali

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shamsisaz.jalali.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        binding.btnOpenSystemLang.setOnClickListener { DeviceHelper.openLanguageSettings(this) }
        binding.btnOpenCalendar.setOnClickListener { DeviceHelper.openCalendarApp(this) }
        binding.tvDeviceInfo.text = "${DeviceHelper.brandLabel()}\n${DeviceHelper.systemHint()}"
        val today = JalaliConverter.today()
        binding.tvTodayPersian.text = "${today.formatPersian()} — ${JalaliConverter.toPersianDigits(today.formatNumeric())}"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
