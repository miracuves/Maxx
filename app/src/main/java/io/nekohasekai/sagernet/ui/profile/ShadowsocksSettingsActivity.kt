package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.utils.BandwidthManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.preference.SwitchPreference
import androidx.preference.Preference
import moe.matsuri.nb4a.ui.SimpleMenuPreference
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override fun createEntity() = ShadowsocksBean()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val method = pbm.add(PreferenceBinding(Type.Text, "method"))
    private val pluginName =
        pbm.add(PreferenceBinding(Type.Text, "pluginName").apply { disable = true })
    private val pluginConfig =
        pbm.add(PreferenceBinding(Type.Text, "pluginConfig").apply { disable = true })
    private val sUoT = pbm.add(PreferenceBinding(Type.Bool, "sUoT"))

    override fun ShadowsocksBean.init() {
        pbm.writeToCacheAll(this)

        DataStore.profileCacheStore.putString("pluginName", plugin.substringBefore(";"))
        DataStore.profileCacheStore.putString("pluginConfig", plugin.substringAfter(";"))
    }

    override fun ShadowsocksBean.serialize() {
        pbm.fromCacheAll(this)

        val pn = pluginName.readStringFromCache()
        val pc = pluginConfig.readStringFromCache()
        plugin = if (pn.isNotBlank()) "$pn;$pc" else ""
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowsocks_preferences)
        pbm.setPreferenceFragment(this)

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }

        // Bandwidth settings
        val enableLimit = findPreference<SwitchPreference>("enableBandwidthLimit")!!
        val limitValue = findPreference<EditTextPreference>("bandwidthLimitValue")!!
        val limitUnit = findPreference<SimpleMenuPreference>("bandwidthLimitUnit")!!
        val usageInfo = findPreference<Preference>("bandwidthUsageInfo")!!
        val resetUsage = findPreference<Preference>("resetBandwidthUsage")!!

        // Get current proxy entity for bandwidth settings
        val editingId = DataStore.editingId
        val proxyEntity = if (editingId != 0L) {
            ProfileManager.getProfile(editingId)
        } else null

        // Set current values
        if (proxyEntity != null) {
            enableLimit.isChecked = proxyEntity.bandwidthLimitEnabled
            limitValue.text = if (proxyEntity.bandwidthLimit > 0) {
                BandwidthManager.convertFromBytes(proxyEntity.bandwidthLimit, proxyEntity.bandwidthLimitUnit).toString()
            } else "100"
            limitUnit.value = proxyEntity.bandwidthLimitUnit ?: "MB"
        }

        // Set up listeners
        enableLimit.setOnPreferenceChangeListener { _, newValue ->
            if (proxyEntity != null) {
                proxyEntity.bandwidthLimitEnabled = newValue as Boolean
                lifecycleScope.launch { ProfileManager.updateProfile(proxyEntity) }
                updateBandwidthUsageDisplay(usageInfo, proxyEntity)
            }
            true
        }

        limitValue.setOnBindEditTextListener { editText ->
            editText.hint = "Enter limit value"
            editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        limitValue.setOnPreferenceChangeListener { _, newValue ->
            if (proxyEntity != null) {
                val value = (newValue as String).toLongOrNull() ?: 100L
                val unit = limitUnit.value ?: "MB"
                proxyEntity.bandwidthLimit = BandwidthManager.convertToBytes(value, unit)
                proxyEntity.bandwidthLimitUnit = unit
                lifecycleScope.launch { ProfileManager.updateProfile(proxyEntity) }
                updateBandwidthUsageDisplay(usageInfo, proxyEntity)
            }
            true
        }

        limitUnit.setOnPreferenceChangeListener { _, newValue ->
            if (proxyEntity != null) {
                val value = limitValue.text?.toLongOrNull() ?: 100L
                val unit = newValue as? String ?: "MB"
                proxyEntity.bandwidthLimit = BandwidthManager.convertToBytes(value, unit)
                proxyEntity.bandwidthLimitUnit = unit
                lifecycleScope.launch { ProfileManager.updateProfile(proxyEntity) }
                updateBandwidthUsageDisplay(usageInfo, proxyEntity)
            }
            true
        }

        resetUsage.setOnPreferenceClickListener {
            if (proxyEntity != null) {
                BandwidthManager.resetUsage(proxyEntity)
                updateBandwidthUsageDisplay(usageInfo, proxyEntity)
            }
            true
        }

        if (proxyEntity != null) {
            updateBandwidthUsageDisplay(usageInfo, proxyEntity)
        }
    }

    private fun updateBandwidthUsageDisplay(usageInfo: Preference, proxyEntity: ProxyEntity) {
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
