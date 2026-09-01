package com.example

import com.example.data.api.GeminiModelConfig
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun geminiModelConfig_sanitizesInvalidModels() {
        assertEquals("gemini-3.7-flash", GeminiModelConfig.DEFAULT_MODEL)
        assertEquals("gemini-3.5-flash", GeminiModelConfig.FALLBACK_MODEL)
        
        // Blank or null defaults to DEFAULT_MODEL
        assertEquals("gemini-3.7-flash", GeminiModelConfig.sanitizeModelName(""))
        assertEquals("gemini-3.7-flash", GeminiModelConfig.sanitizeModelName(null))

        // Valid new and existing models
        assertEquals("gemini-3.7-flash", GeminiModelConfig.sanitizeModelName("gemini-3.7-flash"))
        assertEquals("gemini-3.5-flash", GeminiModelConfig.sanitizeModelName("gemini-3.5-flash"))
        assertEquals("gemini-3.1-pro-preview", GeminiModelConfig.sanitizeModelName("gemini-3.1-pro-preview"))
        assertEquals("gemini-3.1-pro-preview", GeminiModelConfig.sanitizeModelName("models/gemini-3.1-pro-preview"))
        assertEquals("gemini-3.1-flash-lite-preview", GeminiModelConfig.sanitizeModelName("gemini-3.1-flash-lite-preview"))
        
        // Migration from legacy models
        assertEquals("gemini-3.5-flash", GeminiModelConfig.sanitizeModelName("gemini-1.5-flash"))
        assertEquals("gemini-3.5-flash", GeminiModelConfig.sanitizeModelName("Gemini 1.5 Flash"))
        assertEquals("gemini-3.1-pro-preview", GeminiModelConfig.sanitizeModelName("gemini-1.5-pro"))
        assertEquals("gemini-3.1-pro-preview", GeminiModelConfig.sanitizeModelName("Gemini 1.5 Pro"))
        assertEquals("gemini-3.1-pro-preview", GeminiModelConfig.sanitizeModelName("gemini-advanced"))
        assertEquals("gemini-3.1-pro-preview", GeminiModelConfig.sanitizeModelName("Gemini Advanced"))

        // Display name and model options checks
        assertEquals(5, GeminiModelConfig.MODEL_OPTIONS.size)
        assertEquals("Gemini 3.7 Flash", GeminiModelConfig.getDisplayName("gemini-3.7-flash"))
        assertEquals("Gemini 3.5 Flash", GeminiModelConfig.getDisplayName("gemini-3.5-flash"))
        assertEquals("Gemini 3.1 Pro Preview", GeminiModelConfig.getDisplayName("gemini-3.1-pro-preview"))
        assertEquals("Gemini 3.1 Flash Lite Preview", GeminiModelConfig.getDisplayName("gemini-3.1-flash-lite-preview"))
        assertEquals("Gemini Advanced", GeminiModelConfig.getDisplayName("gemini-advanced"))
        assertEquals("Recommended", GeminiModelConfig.getModelOption("gemini-3.7-flash")?.badge)
        assertEquals("Best for Complex Tasks", GeminiModelConfig.getModelOption("gemini-advanced")?.badge)
        assertEquals("Preview", GeminiModelConfig.getModelOption("gemini-3.1-pro-preview")?.badge)
        assertEquals("Preview", GeminiModelConfig.getModelOption("gemini-3.1-flash-lite-preview")?.badge)
    }
}
