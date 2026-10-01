package com.example.data

object GeminiModels {
    // Current supported preview models per official guidelines
    const val MODEL_TEXT_FAST = "gemini-3.5-flash"
    const val MODEL_TEXT_PRO = "gemini-3.1-pro-preview"
    const val MODEL_TTS = "gemini-2.5-flash-preview-tts"
    const val MODEL_REALTIME_AUDIO = "gemini-2.5-flash-native-audio-preview-12-2025"

    val AVAILABLE_MODELS = listOf(
        ModelOption(
            id = MODEL_TEXT_FAST,
            name = "Gemini 3.5 Flash",
            description = "Fastest multimodal model for general queries & tool calling",
            supportsAudioOutput = false
        ),
        ModelOption(
            id = MODEL_TTS,
            name = "Gemini 2.5 Flash TTS",
            description = "Specialized Gemini speech synthesis model with native audio responses",
            supportsAudioOutput = true
        ),
        ModelOption(
            id = MODEL_REALTIME_AUDIO,
            name = "Gemini 2.5 Flash Native Audio",
            description = "Low-latency bidirectional native audio conversation",
            supportsAudioOutput = true
        ),
        ModelOption(
            id = MODEL_TEXT_PRO,
            name = "Gemini 3.1 Pro Preview",
            description = "Advanced reasoning and complex phone automation",
            supportsAudioOutput = false
        )
    )

    // Actually supported Gemini Voice names
    val GEMINI_VOICES = listOf(
        "Kore",    // Feminine, warm, clear (default for SANA)
        "Aoede",   // Feminine, gentle, expressive
        "Puck",    // Playful, vibrant
        "Fenrir",  // Deep, masculine
        "Charon"   // Calm, resonant
    )
}

data class ModelOption(
    val id: String,
    val name: String,
    val description: String,
    val supportsAudioOutput: Boolean
)
