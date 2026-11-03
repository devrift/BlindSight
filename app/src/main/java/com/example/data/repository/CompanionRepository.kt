package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryFact
import com.example.data.model.ReflexAlert
import com.example.data.model.ReflexTelemetry
import com.example.data.model.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CompanionRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("blindsight_app_prefs", Context.MODE_PRIVATE)

    private val _facts = MutableStateFlow<List<MemoryFact>>(emptyList())
    val facts: StateFlow<List<MemoryFact>> = _facts.asStateFlow()

    private val _history = MutableStateFlow<List<ChatMessage>>(emptyList())
    val history: StateFlow<List<ChatMessage>> = _history.asStateFlow()

    private val _telemetry = MutableStateFlow(ReflexTelemetry())
    val telemetry: StateFlow<ReflexTelemetry> = _telemetry.asStateFlow()

    init {
        loadFacts()
        loadHistory()
    }

    // --- Memory Facts ---
    private fun loadFacts() {
        val raw = prefs.getString("saved_facts", "[]") ?: "[]"
        try {
            val jsonArr = JSONArray(raw)
            val list = mutableListOf<MemoryFact>()
            for (i in 0 until jsonArr.length()) {
                val item = jsonArr.optJSONObject(i)
                if (item != null) {
                    list.add(
                        MemoryFact(
                            id = item.optString("id", "${System.currentTimeMillis()}_$i"),
                            text = item.optString("text", ""),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                } else {
                    val str = jsonArr.optString(i)
                    if (str.isNotBlank()) {
                        list.add(MemoryFact(text = str))
                    }
                }
            }
            _facts.value = list
        } catch (e: Exception) {
            _facts.value = emptyList()
        }
    }

    private fun saveFacts(list: List<MemoryFact>) {
        val jsonArr = JSONArray()
        list.takeLast(20).forEach { fact ->
            val obj = JSONObject()
            obj.put("id", fact.id)
            obj.put("text", fact.text)
            obj.put("timestamp", fact.timestamp)
            jsonArr.put(obj)
        }
        prefs.edit().putString("saved_facts", jsonArr.toString()).apply()
        _facts.value = list.takeLast(20)
    }

    fun addFact(text: String): String {
        val clean = text.trim().take(200)
        if (clean.isBlank()) return "Please state the fact to remember, for example: remember that my front door has two steps."
        val current = _facts.value.filter { !it.text.equals(clean, ignoreCase = true) }.toMutableList()
        current.add(MemoryFact(text = clean))
        saveFacts(current)
        return "Remembered: $clean"
    }

    fun forgetFact(query: String): String {
        val q = query.trim().lowercase()
        val current = _facts.value.toMutableList()
        val index = current.indexOfLast { it.text.lowercase().contains(q) }
        return if (index != -1) {
            val removed = current.removeAt(index)
            saveFacts(current)
            "Forgotten: ${removed.text}"
        } else {
            "I could not find \"$query\" in my memory."
        }
    }

    fun clearAllFacts(): String {
        val count = _facts.value.size
        saveFacts(emptyList())
        return "I have cleared all $count facts from memory."
    }

    // --- Chat History ---
    private fun loadHistory() {
        val raw = prefs.getString("chat_history", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = obj.getString("id"),
                        isUser = obj.getBoolean("isUser"),
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    fun addMessage(isUser: Boolean, text: String) {
        val current = _history.value.toMutableList()
        val msg = ChatMessage(isUser = isUser, text = text)
        current.add(msg)
        if (current.size > 24) current.removeAt(0)
        _history.value = current

        val arr = JSONArray()
        current.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("isUser", m.isUser)
            obj.put("text", m.text)
            obj.put("timestamp", m.timestamp)
            arr.put(obj)
        }
        prefs.edit().putString("chat_history", arr.toString()).apply()
    }

    fun clearHistory(): String {
        prefs.edit().remove("chat_history").apply()
        _history.value = emptyList()
        return "Conversation cleared. Memory facts are still preserved."
    }

    // --- Ultrasonic Reflex Subsystem ---
    fun updateSensors(leftCm: Int, centerCm: Int, rightCm: Int): ReflexTelemetry {
        val min = minOf(leftCm, centerCm, rightCm)
        val alert = when {
            min < 25 -> ReflexAlert.STOP
            min <= 45 -> if (leftCm < rightCm) ReflexAlert.STEER_RIGHT else ReflexAlert.STEER_LEFT
            min < 100 -> ReflexAlert.CAREFUL
            else -> ReflexAlert.CLEAR
        }
        val tel = ReflexTelemetry(
            leftCm = leftCm,
            centerCm = centerCm,
            rightCm = rightCm,
            alert = alert
        )
        _telemetry.value = tel
        return tel
    }

    // --- Offline Command Evaluation ---
    fun evaluateLocalCommand(
        prompt: String,
        currentLang: SupportedLanguage
    ): LocalCommandResult? {
        val low = prompt.trim().lowercase()

        if (low.startsWith("remember ")) {
            val fact = prompt.trim().substring("remember ".length)
            return LocalCommandResult(addFact(fact))
        }

        if (low in listOf("forget everything", "forget all", "forget everything.")) {
            return LocalCommandResult(clearAllFacts())
        }

        if (low.startsWith("forget ")) {
            val target = prompt.trim().substring("forget ".length)
            return LocalCommandResult(forgetFact(target))
        }

        if (low.replace(Regex("[?!.]"), "").trim() in listOf("what do you remember", "list memories", "your memory", "what's in memory")) {
            val factsList = _facts.value
            return if (factsList.isEmpty()) {
                LocalCommandResult("I don't remember anything yet. Say 'remember' followed by any fact to teach me.")
            } else {
                LocalCommandResult("I remember: " + factsList.joinToString("; ") { it.text } + ".")
            }
        }

        if (low.replace(Regex("[?!.]"), "").trim() in listOf("clear chat", "start over", "new chat", "reset")) {
            return LocalCommandResult(clearHistory())
        }

        // Language switch
        var targetLang: String? = null
        if (low.startsWith("switch language to ")) {
            targetLang = low.substring("switch language to ".length).replace(Regex("[.!?]"), "").trim()
        } else if (low.startsWith("language ")) {
            targetLang = low.substring("language ".length).replace(Regex("[.!?]"), "").trim()
        }
        if (targetLang != null) {
            val match = SupportedLanguage.entries.find {
                it.name.equals(targetLang, ignoreCase = true) ||
                it.code.equals(targetLang, ignoreCase = true) ||
                it.label.lowercase().contains(targetLang.lowercase())
            }
            return if (match != null) {
                LocalCommandResult(match.confirmation, newLanguage = match)
            } else {
                val available = SupportedLanguage.entries.joinToString(", ") { it.label }
                LocalCommandResult("Language not recognized. Supported languages are: $available.")
            }
        }

        // Status report
        if (low.replace(Regex("[?!.]"), "").trim() in listOf("read status", "status", "system status", "status report")) {
            val tel = _telemetry.value
            val statusText = "System Status: Voice recognition active. Speech active. Reflex sensors report: ${tel.alert.description}. Memory has ${_facts.value.size} facts stored."
            return LocalCommandResult(statusText)
        }

        // Help
        if (low.replace(Regex("[?!.]"), "").trim() in listOf("help", "commands", "what can i say", "instructions")) {
            val help = "Say 'Hey BlindSight' followed by your question. Say 'describe' to scan what is in front of you. Say 'remember' followed by a fact. Say 'what do you remember' to list facts. Say 'read status' for reflex sensors. Say 'stop' to silence."
            return LocalCommandResult(help)
        }

        return null
    }

    // --- Honest vision fallback ---
    // HONESTY CONTRACT: never invent a scene. Without a connected vision AI
    // the only safe answer is to say so, in the user's language.
    fun generateSceneDescription(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.HINDI ->
                "चित्र लिया गया, पर दृष्टि AI जुड़ा नहीं है, इसलिए मैं अनुमान नहीं लगाऊँगा। Offline कमांड के लिए help बोलें।"
            SupportedLanguage.SPANISH ->
                "Capturé la imagen, pero la IA de visión no está conectada; no adivinaré tu entorno. Di help para comandos offline."
            SupportedLanguage.FRENCH ->
                "Image capturée, mais l'IA visuelle n'est pas connectée ; je ne devinerai pas votre environnement. Dites help."
            SupportedLanguage.BENGALI ->
                "ছবি নেওয়া হয়েছে, কিন্তু ভিশন AI সংযুক্ত নয়, তাই আমি অনুমান করব না। offline কমান্ডের জন্য help বলুন।"
            SupportedLanguage.TAMIL ->
                "படம் பிடிக்கப்பட்டது, ஆனால் விஷன் AI இணைக்கப்படவில்லை; நான் ஊகிக்க மாட்டேன். help சொல்லவும்."
            SupportedLanguage.TELUGU ->
                "చిత్రం తీసుకోబడింది, కానీ విజన్ AI కనెక్ట్ కాలేదు; నేను ఊహించను. help చెప్పండి."
            else ->
                "I captured the frame, but the vision AI is not connected on this device, so I will not guess your surroundings. Connect the BlindSight gateway for real scene answers, or say help for offline commands."
        }
    }
}

data class LocalCommandResult(
    val reply: String,
    val newLanguage: SupportedLanguage? = null
)
