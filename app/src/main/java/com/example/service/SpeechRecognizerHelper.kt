package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
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
    var isContinuousMode = true
    private var currentLanguageCode = "en-US"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
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
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
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
     * Suppresses default Google Assistant beep/chime sound effects during speech recognition.
     */
    private fun suppressSystemSoundsTemporarily(durationMs: Long = 320L) {
        try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            mainHandler.postDelayed({
                try {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                } catch (_: Exception) {}
            }, durationMs)
        } catch (_: Exception) {}
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
            "a blindsight", "blind sight", "hey blind sight", "blind side",
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

    fun isWakeWordTrigger(text: String): Boolean {
        val low = text.lowercase().trim()
        val wakeRegex = Regex(
            "\\b(hey|a|ay|ey|hi|hello|ok|okay|the|हे|नमस्ते|सुनो)?\\s*(?:blind\\s*sight|blind\\s*side|blind\\s*site|blindsight|blind\\s*light|line\\s*sight|ब्लाइंड\\s*साइट|ब्लाइंडसाइट)\\b",
            RegexOption.IGNORE_CASE
        )
        return wakeRegex.containsMatchIn(low)
    }

    private fun normalizePhonetics(raw: String): String {
        var text = raw.trim()
        val low = text.lowercase()

        // Normalize common phonetic slips for wake word ("a blindsight", "blind side", "blind site", etc.)
        if (low.contains("a blindsight") || low.contains("a blind sight") || low.contains("a blind side") ||
            low.contains("blind site") || low.contains("blind side") || low.contains("bind sight") ||
            low.contains("line sight") || low.contains("ey blindsight") || low.contains("ay blindsight") ||
            low.contains("blindsighted") || low.contains("hey blind")
        ) {
            text = text.replace(
                Regex("(?i)\\b(a|ay|ey|the|hey)?\\s*(blind\\s*site|blind\\s*side|bind\\s*sight|line\\s*sight|blind\\s*light|blind\\s*sight)\\b"),
                "BlindSight"
            )
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
                suppressSystemSoundsTemporarily(200L)
                Log.d(tag, "Speech ended. Processing audio.")
            }

            override fun onError(error: Int) {
                isListening = false
                onListeningStateChanged?.invoke(false)

                Log.w(tag, "SpeechRecognizer onError: code=$error")

                // 1. Standby timeout (no speech detected) - seamless restart with zero lag!
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (isContinuousMode && !isPaused && hasAudioPermission()) {
                        mainHandler.postDelayed({
                            if (isContinuousMode && !isPaused && !isListening) {
                                startListening(currentLanguageCode)
                            }
                        }, 150L)
                    }
                    return
                }

                // 2. Recognizer is busy -> reset and resume quickly
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    destroyRecognizerInternal()
                    if (isContinuousMode && !isPaused && hasAudioPermission()) {
                        mainHandler.postDelayed({
                            if (!isPaused && isContinuousMode) {
                                startListening(currentLanguageCode, forceRestart = true)
                            }
                        }, 250L)
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

                // Other transient errors -> fast restart
                if (isContinuousMode && !isPaused && hasAudioPermission()) {
                    mainHandler.postDelayed({
                        if (isContinuousMode && !isPaused && !isListening) {
                            startListening(currentLanguageCode)
                        }
                    }, 500L)
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
                }

                // Automatically keep listening in continuous wake-word mode
                if (isContinuousMode && !isPaused && hasAudioPermission()) {
                    mainHandler.postDelayed({
                        if (isContinuousMode && !isPaused && !isListening) {
                            startListening(currentLanguageCode)
                        }
                    }, 150L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Keep recognizing full user sentence without premature cut-off.
                // Aborting here truncates the utterance ("Hey BlindSight what's ahead?")
                // and drops the user's question before they finish speaking.
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                if (matches.isNotEmpty()) {
                    val cand = matches[0]
                    Log.v(tag, "Partial speech: \"$cand\"")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleClientError() {
        clientErrorCount++
        Log.w(tag, "Encountered ERROR_CLIENT ($clientErrorCount/$maxClientRetries).")
        destroyRecognizerInternal()

        if (isContinuousMode && clientErrorCount <= maxClientRetries) {
            val backoffMs = 500L * clientErrorCount
            mainHandler.postDelayed({
                if (!isPaused && hasAudioPermission() && isContinuousMode) {
                    startListening(currentLanguageCode, forceRestart = true)
                }
            }, backoffMs)
        } else {
            clientErrorCount = 0
            onError("Tap center button to speak.")
        }
    }

    fun sanitizeLocale(raw: String): String {
        val low = raw.lowercase()
        return when {
            low.startsWith("hi") || low.startsWith("ur") -> "hi-IN"
            low.startsWith("pa") -> "pa-IN"
            low.startsWith("bn") -> "bn-IN"
            low.startsWith("en") && low.contains("us") -> "en-US"
            else -> "en-IN"
        }
    }

    fun startListening(languageCode: String = currentLanguageCode, forceRestart: Boolean = false) {
        mainHandler.post {
            val lockedLocale = sanitizeLocale(languageCode)
            currentLanguageCode = lockedLocale
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lockedLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lockedLocale)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lockedLocale)
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("en-IN", "hi-IN", "ur-IN", "pa-IN", "bn-IN"))
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            try {
                // Force audio mode to MODE_IN_COMMUNICATION to activate native hardware AEC & NoiseSuppressor
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                // Suppress Google Assistant prompt sound effect
                suppressSystemSoundsTemporarily(320L)
                speechRecognizer?.startListening(intent)
                isListening = true
                onListeningStateChanged?.invoke(true)
                Log.d(tag, "In-app startListening called successfully with hardware AEC/ENC.")
            } catch (e: Exception) {
                try { audioManager?.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
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
                audioManager?.mode = AudioManager.MODE_NORMAL
            } catch (_: Exception) {}
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
            Log.d(tag, "Microphone permission confirmed. Standby ready.")
            clientErrorCount = 0
            destroyRecognizerInternal()
            // Do not aggressively auto-start flapping recognizer on launch unless continuous mode is active
            if (isContinuousMode) {
                startListening(languageCode, forceRestart = true)
            }
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
