package com.example.ui.localization.languages

import com.example.ui.localization.AppStrings

object LanguageRegistry {
    fun get(code: String): AppStrings {
        return when (code) {
            "ta" -> TAMIL
            "hi" -> HINDI
            "te" -> TELUGU
            "ml" -> MALAYALAM
            "kn" -> KANNADA
            "bn" -> BENGALI
            "mr" -> MARATHI
            "gu" -> GUJARATI
            "pa" -> PUNJABI
            "ur" -> URDU
            "es" -> SPANISH
            "fr" -> FRENCH
            "de" -> GERMAN
            "zh" -> CHINESE
            "ja" -> JAPANESE
            "ko" -> KOREAN
            "ar" -> ARABIC
            "pt" -> PORTUGUESE
            "ru" -> RUSSIAN
            else -> AppStrings()
        }
    }
}
