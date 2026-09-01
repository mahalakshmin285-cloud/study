package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.Screen
import com.example.ui.components.StudyBottomBar
import com.example.ui.localization.LocalAppStrings
import com.example.ui.localization.LocalizationHelper
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.flashcards.FlashcardsScreen
import com.example.ui.screens.notes.NotesScreen
import com.example.ui.screens.ocr.OcrScreen
import com.example.ui.screens.pdf.PdfScreen
import com.example.ui.screens.planner.PlannerScreen
import com.example.ui.screens.pomodoro.PomodoroScreen
import com.example.ui.screens.quiz.QuizScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.voice.VoiceScreen
import com.example.ui.theme.StudyAssistantTheme
import com.example.viewmodel.AuthStatus
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Ensure standard 32-bit RGBA_8888 window pixel format for universal emulator & device GPU compatibility
            window.setFormat(android.graphics.PixelFormat.RGBA_8888)
        } catch (_: Throwable) {
            // Safe fallback if window pixel format modification is restricted
        }
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val authStatus by viewModel.authStatus.collectAsState()

            val appStrings = remember(appLanguage) {
                LocalizationHelper.getStrings(appLanguage)
            }

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                StudyAssistantTheme(themeMode = themeMode) {
                    when (authStatus) {
                        is AuthStatus.Initializing -> {
                            // Seamless startup background container while session is verified
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }
                        is AuthStatus.Unauthenticated -> {
                            AuthScreen(viewModel = viewModel)
                        }
                        is AuthStatus.Authenticated -> {
                            val navController = rememberNavController()
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                bottomBar = {
                                    val isImeVisible = WindowInsets.isImeVisible
                                    // Show bottom nav bar for main routes only when keyboard is not open
                                    val isMainRoute = listOf(
                                        Screen.Dashboard.route,
                                        Screen.Chat.route,
                                        Screen.PdfOcr.route,
                                        Screen.QuizFlash.route,
                                        Screen.NotesPlanner.route,
                                        Screen.Settings.route
                                    ).contains(currentRoute)

                                    if (isMainRoute && !isImeVisible) {
                                        StudyBottomBar(
                                            currentRoute = currentRoute,
                                            onNavigate = { route ->
                                                navController.navigate(route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.Dashboard.route,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .consumeWindowInsets(innerPadding)
                                ) {
                                    composable(Screen.Dashboard.route) {
                                        DashboardScreen(
                                            viewModel = viewModel,
                                            onNavigate = { route -> navController.navigate(route) }
                                        )
                                    }

                                    composable(Screen.Chat.route) {
                                        ChatScreen(viewModel = viewModel)
                                    }

                                    composable(Screen.PdfOcr.route) {
                                        PdfScreen(
                                            viewModel = viewModel,
                                            onNavigate = { route -> navController.navigate(route) }
                                        )
                                    }

                                    composable("ocr") {
                                        OcrScreen(viewModel = viewModel)
                                    }

                                    composable("voice") {
                                        VoiceScreen(viewModel = viewModel)
                                    }

                                    composable(Screen.QuizFlash.route) {
                                        // Unified Hub for Quiz & Flashcards
                                        QuizScreen(viewModel = viewModel)
                                    }

                                    composable("flashcards") {
                                        FlashcardsScreen(viewModel = viewModel)
                                    }

                                    composable(Screen.NotesPlanner.route) {
                                        // Unified Hub for Notes & Planner
                                        NotesScreen(
                                            viewModel = viewModel,
                                            onNavigate = { route -> navController.navigate(route) }
                                        )
                                    }

                                    composable("planner") {
                                        PlannerScreen(viewModel = viewModel)
                                    }

                                    composable("pomodoro") {
                                        PomodoroScreen(viewModel = viewModel)
                                    }

                                    composable("search") {
                                        SearchScreen(viewModel = viewModel)
                                    }

                                    composable(Screen.Settings.route) {
                                        SettingsScreen(viewModel = viewModel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
