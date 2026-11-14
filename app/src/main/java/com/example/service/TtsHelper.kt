package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.SupportedLanguage
import java.util.Locale

class TtsHelper(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private val tag = "TtsHelper"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: SupportedLanguage = SupportedLanguage.ENGLISH
    private var toneGenerator: ToneGenerator? = null
    private var currentOnDone: (() -> Unit)? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (e: Exception) {
            Log.w(tag, "Could not initialize ToneGenerator: ${e.message}")
        }
        tts = TextToSpeech(context.applicationContext, this)
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

            // Tune natural human cadence (not slow robotic drone)
            tts?.setSpeechRate(1.03f)
            tts?.setPitch(1.02f)

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
     */
    private fun selectBestNaturalVoice(locale: Locale) {
        try {
            val voices = tts?.voices ?: return
            val langCode = locale.language

            val matchingVoices = voices.filter { voice ->
                voice.locale.language.equals(langCode, ignoreCase = true)
            }

            // Priority 1: High-quality natural/neural voice models
            val bestVoice = matchingVoices.firstOrNull { voice ->
                val name = voice.name.lowercase()
                (name.contains("natural") || name.contains("neural") || name.contains("-x-")) &&
                !voice.isNetworkConnectionRequired &&
                voice.quality >= Voice.QUALITY_HIGH
            } ?: matchingVoices.firstOrNull { voice ->
                // Priority 2: Highest quality local voice
                !voice.isNetworkConnectionRequired && voice.quality >= Voice.QUALITY_NORMAL
            } ?: matchingVoices.firstOrNull()

            if (bestVoice != null) {
                tts?.voice = bestVoice
                Log.d(tag, "Selected natural voice: ${bestVoice.name} (quality=${bestVoice.quality})")
            }
        } catch (e: Exception) {
            Log.w(tag, "Voice selection failed: ${e.message}")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            onDone?.invoke()
            return
        }
        stop()
        currentOnDone = onDone
        val uId = "blindsight_${System.currentTimeMillis()}"
        val params = android.os.Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, uId)
    }

    fun stop() {
        currentOnDone = null
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun playEarcon(type: EarconType) {
        try {
            when (type) {
                EarconType.WAKE -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                }
                EarconType.LISTEN -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                }
                EarconType.OK -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
                }
                EarconType.ERROR -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
                }
                EarconType.TICK -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 50)
                }
                EarconType.OBSTACLE_CRITICAL -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 300)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error playing earcon: ${e.message}")
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        toneGenerator?.release()
        toneGenerator = null
    }

    enum class EarconType {
        WAKE, LISTEN, OK, ERROR, TICK, OBSTACLE_CRITICAL
    }
}
