package com.example.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object RemoteCameraHelper {

    @SuppressLint("MissingPermission")
    suspend fun capturePhoto(context: Context, isFront: Boolean): Result<File> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.failure(Exception("Camera permission (CAMERA) is not granted in phone settings."))
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@withContext Result.failure(Exception("Camera service not available on device"))

        val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

        val cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == targetFacing
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Failed to access camera list: ${e.message}"))
        } ?: return@withContext Result.failure(Exception("No camera lens found for ${if (isFront) "Front" else "Back"}"))

        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Failed to get camera characteristics: ${e.message}"))
        }

        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = streamMap?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

        // Pick a sensible resolution (around 1280x720, 640x480, or 1920x1080) for rapid upload
        val targetSize = sizes.firstOrNull { it.width in 640..1280 && it.height in 480..720 }
            ?: sizes.firstOrNull { it.width in 1200..1920 && it.height in 700..1200 }
            ?: sizes.firstOrNull { it.width <= 1920 }
            ?: sizes.firstOrNull()
            ?: return@withContext Result.failure(Exception("Could not find suitable camera resolution"))

        val result = withTimeoutOrNull(12000L) {
            takePictureInternal(context, cameraManager, characteristics, cameraId, targetSize.width, targetSize.height, isFront)
        }

        result ?: Result.failure(Exception("Camera capture timed out (camera might be in use by another app or hardware error)."))
    }

    @SuppressLint("MissingPermission")
    private suspend fun takePictureInternal(
        context: Context,
        cameraManager: CameraManager,
        characteristics: CameraCharacteristics,
        cameraId: String,
        width: Int,
        height: Int,
        isFront: Boolean
    ): Result<File> = suspendCancellableCoroutine { continuation ->

        val handlerThread = HandlerThread("RemoteCameraThread_${System.currentTimeMillis()}").apply { start() }
        val handler = Handler(handlerThread.looper)

        val imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2)
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        val isCompleted = AtomicBoolean(false)

        fun cleanup() {
            try {
                captureSession?.close()
            } catch (_: Throwable) {}
            captureSession = null

            try {
                cameraDevice?.close()
            } catch (_: Throwable) {}
            cameraDevice = null

            try {
                imageReader.close()
            } catch (_: Throwable) {}

            try {
                handlerThread.quitSafely()
            } catch (_: Throwable) {}
        }

        fun completeWith(result: Result<File>) {
            if (isCompleted.compareAndSet(false, true)) {
                cleanup()
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

        imageReader.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    val prefix = if (isFront) "front_cam" else "back_cam"
                    val outFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(outFile).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }

                    completeWith(Result.success(outFile))
                }
            } catch (e: Exception) {
                completeWith(Result.failure(e))
            }
        }, handler)

        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                try {
                    val surfaces = listOf(imageReader.surface)
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                    addTarget(imageReader.surface)

                                    val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                                    if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
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

                                session.capture(
                                    captureBuilder.build(),
                                    object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureFailed(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            failure: CaptureFailure
                                        ) {
                                            completeWith(Result.failure(Exception("Camera capture request failed (reason code: ${failure.reason})")))
                                        }

                                        override fun onCaptureCompleted(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            result: TotalCaptureResult
                                        ) {
                                            // ImageReader listener will handle the image buffer
                                        }
                                    },
                                    handler
                                )
                            } catch (e: Exception) {
                                completeWith(Result.failure(e))
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            completeWith(Result.failure(Exception("Failed to configure camera capture session")))
                        }
                    }, handler)
                } catch (e: Exception) {
                    completeWith(Result.failure(e))
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraDevice = camera
                completeWith(Result.failure(Exception("Camera disconnected")))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraDevice = camera
                val reason = when (error) {
                    ERROR_CAMERA_IN_USE -> "Camera already in use by another app"
                    ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                    ERROR_CAMERA_DISABLED -> "Camera disabled by device policy"
                    ERROR_CAMERA_DEVICE -> "Camera device hardware error"
                    ERROR_CAMERA_SERVICE -> "Camera system service error"
                    else -> "Camera error code: $error"
                }
                completeWith(Result.failure(Exception(reason)))
            }
        }

        continuation.invokeOnCancellation {
            cleanup()
        }

        try {
            cameraManager.openCamera(cameraId, stateCallback, handler)
        } catch (e: Exception) {
            completeWith(Result.failure(e))
        }
    }
}
