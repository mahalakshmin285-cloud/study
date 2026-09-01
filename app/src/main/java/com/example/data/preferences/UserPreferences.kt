package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.api.GeminiModelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class UserPreferences(private val context: Context) {

    private val sharedPrefs = try {
        context.getSharedPreferences("user_auth_cache", Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    fun isUserLoggedInSync(): Boolean {
        return try {
            sharedPrefs?.getBoolean("is_logged_in", false) ?: false
        } catch (_: Throwable) {
            false
        }
    }

    fun getSyncAuthInfo(): Triple<Boolean, String, String> {
        return try {
            val isLoggedIn = sharedPrefs?.getBoolean("is_logged_in", false) ?: false
            val email = sharedPrefs?.getString("user_email", "") ?: ""
            val name = sharedPrefs?.getString("user_name", "Scholar") ?: "Scholar"
            Triple(isLoggedIn, email, name)
        } catch (_: Throwable) {
            Triple(false, "", "Scholar")
        }
    }

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "System", "Light", "Dark"
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val AUTO_TTS_ENABLED = booleanPreferencesKey("auto_tts_enabled")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    private val safeDataFlow: Flow<Preferences> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            emit(emptyPreferences())
        }
    }

    val isLoggedIn: Flow<Boolean> = safeDataFlow.map { prefs ->
        prefs[IS_LOGGED_IN] ?: false
    }

    val appLanguage: Flow<String> = safeDataFlow.map { prefs ->
        prefs[APP_LANGUAGE] ?: "en"
    }

    val userEmail: Flow<String> = safeDataFlow.map { prefs ->
        prefs[USER_EMAIL] ?: ""
    }

    val userPhotoUrl: Flow<String> = safeDataFlow.map { prefs ->
        prefs[USER_PHOTO_URL] ?: ""
    }

    val themeMode: Flow<String> = safeDataFlow.map { prefs ->
        prefs[THEME_MODE] ?: "System"
    }

    val selectedModel: Flow<String> = safeDataFlow.map { prefs ->
        val saved = prefs[SELECTED_MODEL]
        GeminiModelConfig.sanitizeModelName(saved)
    }

    val userName: Flow<String> = safeDataFlow.map { prefs ->
        prefs[USER_NAME] ?: "Scholar"
    }

    val dailyGoalMinutes: Flow<Int> = safeDataFlow.map { prefs ->
        prefs[DAILY_GOAL_MINUTES] ?: 60
    }

    val autoTtsEnabled: Flow<Boolean> = safeDataFlow.map { prefs ->
        prefs[AUTO_TTS_ENABLED] ?: true
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE] = mode }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { prefs -> prefs[SELECTED_MODEL] = model }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[USER_NAME] = name }
    }

    suspend fun setAuth(isLoggedIn: Boolean, email: String, name: String, photoUrl: String = "") {
        try {
            sharedPrefs?.edit()
                ?.putBoolean("is_logged_in", isLoggedIn)
                ?.putString("user_email", email)
                ?.putString("user_name", name)
                ?.putString("user_photo_url", photoUrl)
                ?.apply()
        } catch (_: Throwable) {}
        try {
            context.dataStore.edit { prefs ->
                prefs[IS_LOGGED_IN] = isLoggedIn
                prefs[USER_EMAIL] = email
                prefs[USER_NAME] = name
                prefs[USER_PHOTO_URL] = photoUrl
            }
        } catch (_: Throwable) {}
    }

    suspend fun logout() {
        try {
            sharedPrefs?.edit()
                ?.putBoolean("is_logged_in", false)
                ?.putString("user_email", "")
                ?.putString("user_photo_url", "")
                ?.apply()
        } catch (_: Throwable) {}
        try {
            context.dataStore.edit { prefs ->
                prefs[IS_LOGGED_IN] = false
                prefs[USER_PHOTO_URL] = ""
            }
        } catch (_: Throwable) {}
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[DAILY_GOAL_MINUTES] = minutes }
    }

    suspend fun setAutoTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_TTS_ENABLED] = enabled }
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { prefs -> prefs[APP_LANGUAGE] = languageCode }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs -> prefs.clear() }
    }
}
