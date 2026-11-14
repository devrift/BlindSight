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
import com.example.service.HapticHelper
import com.example.service.SpeechRecognizerHelper
import com.example.service.TtsHelper
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
    val highContrastYellow: Boolean = false
)

class BlindSightViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompanionRepository(application)
    private val hapticHelper = HapticHelper(application)
    private var ttsHelper: TtsHelper? = null
    private var speechRecognizerHelper: SpeechRecognizerHelper? = null

    val facts: StateFlow<List<MemoryFact>> = repository.facts
    val history: StateFlow<List<ChatMessage>> = repository.history
    val telemetry: StateFlow<ReflexTelemetry> = repository.telemetry

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var autoSweepJob: Job? = null

    init {
        ttsHelper = TtsHelper(application) { ready ->
            if (ready) {
                speak("BlindSight assistant active. Say \"Hey BlindSight\" or tap the center button to speak.") {
                    speechRecognizerHelper?.startListening(_uiState.value.language.code)
                }
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
                ttsHelper?.playEarcon(TtsHelper.EarconType.ERROR)
            },
            onListeningStateChanged = { listening ->
                if (!listening && _uiState.value.appState == AppState.COMMAND_LISTENING) {
                    _uiState.value = _uiState.value.copy(appState = AppState.IDLE)
                }
            }
        )
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
            AppTab.VISION -> "Switched to Camera Vision tab."
            AppTab.MEMORIES -> "Switched to Memory Bank tab."
        }
        speak(tabDescription)
    }

    fun onGiantMicClick() {
        hapticHelper.vibrateTap()
        val currentState = _uiState.value.appState
        if (currentState == AppState.SPEAKING) {
            stopSpeaking()
            speechRecognizerHelper?.resumeListening(_uiState.value.language.code)
            return
        }

        ttsHelper?.playEarcon(TtsHelper.EarconType.LISTEN)
        _uiState.value = _uiState.value.copy(
            appState = AppState.COMMAND_LISTENING,
            statusText = "Listening… Go ahead, say your question or command."
        )

        val langTag = _uiState.value.language.code
        speechRecognizerHelper?.startListening(langTag)
    }

    fun onSpeechResult(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return
        val low = trimmed.lowercase()

        // Wake triggers for hands-free invocation
        val wakeTriggers = listOf(
            "hey blindsight", "blindsight", "ok blindsight", "okay blindsight",
            "blind sight", "hey blind sight", "ok blind sight", "okay blind sight"
        )
        var cleanPrompt = trimmed
        var isWakeTriggered = false

        for (trigger in wakeTriggers) {
            if (low.startsWith(trigger)) {
                isWakeTriggered = true
                cleanPrompt = trimmed.substring(trigger.length).trim().removePrefix(",").trim()
                break
            } else if (low == trigger) {
                isWakeTriggered = true
                cleanPrompt = ""
                break
            }
        }

        // 1. Wake word alone: "Hey BlindSight"
        if (isWakeTriggered && cleanPrompt.isEmpty()) {
            hapticHelper.vibrateConfirm()
            ttsHelper?.playEarcon(TtsHelper.EarconType.WAKE)
            _uiState.value = _uiState.value.copy(
                appState = AppState.COMMAND_LISTENING,
                statusText = "I'm listening… Go ahead, say your question."
            )
            return
        }

        val promptToProcess = if (isWakeTriggered) cleanPrompt else trimmed

        // In continuous all-in-one mode, if in standby and no wake word was spoken,
        // and user didn't tap button to command:
        if (!isWakeTriggered && _uiState.value.appState == AppState.IDLE) {
            // Room noise/ambient speech - ignore quietly and continue standby listening
            return
        }

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

        // 2. Scene description check
        if (low in listOf("describe", "describe the scene", "what is around me", "what's around me", "what is in front of me", "look for me", "what do you see", "read this label", "look ahead")) {
            describeScene()
            return
        }

        // 3. Try Gemini Multimodal / Deep QA if configured
        val geminiReply = repository.queryGemini(prompt, lang = _uiState.value.language)
        val reply = if (!geminiReply.isNullOrBlank()) {
            geminiReply
        } else {
            buildConversationalAnswer(prompt)
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

        // Contextual pattern fallbacks if Gemini is unreachable
        if (low.contains("name") || low.contains("who")) {
            return "${memPrefix}I am BlindSight, your assistive navigation companion and AI voice assistant."
        }
        if (low.contains("joke") || low.contains("funny") || low.contains("chutkula")) {
            return "Why do programmers prefer dark mode? Because light attracts bugs!"
        }
        if (low.contains("hello") || low.contains("hi") || low.contains("hey") || low.contains("namaste")) {
            return "${memPrefix}Hello! BlindSight is active and ready to assist with guidance, questions, or checking obstacles."
        }
        if (low.contains("thank")) {
            return "You're very welcome! Let me know if you need any directions or help."
        }
        if (low.contains("time") || low.contains("clock")) {
            val t = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            return "The current time is $t."
        }
        if (low.contains("battery") || low.contains("charge")) {
            return repository.getBatteryStatus()
        }

        return when (_uiState.value.language) {
            SupportedLanguage.HINDI ->
                "${memPrefix}मैं समझ गया: \"$prompt\"। आपके सभी नेविगेशन और बाधा सेंसर सक्रिय हैं। आप मुझसे समय, बैटरी, या आगे देखने के लिए कह सकते हैं।"
            SupportedLanguage.SPANISH ->
                "${memPrefix}Entendido: \"$prompt\". Tus sensores de navegación están activos. ¿En qué más te puedo ayudar?"
            SupportedLanguage.FRENCH ->
                "${memPrefix}Bien reçu : \"$prompt\". Vos capteurs de navigation sont actifs. Comment puis-je vous aider ?"
            else ->
                "${memPrefix}I understand you asked about \"$prompt\". All navigation sensors are active. Feel free to ask me for the time, battery, jokes, or say describe to inspect the path."
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
