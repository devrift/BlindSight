package com.example.data.model

import java.util.Locale

enum class AppState(val label: String) {
    IDLE("Ready"),
    WAKE_LISTENING("Listening for wake word"),
    COMMAND_LISTENING("Listening to command"),
    THINKING("Processing…"),
    SPEAKING("Speaking answer"),
    CONFIRMING("Understood"),
    CAUTION("Caution")
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

enum class CameraSource(val label: String, val spokenAnnouncementEn: String, val spokenAnnouncementHi: String) {
    NONE("No Camera", "No camera connected", "कोई कैमरा कनेक्ट नहीं है"),
    HARDWARE_GLASSES("Smart Glasses Camera", "Using smart glasses camera", "स्मार्ट चश्मे के कैमरे का उपयोग करते हुए"),
    PHONE_BACK_CAMERA("Mobile Back Camera", "Using your mobile back camera", "आपके मोबाइल के बैक कैमरे का उपयोग करते हुए")
}

data class CameraFrameData(
    val bytes: ByteArray?,
    val source: CameraSource,
    val timestamp: Long = System.currentTimeMillis()
)

data class ReflexTelemetry(
    val isHardwareConnected: Boolean = false,
    val leftCm: Int = 0,
    val centerCm: Int = 0,
    val rightCm: Int = 0,
    val alert: ReflexAlert = ReflexAlert.CLEAR,
    val timestamp: Long = System.currentTimeMillis()
) {
    val minDistance: Int get() = if (isHardwareConnected) minOf(leftCm, centerCm, rightCm) else 0
}

enum class SupportedLanguage(
    val code: String,
    val bcp47: String,
    val label: String,
    val shortCode: String,
    val locale: Locale,
    val confirmation: String
) {
    ENGLISH("en", "en-IN", "English", "EN", Locale("en", "IN"), "Language set to English."),
    HINDI("hi", "hi-IN", "हिंदी-उर्दू", "HI", Locale("hi", "IN"), "भाषा हिंदी-उर्दू पर सेट हो गई।"),
    PUNJABI("pa", "pa-IN", "ਪੰਜਾਬੀ", "PA", Locale("pa", "IN"), "ਭਾਸ਼ਾ ਪੰਜਾਬੀ ਵਿੱਚ ਸੈੱਟ ਕੀਤੀ ਗਈ ਹੈ।"),
    BENGALI("bn", "bn-IN", "বাংলা", "BN", Locale("bn", "IN"), "ভাষা বাংলায় সেট করা হয়েছে।")
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
