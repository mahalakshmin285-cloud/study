package com.example.ui.screens.notes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.database.entities.NoteEntity
import com.example.ui.components.FormattedAiText
import com.example.ui.localization.LocalAppStrings
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val allNotes by viewModel.allNotes.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var viewingNoteId by remember { mutableStateOf<Long?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }

    var titleInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }

    var noteForQuiz by remember { mutableStateOf<NoteEntity?>(null) }
    var noteForFlashcards by remember { mutableStateOf<NoteEntity?>(null) }
    var quizCount by remember { mutableStateOf(5) }
    var quizDifficulty by remember { mutableStateOf("Medium") }
    var flashcardCount by remember { mutableStateOf(5) }

    val currentViewingNote = remember(allNotes, viewingNoteId) {
        allNotes.find { it.id == viewingNoteId }
    }

    // If a note was deleted while being viewed, reset back to list
    LaunchedEffect(viewingNoteId, currentViewingNote) {
        if (viewingNoteId != null && currentViewingNote == null) {
            viewingNoteId = null
        }
    }

    // Handle back button when viewing a note's dedicated page
    BackHandler(enabled = currentViewingNote != null) {
        viewingNoteId = null
    }

    val filteredNotes = remember(allNotes, searchQuery) {
        if (searchQuery.isBlank()) allNotes
        else allNotes.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.content.contains(searchQuery, ignoreCase = true) 
        }
    }

    if (currentViewingNote != null) {
        // Dedicated Note Detail Page
        val note = currentViewingNote
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewingNoteId = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Notes"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(note.content))
                            Toast.makeText(context, "Note copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Content")
                        }
                        IconButton(onClick = { viewModel.toggleNotePin(note) }) {
                            Icon(
                                imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin Note",
                                tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            editingNote = note
                            titleInput = note.title
                            contentInput = note.content
                            showAddDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Note")
                        }
                        IconButton(onClick = { noteToDelete = note }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Note",
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Note Header / Metadata Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (note.isPinned) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Pinned",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (!note.pdfFileName.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "PDF: ${note.pdfFileName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            val formattedDate = remember(note.updatedAt) {
                                SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(note.updatedAt))
                            }
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Study Actions Card: Create Quiz & Flashcards
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Generate Study Material from Note",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create an AI quiz or flashcards deck based on this note's content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    noteForQuiz = note
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Quiz", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    noteForFlashcards = note
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Flashcards", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Full Note Content Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        FormattedAiText(text = note.content)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    } else {
        // Study Notes Main List Page
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.notesScreenTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                    actions = {
                        IconButton(onClick = { onNavigate("planner") }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = strings.timetableTitle,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingNote = null
                        titleInput = ""
                        contentInput = ""
                        showAddDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notes...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Notes Found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Tap + to create your first manual or AI-generated study note.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = { viewingNoteId = note.id },
                                onEdit = {
                                    editingNote = note
                                    titleInput = note.title
                                    contentInput = note.content
                                    showAddDialog = true
                                },
                                onTogglePin = { viewModel.toggleNotePin(note) },
                                onDelete = { noteToDelete = note },
                                onCreateQuiz = {
                                    noteForQuiz = note
                                },
                                onCreateFlashcards = {
                                    noteForFlashcards = note
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Quiz Generation Dialog
    if (noteForQuiz != null) {
        val targetNote = noteForQuiz!!
        AlertDialog(
            onDismissRequest = { noteForQuiz = null },
            title = { Text("Create Quiz from Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Note: ${targetNote.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    
                    Text("Number of Questions:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20).forEach { count ->
                            FilterChip(
                                selected = quizCount == count,
                                onClick = { quizCount = count },
                                label = { Text("$count") }
                            )
                        }
                    }

                    Text("Difficulty Level:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Easy", "Medium", "Hard").forEach { diff ->
                            FilterChip(
                                selected = quizDifficulty == diff,
                                onClick = { quizDifficulty = diff },
                                label = { Text(diff) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.generateQuiz(
                        topicOrContent = "Note: ${targetNote.title}\n\n${targetNote.content.take(3000)}",
                        subject = targetNote.title,
                        difficulty = quizDifficulty,
                        questionCount = quizCount
                    )
                    noteForQuiz = null
                    onNavigate(com.example.ui.components.Screen.QuizFlash.route)
                }) {
                    Text("Generate Quiz")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteForQuiz = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Flashcards Generation Dialog
    if (noteForFlashcards != null) {
        val targetNote = noteForFlashcards!!
        AlertDialog(
            onDismissRequest = { noteForFlashcards = null },
            title = { Text("Create Flashcards from Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Note: ${targetNote.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    
                    Text("Number of Flashcards:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20).forEach { count ->
                            FilterChip(
                                selected = flashcardCount == count,
                                onClick = { flashcardCount = count },
                                label = { Text("$count") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cleanDeck = targetNote.title.ifBlank { "Study Note" }
                    viewModel.generateFlashcards(
                        deckName = "$cleanDeck – $flashcardCount Flashcards",
                        topicOrContent = targetNote.content,
                        count = flashcardCount
                    )
                    noteForFlashcards = null
                    onNavigate("flashcards")
                }) {
                    Text("Generate Flashcards")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteForFlashcards = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Edit Dialog (Shared between list and full detail page)
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingNote != null) "Edit Study Note" else "Create Study Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Title / Topic") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        label = { Text("Content or Raw Text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        maxLines = 10
                    )

                    if (editingNote == null) {
                        TextButton(
                            onClick = {
                                if (titleInput.isNotBlank() || contentInput.isNotBlank()) {
                                    isAiGenerating = true
                                    viewModel.generateAiNote(
                                        title = titleInput.ifBlank { "AI Summary Note" },
                                        rawContent = contentInput.ifBlank { titleInput }
                                    ) { generatedNote ->
                                        isAiGenerating = false
                                        titleInput = generatedNote.title
                                        contentInput = generatedNote.content
                                    }
                                }
                            },
                            enabled = !isAiGenerating && (titleInput.isNotBlank() || contentInput.isNotBlank())
                        ) {
                            if (isAiGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generating Note...")
                            } else {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate AI Structured Note")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingNote != null) {
                            viewModel.updateNote(editingNote!!.copy(title = titleInput, content = contentInput, updatedAt = System.currentTimeMillis()))
                        } else {
                            viewModel.saveNote(titleInput, contentInput)
                        }
                        titleInput = ""
                        contentInput = ""
                        editingNote = null
                        showAddDialog = false
                    },
                    enabled = titleInput.isNotBlank() || contentInput.isNotBlank()
                ) {
                    Text(if (editingNote != null) "Update Note" else "Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    editingNote = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog (Shared)
    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note?") },
            text = { Text("Are you sure you want to delete '${noteToDelete!!.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val toDeleteId = noteToDelete!!.id
                        if (viewingNoteId == toDeleteId) {
                            viewingNoteId = null
                        }
                        viewModel.deleteNote(toDeleteId)
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NoteCardItem(
    note: NoteEntity,
    onClick: () -> Unit = {},
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onCreateQuiz: () -> Unit,
    onCreateFlashcards: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (note.isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                        Text(
                            text = if (!note.pdfFileName.isNullOrBlank()) "PDF: ${note.pdfFileName} • Tap to view full note" else "Study Note • Tap to view full note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateQuiz,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Quiz", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCreateFlashcards,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Style, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Flashcards", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
