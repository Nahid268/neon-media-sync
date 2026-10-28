package com.example.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object AudioRecordHelper {

    suspend fun recordAmbientAudio(context: Context, durationSeconds: Int): Result<File> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.failure(Exception("Microphone permission (RECORD_AUDIO) is not granted in phone settings."))
        }

        val clampedSeconds = durationSeconds.coerceIn(3, 60)
        val outFile = File(context.cacheDir, "ambient_${System.currentTimeMillis()}.m4a")

        var recorder: MediaRecorder? = null
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }

            // Record for requested duration
            delay(clampedSeconds * 1000L)

            try {
                recorder.stop()
            } catch (_: Exception) {}
            recorder.release()
            recorder = null

            if (outFile.exists() && outFile.length() > 0) {
                Result.success(outFile)
            } else {
                Result.failure(Exception("Recorded audio file was empty or failed to save."))
            }
        } catch (e: Exception) {
            try {
                recorder?.release()
            } catch (_: Exception) {}
            if (outFile.exists()) outFile.delete()
            Result.failure(e)
        }
    }
}
