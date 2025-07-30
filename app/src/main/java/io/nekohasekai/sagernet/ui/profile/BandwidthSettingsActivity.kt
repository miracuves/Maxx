package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.utils.BandwidthManager
import moe.matsuri.nb4a.ui.SimpleMenuPreference
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BandwidthSettingsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_bandwidth_settings_activity)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.bandwidth_limit)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, BandwidthSettingsFragment())
                .commit()
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    class BandwidthSettingsFragment : PreferenceFragmentCompat() {
        private lateinit var proxyEntity: ProxyEntity
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.bandwidth_preferences, rootKey)
            proxyEntity = requireActivity().intent.getParcelableExtra("proxyEntity")!!
            val enableLimit = findPreference<SwitchPreference>("enableBandwidthLimit")!!
            val limitValue = findPreference<EditTextPreference>("bandwidthLimitValue")!!
            val limitUnit = findPreference<SimpleMenuPreference>("bandwidthLimitUnit")!!
            val usageInfo = findPreference<Preference>("bandwidthUsageInfo")!!
            val resetUsage = findPreference<Preference>("resetBandwidthUsage")!!
            enableLimit.isChecked = proxyEntity.bandwidthLimitEnabled
            limitValue.text = if (proxyEntity.bandwidthLimit > 0) {
                BandwidthManager.convertFromBytes(proxyEntity.bandwidthLimit, proxyEntity.bandwidthLimitUnit).toString()
            } else "100"
            limitUnit.value = proxyEntity.bandwidthLimitUnit ?: "MB"
            enableLimit.setOnPreferenceChangeListener { _, newValue ->
                proxyEntity.bandwidthLimitEnabled = newValue as Boolean
                lifecycleScope.launch { ProfileManager.updateProfile(proxyEntity) }
                updateUsageDisplay(usageInfo)
                true
            }
            limitValue.setOnBindEditTextListener { editText ->
                editText.hint = "Enter limit value"
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            limitValue.setOnPreferenceChangeListener { _, newValue ->
                val value = (newValue as String).toLongOrNull() ?: 100L
                val unit = limitUnit.value ?: "MB"
                proxyEntity.bandwidthLimit = BandwidthManager.convertToBytes(value, unit)
                proxyEntity.bandwidthLimitUnit = unit
                lifecycleScope.launch { ProfileManager.updateProfile(proxyEntity) }
                updateUsageDisplay(usageInfo)
                true
            }
            limitUnit.setOnPreferenceChangeListener { _, newValue ->
                val value = limitValue.text?.toLongOrNull() ?: 100L
                val unit = newValue as? String ?: "MB"
                proxyEntity.bandwidthLimit = BandwidthManager.convertToBytes(value, unit)
                proxyEntity.bandwidthLimitUnit = unit
                lifecycleScope.launch { ProfileManager.updateProfile(proxyEntity) }
                updateUsageDisplay(usageInfo)
                true
            }
            resetUsage.setOnPreferenceClickListener {
                BandwidthManager.resetUsage(proxyEntity)
                updateUsageDisplay(usageInfo)
                true
            }
            updateUsageDisplay(usageInfo)
        }
        private fun updateUsageDisplay(usageInfo: Preference) {
            val totalUsage = BandwidthManager.getTotalUsage(proxyEntity)
            val usageFormatted = BandwidthManager.formatBytes(totalUsage)
            if (proxyEntity.bandwidthLimitEnabled && proxyEntity.bandwidthLimit > 0) {
                val remaining = BandwidthManager.getRemainingBytes(proxyEntity)
                val remainingFormatted = BandwidthManager.formatBytes(remaining)
                val percentage = BandwidthManager.getUsagePercentage(proxyEntity)
                usageInfo.summary = "Used: $usageFormatted\nRemaining: $remainingFormatted\nProgress: ${String.format("%.1f", percentage)}%"
            } else {
                usageInfo.summary = "Used: $usageFormatted\nNo limit set"
            }
        }
    }
} 