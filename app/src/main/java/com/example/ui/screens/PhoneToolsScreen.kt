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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tools.SanaToolRouter
import com.example.tools.ToolExecutionResult

@Composable
fun PhoneToolsScreen(
    toolRouter: SanaToolRouter,
    modifier: Modifier = Modifier
) {
    var lastResult by remember { mutableStateOf<ToolExecutionResult?>(null) }

    var appQuery by remember { mutableStateOf("YouTube") }
    var contactQuery by remember { mutableStateOf("Ahmed") }
    var callQuery by remember { mutableStateOf("1234567890") }
    var whatsAppPhone by remember { mutableStateOf("") }
    var whatsAppContact by remember { mutableStateOf("Ahmed") }
    var whatsAppMessage by remember { mutableStateOf("I'll call you later.") }
    var mapsQuery by remember { mutableStateOf("San Francisco") }
    var navDestination by remember { mutableStateOf("Golden Gate Bridge") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B18))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Android Phone Tools",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Real Android system operations. Never simulated. If Android restricts an action, the exact limitation is reported.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        // Result banner
        if (lastResult != null) {
            val res = lastResult!!
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (res.success) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Tool: ${res.toolName} — ${if (res.success) "Executed" else "Restricted / Failed"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (res.success) Color(0xFF00E676) else Color(0xFFFF8A80)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = res.userSummary, fontSize = 12.sp, color = Color.White)
                    if (res.technicalDetails != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Technical info: ${res.technicalDetails}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // --- 1. WhatsApp Tool Card ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("openWhatsApp()", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Prepares and dispatches WhatsApp conversation intent with contact and message pre-filled.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = whatsAppContact,
                    onValueChange = { whatsAppContact = it },
                    label = { Text("Contact Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = whatsAppMessage,
                    onValueChange = { whatsAppMessage = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        lastResult = toolRouter.openWhatsApp(
                            phoneNumber = whatsAppPhone,
                            message = whatsAppMessage,
                            contactName = whatsAppContact
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send via WhatsApp", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- 2. Phone Calls & Contacts ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF7052FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Phone Calling & Contacts", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = callQuery,
                    onValueChange = { callQuery = it },
                    label = { Text("Phone Number or Contact Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { lastResult = toolRouter.makeCall(callQuery) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7052FF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("makeCall()")
                    }
                    Button(
                        onClick = { lastResult = toolRouter.findContact(contactQuery) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2A4A)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("findContact()")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { lastResult = toolRouter.answerCall() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("answerCall()", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { lastResult = toolRouter.endCall() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("endCall()", fontSize = 11.sp)
                    }
                }
            }
        }

        // --- 3. App Launcher ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("openApp(appName)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    label = { Text("App Name e.g. YouTube, Settings") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { lastResult = toolRouter.openApp(appQuery) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7052FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Launch Application")
                }
            }
        }

        // --- 4. Camera, Gallery & Navigation ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Media, Camera & Navigation", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { lastResult = toolRouter.openCamera() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { lastResult = toolRouter.openGallery() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { lastResult = toolRouter.openMaps(mapsQuery) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Maps", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { lastResult = toolRouter.startNavigation(navDestination) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Navigation", fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 5. Media Controls & Battery ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171427)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Media Controls & System Telemetry", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = { lastResult = toolRouter.mediaPrevious() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    OutlinedButton(onClick = { lastResult = toolRouter.mediaPlay() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    OutlinedButton(onClick = { lastResult = toolRouter.mediaPause() }, modifier = Modifier.weight(1f)) {
                        Text("Pause", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { lastResult = toolRouter.mediaNext() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { lastResult = toolRouter.getBatteryStatus() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38E8C6)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Battery Status", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { lastResult = toolRouter.readSupportedNotifications() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2A4A)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Notifications", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
