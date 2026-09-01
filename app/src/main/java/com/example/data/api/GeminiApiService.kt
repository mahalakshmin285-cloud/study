package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

object GeminiModelConfig {
    const val DEFAULT_MODEL = "gemini-3.7-flash"
    const val FALLBACK_MODEL = "gemini-3.5-flash"

    data class ModelOption(
        val id: String,
        val displayName: String,
        val badge: String? = null,
        val description: String? = null
    )

    val MODEL_OPTIONS = listOf(
        ModelOption(
            id = "gemini-3.7-flash",
            displayName = "Gemini 3.7 Flash",
            badge = "Recommended",
            description = "Fastest multimodal reasoning & real-time responsiveness"
        ),
        ModelOption(
            id = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            description = "High-speed balanced performance for everyday learning"
        ),
        ModelOption(
            id = "gemini-3.1-pro-preview",
            displayName = "Gemini 3.1 Pro Preview",
            badge = "Preview",
            description = "Advanced multi-step reasoning and deep analysis"
        ),
        ModelOption(
            id = "gemini-3.1-flash-lite-preview",
            displayName = "Gemini 3.1 Flash Lite Preview",
            badge = "Preview",
            description = "Ultra-lightweight and rapid answer generation"
        ),
        ModelOption(
            id = "gemini-advanced",
            displayName = "Gemini Advanced",
            badge = "Best for Complex Tasks",
            description = "Maximum intelligence for research & problem solving"
        )
    )

    val AVAILABLE_MODELS = MODEL_OPTIONS.map { it.id }

    fun getDisplayName(modelId: String): String {
        return MODEL_OPTIONS.find { it.id.equals(modelId, ignoreCase = true) }?.displayName
            ?: modelId
    }

    fun getModelOption(modelId: String): ModelOption? {
        return MODEL_OPTIONS.find { it.id.equals(modelId, ignoreCase = true) }
    }

    fun sanitizeModelName(inputModel: String?): String {
        if (inputModel.isNullOrBlank()) return DEFAULT_MODEL
        val cleaned = inputModel.trim().removePrefix("models/")
        val normalized = cleaned.replace(" ", "-").lowercase()
        return when {
            normalized == "gemini-advanced" || normalized.contains("advanced") -> "gemini-3.1-pro-preview"
            normalized.contains("1.5-flash") -> "gemini-3.5-flash"
            normalized.contains("1.5-pro") -> "gemini-3.1-pro-preview"
            normalized.contains("1.5") -> "gemini-3.5-flash"
            normalized.contains("3.7-flash") -> "gemini-3.7-flash"
            normalized.contains("3.5-flash") -> "gemini-3.5-flash"
            normalized.contains("3.1-pro") -> "gemini-3.1-pro-preview"
            normalized.contains("3.1-flash-lite") || normalized.contains("flash-lite") -> "gemini-3.1-flash-lite-preview"
            normalized.contains("flash-latest") -> "gemini-flash-latest"
            normalized.contains("2.5-pro") -> "gemini-3.1-pro-preview"
            normalized.contains("2.5-flash") || normalized.contains("2.0-flash") -> "gemini-3.7-flash"
            cleaned in AVAILABLE_MODELS -> {
                if (cleaned == "gemini-advanced") "gemini-3.1-pro-preview" else cleaned
            }
            cleaned.isNotBlank() && cleaned.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } -> cleaned
            else -> DEFAULT_MODEL
        }
    }
}

// Moshi data models for Gemini REST API
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mimeType: String,
    val data: String // Base64 encoded data
)

