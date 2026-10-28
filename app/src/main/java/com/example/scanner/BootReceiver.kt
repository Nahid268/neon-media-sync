package com.example.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESTART_SERVICE = "com.example.scanner.ACTION_RESTART_SERVICE"
        const val ACTION_WATCHDOG_CHECK = "com.example.scanner.ACTION_WATCHDOG_CHECK"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == ACTION_RESTART_SERVICE ||
            action == ACTION_WATCHDOG_CHECK ||
            action == "android.net.conn.CONNECTIVITY_CHANGE" ||
            action == "android.net.wifi.WIFI_STATE_CHANGED" ||
            action == "android.net.wifi.STATE_CHANGE"
        ) {
            // Automatically launch and keep alive background protection and command engine
            MediaSyncBackgroundService.start(context)

            // Re-arm periodic watchdog alarm so it continues indefinitely through screen OFF and Doze mode
            MediaSyncBackgroundService.schedulePeriodicWatchdog(context)
        }
    }
}

