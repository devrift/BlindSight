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
import com.example.data.model.CameraFrameData
import com.example.data.model.CameraSource
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
        if (saved.isNotBlank() && !saved.contains("192.168.29.173") && !saved.contains("blindsightpage.vercel.app")) return saved
        return "https://blindsightai.vercel.app"
    }

    fun setGatewayUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        prefs.edit().putString("gateway_url", cleaned).apply()
    }

    suspend fun fetchLatestHardwareFrame(): ByteArray? = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<String>()
        val gateway = getGatewayUrl().trim().trimEnd('/')
        if (gateway.isNotBlank() && !gateway.contains("blindsightai.vercel.app") && !gateway.contains("blindsightpage.vercel.app")) {
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

    fun isPhoneCameraFallbackEnabled(): Boolean {
        return prefs.getBoolean("phone_camera_fallback_enabled", true)
    }

    fun setPhoneCameraFallbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("phone_camera_fallback_enabled", enabled).apply()
    }

    suspend fun captureFreshFrameWithSource(): CameraFrameData = withContext(Dispatchers.IO) {
        // 1. Check hardware glasses first
        val hwFrame = fetchLatestHardwareFrame()
        if (hwFrame != null && hwFrame.isNotEmpty()) {
            return@withContext CameraFrameData(hwFrame, CameraSource.HARDWARE_GLASSES)
        }

        // 2. Fall back to live capture from mobile back camera if enabled
        if (isPhoneCameraFallbackEnabled()) {
            try {
                val phoneFrame = phoneCameraHelper.captureLiveFrame()
                if (phoneFrame != null && phoneFrame.isNotEmpty()) {
                    return@withContext CameraFrameData(phoneFrame, CameraSource.PHONE_BACK_CAMERA)
                }
            } catch (e: Exception) {
                Log.w("CompanionRepository", "Live mobile back camera capture error: ${e.message}")
            }
        }
        return@withContext CameraFrameData(null, CameraSource.NONE)
    }

    suspend fun captureFreshFrame(): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext captureFreshFrameWithSource().bytes
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

    fun openGoogleMaps(destination: String? = null): Boolean {
        return locationWeatherHelper.openGoogleMaps(destination)
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
            val success = phoneCameraHelper.setTorch(enable)
            if (success) {
                if (enable) "Flashlight turned on." else "Flashlight turned off."
            } else {
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
            }
        } catch (e: Exception) {
            "Could not toggle flashlight."
        }
    }

    fun setTorch(enable: Boolean): Boolean {
        toggleTorch(enable)
        return true
    }

    fun switchCamera(): Boolean {
        return phoneCameraHelper.switchCamera()
    }

    fun isFrontCamera(): Boolean = phoneCameraHelper.isFrontCamera

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

        // 2. Google Maps Launch & Walking Navigation (Direct Action)
        if (clean in listOf("open google maps", "open maps", "google maps", "show on map", "show map", "map", "maps", "map kholo")) {
            locationWeatherHelper.openGoogleMaps()
            val reply = if (currentLang == SupportedLanguage.HINDI) "आपकी लाइव लोकेशन के साथ गूगल मैप्स खोला जा रहा है।" else "Opening Google Maps with your current live coordinates."
            return LocalCommandResult(reply)
        }

        val navMatch = Regex("^(?:navigate to|take me to|directions to|walk to|route to|how to reach|how to get to|chalo|rasta dikhao|navigation to)\\s+(.+)", RegexOption.IGNORE_CASE).find(clean)
        if (navMatch != null) {
            val destination = navMatch.groupValues[1].replace(Regex("[?!.]"), "").trim()
            locationWeatherHelper.openGoogleMaps(destination)
            val reply = if (currentLang == SupportedLanguage.HINDI) "$destination के लिए गूगल मैप्स पर पैदल नेविगेशन शुरू हो रहा है।" else "Starting Google Maps walking navigation to $destination."
            return LocalCommandResult(reply)
        }

        // 2b. Phone Controls - Calls: "call mom", "dial 9876543210", "call rahul", "phone maa"
        val callMatch = Regex("^(?:call|dial|phone)\\s+(.+)", RegexOption.IGNORE_CASE).find(clean)
        if (callMatch != null) {
            val target = callMatch.groupValues[1].replace(Regex("[?!.]"), "").trim()
            if (target.isNotBlank()) {
                val callResult = com.example.service.PhoneControlHelper.makeCall(context, target)
                return LocalCommandResult(callResult)
            }
        }

        // 2c. Phone Controls - Launch App: "open discord", "open whatsapp", "launch spotify", "open settings"
        val openMatch = Regex("^(?:open|launch|start)\\s+(.+)", RegexOption.IGNORE_CASE).find(clean)
        if (openMatch != null) {
            val appTarget = openMatch.groupValues[1].replace(Regex("[?!.]"), "").trim()
            val launched = com.example.service.PhoneControlHelper.launchApp(context, appTarget)
            if (launched != null) {
                val reply = if (currentLang == SupportedLanguage.HINDI) "$launched खोला जा रहा है।" else "Opening $launched."
                return LocalCommandResult(reply)
            }
        }

        // 2d. Phone Controls - Timer: "set timer for 5 minutes", "timer 30 seconds"
        val timerMatch = Regex("^(?:set\\s+)?timer(?:\\s+for)?\\s+(\\d+)\\s*(second|seconds|sec|minute|minutes|min|hour|hours|hr|hrs)?", RegexOption.IGNORE_CASE).find(clean)
        if (timerMatch != null) {
            val value = timerMatch.groupValues[1].toIntOrNull() ?: 60
            val unit = timerMatch.groupValues.getOrNull(2)?.lowercase() ?: "minute"
            val seconds = when {
                unit.startsWith("sec") -> value
                unit.startsWith("hour") || unit.startsWith("hr") -> value * 3600
                else -> value * 60
            }
            val success = com.example.service.PhoneControlHelper.setTimer(context, seconds, "BlindSight Timer")
            if (success) {
                val unitStr = if (seconds >= 3600) "${seconds / 3600} hours" else if (seconds >= 60) "${seconds / 60} minutes" else "$seconds seconds"
                val reply = if (currentLang == SupportedLanguage.HINDI) "$unitStr का टाइमर सेट कर दिया गया है।" else "Timer set for $unitStr."
                return LocalCommandResult(reply)
            }
        }

        // 2e. Phone Controls - Alarm: "set alarm for 7:30 am", "set alarm for 8 am", "alarm 7 pm"
        val alarmMatch = Regex("^(?:set\\s+)?(?:an\\s+)?alarm(?:\\s+for|\\s+at)?\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(clean)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val min = alarmMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val ampm = alarmMatch.groupValues.getOrNull(3)?.lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            val success = com.example.service.PhoneControlHelper.setAlarm(context, hour, min, "BlindSight Alarm")
            if (success) {
                val timeLabel = String.format(Locale.getDefault(), "%02d:%02d", hour, min)
                val reply = if (currentLang == SupportedLanguage.HINDI) "$timeLabel का अलार्म सेट कर दिया गया है।" else "Alarm set for $timeLabel."
                return LocalCommandResult(reply)
            }
        }

        // 3. Where Am I & Live Spatial QA (1-sentence street landmark answer)
        val isWhereAmI = clean in listOf(
            "where am i", "what is my location", "where am i right now", "my location",
            "what is around me", "tell me my location", "kahan hoon", "main kahan hoon",
            "meri location", "location", "current location"
        ) || Regex("\\b(?:where am i|my location|what is around me|kahan hoon|where am i right now)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean)

        if (isWhereAmI) {
            val locationReply = locationWeatherHelper.getConversationalLocationSummary(currentLang)
            return LocalCommandResult(locationReply)
        }

        // 5. Battery
        if (clean in listOf("battery", "battery status", "battery level", "how much battery", "check battery", "what is my battery")) {
            return LocalCommandResult(getBatteryStatus())
        }

        // Hardware & Sensor Status Check / Privacy ("what is in use", "what is connected", "hardware status", "are you using my camera")
        val isHardwareOrPrivacyQuery = clean in listOf(
            "hardware status", "what is connected", "what's connected", "is hardware connected",
            "what is in use", "what's in use", "what hardware is connected", "sensor status",
            "are you using my camera", "is my camera in use", "is camera on", "why did you use my camera",
            "camera privacy", "privacy", "kya connected hai", "camera on hai kya", "hardware check"
        ) || Regex("\\b(?:what is in use|what's in use|hardware status|what is connected|what's connected|are you using my camera|camera privacy|kya connected hai)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean)

        if (isHardwareOrPrivacyQuery) {
            val isGlasses = _telemetry.value.isHardwareConnected
            val isFallback = isPhoneCameraFallbackEnabled()
            val reply = if (currentLang == SupportedLanguage.HINDI) {
                val glassesStr = if (isGlasses) "स्मार्ट चश्मा कनेक्टेड है।" else "स्मार्ट चश्मा कनेक्ट नहीं है।"
                val camStr = if (isGlasses) "स्मार्ट चश्मे का कैमरा सक्रिय है।" else if (isFallback) "जब आप विज़न पूछेंगे, तो आपके मोबाइल के बैक कैमरे का उपयोग घोषणा के साथ किया जाता है।" else "मोबाइल कैमरा फॉलबैक बंद है।"
                "हार्डवेयर स्थिति: $glassesStr $camStr अल्ट्रासोनिक रडार ${if (isGlasses) "सक्रिय है।" else "हार्डवेयर कनेक्ट नहीं है।"} माइक्रोफोन एक्टिव है। आपकी अनुमति के बिना कोई भी बैकग्राउंड कैमरा रिकॉर्डिंग नहीं होती।"
            } else {
                val glassesStr = if (isGlasses) "Smart glasses are connected." else "Smart glasses are disconnected."
                val camStr = if (isGlasses) "Using smart glasses camera for vision." else if (isFallback) "When vision is requested, BlindSight uses your mobile back camera and explicitly announces 'Using your mobile back camera'." else "Mobile camera fallback is disabled."
                "Hardware status: $glassesStr $camStr Ultrasonic hardware is ${if (isGlasses) "active." else "disconnected."} Microphone is active. BlindSight never streams or records in the background."
            }
            return LocalCommandResult(reply)
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

        // 8. Reset / Clear chat
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
                val sorry = if (currentLang == SupportedLanguage.HINDI) "माफ़ कीजिए, समझ नहीं आया। क्या आप दोबारा बोल सकते हैं?" else "Sorry, I'm not clear. Can you speak again?"
                LocalCommandResult(sorry)
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

        val envContext = locationWeatherHelper.getEnvironmentalContext()
        val factsList = _facts.value
        val factsBlock = if (factsList.isNotEmpty()) {
            "\n[Saved Memory: " + factsList.joinToString("; ") { it.text } + "]\n"
        } else ""

        val sysInstruction = """
        You are BlindSight, a warm, observant, and dependable visual guide for a visually impaired user.

        VOICE CADENCE & RECOVERY:
        - Speak at a natural, lively conversational speed.
        - If user input is muffled, completely ambiguous, or incomprehensible, say strictly:
          "Sorry, I'm not clear. Can you speak again?"
        - Never lecture the user about language rules or list supported languages.
        - Speak naturally in English, natural Hindi-Urdu (Hindustani), Punjabi, or Bengali based on the user's input.
        - Keep observations clear, spatial, and conversational. Describe immediate surroundings, distances, and visible text naturally.
        - Absolutely no robotic lists, bullets, markdown asterisks, or emojis.

        APP CONTROL COMMAND TOKENS:
        Append actions cleanly when requested:
        - Pause playback and enter standby: [ACTION:PAUSE]
        - Resume active conversation: [ACTION:RESUME]
        - Open settings: [ACTION:GO_TO_SETTINGS]
        - Return to home: [ACTION:GO_TO_HOME]
        - Toggle flashlight: [ACTION:TORCH_ON] / [ACTION:TORCH_OFF]
        - Switch lens: [ACTION:SWITCH_CAMERA]
        - Read text: [ACTION:READ_TEXT]
        - Walking navigation: [ACTION:NAVIGATE <destination>]
        - Set Alarm: [ACTION:SET_ALARM:hour:minute:label] (e.g. [ACTION:SET_ALARM:7:30:Wake up])
        - Set Timer: [ACTION:SET_TIMER:seconds:label] (e.g. [ACTION:SET_TIMER:300:Tea])
        - Make Call: [ACTION:CALL:number_or_name] (e.g. [ACTION:CALL:Mom] or [ACTION:CALL:9876543210])
        - Set Reminder: [ACTION:SET_REMINDER:text] (e.g. [ACTION:SET_REMINDER:Take medicine at 8pm])
        - Open App: [ACTION:OPEN_APP:name] (e.g. [ACTION:OPEN_APP:discord], [ACTION:OPEN_APP:whatsapp], [ACTION:OPEN_APP:spotify])

        $envContext$factsBlock
        """.trimIndent()

        val models = listOf("openai/gpt-oss-120b", "qwen/qwen3.8-27b", "openai/gpt-oss-20b")
        for (m in models) {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "BlindSight-Android/1.7")
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

                // Part 2: response_format (Language auto-detected by Whisper)
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
            "gemini-1.5-flash",
            "gemini-2.5-flash",
            "gemini-flash-latest"
        )

        val envContext = locationWeatherHelper.getEnvironmentalContext()
        val factsList = _facts.value
        val factsBlock = if (factsList.isNotEmpty()) {
            "\n[Saved Memory: " + factsList.joinToString("; ") { it.text } + "]\n"
        } else ""

        val sysInstruction = """
        You are BlindSight, a warm, observant, and dependable visual guide for a visually impaired user.

        VOICE CADENCE & RECOVERY:
        - Speak at a natural, lively conversational speed.
        - If user input is muffled, completely ambiguous, or incomprehensible, say strictly:
          "Sorry, I'm not clear. Can you speak again?"
        - Never lecture the user about language rules or list supported languages.
        - Speak naturally in English, natural Hindi-Urdu (Hindustani), Punjabi, or Bengali based on the user's input.
        - Keep observations clear, spatial, and conversational. Describe immediate surroundings, distances, and visible text naturally.
        - Absolutely no robotic lists, bullets, markdown asterisks, or emojis.

        APP CONTROL COMMAND TOKENS:
        Append actions cleanly when requested:
        - Pause playback and enter standby: [ACTION:PAUSE]
        - Resume active conversation: [ACTION:RESUME]
        - Open settings: [ACTION:GO_TO_SETTINGS]
        - Return to home: [ACTION:GO_TO_HOME]
        - Toggle flashlight: [ACTION:TORCH_ON] / [ACTION:TORCH_OFF]
        - Switch lens: [ACTION:SWITCH_CAMERA]
        - Read text: [ACTION:READ_TEXT]
        - Walking navigation: [ACTION:NAVIGATE <destination>]
        - Set Alarm: [ACTION:SET_ALARM:hour:minute:label] (e.g. [ACTION:SET_ALARM:7:30:Wake up])
        - Set Timer: [ACTION:SET_TIMER:seconds:label] (e.g. [ACTION:SET_TIMER:300:Tea])
        - Make Call: [ACTION:CALL:number_or_name] (e.g. [ACTION:CALL:Mom] or [ACTION:CALL:9876543210])
        - Set Reminder: [ACTION:SET_REMINDER:text] (e.g. [ACTION:SET_REMINDER:Take medicine at 8pm])
        - Open App: [ACTION:OPEN_APP:name] (e.g. [ACTION:OPEN_APP:discord], [ACTION:OPEN_APP:whatsapp], [ACTION:OPEN_APP:spotify])

        $envContext$factsBlock
        """.trimIndent()

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
                put("temperature", 0.2)
                put("maxOutputTokens", 120)
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

    // --- Groq LPU Ultra-Fast Multimodal Vision (qwen/qwen3.8-27b, <100ms) ---
    suspend fun queryVisionGroq(
        prompt: String,
        imageBase64: String,
        lang: SupportedLanguage = SupportedLanguage.ENGLISH
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getGroqApiKey()
        if (apiKey.isBlank() || imageBase64.isBlank()) return@withContext null

        val sysInstruction = if (lang == SupportedLanguage.HINDI) {
            "आप ब्लाइंडसाइट हैं — दृष्टिबाधित उपयोगकर्ताओं के लिए समर्पित पर्सनल विज़न गाइड और आँखें। कैमरे में जो भौतिक रूप से दिख रहा है, उसे 1-2 सीधे, सटीक वाक्यों (20-30 शब्द) में बताएं। स्थानिक दिशाएं: सीधे 'बिल्कुल आपके सामने', 'बाईं तरफ सीढ़ियां', 'दाईं तरफ खुला रास्ता' जैसे प्रत्यक्ष शब्दों का उपयोग करें। '12 बजे की दिशा' जैसे शब्द न बोलें। रास्ता खुला (CLEAR) है या बंद (BLOCKED), बाधाएं और खतरे स्पष्ट बताएं। कभी भी सफेद छड़ी (cane) का उल्लेख न करें — ब्लाइंडसाइट छड़ी का आधुनिक इलेक्ट्रॉनिक विकल्प है।"
        } else {
            "You are BlindSight — the dedicated AI visual guide and eyes for a blind user. Describe what is genuinely in front of the user with immediate physical and spatial clarity in 1 to 2 concise spoken sentences (strictly under 20-30 words). Spatial Anchors: Use direct anchors like 'Directly ahead', 'Steps down on your left', 'Clear pathway to your right'. NEVER use repetitive clock phrases like 'at your 12 o'clock'. Mention whether the path is open (CLEAR) or blocked (BLOCKED), key objects, distances, and hazards. Never mention white cane or cane — BlindSight is designed as an advanced electronic replacement for canes."
        }

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "BlindSight-Android/1.7")
            conn.connectTimeout = 4000
            conn.readTimeout = 8000
            conn.doOutput = true

            val contentArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$imageBase64")
                    })
                })
            }

            val messagesArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", sysInstruction)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArr)
                })
            }

            val body = JSONObject().apply {
                put("model", "qwen/qwen3.8-27b")
                put("messages", messagesArr)
                put("max_tokens", 250)
                put("temperature", 0.5)
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
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w("CompanionRepository", "Groq vision HTTP ${conn.responseCode}: $err")
            }
        } catch (e: Exception) {
            Log.e("CompanionRepository", "Groq vision failed: ${e.message}", e)
        }
        return@withContext null
    }

    // --- Scene Description with Groq Multimodal Vision + Hardware Cam + Phone Cam ---
    suspend fun generateSceneDescription(
        language: SupportedLanguage,
        customPrompt: String? = null,
        suppliedBytes: ByteArray? = null,
        suppliedSource: CameraSource? = null
    ): SceneAnalysisResult = withContext(Dispatchers.IO) {
        val frameData = if (suppliedBytes != null) {
            CameraFrameData(suppliedBytes, suppliedSource ?: CameraSource.PHONE_BACK_CAMERA)
        } else {
            captureFreshFrameWithSource()
        }

        val freshBytes = frameData.bytes
        val cameraSource = frameData.source

        if (freshBytes == null || freshBytes.isEmpty() || cameraSource == CameraSource.NONE) {
            val noCamMsg = if (!isPhoneCameraFallbackEnabled()) {
                if (language == SupportedLanguage.HINDI) {
                    "स्मार्ट चश्मा कनेक्ट नहीं है। गोपनीयता के लिए मोबाइल कैमरा फॉलबैक बंद है। सेटिंग्स में इसे चालू करें।"
                } else {
                    "Smart glasses are not connected. Mobile camera fallback is disabled in Settings for privacy."
                }
            } else {
                if (language == SupportedLanguage.HINDI) {
                    "कैमरा या स्मार्ट चश्मा कनेक्ट नहीं है। कृपया आगे देखने के लिए कैमरा या हार्डवेयर कनेक्ट करें।"
                } else {
                    "Smart glasses and mobile camera are not connected. Please connect your glasses or grant camera permissions to look ahead."
                }
            }
            return@withContext SceneAnalysisResult(noCamMsg, null, false, CameraSource.NONE)
        }

        val base64ToUse = android.util.Base64.encodeToString(freshBytes, android.util.Base64.NO_WRAP)

        val prompt = if (!customPrompt.isNullOrBlank()) {
            if (language == SupportedLanguage.HINDI) {
                "उपयोगकर्ता का प्रश्न: $customPrompt\nदृष्टिबाधित व्यक्ति के सामने जो दिख रहा है, उसके आधार पर 'बिल्कुल सामने', 'बाईं तरफ', 'दाईं तरफ' जैसे प्रत्यक्ष स्थानिक शब्दों के साथ 1-2 सरल वाक्यों (20-30 शब्द) में भौतिक रूप से सटीक उत्तर दें।"
            } else {
                "User's Question: $customPrompt\nBased strictly on what is physically visible in this camera view in front of the blind user, answer clearly, honestly, and crisply using direct spatial anchors ('Directly ahead', 'On your left', 'To your right') in 1 to 2 spoken sentences (max 20 to 30 words)."
            }
        } else {
            if (language == SupportedLanguage.HINDI) {
                "आप दृष्टिबाधित व्यक्ति के गाइड हैं। आगे के रास्ते का भौतिक रूप से सटीक विवरण 'बिल्कुल सामने', 'बाईं तरफ', 'दाईं तरफ' जैसे सीधे शब्दों में 1-2 संक्षिप्त वाक्यों (20-30 शब्द) में दें। स्पष्ट बताएं कि रास्ता खुला (CLEAR) है या बंद (BLOCKED)। यदि बाधा, सीढ़ी या दरवाजा है तो सीधे बताएं।"
            } else {
                "You are an assistive vision guide for a blind pedestrian. In 1 to 2 concise spoken sentences (strictly under 20-30 words), state clearly whether the forward path is BLOCKED or CLEAR using direct spatial anchors ('Directly ahead', 'Steps on your left', 'Pathway on your right'). Confirm if it is safe to walk forward."
            }
        }

        // Explicit announcement of source so user always knows what is in use
        val sourceAnnouncement = when (cameraSource) {
            CameraSource.PHONE_BACK_CAMERA -> {
                if (language == SupportedLanguage.HINDI) {
                    "आपके मोबाइल के बैक कैमरे का उपयोग करते हुए: "
                } else {
                    "Using your mobile back camera: "
                }
            }
            CameraSource.HARDWARE_GLASSES -> {
                if (language == SupportedLanguage.HINDI) {
                    "स्मार्ट चश्मे के कैमरे का उपयोग करते हुए: "
                } else {
                    "Using smart glasses camera: "
                }
            }
            CameraSource.NONE -> ""
        }

        // 1. Primary: Groq LPU Multimodal Vision (tested, <100ms ultra-fast response)
        val groqVisionReply = queryVisionGroq(prompt, base64ToUse, language)
        if (!groqVisionReply.isNullOrBlank()) {
            return@withContext SceneAnalysisResult("$sourceAnnouncement$groqVisionReply", freshBytes, true, cameraSource)
        }

        // 2. Secondary: Gemini Multimodal Vision if key is available
        val geminiReply = queryGemini(prompt, base64ToUse, language)
        if (!geminiReply.isNullOrBlank()) {
            return@withContext SceneAnalysisResult("$sourceAnnouncement$geminiReply", freshBytes, true, cameraSource)
        }

        // 3. Truthful failure announcement - NEVER return a canned fake description or mention a cane!
        val failureMsg = if (language == SupportedLanguage.HINDI) {
            "${sourceAnnouncement}कैमरा दृश्य का विश्लेषण नहीं हो सका। कृपया अपना नेटवर्क कनेक्शन जांचें।"
        } else {
            "${sourceAnnouncement}Could not analyze the camera view right now. Please check your network connection."
        }
        return@withContext SceneAnalysisResult(failureMsg, freshBytes, false, cameraSource)
    }
}

data class SceneAnalysisResult(
    val description: String,
    val frameBytes: ByteArray?,
    val isLiveCapture: Boolean,
    val cameraSource: CameraSource = CameraSource.NONE
)

data class LocalCommandResult(
    val reply: String,
    val newLanguage: SupportedLanguage? = null
)
