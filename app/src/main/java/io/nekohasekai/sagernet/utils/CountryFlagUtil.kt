package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object CountryFlagUtil {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Convert country code to flag emoji
     */
    fun getCountryFlag(countryCode: String): String {
        if (countryCode.isBlank()) return ""
        
        val code = countryCode.uppercase()
        Logs.d("getCountryFlag called with: '$countryCode' -> '$code'")
        
        if (code.length != 2) {
            Logs.w("Invalid country code length: '$code' (length: ${code.length})")
            return ""
        }
        
        // Convert country code to flag emoji
        // Each letter is converted to regional indicator symbol
        val first = code[0].code - 65 + 0x1F1E6
        val second = code[1].code - 65 + 0x1F1E6
        Logs.d("Calculated Unicode codes: first=${code[0]}(${code[0].code}) -> $first, second=${code[1]}(${code[1].code}) -> $second")
        
        val flag = String(Character.toChars(first)) + String(Character.toChars(second))
        Logs.d("Generated flag: '$flag' (length: ${flag.length})")
        
        return flag
    }

    /**
     * Get country code from IP address using multiple geolocation APIs
     */
    suspend fun getCountryFromIP(ipAddress: String): String {
        return withContext(Dispatchers.IO) {
            // Try multiple APIs in order of preference
            try {
                // Primary API: ip-api.com
                val result1 = getCountryFromIPAPI(ipAddress)
                if (result1.isNotBlank()) {
                    Logs.d("Country detected for $ipAddress: $result1")
                    return@withContext result1
                }
            } catch (e: Exception) {
                Logs.w("Primary API failed for $ipAddress: ${e.message}")
            }
            
            try {
                // Fallback API: ipinfo.io
                val result2 = getCountryFromIPInfo(ipAddress)
                if (result2.isNotBlank()) {
                    Logs.d("Country detected for $ipAddress: $result2")
                    return@withContext result2
                }
            } catch (e: Exception) {
                Logs.w("Fallback API failed for $ipAddress: ${e.message}")
            }
            
            try {
                // Secondary fallback: ipgeolocation.io
                val result3 = getCountryFromIPGeolocation(ipAddress)
                if (result3.isNotBlank()) {
                    Logs.d("Country detected for $ipAddress: $result3")
                    return@withContext result3
                }
            } catch (e: Exception) {
                Logs.w("Secondary API failed for $ipAddress: ${e.message}")
            }
            
            Logs.w("All APIs failed for $ipAddress")
            ""
        }
    }

    /**
     * Primary API: ip-api.com (free, reliable)
     */
    private fun getCountryFromIPAPI(ipAddress: String): String {
        val url = "http://ip-api.com/json/$ipAddress?fields=countryCode"
        val request = Request.Builder()
            .url(url)
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "")
            return json.optString("countryCode", "")
        }
        return ""
    }

    /**
     * Fallback API: ipinfo.io (free tier available)
     */
    private fun getCountryFromIPInfo(ipAddress: String): String {
        val url = "https://ipinfo.io/$ipAddress/json"
        val request = Request.Builder()
            .url(url)
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "")
            return json.optString("country", "")
        }
        return ""
    }

    /**
     * Secondary fallback: ipgeolocation.io (free tier)
     */
    private fun getCountryFromIPGeolocation(ipAddress: String): String {
        val url = "https://api.ipgeolocation.io/ipgeo?apiKey=free&ip=$ipAddress"
        val request = Request.Builder()
            .url(url)
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "")
            return json.optString("country_code2", "")
        }
        return ""
    }

    /**
     * Resolve IP address from hostname
     */
    suspend fun resolveIP(hostname: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = InetAddress.getAllByName(hostname)
                addresses.firstOrNull()?.hostAddress ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    /**
     * Get country flag for a SOCKS proxy by resolving its IP and fetching country info
     */
    suspend fun getCountryFlagForProxy(serverAddress: String): String {
        if (serverAddress.isBlank()) return ""
        
        val ip = if (serverAddress.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            serverAddress
        } else {
            resolveIP(serverAddress)
        }
        
        if (ip.isBlank()) {
            Logs.w("Could not resolve IP for $serverAddress")
            return ""
        }
        
        Logs.d("Resolved $serverAddress to IP: $ip")
        val countryCode = getCountryFromIP(ip)
        Logs.d("Country code from API: '$countryCode'")
        
        val flag = getCountryFlag(countryCode)
        Logs.d("Generated flag from country code '$countryCode': '$flag' (length: ${flag.length})")
        
        return flag
    }
} 