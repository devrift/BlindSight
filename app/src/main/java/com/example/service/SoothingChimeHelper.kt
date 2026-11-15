package com.example.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural synthesizer for soothing, calming, non-alerting earcons.
 * Replaces harsh DTMF ToneGenerators and Google Assistant chime sound effects
 * with warm sinusoidal harmonic tones with smooth exponential envelopes.
 */
class SoothingChimeHelper {

    private val tag = "SoothingChimeHelper"
    private val sampleRate = 44100

    enum class ChimeType {
        WAKE,
        LISTEN,
        OK,
        ERROR,
        TICK,
        OBSTACLE_CRITICAL
    }

    private val precomputedChimes = mutableMapOf<ChimeType, ByteArray>()

    init {
        try {
            precomputeChimes()
        } catch (e: Exception) {
            Log.w(tag, "Failed to precompute soothing chimes: ${e.message}")
        }
    }

    private fun precomputeChimes() {
        // WAKE: Warm, gentle rising two-tone chime (C5 523.25 Hz -> G5 783.99 Hz)
        precomputedChimes[ChimeType.WAKE] = generateTwoTone(
            freq1 = 523.25,
            freq2 = 783.99,
            duration1Ms = 90,
            duration2Ms = 190,
            amplitude = 0.28
        )

        // LISTEN: Very soft, gentle high ping (A5 880 Hz, 70ms) - subtle and zen
        precomputedChimes[ChimeType.LISTEN] = generateSingleTone(
            freq = 880.0,
            durationMs = 70,
            amplitude = 0.16
        )

        // OK: Calming major resolution (E5 659.25 Hz -> C6 1046.50 Hz)
        precomputedChimes[ChimeType.OK] = generateTwoTone(
            freq1 = 659.25,
            freq2 = 1046.50,
            duration1Ms = 80,
            duration2Ms = 170,
            amplitude = 0.24
        )

        // ERROR / CANCEL: Gentle low cello tone (D4 293.66 Hz -> A3 220.00 Hz) - no harsh buzz
        precomputedChimes[ChimeType.ERROR] = generateTwoTone(
            freq1 = 293.66,
            freq2 = 220.00,
            duration1Ms = 80,
            duration2Ms = 140,
            amplitude = 0.20
        )

        // TICK: Subtle micro-click for UI tab switches (1000 Hz, 12ms)
        precomputedChimes[ChimeType.TICK] = generateSingleTone(
            freq = 1000.0,
            durationMs = 15,
            amplitude = 0.08
        )

        // OBSTACLE_CRITICAL: Warm double marimba pulse (C5 523.25 Hz -> E5 659.25 Hz)
        precomputedChimes[ChimeType.OBSTACLE_CRITICAL] = generateTwoTone(
            freq1 = 523.25,
            freq2 = 659.25,
            duration1Ms = 110,
            duration2Ms = 170,
            amplitude = 0.35
        )
    }

    private fun generateSingleTone(freq: Double, durationMs: Int, amplitude: Double): ByteArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val byteBuf = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        val attackSamples = (sampleRate * 0.015).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val attack = min(1.0, i.toDouble() / attackSamples)
            val decay = exp(-3.8 * (i.toDouble() / numSamples))
            val env = attack * decay
            val sampleVal = (amplitude * env * sin(2.0 * Math.PI * freq * t) * 32767.0).toInt().coerceIn(-32767, 32767)
            byteBuf.putShort(sampleVal.toShort())
        }
        return byteBuf.array()
    }

    private fun generateTwoTone(
        freq1: Double,
        freq2: Double,
        duration1Ms: Int,
        duration2Ms: Int,
        amplitude: Double
    ): ByteArray {
        val n1 = (sampleRate * (duration1Ms / 1000.0)).toInt()
        val n2 = (sampleRate * (duration2Ms / 1000.0)).toInt()
        val totalSamples = n1 + n2
        val byteBuf = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        val attack1 = (sampleRate * 0.012).toInt().coerceAtLeast(1)
        for (i in 0 until n1) {
            val t = i.toDouble() / sampleRate
            val env = min(1.0, i.toDouble() / attack1) * exp(-2.2 * (i.toDouble() / n1))
            val sampleVal = (amplitude * env * sin(2.0 * Math.PI * freq1 * t) * 32767.0).toInt().coerceIn(-32767, 32767)
            byteBuf.putShort(sampleVal.toShort())
        }

        val attack2 = (sampleRate * 0.015).toInt().coerceAtLeast(1)
        for (i in 0 until n2) {
            val t = i.toDouble() / sampleRate
            val env = min(1.0, i.toDouble() / attack2) * exp(-3.5 * (i.toDouble() / n2))
            val sampleVal = (amplitude * 1.15 * env * sin(2.0 * Math.PI * freq2 * t) * 32767.0).toInt().coerceIn(-32767, 32767)
            byteBuf.putShort(sampleVal.toShort())
        }

        return byteBuf.array()
    }

    fun playChime(type: ChimeType) {
        val audioBytes = precomputedChimes[type] ?: return
        thread(name = "BlindSightSoothingChime", priority = Thread.MAX_PRIORITY) {
            var track: AudioTrack? = null
            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuffer, audioBytes.size)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                track.write(audioBytes, 0, audioBytes.size)

                val playDurationMs = (audioBytes.size * 1000L) / (sampleRate * 2) + 30
                Thread.sleep(playDurationMs)
            } catch (e: Exception) {
                Log.w(tag, "Chime playback failed: ${e.message}")
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }
    }
}
