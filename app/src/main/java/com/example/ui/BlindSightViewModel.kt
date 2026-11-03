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

    val facts: StateFlow<List<MemoryFact>> = repository.facts
    val history: StateFlow<List<ChatMessage>> = repository.history
    val telemetry: StateFlow<ReflexTelemetry> = repository.telemetry

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var autoSweepJob: Job? = null

    init {
        ttsHelper = TtsHelper(application) { ready ->
            if (ready) {
                // Initial welcoming announcement for blind users
                speak("BlindSight companion active. Double-tap the giant center button to speak, or monitor physical obstacles on the Obstacles tab.")
            }
        }
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

        // Check local command
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

        // Scene description check
        if (low in listOf("describe", "describe the scene", "what is around me", "what's around me", "what is in front of me", "look for me")) {
            describeScene()
            return
        }

        // General AI assistance
        val reply = buildConversationalAnswer(prompt)
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
                "मैं समझ गया। ${if (facts.isNotEmpty()) "मुझे याद है कि " + facts.first().text + "। " else ""}मैं आपकी सुरक्षित यात्रा के लिए हमेशा तैयार हूँ।"
            SupportedLanguage.SPANISH ->
                "Entendido. ${if (facts.isNotEmpty()) "Recordando que " + facts.first().text + ". " else ""}Estoy aquí para guiarte en todo momento."
            SupportedLanguage.FRENCH ->
                "Bien reçu. ${if (facts.isNotEmpty()) "En gardant en mémoire que " + facts.first().text + ". " else ""}Je suis là pour vous accompagner."
            else ->
                "${memPrefix}I hear you clearly. Path forward is monitored and I am ready to guide or describe anything you need."
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
            delay(1200) // Realistic perception inference delay
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
        speak("Text size set to $label.")
    }

    fun toggleHighContrastYellow() {
        val next = !_uiState.value.highContrastYellow
        _uiState.value = _uiState.value.copy(highContrastYellow = next)
        hapticHelper.vibrateTap()
        speak(if (next) "High contrast yellow-on-black mode enabled." else "Standard amber high contrast mode enabled.")
    }

    fun updateSensorDistance(leftCm: Int, centerCm: Int, rightCm: Int) {
        val tel = repository.updateSensors(leftCm, centerCm, rightCm)
        handleReflexHapticsAndAudio(tel)
    }

    private fun handleReflexHapticsAndAudio(tel: ReflexTelemetry) {
        when (tel.alert) {
            ReflexAlert.STOP -> {
                hapticHelper.vibrateCriticalStop()
                ttsHelper?.playEarcon(TtsHelper.EarconType.OBSTACLE_CRITICAL)
                speak("Critical obstacle! Stop immediately.")
            }
            ReflexAlert.STEER_LEFT -> {
                hapticHelper.vibrateWarning()
                speak("Obstacle on right. Steer left.")
            }
            ReflexAlert.STEER_RIGHT -> {
                hapticHelper.vibrateWarning()
                speak("Obstacle on left. Steer right.")
            }
            ReflexAlert.CAREFUL -> {
                hapticHelper.vibrateTap()
                speak("Obstacle ahead. Proceed carefully.")
            }
            ReflexAlert.CLEAR -> {
                // Clear state
            }
        }
    }

    fun toggleAutoSweep() {
        val next = !_uiState.value.isAutoSweeping
        _uiState.value = _uiState.value.copy(isAutoSweeping = next)
        hapticHelper.vibrateTap()

        if (next) {
            speak("Obstacle simulation radar active. Moving objects simulated.")
            autoSweepJob = viewModelScope.launch {
                val scenarios = listOf(
                    Triple(160, 180, 170), // Clear
                    Triple(70, 85, 75),   // Careful
                    Triple(30, 60, 140),  // Steer right
                    Triple(18, 22, 24),   // Stop
                    Triple(140, 50, 28),  // Steer left
                    Triple(190, 210, 180) // Clear
                )
                var index = 0
                while (_uiState.value.isAutoSweeping) {
                    val (l, c, r) = scenarios[index % scenarios.size]
                    updateSensorDistance(l, c, r)
                    index++
                    delay(3500)
                }
            }
        } else {
            autoSweepJob?.cancel()
            autoSweepJob = null
            updateSensorDistance(150, 180, 160)
            speak("Obstacle simulation stopped.")
        }
    }

    fun addManualFact(fact: String) {
        val reply = repository.addFact(fact)
        hapticHelper.vibrateConfirm()
        speak(reply)
    }

    fun forgetFact(factText: String) {
        val reply = repository.forgetFact(factText)
        hapticHelper.vibrateTap()
        speak(reply)
    }

    fun clearAllFacts() {
        val reply = repository.clearAllFacts()
        hapticHelper.vibrateTap()
        speak(reply)
    }

    fun clearConversationHistory() {
        val reply = repository.clearHistory()
        hapticHelper.vibrateTap()
        speak(reply)
    }

    fun speak(text: String) {
        ttsHelper?.speak(text)
    }

    fun stopSpeaking() {
        ttsHelper?.stop()
        _uiState.value = _uiState.value.copy(
            appState = AppState.IDLE,
            statusText = "Stopped."
        )
        hapticHelper.vibrateTap()
    }

    override fun onCleared() {
        super.onCleared()
        autoSweepJob?.cancel()
        ttsHelper?.shutdown()
    }
}
