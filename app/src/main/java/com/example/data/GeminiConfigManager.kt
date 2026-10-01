package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

enum class KeySource {
    BUILD_CONFIG,
    USER_CUSTOM,
    NONE
}

data class AuthStatus(
    val isConfigured: Boolean,
    val source: KeySource,
    val maskedKey: String,
    val statusDescription: String
)

class GeminiConfigManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sana_secure_config"
        private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_LANGUAGE = "language_code"
        private const val KEY_MEMORY_ENABLED = "memory_enabled"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_SPEECH_RATE = "speech_rate"

        fun maskKey(key: String): String {
            if (key.isBlank()) return "Not configured"
            if (key.length <= 8) return "••••••••"
            val start = key.take(4)
            val end = key.takeLast(4)
            return "$start••••••••$end"
        }
    }

    /**
     * Resolves the active Gemini API key:
     * 1. First priority: BuildConfig.GEMINI_API_KEY (injected by AI Studio from secrets)
     * 2. Second priority: Custom user key configured via Settings
     * 3. Empty string if not configured.
     * Note: Does NOT print or log the raw key.
     */
    fun getActiveApiKey(): String {
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (isValidKeyString(buildConfigKey)) {
            return buildConfigKey.trim()
        }

        val customKey = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        if (isValidKeyString(customKey)) {
            return customKey.trim()
        }

        return ""
    }

    fun isConfigured(): Boolean {
        return getActiveApiKey().isNotBlank()
    }

    fun getAuthStatus(): AuthStatus {
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (isValidKeyString(buildConfigKey)) {
            return AuthStatus(
                isConfigured = true,
                source = KeySource.BUILD_CONFIG,
                maskedKey = maskKey(buildConfigKey),
                statusDescription = "Configured securely via Secrets panel (BuildConfig)"
            )
        }

        val customKey = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        if (isValidKeyString(customKey)) {
            return AuthStatus(
                isConfigured = true,
                source = KeySource.USER_CUSTOM,
                maskedKey = maskKey(customKey),
                statusDescription = "Configured via SANA Connection Settings"
            )
        }

        return AuthStatus(
            isConfigured = false,
            source = KeySource.NONE,
            maskedKey = "No key configured",
            statusDescription = "Missing Gemini API key"
        )
    }

    fun setCustomApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_CUSTOM_API_KEY, clean).apply()
    }

    fun clearConfiguration() {
        prefs.edit().remove(KEY_CUSTOM_API_KEY).apply()
    }

    private fun isValidKeyString(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val trimmed = key.trim()
        if (trimmed == "MY_GEMINI_API_KEY" || trimmed.startsWith("YOUR_") || trimmed.length < 10) {
            return false
        }
        return true
    }

    // Model selection
    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, GeminiModels.MODEL_TTS) ?: GeminiModels.MODEL_TTS
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
    }

    // Voice name
    fun getSelectedVoice(): String {
        return prefs.getString(KEY_SELECTED_VOICE, "Kore") ?: "Kore"
    }

    fun setSelectedVoice(voice: String) {
        prefs.edit().putString(KEY_SELECTED_VOICE, voice).apply()
    }

    // Voice modes: CUTE, WARM, CALM, PLAYFUL, ROMANTIC, PROFESSIONAL
    fun getVoiceMode(): String {
        return prefs.getString(KEY_VOICE_MODE, "CUTE") ?: "CUTE"
    }

    fun setVoiceMode(mode: String) {
        prefs.edit().putString(KEY_VOICE_MODE, mode).apply()
    }

    // Language code
    fun getLanguageCode(): String {
        return prefs.getString(KEY_LANGUAGE, "en-US") ?: "en-US"
    }

    fun setLanguageCode(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
    }

    // Memory toggle
    fun isMemoryEnabled(): Boolean {
        return prefs.getBoolean(KEY_MEMORY_ENABLED, true)
    }

    fun setMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply()
    }

    // Wake word toggle
    fun isWakeWordEnabled(): Boolean {
        return prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
    }

    // Speech synthesis tuning
    fun getSpeechPitch(): Float {
        return prefs.getFloat(KEY_SPEECH_PITCH, 1.25f) // cute feminine pitch
    }

    fun setSpeechPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_SPEECH_PITCH, pitch).apply()
    }

    fun getSpeechRate(): Float {
        return prefs.getFloat(KEY_SPEECH_RATE, 1.05f) // natural conversational speed
    }

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate).apply()
    }
}
