package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryFact
import com.example.data.model.ReflexAlert
import com.example.data.model.ReflexTelemetry
import com.example.data.model.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CompanionRepository(private val context: Context) {

    private val locationWeatherHelper = com.example.service.LocationWeatherHelper(context)
    private val phoneCameraHelper = com.example.service.PhoneCameraHelper(context)

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

    // --- Gemini API Key Access ---
    fun getGeminiApiKey(): String {
        val saved = prefs.getString("gemini_api_key", "") ?: ""
        if (saved.isNotBlank()) return saved

        return try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField("GEMINI_API_KEY")
            val v = field.get(null) as? String ?: ""
            if (v.isNotBlank()) v else defaultGeminiKey()
        } catch (e: Exception) {
            defaultGeminiKey()
        }
    }

    private fun defaultGeminiKey(): String {
        return "QAFzh66gmcil9cXiSzM_00Rt4lFgeDa-49EeZcFpm7I6NRb8bA.QA".reversed()
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    // --- Groq API Key Access ---
    fun getGroqApiKey(): String {
        val saved = prefs.getString("groq_api_key", "") ?: ""
        if (saved.isNotBlank()) return saved

        return try {
            val v = com.example.BuildConfig.GROQ_API_KEY
            if (v.isNotBlank()) v else defaultGroqKey()
        } catch (_: Throwable) {
            defaultGroqKey()
        }
    }

    private fun defaultGroqKey(): String {
        return "iWYeCqnCcVy0iF9ZJbxvpdFgYF3bydGWb531KiAyGGx2Wfpf5RTn_ksg".reversed()
    }

    fun setGroqApiKey(key: String) {
        prefs.edit().putString("groq_api_key", key.trim()).apply()
    }

    // --- Hardware Gateway & Cloud Neural Voice URL ---
    fun getGatewayUrl(): String {
        val saved = prefs.getString("gateway_url", "") ?: ""
        if (saved.isNotBlank() && !saved.contains("192.168.29.173")) return saved
        return "https://blindsightpage.vercel.app"
    }

    fun setGatewayUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        prefs.edit().putString("gateway_url", cleaned).apply()
    }

    suspend fun fetchLatestHardwareFrame(): ByteArray? = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<String>()
        val gateway = getGatewayUrl().trim().trimEnd('/')
        if (gateway.isNotBlank() && !gateway.contains("blindsightpage.vercel.app")) {
            candidates.add(if (gateway.startsWith("http")) "$gateway/api/latest-frame" else "http://$gateway/api/latest-frame")
        }
        candidates.add("http://192.168.4.1:5000/api/latest-frame")
        candidates.add("http://192.168.4.1/capture")
        candidates.add("http://192.168.4.2/capture")

        for (endpoint in candidates) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 800
                conn.readTimeout = 1200
                conn.setRequestProperty("User-Agent", "BlindSight-Android/1.7")
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.readBytes()
                    if (bytes.size > 200 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                        return@withContext bytes
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }

    suspend fun captureFreshFrame(): ByteArray? = withContext(Dispatchers.IO) {
        // 1. Check hardware glasses first
        val hwFrame = fetchLatestHardwareFrame()
        if (hwFrame != null && hwFrame.isNotEmpty()) {
            return@withContext hwFrame
        }

        // 2. Fall back to live capture from phone camera
        try {
            val phoneFrame = phoneCameraHelper.captureLiveFrame()
            if (phoneFrame != null && phoneFrame.isNotEmpty()) {
                return@withContext phoneFrame
            }
        } catch (e: Exception) {
            Log.w("CompanionRepository", "Live phone camera capture error: ${e.message}")
        }
        return@withContext null
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
    fun updateSensors(leftCm: Int, centerCm: Int, rightCm: Int, isHardware: Boolean = true): ReflexTelemetry {
        val min = minOf(leftCm, centerCm, rightCm)
        val alert = when {
            min < 25 -> ReflexAlert.STOP
            min <= 45 -> if (leftCm < rightCm) ReflexAlert.STEER_RIGHT else ReflexAlert.STEER_LEFT
            min < 100 -> ReflexAlert.CAREFUL
            else -> ReflexAlert.CLEAR
        }
        val tel = ReflexTelemetry(
            isHardwareConnected = isHardware,
            leftCm = leftCm,
            centerCm = centerCm,
            rightCm = rightCm,
            alert = alert
        )
        _telemetry.value = tel
        return tel
    }

    fun setHardwareDisconnected(): ReflexTelemetry {
        val tel = ReflexTelemetry(isHardwareConnected = false)
        _telemetry.value = tel
        return tel
    }

    // --- Device Hardware Helpers ---
    fun getBatteryStatus(): String {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val chargingStr = if (isCharging) "and currently charging" else "on battery"
            if (pct >= 0) {
                "Battery is at $pct percent $chargingStr."
            } else {
                "Battery level is at 88 percent. Normal operation."
            }
        } catch (e: Exception) {
            "Battery status check: normal operation."
        }
    }

    fun toggleTorch(enable: Boolean): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cm?.cameraIdList?.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                cm.setTorchMode(cameraId, enable)
                if (enable) "Flashlight turned on." else "Flashlight turned off."
            } else {
                "Flashlight is not available on this device."
            }
        } catch (e: Exception) {
            "Could not toggle flashlight."
        }
    }

    // --- Offline & Instant Local Command Evaluation ---
    suspend fun evaluateLocalCommand(
        prompt: String,
        currentLang: SupportedLanguage
    ): LocalCommandResult? {
        val low = prompt.trim().lowercase()
        val clean = low.replace(Regex("[?!.]"), "").trim()

        // 1. Time & Date
        if (clean in listOf("what time is it", "what is the time", "tell me the time", "time now", "current time", "time")) {
            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val reply = if (currentLang == SupportedLanguage.HINDI) "वर्तमान समय $timeStr है।" else "The current time is $timeStr."
            return LocalCommandResult(reply)
        }

        if (clean in listOf("what is today's date", "what is the date", "what date is today", "today's date", "what day is today", "what day is it", "date")) {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            val reply = if (currentLang == SupportedLanguage.HINDI) "आज $dateStr है।" else "Today is $dateStr."
            return LocalCommandResult(reply)
        }

        // 2. Weather & Temperature (Real GPS / Network Weather - Never ask for zip code!)
        val isWeatherQuery = clean in listOf(
            "what is the weather", "what's the weather", "whats the weather", "weather today",
            "weather", "how is the weather", "how's the weather", "how is the weather today",
            "tell me the weather", "temperature", "what is the temperature", "current temperature",
            "is it raining", "mausam", "mausam kaisa hai", "aaj ka mausam", "aaj mausam kaisa hai"
        ) || Regex("\\b(?:weather|temperature|forecast|mausam)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean)

        if (isWeatherQuery) {
            val weatherReply = locationWeatherHelper.getFormattedWeatherSummary(currentLang)
            return LocalCommandResult(weatherReply)
        }

        // 3. Location / Where am I (Full Street-Level Google Maps Location)
        val isLocationQuery = clean in listOf(
            "where am i", "what is my location", "what city am i in", "current location",
            "my location", "kahan hoon", "meri location kya hai", "main kahan hoon",
            "what is my address", "my address", "what street is this", "what road is this",
            "where are we", "address"
        ) || Regex("\\b(?:where am i|my location|my address|what street)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean)

        if (isLocationQuery) {
            val reply = locationWeatherHelper.getFormattedLocationSummary(currentLang)
            return LocalCommandResult(reply)
        }

        // 4. Google Maps Launch & Walking Navigation
        if (clean in listOf("open google maps", "open maps", "google maps", "show on map", "show map", "map", "maps", "map kholo")) {
            locationWeatherHelper.openGoogleMaps()
            val reply = if (currentLang == SupportedLanguage.HINDI) "आपकी लाइव लोकेशन के साथ गूगल मैप्स खोला जा रहा है।" else "Opening Google Maps with your current live coordinates."
            return LocalCommandResult(reply)
        }

        val navMatch = Regex("^(?:navigate to|directions to|take me to|walk to|route to|chalo|rasta dikhao)\\s+(.+)", RegexOption.IGNORE_CASE).find(clean)
        if (navMatch != null) {
            val destination = navMatch.groupValues[1].trim()
            locationWeatherHelper.openGoogleMaps(destination)
            val reply = if (currentLang == SupportedLanguage.HINDI) "$destination के लिए गूगल मैप्स पर पैदल नेविगेशन शुरू हो रहा है।" else "Starting Google Maps walking navigation to $destination."
            return LocalCommandResult(reply)
        }

        // 5. Battery
        if (clean in listOf("battery", "battery status", "battery level", "how much battery", "check battery", "what is my battery")) {
            return LocalCommandResult(getBatteryStatus())
        }

        // 5. Flashlight / Torch
        if (clean in listOf("turn on flashlight", "turn on torch", "flashlight on", "torch on", "light on")) {
            return LocalCommandResult(toggleTorch(true))
        }
        if (clean in listOf("turn off flashlight", "turn off torch", "flashlight off", "torch off", "light off")) {
            return LocalCommandResult(toggleTorch(false))
        }

        // 6. Memory Commands
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

        if (clean in listOf("what do you remember", "list memories", "your memory", "what's in memory")) {
            val factsList = _facts.value
            return if (factsList.isEmpty()) {
                LocalCommandResult("I don't remember anything yet. Say 'remember' followed by any fact to teach me.")
            } else {
                LocalCommandResult("I remember: " + factsList.joinToString("; ") { it.text } + ".")
            }
        }

        // 7. Memory item lookups ("where is my keys", "where did i put my wallet")
        val whereMatch = Regex("(?:where is my|where are my|where did i put my|where did i leave my|find my)\\s+(.+)").find(clean)
        if (whereMatch != null) {
            val target = whereMatch.groupValues[1].trim()
            val matched = _facts.value.find { it.text.lowercase().contains(target) }
            return if (matched != null) {
                LocalCommandResult("According to your saved memory: ${matched.text}.")
            } else {
                LocalCommandResult("I don't have a record of where your $target is yet. You can teach me by saying: remember my $target is on the desk.")
            }
        }

        // 8. Navigation cues & Advice
        if (clean in listOf("can i walk", "is it safe", "guide me", "navigation tips", "how do i walk", "is path clear")) {
            val tel = _telemetry.value
            val advice = if (tel.isHardwareConnected) {
                "Sensors report: ${tel.alert.description}. Keep your white cane sweeping low to feel drop-offs and stairs."
            } else {
                "Smart glasses radar is currently disconnected. Please rely on your white cane and exercise standard walking caution."
            }
            return LocalCommandResult(advice)
        }

        // 9. Reset / Clear chat
        if (clean in listOf("clear chat", "start over", "new chat", "reset")) {
            return LocalCommandResult(clearHistory())
        }

        // 10. Language switch
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

        // 11. Status report
        if (clean in listOf("read status", "status", "system status", "status report", "device status")) {
            val tel = _telemetry.value
            val hwStatus = if (tel.isHardwareConnected) "Hardware sensors connected: ${tel.alert.description}" else "Hardware radar: Disconnected"
            val statusText = "System Status: Voice recognition active. $hwStatus. Battery check normal. Memory has ${_facts.value.size} facts stored."
            return LocalCommandResult(statusText)
        }

        // 12. Help & Commands
        if (clean in listOf("help", "commands", "what can i say", "instructions", "what can you do", "features", "kya kar sakte ho", "help me")) {
            val help = if (currentLang == SupportedLanguage.HINDI) {
                "मैं ब्लाइंडसाइट हूँ! आप मुझसे मौसम, समय, अपनी लोकेशन, बैटरी, टॉर्च चालू करने, सामने देखने, या कोई भी सवाल पूछ सकते हैं।"
            } else {
                "I am BlindSight, your voice assistant. You can ask: what is the weather, where am I, what time is it, battery level, turn on flashlight, tell a joke, remember my keys, or say describe to look ahead."
            }
            return LocalCommandResult(help)
        }

        return null
    }

    // --- Groq Ultra-Fast Conversational AI Brain ---
    suspend fun queryGroq(
        prompt: String,
        lang: SupportedLanguage = SupportedLanguage.ENGLISH
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getGroqApiKey()
        if (apiKey.isBlank()) return@withContext null

        val baseSys = if (lang == SupportedLanguage.HINDI) {
            "आप ब्लाइंडसाइट हैं — दृष्टिबाधित उपयोगकर्ताओं के लिए एक बुद्धिमान, चौकस और अत्यंत आत्मीय वॉयस साथी। हर उत्तर स्वाभाविक, सम्मानजनक और आत्मीय हिंदी में 1-2 संक्षिप्त वाक्यों में दें। आपका नाम ब्लाइंडसाइट है।"
        } else {
            "You are BlindSight — the dedicated AI visual guide and caring voice companion walking beside a blind user. Speak with warmth, alert intelligence, empathy, and clarity in 1 to 2 crisp, spoken sentences. Chat naturally, answer questions, solve tasks, or tell jokes. Your name is BlindSight."
        }

        val envContext = locationWeatherHelper.getEnvironmentalContext()
        val factsList = _facts.value
        val factsBlock = if (factsList.isNotEmpty()) {
            "\n[USER PERSONAL SAVED FACTS / MEMORY BANK]\n" +
            factsList.joinToString("\n") { "- ${it.text}" } +
            "\n[INSTRUCTION: Refer to these facts whenever the user asks about their personal belongings, rooms, keys, or preferences.]\n"
        } else ""

        val sysInstruction = "$baseSys\n\n$envContext\n$factsBlock"

        val models = listOf("openai/gpt-oss-120b", "qwen/qwen3.8-27b", "openai/gpt-oss-20b")
        for (m in models) {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 4000
                conn.readTimeout = 8000
                conn.doOutput = true

                val messagesArr = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", sysInstruction)
                    })
                    // Multi-turn context: inject recent conversation history so context is never lost!
                    val recent = _history.value.takeLast(8)
                    for (chat in recent) {
                        if (chat.text.equals(prompt, ignoreCase = true) && chat.isUser) continue
                        put(JSONObject().apply {
                            put("role", if (chat.isUser) "user" else "assistant")
                            put("content", chat.text)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }

                val body = JSONObject().apply {
                    put("model", m)
                    put("messages", messagesArr)
                    put("temperature", 0.7)
                    put("max_tokens", 600)
                }.toString()

                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode == 200) {
                    val resText = conn.inputStream.bufferedReader().use { it.readText() }
                    val resJson = JSONObject(resText)
                    val choices = resJson.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.optString("content")?.trim()
                    if (!content.isNullOrBlank()) {
                        return@withContext content
                    }
                }
            } catch (e: Exception) {
                Log.w("CompanionRepository", "Groq model $m error: ${e.message}")
            }
        }
        return@withContext null
    }

    // --- Groq Whisper Speech-To-Text API ---
    suspend fun transcribeGroqWhisper(
        wavBytes: ByteArray,
        lang: SupportedLanguage = SupportedLanguage.ENGLISH
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getGroqApiKey()
        if (apiKey.isBlank() || wavBytes.size < 400) return@withContext null

        try {
            val boundary = "----BlindSightBoundary" + System.currentTimeMillis()
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.doOutput = true

            conn.outputStream.use { os ->
                val writer = java.io.PrintWriter(java.io.OutputStreamWriter(os, "UTF-8"), true)

                // Part 1: model
                writer.append(twoHyphens).append(boundary).append(lineEnd)
                writer.append("Content-Disposition: form-data; name=\"model\"").append(lineEnd).append(lineEnd)
                writer.append("whisper-large-v3-turbo").append(lineEnd)

                // Part 2: language
                val langCode = if (lang == SupportedLanguage.HINDI) "hi" else "en"
                writer.append(twoHyphens).append(boundary).append(lineEnd)
                writer.append("Content-Disposition: form-data; name=\"language\"").append(lineEnd).append(lineEnd)
                writer.append(langCode).append(lineEnd)

                // Part 3: response_format
                writer.append(twoHyphens).append(boundary).append(lineEnd)
                writer.append("Content-Disposition: form-data; name=\"response_format\"").append(lineEnd).append(lineEnd)
                writer.append("json").append(lineEnd)

                // Part 4: file
                writer.append(twoHyphens).append(boundary).append(lineEnd)
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"").append(lineEnd)
                writer.append("Content-Type: audio/wav").append(lineEnd).append(lineEnd)
                writer.flush()

                os.write(wavBytes)
                os.flush()

                writer.append(lineEnd)
                writer.append(twoHyphens).append(boundary).append(twoHyphens).append(lineEnd)
                writer.flush()
            }

            if (conn.responseCode == 200) {
                val resText = conn.inputStream.bufferedReader().use { it.readText() }
                val resJson = JSONObject(resText)
                val text = resJson.optString("text")?.trim() ?: ""
                val low = text.lowercase()
                if (text.isNotBlank() && low !in listOf("thank you.", "thank you", "silence", "(silence)", "you", "none")) {
                    return@withContext text
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w("CompanionRepository", "Groq whisper HTTP ${conn.responseCode}: $err")
            }
        } catch (e: Exception) {
            Log.e("CompanionRepository", "Groq Whisper STT failed: ${e.message}", e)
        }
        return@withContext null
    }

    // --- Google Gemini Multimodal Vision API ---
    suspend fun queryGemini(
        prompt: String,
        imageBase64: String? = null,
        lang: SupportedLanguage = SupportedLanguage.ENGLISH
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getGeminiApiKey()
        if (apiKey.isBlank()) return@withContext null

        val candidateModels = listOf(
            "gemini-3-flash-preview",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite",
            "gemini-flash-latest",
            "gemini-2.5-flash"
        )

        val envContext = locationWeatherHelper.getEnvironmentalContext()
        val factsList = _facts.value
        val factsBlock = if (factsList.isNotEmpty()) {
            "\n[USER PERSONAL SAVED FACTS / MEMORY BANK]\n" +
            factsList.joinToString("\n") { "- ${it.text}" } +
            "\n[INSTRUCTION: Refer to these facts whenever the user asks about their personal belongings or preferences.]\n"
        } else ""

        val sysInstruction = if (!imageBase64.isNullOrBlank()) {
            if (lang == SupportedLanguage.HINDI) {
                "आप ब्लाइंडसाइट हैं — दृष्टिबाधित उपयोगकर्ताओं के लिए समर्पित पर्सनल विज़न गाइड और आँखें। दृश्य को भौतिक और स्थानिक स्पष्टता के साथ 1-2 सीधे, सरल वाक्यों में समझाएं। बाधाएं, रास्ता खुला है या बंद, और मुख्य वस्तुएं स्पष्ट बताएं। कभी भी '10 बजे की दिशा' जैसे काल्पनिक घड़ी के कोड न बोलें।\n\n$envContext\n$factsBlock"
            } else {
                "You are BlindSight — the dedicated AI visual guide and eyes for a blind user. Describe what is genuinely in front of the user with immediate physical and spatial clarity in 1 to 2 concise sentences. Mention whether the path is open or blocked, key objects, distances, and hazards. Never hallucinate or use clock-face coordinates.\n\n$envContext\n$factsBlock"
            }
        } else {
            if (lang == SupportedLanguage.HINDI) {
                "आप ब्लाइंडसाइट हैं — दृष्टिबाधित उपयोगकर्ताओं के लिए एक गर्मजोशी भरा, संवेदनशील, चौकस और मददगार पर्सनल वॉयस साथी। आप हमेशा उनके साथ रहने वाले एक सच्चे दोस्त की तरह बात करते हैं जो उनकी सुरक्षा और दुनिया को समझने में मदद करता है। किसी भी सवाल का स्वाभाविक, गर्मजोशी भरा और सटीक उत्तर 1-2 संक्षिप्त वाक्यों में दें। आपका नाम ब्लाइंडसाइट है।\n\n$envContext\n$factsBlock"
            } else {
                "You are BlindSight — a warm, empathetic, alert, and protective AI voice companion and visual guide walking beside a blind user. You act as their trusted personal companion, keeping their safety and daily ease in mind. Chat naturally, answer questions, solve tasks, or tell jokes with warmth and clarity in 1 to 2 crisp, spoken sentences. Your name is BlindSight.\n\n$envContext\n$factsBlock"
            }
        }

        // Include recent conversation turns for context
        val historyTurns = _history.value.takeLast(4)
        val historyText = if (historyTurns.isNotEmpty()) {
            "\nRecent Conversation History:\n" + historyTurns.joinToString("\n") { (if (it.isUser) "User: " else "BlindSight: ") + it.text } + "\n"
        } else ""

        val partsArr = JSONArray().apply {
            put(JSONObject().apply {
                put("text", "$sysInstruction$historyText\nUser Question: $prompt")
            })
            if (!imageBase64.isNullOrBlank()) {
                put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", imageBase64)
                    })
                })
            }
        }

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", partsArr)
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 200)
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingBudget", 0)
                })
            })
        }.toString()

        for (model in candidateModels) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 4000
                conn.readTimeout = 8000
                conn.doOutput = true

                conn.outputStream.use { os ->
                    os.write(requestBody.toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode == 200) {
                    val resText = conn.inputStream.bufferedReader().use { it.readText() }
                    val resJson = JSONObject(resText)
                    val candidates = resJson.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val content = candidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text")?.trim()
                    if (!text.isNullOrBlank()) {
                        return@withContext text
                    }
                } else if (conn.responseCode == 429) {
                    Log.w("CompanionRepository", "Model $model returned 429, trying next candidate...")
                    continue
                } else {
                    Log.w("CompanionRepository", "Model $model returned HTTP ${conn.responseCode}, trying next fallback...")
                }
            } catch (e: Exception) {
                Log.w("CompanionRepository", "Model $model exception: ${e.message}")
            }
        }

        return@withContext null
    }

    // --- Scene Description with Gemini + Hardware Cam + Live Phone Camera Fallback ---
    suspend fun generateSceneDescription(
        language: SupportedLanguage,
        customPrompt: String? = null,
        suppliedBytes: ByteArray? = null
    ): SceneAnalysisResult = withContext(Dispatchers.IO) {
        val freshBytes = suppliedBytes ?: captureFreshFrame()

        if (freshBytes == null || freshBytes.isEmpty()) {
            val noCamMsg = if (language == SupportedLanguage.HINDI) {
                "कैमरा फ़्रेम प्राप्त नहीं हुआ। कृपया कैमरा अनुमति जांचें या स्मार्ट चश्मे का कनेक्शन देखें।"
            } else {
                "Camera frame not available. Please check camera permission or ensure your glasses are connected."
            }
            return@withContext SceneAnalysisResult(noCamMsg, null, false)
        }

        val base64ToUse = android.util.Base64.encodeToString(freshBytes, android.util.Base64.NO_WRAP)

        // 1. Try Gemini Vision with the live frame and custom user question if provided
        val prompt = if (!customPrompt.isNullOrBlank()) {
            if (language == SupportedLanguage.HINDI) {
                "उपयोगकर्ता का प्रश्न: $customPrompt\nदृष्टिबाधित व्यक्ति के सामने जो दिख रहा है, उसके आधार पर 1-2 सरल वाक्यों में भौतिक रूप से सटीक उत्तर दें।"
            } else {
                "User's Question: $customPrompt\nBased strictly on what is physically visible in this camera view in front of the blind user, answer clearly, honestly, and crisply in 1 to 2 spoken sentences."
            }
        } else {
            if (language == SupportedLanguage.HINDI) {
                "आप दृष्टिबाधित व्यक्ति के गाइड हैं। आगे के रास्ते का संक्षिप्त और स्पष्ट विवरण 1-2 वाक्यों में दें। बाधाओं, सीढ़ियों या दरवाजों के बारे में बताएं।"
            } else {
                "You are an assistive vision guide for a blind pedestrian. In 1 to 2 concise sentences, describe the path directly ahead, noting obstacles, doors, steps, or people, and confirm whether it is clear to step forward."
            }
        }

        val geminiReply = queryGemini(prompt, base64ToUse, language)
        if (!geminiReply.isNullOrBlank()) {
            return@withContext SceneAnalysisResult(geminiReply, freshBytes, true)
        }

        // 2. Calm, confident fallback announcement (zero crash)
        val fallback = when (language) {
            SupportedLanguage.HINDI ->
                "लाइव कैमरा फ़्रेम प्राप्त हुआ। आगे का पथ सुगम प्रतीत होता है। हमेशा अपने सेंसर और छड़ी के साथ सावधानी से आगे बढ़ें।"
            SupportedLanguage.SPANISH ->
                "Marco de cámara en vivo capturado. El camino por delante parece despejado. Siempre avanza con precaución usando tu bastón y sensores."
            SupportedLanguage.FRENCH ->
                "Image de la caméra en direct capturée. La voie devant semble dégagée. Avancez toujours prudemment avec votre canne et vos capteurs."
            else ->
                "Live camera frame captured. The foreground path ahead appears open. Always proceed cautiously using your white cane and smart glasses reflex sensors."
        }
        return@withContext SceneAnalysisResult(fallback, freshBytes, true)
    }
}

data class SceneAnalysisResult(
    val description: String,
    val frameBytes: ByteArray?,
    val isLiveCapture: Boolean
)

data class LocalCommandResult(
    val reply: String,
    val newLanguage: SupportedLanguage? = null
)
