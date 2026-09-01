package com.example.ui.screens.flashcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entities.FlashcardEntity
import com.example.ui.localization.LocalAppStrings
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    viewModel: MainViewModel
) {
    val strings = LocalAppStrings.current
    val allFlashcards by viewModel.allFlashcards.collectAsState()
    val deckNames by viewModel.deckNames.collectAsState()
    val vmSelectedDeck by viewModel.selectedDeckName.collectAsState()

    var selectedDeck by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<String?>(null) }

    var topicInput by remember { mutableStateOf("") }
    var deckInput by remember { mutableStateOf("") }
    var questionInput by remember { mutableStateOf("") }
    var answerInput by remember { mutableStateOf("") }
    var generateCardCount by remember { mutableStateOf(5) }

    // Sync selectedDeck when ViewModel sets an active deck (e.g. from Notes or PDF Assistant)
    LaunchedEffect(vmSelectedDeck) {
        if (vmSelectedDeck != null) {
            selectedDeck = vmSelectedDeck
            currentIndex = 0
            isFlipped = false
        }
    }

    val currentDeckCards = remember(allFlashcards, selectedDeck) {
        if (selectedDeck == null) emptyList()
        else allFlashcards.filter { it.deckName == selectedDeck }
    }

    if (selectedDeck == null) {
        // =========================================================================
        // VIEW 1: FOLDERS / DECKS ONLY VIEW
        // =========================================================================
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = strings.flashcardsScreenTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            deckInput = ""
                            topicInput = ""
                            showGenerateDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = strings.generateAiFlashcards)
                        }
                        IconButton(onClick = {
                            deckInput = ""
                            questionInput = ""
                            answerInput = ""
                            showManualDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = strings.addManualFlashcard)
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (deckNames.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.noFlashcardsFound,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.noFlashcardsSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = {
                            deckInput = ""
                            topicInput = ""
                            showGenerateDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.generateAiFlashcards)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "${strings.flashcards} Folders (${deckNames.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(deckNames, key = { it }) { deck ->
                        val cardCount = allFlashcards.count { it.deckName == deck }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDeck = deck
                                    viewModel.setSelectedDeck(deck)
                                    currentIndex = 0
                                    isFlipped = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deck,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "$cardCount Cards",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Tap to open folder",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = { deckToDelete = deck }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = strings.deleteDeckFolder,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // VIEW 2: DEDICATED DECK / FOLDER DETAIL PAGE
        // =========================================================================
        val activeDeckName = selectedDeck!!
        val cardCount = currentDeckCards.size
        val safeIndex = if (cardCount > 0) currentIndex.coerceIn(0, cardCount - 1) else 0

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedDeck = null
                            viewModel.setSelectedDeck(null)
                            currentIndex = 0
                            isFlipped = false
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Folders"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = activeDeckName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (cardCount > 0) "Card ${safeIndex + 1} of $cardCount" else "0 Cards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            deckInput = activeDeckName
                            questionInput = ""
                            answerInput = ""
                            showManualDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = strings.addManualFlashcard)
                        }
                        IconButton(onClick = { deckToDelete = activeDeckName }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = strings.deleteDeckFolder,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentDeckCards.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No cards in this folder yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                deckInput = activeDeckName
                                questionInput = ""
                                answerInput = ""
                                showManualDialog = true
                            }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Flashcard")
                            }
                        }
                    }
                } else {
                    val card = currentDeckCards[safeIndex]
                    val progress = (safeIndex + 1).toFloat() / cardCount.toFloat()

                    // Progress indicator
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Flashcard flip card view
                    FlipCardView(
                        flashcard = card,
                        isFlipped = isFlipped,
                        strings = strings,
                        onFlip = { isFlipped = !isFlipped },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 280.dp)
                    )

                    // Card controls (Previous, Flip, Delete, Next)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                if (safeIndex > 0) {
                                    currentIndex = safeIndex - 1
                                    isFlipped = false
                                }
                            },
                            enabled = safeIndex > 0
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = strings.previous)
                        }

                        Button(
                            onClick = { isFlipped = !isFlipped },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(imageVector = Icons.Default.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.tapToFlip)
                        }

                        IconButton(
                            onClick = {
                                viewModel.deleteFlashcard(card)
                                if (safeIndex >= currentDeckCards.size - 1 && safeIndex > 0) {
                                    currentIndex = safeIndex - 1
                                }
                                isFlipped = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = strings.deleteFlashcard,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                if (safeIndex < cardCount - 1) {
                                    currentIndex = safeIndex + 1
                                    isFlipped = false
                                }
                            },
                            enabled = safeIndex < cardCount - 1
                        ) {
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = strings.next)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOG: GENERATE AI FLASHCARDS
    // =========================================================================
    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text(strings.generateAiFlashcards) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = deckInput,
                        onValueChange = { deckInput = it },
                        label = { Text("Folder / Deck Heading") },
                        placeholder = { Text("e.g. Cloud Computing") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text(strings.topicOrContentPrompt) },
                        placeholder = { Text("e.g. AWS EC2, S3 storage classes, Lambda, and VPC peering") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )

                    Text(strings.numberOfFlashcards, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20).forEach { count ->
                            FilterChip(
                                selected = generateCardCount == count,
                                onClick = { generateCardCount = count },
                                label = { Text("$count") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val baseName = if (deckInput.isNotBlank()) deckInput.trim() else topicInput.take(24).trim().ifBlank { "Study Deck" }
                        val formattedDeckName = "$baseName – $generateCardCount Flashcards"
                        viewModel.generateFlashcards(
                            deckName = formattedDeckName,
                            topicOrContent = topicInput,
                            count = generateCardCount
                        )
                        selectedDeck = formattedDeckName
                        viewModel.setSelectedDeck(formattedDeckName)
                        currentIndex = 0
                        isFlipped = false
                        deckInput = ""
                        topicInput = ""
                        showGenerateDialog = false
                    },
                    enabled = topicInput.isNotBlank() || deckInput.isNotBlank()
                ) {
                    Text("${strings.generateCardsBtn} $generateCardCount")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // =========================================================================
    // DIALOG: ADD MANUAL FLASHCARD
    // =========================================================================
    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(strings.addManualFlashcard) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = deckInput,
                        onValueChange = { deckInput = it },
                        label = { Text(strings.deckNameLabel) },
                        placeholder = { Text("e.g. Cloud Computing – 15 Flashcards") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        label = { Text(strings.frontQuestion) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = answerInput,
                        onValueChange = { answerInput = it },
                        label = { Text(strings.backAnswer) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDeck = deckInput.ifBlank { selectedDeck ?: "General Deck" }
                        viewModel.addManualFlashcard(targetDeck, questionInput, answerInput)
                        selectedDeck = targetDeck
                        viewModel.setSelectedDeck(targetDeck)
                        questionInput = ""
                        answerInput = ""
                        showManualDialog = false
                    },
                    enabled = questionInput.isNotBlank() && answerInput.isNotBlank()
                ) {
                    Text(strings.saveCard)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // =========================================================================
    // DIALOG: DELETE DECK CONFIRMATION
    // =========================================================================
    if (deckToDelete != null) {
        val target = deckToDelete!!
        AlertDialog(
            onDismissRequest = { deckToDelete = null },
            title = { Text("Delete Flashcard Folder?") },
            text = {
                Text("Are you sure you want to delete \"$target\" and all flashcards inside it?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDeck(target)
                        if (selectedDeck == target) {
                            selectedDeck = null
                            viewModel.setSelectedDeck(null)
                            currentIndex = 0
                            isFlipped = false
                        }
                        deckToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deckToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FlipCardView(
    flashcard: FlashcardEntity,
    isFlipped: Boolean,
    strings: com.example.ui.localization.AppStrings,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "flip"
    )

    Card(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
            .clickable { onFlip() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFlipped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                // Front
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = strings.questionHeader,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = flashcard.question,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Back
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer { rotationY = 180f }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = strings.answerHeader,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = flashcard.answer,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
