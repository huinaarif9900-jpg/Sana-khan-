package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SanaAudioManager
import com.example.data.ClassifiedError
import com.example.data.GeminiConfigManager
import com.example.data.GeminiErrorClassifier
import com.example.data.GeminiModels
import com.example.data.GeminiService
import kotlinx.coroutines.launch

@Composable
fun VoiceStudioScreen(
    audioManager: SanaAudioManager,
    configManager: GeminiConfigManager,
    geminiService: GeminiService,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val lastVoiceError by audioManager.lastVoiceError.collectAsState()

    var activeMode by remember { mutableStateOf(configManager.getVoiceMode()) }
    var activeLang by remember { mutableStateOf(configManager.getLanguageCode()) }
    var activeVoice by remember { mutableStateOf(configManager.getSelectedVoice()) }

    var pitch by remember { mutableFloatStateOf(configManager.getSpeechPitch()) }
    var rate by remember { mutableFloatStateOf(configManager.getSpeechRate()) }

    var isTestingVoice by remember { mutableStateOf(false) }
    var voiceTestError by remember { mutableStateOf<ClassifiedError?>(null) }
    var voiceSuccessMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B18))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header ---
        Text(
            text = "Voice & Personality Studio",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Fine-tune SANA's natural feminine voice, speech acoustic pitch, language, and behavioral modes.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        // --- Voice Test Card ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice Output Verification",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "REAL AUDIO ENGINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Press to test SANA's voice phrase: \"Hi Boss. I'm SANA. How does my voice sound?\"",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val testPhrase = "Hi Boss. I'm SANA. How does my voice sound?"
                        voiceTestError = null
                        voiceSuccessMessage = null

                        val selectedModel = configManager.getSelectedModel()
                        val modelSupportsAudio = selectedModel == GeminiModels.MODEL_TTS ||
                                selectedModel == GeminiModels.MODEL_REALTIME_AUDIO

                        if (modelSupportsAudio && configManager.isConfigured()) {
                            // Synthesize using Gemini Audio API
                            coroutineScope.launch {
                                isTestingVoice = true
                                val (audioBytes, err) = geminiService.generateSpeechAudio(testPhrase)
                                isTestingVoice = false
                                if (audioBytes != null && audioBytes.isNotEmpty()) {
                                    val played = audioManager.playGeminiAudio(audioBytes)
                                    if (played) {
                                        voiceSuccessMessage = "Gemini neural audio synthesised & playing ($activeVoice voice)."
                                    } else {
                                        voiceTestError = GeminiErrorClassifier.voiceError(
                                            "Android MediaPlayer could not decode audio container.",
                                            audioManager.lastVoiceError.value.orEmpty()
                                        )
                                    }
                                } else {
                                    voiceTestError = err ?: GeminiErrorClassifier.voiceError(
                                        "Voice synthesis did not return audio data.",
                                        "Null audio bytes from API"
                                    )
                                    // Fallback to local TTS
                                    audioManager.speakText(testPhrase)
                                }
                            }
                        } else {
                            // Use Android acoustic engine with cute feminine pitch
                            try {
                                audioManager.speakText(testPhrase)
                                voiceSuccessMessage = "Playing via Android TTS engine with SANA cute acoustics (${pitch}x pitch)."
                            } catch (e: Exception) {
                                voiceTestError = GeminiErrorClassifier.voiceError(
                                    "Android speech synthesis failed: ${e.message}",
                                    e.stackTraceToString()
                                )
                            }
                        }
                    },
                    enabled = !isTestingVoice,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_voice_button")
                ) {
                    if (isTestingVoice) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synthesizing Voice...")
                    } else {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TEST VOICE", fontWeight = FontWeight.Bold)
                    }
                }

                // Success confirmation
                if (voiceSuccessMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = voiceSuccessMessage!!,
                        fontSize = 12.sp,
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Error Diagnostic Box
                if (voiceTestError != null || lastVoiceError != null) {
                    val err = voiceTestError
                    val reason = err?.explanation ?: lastVoiceError ?: "Unknown voice error"
                    val technical = err?.technicalDetails ?: lastVoiceError.orEmpty()

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF5252).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VOICE_ERROR",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8A80)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = reason, fontSize = 12.sp, color = Color.White)
                            if (technical.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "Technical reason: $technical", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // Quick Action Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        voiceTestError = null
                                        audioManager.speakText("Hi Boss. I'm SANA. How does my voice sound?")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = onNavigateToPermissions,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Check Mic", fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gemini Key", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { audioManager.applyVoiceSettings() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Speaker", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Voice Modes ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Personality & Voice Modes",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Changes speaking style, emotional inflection, and response wording naturally.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                val modes = listOf(
                    "CUTE" to "Feminine, sweet, warm, high-spirited, calls you 'Boss'",
                    "WARM" to "Gentle, supportive, comforting, soothing presence",
                    "CALM" to "Serene, tranquil, peaceful, steady guidance",
                    "PLAYFUL" to "Witty, delightfully lively, energetic teasing",
                    "ROMANTIC" to "Affectionate, tender, occasional 'Babe/Love/Boss'",
                    "PROFESSIONAL" to "Crisp, concise, efficient, courteous and direct"
                )

                modes.forEach { (modeName, modeDesc) ->
                    val isSelected = activeMode == modeName
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF2E2A4A) else Color(0xFF1E1B33),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            activeMode = modeName
                            configManager.setVoiceMode(modeName)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    activeMode = modeName
                                    configManager.setVoiceMode(modeName)
                                },
                                label = { Text(modeName, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF7052FF),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF171427),
                                    labelColor = Color(0xFFE0E0FF)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = modeDesc,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // --- Gemini Voice Selector ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Gemini Neural Voice Selection",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Supported official Gemini voice names. 'Kore' is SANA's default ultra-cute feminine voice.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GeminiModels.GEMINI_VOICES.forEach { voice ->
                        val isSelected = activeVoice == voice
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                activeVoice = voice
                                configManager.setSelectedVoice(voice)
                            },
                            label = { Text(voice) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF4081),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E1B33),
                                labelColor = Color(0xFFE0E0FF)
                            )
                        )
                    }
                }
            }
        }

        // --- Multilingual Voice Selection ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Multilingual Support",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configures speech recognition and synthesis locale for SANA.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                val languages = listOf(
                    "English (US)" to "en-US",
                    "Malay" to "ms-MY",
                    "Urdu" to "ur-PK",
                    "Hindi" to "hi-IN"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { (langLabel, code) ->
                        val isSelected = activeLang == code
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                activeLang = code
                                configManager.setLanguageCode(code)
                                audioManager.applyVoiceSettings()
                            },
                            label = { Text(langLabel, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF38E8C6),
                                selectedLabelColor = Color(0xFF0D0B18),
                                containerColor = Color(0xFF1E1B33),
                                labelColor = Color(0xFFE0E0FF)
                            )
                        )
                    }
                }
            }
        }

        // --- Acoustic Sliders ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Acoustic Pitch & Rate Tuning",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Voice Pitch (Feminine)", fontSize = 13.sp, color = Color.White)
                    Text("${String.format("%.2f", pitch)}x", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF7BB0))
                }
                Slider(
                    value = pitch,
                    onValueChange = {
                        pitch = it
                        configManager.setSpeechPitch(it)
                        audioManager.applyVoiceSettings()
                    },
                    valueRange = 0.8f..1.6f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF7BB0),
                        activeTrackColor = Color(0xFFFF7BB0)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Speech Rate (Speed)", fontSize = 13.sp, color = Color.White)
                    Text("${String.format("%.2f", rate)}x", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7052FF))
                }
                Slider(
                    value = rate,
                    onValueChange = {
                        rate = it
                        configManager.setSpeechRate(it)
                        audioManager.applyVoiceSettings()
                    },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7052FF),
                        activeTrackColor = Color(0xFF7052FF)
                    )
                )
            }
        }
    }
}
