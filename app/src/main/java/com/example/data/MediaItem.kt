package com.example.data

import android.net.Uri

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    SCREENSHOT
}

enum class UploadStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    val isVideo: Boolean,
    val mediaType: MediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
    val duration: Long = 0L,
    val status: UploadStatus = UploadStatus.PENDING,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val uploadedAt: Long? = null
)

enum class LogType {
    INFO,
    SUCCESS,
    ERROR,
    WARNING,
    TELEGRAM
}

data class LogEntry(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: LogType = LogType.INFO
)

data class SyncStats(
    val totalCount: Int = 0,
    val uploadedCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val totalBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val isSyncing: Boolean = false,
    val isPaused: Boolean = false,
    val isScanning: Boolean = false,
    val currentFileName: String? = null,
    val currentProgress: Float = 0f,
    val statusMessage: String = "Ready to scan & sync"
)

data class TelegramConfig(
    val botToken: String = "8620579280:AAF9zUBAORawb9oQvy5R2WEb4DFQ3xJRwKI",
    val chatId: String = "-1004457596144"
)
