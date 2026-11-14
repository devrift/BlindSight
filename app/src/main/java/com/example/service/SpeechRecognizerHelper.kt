package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clientErrorCount = 0
    private val maxClientRetries = 3

    init {
        initRecognizer()
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initRecognizer() {
        mainHandler.post {
            destroyRecognizerInternal()

            if (!hasAudioPermission()) {
                Log.d(tag, "Audio permission not yet granted; recognizer initialization deferred.")
                return@post
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(tag, "Speech recognition service is not available on this device.")
                return@post
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
                Log.d(tag, "SpeechRecognizer created successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to create SpeechRecognizer: ${e.message}", e)
                speechRecognizer = null
            }
        }
    }

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(tag, "cancel failed: ${e.message}")
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(tag, "destroy failed: ${e.message}")
        }
        speechRecognizer = null
        isListening = false
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                clientErrorCount = 0
                onListeningStateChanged?.invoke(true)
                Log.d(tag, "Ready for speech.")
            }

            override fun onBeginningOfSpeech() {
                Log.d(tag, "Speech started.")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                onListeningStateChanged?.invoke(false)
                Log.d(tag, "Speech ended.")
            }

            override fun onError(error: Int) {
                isListening = false
                onListeningStateChanged?.invoke(false)

                Log.w(tag, "SpeechRecognizer onError: code=$error")

                // 1. Standby timeout or no speech detected (normal background behavior)
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (!isPaused && hasAudioPermission()) {
                        mainHandler.postDelayed({
                            if (!isPaused && !isListening) {
                                startListening(currentLanguageCode)
                            }
                        }, 250)
                    }
                    return
                }

                // 2. Recognizer is busy -> reset and resume
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    destroyRecognizerInternal()
                    if (!isPaused && hasAudioPermission()) {
                        mainHandler.postDelayed({
                            if (!isPaused) {
                                startListening(currentLanguageCode, forceRestart = true)
                            }
                        }, 350)
                    }
                    return
                }

                // 3. Client error (Error 5): binding drop or state conflict -> self-heal
                if (error == SpeechRecognizer.ERROR_CLIENT) {
                    handleClientError()
                    return
                }

                // 4. Insufficient permissions (Error 9)
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    onError("Microphone permission required. Please allow access.")
                    return
                }

                // 5. Network / Audio / Server errors
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone."
                    SpeechRecognizer.ERROR_NETWORK -> "Network issue with speech services."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network timeout."
                    SpeechRecognizer.ERROR_SERVER -> "Speech server error."
                    else -> "Microphone ready. Tap center button to speak."
                }

                if (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_NETWORK) {
                    onError(errorMessage)
                }

                // Auto-recover after transient hiccups
                if (!isPaused && hasAudioPermission()) {
                    mainHandler.postDelayed({
                        if (!isPaused && !isListening) {
                            startListening(currentLanguageCode)
                        }
                    }, 1000)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                clientErrorCount = 0
                onListeningStateChanged?.invoke(false)

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim() ?: ""
                Log.d(tag, "Recognized text: \"$recognizedText\"")

                if (recognizedText.isNotBlank()) {
                    onResult(recognizedText)
                } else if (!isPaused && hasAudioPermission()) {
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

    private fun handleClientError() {
        clientErrorCount++
        Log.w(tag, "Encountered ERROR_CLIENT ($clientErrorCount/$maxClientRetries). Re-initializing...")

        destroyRecognizerInternal()

        if (clientErrorCount <= maxClientRetries) {
            val backoffMs = 350L * clientErrorCount
            mainHandler.postDelayed({
                if (!isPaused && hasAudioPermission()) {
                    startListening(currentLanguageCode, forceRestart = true)
                }
            }, backoffMs)
        } else {
            Log.w(tag, "SpeechRecognizer binding unreachable after $maxClientRetries attempts.")
            clientErrorCount = 0
            onError("Tap center button to speak.")
        }
    }

    fun startListening(languageCode: String = currentLanguageCode, forceRestart: Boolean = false) {
        mainHandler.post {
            currentLanguageCode = languageCode
            isPaused = false

            if (!hasAudioPermission()) {
                Log.d(tag, "Cannot start listening: RECORD_AUDIO permission missing.")
                return@post
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Tap center button to speak.")
                return@post
            }

            if (speechRecognizer == null) {
                destroyRecognizerInternal()
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                } catch (e: Exception) {
                    Log.e(tag, "createSpeechRecognizer failed in startListening: ${e.message}", e)
                    return@post
                }
            }

            if (isListening && !forceRestart) {
                Log.d(tag, "Already listening, keeping active session.")
                return@post
            }

            // Cancel any prior session to clear state before startListening
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(tag, "Cancel error: ${e.message}")
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                onListeningStateChanged?.invoke(true)
                Log.d(tag, "startListening called successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Error invoking startListening: ${e.message}", e)
                handleClientError()
            }
        }
    }

    fun pauseListening() {
        mainHandler.post {
            isPaused = true
            stopListeningInternal()
        }
    }

    fun resumeListening(languageCode: String = currentLanguageCode) {
        mainHandler.post {
            isPaused = false
            mainHandler.postDelayed({
                if (!isListening && hasAudioPermission()) {
                    startListening(languageCode)
                }
            }, 150)
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
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

    fun onPermissionGranted(languageCode: String = currentLanguageCode) {
        mainHandler.post {
            Log.d(tag, "Microphone permission confirmed. Starting recognizer.")
            clientErrorCount = 0
            destroyRecognizerInternal()
            startListening(languageCode, forceRestart = true)
        }
    }

    fun destroy() {
        mainHandler.post {
            isPaused = true
            mainHandler.removeCallbacksAndMessages(null)
            destroyRecognizerInternal()
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
}
