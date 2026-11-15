package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ExifInterface
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * Captures live photos directly using the device's back camera
 * with automatic orientation correction (eliminating the 90-degree sideways camera issue).
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
     * Captures a single live upright JPEG photo from the back camera within 2.5 seconds.
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

                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

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
                            val rawBytes = ByteArray(buffer.remaining())
                            buffer.get(rawBytes)
                            image.close()

                            // Fix 90-degree sideways rotation so image is ALWAYS perfectly upright!
                            val uprightBytes = ensureUprightJpeg(rawBytes, sensorOrientation)

                            cleanup()
                            if (continuation.isActive) {
                                continuation.resume(uprightBytes)
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
                                                set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
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

    /**
     * Inspects EXIF and hardware sensor orientation, rotating the JPEG bytes
     * to guaranteed 0-degree upright portrait orientation.
     */
    private fun ensureUprightJpeg(jpegBytes: ByteArray, defaultSensorOrientation: Int): ByteArray {
        try {
            var rotationDegrees = 0
            try {
                val exif = ExifInterface(ByteArrayInputStream(jpegBytes))
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
                rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (_: Exception) {}

            // If EXIF metadata was missing or unrotated while sensor is rotated:
            if (rotationDegrees == 0 && defaultSensorOrientation != 0) {
                rotationDegrees = defaultSensorOrientation
            }

            if (rotationDegrees == 0) return jpegBytes

            val originalBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return jpegBytes
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix, true
            )

            val outputStream = ByteArrayOutputStream()
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)

            originalBitmap.recycle()
            rotatedBitmap.recycle()

            return outputStream.toByteArray()
        } catch (e: Exception) {
            Log.w(tag, "ensureUprightJpeg rotation error: ${e.message}")
            return jpegBytes
        }
    }
}
