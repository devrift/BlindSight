package com.example.service

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DirectAudioRecorder records raw PCM audio directly from the device microphone
 * with multi-tier Active / Environmental Noise Cancellation (ANC / ENC) and
 * adaptive Voice Activity Detection (VAD).
 *
 * Audio Processing Pipeline:
 * 1. Hardware Noise Cancellation: Uses VOICE_COMMUNICATION multi-mic beamforming
 *    and attaches Android's hardware NoiseSuppressor, AcousticEchoCanceler, and AutomaticGainControl.
 * 2. 2nd-Order Biquad High-Pass Filter (150 Hz): Eliminates low-frequency traffic rumble,
 *    sub-bass engine drone, wind pops, and HVAC hum.
 * 3. 2nd-Order Biquad Low-Pass Filter (3600 Hz): Eliminates high-frequency party screeches,
 *    glass clinks, tire squeals, and background hiss.
 * 4. Adaptive Spectral Noise Gate (ENC): Tracks ambient noise floor in real time.
 *    Attenuates non-speech ambient noise down to total silence (-36 dB), smoothly opening
 *    with voice clarity enhancement when the user speaks.
 * 5. Adaptive VAD: Auto-stops recording as soon as trailing silence is detected (~600ms).
 */
class DirectAudioRecorder(
    private val onSilenceDetected: (() -> Unit)? = null,
    private val onRmsChanged: ((Float) -> Unit)? = null
) {
    private val tag = "DirectAudioRecorder"
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var gainControl: AutomaticGainControl? = null

    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val pcmStream = ByteArrayOutputStream()
    private val mainHandler = Handler(Looper.getMainLooper())

    val recording: Boolean
        get() = isRecording

    // 2nd Order Biquad Filter (Direct Form II Transposed)
    private class BiquadFilter {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        fun initHighPass(sampleRate: Double, cutoff: Double, q: Double = 0.7071) {
            val w0 = 2.0 * PI * cutoff / sampleRate
            val cosw0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b0_raw = (1.0 + cosw0) / 2.0
            val b1_raw = -(1.0 + cosw0)
            val b2_raw = (1.0 + cosw0) / 2.0
            val a0_raw = 1.0 + alpha
            val a1_raw = -2.0 * cosw0
            val a2_raw = 1.0 - alpha

            b0 = b0_raw / a0_raw
            b1 = b1_raw / a0_raw
            b2 = b2_raw / a0_raw
            a1 = a1_raw / a0_raw
            a2 = a2_raw / a0_raw
            reset()
        }

        fun initLowPass(sampleRate: Double, cutoff: Double, q: Double = 0.7071) {
            val w0 = 2.0 * PI * cutoff / sampleRate
            val cosw0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b0_raw = (1.0 - cosw0) / 2.0
            val b1_raw = 1.0 - cosw0
            val b2_raw = (1.0 - cosw0) / 2.0
            val a0_raw = 1.0 + alpha
            val a1_raw = -2.0 * cosw0
            val a2_raw = 1.0 - alpha

            b0 = b0_raw / a0_raw
            b1 = b1_raw / a0_raw
            b2 = b2_raw / a0_raw
            a1 = a1_raw / a0_raw
            a2 = a2_raw / a0_raw
            reset()
        }

        fun process(input: Double): Double {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) return true

        synchronized(pcmStream) {
            pcmStream.reset()
        }

        try {
            // Prefer VOICE_COMMUNICATION for hardware multi-mic beamforming & acoustic noise cancellation
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )

            for (src in sources) {
                try {
                    val record = AudioRecord(
                        src,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecord = record
                        Log.i(tag, "AudioRecord initialized using AudioSource $src")
                        break
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to init AudioRecord on source $src: ${e.message}")
                }
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "All AudioRecord initialization attempts failed")
                return false
            }

            // Enable hardware pre-processing effects if available on device
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.i(tag, "Hardware NoiseSuppressor enabled: ${noiseSuppressor?.enabled}")
                    } catch (e: Exception) {
                        Log.w(tag, "Hardware NoiseSuppressor error: ${e.message}")
                    }
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.i(tag, "Hardware AcousticEchoCanceler enabled: ${echoCanceler?.enabled}")
                    } catch (e: Exception) {
                        Log.w(tag, "Hardware AcousticEchoCanceler error: ${e.message}")
                    }
                }
                if (AutomaticGainControl.isAvailable()) {
                    try {
                        gainControl = AutomaticGainControl.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.i(tag, "Hardware AutomaticGainControl enabled: ${gainControl?.enabled}")
                    } catch (e: Exception) {
                        Log.w(tag, "Hardware AutomaticGainControl error: ${e.message}")
                    }
                }
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = thread(name = "BlindSightAudioRecord", priority = Thread.MAX_PRIORITY) {
                val shortBuffer = ShortArray(bufferSize / 2)
                val frameDurationMs = (shortBuffer.size * 1000f) / sampleRate // ~128ms per frame

                // Software DSP Filters:
                // 1. High-Pass at 150 Hz: Mutes sub-bass rumble, bus/engine idle, wind pops
                val hpFilter = BiquadFilter().apply { initHighPass(sampleRate.toDouble(), 150.0) }
                // 2. Low-Pass at 3600 Hz: Mutes party screech, glass clinking, tire hiss
                val lpFilter = BiquadFilter().apply { initLowPass(sampleRate.toDouble(), 3600.0) }

                // Adaptive Noise Gate & Floor Tracker
                var ambientNoiseFloor = 180f
                var speechFrames = 0
                var silenceFrames = 0
                var totalFrames = 0
                var hasConfirmedSpeech = false
                var gateOpenness = 0.05f // 0.05 = -26dB attenuation (near silence)

                val targetSilenceFrames = (650f / frameDurationMs).toInt().coerceIn(4, 6)
                val maxTotalFrames = (5000f / frameDurationMs).toInt().coerceAtLeast(35)
                val maxNoSpeechFrames = (4000f / frameDurationMs).toInt().coerceAtLeast(28)

                while (isRecording) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (read > 0) {
                        totalFrames++

                        // Step 1: Calculate raw RMS for noise floor tracking & VAD
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = shortBuffer[i].toDouble()
                            sumSq += s * s
                        }
                        val rawRms = sqrt(sumSq / read).toFloat()
                        mainHandler.post { onRmsChanged?.invoke(rawRms) }

                        // Step 2: Track ambient noise floor adaptively
                        if (totalFrames <= 3) {
                            ambientNoiseFloor = max(rawRms, 120f)
                        } else if (!hasConfirmedSpeech) {
                            // Smoothly track background noise floor before speech
                            ambientNoiseFloor = ambientNoiseFloor * 0.85f + rawRms * 0.15f
                        }

                        val speechThreshold = max(ambientNoiseFloor * 1.45f + 80f, 240f)
                        val silenceThreshold = max(ambientNoiseFloor * 1.15f + 40f, 210f)

                        val isSpeechNow = rawRms >= speechThreshold

                        if (isSpeechNow) {
                            speechFrames++
                            silenceFrames = 0
                            if (speechFrames >= 2) {
                                hasConfirmedSpeech = true
                            }
                            // Smoothly open gate (fast attack)
                            gateOpenness = min(1.0f, gateOpenness + 0.35f)
                        } else if (rawRms <= silenceThreshold) {
                            if (hasConfirmedSpeech) {
                                silenceFrames++
                                if (silenceFrames >= targetSilenceFrames) {
                                    Log.d(tag, "Trailing silence reached after speech ($silenceFrames frames ~650ms). Auto-finishing capture.")
                                    mainHandler.post { onSilenceDetected?.invoke() }
                                    break
                                }
                            }
                            // Smoothly close gate (soft release to prevent audio clipping)
                            gateOpenness = max(0.04f, gateOpenness - 0.2f)
                        } else {
                            if (hasConfirmedSpeech) {
                                silenceFrames++
                                if (silenceFrames >= targetSilenceFrames + 2) {
                                    Log.d(tag, "Post-speech breath pause reached. Auto-finishing capture.")
                                    mainHandler.post { onSilenceDetected?.invoke() }
                                    break
                                }
                            }
                            gateOpenness = max(0.15f, gateOpenness - 0.15f)
                        }

                        // Step 3: Apply Bandpass Filtering + Noise Gate + Voice Enhancement to PCM samples
                        val byteBuf = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                        val voiceClarityMultiplier = if (gateOpenness > 0.6f) 1.22 else 1.0

                        for (i in 0 until read) {
                            val rawSample = shortBuffer[i].toDouble()
                            // Bandpass filter (High-Pass 150Hz -> Low-Pass 3600Hz)
                            val filtered = lpFilter.process(hpFilter.process(rawSample))
                            // Spectral Noise Gate + Voice Clarity Boost
                            val gated = filtered * gateOpenness * voiceClarityMultiplier
                            // Clamp to 16-bit range
                            val clamped = gated.coerceIn(-32767.0, 32767.0).toInt().toShort()
                            byteBuf.putShort(clamped)
                        }

                        val bytes = byteBuf.array()
                        synchronized(pcmStream) {
                            pcmStream.write(bytes, 0, bytes.size)
                        }

                        // Max timeout safety (5s)
                        if (totalFrames >= maxTotalFrames) {
                            Log.d(tag, "Max capture duration reached (5s). Auto-finishing capture.")
                            mainHandler.post { onSilenceDetected?.invoke() }
                            break
                        }

                        // No-speech timeout (4s)
                        if (!hasConfirmedSpeech && totalFrames >= maxNoSpeechFrames) {
                            Log.d(tag, "No speech detected within 4s. Auto-finishing capture.")
                            mainHandler.post { onSilenceDetected?.invoke() }
                            break
                        }
                    } else if (read < 0) {
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error starting AudioRecord: ${e.message}", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        try {
            gainControl?.release()
        } catch (_: Exception) {}

        audioRecord = null
        noiseSuppressor = null
        echoCanceler = null
        gainControl = null
        recordingThread = null

        val pcmBytes: ByteArray
        synchronized(pcmStream) {
            pcmBytes = pcmStream.toByteArray()
            pcmStream.reset()
        }

        if (pcmBytes.isEmpty()) return ByteArray(0)
        return convertPcmToWav(pcmBytes, sampleRate, 1, 16)
    }

    private fun convertPcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Audio format 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavData = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, wavData, 0, header.size)
        System.arraycopy(pcmData, 0, wavData, header.size, pcmData.size)
        return wavData
    }
}
