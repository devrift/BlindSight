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

    var isFrontCamera: Boolean = false
        private set

    fun switchCamera(): Boolean {
        isFrontCamera = !isFrontCamera
        Log.d(tag, "Switched camera. isFrontCamera=$isFrontCamera")
        return isFrontCamera
    }

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

                val targetFacing = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    characteristics.get(CameraCharacteristics.LENS_FACING) == targetFacing
                } ?: cameraManager.cameraIdList.firstOrNull()

                if (cameraId == null) {
                    cameraThread.quitSafely()
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

                // Dynamically resolve high-definition camera capture resolution (1080p / 720p)
                val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val jpegSizes = configMap?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
                val targetSize = jpegSizes
                    .filter { it.width <= 1920 && it.height <= 1080 }
                    .maxByOrNull { it.width * it.height }
                    ?: jpegSizes.firstOrNull()
                    ?: android.util.Size(1280, 720)

                Log.d(tag, "Configuring HD camera capture at ${targetSize.width}x${targetSize.height}")
                val imageReader = ImageReader.newInstance(targetSize.width, targetSize.height, ImageFormat.JPEG, 2)
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
                    @Suppress("DEPRECATION")
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
     * to guaranteed 0-degree upright portrait orientation and downscaling to 640x480 (0.65 quality).
     */
    private fun ensureUprightJpeg(jpegBytes: ByteArray, defaultSensorOrientation: Int): ByteArray {
        try {
            val originalBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return jpegBytes
            
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

            // If the camera delivers landscape (width > height) for a handheld portrait shot, rotate upright
            if (originalBitmap.width > originalBitmap.height && rotationDegrees == 0) {
                rotationDegrees = if (defaultSensorOrientation != 0) defaultSensorOrientation else 90
            }

            val matrix = Matrix()
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }

            val rotatedBitmap = if (rotationDegrees != 0) {
                Bitmap.createBitmap(
                    originalBitmap, 0, 0,
                    originalBitmap.width, originalBitmap.height,
                    matrix, true
                )
            } else {
                originalBitmap
            }

            // High-definition bounds (1280px max bound) with 85% JPEG quality for crisp visual perception
            val maxBound = 1280
            val scaledBitmap = if (rotatedBitmap.width > maxBound || rotatedBitmap.height > maxBound) {
                val scale = minOf(maxBound.toFloat() / rotatedBitmap.width, maxBound.toFloat() / rotatedBitmap.height)
                Bitmap.createScaledBitmap(
                    rotatedBitmap,
                    (rotatedBitmap.width * scale).toInt(),
                    (rotatedBitmap.height * scale).toInt(),
                    true
                )
            } else {
                rotatedBitmap
            }

            val outputStream = ByteArrayOutputStream()
            // Crisp 85% quality JPEG compression
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

            if (scaledBitmap != originalBitmap && scaledBitmap != rotatedBitmap) scaledBitmap.recycle()
            if (rotatedBitmap != originalBitmap) rotatedBitmap.recycle()
            originalBitmap.recycle()

            return outputStream.toByteArray()
        } catch (e: Exception) {
            Log.w(tag, "ensureUprightJpeg rotation error: ${e.message}")
            return jpegBytes
        }
    }

    /**
     * Toggles phone flashlight on or off
     */
    fun setTorch(enabled: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return false

            cameraManager.setTorchMode(cameraId, enabled)
            Log.d(tag, "Torch mode set to $enabled on camera $cameraId")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle torch mode: ${e.message}")
            false
        }
    }
}
