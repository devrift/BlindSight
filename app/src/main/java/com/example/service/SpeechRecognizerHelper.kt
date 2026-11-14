package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: ((Boolean) -> Unit)? = null
) {
    private val tag = "SpeechRecognizerHelper"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isPaused = false
    private var currentLanguageCode = "en-US"
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "Speech recognition service is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize SpeechRecognizer: ${e.message}", e)
            speechRecognizer = null
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onListeningStateChanged?.invoke(true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                onListeningStateChanged?.invoke(false)
            }

            override fun onError(error: Int) {
                isListening = false
                onListeningStateChanged?.invoke(false)

                // Silent auto-resume on normal silence/timeout in background wake word mode
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (!isPaused) {
                        mainHandler.postDelayed({
                            if (!isPaused && !isListening) {
                                startListening(currentLanguageCode)
                            }
                        }, 250)
                    }
                    return
                }

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    initRecognizer()
                    if (!isPaused) {
                        mainHandler.postDelayed({
                            if (!isPaused && !isListening) {
                                startListening(currentLanguageCode)
                            }
                        }, 400)
                    }
                    return
                }

                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network issue with speech service."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network timeout."
                    SpeechRecognizer.ERROR_SERVER -> "Speech server error."
                    else -> "Speech recognition error ($error)."
                }
                Log.w(tag, "SpeechRecognizer error: $errorMessage")
                onError(errorMessage)

                // Attempt recovery after network or client glitches
                if (!isPaused) {
                    mainHandler.postDelayed({
                        if (!isPaused && !isListening) {
                            startListening(currentLanguageCode)
                        }
                    }, 1000)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                onListeningStateChanged?.invoke(false)

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim() ?: ""
                if (recognizedText.isNotBlank()) {
                    onResult(recognizedText)
                } else if (!isPaused) {
                    mainHandler.postDelayed({
                        if (!isPaused && !isListening) {
                            startListening(currentLanguageCode)
                        }
                    }, 250)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening(languageCode: String = "en-US") {
        currentLanguageCode = languageCode
        isPaused = false

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service not available.")
            return
        }

        if (speechRecognizer == null) {
            initRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error starting speech recognition: ${e.message}", e)
        }
    }

    fun pauseListening() {
        isPaused = true
        stopListening()
    }

    fun resumeListening(languageCode: String = currentLanguageCode) {
        isPaused = false
        mainHandler.postDelayed({
            if (!isListening) {
                startListening(languageCode)
            }
        }, 150)
    }

    fun stopListening() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Error stopping listening: ${e.message}")
            }
            isListening = false
            onListeningStateChanged?.invoke(false)
        }
    }

    fun destroy() {
        isPaused = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(tag, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
        isListening = false
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
}
