package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.SupportedLanguage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

enum class VoicePersona(val id: String, val label: String, val edgeVoice: String) {
    AUTO("auto", "Natural Neural (Recommended)", "en-GB-RyanNeural"),
    JARVIS("jarvis", "Jarvis British Neural (Male)", "en-GB-RyanNeural"),
    ARIA("aria", "Aria Warm Neural (Female)", "en-US-AriaNeural"),
    GUY("guy", "Guy American Neural (Male)", "en-US-GuyNeural"),
    SWARA("swara", "Swara Indic Neural (Hindi)", "hi-IN-SwaraNeural")
}

class TtsHelper(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private val tag = "TtsHelper"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: SupportedLanguage = SupportedLanguage.ENGLISH
    private var currentPersona: VoicePersona = VoicePersona.AUTO
    private var soothingChimeHelper = SoothingChimeHelper()
    private var currentOnDone: (() -> Unit)? = null
    private var mediaPlayer: MediaPlayer? = null
    private var gatewayUrl: String = ""

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    fun setGatewayUrl(url: String) {
        gatewayUrl = url.trim().removeSuffix("/")
    }

    fun setVoicePersona(persona: VoicePersona) {
        currentPersona = persona
        if (isInitialized) {
            selectBestNaturalVoice(currentLanguage.locale)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true

            // Set accessibility speech audio attributes for crystal-clear natural audio
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                Log.w(tag, "Failed to set audio attributes: ${e.message}")
            }

            // Natural, warm human cadence (never high-pitched or robotic)
            tts?.setSpeechRate(0.98f)
            tts?.setPitch(0.98f)

            setLanguage(currentLanguage)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    currentOnDone?.invoke()
                    currentOnDone = null
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    currentOnDone?.invoke()
                    currentOnDone = null
                }
            })
            onInitComplete(true)
        } else {
            isInitialized = false
            onInitComplete(false)
        }
    }

    fun setLanguage(language: SupportedLanguage) {
        currentLanguage = language
        if (isInitialized) {
            val result = tts?.setLanguage(language.locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(SupportedLanguage.ENGLISH.locale)
            }
            selectBestNaturalVoice(language.locale)
        }
    }

    /**
     * Finds and selects the highest-quality, most human-sounding natural voice installed on the device.
     * Prioritizes high-fidelity neural voices (such as Google Neural / WaveNet voices).
     */
    private fun selectBestNaturalVoice(locale: Locale) {
        try {
            val voices = tts?.voices ?: return
            val langCode = locale.language

            val matchingVoices = voices.filter { voice ->
                voice.locale.language.equals(langCode, ignoreCase = true)
            }

            if (matchingVoices.isEmpty()) return

            // Persona-specific preference
            val targetKeyword = when (currentPersona) {
                VoicePersona.JARVIS -> "en-gb"
                VoicePersona.ARIA -> "female"
                VoicePersona.GUY -> "male"
                VoicePersona.SWARA -> "hi-in"
                VoicePersona.AUTO -> ""
            }

            // Priority 1: High quality neural network voice models
            val bestVoice = matchingVoices.firstOrNull { voice ->
                val name = voice.name.lowercase()
                val matchesPersona = targetKeyword.isEmpty() || name.contains(targetKeyword)
                matchesPersona &&
                    (name.contains("neural") || name.contains("natural") || name.contains("-network") || name.contains("-x-")) &&
                    voice.quality >= Voice.QUALITY_HIGH
            } ?: matchingVoices.firstOrNull { voice ->
                val name = voice.name.lowercase()
                (name.contains("neural") || name.contains("natural") || name.contains("-network") || name.contains("-x-"))
            } ?: matchingVoices.firstOrNull { voice ->
                voice.quality >= Voice.QUALITY_HIGH
            } ?: matchingVoices.firstOrNull { voice ->
                voice.quality >= Voice.QUALITY_NORMAL
            } ?: matchingVoices.firstOrNull()

            if (bestVoice != null) {
                tts?.voice = bestVoice
                Log.d(tag, "Selected natural human voice: ${bestVoice.name} (quality=${bestVoice.quality})")
            }
        } catch (e: Exception) {
            Log.w(tag, "Voice selection failed: ${e.message}")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        stop()
        currentOnDone = onDone

        // 1. If Edge Gateway is available, attempt streaming human Edge-TTS neural audio
        if (gatewayUrl.isNotBlank() && (gatewayUrl.startsWith("http://") || gatewayUrl.startsWith("https://"))) {
            thread(name = "BlindSightStreamTts") {
                val streamed = tryStreamGatewayTts(text)
                if (!streamed) {
                    // Fall back to on-device natural voice
                    speakOnDevice(text)
                }
            }
            return
        }

        // 2. Direct on-device natural neural synthesis
        speakOnDevice(text)
    }

    private fun tryStreamGatewayTts(text: String): Boolean {
        try {
            val voiceParam = when (currentPersona) {
                VoicePersona.JARVIS -> "en-GB-RyanNeural"
                VoicePersona.ARIA -> "en-US-AriaNeural"
                VoicePersona.GUY -> "en-US-GuyNeural"
                VoicePersona.SWARA -> "hi-IN-SwaraNeural"
                VoicePersona.AUTO -> if (currentLanguage == SupportedLanguage.HINDI) "hi-IN-SwaraNeural" else "en-GB-RyanNeural"
            }
            val langParam = currentLanguage.code.split("-").first().lowercase()

            val url = URL("$gatewayUrl/api/tts")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 1800
            conn.readTimeout = 4000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val jsonBody = """{"text":${org.json.JSONObject.quote(text)},"lang":"$langParam","voice":"$voiceParam"}"""
            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val tempMp3 = File(context.cacheDir, "streamed_tts_${System.currentTimeMillis()}.mp3")
                conn.inputStream.use { input ->
                    FileOutputStream(tempMp3).use { output ->
                        input.copyTo(output)
                    }
                }

                if (tempMp3.length() > 500) {
                    playMp3File(tempMp3)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Gateway TTS stream unavailable (${e.message}), using on-device natural voice.")
        }
        return false
    }

    private fun playMp3File(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                    currentOnDone?.invoke()
                    currentOnDone = null
                }
                setOnErrorListener { _, _, _ ->
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                    currentOnDone?.invoke()
                    currentOnDone = null
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(tag, "MediaPlayer playback error: ${e.message}")
            try {
                file.delete()
            } catch (_: Exception) {}
            speakOnDevice(text = "")
        }
    }

    private fun speakOnDevice(text: String) {
        if (!isInitialized || text.isBlank()) {
            currentOnDone?.invoke()
            currentOnDone = null
            return
        }
        val uId = "blindsight_${System.currentTimeMillis()}"
        val params = android.os.Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, uId)
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        if (isInitialized) {
            try {
                tts?.stop()
            } catch (_: Exception) {}
        }
        currentOnDone = null
    }

    fun playEarcon(type: EarconType) {
        val chime = when (type) {
            EarconType.WAKE -> SoothingChimeHelper.ChimeType.WAKE
            EarconType.LISTEN -> SoothingChimeHelper.ChimeType.LISTEN
            EarconType.OK -> SoothingChimeHelper.ChimeType.OK
            EarconType.ERROR -> SoothingChimeHelper.ChimeType.ERROR
            EarconType.TICK -> SoothingChimeHelper.ChimeType.TICK
            EarconType.OBSTACLE_CRITICAL -> SoothingChimeHelper.ChimeType.OBSTACLE_CRITICAL
        }
        soothingChimeHelper.playChime(chime)
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    enum class EarconType {
        WAKE, LISTEN, OK, ERROR, TICK, OBSTACLE_CRITICAL
    }
}
