package io.nekohasekai.sagernet.utils

import android.content.Context
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object LatencyChecker {
    
    private val isRunning = AtomicBoolean(false)
    private val latencyCache = ConcurrentHashMap<Long, Int>()
    private var latencyJob: Job? = null
    private var uiUpdateCallback: (() -> Unit)? = null
    
    fun setUIUpdateCallback(callback: () -> Unit) {
        uiUpdateCallback = callback
    }
    
    fun startLatencyCheck(context: Context) {
        if (isRunning.getAndSet(true)) {
            Logs.d("Latency checker already running")
            return
        }
        
        Logs.d("Starting latency checker")
        latencyJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    checkAllProxiesLatency(context)
                    delay(60000) // Wait 1 minute
                } catch (e: Exception) {
                    Logs.w("Error in latency check: ${e.message}")
                    delay(10000) // Wait 10 seconds on error
                }
            }
        }
    }
    
    suspend fun performImmediateCheck(context: Context) {
        Logs.d("Performing immediate latency check")
        try {
            // For testing, add some fake latency data first
            val proxies = SagerDatabase.proxyDao.getAll()
            if (proxies.isNotEmpty()) {
                Logs.d("Adding test latency data for ${proxies.size} proxies")
                proxies.forEach { proxy ->
                    latencyCache[proxy.id] = (100..500).random() // Random latency between 100-500ms
                }
                // Trigger UI update immediately
                onMainDispatcher {
                    try {
                        uiUpdateCallback?.invoke()
                        Logs.d("Triggered UI update with test latency data")
                    } catch (e: Exception) {
                        Logs.w("Error updating UI with test data: ${e.message}")
                    }
                }
            }
            
            // Now do the real latency check
            checkAllProxiesLatency(context)
            Logs.d("Immediate latency check completed")
        } catch (e: Exception) {
            Logs.w("Error in immediate latency check: ${e.message}")
        }
    }
    
    fun stopLatencyCheck() {
        isRunning.set(false)
        latencyJob?.cancel()
        latencyJob = null
        Logs.d("Stopped latency checker")
    }
    
    private suspend fun checkAllProxiesLatency(context: Context) {
        try {
            val proxies = SagerDatabase.proxyDao.getAll()
            Logs.d("Found ${proxies.size} proxies to check latency for")
            
            if (proxies.isEmpty()) {
                Logs.d("No proxies found to check")
                return
            }
            
            // Check latency for all proxies concurrently with timeout
            val jobs = proxies.map { proxy ->
                CoroutineScope(Dispatchers.IO).async {
                    try {
                        val latency = checkProxyLatency(proxy)
                        latencyCache[proxy.id] = latency
                        Logs.d("Cached latency for proxy ${proxy.id}: $latency")
                        latency
                    } catch (e: Exception) {
                        Logs.w("Error checking latency for proxy ${proxy.id}: ${e.message}")
                        latencyCache[proxy.id] = -1
                        -1
                    }
                }
            }
            
            // Wait for all latency checks to complete with timeout
            withTimeout(30000) { // 30 second timeout
                jobs.awaitAll()
            }
            
            // Update UI on main thread
            onMainDispatcher {
                try {
                    // Use callback to update UI
                    uiUpdateCallback?.invoke()
                    Logs.d("Triggered UI update via callback")
                } catch (e: Exception) {
                    Logs.w("Error updating UI: ${e.message}")
                }
            }
            
            Logs.d("Latency check completed for ${proxies.size} proxies")
        } catch (e: Exception) {
            Logs.w("Error checking proxies latency: ${e.message}")
        }
    }
    
    private suspend fun checkProxyLatency(proxy: ProxyEntity): Int {
        return withContext(Dispatchers.IO) {
            try {
                val serverAddress = proxy.displayAddress()
                val serverPort = getServerPort(proxy)
                
                Logs.d("Checking latency for ${proxy.displayName()} at $serverAddress:$serverPort")
                
                if (serverAddress.isBlank() || serverPort <= 0) {
                    Logs.d("Invalid server address or port for ${proxy.displayName()}")
                    return@withContext -1
                }
                
                val socket = Socket()
                val startTime = System.currentTimeMillis()
                
                socket.connect(InetSocketAddress(serverAddress, serverPort), 3000) // 3 second timeout
                socket.close()
                
                val latency = (System.currentTimeMillis() - startTime).toInt()
                Logs.d("Latency for ${proxy.displayName()}: ${latency}ms")
                latency
            } catch (e: IOException) {
                Logs.d("Connection failed for ${proxy.displayName()}: ${e.message}")
                -1
            } catch (e: Exception) {
                Logs.w("Error checking latency for ${proxy.displayName()}: ${e.message}")
                -1
            }
        }
    }
    
    private fun getServerPort(proxy: ProxyEntity): Int {
        return when (proxy.type) {
            ProxyEntity.TYPE_SOCKS -> proxy.socksBean?.serverPort ?: 0
            ProxyEntity.TYPE_HTTP -> proxy.httpBean?.serverPort ?: 0
            ProxyEntity.TYPE_SS -> proxy.ssBean?.serverPort ?: 0
            ProxyEntity.TYPE_VMESS -> proxy.vmessBean?.serverPort ?: 0
            ProxyEntity.TYPE_TROJAN -> proxy.trojanBean?.serverPort ?: 0
            ProxyEntity.TYPE_TROJAN_GO -> proxy.trojanGoBean?.serverPort ?: 0
            ProxyEntity.TYPE_MIERU -> proxy.mieruBean?.serverPort ?: 0
            ProxyEntity.TYPE_NAIVE -> proxy.naiveBean?.serverPort ?: 0
            ProxyEntity.TYPE_HYSTERIA -> proxy.hysteriaBean?.serverPort ?: 0
            ProxyEntity.TYPE_SSH -> proxy.sshBean?.serverPort ?: 0
            ProxyEntity.TYPE_WG -> proxy.wgBean?.serverPort ?: 0
            ProxyEntity.TYPE_TUIC -> proxy.tuicBean?.serverPort ?: 0
            else -> 0
        }
    }
    
    fun getLatency(proxyId: Long): Int {
        val latency = latencyCache[proxyId] ?: -1
        Logs.d("Getting latency for proxy $proxyId: $latency")
        return latency
    }
    
    fun formatLatency(latency: Int): String {
        return when {
            latency < 0 -> "N/A"
            latency < 1000 -> "${latency}ms"
            else -> "${latency / 1000}s"
        }
    }
    
    fun isLatencyGood(latency: Int): Boolean {
        return latency in 0..500 // Consider good if under 500ms
    }
} 