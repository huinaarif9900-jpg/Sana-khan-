package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiResponse {
    data class Success(
        val text: String,
        val audioData: ByteArray? = null,
        val audioMimeType: String? = null,
        val toolCalls: List<ToolCallRequest> = emptyList()
    ) : GeminiResponse()

    data class Error(val error: ClassifiedError) : GeminiResponse()
}

data class ToolCallRequest(
    val name: String,
    val arguments: Map<String, String>
)

class GeminiService(private val configManager: GeminiConfigManager) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Safe connectivity test. Sends a tiny prompt "ping".
     */
    suspend fun testConnection(): Pair<Boolean, ClassifiedError?> = withContext(Dispatchers.IO) {
        val apiKey = configManager.getActiveApiKey()
        if (apiKey.isBlank()) {
            val err = ClassifiedError(
                category = ErrorCategory.AUTHENTICATION_ERROR,
                title = "API Key Missing",
                technicalDetails = "No Gemini API key resolved from BuildConfig or settings",
                explanation = "A Gemini API key is required to connect to Google's AI services.",
                suggestedFix = "Add GEMINI_API_KEY in AI Studio Secrets panel, or enter your key in Gemini Connection settings."
            )
            return@withContext Pair(false, err)
        }

        val requestJson = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                val turn = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", "ping") })
                    }
                    put("parts", parts)
                }
                put(turn)
            }
            put("contents", contentsArr)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${GeminiModels.MODEL_TEXT_FAST}:generateContent?key=$apiKey"
        val body = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Pair(true, null)
                } else {
                    val classified = GeminiErrorClassifier.classifyHttp(response.code, responseString)
                    Pair(false, classified)
                }
            }
        } catch (t: Throwable) {
            val classified = GeminiErrorClassifier.classifyException(t)
            Pair(false, classified)
        }
    }

    /**
     * Generates a conversational response with optional audio synthesis and tool declaration.
     */
    suspend fun generateResponse(
        prompt: String,
        history: List<Pair<String, String>>, // sender ("user" or "model") to text
        systemPrompt: String,
        requestAudio: Boolean = false,
        modelOverride: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val apiKey = configManager.getActiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext GeminiResponse.Error(
                ClassifiedError(
                    category = ErrorCategory.AUTHENTICATION_ERROR,
                    title = "API Key Not Configured",
                    technicalDetails = "Missing API key in active session",
                    explanation = "Please configure your Gemini API key before speaking with SANA.",
                    suggestedFix = "Go to SANA Settings → Gemini Connection to set your key."
                )
            )
        }

        val selectedModel = modelOverride ?: if (requestAudio) {
            configManager.getSelectedModel()
        } else {
            GeminiModels.MODEL_TEXT_FAST
        }

        val requestJson = JSONObject()

        // System Instruction
        requestJson.put("systemInstruction", JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", systemPrompt) })
            })
        })

        // Conversation history
        val contentsArr = JSONArray()
        for ((sender, message) in history.takeLast(10)) {
            val contentObj = JSONObject().apply {
                put("role", if (sender == "user") "user" else "model")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", message) })
                })
            }
            contentsArr.put(contentObj)
        }
        // Current user message
        contentsArr.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", prompt) })
            })
        })
        requestJson.put("contents", contentsArr)

        // Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.7)

        val voiceName = configManager.getSelectedVoice()

        if (requestAudio && (selectedModel == GeminiModels.MODEL_TTS || selectedModel == GeminiModels.MODEL_REALTIME_AUDIO)) {
            // Audio response modality
            val modalities = JSONArray().apply {
                put("TEXT")
                put("AUDIO")
            }
            genConfig.put("responseModalities", modalities)
            genConfig.put("speechConfig", JSONObject().apply {
                put("voiceConfig", JSONObject().apply {
                    put("prebuiltVoiceConfig", JSONObject().apply {
                        put("voiceName", voiceName)
                    })
                })
            })
        }
        requestJson.put("generationConfig", genConfig)

        // Tool Declarations for Android Phone Control
        val toolsArray = JSONArray().apply {
            val toolObj = JSONObject().apply {
                put("functionDeclarations", buildToolDeclarations())
            }
            put(toolObj)
        }
        requestJson.put("tools", toolsArray)

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val classified = GeminiErrorClassifier.classifyHttp(response.code, responseString)
                    return@withContext GeminiResponse.Error(classified)
                }

                parseGeminiResponse(responseString)
            }
        } catch (t: Throwable) {
            val classified = GeminiErrorClassifier.classifyException(t)
            GeminiResponse.Error(classified)
        }
    }

    /**
     * Specifically generates SANA speech for a fixed text phrase using Gemini Audio TTS model.
     */
    suspend fun generateSpeechAudio(text: String): Pair<ByteArray?, ClassifiedError?> = withContext(Dispatchers.IO) {
        val apiKey = configManager.getActiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Pair(
                null,
                ClassifiedError(
                    category = ErrorCategory.AUTHENTICATION_ERROR,
                    title = "Authentication Missing",
                    technicalDetails = "No Gemini API key available for TTS generation",
                    explanation = "A Gemini API key is required to synthesize natural voice via Gemini TTS.",
                    suggestedFix = "Configure GEMINI_API_KEY in Gemini Connection settings."
                )
            )
        }

        val voiceName = configManager.getSelectedVoice()
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply { put("AUDIO") })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${GeminiModels.MODEL_TTS}:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val classified = GeminiErrorClassifier.classifyHttp(response.code, responseString)
                    return@withContext Pair(null, classified)
                }

                val json = JSONObject(responseString)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val base64Data = inlineData.optString("data")
                                if (base64Data.isNotBlank()) {
                                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                    return@withContext Pair(bytes, null)
                                }
                            }
                        }
                    }
                }

                Pair(
                    null,
                    GeminiErrorClassifier.voiceError(
                        "Audio model returned text instead of audio data.",
                        "No inlineData audio part found in candidate response"
                    )
                )
            }
        } catch (t: Throwable) {
            Pair(null, GeminiErrorClassifier.classifyException(t))
        }
    }

    private fun parseGeminiResponse(responseString: String): GeminiResponse {
        try {
            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return GeminiResponse.Error(
                    ClassifiedError(
                        category = ErrorCategory.MODEL_ERROR,
                        title = "Empty Model Response",
                        technicalDetails = responseString,
                        explanation = "The model produced no candidates, possibly due to safety filters.",
                        suggestedFix = "Try rephrasing your request or adjust safety thresholds."
                    )
                )
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()

            val textBuilder = StringBuilder()
            var audioBytes: ByteArray? = null
            var mimeType: String? = null
            val toolCalls = mutableListOf<ToolCallRequest>()

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                // Text
                if (part.has("text")) {
                    textBuilder.append(part.getString("text"))
                }

                // Inline audio
                if (part.has("inlineData")) {
                    val inlineData = part.getJSONObject("inlineData")
                    mimeType = inlineData.optString("mimeType")
                    val data = inlineData.optString("data")
                    if (data.isNotBlank()) {
                        audioBytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                    }
                }

                // Function Call
                if (part.has("functionCall")) {
                    val fnCall = part.getJSONObject("functionCall")
                    val name = fnCall.optString("name")
                    val argsObj = fnCall.optJSONObject("args")
                    val argsMap = mutableMapOf<String, String>()
                    if (argsObj != null) {
                        val keys = argsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            argsMap[key] = argsObj.optString(key)
                        }
                    }
                    toolCalls.add(ToolCallRequest(name = name, arguments = argsMap))
                }
            }

            return GeminiResponse.Success(
                text = textBuilder.toString().trim(),
                audioData = audioBytes,
                audioMimeType = mimeType,
                toolCalls = toolCalls
            )
        } catch (e: Exception) {
            return GeminiResponse.Error(
                ClassifiedError(
                    category = ErrorCategory.MODEL_ERROR,
                    title = "Response Parsing Failed",
                    technicalDetails = e.message.orEmpty(),
                    explanation = "Failed to parse the Gemini JSON response structure.",
                    suggestedFix = "Verify response format and API version compatibility."
                )
            )
        }
    }

    private fun buildToolDeclarations(): JSONArray {
        val array = JSONArray()

        fun addFunction(name: String, description: String, requiredParams: List<String>, properties: Map<String, Pair<String, String>>) {
            val fn = JSONObject()
            fn.put("name", name)
            fn.put("description", description)

            val params = JSONObject()
            params.put("type", "OBJECT")
            val props = JSONObject()
            properties.forEach { (propName, typeAndDesc) ->
                val prop = JSONObject()
                prop.put("type", typeAndDesc.first)
                prop.put("description", typeAndDesc.second)
                props.put(propName, prop)
            }
            params.put("properties", props)

            val reqArr = JSONArray()
            requiredParams.forEach { reqArr.put(it) }
            params.put("required", reqArr)

            fn.put("parameters", params)
            array.put(fn)
        }

        addFunction(
            "openApp",
            "Opens an installed Android app by name or package",
            listOf("appName"),
            mapOf("appName" to Pair("STRING", "Name of the app (e.g. YouTube, WhatsApp, Spotify, Settings)"))
        )

        addFunction(
            "findContact",
            "Searches Android contacts by name to retrieve phone number",
            listOf("name"),
            mapOf("name" to Pair("STRING", "Name of the contact to search for"))
        )

        addFunction(
            "makeCall",
            "Initiates a phone call or opens the dialer for a phone number or contact",
            listOf("phoneNumber"),
            mapOf("phoneNumber" to Pair("STRING", "The phone number or contact name to call"))
        )

        addFunction(
            "openWhatsApp",
            "Prepares a WhatsApp message for a phone number or contact",
            listOf("message"),
            mapOf(
                "phoneNumber" to Pair("STRING", "Phone number with country code, if available"),
                "contactName" to Pair("STRING", "Contact name to message"),
                "message" to Pair("STRING", "The text message content to send")
            )
        )

        addFunction(
            "composeMessage",
            "Prepares an SMS text message",
            listOf("message"),
            mapOf(
                "phoneNumber" to Pair("STRING", "Phone number or contact"),
                "message" to Pair("STRING", "Message text")
            )
        )

        addFunction(
            "openCamera",
            "Opens the device camera to take photos or record video",
            emptyList(),
            emptyMap()
        )

        addFunction(
            "openGallery",
            "Opens the device gallery or photo picker",
            emptyList(),
            emptyMap()
        )

        addFunction(
            "openMaps",
            "Searches for places or shows map for a location",
            listOf("query"),
            mapOf("query" to Pair("STRING", "Location, restaurant, or address to search on Maps"))
        )

        addFunction(
            "startNavigation",
            "Starts GPS turn-by-turn navigation to a destination",
            listOf("destination"),
            mapOf("destination" to Pair("STRING", "Destination address or city name"))
        )

        addFunction(
            "mediaControl",
            "Controls media playback: play, pause, next, previous",
            listOf("action"),
            mapOf("action" to Pair("STRING", "Action to perform: 'play', 'pause', 'next', 'previous'"))
        )

        addFunction(
            "openSettings",
            "Opens device settings such as wifi, bluetooth, sound, or main settings",
            emptyList(),
            mapOf("settingType" to Pair("STRING", "Specific setting e.g. 'wifi', 'bluetooth', 'sound', 'all'"))
        )

        addFunction(
            "getBatteryStatus",
            "Checks the current battery percentage and charging state",
            emptyList(),
            emptyMap()
        )

        return array
    }
}
