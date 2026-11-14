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
    val listenMode: ListenMode = ListenMode.WAKE_WORD,
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val statusText: String = "BlindSight ready. Say \"Hey BlindSight\" or tap the giant button.",
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
                speak("BlindSight companion active. Double-tap the giant center button to speak, or monitor physical obstacles on the Obstacles tab.")
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
            return
        }
        if (currentState == AppState.COMMAND_LISTENING) {
            speechRecognizerHelper?.stopListening()
            _uiState.value = _uiState.value.copy(
                appState = AppState.IDLE,
                statusText = "Listening paused."
            )
            ttsHelper?.playEarcon(TtsHelper.EarconType.TICK)
            return
        }

        ttsHelper?.playEarcon(TtsHelper.EarconType.LISTEN)
        _uiState.value = _uiState.value.copy(
            appState = AppState.COMMAND_LISTENING,
            statusText = "Listening… Go ahead, say a question or command."
        )

        val langTag = _uiState.value.language.code
        speechRecognizerHelper?.startListening(langTag)
    }

    fun onSpeechResult(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return

        hapticHelper.vibrateConfirm()
        _uiState.value = _uiState.value.copy(
            heardText = trimmed,
            appState = AppState.THINKING,
            statusText = "Processing: \"$trimmed\""
        )
        ttsHelper?.playEarcon(TtsHelper.EarconType.OK)

        viewModelScope.launch {
            processPrompt(trimmed)
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
        val memPrefix = if (facts.isNotEmpty()) "Relying on your saved memory that ${facts.first().text}. " else ""
        return when (_uiState.value.language) {
            SupportedLanguage.HINDI ->
                "मैंने सुना: \"$prompt\"। ${if (facts.isNotEmpty()) "मुझे याद है कि " + facts.first().text + "। " else ""}मैं आपकी सुरक्षित यात्रा के लिए हमेशा तैयार हूँ।"
            SupportedLanguage.SPANISH ->
                "Entendido: \"$prompt\". ${if (facts.isNotEmpty()) "Recordando que " + facts.first().text + ". " else ""}Estoy aquí para guiarte en todo momento."
            SupportedLanguage.FRENCH ->
                "Bien reçu : \"$prompt\". ${if (facts.isNotEmpty()) "En gardant en mémoire que " + facts.first().text + ". " else ""}Je suis là pour vous accompagner."
            else ->
                "${memPrefix}I hear you ask: \"$prompt\". Path forward is monitored and I am ready to guide or describe anything you need."
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

    fun setListenMode(mode: ListenMode) {
        _uiState.value = _uiState.value.copy(listenMode = mode)
        hapticHelper.vibrateTap()
        val msg = "Listening mode set to ${mode.displayName}."
        speak(msg)
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

    fun speak(text: String) {
        _uiState.value = _uiState.value.copy(
            appState = AppState.SPEAKING,
            statusText = text
        )
        ttsHelper?.speak(text) {
            _uiState.value = _uiState.value.copy(
                appState = AppState.IDLE,
                statusText = "Ready"
            )
        }
    }

    fun stopSpeaking() {
        ttsHelper?.stop()
        speechRecognizerHelper?.stopListening()
        hapticHelper.vibrateTap()
        _uiState.value = _uiState.value.copy(
            appState = AppState.IDLE,
            statusText = "Stopped."
        )
    }

    override fun onCleared() {
        super.onCleared()
        autoSweepJob?.cancel()
        ttsHelper?.shutdown()
        speechRecognizerHelper?.destroy()
    }
}
