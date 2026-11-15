package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * Captures live photos directly using the device's back camera
 * when hardware glasses are disconnected or unavailable.
 */
class PhoneCameraHelper(private val context: Context) {

    private val tag = "PhoneCameraHelper"

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Captures a single live JPEG photo from the back camera within 2.5 seconds.
     */
    suspend fun captureLiveFrame(): ByteArray? = withTimeoutOrNull(2500L) {
        if (!hasCameraPermission()) {
            Log.w(tag, "Camera permission not granted")
            return@withTimeoutOrNull null
        }

        suspendCancellableCoroutine { continuation ->
            val cameraThread = HandlerThread("PhoneCameraCapture").apply { start() }
            val cameraHandler = Handler(cameraThread.looper)

            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                if (cameraManager == null) {
                    cameraThread.quitSafely()
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: cameraManager.cameraIdList.firstOrNull()

                if (cameraId == null) {
                    cameraThread.quitSafely()
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
                var cameraDevice: CameraDevice? = null
                var captureSession: CameraCaptureSession? = null

                fun cleanup() {
                    try { captureSession?.close() } catch (_: Exception) {}
                    try { cameraDevice?.close() } catch (_: Exception) {}
                    try { imageReader.close() } catch (_: Exception) {}
                    try { cameraThread.quitSafely() } catch (_: Exception) {}
                }

                imageReader.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val planes = image.planes
                            val buffer: ByteBuffer = planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()

                            cleanup()
                            if (continuation.isActive) {
                                continuation.resume(bytes)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Image processing error: ${e.message}")
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }, cameraHandler)

                @SuppressLint("MissingPermission")
                val stateCallback = object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        try {
                            camera.createCaptureSession(
                                listOf(imageReader.surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        captureSession = session
                                        try {
                                            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                addTarget(imageReader.surface)
                                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                            }
                                            session.capture(captureBuilder.build(), null, cameraHandler)
                                        } catch (e: Exception) {
                                            Log.w(tag, "Capture request failed: ${e.message}")
                                            cleanup()
                                            if (continuation.isActive) continuation.resume(null)
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        cleanup()
                                        if (continuation.isActive) continuation.resume(null)
                                    }
                                },
                                cameraHandler
                            )
                        } catch (e: Exception) {
                            Log.w(tag, "CreateCaptureSession error: ${e.message}")
                            cleanup()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        cleanup()
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        cleanup()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                cameraManager.openCamera(cameraId, stateCallback, cameraHandler)

                continuation.invokeOnCancellation {
                    cleanup()
                }

            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize phone camera capture: ${e.message}", e)
                cameraThread.quitSafely()
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}
