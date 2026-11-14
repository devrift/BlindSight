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
 * 1. Push-to-talk / tap-to-talk recording
 * 2. On-device Voice Activity Detection (VAD) to auto-stop after 1.4s of trailing silence
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

            recordingThread = thread(name = "BlindSightAudioRecord") {
                val shortBuffer = ShortArray(bufferSize / 2)
                var hasSpoken = false
                var silenceFrames = 0
                var totalFrames = 0
                // 1.6 seconds of trailing silence
                val maxSilenceFrames = ((1.6 * sampleRate) / shortBuffer.size).toInt().coerceAtLeast(10)
                // 8.0 seconds max recording duration
                val maxTotalFrames = ((8.0 * sampleRate) / shortBuffer.size).toInt().coerceAtLeast(40)

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

                        // Speech vs Silence detection (low threshold 250f for phone mics)
                        if (rms > 250f) {
                            hasSpoken = true
                            silenceFrames = 0
                        } else if (hasSpoken) {
                            silenceFrames++
                            if (silenceFrames >= maxSilenceFrames) {
                                Log.d(tag, "Silence threshold reached after speech. Auto-finishing capture.")
                                mainHandler.post { onSilenceDetected?.invoke() }
                                break
                            }
                        }

                        if (totalFrames >= maxTotalFrames) {
                            Log.d(tag, "Max capture duration reached (8s). Auto-finishing capture.")
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

        try {
            recordingThread?.join(400)
        } catch (_: Exception) {}
        recordingThread = null

        val pcm = synchronized(pcmStream) { pcmStream.toByteArray() }
        if (pcm.isEmpty()) return ByteArray(0)

        return addWavHeader(pcm, sampleRate, 1, 16)
    }

    private fun addWavHeader(pcm: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val totalAudioLen = pcm.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitDepth / 8).toLong()

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
        header[20] = 1 // Linear PCM
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
        header[32] = (channels * bitDepth / 8).toByte()
        header[33] = 0
        header[34] = bitDepth.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavStream = ByteArrayOutputStream(44 + pcm.size)
        wavStream.write(header)
        wavStream.write(pcm)
        return wavStream.toByteArray()
    }
}
