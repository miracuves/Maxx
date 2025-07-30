package io.nekohasekai.sagernet.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import io.nekohasekai.sagernet.database.DataStore
import java.util.*

object LanguageHelper {
    
    fun setLanguage(context: Context, languageCode: String) {
        val locale = when (languageCode) {
            "en" -> Locale("en")
            "ru" -> Locale("ru")
            "es" -> Locale("es")
            "zh" -> Locale("zh")
            "vi" -> Locale("vi")
            "fr" -> Locale("fr")
            "de" -> Locale("de")
            else -> Locale("en")
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        context.createConfigurationContext(config)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
    
    fun getCurrentLanguage(): String {
        return DataStore.language ?: "en"
    }
    
    fun applyLanguage(context: Context) {
        val languageCode = getCurrentLanguage()
        setLanguage(context, languageCode)
    }
} 