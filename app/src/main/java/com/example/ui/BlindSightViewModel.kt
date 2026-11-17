package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppState
import com.example.data.model.CameraFrameData
import com.example.data.model.CameraSource
import com.example.data.model.ChatMessage
import com.example.data.model.ListenMode
import com.example.data.model.MemoryFact
import com.example.data.model.ReflexAlert
import com.example.data.model.ReflexTelemetry
import com.example.data.model.SupportedLanguage
import com.example.data.repository.CompanionRepository
import com.example.service.AppUpdateHelper
import com.example.service.DirectAudioRecorder
import com.example.service.HapticHelper
import com.example.service.SpeechRecognizerHelper
import com.example.service.TtsHelper
import com.example.service.UpdateDownloadState
import com.example.service.UpdateInfo
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
    val gatewayUrl: String = "https://blindsightai.vercel.app",
    val latestFrameBytes: ByteArray? = null,
    val latestFrameSource: CameraSource = CameraSource.NONE,
    val isPhoneCameraFallbackEnabled: Boolean = true,
    val isHardwareGlassesConnected: Boolean = false,
    val voicePersona: VoicePersona = VoicePersona.AUTO
)

class BlindSightViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CompanionRepository(application)
    private val hapticHelper = HapticHelper(application)
    private val updateHelper = AppUpdateHelper(application)
    private var ttsHelper: TtsHelper? = null
    private var speechRecognizerHelper: SpeechRecognizerHelper? = null
    private var directAudioRecorder: com.example.service.DirectAudioRecorder? = null

    val facts: StateFlow<List<MemoryFact>> = repository.facts
    val history: StateFlow<List<ChatMessage>> = repository.history
    val telemetry: StateFlow<ReflexTelemetry> = repository.telemetry

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateState: StateFlow<UpdateDownloadState> = _updateState.asStateFlow()

    private var autoSweepJob: Job? = null

    private var isUserCommandActive = false

    init {
        _uiState.value = _uiState.value.copy(
            isPhoneCameraFallbackEnabled = repository.isPhoneCameraFallbackEnabled()
        )

        viewModelScope.launch {
            repository.telemetry.collect { tel ->
                _uiState.value = _uiState.value.copy(
                    isHardwareGlassesConnected = tel.isHardwareConnected
                )
            }
        }

        checkForUpdates(silent = true)
        directAudioRecorder = com.example.service.DirectAudioRecorder(
            onSilenceDetected = {
                finishListeningAndProcess()
            }
        )

        ttsHelper = TtsHelper(application) { ready ->
            if (ready) {
                ttsHelper?.setGatewayUrl(repository.getGatewayUrl())
                speak("BlindSight assistant active. Say \"Hey BlindSight\" or tap the center button.")
            }
        }

        speechRecognizerHelper = SpeechRecognizerHelper(
            context = application,
            onResult = { text ->
                onSpeechResult(text)
            },
            onError = { err ->
                if (_uiState.value.appState == AppState.COMMAND_LISTENING && directAudioRecorder?.recording != true) {
                    _uiState.value = _uiState.value.copy(
                        appState = AppState.IDLE,
                        statusText = err
                    )
                }
                if (err.contains("permission", ignoreCase = true) || err.contains("Audio recording", ignoreCase = true)) {
                    ttsHelper?.playEarcon(TtsHelper.EarconType.ERROR)
                }
            },
            onListeningStateChanged = { listening ->
                // Do not kill COMMAND_LISTENING here; DirectAudioRecorder and VAD manage recording duration
            }
        ).apply {
            isContinuousMode = true
        }

        // Automatically start listening for "Hey BlindSight" wake word on app launch!
        if (hasMicrophonePermission()) {
            speechRecognizerHelper?.startListening(_uiState.value.language.code)
        }
    }

    fun hasMicrophonePermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun onMicrophonePermissionGranted() {
        speechRecognizerHelper?.onPermissionGranted(_uiState.value.language.code)
        speechRecognizerHelper?.startListening(_uiState.value.language.code, forceRestart = true)
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

    fun checkForUpdates(silent: Boolean = false) {
        if (!silent) {
            _updateState.value = UpdateDownloadState.Checking
        }
        updateHelper.checkForUpdate(
            onResult = { info ->
                if (info != null) {
                    _updateState.value = UpdateDownloadState.Available(info)
                    hapticHelper.vibrateConfirm()
                    if (!silent) {
                        speak("Update version ${info.versionName} is ready to install.")
                    }
                } else if (!silent) {
                    _updateState.value = UpdateDownloadState.Idle
                    speak("Your BlindSight app is already on the newest version.")
                }
            },
            onError = { err ->
                if (!silent) {
                    _updateState.value = UpdateDownloadState.Error(err)
                    speak("Could not reach update server.")
                }
            }
        )
    }

    fun startUpdateDownload(info: UpdateInfo) {
        _updateState.value = UpdateDownloadState.Downloading(0)
        hapticHelper.vibrateConfirm()
        speak("Downloading BlindSight update...")
        updateHelper.downloadAndInstall(
            info = info,
            onProgress = { percent ->
                _updateState.value = UpdateDownloadState.Downloading(percent)
            },
            onError = { err ->
                _updateState.value = UpdateDownloadState.Error(err)
                speak("Update download failed: $err")
            }
        )
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateDownloadState.Idle
    }

    fun refreshLatestHardwareFrame() {
        viewModelScope.launch {
            val frameData = repository.captureFreshFrameWithSource()
            if (frameData.bytes != null && frameData.bytes.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    latestFrameBytes = frameData.bytes,
                    latestFrameSource = frameData.source
                )
            }
        }
    }

    fun onTripleTapTriggered() {
        hapticHelper.vibrateAlert()
        onGiantMicClick()
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

        // Pause on-device SpeechRecognizer so DirectAudioRecorder gets clean, exclusive mic access
        speechRecognizerHelper?.pauseListening()

        // 100% In-App recording directly to Groq Whisper
        val started = directAudioRecorder?.startRecording() ?: false
        if (!started) {
            val langTag = _uiState.value.language.code
            speechRecognizerHelper?.resumeListening(langTag)
        }
    }

    fun detectSpokenLanguage(text: String): SupportedLanguage {
        // 1. Script checks for the 4 strictly locked languages
        if (text.any { it in '\u0A00'..'\u0A7F' }) {
            return SupportedLanguage.PUNJABI
        }
        if (text.any { it in '\u0980'..'\u09FF' }) {
            return SupportedLanguage.BENGALI
        }
        if (text.any { it in '\u0900'..'\u097F' }) {
            return SupportedLanguage.HINDI
        }

        // 2. Phonetic / Transliterated keyword matching
        val low = text.lowercase()
        val punjabiKeywords = Regex(
            "\\b(kivein|kiddan|kithe|daso|dasso|aaho|hanji|sat sri akal|tusi|tuhada|saamne|agay|chalo|veere|paji|hor)\\b",
            RegexOption.IGNORE_CASE
        )
        if (punjabiKeywords.containsMatchIn(low)) {
            return SupportedLanguage.PUNJABI
        }

        val bengaliKeywords = Regex(
            "\\b(kemon|kothay|bolo|shunun|dorkar|amake|amar|thik|dhonnobad|namaskar|samne|aage)\\b",
            RegexOption.IGNORE_CASE
        )
        if (bengaliKeywords.containsMatchIn(low)) {
            return SupportedLanguage.BENGALI
        }

        val hindiKeywords = Regex(
            "\\b(kya|kaise|kahan|kidhar|batao|dekho|mera|meri|mere|mujhe|aage|samne|hai|hain|ho|nahi|mat|chalo|rasta|seedhe|kripya|namaste|shukriya|kaun|kab|kyun|chahiye|aur|pe|par|yeh|woh|karo|bataiye|dikhaye|ruko|bhai)\\b",
            RegexOption.IGNORE_CASE
        )
        if (hindiKeywords.containsMatchIn(low)) {
            return SupportedLanguage.HINDI
        }

        return SupportedLanguage.ENGLISH
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

            // 1. Primary: Groq Whisper Turbo (Language auto-detected by Whisper)
            if (wavBytes.isNotEmpty() && wavBytes.size > 800) {
                val whisperResult = repository.transcribeGroqWhisper(wavBytes, _uiState.value.language)
                if (!whisperResult.isNullOrBlank()) {
                    recognizedText = whisperResult.trim()
                }
            }

            // 2. If nothing recognized from audio
            if (recognizedText.isBlank()) {
                val noAudioMsg = if (_uiState.value.language == SupportedLanguage.HINDI) {
                    "मुझे कोई आवाज़ सुनाई नहीं दी। कृपया फिर से बोलें।"
                } else {
                    "I didn't hear anything. Tap to speak or say 'Hey BlindSight'."
                }
                _uiState.value = _uiState.value.copy(
                    appState = AppState.IDLE,
                    statusText = noAudioMsg
                )
                speechRecognizerHelper?.resumeListening(_uiState.value.language.code)
                return@launch
            }

            // Automatically detect spoken language (Hindi / English) and adapt immediately!
            val detected = detectSpokenLanguage(recognizedText)
            if (detected != _uiState.value.language) {
                _uiState.value = _uiState.value.copy(language = detected)
                ttsHelper?.setLanguage(detected)
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
            // Pause on-device recognizer so DirectAudioRecorder gets clean mic access
            speechRecognizerHelper?.pauseListening()
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

        // Automatically detect spoken language
        val detected = detectSpokenLanguage(promptToProcess)
        if (detected != _uiState.value.language) {
            _uiState.value = _uiState.value.copy(language = detected)
            ttsHelper?.setLanguage(detected)
        }

        hapticHelper.vibrateConfirm()
        _uiState.value = _uiState.value.copy(
            heardText = promptToProcess,
            appState = AppState.THINKING,
            statusText = "Processing: \"$promptToProcess\""
        )
        // Hard mute mic during thinking to enforce atomic IDLE -> THINKING -> SPEAKING mutex
        speechRecognizerHelper?.pauseListening()
        directAudioRecorder?.stopRecording()
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

        // 2. Vision / Camera query check: If user asks what they are looking at, what's in front, what they're holding,
        // whether it's safe to walk, or if they are currently on the Vision / Camera tab:
        val isVisualQuery = Regex(
            "\\b(describe|look|see|holding|in front|ahead|around|what is this|what's this|what are these|what is that|what's that|read|sign|color|colour|door|obstacle|camera|view|walk|safe|clear|path|guide|सामने|आगे|देखो|दिख|पढ़ो|पहचानो|क्या है|रास्ता|चलूं|सुरक्षित)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(low) || (_uiState.value.selectedTab == AppTab.VISION)

        if (isVisualQuery) {
            describeScene(customPrompt = prompt)
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

        // Process actions and deliver response
        handleAiResponseWithActions(reply)
    }

    fun triggerConfirmEmotion() {
        hapticHelper.vibrateConfirm()
        _uiState.value = _uiState.value.copy(appState = AppState.CONFIRMING)
        viewModelScope.launch {
            delay(1200L)
            if (_uiState.value.appState == AppState.CONFIRMING) {
                _uiState.value = _uiState.value.copy(appState = AppState.IDLE)
            }
        }
    }

    fun triggerCautionEmotion() {
        hapticHelper.vibrateWarning()
        _uiState.value = _uiState.value.copy(appState = AppState.CAUTION)
    }

    fun cycleLanguage() {
        val all = SupportedLanguage.entries
        val nextIdx = (all.indexOf(_uiState.value.language) + 1) % all.size
        setLanguage(all[nextIdx])
    }

    fun onTactileSingleTap() {
        val currentState = _uiState.value.appState
        if (currentState == AppState.SPEAKING) {
            stopSpeaking()
            return
        }
        if (currentState == AppState.COMMAND_LISTENING) {
            finishListeningAndProcess()
            return
        }
        if (currentState == AppState.THINKING) {
            return
        }
        onGiantMicClick()
    }

    fun readVisibleText() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                appState = AppState.THINKING,
                statusText = "Reading text in view…"
            )
            hapticHelper.vibrateConfirm()
            ttsHelper?.playEarcon(TtsHelper.EarconType.WAKE)

            val frameData = repository.captureFreshFrameWithSource()
            val imageBase64 = frameData.bytes?.let { bytes ->
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
            val prompt = "Read all text, signs, labels, or numbers visible in this view clearly. If no text is visible, say 'No text visible'."
            val reply = repository.queryGemini(prompt, imageBase64, _uiState.value.language)
                ?: repository.queryGroq(prompt, _uiState.value.language)
                ?: "No text detected."

            handleAiResponseWithActions(reply)
        }
    }

    private fun handleAiResponseWithActions(rawReply: String) {
        var text = rawReply

        // 1. Navigation Action: [ACTION:NAVIGATE <destination>] or NAVIGATE_TO: <destination>
        val navRegex = Regex("\\[ACTION:NAVIGATE\\s+([^\\]]+)\\]|NAVIGATE_TO:\\s*(.+)", RegexOption.IGNORE_CASE)
        val navMatch = navRegex.find(rawReply)
        if (navMatch != null) {
            val destination = (navMatch.groupValues[1].ifBlank { navMatch.groupValues[2] })
                .replace(Regex("[*._#`\n\r]"), "").trim()
            text = text.replace(navMatch.value, "").trim()
            repository.openGoogleMaps(destination)
            triggerConfirmEmotion()
            val navNotice = when (_uiState.value.language) {
                SupportedLanguage.HINDI -> "$destination के लिए गूगल मैप्स पर पैदल नेविगेशन शुरू हो रहा है।"
                SupportedLanguage.PUNJABI -> "$destination ਲਈ ਗੂਗਲ ਮੈਪਸ 'ਤੇ ਪੈਦਲ ਨੇਵੀਗੇਸ਼ਨ ਸ਼ੁਰੂ ਹੋ ਰਹੀ ਹੈ।"
                SupportedLanguage.BENGALI -> "$destination এর জন্য গুগল ম্যাপে হাঁটার নেভিগেশন শুরু হচ্ছে।"
                else -> "Starting walking navigation to $destination on Google Maps."
            }
            val combined = if (text.isNotBlank()) "$text $navNotice" else navNotice
            deliverSpokenReply(combined)
            return
        }

        // 2. Flashlight Action: [ACTION:TORCH ON] or [ACTION:TORCH OFF]
        val torchOnRegex = Regex("\\[ACTION:TORCH\\s+ON\\]", RegexOption.IGNORE_CASE)
        val torchOffRegex = Regex("\\[ACTION:TORCH\\s+OFF\\]", RegexOption.IGNORE_CASE)
        if (torchOnRegex.containsMatchIn(rawReply)) {
            text = text.replace(torchOnRegex, "").trim()
            repository.setTorch(true)
            triggerConfirmEmotion()
        } else if (torchOffRegex.containsMatchIn(rawReply)) {
            text = text.replace(torchOffRegex, "").trim()
            repository.setTorch(false)
            triggerConfirmEmotion()
        }

        // 3. Text Reading Action: [ACTION:READ_TEXT]
        val readTextRegex = Regex("\\[ACTION:READ_TEXT\\]", RegexOption.IGNORE_CASE)
        if (readTextRegex.containsMatchIn(rawReply)) {
            text = text.replace(readTextRegex, "").trim()
            readVisibleText()
            return
        }

        // 4. Surroundings / Describe Scene Action: [ACTION:DESCRIBE_SCENE]
        val describeRegex = Regex("\\[ACTION:DESCRIBE_SCENE\\]", RegexOption.IGNORE_CASE)
        if (describeRegex.containsMatchIn(rawReply)) {
            text = text.replace(describeRegex, "").trim()
            describeScene()
            return
        }

        // Strip any other stray [ACTION:...] tags
        text = text.replace(Regex("\\[ACTION:.*?\\]"), "").trim()
        if (text.isBlank()) {
            text = when (_uiState.value.language) {
                SupportedLanguage.HINDI -> "रास्ता सीधा खुला है। सुरक्षित आगे बढ़ें।"
                SupportedLanguage.PUNJABI -> "ਰਾਸਤਾ ਸਿੱਧਾ ਖੁੱਲ੍ਹਾ ਹੈ। ਸੁਰੱਖਿਅਤ ਅੱਗੇ ਵਧੋ।"
                SupportedLanguage.BENGALI -> "সামনের পথ খোলা আছে। সাবধানে এগিয়ে চলুন।"
                else -> "Directly ahead is clear. Proceed with confidence."
            }
        }

        deliverSpokenReply(text)
    }

    private fun deliverSpokenReply(text: String) {
        repository.addMessage(isUser = false, text = text)
        _uiState.value = _uiState.value.copy(
            appState = AppState.SPEAKING,
            statusText = text
        )
        speak(text)
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

        return when (_uiState.value.language) {
            SupportedLanguage.HINDI ->
                "${memPrefix}यह बहुत अच्छा विचार है! मैं हर कदम पर आपकी मदद और सुरक्षा के लिए तैयार हूँ। आप मुझसे समय, बैटरी, या आगे देखने के लिए कह सकते हैं।"
            SupportedLanguage.PUNJABI ->
                "${memPrefix}ਇਹ ਬਹੁਤ ਵਧੀਆ ਸਵਾਲ ਹੈ! ਮੈਂ ਹਰ ਕਦਮ 'ਤੇ ਤੁਹਾਡੀ ਮਦਦ ਲਈ ਤਿਆਰ ਹਾਂ। ਤੁਸੀਂ ਮੈਨੂੰ ਰਸਤਾ, ਸਮਾਂ, ਬੈਟਰੀ ਜਾਂ ਸਾਹਮਣੇ ਦੇਖਣ ਲਈ ਕਹਿ ਸਕਦੇ ਹੋ।"
            SupportedLanguage.BENGALI ->
                "${memPrefix}খুব সুন্দর প্রশ্ন! আমি প্রতিটি পদক্ষেপে আপনাকে সাহায্য করার জন্য প্রস্তুত। আপনি সময়, ব্যাটারি, বা সামনের দৃশ্য বর্ণনা করতে বলতে পারেন।"
            else ->
                "${memPrefix}That is a thoughtful question! I am right here walking with you. You can ask me for directions, check your battery or time, or say 'describe the scene' anytime."
        }
    }

    fun describeScene(customPrompt: String? = null) {
        hapticHelper.vibrateConfirm()
        ttsHelper?.playEarcon(TtsHelper.EarconType.WAKE)
        val initialStatus = if (_uiState.value.isHardwareGlassesConnected) {
            if (_uiState.value.language == SupportedLanguage.HINDI) "स्मार्ट चश्मे के कैमरे से दृश्य देखा जा रहा है…" else "Scanning scene with smart glasses camera…"
        } else {
            if (_uiState.value.language == SupportedLanguage.HINDI) "मोबाइल बैक कैमरे से आगे का दृश्य देखा जा रहा है…" else "Using mobile back camera to inspect path ahead…"
        }
        _uiState.value = _uiState.value.copy(
            isCameraScanning = true,
            statusText = initialStatus
        )

        viewModelScope.launch {
            val result = repository.generateSceneDescription(
                language = _uiState.value.language,
                customPrompt = customPrompt
            )
            _uiState.value = _uiState.value.copy(
                isCameraScanning = false,
                latestFrameBytes = result.frameBytes,
                latestFrameSource = result.cameraSource,
                appState = AppState.SPEAKING,
                statusText = result.description
            )
            repository.addMessage(isUser = false, text = result.description)
            speak(result.description)
        }
    }

    fun togglePhoneCameraFallback() {
        val newVal = !_uiState.value.isPhoneCameraFallbackEnabled
        repository.setPhoneCameraFallbackEnabled(newVal)
        _uiState.value = _uiState.value.copy(isPhoneCameraFallbackEnabled = newVal)
        hapticHelper.vibrateConfirm()
        val announcement = if (newVal) {
            if (_uiState.value.language == SupportedLanguage.HINDI) {
                "मोबाइल कैमरा फॉलबैक सक्षम है। जब भी विज़न स्कैन होगा, मैं बोलकर सूचित करूंगा।"
            } else {
                "Mobile camera fallback enabled. BlindSight will announce 'Using your mobile back camera' whenever scanning."
            }
        } else {
            if (_uiState.value.language == SupportedLanguage.HINDI) {
                "मोबाइल कैमरा फॉलबैक बंद है। केवल स्मार्ट चश्मे का उपयोग होगा।"
            } else {
                "Mobile camera fallback disabled. BlindSight will only use smart glasses camera."
            }
        }
        _uiState.value = _uiState.value.copy(statusText = announcement)
        speak(announcement)
    }

    fun speakHardwareStatus() {
        hapticHelper.vibrateTap()
        val isGlasses = _uiState.value.isHardwareGlassesConnected
        val isFallback = _uiState.value.isPhoneCameraFallbackEnabled
        val lang = _uiState.value.language
        val status = if (lang == SupportedLanguage.HINDI) {
            val glasses = if (isGlasses) "स्मार्ट चश्मा कनेक्टेड है।" else "स्मार्ट चश्मा कनेक्ट नहीं है।"
            val camera = if (isGlasses) "स्मार्ट चश्मे का कैमरा सक्रिय है।" else if (isFallback) "विज़न के लिए मोबाइल का बैक कैमरा उपयोग में है।" else "मोबाइल कैमरा फॉलबैक बंद है।"
            "हार्डवेयर स्थिति: $glasses $camera अल्ट्रासोनिक रडार ${if (isGlasses) "सक्रिय है।" else "हार्डवेयर कनेक्ट नहीं है।"} माइक्रोफोन चालू है।"
        } else {
            val glasses = if (isGlasses) "Smart glasses are connected." else "Smart glasses are disconnected."
            val camera = if (isGlasses) "Using smart glasses camera." else if (isFallback) "Mobile back camera is in use with spoken privacy notice." else "Mobile camera fallback is disabled."
            "Hardware status: $glasses $camera Ultrasonic radar is ${if (isGlasses) "active." else "hardware disconnected."} Microphone is active."
        }
        _uiState.value = _uiState.value.copy(statusText = status)
        speak(status)
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

    fun updateSensorDistance(leftCm: Int, centerCm: Int, rightCm: Int, isHardware: Boolean = true) {
        val tel = repository.updateSensors(leftCm, centerCm, rightCm, isHardware = isHardware)
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
            speak("Demo walk enabled. Simulating sensor distance variations.")
            startAutoSweepLoop()
        } else {
            autoSweepJob?.cancel()
            repository.setHardwareDisconnected()
            speak("Demo walk stopped. Hardware radar is not connected.")
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
        // Hard mute mic inputs the millisecond speech begins
        speechRecognizerHelper?.pauseListening()
        directAudioRecorder?.stopRecording()

        ttsHelper?.speak(text) {
            _uiState.value = _uiState.value.copy(
                appState = AppState.IDLE,
                statusText = "Listening for \"Hey BlindSight\" or tap the center button."
            )
            onDone?.invoke()
            // 450ms post-speech guard buffer prevents capturing assistant's room echo
            viewModelScope.launch {
                delay(450L)
                if (_uiState.value.appState == AppState.IDLE) {
                    speechRecognizerHelper?.resumeListening(_uiState.value.language.code)
                }
            }
        }
    }

    fun stopSpeaking() {
        ttsHelper?.stop()
        hapticHelper.vibrateTap()
        _uiState.value = _uiState.value.copy(
            appState = AppState.IDLE,
            statusText = "Stopped. Listening for \"Hey BlindSight\"."
        )
        viewModelScope.launch {
            delay(450L)
            if (_uiState.value.appState == AppState.IDLE) {
                speechRecognizerHelper?.resumeListening(_uiState.value.language.code)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSweepJob?.cancel()
        ttsHelper?.shutdown()
        speechRecognizerHelper?.destroy()
    }
}
