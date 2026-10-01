package com.example.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.speech.SpeechRecognizer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.SanaAudioManager
import com.example.data.GeminiConfigManager
import com.example.data.GeminiModels
import com.example.data.GeminiService
import com.example.service.SanaForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticCheck(
    val id: String,
    val name: String,
    var isPassed: Boolean = false,
    var isChecked: Boolean = false,
    var details: String = "Pending check..."
)

@Composable
fun DiagnosticsScreen(
    configManager: GeminiConfigManager,
    geminiService: GeminiService,
    audioManager: SanaAudioManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }

    var checks by remember {
        mutableStateOf(
            listOf(
                DiagnosticCheck("auth", "Gemini Authentication"),
                DiagnosticCheck("model", "Gemini Model Compatibility"),
                DiagnosticCheck("network", "Network Connectivity"),
                DiagnosticCheck("mic", "Microphone Capability"),
                DiagnosticCheck("speaker", "Speaker & Audio Volume"),
                DiagnosticCheck("audio_session", "Audio Session & TTS"),
                DiagnosticCheck("permissions", "Android Permissions"),
                DiagnosticCheck("bg_service", "Background Service"),
                DiagnosticCheck("whatsapp", "WhatsApp Availability"),
                DiagnosticCheck("phone", "Phone Capability")
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B18))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SANA Diagnostics",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Runs complete 10-point technical validation across Gemini API, hardware audio, and Android capabilities.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    isRunning = true
                    checks = runAllDiagnostics(context, configManager, geminiService, audioManager)
                    isRunning = false
                }
            },
            enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7052FF)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("run_diagnostics_button")
        ) {
            if (isRunning) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Running Diagnostic Suite...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("RUN DIAGNOSTICS", fontWeight = FontWeight.Bold)
            }
        }

        // Summary Card
        val passedCount = checks.count { it.isChecked && it.isPassed }
        val checkedCount = checks.count { it.isChecked }
        if (checkedCount > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (passedCount == checkedCount) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFFB300).copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Diagnostic Summary: $passedCount / $checkedCount Passed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (passedCount == checkedCount) Color(0xFF00E676) else Color(0xFFFFD54F)
                        )
                        Text(
                            text = if (passedCount == checkedCount) "All system capabilities are operational." else "Some subsystems require attention or permissions.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Checklist
        checks.forEach { check ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            !check.isChecked -> Icons.Default.Warning
                            check.isPassed -> Icons.Default.CheckCircle
                            else -> Icons.Default.Close
                        },
                        contentDescription = null,
                        tint = when {
                            !check.isChecked -> Color.Gray
                            check.isPassed -> Color(0xFF00E676)
                            else -> Color(0xFFFF5252)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = check.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = check.details,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

suspend fun runAllDiagnostics(
    context: Context,
    configManager: GeminiConfigManager,
    geminiService: GeminiService,
    audioManager: SanaAudioManager
): List<DiagnosticCheck> = withContext(Dispatchers.IO) {
    val results = mutableListOf<DiagnosticCheck>()

    // 1. Gemini Authentication
    val authStatus = configManager.getAuthStatus()
    val isAuthConfigured = authStatus.isConfigured
    val authDetails = if (isAuthConfigured) {
        "Configured via ${authStatus.source.name} (${authStatus.maskedKey})"
    } else {
        "Missing Gemini API key"
    }
    results.add(DiagnosticCheck("auth", "Gemini Authentication", isAuthConfigured, true, authDetails))

    // 2. Gemini Model
    val selectedModel = configManager.getSelectedModel()
    val modelSupported = GeminiModels.AVAILABLE_MODELS.any { it.id == selectedModel }
    val modelAudio = selectedModel == GeminiModels.MODEL_TTS || selectedModel == GeminiModels.MODEL_REALTIME_AUDIO
    results.add(
        DiagnosticCheck(
            "model",
            "Gemini Model Compatibility",
            modelSupported,
            true,
            "Selected: $selectedModel (Audio capable: $modelAudio)"
        )
    )

    // 3. Network
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork
    val capabilities = cm.getNetworkCapabilities(network)
    val hasInternet = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    results.add(DiagnosticCheck("network", "Network Connectivity", hasInternet, true, if (hasInternet) "Connected to Internet" else "No active Internet connection"))

    // 4. Microphone
    val micAvailable = SpeechRecognizer.isRecognitionAvailable(context)
    val micPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    results.add(DiagnosticCheck("mic", "Microphone Capability", micAvailable && micPermission, true, "Available: $micAvailable, Permission: $micPermission"))

    // 5. Speaker
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    results.add(DiagnosticCheck("speaker", "Speaker & Audio Volume", volume > 0, true, "Media Volume: $volume / $maxVolume"))

    // 6. Audio Session & TTS
    val voiceDiag = audioManager.getDiagnostics()
    results.add(DiagnosticCheck("audio_session", "Audio Session & TTS", voiceDiag.ttsInitialized, true, "Engine: ${voiceDiag.ttsEngine}, Voice: ${voiceDiag.activeVoiceName}"))

    // 7. Android Permissions
    val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasContacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val hasPhone = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val permScore = (if (hasMic) 1 else 0) + (if (hasContacts) 1 else 0) + (if (hasPhone) 1 else 0)
    results.add(DiagnosticCheck("permissions", "Android Permissions", hasMic, true, "Mic: $hasMic, Contacts: $hasContacts, Phone: $hasPhone"))

    // 8. Background Service
    val bgServiceRunning = SanaForegroundService.isRunning
    results.add(DiagnosticCheck("bg_service", "Background Service", true, true, if (bgServiceRunning) "Running active foreground listener" else "Ready to launch upon demand"))

    // 9. WhatsApp Availability
    val pm = context.packageManager
    val isWhatsAppInstalled = try {
        pm.getPackageInfo("com.whatsapp", 0)
        true
    } catch (e: Exception) {
        false
    }
    results.add(DiagnosticCheck("whatsapp", "WhatsApp Availability", isWhatsAppInstalled, true, if (isWhatsAppInstalled) "WhatsApp package detected" else "Not installed (intents will open web/store)"))

    // 10. Phone Capability
    val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    results.add(DiagnosticCheck("phone", "Phone Capability", hasTelephony, true, if (hasTelephony) "Telephony hardware detected" else "No cellular hardware (tablet/emulator)"))

    results
}
