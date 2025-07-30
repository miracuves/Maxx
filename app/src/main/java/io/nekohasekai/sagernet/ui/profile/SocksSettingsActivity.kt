package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.CountryFlagUtil
import kotlinx.coroutines.launch
import moe.matsuri.nb4a.ui.SimpleMenuPreference
import io.nekohasekai.sagernet.utils.BandwidthManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.preference.SwitchPreference
import androidx.preference.Preference

class SocksSettingsActivity : ProfileSettingsActivity<SOCKSBean>() {
    override fun createEntity() = SOCKSBean()

    override fun SOCKSBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort

        DataStore.serverProtocolInt = protocol
        DataStore.serverUsername = username
        DataStore.serverPassword = password

        DataStore.profileCacheStore.putBoolean("sUoT", sUoT)
        
        // Fetch country information if not already set
        if (country.isBlank() && serverAddress.isNotBlank()) {
            runOnDefaultDispatcher {
                try {
                    val countryFlag = CountryFlagUtil.getCountryFlagForProxy(serverAddress)
                    if (countryFlag.isNotBlank()) {
                        // Extract country code from flag emoji
                        val flag = countryFlag
                        if (flag.length == 2) {
                            val first = flag[0].code - 0x1F1E6 + 65
                            val second = flag[1].code - 0x1F1E6 + 65
                            country = "${first.toChar()}${second.toChar()}"
                            
                            // Update the profile in the database to persist the country
                            onMainDispatcher {
                                try {
                                    val editingId = DataStore.editingId
                                    if (editingId != 0L) {
                                        val profile = ProfileManager.getProfile(editingId)
                                        if (profile != null) {
                                            profile.socksBean?.country = country
                                            ProfileManager.updateProfile(profile)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore errors in profile update
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors in country detection
                }
            }
        }
    }

    override fun SOCKSBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort

        protocol = DataStore.serverProtocolInt
        username = DataStore.serverUsername
        password = DataStore.serverPassword

        sUoT = DataStore.profileCacheStore.getBoolean("sUoT")
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.socks_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        val password = findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!

        fun updateProtocol(version: Int) {
            password.isVisible = version == SOCKSBean.PROTOCOL_SOCKS5
        }

        updateProtocol(DataStore.protocolVersion)
        protocol.setOnPreferenceChangeListener { _, newValue ->
            updateProtocol((newValue as String).toInt())
            true
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
