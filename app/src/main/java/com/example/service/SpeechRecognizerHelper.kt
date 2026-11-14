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
    var isContinuousMode = false
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
                Log.d(tag, "In-app SpeechRecognizer created successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to create SpeechRecognizer: ${e.message}", e)
                speechRecognizer = null
            }
        }
    }

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    /**
     * Resolves phonetic slips and matches priority BlindSight voice intents
     * so that similar-sounding words are not shuffled into nonsense.
     */
    private fun selectBestTranscript(candidates: List<String>): String {
        if (candidates.isEmpty()) return ""

        val normalized = candidates.map { normalizePhonetics(it) }

        val priorityKeywords = listOf(
            "hey blindsight", "blindsight", "ok blindsight", "okay blindsight",
            "blind sight", "hey blind sight",
            "describe the scene", "describe scene", "describe what is in front of me", "describe",
            "what is in front of me", "what's in front of me", "look ahead", "look around",
            "what time is it", "tell me the time", "battery level", "battery",
            "turn on flashlight", "turn off flashlight", "flashlight", "torch",
            "tell me a joke", "tell a joke", "who are you", "what is your name",
            "stop", "be quiet", "cancel",
            "नमस्ते", "ब्लाइंडसाइट", "सामने क्या है", "समय क्या है", "बैटरी कितनी है"
        )

        for (cand in normalized) {
            val low = cand.lowercase().trim()
            for (kw in priorityKeywords) {
                if (low.startsWith(kw) || low.contains(kw)) {
                    Log.d(tag, "Matched priority voice intent: \"$cand\" via keyword: \"$kw\"")
                    return cand
                }
            }
        }

        return normalized.first().trim()
    }

    private fun normalizePhonetics(raw: String): String {
        var text = raw.trim()
        val low = text.lowercase()

        // Normalize common phonetic slips for wake word and key blind assistant commands
        if (low.contains("blind site") || low.contains("blind side") || low.contains("bind sight") || low.contains("line sight")) {
            text = text.replace(Regex("(?i)blind\\s+site|blind\\s+side|bind\\s+sight|line\\s+sight"), "BlindSight")
        }
        if (low.startsWith("this tribe") || low.startsWith("the scribe") || low.startsWith("dis tribe") || low.startsWith("the stripe")) {
            text = text.replace(Regex("(?i)^this\\s+tribe|^the\\s+scribe|^dis\\s+tribe|^the\\s+stripe"), "describe")
        }
        if (low.contains("ब्लाइंड साइट") || low.contains("ब्लाइंड साइड")) {
            text = text.replace(Regex("ब्लाइंड\\s+साइट|ब्लाइंड\\s+साइड"), "ब्लाइंडसाइट")
        }

        return text
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                clientErrorCount = 0
                onListeningStateChanged?.invoke(true)
                Log.d(tag, "In-app recognizer ready for speech.")
            }

            override fun onBeginningOfSpeech() {
                Log.d(tag, "Speech detected.")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                Log.d(tag, "Speech ended. Processing audio.")
            }

            override fun onError(error: Int) {
                isListening = false
                onListeningStateChanged?.invoke(false)

                Log.w(tag, "SpeechRecognizer onError: code=$error")

                // 1. Standby timeout (no speech detected)
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (isContinuousMode && !isPaused && hasAudioPermission()) {
                        mainHandler.postDelayed({
                            if (isContinuousMode && !isPaused && !isListening) {
                                startListening(currentLanguageCode)
                            }
                        }, 1200)
                    } else {
                        // On-demand mode: cleanly rest in IDLE. No annoying 2-second mic oscillation!
                        onError("Tap center button to speak.")
                    }
                    return
                }

                // 2. Recognizer is busy -> reset and resume
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    destroyRecognizerInternal()
                    if (isContinuousMode && !isPaused && hasAudioPermission()) {
                        mainHandler.postDelayed({
                            if (!isPaused) {
                                startListening(currentLanguageCode, forceRestart = true)
                            }
                        }, 500)
                    } else {
                        onError("Tap center button to speak.")
                    }
                    return
                }

                // 3. Client error (Error 5): binding drop -> self-heal
                if (error == SpeechRecognizer.ERROR_CLIENT) {
                    handleClientError()
                    return
                }

                // 4. Insufficient permissions (Error 9)
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    onError("Microphone permission required. Please allow access in Settings.")
                    return
                }

                // Other errors
                if (isContinuousMode && !isPaused && hasAudioPermission()) {
                    mainHandler.postDelayed({
                        if (isContinuousMode && !isPaused && !isListening) {
                            startListening(currentLanguageCode)
                        }
                    }, 2000)
                } else {
                    onError("Tap center button to speak.")
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                clientErrorCount = 0
                onListeningStateChanged?.invoke(false)

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                val bestText = selectBestTranscript(matches)
                Log.d(tag, "Recognized candidates: $matches -> Selected: \"$bestText\"")

                if (bestText.isNotBlank()) {
                    onResult(bestText)
                } else if (isContinuousMode && !isPaused && hasAudioPermission()) {
                    mainHandler.postDelayed({
                        if (isContinuousMode && !isPaused && !isListening) {
                            startListening(currentLanguageCode)
                        }
                    }, 1200)
                } else {
                    onError("Tap center button to speak.")
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
            val backoffMs = 500L * clientErrorCount
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
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000L)
            }

            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                onListeningStateChanged?.invoke(true)
                Log.d(tag, "In-app startListening called successfully.")
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
            }, 250)
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
