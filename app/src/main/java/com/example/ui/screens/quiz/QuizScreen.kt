package com.example.ui.screens.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entities.QuizResultEntity
import com.example.repository.QuizQuestion
import com.example.ui.localization.LocalAppStrings
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(
    viewModel: MainViewModel
) {
    val strings = LocalAppStrings.current
    val quizState by viewModel.quizState.collectAsState()
    val quizResults by viewModel.quizResults.collectAsState()

    var isCreatingQuiz by remember { mutableStateOf(false) }
    var subjectInput by remember { mutableStateOf("") }
    var topicInput by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf("Medium") }
    var selectedCount by remember { mutableStateOf(5) }
    var userAnswers by remember { mutableStateOf(mutableMapOf<Int, String>()) }
    var isSubmitted by remember { mutableStateOf(false) }

    // When a quiz succeeds, automatically make sure we are in quiz active view
    LaunchedEffect(quizState) {
        if (quizState is UiState.Success) {
            isCreatingQuiz = false
        }
    }

    when {
        // --- SCREEN 1: ACTIVE QUIZ VIEW ---
        quizState is UiState.Success -> {
            val questions = (quizState as UiState.Success<List<QuizQuestion>>).data
            val answeredCount = userAnswers.size
            val progress = if (questions.isNotEmpty()) answeredCount.toFloat() / questions.size.toFloat() else 0f

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = {
                                userAnswers = mutableMapOf()
                                isSubmitted = false
                                viewModel.resetQuizState()
                            }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.exit)
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = topicInput.ifBlank { strings.aiQuiz },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = if (isSubmitted) strings.reviewAnswers else "$answeredCount / ${questions.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                userAnswers = mutableMapOf()
                                isSubmitted = false
                                viewModel.resetQuizState()
                            }) {
                                Text(strings.exit)
                            }
                        }
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!isSubmitted) {
                        item {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }

                    if (isSubmitted) {
                        // Score Summary Banner
                        item {
                            val score = questions.indices.count { idx ->
                                val userAns = userAnswers[idx]?.trim() ?: ""
                                val correct = questions[idx].correctAnswer.trim()
                                userAns.equals(correct, ignoreCase = true)
                            }
                            val percentage = if (questions.isNotEmpty()) (score * 100) / questions.size else 0

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (percentage >= 80) strings.quizOutstanding else if (percentage >= 50) strings.quizGoodEffort else strings.quizKeepPracticing,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${strings.score}: $score / ${questions.size} ($percentage%)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                userAnswers = mutableMapOf()
                                                isSubmitted = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.retry, maxLines = 1, softWrap = false)
                                        }
                                        Button(
                                            onClick = {
                                                userAnswers = mutableMapOf()
                                                isSubmitted = false
                                                isCreatingQuiz = true
                                                viewModel.resetQuizState()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.newQuiz, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(questions) { index, q ->
                        QuizQuestionCard(
                            questionIndex = index + 1,
                            question = q,
                            selectedAnswer = userAnswers[index] ?: "",
                            isSubmitted = isSubmitted,
                            onSelectAnswer = { ans ->
                                if (!isSubmitted) {
                                    val newMap = userAnswers.toMutableMap()
                                    newMap[index] = ans
                                    userAnswers = newMap
                                }
                            }
                        )
                    }

                    if (!isSubmitted) {
                        item {
                            Button(
                                onClick = {
                                    isSubmitted = true
                                    val score = questions.indices.count { idx ->
                                        val userAns = userAnswers[idx]?.trim() ?: ""
                                        val correct = questions[idx].correctAnswer.trim()
                                        userAns.equals(correct, ignoreCase = true)
                                    }
                                    viewModel.saveQuizScore(
                                        topic = topicInput.ifBlank { "General AI Quiz" },
                                        score = score,
                                        totalQuestions = questions.size
                                    )
                                },
                                enabled = userAnswers.size == questions.size,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${strings.submitQuizAnswers} (${userAnswers.size}/${questions.size})", maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        // --- SCREEN 2: DEDICATED QUIZ CREATION PAGE ---
        isCreatingQuiz || quizState is UiState.Loading || quizState is UiState.Error -> {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = {
                                isCreatingQuiz = false
                                viewModel.resetQuizState()
                            }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backToQuizzes)
                            }
                        },
                        title = {
                            Text(
                                text = strings.createAiQuiz,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Subject and Topic Direct Input Card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = strings.subjectTopicLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.subjectTopicDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Subject Input
                            Text(
                                text = strings.subjectOptional,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = subjectInput,
                                onValueChange = { subjectInput = it },
                                placeholder = { Text(strings.subjectPlaceholder) },
                                trailingIcon = {
                                    if (subjectInput.isNotEmpty()) {
                                        IconButton(onClick = { subjectInput = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = strings.clearDataButton)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Topic Input
                            Text(
                                text = strings.topicRequired,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = topicInput,
                                onValueChange = { topicInput = it },
                                placeholder = { Text(strings.topicPlaceholder) },
                                trailingIcon = {
                                    if (topicInput.isNotEmpty()) {
                                        IconButton(onClick = { topicInput = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = strings.clearDataButton)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    // Difficulty Level Card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = strings.difficultyLevel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(
                                    "Easy" to strings.diffEasy,
                                    "Medium" to strings.diffMedium,
                                    "Hard" to strings.diffHard
                                ).forEach { (diffKey, diffLabel) ->
                                    FilterChip(
                                        selected = selectedDifficulty == diffKey,
                                        onClick = { selectedDifficulty = diffKey },
                                        label = {
                                            Text(
                                                text = diffLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Number of Questions Card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = strings.questionCount,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(5, 10, 15, 20).forEach { count ->
                                    FilterChip(
                                        selected = selectedCount == count,
                                        onClick = { selectedCount = count },
                                        label = {
                                            Text(
                                                text = "$count ${strings.questionsSuffix}",
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Info note
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = strings.quizInfoNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Error Message (if any)
                    if (quizState is UiState.Error) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = (quizState as UiState.Error).message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Generate Action Button
                    Button(
                        onClick = {
                            userAnswers = mutableMapOf()
                            isSubmitted = false
                            viewModel.generateQuiz(
                                subject = subjectInput,
                                topicOrContent = topicInput,
                                difficulty = selectedDifficulty,
                                questionCount = selectedCount
                            )
                        },
                        enabled = topicInput.isNotBlank() && quizState !is UiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (quizState is UiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("${strings.generatingQuestions} $selectedCount ${strings.questionsSuffix}...", maxLines = 1)
                        } else {
                            val diffLabel = when (selectedDifficulty) {
                                "Easy" -> strings.diffEasy
                                "Medium" -> strings.diffMedium
                                "Hard" -> strings.diffHard
                                else -> selectedDifficulty
                            }
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${strings.generateQuizAction} ($selectedCount ${strings.questionsSuffix} • $diffLabel)", maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // --- SCREEN 3: QUIZ HUB HOME VIEW ---
        else -> {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = strings.quizScreenTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        actions = {
                            IconButton(onClick = { isCreatingQuiz = true }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = strings.createQuiz)
                            }
                        }
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { isCreatingQuiz = true },
                        icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                        text = { Text(strings.createQuiz) },
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Hero Card: Create Custom AI Quiz
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCreatingQuiz = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Psychology,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = strings.createNewQuiz,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = strings.tailoredTestSubtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { isCreatingQuiz = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.openQuizForm)
                                }
                            }
                        }
                    }

                    // Past Quiz Results & History Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.quizHistoryScores,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (quizResults.isNotEmpty()) {
                                Text(
                                    text = "${quizResults.size} ${strings.completed}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (quizResults.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Quiz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = strings.noQuizResultsYet,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = strings.noQuizResultsSub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(quizResults, key = { it.id }) { result ->
                            QuizResultCard(
                                result = result,
                                onRetake = {
                                    topicInput = result.topic
                                    isCreatingQuiz = true
                                },
                                onDelete = {
                                    viewModel.deleteQuizResult(result.id)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(76.dp)) // Clearance for FAB
                    }
                }
            }
        }
    }
}

@Composable
fun QuizResultCard(
    result: QuizResultEntity,
    onRetake: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val dateStr = remember(result.dateTaken) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(result.dateTaken))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.topic,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (result.percentage >= 70) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${result.score}/${result.totalQuestions} (${result.percentage}%)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (result.percentage >= 70) Color(0xFF166534) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                IconButton(onClick = onRetake) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = strings.retakeQuiz,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = strings.deleteResult,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun QuizQuestionCard(
    questionIndex: Int,
    question: QuizQuestion,
    selectedAnswer: String,
    isSubmitted: Boolean,
    onSelectAnswer: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.questionNumber} $questionIndex",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when (question.type) {
                            "TRUE_FALSE" -> strings.trueFalse
                            "FILL_BLANK" -> strings.fillInBlank
                            else -> strings.multipleChoice
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (question.type == "FILL_BLANK") {
                OutlinedTextField(
                    value = selectedAnswer,
                    onValueChange = onSelectAnswer,
                    placeholder = { Text(strings.typeAnswerPlaceholder) },
                    enabled = !isSubmitted,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                question.options.forEach { option ->
                    val isSelected = selectedAnswer == option
                    val isCorrect = option.trim().equals(question.correctAnswer.trim(), ignoreCase = true)

                    Surface(
                        onClick = { onSelectAnswer(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSubmitted && isCorrect -> Color(0xFFDCFCE7)
                            isSubmitted && isSelected && !isCorrect -> Color(0xFFFEE2E2)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectAnswer(option) },
                                enabled = !isSubmitted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSubmitted && isCorrect -> Color(0xFF166534)
                                    isSubmitted && isSelected && !isCorrect -> Color(0xFF991B1B)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            if (isSubmitted) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${strings.correctAnswer}: ${question.correctAnswer}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (question.explanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = question.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