data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val responseMimeType: String? = null
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiCandidate(
    val content: GeminiContent?
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private fun getRetryDelayMs(response: retrofit2.Response<*>?): Long {
        val retryAfterHeader = response?.headers()?.get("Retry-After") ?: response?.headers()?.get("retry-after")
        if (!retryAfterHeader.isNullOrBlank()) {
            try {
                val seconds = retryAfterHeader.trim().toLongOrNull()
                if (seconds != null && seconds > 0) {
                    return (seconds * 1000L).coerceIn(500L, 3000L)
                }
            } catch (_: Exception) {
                // Ignore parse errors and fallback
            }
        }
        return 0L
    }

    private fun parseHttpError(e: HttpException, model: String = ""): String {
        val rawBody = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        android.util.Log.e("GeminiApiService", "Gemini API HTTP ${e.code()} for model '$model': $rawBody", e)

        val extractedMessage = try {
            if (!rawBody.isNullOrBlank()) {
                val json = JSONObject(rawBody)
                val errorObj = json.optJSONObject("error")
                errorObj?.optString("message")?.takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }

        val displayModelName = if (model.isNotBlank()) GeminiModelConfig.getDisplayName(model) else "Gemini"

        return when (e.code()) {
            401, 403 -> "API Key error: ${extractedMessage ?: "Invalid or unauthorized Gemini API key. Please check your key in Settings."}"
            429 -> "Rate limit exceeded for $displayModelName (HTTP 429). Please wait a few moments before trying again."
            503 -> "Gemini service temporarily unavailable for $displayModelName (HTTP 503). Please retry in a moment."
            500, 504 -> "AI service server error ($displayModelName HTTP ${e.code()}). Please retry shortly."
            404 -> "Model '$displayModelName' was not found (HTTP 404). ${extractedMessage ?: "Please select another model in Settings."}".trim()
            400 -> "Request error (HTTP 400): ${extractedMessage ?: "Invalid request format or unsupported parameter."}"
            else -> extractedMessage ?: "AI service returned status ${e.code()}. Please try again."
        }
    }

    private suspend fun executeWithRetryAndFallback(
        primaryModel: String,
        apiKey: String,
        request: GeminiRequest,
        defaultErrorMessage: String = "Unable to generate a response. Please try again."
    ): String {
        val targetModel = GeminiModelConfig.sanitizeModelName(primaryModel)
        val fallbackChain = linkedSetOf(
            targetModel,
            GeminiModelConfig.DEFAULT_MODEL,
            GeminiModelConfig.FALLBACK_MODEL,
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite-preview"
        ).toList()

        var lastException: Exception? = null
        var lastHttpException: HttpException? = null
        var lastAttemptedModel = targetModel

        for (model in fallbackChain) {
            lastAttemptedModel = model
            var attempts = 0
            val maxAttempts = 2

            while (attempts < maxAttempts) {
                attempts++
                try {
                    val response = service.generateContent(
                        model = model,
                        apiKey = apiKey,
                        request = request
                    )
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!text.isNullOrBlank()) {
                        return text
                    }
                } catch (e: HttpException) {
                    lastHttpException = e
                    lastException = e
                    val code = e.code()
                    // 401/403 (Auth failure) or 400 (Bad request) should not retry blindly
                    if (code == 401 || code == 403 || code == 400) {
                        return parseHttpError(e, model)
                    }
                    if (code == 429 || code == 503) {
                        if (attempts < maxAttempts) {
                            val headerDelay = getRetryDelayMs(e.response())
                            val delayMs = if (headerDelay > 0) headerDelay else (500L * attempts)
                            kotlinx.coroutines.delay(delayMs)
                            continue
                        }
                    }
                    // For 404 or exhausted 429/503, break out to try next fallback model
                    break
                } catch (e: Exception) {
                    lastException = e
                    android.util.Log.e("GeminiApiService", "Network error on model '$model': ${e.message}", e)
                    break
                }
            }
        }

        return when {
            lastHttpException != null -> parseHttpError(lastHttpException, lastAttemptedModel)
            lastException != null -> "Unable to connect to the AI service. Please check your internet connection."
            else -> defaultErrorMessage
        }
    }

    suspend fun testModel(modelName: String): Boolean {
        val sanitized = GeminiModelConfig.sanitizeModelName(modelName)
        return GeminiModelConfig.AVAILABLE_MODELS.contains(sanitized)
    }

    suspend fun generateChat(
        contents: List<GeminiContent>,
        systemInstruction: String? = null,
        modelName: String = GeminiModelConfig.DEFAULT_MODEL
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "Please configure your Gemini API key in Settings or the Secrets panel."
        }

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = systemInstruction?.takeIf { it.isNotBlank() }?.let {
                GeminiContent(parts = listOf(GeminiPart(text = it)))
            }
        )

        return executeWithRetryAndFallback(
            primaryModel = modelName,
            apiKey = apiKey,
            request = request,
            defaultErrorMessage = "Unable to generate a chat response. Please try again."
        )
    }

    suspend fun generateText(
        prompt: String,
        systemInstruction: String? = null,
        modelName: String = GeminiModelConfig.DEFAULT_MODEL,
        isJsonMode: Boolean = false
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "Please configure your Gemini API key in Settings or the Secrets panel."
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            systemInstruction = systemInstruction?.takeIf { it.isNotBlank() }?.let {
                GeminiContent(parts = listOf(GeminiPart(text = it)))
            },
            generationConfig = if (isJsonMode) {
                GeminiGenerationConfig(responseMimeType = "application/json")
            } else null
        )

        return executeWithRetryAndFallback(
            primaryModel = modelName,
            apiKey = apiKey,
            request = request,
            defaultErrorMessage = "Unable to generate content with AI. Please try again."
        )
    }

    suspend fun generateMultimodal(
        prompt: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        modelName: String = GeminiModelConfig.DEFAULT_MODEL
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "Please configure your Gemini API key in Settings or the Secrets panel."
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = imageBase64))
                    )
                )
            )
        )

        return executeWithRetryAndFallback(
            primaryModel = modelName,
            apiKey = apiKey,
            request = request,
            defaultErrorMessage = "Unable to process the image with AI. Please try again."
        )
    }
}
