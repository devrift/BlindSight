package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.SupportedLanguage

class TtsHelper(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: SupportedLanguage = SupportedLanguage.ENGLISH
    private var toneGenerator: ToneGenerator? = null
    private var currentOnDone: (() -> Unit)? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (e: Exception) {
            Log.w("TtsHelper", "Could not initialize ToneGenerator: ${e.message}")
        }
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
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
                // Fallback to default
                tts?.setLanguage(SupportedLanguage.ENGLISH.locale)
            }
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
            Log.w("TtsHelper", "Error playing earcon: ${e.message}")
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
