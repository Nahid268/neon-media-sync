package com.example.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.data.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaScanner(private val context: Context) {

    suspend fun scanAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()

        // 1. Scan Images (including Screenshot detection)
        scanImages(mediaList)

        // 2. Scan Videos
        scanVideos(mediaList)

        // 3. Scan Audio & Voice Notes
        scanAudio(mediaList)

        // 4. Scan Documents & Downloaded Files
        scanDocuments(mediaList)

        // Sort combined media items by date added descending
        mediaList.sortedByDescending { it.dateAdded }
    }

    private fun scanImages(mediaList: MutableList<MediaItem>) {
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED
        )

        val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                imageSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "IMG_$id.jpg"
                    val size = cursor.getLong(sizeColumn)
                    val mime = cursor.getString(mimeColumn) ?: "image/jpeg"
                    val dateAdded = cursor.getLong(dateColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val isScreenshot = name.lowercase(Locale.ROOT).contains("screenshot") ||
                            name.lowercase(Locale.ROOT).contains("screen_shot")

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            size = size,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            isVideo = false,
                            mediaType = if (isScreenshot) MediaType.SCREENSHOT else MediaType.PHOTO,
                            status = UploadStatus.PENDING
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun scanVideos(mediaList: MutableList<MediaItem>) {
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION
        )

        val videoSortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                videoSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "VID_$id.mp4"
                    val size = cursor.getLong(sizeColumn)
                    val mime = cursor.getString(mimeColumn) ?: "video/mp4"
                    val dateAdded = cursor.getLong(dateColumn)
                    val duration = cursor.getLong(durationColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            size = size,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            isVideo = true,
                            mediaType = MediaType.VIDEO,
                            duration = duration,
                            status = UploadStatus.PENDING
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun scanAudio(mediaList: MutableList<MediaItem>) {
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION
        )

        val audioSortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                audioSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "AUD_$id.mp3"
                    val size = cursor.getLong(sizeColumn)
                    val mime = cursor.getString(mimeColumn) ?: "audio/mpeg"
                    val dateAdded = cursor.getLong(dateColumn)
                    val duration = cursor.getLong(durationColumn)

                    // Skip zero byte / corrupted audio
                    if (size <= 0) continue

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            size = size,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            isVideo = false,
                            mediaType = MediaType.AUDIO,
                            duration = duration,
                            status = UploadStatus.PENDING
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun scanDocuments(mediaList: MutableList<MediaItem>) {
        val fileProjection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED
        )

        val docMimeSelection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"

        val docMimeArgs = arrayOf(
            "%pdf%",
            "%word%",
            "%sheet%",
            "%presentation%",
            "text/plain",
            "application/vnd.android.package-archive",
            "%.pdf",
            "%.docx"
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        try {
            val externalFilesUri = MediaStore.Files.getContentUri("external")
            context.contentResolver.query(
                externalFilesUri,
                fileProjection,
                docMimeSelection,
                docMimeArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "DOC_$id.pdf"
                    val size = cursor.getLong(sizeColumn)
                    val mime = cursor.getString(mimeColumn) ?: "application/pdf"
                    val dateAdded = cursor.getLong(dateColumn)

                    if (size <= 0) continue

                    val contentUri: Uri = ContentUris.withAppendedId(externalFilesUri, id)

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            size = size,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            isVideo = false,
                            mediaType = MediaType.DOCUMENT,
                            status = UploadStatus.PENDING
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }
}
