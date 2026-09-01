package com.example.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel
) {
    var searchQuery by remember { mutableStateOf("") }

    val allNotes by viewModel.allNotes.collectAsState()
    val allFlashcards by viewModel.allFlashcards.collectAsState()
    val pdfSummaries by viewModel.pdfSummaries.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()

    val matchedNotes = remember(allNotes, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allNotes.filter { it.title.contains(searchQuery, ignoreCase = true) || it.content.contains(searchQuery, ignoreCase = true) }
    }

    val matchedFlashcards = remember(allFlashcards, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allFlashcards.filter { it.question.contains(searchQuery, ignoreCase = true) || it.answer.contains(searchQuery, ignoreCase = true) }
    }

    val matchedPdfs = remember(pdfSummaries, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else pdfSummaries.filter { it.fileName.contains(searchQuery, ignoreCase = true) || it.summaryText.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Study Search 🔍", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }
            )
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
                placeholder = { Text("Search notes, flashcards, PDFs, chat...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (searchQuery.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Type a keyword above to search all your study materials.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (matchedNotes.isNotEmpty()) {
                        item {
                            Text(text = "Notes (${matchedNotes.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        items(matchedNotes) { note ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = note.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(text = note.content, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }

                    if (matchedFlashcards.isNotEmpty()) {
                        item {
                            Text(text = "Flashcards (${matchedFlashcards.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        items(matchedFlashcards) { card ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "Q: ${card.question}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(text = "A: ${card.answer}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }

                    if (matchedPdfs.isNotEmpty()) {
                        item {
                            Text(text = "PDF Summaries (${matchedPdfs.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        items(matchedPdfs) { pdf ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = pdf.fileName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(text = pdf.summaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
