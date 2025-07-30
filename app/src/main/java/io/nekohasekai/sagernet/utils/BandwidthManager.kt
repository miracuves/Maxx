package io.nekohasekai.sagernet.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import java.text.DecimalFormat
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object BandwidthManager {
    
    private const val NOTIFICATION_CHANNEL_ID = "bandwidth_limit"
    private const val NOTIFICATION_ID = 1001
    
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val df = DecimalFormat("#,##0.#")
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        
        return "${df.format(value)} ${units[digitGroups]}"
    }
    
    fun convertToBytes(value: Long, unit: String): Long {
        return when (unit.uppercase()) {
            "KB" -> value * 1024
            "MB" -> value * 1024 * 1024
            "GB" -> value * 1024 * 1024 * 1024
            "TB" -> value * 1024L * 1024 * 1024 * 1024
            else -> value
        }
    }
    
    fun convertFromBytes(bytes: Long, unit: String): Long {
        return when (unit.uppercase()) {
            "KB" -> bytes / 1024
            "MB" -> bytes / (1024 * 1024)
            "GB" -> bytes / (1024 * 1024 * 1024)
            "TB" -> bytes / (1024L * 1024 * 1024 * 1024)
            else -> bytes
        }
    }
    
    fun getTotalUsage(proxyEntity: ProxyEntity): Long {
        return proxyEntity.tx + proxyEntity.rx
    }
    
    fun getRemainingBytes(proxyEntity: ProxyEntity): Long {
        if (!proxyEntity.bandwidthLimitEnabled || proxyEntity.bandwidthLimit <= 0) {
            return Long.MAX_VALUE
        }
        
        val limitBytes = convertToBytes(proxyEntity.bandwidthLimit, proxyEntity.bandwidthLimitUnit)
        val totalUsage = getTotalUsage(proxyEntity)
        return (limitBytes - totalUsage).coerceAtLeast(0)
    }
    
    fun isLimitReached(proxyEntity: ProxyEntity): Boolean {
        if (!proxyEntity.bandwidthLimitEnabled || proxyEntity.bandwidthLimit <= 0) {
            return false
        }
        
        val limitBytes = convertToBytes(proxyEntity.bandwidthLimit, proxyEntity.bandwidthLimitUnit)
        val totalUsage = getTotalUsage(proxyEntity)
        return totalUsage >= limitBytes
    }
    
    fun checkAndShowAlert(proxyEntity: ProxyEntity, context: Context) {
        if (!proxyEntity.bandwidthLimitEnabled || proxyEntity.bandwidthLimit <= 0) {
            return
        }
        
        if (isLimitReached(proxyEntity) && !proxyEntity.bandwidthAlertShown) {
            showLimitReachedNotification(proxyEntity, context)
            proxyEntity.bandwidthAlertShown = true
            GlobalScope.launch { ProfileManager.updateProfile(proxyEntity) }
        }
    }
    
    private fun showLimitReachedNotification(proxyEntity: ProxyEntity, context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            
            // Create notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Bandwidth Limit",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for bandwidth limit alerts"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_warning_24)
                .setContentTitle(context.getString(R.string.bandwidth_limit_reached))
                .setContentText(context.getString(R.string.bandwidth_limit_reached_message, proxyEntity.displayName()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID + proxyEntity.id.toInt(), notification)
            
            Logs.d("Bandwidth limit alert shown for profile: ${proxyEntity.displayName()}")
        } catch (e: Exception) {
            Logs.w("Failed to show bandwidth limit notification: ${e.message}")
        }
    }
    
    fun resetUsage(proxyEntity: ProxyEntity) {
        proxyEntity.tx = 0L
        proxyEntity.rx = 0L
        proxyEntity.bandwidthAlertShown = false
        GlobalScope.launch { ProfileManager.updateProfile(proxyEntity) }
        Logs.d("Bandwidth usage reset for profile: ${proxyEntity.displayName()}")
    }
    
    fun getUsagePercentage(proxyEntity: ProxyEntity): Float {
        if (!proxyEntity.bandwidthLimitEnabled || proxyEntity.bandwidthLimit <= 0) {
            return 0f
        }
        
        val limitBytes = convertToBytes(proxyEntity.bandwidthLimit, proxyEntity.bandwidthLimitUnit)
        val totalUsage = getTotalUsage(proxyEntity)
        return (totalUsage.toFloat() / limitBytes.toFloat() * 100f).coerceAtMost(100f)
    }
} 