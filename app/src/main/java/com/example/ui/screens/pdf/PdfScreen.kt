package com.example.ui.screens.pdf

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.FileProvider
import com.example.data.database.entities.PdfSummaryEntity
import com.example.data.pdf.PdfExtractionResult
import com.example.ui.components.FormattedAiText
import com.example.ui.localization.LocalAppStrings
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.UiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val pdfState by viewModel.pdfState.collectAsState()
    val pdfQnaState by viewModel.pdfQnaState.collectAsState()
    val savedSummaries by viewModel.pdfSummaries.collectAsState()

    var questionInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: PDF Viewer/Q&A, 1: Saved PDFs
    var showQuizDialog by remember { mutableStateOf(false) }
    var showFlashcardDialog by remember { mutableStateOf(false) }
    var quizTitleInput by remember { mutableStateOf("") }
    var flashcardTitleInput by remember { mutableStateOf("") }
    var quizCount by remember { mutableStateOf(5) }
    var quizDifficulty by remember { mutableStateOf("Medium") }
    var flashcardCount by remember { mutableStateOf(5) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadPdf(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(strings.pdfScreenTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                actions = {
                    Button(
                        onClick = { pdfPickerLauncher.launch("application/pdf") },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.uploadPdfBtn)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(strings.pdfAssistant) },
                    icon = { Icon(imageVector = Icons.Default.Article, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("${strings.pdfAssistant} (${savedSummaries.size})") },
                    icon = { Icon(imageVector = Icons.Default.Bookmark, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                when (pdfState) {
                    is UiState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = strings.noPdfUploadedTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = strings.noPdfUploadedSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(strings.selectPdf)
                                }
                            }
                        }
                    }

                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Reading PDF & Extracting Text in Background...")
                            }
                        }
                    }

                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = (pdfState as UiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    is UiState.Success -> {
                        val pdf = (pdfState as UiState.Success<PdfExtractionResult>).data
                        val isAlreadySaved = savedSummaries.any { it.fileName == pdf.fileName }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // PDF Header Info & Save PDF Button
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = pdf.fileName,
                                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }

                                            // Save PDF Button
                                            Button(
                                                onClick = {
                                                    viewModel.savePdfToLibrary(pdf)
                                                    Toast.makeText(context, "Saved '${pdf.fileName}' to Saved PDFs!", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = if (isAlreadySaved) {
                                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                } else {
                                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isAlreadySaved) Icons.Default.BookmarkAdded else Icons.Default.Bookmark,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isAlreadySaved) "Saved ✓" else "Save PDF")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Pages: ${pdf.pageCount} | Mode: Digital & Scanned PDF Extraction",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Action buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.generatePdfSummary(pdf.fileName, pdf.extractedText) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Summarize", style = MaterialTheme.typography.labelMedium)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    flashcardTitleInput = pdf.fileName.removeSuffix(".pdf").ifBlank { "PDF Study Deck" }
                                                    showFlashcardDialog = true
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Style, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Flashcards", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.saveNote("Notes: ${pdf.fileName}", pdf.extractedText.take(2500), pdf.fileName) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Save Note", style = MaterialTheme.typography.labelMedium)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    quizTitleInput = pdf.fileName.removeSuffix(".pdf").ifBlank { "PDF Study Quiz" }
                                                    showQuizDialog = true
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Quiz from PDF", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }

                            // Q&A Input Box
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "Ask AI About This PDF", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = questionInput,
                                            onValueChange = { questionInput = it },
                                            placeholder = { Text("e.g. \"What are the main concepts in this PDF?\"") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                val q = questionInput
                                                questionInput = ""
                                                viewModel.askPdfQuestion(pdf.extractedText, q)
                                            },
                                            enabled = questionInput.isNotBlank() && pdfQnaState !is UiState.Loading,
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Ask Question")
                                        }
                                    }
                                }
                            }

                            // AI Response Box (Properly Scrollable / Swipeable)
                            if (pdfQnaState is UiState.Loading) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Analyzing PDF text with Gemini AI...")
                                    }
                                }
                            }

                            if (pdfQnaState is UiState.Success) {
                                val answerText = (pdfQnaState as UiState.Success<String>).data
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = "AI Answer / Analysis:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                                IconButton(onClick = {
                                                    clipboardManager.setText(AnnotatedString(answerText))
                                                    Toast.makeText(context, "Copied AI answer to clipboard", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Answer", modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 380.dp)
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                    .padding(12.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                FormattedAiText(text = answerText)
                                            }
                                        }
                                    }
                                }
                            }

                            // Extracted PDF Content Viewer (Scrollable / Swipeable)
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Extracted PDF Actual Content",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            IconButton(onClick = {
                                                clipboardManager.setText(AnnotatedString(pdf.extractedText))
                                                Toast.makeText(context, "Copied PDF text to clipboard", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Text", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 350.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(12.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = if (pdf.extractedText.isNotBlank()) pdf.extractedText else "No text extracted.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Saved PDFs Tab
                var pdfToDelete by remember { mutableStateOf<PdfSummaryEntity?>(null) }

                if (savedSummaries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No saved PDFs in library yet.", style = MaterialTheme.typography.bodyLarge)
                            Text("Upload a PDF and tap 'Save PDF' to store it permanently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(savedSummaries, key = { it.id }) { summary ->
                            PdfSavedItem(
                                summary = summary,
                                onClick = {
                                    openPdfWithSystemViewer(context, summary)
                                },
                                onStudyWithAi = {
                                    viewModel.openSavedPdf(summary)
                                    selectedTab = 0
                                },
                                onDelete = { pdfToDelete = summary }
                            )
                        }
                    }
                }

                if (pdfToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { pdfToDelete = null },
                        title = { Text("Delete Saved PDF?") },
                        text = { Text("Are you sure you want to delete '${pdfToDelete!!.fileName}' from your saved PDFs? This action cannot be undone.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deletePdfSummary(pdfToDelete!!.id)
                                    pdfToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pdfToDelete = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }

        if (showQuizDialog && pdfState is UiState.Success) {
            val pdf = (pdfState as UiState.Success<PdfExtractionResult>).data
            AlertDialog(
                onDismissRequest = { showQuizDialog = false },
                title = { Text("Create Quiz from PDF") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = quizTitleInput,
                            onValueChange = { quizTitleInput = it },
                            label = { Text("Quiz Heading / Title") },
                            placeholder = { Text("e.g. ${pdf.fileName.removeSuffix(".pdf")}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("PDF Source: ${pdf.fileName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        
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
                        val heading = quizTitleInput.trim().ifBlank { pdf.fileName.removeSuffix(".pdf") }
                        showQuizDialog = false
                        viewModel.generateQuiz(
                            topicOrContent = "PDF Content for $heading:\n${pdf.extractedText.take(3000)}",
                            subject = heading,
                            difficulty = quizDifficulty,
                            questionCount = quizCount
                        )
                        onNavigate(com.example.ui.components.Screen.QuizFlash.route)
                    }) {
                        Text("Generate Quiz")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuizDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showFlashcardDialog && pdfState is UiState.Success) {
            val pdf = (pdfState as UiState.Success<PdfExtractionResult>).data
            AlertDialog(
                onDismissRequest = { showFlashcardDialog = false },
                title = { Text("Create Flashcards from PDF") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = flashcardTitleInput,
                            onValueChange = { flashcardTitleInput = it },
                            label = { Text("Folder / Deck Heading") },
                            placeholder = { Text("e.g. ${pdf.fileName.removeSuffix(".pdf")}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("PDF Source: ${pdf.fileName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        
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
                        showFlashcardDialog = false
                        val cleanDeck = flashcardTitleInput.trim().ifBlank {
                            if (pdf.fileName.isNotBlank()) pdf.fileName.removeSuffix(".pdf") else "PDF Study Deck"
                        }
                        val deckHeading = "$cleanDeck – $flashcardCount Flashcards"
                        viewModel.generateFlashcards(
                            deckName = deckHeading,
                            topicOrContent = pdf.extractedText,
                            count = flashcardCount
                        )
                        onNavigate("flashcards")
                    }) {
                        Text("Generate Flashcards")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFlashcardDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PdfSavedItem(
    summary: PdfSummaryEntity,
    onClick: () -> Unit,
    onStudyWithAi: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = summary.fileName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = "Pages: ${summary.pageCount} • Tap to open in WPS / Drive PDF Viewer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open PDF File", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onStudyWithAi,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Study & Q&A", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

fun openPdfWithSystemViewer(context: Context, summary: PdfSummaryEntity) {
    try {
        val filePath = summary.localFilePath
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists()) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open PDF with..."))
                return
            }
        }
        Toast.makeText(context, "PDF file path not found. Please re-upload.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "No PDF viewer app installed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
