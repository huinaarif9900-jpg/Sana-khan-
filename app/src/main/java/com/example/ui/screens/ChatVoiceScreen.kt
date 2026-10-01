package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioState
import com.example.audio.SanaAudioManager
import com.example.data.GeminiConfigManager
import com.example.session.ChatMessageItem
import com.example.session.SanaSessionManager
import com.example.ui.components.AudioWaveform
import com.example.ui.components.SanaOrb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatVoiceScreen(
    sessionManager: SanaSessionManager,
    audioManager: SanaAudioManager,
    configManager: GeminiConfigManager,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by sessionManager.messages.collectAsState()
    val audioState by audioManager.audioState.collectAsState()
    val audioLevel by audioManager.audioLevel.collectAsState()
    val isMuted by audioManager.isMuted.collectAsState()
    val isProcessing by sessionManager.isProcessing.collectAsState()
    val detectedEmotion by sessionManager.detectedEmotion.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B18))
            .padding(horizontal = 16.dp)
    ) {
        // --- Top Bar Status Indicators ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connection indicator dot
                val isConfigured = configManager.isConfigured()
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isConfigured) Color(0xFF00E676) else Color(0xFFFFB300))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConfigured) "Gemini Live" else "Setup Needed",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(8.dp))
                // Mode badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF7052FF).copy(alpha = 0.25f)
                ) {
                    Text(
                        text = configManager.getVoiceMode(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF7BB0),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { audioManager.toggleMute() },
                    modifier = Modifier.testTag("mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Mute",
                        tint = if (isMuted) Color(0xFFFF5252) else Color(0xFF38E8C6)
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Connection Settings",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // --- Center AI Voice Assistant Orb & Waveform ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SanaOrb(
                audioState = audioState,
                audioLevel = audioLevel,
                emotion = detectedEmotion,
                size = 140.dp
            )

            Spacer(modifier = Modifier.height(6.dp))

            val stateLabel = when {
                isProcessing -> "SANA is thinking..."
                audioState == AudioState.LISTENING -> "Listening to Boss..."
                audioState == AudioState.SPEAKING -> "SANA speaking..."
                audioState == AudioState.ERROR -> "Voice Error"
                else -> "Boss, tap Push to Talk or ask me anything"
            }

            Text(
                text = stateLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = when (audioState) {
                    AudioState.LISTENING -> Color(0xFF00E5FF)
                    AudioState.SPEAKING -> Color(0xFFFF4081)
                    AudioState.ERROR -> Color(0xFFFF5252)
                    else -> Color.White.copy(alpha = 0.7f)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
            AudioWaveform(
                audioState = audioState,
                audioLevel = audioLevel,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // --- Quick Tool Suggestions ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "Check Battery" to "What is my battery level?",
                "Open WhatsApp" to "Message Ahmed on WhatsApp I'll call you later",
                "Open Camera" to "Open the camera",
                "Call Ahmed" to "Call Ahmed",
                "Play Music" to "Play some music",
                "Open Maps" to "Open maps to nearest coffee shop",
                "Who are you?" to "Introduce yourself, SANA!"
            )
            items(suggestions) { (chipLabel, prompt) ->
                SuggestionChip(
                    onClick = { sessionManager.handleUserInput(prompt) },
                    label = { Text(chipLabel, fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF1E1B33),
                        labelColor = Color(0xFFE0E0FF)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF7052FF).copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("suggestion_${chipLabel.replace(" ", "_").lowercase()}")
                )
            }
        }

        // --- Conversation History ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg = msg)
            }
        }

        // --- Bottom Interaction & Push-To-Talk ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Input
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Message SANA...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7052FF),
                    unfocusedBorderColor = Color(0xFF2E2A4A),
                    focusedContainerColor = Color(0xFF171427),
                    unfocusedContainerColor = Color(0xFF171427),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 2,
                trailingIcon = {
                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val text = textInput
                                textInput = ""
                                sessionManager.handleUserInput(text)
                            },
                            modifier = Modifier.testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color(0xFF7052FF)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Interrupt Button (visible when SANA is speaking)
            if (audioState == AudioState.SPEAKING) {
                IconButton(
                    onClick = { sessionManager.stopAudio() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFFF5252).copy(alpha = 0.2f), CircleShape)
                        .testTag("interrupt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop SANA speaking",
                        tint = Color(0xFFFF5252)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Push-To-Talk Primary Button
            FloatingActionButton(
                onClick = {
                    if (audioState == AudioState.LISTENING) {
                        audioManager.stopListening()
                    } else {
                        audioManager.startListening()
                    }
                },
                shape = CircleShape,
                containerColor = if (audioState == AudioState.LISTENING) Color(0xFF00E5FF) else Color(0xFF7052FF),
                contentColor = Color.White,
                modifier = Modifier
                    .size(54.dp)
                    .testTag("push_to_talk_button")
            ) {
                Icon(
                    imageVector = if (audioState == AudioState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Push To Talk",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessageItem) {
    val isUser = msg.sender == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF7052FF) else Color(0xFF1E1B33)
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header (sender name & tool pill)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "Boss" else "SANA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.9f) else Color(0xFFFF7BB0)
                    )

                    if (msg.toolResult != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "⚡ ${msg.toolResult.toolName}",
                                fontSize = 10.sp,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = msg.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                // If tool required user action (e.g. WhatsApp / SMS / Dialer confirmation)
                if (msg.toolResult?.requiresUserAction == true) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android confirmation required: tap Send/Call on the opened app.",
                        fontSize = 11.sp,
                        color = Color(0xFFFFD54F)
                    )
                }

                // Error details if any
                if (msg.error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFF5252).copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Category: ${msg.error.category.name}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A80)
                            )
                            Text(
                                text = "Fix: ${msg.error.suggestedFix}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}
