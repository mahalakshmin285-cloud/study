package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.localization.LocalAppStrings

sealed class Screen(val route: String, val defaultLabel: String, val icon: ImageVector, val unselectedIcon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Chat : Screen("chat", "AI Chat", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    object PdfOcr : Screen("pdf_ocr", "PDFs", Icons.Filled.Description, Icons.Outlined.Description)
    object QuizFlash : Screen("quiz_flash", "Quiz", Icons.Filled.School, Icons.Outlined.School)
    object NotesPlanner : Screen("notes_planner", "Notes", Icons.Filled.EditNote, Icons.Outlined.EditNote)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Chat,
    Screen.PdfOcr,
    Screen.QuizFlash,
    Screen.NotesPlanner,
    Screen.Settings
)

@Composable
fun StudyBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        bottomNavScreens.forEach { screen ->
            val isSelected = currentRoute == screen.route
            val localizedLabel = when (screen) {
                Screen.Dashboard -> strings.navHome
                Screen.Chat -> strings.navAiChat
                Screen.PdfOcr -> strings.navPdfs
                Screen.QuizFlash -> strings.navQuiz
                Screen.NotesPlanner -> strings.navNotes
                Screen.Settings -> strings.navSettings
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) screen.icon else screen.unselectedIcon,
                        contentDescription = localizedLabel
                    )
                },
                label = {
                    Text(
                        text = localizedLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}

