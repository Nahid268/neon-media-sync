package com.example.scanner

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object RemoteScreenshotHelper {

    @Volatile
    var foregroundActivityRootView: View? = null

    suspend fun captureScreenshot(context: Context): Result<File> = withContext(Dispatchers.IO) {
        // 1. Try native AccessibilityService screenshot (Android 11+ background, silent, full screen)
        if (AppAccessibilityService.isRunning() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val accResult = AppAccessibilityService.takeScreenshotFile(context.cacheDir)
            if (accResult.isSuccess) {
                return@withContext accResult
            }
        }

        // 2. Try shell screencap (works on rooted phones or emulator/userdebug)
        try {
            val shellFile = File(context.cacheDir, "shell_screen_${System.currentTimeMillis()}.png")
            val process = Runtime.getRuntime().exec(arrayOf("screencap", "-p", shellFile.absolutePath))
            process.waitFor()
            if (shellFile.exists() && shellFile.length() > 5000) {
                return@withContext Result.success(shellFile)
            }
        } catch (_: Exception) {}

        // 3. Try foreground activity view if app is active on screen
        val view = foregroundActivityRootView
        if (view != null && view.isAttachedToWindow && view.width > 0 && view.height > 0) {
            try {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                view.draw(canvas)
                val file = File(context.cacheDir, "app_screen_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                }
                return@withContext Result.success(file)
            } catch (_: Exception) {}
        }

        Result.failure(
            Exception(
                "Accessibility Service is not enabled.\n" +
                "To capture the screen remotely in the background anytime without prompting, " +
                "please enable 'Nova Protection / Scanner' under Phone Settings ➔ Accessibility."
            )
        )
    }
}
