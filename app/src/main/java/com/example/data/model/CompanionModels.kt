package com.example.data.model

import java.util.Locale

enum class AppState(val label: String) {
    IDLE("Ready"),
    WAKE_LISTENING("Listening for wake word"),
    COMMAND_LISTENING("Listening to command"),
    THINKING("Processing…"),
    SPEAKING("Speaking answer")
}

enum class ListenMode(val displayName: String, val hint: String) {
    WAKE_WORD("Wake word", "Say \"Hey BlindSight\" to begin"),
    HANDS_FREE("Hands-free", "Continuous conversation"),
    BUTTON_ONLY("Button only", "Double-tap giant button to talk")
}

enum class ReflexAlert(val title: String, val description: String) {
    STOP("STOP", "Critical obstacle detected within 25 cm! Stop immediately."),
    STEER_LEFT("STEER LEFT", "Obstacle on right side (25–45 cm). Steer left."),
    STEER_RIGHT("STEER RIGHT", "Obstacle on left side (25–45 cm). Steer right."),
    CAREFUL("CAREFUL", "Obstacle ahead within 100 cm. Proceed with caution."),
    CLEAR("CLEAR", "Path ahead is clear.")
}

data class ReflexTelemetry(
    val leftCm: Int = 150,
    val centerCm: Int = 180,
    val rightCm: Int = 160,
    val alert: ReflexAlert = ReflexAlert.CLEAR,
    val timestamp: Long = System.currentTimeMillis()
) {
    val minDistance: Int get() = minOf(leftCm, centerCm, rightCm)
}

enum class SupportedLanguage(
    val code: String,
    val label: String,
    val locale: Locale,
    val confirmation: String
) {
    ENGLISH("en", "English", Locale.ENGLISH, "Language set to English."),
    HINDI("hi", "हिंदी (Hindi)", Locale("hi", "IN"), "भाषा हिंदी पर सेट हो गई।"),
    BENGALI("bn", "বাংলা (Bengali)", Locale("bn", "IN"), "ভাষা বাংলায় সেট করা হয়েছে।"),
    TAMIL("ta", "தமிழ் (Tamil)", Locale("ta", "IN"), "மொழி தமிழக்கு அமைக்கப்பட்டது."),
    TELUGU("te", "తెలుగు (Telugu)", Locale("te", "IN"), "భాష తెలుగుకు సెట్ చేయబడింది."),
    SPANISH("es", "Español (Spanish)", Locale("es", "ES"), "Idioma cambiado a español."),
    FRENCH("fr", "Français (French)", Locale.FRENCH, "Langue réglée sur le français.")
}

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MemoryFact(
    val id: String = System.currentTimeMillis().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
