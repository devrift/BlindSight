package com.example.service

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * DirectAudioRecorder records raw PCM audio directly from the device microphone
 * using Android's native AudioRecord API (completely independent of Google SpeechRecognizer
 * or Google Voice Assistant, with zero mic flapping or oscillation).
 *
 * It provides:
 * 1. Push-to-talk / tap-to-talk recording with instant hardware mic acquisition
 * 2. Adaptive on-device Voice Activity Detection (VAD) that automatically calibrates
 *    to ambient room noise and triggers auto-answer as soon as the user completes
 *    their sentence (~1.1s trailing silence).
 * 3. Conversion of captured audio into standard 16kHz 16-bit mono WAV format for Groq Whisper
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
    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val pcmStream = ByteArrayOutputStream()
    private val mainHandler = Handler(Looper.getMainLooper())

    val recording: Boolean
        get() = isRecording

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) return true

        synchronized(pcmStream) {
            pcmStream.reset()
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = thread(name = "BlindSightAudioRecord", priority = Thread.MAX_PRIORITY) {
                val shortBuffer = ShortArray(bufferSize / 2)
                val frameDurationMs = (shortBuffer.size * 1000f) / sampleRate // ~128ms per frame

                // Adaptive Noise & VAD Calibration
                var ambientRms = 180f
                var calibrationFrames = 0
                val maxCalibrationFrames = 3 // First ~380ms adapts to room baseline

                var speechFrames = 0
                var silenceFrames = 0
                var totalFrames = 0
                var hasConfirmedSpeech = false

                // 1.1s trailing silence (approx 8-9 frames)
                val targetSilenceFrames = (1100f / frameDurationMs).toInt().coerceAtLeast(8)
                // 9s max duration safety timeout
                val maxTotalFrames = (9000f / frameDurationMs).toInt().coerceAtLeast(45)

                while (isRecording) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (read > 0) {
                        totalFrames++
                        val byteBuf = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val sample = shortBuffer[i]
                            byteBuf.putShort(sample)
                            sumSq += sample.toDouble() * sample.toDouble()
                        }
                        val bytes = byteBuf.array()

                        synchronized(pcmStream) {
                            pcmStream.write(bytes, 0, bytes.size)
                        }

                        val rms = sqrt(sumSq / read).toFloat()
                        mainHandler.post { onRmsChanged?.invoke(rms) }

                        // 1. Initial Calibration Phase
                        if (calibrationFrames < maxCalibrationFrames) {
                            ambientRms = if (calibrationFrames == 0) rms else (ambientRms * 0.5f + rms * 0.5f)
                            calibrationFrames++
                        } else {
                            // 2. Dynamic thresholds based on measured ambient noise
                            val speechThreshold = maxOf(ambientRms * 2.0f, 320f)
                            val silenceThreshold = maxOf(ambientRms * 1.35f, 220f)

                            if (rms >= speechThreshold) {
                                speechFrames++
                                silenceFrames = 0
                                if (speechFrames >= 2) {
                                    hasConfirmedSpeech = true
                                }
                            } else if (rms <= silenceThreshold) {
                                if (hasConfirmedSpeech) {
                                    silenceFrames++
                                    if (silenceFrames >= targetSilenceFrames) {
                                        Log.d(tag, "Silence threshold reached after speech ($silenceFrames frames ~1.1s). Auto-finishing capture.")
                                        mainHandler.post { onSilenceDetected?.invoke() }
                                        break
                                    }
                                } else {
                                    // Before speech begins, slowly drift ambient noise floor
                                    ambientRms = (ambientRms * 0.92f + rms * 0.08f).coerceIn(80f, 1200f)
                                }
                            } else {
                                // Intermediate energy level (pause or breath)
                                if (hasConfirmedSpeech) {
                                    silenceFrames++
                                    if (silenceFrames >= targetSilenceFrames + 2) {
                                        Log.d(tag, "Post-speech breath pause reached. Auto-finishing capture.")
                                        mainHandler.post { onSilenceDetected?.invoke() }
                                        break
                                    }
                                }
                            }
                        }

                        // Max timeout fallback (9s)
                        if (totalFrames >= maxTotalFrames) {
                            Log.d(tag, "Max capture duration reached (9s). Auto-finishing capture.")
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
        audioRecord = null
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
