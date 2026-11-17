package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.SupportedLanguage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

enum class VoicePersona(val id: String, val label: String, val edgeVoice: String) {
    AUTO("auto", "Natural Neural Human (Recommended)", "en-GB-RyanNeural"),
    JARVIS("jarvis", "Jarvis British Neural (Male)", "en-GB-RyanNeural"),
    ARIA("aria", "Aria Warm Human (Female)", "en-US-AriaNeural"),
    GUY("guy", "Guy American Human (Male)", "en-US-GuyNeural"),
    SWARA("swara", "Swara Indic Human (Hindi)", "hi-IN-SwaraNeural")
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
    private val soothingChimeHelper = SoothingChimeHelper()
    private var currentOnDone: (() -> Unit)? = null
    private var mediaPlayer: MediaPlayer? = null
    private var gatewayUrl: String = ""
    private val isSpeakingActive = AtomicBoolean(false)
    private var playbackThread: Thread? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager

    init {
        // Try initializing with Google TTS engine first for neural human-quality speech
        try {
            tts = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
        } catch (_: Exception) {
            tts = TextToSpeech(context.applicationContext, this)
        }
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

            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                Log.w(tag, "Failed to set audio attributes: ${e.message}")
            }

            // Natural, brisk conversational cadence (1.15x speed, natural pitch)
            tts?.setSpeechRate(1.15f)
            tts?.setPitch(1.0f)

            setLanguage(currentLanguage)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onPlaybackFinished()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onPlaybackFinished()
                }
            })
            onInitComplete(true)
        } else {
            // Fall back to default engine if Google TTS failed
            try {
                tts = TextToSpeech(context.applicationContext) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        setLanguage(currentLanguage)
                        onInitComplete(true)
                    } else {
                        isInitialized = false
                        onInitComplete(false)
                    }
                }
            } catch (e: Exception) {
                isInitialized = false
                onInitComplete(false)
            }
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
     * Prioritizes high-fidelity neural voices (such as Google Neural / WaveNet network voices).
     */
    private fun selectBestNaturalVoice(locale: Locale) {
        try {
            val voices = tts?.voices ?: return
            val langCode = locale.language

            val matchingVoices = voices.filter { voice ->
                voice.locale.language.equals(langCode, ignoreCase = true)
            }

            if (matchingVoices.isEmpty()) return

            val targetKeyword = when (currentPersona) {
                VoicePersona.JARVIS -> "en-gb"
                VoicePersona.ARIA -> "female"
                VoicePersona.GUY -> "male"
                VoicePersona.SWARA -> "hi-in"
                VoicePersona.AUTO -> ""
            }

            val bestVoice = matchingVoices.firstOrNull { voice ->
                val name = voice.name.lowercase()
                val matchesPersona = targetKeyword.isEmpty() || name.contains(targetKeyword)
                matchesPersona &&
                    (name.contains("network") || name.contains("neural") || name.contains("-x-") || name.contains("natural")) &&
                    voice.quality >= Voice.QUALITY_HIGH
            } ?: matchingVoices.firstOrNull { voice ->
                val name = voice.name.lowercase()
                (name.contains("network") || name.contains("neural") || name.contains("-x-"))
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

    private fun onPlaybackFinished() {
        isSpeakingActive.set(false)
        try {
            audioManager?.mode = android.media.AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        currentOnDone?.invoke()
        currentOnDone = null
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        stop()
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        currentOnDone = onDone
        isSpeakingActive.set(true)
        try {
            audioManager?.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {}

        val cleanText = text
            .replace(Regex("[*#_`~\\[\\]()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 1 Single Professional, Easy-to-Understand Neural Voice per Supported Language
        val voiceParam = when (currentLanguage) {
            SupportedLanguage.HINDI -> "hi-IN-SwaraNeural"
            SupportedLanguage.PUNJABI -> "pa-IN-OjasNeural"
            SupportedLanguage.BENGALI -> "bn-IN-TanishaaNeural"
            SupportedLanguage.ENGLISH -> "en-IN-NeerjaNeural"
        }
        val langParam = currentLanguage.code.split("-").first().lowercase()

        val textToStream = cleanText.take(400)
        val streamUrl = "https://blindsightai.vercel.app/api/tts?text=${URLEncoder.encode(textToStream, "UTF-8")}&voice=$voiceParam&lang=$langParam"

        val mainHandler = Handler(Looper.getMainLooper())
        var watchdogTriggered = false

        mainHandler.post {
            try {
                mediaPlayer?.release()
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .build()
                    )
                    setDataSource(streamUrl)
                }
                mediaPlayer = player

                val fallbackRunnable = Runnable {
                    if (isSpeakingActive.get()) {
                        watchdogTriggered = true
                        Log.d(tag, "Stream watchdog fired after 3800ms. Falling back to on-device TTS.")
                        try {
                            player.reset()
                            player.release()
                        } catch (_: Exception) {}
                        if (mediaPlayer == player) mediaPlayer = null
                        speakOnDevice(cleanText)
                    }
                }
                mainHandler.postDelayed(fallbackRunnable, 3800L)

                player.setOnPreparedListener { mp ->
                    mainHandler.removeCallbacks(fallbackRunnable)
                    if (isSpeakingActive.get() && !watchdogTriggered) {
                        try {
                            mp.start()
                        } catch (e: Exception) {
                            speakOnDevice(cleanText)
                        }
                    } else {
                        try {
                            mp.release()
                        } catch (_: Exception) {}
                        if (mediaPlayer == mp) mediaPlayer = null
                    }
                }

                player.setOnCompletionListener { mp ->
                    try {
                        mp.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer == mp) mediaPlayer = null
                    onPlaybackFinished()
                }

                player.setOnErrorListener { mp, what, extra ->
                    mainHandler.removeCallbacks(fallbackRunnable)
                    Log.w(tag, "MediaPlayer stream error: what=$what, extra=$extra. Falling back to on-device.")
                    try {
                        mp.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer == mp) mediaPlayer = null
                    if (isSpeakingActive.get() && !watchdogTriggered) {
                        speakOnDevice(cleanText)
                    } else {
                        onPlaybackFinished()
                    }
                    true
                }

                player.prepareAsync()

            } catch (e: Exception) {
                Log.w(tag, "MediaPlayer setup error: ${e.message}. Using on-device voice.")
                speakOnDevice(cleanText)
            }
        }
    }

    private fun speakOnDevice(text: String) {
        if (!isInitialized || text.isBlank() || !isSpeakingActive.get()) {
            onPlaybackFinished()
            return
        }
        val uId = "blindsight_${System.currentTimeMillis()}"
        val params = android.os.Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, uId)
    }

    fun stop() {
        isSpeakingActive.set(false)
        try {
            audioManager?.mode = android.media.AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        try {
            playbackThread?.interrupt()
            playbackThread = null
        } catch (_: Exception) {}

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
