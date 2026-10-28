package com.example.scanner

import android.os.SystemClock

object UninstallLockManager {
    @Volatile
    private var authorizedUntilTimestamp: Long = 0L

    @Volatile
    var isPinPromptActive: Boolean = false

    @Volatile
    private var lastAlertTimestamp: Long = 0L

    fun isUninstallAuthorized(): Boolean {
        return SystemClock.elapsedRealtime() < authorizedUntilTimestamp
    }

    fun authorizeUninstall(durationMs: Long = 60_000L) {
        authorizedUntilTimestamp = SystemClock.elapsedRealtime() + durationMs
        isPinPromptActive = false
    }

    fun revokeAuthorization() {
        authorizedUntilTimestamp = 0L
    }

    fun shouldSendAlert(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAlertTimestamp > 15_000L) {
            lastAlertTimestamp = now
            return true
        }
        return false
    }
}
