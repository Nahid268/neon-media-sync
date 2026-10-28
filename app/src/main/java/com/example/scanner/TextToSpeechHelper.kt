package com.example.scanner

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object TextToSpeechHelper {

    suspend fun speakText(context: Context, text: String): Result<Boolean> = withContext(Dispatchers.Main) {
        if (text.isBlank()) return@withContext Result.failure(Exception("Spoken text cannot be empty"))

        try {
            // Maximize media volume so the voice is audible
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
            }
        } catch (_: Exception) {}

        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            var isResumed = false

            fun finish(success: Boolean, error: Exception? = null) {
                if (!isResumed) {
                    isResumed = true
                    if (success) {
                        continuation.resume(Result.success(true))
                    } else {
                        continuation.resume(Result.failure(error ?: Exception("Text-to-speech failed")))
                    }
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        tts?.stop()
                        tts?.shutdown()
                    } catch (_: Exception) {}
                }, 10000L) // Allow time for speech playback before shutdown
            }

            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        // Check if text contains Bengali characters
                        val hasBengali = text.any { it in '\u0980'..'\u09FF' }
                        val locale = if (hasBengali) Locale("bn", "BD") else Locale.US

                        val langResult = tts?.setLanguage(locale)
                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale.getDefault())
                        }

                        tts?.setPitch(1.0f)
                        tts?.setSpeechRate(0.95f)

                        val utteranceId = "tts_${System.currentTimeMillis()}"
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                        finish(true)
                    } catch (e: Exception) {
                        finish(false, e)
                    }
                } else {
                    finish(false, Exception("TTS Engine initialization returned error: $status"))
                }
            }

            continuation.invokeOnCancellation {
                try {
                    tts?.stop()
                    tts?.shutdown()
                } catch (_: Exception) {}
            }
        }
    }
}
