package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SanaAudioManager
import com.example.data.GeminiConfigManager
import com.example.data.GeminiService
import com.example.data.local.SanaDatabase
import com.example.data.local.SanaRepository
import com.example.service.SanaForegroundService
import com.example.session.SanaSessionManager
import com.example.tools.SanaToolRouter
import com.example.ui.screens.ChatVoiceScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.GeminiConnectionScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PhoneToolsScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.theme.MyApplicationTheme

enum class SanaNavScreen(val title: String, val icon: ImageVector) {
    CHAT("Assistant", Icons.AutoMirrored.Filled.Chat),
    TOOLS("Tools", Icons.Default.Build),
    VOICE("Voice", Icons.Default.RecordVoiceOver),
    CONNECTION("Gemini", Icons.Default.Hub),
    SYSTEM("System", Icons.Default.Tune)
}

class MainActivity : ComponentActivity() {

    private lateinit var configManager: GeminiConfigManager
    private lateinit var geminiService: GeminiService
    private lateinit var audioManager: SanaAudioManager
    private lateinit var repository: SanaRepository
    private lateinit var toolRouter: SanaToolRouter
    private lateinit var sessionManager: SanaSessionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Service start if mic granted
        if (permissions[android.Manifest.permission.RECORD_AUDIO] == true && configManager.isWakeWordEnabled()) {
            startSanaService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize application components
        configManager = GeminiConfigManager(this)
        geminiService = GeminiService(configManager)
        audioManager = SanaAudioManager(this, configManager)
        val database = SanaDatabase.getInstance(this)
        repository = SanaRepository(database.sanaDao())
        toolRouter = SanaToolRouter(this)

        // 2. Request core permissions
        val permissionsToRequest = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            val coroutineScope = rememberCoroutineScope()
            sessionManager = remember {
                SanaSessionManager(
                    context = this@MainActivity,
                    configManager = configManager,
                    geminiService = geminiService,
                    audioManager = audioManager,
                    repository = repository,
                    toolRouter = toolRouter,
                    scope = coroutineScope
                )
            }

            var currentScreen by remember { mutableStateOf(SanaNavScreen.CHAT) }
            var systemSubTab by remember { mutableIntStateOf(0) } // 0: Diagnostics, 1: Memory, 2: Permissions

            BackHandler(enabled = currentScreen != SanaNavScreen.CHAT) {
                currentScreen = SanaNavScreen.CHAT
            }

            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars,
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF131120),
                            contentColor = Color.White
                        ) {
                            SanaNavScreen.entries.forEach { screen ->
                                val isSelected = currentScreen == screen
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentScreen = screen },
                                    icon = {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title
                                        )
                                    },
                                    label = { Text(screen.title, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF00E5FF),
                                        selectedTextColor = Color(0xFF00E5FF),
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                        indicatorColor = Color(0xFF7052FF).copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            SanaNavScreen.CHAT -> {
                                ChatVoiceScreen(
                                    sessionManager = sessionManager,
                                    audioManager = audioManager,
                                    configManager = configManager,
                                    onNavigateToSettings = { currentScreen = SanaNavScreen.CONNECTION }
                                )
                            }

                            SanaNavScreen.TOOLS -> {
                                PhoneToolsScreen(toolRouter = toolRouter)
                            }

                            SanaNavScreen.VOICE -> {
                                VoiceStudioScreen(
                                    audioManager = audioManager,
                                    configManager = configManager,
                                    geminiService = geminiService,
                                    onNavigateToSettings = { currentScreen = SanaNavScreen.CONNECTION },
                                    onNavigateToPermissions = {
                                        currentScreen = SanaNavScreen.SYSTEM
                                        systemSubTab = 2
                                    }
                                )
                            }

                            SanaNavScreen.CONNECTION -> {
                                GeminiConnectionScreen(
                                    configManager = configManager,
                                    geminiService = geminiService
                                )
                            }

                            SanaNavScreen.SYSTEM -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    ScrollableTabRow(
                                        selectedTabIndex = systemSubTab,
                                        containerColor = Color(0xFF131120),
                                        contentColor = Color.White,
                                        edgePadding = 16.dp,
                                        indicator = { tabPositions ->
                                            TabRowDefaults.SecondaryIndicator(
                                                modifier = Modifier.tabIndicatorOffset(tabPositions[systemSubTab]),
                                                color = Color(0xFF7052FF)
                                            )
                                        }
                                    ) {
                                        Tab(
                                            selected = systemSubTab == 0,
                                            onClick = { systemSubTab = 0 },
                                            text = { Text("Diagnostics") },
                                            icon = { Icon(Icons.Default.Dns, contentDescription = null) }
                                        )
                                        Tab(
                                            selected = systemSubTab == 1,
                                            onClick = { systemSubTab = 1 },
                                            text = { Text("Memory Vault") },
                                            icon = { Icon(Icons.Default.Psychology, contentDescription = null) }
                                        )
                                        Tab(
                                            selected = systemSubTab == 2,
                                            onClick = { systemSubTab = 2 },
                                            text = { Text("Permissions") },
                                            icon = { Icon(Icons.Default.Security, contentDescription = null) }
                                        )
                                    }

                                    when (systemSubTab) {
                                        0 -> DiagnosticsScreen(
                                            configManager = configManager,
                                            geminiService = geminiService,
                                            audioManager = audioManager
                                        )
                                        1 -> MemoryScreen(
                                            repository = repository,
                                            configManager = configManager
                                        )
                                        2 -> PermissionsScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startSanaService() {
        try {
            val serviceIntent = Intent(this, SanaForegroundService::class.java).apply {
                action = SanaForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Foreground service start restriction
        }
    }

    override fun onDestroy() {
        audioManager.release()
        super.onDestroy()
    }
}
