package com.example.ui.localization

data class AppLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
    val flagEmoji: String
)

object AppLanguages {
    val SUPPORTED = listOf(
        AppLanguage("en", "English", "English", "🇺🇸"),
        AppLanguage("ta", "Tamil", "தமிழ்", "🇮🇳"),
        AppLanguage("hi", "Hindi", "हिन्दी", "🇮🇳"),
        AppLanguage("te", "Telugu", "తెలుగు", "🇮🇳"),
        AppLanguage("ml", "Malayalam", "മലയാളം", "🇮🇳"),
        AppLanguage("kn", "Kannada", "ಕನ್ನಡ", "🇮🇳"),
        AppLanguage("bn", "Bengali", "বাংলা", "🇮🇳"),
        AppLanguage("mr", "Marathi", "मराठी", "🇮🇳"),
        AppLanguage("gu", "Gujarati", "ગુજરાતી", "🇮🇳"),
        AppLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ", "🇮🇳"),
        AppLanguage("ur", "Urdu", "اردو", "🇵🇰"),
        AppLanguage("es", "Spanish", "Español", "🇪🇸"),
        AppLanguage("fr", "French", "Français", "🇫🇷"),
        AppLanguage("de", "German", "Deutsch", "🇩🇪"),
        AppLanguage("zh", "Chinese", "中文", "🇨🇳"),
        AppLanguage("ja", "Japanese", "日本語", "🇯🇵"),
        AppLanguage("ko", "Korean", "한국어", "🇰🇷"),
        AppLanguage("ar", "Arabic", "العربية", "🇸🇦"),
        AppLanguage("pt", "Portuguese", "Português", "🇧🇷"),
        AppLanguage("ru", "Russian", "Русский", "🇷🇺")
    )

    fun getByCode(code: String): AppLanguage {
        return SUPPORTED.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SUPPORTED.first()
    }
}
