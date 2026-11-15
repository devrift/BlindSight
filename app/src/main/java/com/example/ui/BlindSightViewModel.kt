package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppState
import com.example.data.model.ChatMessage
import com.example.data.model.ListenMode
import com.example.data.model.MemoryFact
import com.example.data.model.ReflexAlert
import com.example.data.model.ReflexTelemetry
import com.example.data.model.SupportedLanguage
import com.example.data.repository.CompanionRepository
import com.example.service.DirectAudioRecorder
import com.example.service.HapticHelper
import com.example.service.SpeechRecognizerHelper
import com.example.service.TtsHelper
import com.example.service.VoicePersona
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    COMPANION("Voice"),
    RADAR("Obstacles"),
    VISION("Camera"),
    MEMORIES("Memory")
}

data class UiState(
    val appState: AppState = AppState.IDLE,
    val selectedTab: AppTab = AppTab.COMPANION,
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val statusText: String = "BlindSight active. Say \"Hey BlindSight\" or tap the center button.",
    val heardText: String = "",
    val speechInput: String = "",
    val isAutoSweeping: Boolean = false,
    val isCameraScanning: Boolean = false,
    val textSizeMultiplier: Float = 1.0f,
    val highContrastYellow: Boolean = false,
    val gatewayUrl: String = "http://192.168.29.173:5000",
    val latestFrameBytes: ByteArray? = null,
    val voicePersona: VoicePersona = VoicePersona.AUTO
)

class BlindSightViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompanionRepository(application)
    private val hapticHelper = HapticHelper(application)
    private var ttsHelper: TtsHelper? = null
    private var speechRecognizerHelper: SpeechRecognizerHelper? = null
    private var directAudioRecorder: com.example.service.DirectAudioRecorder? = null

    val facts: StateFlow<List<MemoryFact>> = repository.facts
    val history: StateFlow<List<ChatMessage>> = repository.history
    val telemetry: StateFlow<ReflexTelemetry> = repository.telemetry

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var autoSweepJob: Job? = null

    private var isUserCommandActive = false

    init {
        directAudioRecorder = com.example.service.DirectAudioRecorder(
            onSilenceDetected = {
                finishListeningAndProcess()
            }
        )

        ttsHelper = TtsHelper(application) { ready ->
            if (ready) {
                ttsHelper?.setGatewayUrl(repository.getGatewayUrl())
                speak("BlindSight assistant active. Tap the center button to speak.")
            }
        }

        speechRecognizerHelper = SpeechRecognizerHelper(
            context = application,
            onResult = { text ->
                onSpeechResult(text)
            },
            onError = { err ->
                _uiState.value = _uiState.value.copy(
                    appState = AppState.IDLE,
                    statusText = err
                )
                if (err.contains("permission", ignoreCase = true) || err.contains("Audio recording", ignoreCase = true)) {
                    ttsHelper?.playEarcon(TtsHelper.EarconType.ERROR)
                }
            },
            onListeningStateChanged = { listening ->
                if (!listening && _uiState.value.appState == AppState.COMMAND_LISTENING) {
                    viewModelScope.launch {
                        delay(600)
                        if (_uiState.value.appState == AppState.COMMAND_LISTENING) {
                            _uiState.value = _uiState.value.copy(
                                appState = AppState.IDLE,
                                statusText = "Ready. Tap center button to speak."
                            )
                        }
                    }
                }
            }
        )
    }

    fun hasMicrophonePermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun onMicrophonePermissionGranted() {
        speechRecognizerHelper?.onPermissionGranted(_uiState.value.language.code)
    }

    fun getGeminiApiKey(): String = repository.getGeminiApiKey()

    fun setGeminiApiKey(key: String) {
        repository.setGeminiApiKey(key)
        hapticHelper.vibrateConfirm()
        val msg = if (key.isNotBlank()) "Gemini API key saved." else "Gemini API key cleared. Using local offline mode."
        speak(msg)
    }

    fun selectTab(tab: AppTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        hapticHelper.vibrateTap()
        ttsHelper?.playEarcon(TtsHelper.EarconType.TICK)
        val tabDescription = when (tab) {
            AppTab.COMPANION -> "Switched to Voice Companion tab."
            AppTab.RADAR -> "Switched to Obstacle Radar tab. Ultrasonic reflex sensors active."
            AppTab.VISION -> {
                refreshLatestHardwareFrame()
                "Switched to Camera Vision tab."
            }
            AppTab.MEMORIES -> "Switched to Memory Bank tab."
        }
        speak(tabDescription)
    }

    fun getGatewayUrl(): String = repository.getGatewayUrl()

    fun setGatewayUrl(url: String) {
        repository.setGatewayUrl(url)
        val cleanUrl = repository.getGatewayUrl()
        _uiState.value = _uiState.value.copy(gatewayUrl = cleanUrl)
        ttsHelper?.setGatewayUrl(cleanUrl)
        hapticHelper.vibrateConfirm()
        speak("Glasses Gateway URL updated.")
    }

    fun setVoicePersona(persona: VoicePersona) {
        _uiState.value = _uiState.value.copy(voicePersona = persona)
        ttsHelper?.setVoicePersona(persona)
        hapticHelper.vibrateConfirm()
        speak("Voice set to ${persona.label}.")
    }

    fun refreshLatestHardwareFrame() {
        viewModelScope.launch {
            val bytes = repository.fetchLatestHardwareFrame()
            if (bytes != null && bytes.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(latestFrameBytes = bytes)
            }
        }
    }

    fun onGiantMicClick() {
        hapticHelper.vibrateTap()
        val currentState = _uiState.value.appState
        if (currentState == AppState.SPEAKING) {
            stopSpeaking()
            return
        }

        if (currentState == AppState.COMMAND_LISTENING) {
            // User tapped again to finish speaking
            finishListeningAndProcess()
            return
        }

        if (!hasMicrophonePermission()) {
            _uiState.value = _uiState.value.copy(
                statusText = "Microphone permission required. Please allow access in Settings."
            )
            speak("Microphone permission required. Please allow access in Settings.")
            return
        }

        isUserCommandActive = true
        ttsHelper?.playEarcon(TtsHelper.EarconType.LISTEN)
        _uiState.value = _uiState.value.copy(
            appState = AppState.COMMAND_LISTENING,
            statusText = "Listening… Speak your question to BlindSight."
        )

        // 100% In-App recording directly to Groq Whisper: Zero Google SpeechRecognizer oscillation!
        val started = directAudioRecorder?.startRecording() ?: false
        if (!started) {
            val langTag = _uiState.value.language.code
            speechRecognizerHelper?.startListening(langTag, forceRestart = true)
        }
    }

    fun finishListeningAndProcess() {
        if (_uiState.value.appState != AppState.COMMAND_LISTENING) return

        val wavBytes = directAudioRecorder?.stopRecording() ?: ByteArray(0)
        hapticHelper.vibrateConfirm()
        ttsHelper?.playEarcon(TtsHelper.EarconType.OK)

        _uiState.value = _uiState.value.copy(
            appState = AppState.THINKING,
            statusText = "Transcribing voice…"
        )

        viewModelScope.launch {
            var recognizedText = ""

            // 1. Primary: Groq Whisper Turbo (Ultra-fast ~200ms, understands Indian accents & Hindi)
            if (wavBytes.isNotEmpty() && wavBytes.size > 800) {
                val whisperResult = repository.transcribeGroqWhisper(wavBytes, _uiState.value.language)
                if (!whisperResult.isNullOrBlank()) {
                    recognizedText = whisperResult.trim()
                }
            }

            // 2. If nothing recognized from audio
            if (recognizedText.isBlank()) {
                val noAudioMsg = if (_uiState.value.language == SupportedLanguage.HINDI) {
                    "मुझे कोई आवाज़ सुनाई नहीं दी। कृपया बटन दबाकर अपना सवाल बोलें।"
                } else {
                    "I didn't hear anything. Please tap the center button and speak clearly."
                }
                _uiState.value = _uiState.value.copy(
                    appState = AppState.IDLE,
                    statusText = noAudioMsg
                )
                speak(noAudioMsg)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                heardText = recognizedText,
                statusText = "Processing: \"$recognizedText\""
            )

            processPrompt(recognizedText)
        }
    }

    fun onSpeechResult(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return

        // Comprehensive phonetic regex for wake word: "hey blindsight", "a blindsight", "a blind sight", "blind side", "blindsight", etc.
        val wakeRegex = Regex(
            "^(?:hey|a|ay|ey|hi|hello|ok|okay|the|i|हे|नमस्ते)?\\s*(?:blind\\s*sight|blind\\s*side|blind\\s*site|blindsight|blind\\s*light|line\\s*sight|ब्लाइंड\\s*साइट|ब्लाइंडसाइट)\\b",
            RegexOption.IGNORE_CASE
        )

        val match = wakeRegex.find(trimmed)
        val isWakeTriggered = match != null && match.range.first == 0
        var cleanPrompt = if (isWakeTriggered) {
            trimmed.substring(match!!.range.last + 1).trim().removePrefix(",").trim()
        } else {
            trimmed
        }

        // 1. Wake word alone: "Hey BlindSight" / "a blindsight" / "blindsight"
        if (isWakeTriggered && cleanPrompt.isEmpty()) {
            isUserCommandActive = true
            hapticHelper.vibrateConfirm()
            ttsHelper?.playEarcon(TtsHelper.EarconType.WAKE)
            _uiState.value = _uiState.value.copy(
                appState = AppState.COMMAND_LISTENING,
                statusText = "I'm listening… Go ahead, say your question."
            )
            // Automatically begin audio recording with silence auto-detection!
            directAudioRecorder?.startRecording()
            return
        }

        val promptToProcess = cleanPrompt

        // In continuous all-in-one mode, only drop if this is unprompted room noise in standby
        val shouldProcess = isWakeTriggered || isUserCommandActive || _uiState.value.appState == AppState.COMMAND_LISTENING
        if (!shouldProcess) {
            // Room noise/ambient speech - ignore quietly and continue standby listening
            return
        }
        isUserCommandActive = false

        hapticHelper.vibrateConfirm()
        _uiState.value = _uiState.value.copy(
            heardText = promptToProcess,
            appState = AppState.THINKING,
            statusText = "Processing: \"$promptToProcess\""
        )
        ttsHelper?.playEarcon(TtsHelper.EarconType.OK)

        viewModelScope.launch {
            processPrompt(promptToProcess)
        }
    }

    private suspend fun processPrompt(prompt: String) {
        val low = prompt.lowercase()

        // Stop word check
        if (low in listOf("stop", "be quiet", "quiet", "cancel", "shush")) {
            stopSpeaking()
            return
        }

        // Add user turn
        repository.addMessage(isUser = true, text = prompt)

        // 1. Check local offline command (Google Assistant Offline Clone)
        val localRes = repository.evaluateLocalCommand(prompt, _uiState.value.language)
        if (localRes != null) {
            if (localRes.newLanguage != null) {
                setLanguage(localRes.newLanguage)
            }
            repository.addMessage(isUser = false, text = localRes.reply)
            _uiState.value = _uiState.value.copy(
                appState = AppState.SPEAKING,
                statusText = localRes.reply
            )
            speak(localRes.reply)
            return
        }

        // 2. Scene description check (Robust regex so variations like "please describe the scene" work)
        val isSceneQuery = Regex(
            "\\b(describe|look ahead|look around|what('?s| is) in front|what do you see|what is around|सामने क्या है|आगे क्या है|दृश्य बताओ|देखो)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(low)

        if (isSceneQuery) {
            describeScene()
            return
        }

        // 3. Ultra-Fast Frontier AI Companion via Groq (120B parameter, sub-second latency, zero quota lockout)
        val groqReply = repository.queryGroq(prompt, lang = _uiState.value.language)
        val reply = if (!groqReply.isNullOrBlank()) {
            groqReply
        } else {
            // 4. Fallback to Gemini Multimodal / Deep QA if configured
            val geminiReply = repository.queryGemini(prompt, lang = _uiState.value.language)
            if (!geminiReply.isNullOrBlank()) {
                geminiReply
            } else {
                buildConversationalAnswer(prompt)
            }
        }

        repository.addMessage(isUser = false, text = reply)
        _uiState.value = _uiState.value.copy(
            appState = AppState.SPEAKING,
            statusText = reply
        )
        speak(reply)
    }

    private fun buildConversationalAnswer(prompt: String): String {
        val facts = repository.facts.value
        val low = prompt.lowercase().trim()
        val memPrefix = if (facts.isNotEmpty()) "According to your saved memory: ${facts.first().text}. " else ""

        // Contextual pattern fallbacks if Gemini is unreachable or rate-limited
        if (low.contains("name") || low.contains("who are you") || low.contains("who made you") || low.contains("kaun ho")) {
            return if (_uiState.value.language == SupportedLanguage.HINDI) {
                "${memPrefix}मेरा नाम ब्लाइंडसाइट है। मैं दृष्टिबाधित उपयोगकर्ताओं के लिए बनाया गया आपका व्यक्तिगत विज़न गाइड और वॉयस साथी हूँ।"
            } else {
                "${memPrefix}I am BlindSight, your dedicated assistive navigation companion and AI voice assistant."
            }
        }
        if (low.contains("creator") || low.contains("who created") || low.contains("who made")) {
            return "BlindSight was created by Lavyansh and Agastaya Verma to empower independent mobility for visually impaired individuals."
        }
        if (low.contains("joke") || low.contains("funny") || low.contains("chutkula") || low.contains("laugh")) {
            val jokesEn = listOf(
                "Why don't scientists trust atoms? Because they make up everything!",
                "Why did the computer go to the eye doctor? Because it needed to improve its web-sight!",
                "Why do programmers prefer dark mode? Because light attracts bugs!",
                "What is an astronaut's favorite key on a computer? The space bar!"
            )
            val jokesHi = listOf(
                "संजू डॉक्टर के पास गया: डॉक्टर साहब, चाय पीते वक्त दाईं आँख में दर्द होता है। डॉक्टर: चाय पीते समय कप से चम्मच निकाल लिया करो!",
                "टीचर: संजू, न्यूटन का चौथा नियम बताओ? संजू: जब परीक्षा कठिन हो, तो नींद बहुत गहरी आती है!"
            )
            return if (_uiState.value.language == SupportedLanguage.HINDI) jokesHi.random() else jokesEn.random()
        }
        if (low.contains("hello") || low.contains("hi") || low.contains("hey") || low.contains("namaste")) {
            return if (_uiState.value.language == SupportedLanguage.HINDI) {
                "${memPrefix}नमस्ते! ब्लाइंडसाइट सक्रिय और सतर्क है। मैं आपकी क्या मदद करूँ?"
            } else {
                "${memPrefix}Hello! BlindSight is active and ready to assist with guidance, questions, or checking obstacles."
            }
        }
        if (low.contains("how are you") || low.contains("kaise ho")) {
            return if (_uiState.value.language == SupportedLanguage.HINDI) {
                "मैं बहुत अच्छा हूँ और सभी सेंसर सक्रिय हैं! आप कैसे हैं?"
            } else {
                "I'm feeling great and all navigation sensors are alert! How are you doing today?"
            }
        }
        if (low.contains("thank") || low.contains("dhanyawad") || low.contains("shukriya")) {
            return "You're very welcome! Let me know if you need any directions or help."
        }
        if (low.contains("time") || low.contains("clock") || low.contains("samay")) {
            val t = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            return "The current time is $t."
        }
        if (low.contains("battery") || low.contains("charge")) {
            return repository.getBatteryStatus()
        }
        if (low.contains("capital of india") || low.contains("bharat ki rajdhani")) {
            return "The capital of India is New Delhi."
        }
        if (low.contains("capital of france")) {
            return "The capital of France is Paris."
        }
        if (low.contains("capital of usa") || low.contains("capital of america") || low.contains("capital of the united states")) {
            return "The capital of the United States is Washington, D.C."
        }
        if (low.contains("speed of light")) {
            return "The speed of light in a vacuum is approximately 299,792 kilometers per second."
        }
        if (low.contains("largest planet")) {
            return "Jupiter is the largest planet in our solar system."
        }

        return when (_uiState.value.language) {
            SupportedLanguage.HINDI ->
                "${memPrefix}यह बहुत अच्छा विचार है! मैं हर कदम पर आपकी मदद और सुरक्षा के लिए तैयार हूँ। आप मुझसे समय, बैटरी, गणित, या आगे देखने के लिए कह सकते हैं।"
            SupportedLanguage.SPANISH ->
                "${memPrefix}¡Buena pregunta! Estoy aquí contigo para ayudarte a navegar, calcular números o responder tus dudas."
            SupportedLanguage.FRENCH ->
                "${memPrefix}Excellente question ! Je suis là pour vous aider à vous orienter, calculer ou répondre à vos questions."
            else ->
                "${memPrefix}That is a thoughtful question! I am right here walking with you. You can ask me to calculate, check your battery or time, tell a joke, or say 'describe the scene' anytime."
        }
    }

    fun describeScene() {
        hapticHelper.vibrateConfirm()
        ttsHelper?.playEarcon(TtsHelper.EarconType.WAKE)
        _uiState.value = _uiState.value.copy(
            isCameraScanning = true,
            statusText = "Analyzing camera scene…"
        )

        viewModelScope.launch {
            // Check for fresh frame from hardware glasses
            val hwBytes = repository.fetchLatestHardwareFrame()
            if (hwBytes != null && hwBytes.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(latestFrameBytes = hwBytes)
            }

            val description = repository.generateSceneDescription(_uiState.value.language)
            _uiState.value = _uiState.value.copy(
                isCameraScanning = false,
                appState = AppState.SPEAKING,
                statusText = description
            )
            repository.addMessage(isUser = false, text = description)
            speak(description)
        }
    }

    fun setLanguage(lang: SupportedLanguage) {
        _uiState.value = _uiState.value.copy(language = lang)
        ttsHelper?.setLanguage(lang)
        hapticHelper.vibrateConfirm()
        speak(lang.confirmation)
    }


    fun setTextSize(multiplier: Float) {
        _uiState.value = _uiState.value.copy(textSizeMultiplier = multiplier)
        hapticHelper.vibrateTap()
        val label = when (multiplier) {
            1.2f -> "Large text"
            1.45f -> "Extra large text"
            else -> "Normal text"
        }
        speak("Text size: $label.")
    }

    fun toggleHighContrastYellow() {
        val newVal = !_uiState.value.highContrastYellow
        _uiState.value = _uiState.value.copy(highContrastYellow = newVal)
        hapticHelper.vibrateConfirm()
        val status = if (newVal) "High contrast yellow-on-black mode enabled." else "Standard amber theme restored."
        speak(status)
    }

    fun updateSensorDistance(leftCm: Int, centerCm: Int, rightCm: Int) {
        val tel = repository.updateSensors(leftCm, centerCm, rightCm)
        when (tel.alert) {
            ReflexAlert.STOP -> {
                hapticHelper.vibrateAlert()
                ttsHelper?.playEarcon(TtsHelper.EarconType.OBSTACLE_CRITICAL)
            }
            ReflexAlert.STEER_LEFT, ReflexAlert.STEER_RIGHT -> {
                hapticHelper.vibrateAlert()
            }
            ReflexAlert.CAREFUL -> {
                hapticHelper.vibrateTap()
            }
            ReflexAlert.CLEAR -> {}
        }
    }

    fun toggleAutoSweep() {
        val next = !_uiState.value.isAutoSweeping
        _uiState.value = _uiState.value.copy(isAutoSweeping = next)
        hapticHelper.vibrateConfirm()

        if (next) {
            speak("Auto reflex sweep enabled. Simulating sensor distance variations.")
            startAutoSweepLoop()
        } else {
            autoSweepJob?.cancel()
            speak("Auto sweep disabled.")
        }
    }

    private fun startAutoSweepLoop() {
        autoSweepJob?.cancel()
        autoSweepJob = viewModelScope.launch {
            var step = 0
            while (_uiState.value.isAutoSweeping) {
                delay(2200)
                step++
                when (step % 5) {
                    0 -> updateSensorDistance(150, 180, 200)
                    1 -> updateSensorDistance(80, 95, 110)
                    2 -> updateSensorDistance(35, 60, 120)
                    3 -> updateSensorDistance(18, 15, 22)
                    4 -> updateSensorDistance(120, 40, 30)
                }
            }
        }
    }

    fun addMemoryFact(text: String) {
        val result = repository.addFact(text)
        hapticHelper.vibrateConfirm()
        speak(result)
    }

    fun removeMemoryFact(query: String) {
        val result = repository.forgetFact(query)
        hapticHelper.vibrateTap()
        speak(result)
    }

    fun clearAllMemories() {
        val result = repository.clearAllFacts()
        hapticHelper.vibrateAlert()
        speak(result)
    }

    fun clearConversationHistory() {
        val result = repository.clearHistory()
        hapticHelper.vibrateTap()
        speak(result)
    }

    fun readHelpCommands() {
        hapticHelper.vibrateConfirm()
        val help = "BlindSight Voice Commands: 1. Tap the giant mic to ask questions. 2. Say 'what time is it' or 'battery level'. 3. Say 'turn on flashlight'. 4. Say 'describe' to inspect the surroundings. 5. Say 'remember' followed by an item location. 6. Say 'stop' anytime to silence audio."
        speak(help)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        _uiState.value = _uiState.value.copy(
            appState = AppState.SPEAKING,
            statusText = text
        )
        speechRecognizerHelper?.pauseListening()
        ttsHelper?.speak(text) {
            _uiState.value = _uiState.value.copy(
                appState = AppState.IDLE,
                statusText = "Listening for \"Hey BlindSight\" or tap the center button."
            )
            onDone?.invoke()
            speechRecognizerHelper?.resumeListening(_uiState.value.language.code)
        }
    }

    fun stopSpeaking() {
        ttsHelper?.stop()
        hapticHelper.vibrateTap()
        _uiState.value = _uiState.value.copy(
            appState = AppState.IDLE,
            statusText = "Stopped. Listening for \"Hey BlindSight\"."
        )
        speechRecognizerHelper?.resumeListening(_uiState.value.language.code)
    }

    override fun onCleared() {
        super.onCleared()
        autoSweepJob?.cancel()
        ttsHelper?.shutdown()
        speechRecognizerHelper?.destroy()
    }
}
