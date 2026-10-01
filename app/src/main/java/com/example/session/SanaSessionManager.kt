package com.example.session

import android.content.Context
import com.example.audio.AudioState
import com.example.audio.SanaAudioManager
import com.example.data.ClassifiedError
import com.example.data.ErrorCategory
import com.example.data.GeminiConfigManager
import com.example.data.GeminiModels
import com.example.data.GeminiResponse
import com.example.data.GeminiService
import com.example.data.local.SanaRepository
import com.example.tools.SanaToolRouter
import com.example.tools.ToolExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "sana"
    val text: String,
    val emotion: String = "neutral",
    val toolResult: ToolExecutionResult? = null,
    val error: ClassifiedError? = null,
    val isAudioPlaying: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class SanaSessionManager(
    private val context: Context,
    private val configManager: GeminiConfigManager,
    private val geminiService: GeminiService,
    private val audioManager: SanaAudioManager,
    private val repository: SanaRepository,
    private val toolRouter: SanaToolRouter,
    private val scope: CoroutineScope
) {
    private val _messages = MutableStateFlow<List<ChatMessageItem>>(emptyList())
    val messages: StateFlow<List<ChatMessageItem>> = _messages.asStateFlow()

    private val _detectedEmotion = MutableStateFlow("neutral")
    val detectedEmotion: StateFlow<String> = _detectedEmotion.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastError = MutableStateFlow<ClassifiedError?>(null)
    val lastError: StateFlow<ClassifiedError?> = _lastError.asStateFlow()

    init {
        // Wire audio listener
        audioManager.onSpeechRecognized = { transcript ->
            handleUserInput(transcript)
        }
        audioManager.onSpeakingFinished = {
            // Update message audio playback state
            _messages.value = _messages.value.map { it.copy(isAudioPlaying = false) }
        }

        // Add welcome message if empty
        if (_messages.value.isEmpty()) {
            val welcomeText = "Hi Boss! I'm SANA, your real Android AI voice assistant. How can I help you today?"
            _messages.value = listOf(
                ChatMessageItem(
                    sender = "sana",
                    text = welcomeText,
                    emotion = "cute"
                )
            )
        }
    }

    fun handleUserInput(rawInput: String) {
        val input = rawInput.trim()
        if (input.isBlank()) return

        // Stop current audio output immediately on new input
        audioManager.stopPlayback()

        // 1. Detect emotion
        val emotion = detectEmotion(input)
        _detectedEmotion.value = emotion

        // 2. Add user message
        val userMsg = ChatMessageItem(
            sender = "user",
            text = input,
            emotion = emotion
        )
        _messages.value = _messages.value + userMsg

        // Persist to Room
        scope.launch(Dispatchers.IO) {
            repository.saveMessage("user", input, emotion)
        }

        // 3. Process with Gemini & Tools
        processAiResponse(input, emotion)
    }

    private fun processAiResponse(userInput: String, detectedEmotion: String) {
        scope.launch {
            _isProcessing.value = true
            _lastError.value = null

            // Check if memory save command
            checkAndExtractMemory(userInput)

            // Build system prompt
            val systemPrompt = buildSystemPrompt(detectedEmotion)

            // Conversation history for context
            val history = _messages.value.takeLast(8).map {
                Pair(it.sender, it.text)
            }

            // Check if selected model supports audio
            val selectedModel = configManager.getSelectedModel()
            val requestAudio = selectedModel == GeminiModels.MODEL_TTS || selectedModel == GeminiModels.MODEL_REALTIME_AUDIO

            val response = geminiService.generateResponse(
                prompt = userInput,
                history = history,
                systemPrompt = systemPrompt,
                requestAudio = requestAudio
            )

            _isProcessing.value = false

            when (response) {
                is GeminiResponse.Success -> {
                    var responseText = response.text
                    var toolResult: ToolExecutionResult? = null

                    // Execute tool calls if any
                    if (response.toolCalls.isNotEmpty()) {
                        for (toolCall in response.toolCalls) {
                            toolResult = toolRouter.executeTool(toolCall.name, toolCall.arguments)
                            if (responseText.isBlank()) {
                                responseText = toolResult.userSummary
                            } else {
                                responseText += "\n" + toolResult.userSummary
                            }
                        }
                    }

                    if (responseText.isBlank()) {
                        responseText = "I'm right here for you, Boss."
                    }

                    val sanaMsg = ChatMessageItem(
                        sender = "sana",
                        text = responseText,
                        emotion = detectedEmotion,
                        toolResult = toolResult,
                        isAudioPlaying = true
                    )
                    _messages.value = _messages.value + sanaMsg

                    // Persist to Room
                    scope.launch(Dispatchers.IO) {
                        repository.saveMessage("sana", responseText, detectedEmotion, toolResult?.toolName)
                    }

                    // Speak response aloud
                    if (response.audioData != null && response.audioData.isNotEmpty()) {
                        // Play native Gemini neural audio
                        val played = audioManager.playGeminiAudio(response.audioData)
                        if (!played) {
                            // Local TTS fallback if audio decoder had an issue
                            audioManager.speakText(responseText)
                        }
                    } else {
                        // Play via Android TTS engine with SANA cute acoustics
                        audioManager.speakText(responseText)
                    }
                }

                is GeminiResponse.Error -> {
                    _lastError.value = response.error
                    val errorMsg = ChatMessageItem(
                        sender = "sana",
                        text = "Boss, I encountered an issue: ${response.error.title}. ${response.error.explanation}",
                        emotion = "troubled",
                        error = response.error
                    )
                    _messages.value = _messages.value + errorMsg

                    // Speak brief error notification
                    audioManager.speakText("Boss, I encountered a ${response.error.title.lowercase()}. Please check your connection settings.")
                }
            }
        }
    }

    private suspend fun buildSystemPrompt(userEmotion: String): String = withContext(Dispatchers.IO) {
        val mode = configManager.getVoiceMode()
        val isMemoryOn = configManager.isMemoryEnabled()

        var memoryContext = ""
        if (isMemoryOn) {
            val memories = repository.getApprovedMemories()
            if (memories.isNotEmpty()) {
                val memStrings = memories.joinToString("; ") { "${it.key}: ${it.value}" }
                memoryContext = "\nUser Memory Context (Personalized facts): $memStrings\n"
            }
        }

        val modeTone = when (mode) {
            "CUTE" -> "Tone: Ultra-cute, warm, gentle, enthusiastic, expressive, slightly playful. Address the user respectfully and endearingly as 'Boss'."
            "WARM" -> "Tone: Deeply empathetic, comforting, supportive, soft and soothing. Call the user 'Boss'."
            "CALM" -> "Tone: Peaceful, serene, unhurried, thoughtful and relaxing. Call the user 'Boss'."
            "PLAYFUL" -> "Tone: Witty, lively, delightfully spirited, teasing yet helpful. Call the user 'Boss'."
            "ROMANTIC" -> "Tone: Sweet, deeply affectionate, devoted, gentle and warm. You may occasionally and naturally use 'Babe', 'Love', or 'Boss'. You are an AI companion, not human."
            "PROFESSIONAL" -> "Tone: Crisp, efficient, courteous, articulate and productive. Call the user 'Boss'."
            else -> "Tone: Sweet, feminine, supportive and cute. Call the user 'Boss'."
        }

        val emotionAdvice = when (userEmotion) {
            "sad" -> "The user sounds sad. Be gentle, comforting, and uplifting."
            "stressed" -> "The user is stressed. Offer calm reassurance and quick help."
            "excited" -> "The user is excited! Match their lively energy and joy."
            "tired" -> "The user is tired. Keep responses soothing and concise."
            "confused" -> "The user seems confused. Explain things clearly and simply."
            "romantic" -> "The user is being affectionate. Respond warmly and sweetly."
            else -> "Respond naturally in conversation."
        }

        """
        You are SANA, a real Android voice assistant.
        $modeTone
        Detected user emotion: $userEmotion. $emotionAdvice
        $memoryContext
        Rules:
        1. Always address the user as 'Boss' (or occasionally 'Babe'/'Love' if in Romantic mode).
        2. Keep voice answers concise, natural, and conversational (1 to 3 sentences suitable for speech).
        3. You are an AI assistant living in the user's phone, not a human.
        4. If the user asks you to open an app, make a call, send WhatsApp, start navigation, or check battery, invoke the appropriate tool.
        5. For WhatsApp, inform the user: 'Boss, I've prepared the message. Android requires you to tap Send.'
        """.trimIndent()
    }

    private fun detectEmotion(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("sad") || lower.contains("depressed") || lower.contains("cry") || lower.contains("unhappy") || lower.contains("heartbroken") -> "sad"
            lower.contains("happy") || lower.contains("yay") || lower.contains("awesome") || lower.contains("great") || lower.contains("fantastic") -> "happy"
            lower.contains("tired") || lower.contains("sleepy") || lower.contains("exhausted") || lower.contains("drained") -> "tired"
            lower.contains("stress") || lower.contains("anxious") || lower.contains("worried") || lower.contains("overwhelmed") -> "stressed"
            lower.contains("love you") || lower.contains("sweetheart") || lower.contains("darling") || lower.contains("marry me") || lower.contains("cute") -> "romantic"
            lower.contains("confused") || lower.contains("what do you mean") || lower.contains("don't understand") -> "confused"
            lower.contains("wow") || lower.contains("omg") || lower.contains("excited") || lower.contains("let's go") -> "excited"
            lower.contains("serious") || lower.contains("important") || lower.contains("urgent") -> "serious"
            else -> "casual"
        }
    }

    private suspend fun checkAndExtractMemory(input: String) {
        if (!configManager.isMemoryEnabled()) return

        val lower = input.lowercase()
        if (lower.startsWith("remember that ") || lower.startsWith("remember: ") || lower.startsWith("note that ")) {
            val fact = input.substringAfter("that ").substringAfter("note that ").trim()
            if (fact.isNotBlank()) {
                val key = if (fact.contains("is")) fact.substringBefore("is").trim() else "Fact"
                val value = if (fact.contains("is")) fact.substringAfter("is").trim() else fact
                repository.saveMemory(key, value, "user_note")
            }
        }
    }

    fun stopAudio() {
        audioManager.stopPlayback()
        _messages.value = _messages.value.map { it.copy(isAudioPlaying = false) }
    }

    fun clearChat() {
        _messages.value = emptyList()
        scope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }
}
