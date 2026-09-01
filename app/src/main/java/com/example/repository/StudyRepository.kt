package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.database.AppDatabase
import com.example.data.database.entities.*
import com.example.data.pdf.PdfExtractionResult
import com.example.data.pdf.PdfExtractor
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

data class QuizQuestion(
    val question: String,
    val options: List<String>, // empty for Fill-in-the-blank
    val correctAnswer: String,
    val type: String, // "MCQ", "TRUE_FALSE", "FILL_BLANK"
    val explanation: String
)

class StudyRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    val noteDao = db.noteDao()
    val flashcardDao = db.flashcardDao()
    val quizResultDao = db.quizResultDao()
    val pdfSummaryDao = db.pdfSummaryDao()
    val chatMessageDao = db.chatMessageDao()
    val studyTaskDao = db.studyTaskDao()
    val pomodoroLogDao = db.pomodoroLogDao()

    // --- AI CHAT ---
    fun getChatMessages(sessionId: String = "default"): Flow<List<ChatMessageEntity>> =
        chatMessageDao.getMessagesForSession(sessionId)

    fun getAllChatMessages(): Flow<List<ChatMessageEntity>> =
        chatMessageDao.getAllMessages()

    suspend fun sendChatMessage(sessionId: String = "default", userText: String, modelName: String): String {
        // Fetch existing conversation history for this session before inserting current user message
        val history = chatMessageDao.getMessagesListForSession(sessionId)

        // Save user message to database
        val userMsg = ChatMessageEntity(sessionId = sessionId, sender = "user", text = userText)
        chatMessageDao.insertMessage(userMsg)

        // Build multi-turn contents list for Gemini API
        val contents = mutableListOf<GeminiContent>()

        // Include recent history (up to last 30 turns for thorough context while keeping token usage fast)
        val recentHistory = if (history.size > 30) history.takeLast(30) else history
        for (msg in recentHistory) {
            val role = if (msg.sender == "user") "user" else "model"
            if (msg.text.isNotBlank() && !msg.text.startsWith("API Error") && !msg.text.startsWith("API Key Error")) {
                contents.add(
                    GeminiContent(
                        role = role,
                        parts = listOf(GeminiPart(text = msg.text))
                    )
                )
            }
        }

        // Add current user message
        contents.add(
            GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(text = userText))
            )
        )

        // Ensure contents doesn't start with a 'model' role turn
        while (contents.isNotEmpty() && contents.first().role == "model") {
            contents.removeAt(0)
        }

        // Call Gemini API with full conversation context and multilingual support
        val systemPrompt = "You are Study Assistant AI, a helpful, clear, and encouraging personal tutor for students. Provide precise answers using Markdown formatting. Maintain conversation context and always refer to previous questions, answers, topics, and explanations when responding to follow-ups (such as 'explain briefly', 'explain in detail', 'give an example', 'simplify it', 'what does this mean?'). Understand the user's message in any supported language. Automatically detect the user's language and respond in the same language unless the user requests another language. Preserve the original meaning, context, technical terms, numbers, formulas, code, and names."

        val aiText = GeminiClient.generateChat(
            contents = contents,
            systemInstruction = systemPrompt,
            modelName = modelName
        )

        // Save AI response
        val aiMsg = ChatMessageEntity(sessionId = sessionId, sender = "ai", text = aiText)
        chatMessageDao.insertMessage(aiMsg)

        return aiText
    }

    suspend fun clearChatSession(sessionId: String = "default") {
        chatMessageDao.clearSession(sessionId)
    }

    // --- PDF ASSISTANT ---
    suspend fun extractPdfText(context: Context, uri: Uri, modelName: String): PdfExtractionResult {
        return PdfExtractor.extractTextFromUri(context, uri, modelName)
    }

    suspend fun savePdfToLibraryWithContext(context: Context, pdfResult: PdfExtractionResult, summaryText: String = "") {
        // Ensure the actual PDF file is stored permanently
        var permanentPath = pdfResult.localFilePath
        if (permanentPath.isBlank() || !java.io.File(permanentPath).exists()) {
            val source = pdfResult.tempCachePath.ifBlank { pdfResult.localFilePath }
            if (source.isNotBlank()) {
                val savedFile = PdfExtractor.savePermanentPdfFile(context, source, pdfResult.fileName)
                if (savedFile != null) {
                    permanentPath = savedFile.absolutePath
                }
            }
        }

        val existing = pdfSummaryDao.getSummaryByFileName(pdfResult.fileName)
        if (existing != null) {
            val updated = existing.copy(
                summaryText = summaryText.ifBlank { existing.summaryText.ifBlank { "Saved PDF (${pdfResult.pageCount} pages)" } },
                extractedText = pdfResult.extractedText,
                pageCount = pdfResult.pageCount,
                localFilePath = permanentPath.ifBlank { existing.localFilePath }
            )
            pdfSummaryDao.updatePdfSummary(updated)
        } else {
            val entity = PdfSummaryEntity(
                fileName = pdfResult.fileName,
                summaryText = summaryText.ifBlank { "Saved PDF Document (${pdfResult.pageCount} pages)" },
                extractedText = pdfResult.extractedText,
                pageCount = pdfResult.pageCount,
                localFilePath = permanentPath,
                keyPointsJson = "Saved on ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}"
            )
            pdfSummaryDao.insertPdfSummary(entity)
        }
    }

    suspend fun askPdfQuestion(pdfText: String, question: String, modelName: String): String {
        val prompt = """
            STRICT DOCUMENT Q&A TASK:
            You are answering a question based strictly on the text extracted from the user's PDF document below.
            
            --- PDF DOCUMENT CONTENT ---
            $pdfText
            --- END OF PDF DOCUMENT ---
            
            USER QUESTION: $question
            
            CRITICAL INSTRUCTIONS:
            1. Answer accurately and specifically using ONLY the information provided in the PDF text above.
            2. If the user's question can be answered from the PDF, give a clear, comprehensive, and well-structured answer.
            3. Understand the user's question in any supported language. Automatically respond in the same language as the question unless the user requests another language. Preserve technical terms, numbers, and formulas.
            4. If the answer is completely absent or cannot be found anywhere in the provided PDF text, respond:
               "Information regarding this question was not found in the uploaded PDF document."
            5. Do not make up facts or bring in unreferenced external information.
        """.trimIndent()
        return GeminiClient.generateText(prompt = prompt, modelName = modelName)
    }

    suspend fun generatePdfSummary(fileName: String, pdfText: String, modelName: String): String {
        val prompt = "Summarize the following study document in clear structured markdown with sections for Summary, Key Points, and Important Concepts:\n\n$pdfText"
        return GeminiClient.generateText(prompt = prompt, modelName = modelName)
    }

    fun getAllPdfSummaries(): Flow<List<PdfSummaryEntity>> = pdfSummaryDao.getAllPdfSummaries()

    suspend fun deletePdfSummary(id: Long) {
        val existing = pdfSummaryDao.getSummaryById(id)
        if (existing != null && !existing.localFilePath.isNullOrBlank()) {
            try {
                val file = java.io.File(existing.localFilePath)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        pdfSummaryDao.deletePdfSummaryById(id)
    }

    // --- OCR / IMAGE ANALYSIS ---
    suspend fun analyzeImage(imageBase64: String, userInstruction: String, modelName: String, languageName: String = "English"): String {
        val prompt = if (userInstruction.isNotBlank()) {
            """
            $userInstruction

            CRITICAL LANGUAGE INSTRUCTION:
            1. Provide your entire explanation, solution, notes, and analysis in $languageName.
            2. Preserve mathematical formulas, code, and technical terms accurately.
            """.trimIndent()
        } else {
            """
            Extract all text from this image and solve any questions, math formulas, diagrams, or problems presented.
            
            CRITICAL LANGUAGE INSTRUCTION:
            1. Provide a comprehensive, well-structured explanation and step-by-step solution in $languageName.
            2. Preserve mathematical formulas, numbers, and technical accuracy.
            """.trimIndent()
        }
        return GeminiClient.generateMultimodal(prompt = prompt, imageBase64 = imageBase64, modelName = modelName)
    }

    // --- NOTES ---
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()
    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    private fun writeNoteFile(context: Context, note: NoteEntity): String {
        return try {
            val dir = java.io.File(context.filesDir, "saved_notes")
            if (!dir.exists()) dir.mkdirs()
            val sanitized = note.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val targetFile = if (!note.localFilePath.isNullOrBlank() && java.io.File(note.localFilePath).exists()) {
                java.io.File(note.localFilePath)
            } else {
                java.io.File(dir, "note_${System.currentTimeMillis()}_$sanitized.txt")
            }
            targetFile.writeText("=== ${note.title} ===\nCategory: ${note.category}\nSource PDF: ${note.pdfFileName ?: "None"}\nSaved Date: ${java.util.Date()}\n\n${note.content}")
            targetFile.absolutePath
        } catch (e: Exception) {
            note.localFilePath ?: ""
        }
    }

    suspend fun saveNoteWithContext(context: Context, note: NoteEntity): Long {
        val filePath = writeNoteFile(context, note)
        val noteWithFile = note.copy(localFilePath = filePath)
        return noteDao.insertNote(noteWithFile)
    }

    suspend fun saveNote(note: NoteEntity): Long {
        return noteDao.insertNote(note)
    }

    suspend fun updateNoteWithContext(context: Context, note: NoteEntity) {
        val filePath = writeNoteFile(context, note)
        noteDao.updateNote(note.copy(localFilePath = filePath))
    }

    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)

    suspend fun deleteNote(id: Long) {
        val existing = noteDao.getNoteById(id)
        if (existing != null && !existing.localFilePath.isNullOrBlank()) {
            try {
                val f = java.io.File(existing.localFilePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        noteDao.deleteNoteById(id)
    }

    suspend fun generateNoteFromContent(title: String, rawContent: String, modelName: String): NoteEntity {
        val prompt = "Create a well-structured, neat study note with headers, bullet points, and key takeaways from the following text:\n\n$rawContent"
        val generatedContent = GeminiClient.generateText(prompt = prompt, modelName = modelName)
        val note = NoteEntity(
            title = title.ifBlank { "Study Note (${java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date())})" },
            content = generatedContent
        )
        val id = noteDao.insertNote(note)
        return note.copy(id = id)
    }

    // --- FLASHCARDS ---
    fun getAllFlashcards(): Flow<List<FlashcardEntity>> = flashcardDao.getAllFlashcards()
    fun getDeckNames(): Flow<List<String>> = flashcardDao.getAllDeckNames()
    fun getFlashcardsByDeck(deckName: String): Flow<List<FlashcardEntity>> = flashcardDao.getFlashcardsByDeck(deckName)

    suspend fun saveFlashcard(flashcard: FlashcardEntity) = flashcardDao.insertFlashcard(flashcard)
    suspend fun updateFlashcard(flashcard: FlashcardEntity) = flashcardDao.updateFlashcard(flashcard)
    suspend fun deleteFlashcard(flashcard: FlashcardEntity) = flashcardDao.deleteFlashcard(flashcard)
    suspend fun deleteDeck(deckName: String) = flashcardDao.deleteDeck(deckName)

    suspend fun generateFlashcards(deckName: String, topicOrContent: String, count: Int = 5, modelName: String): List<FlashcardEntity> {
        val prompt = """
            Generate exactly $count high-quality study flashcards based on the topic or content below.
            
            CRITICAL LANGUAGE & CONTENT RULES:
            1. Detect the language of the topic/content (e.g. Tamil, Telugu, Hindi, Spanish, French, German, English, etc.).
            2. Generate all questions and answers in that EXACT same language, unless specifically instructed otherwise.
            3. Preserve all technical terms, mathematical formulas, equations, numbers, and code accurately.
            
            Output JSON only in this exact format:
            [
              {"question": "Question text here?", "answer": "Answer text here."}
            ]
            
            Topic/Content:
            $topicOrContent
        """.trimIndent()

        val jsonResponse = GeminiClient.generateText(prompt = prompt, modelName = modelName, isJsonMode = true)
        if (jsonResponse.startsWith("API Error") || jsonResponse.startsWith("API Key Error")) {
            throw IllegalStateException(jsonResponse)
        }

        val createdList = mutableListOf<FlashcardEntity>()

        try {
            val jsonArray = JSONArray(jsonResponse.substringAfter("[").substringBeforeLast("]").let { "[$it]" })
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.getString("question")
                val a = obj.getString("answer")
                val entity = FlashcardEntity(deckName = deckName.ifBlank { "General Deck" }, question = q, answer = a)
                createdList.add(entity)
            }
            if (createdList.isNotEmpty()) {
                flashcardDao.insertFlashcards(createdList)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse flashcards AI response: ${e.message}")
        }
        return createdList
    }

    // --- AI QUIZ ---
    fun getAllQuizResults(): Flow<List<QuizResultEntity>> = quizResultDao.getAllQuizResults()
    suspend fun saveQuizResult(topic: String, score: Int, totalQuestions: Int) {
        val percentage = if (totalQuestions > 0) (score * 100) / totalQuestions else 0
        quizResultDao.insertQuizResult(
            QuizResultEntity(topic = topic, score = score, totalQuestions = totalQuestions, percentage = percentage)
        )
    }
    suspend fun deleteQuizResult(id: Long) = quizResultDao.deleteQuizResultById(id)

    suspend fun generateQuiz(
        subject: String = "",
        topicOrContent: String,
        difficulty: String = "Medium",
        questionCount: Int = 5,
        modelName: String
    ): List<QuizQuestion> {
        val randomToken = java.util.UUID.randomUUID().toString().take(8)
        val timestamp = System.currentTimeMillis()
        val subjectContext = if (subject.isNotBlank()) "Academic Field / Subject: \"$subject\"\n" else ""
        val prompt = """
            You are an expert subject-matter professor and quiz creator. Generate exactly $questionCount COMPLETELY NEW, UNIQUE, HIGHLY ACCURATE quiz questions.
            ${subjectContext}Specific Topic / Concept: "$topicOrContent"
            Difficulty Level: $difficulty
            Number of Questions: $questionCount
            Session Token: $randomToken-$timestamp
            
            CRITICAL REQUIREMENTS:
            1. Every single question MUST be strictly and directly focused on the requested topic "$topicOrContent"${if (subject.isNotBlank()) " in the domain of $subject" else ""}.
            2. LANGUAGE RULE: Detect the language of the topic/content (e.g. Tamil, Telugu, Hindi, Spanish, French, German, English, etc.). Generate all questions, options, and explanations in that EXACT same language, unless specifically instructed otherwise.
            3. Do NOT ask generic, unrelated, or off-topic questions. For example, if the topic is "Carry Lookahead Adder", every question must specifically test Carry Lookahead Adder logic, equations, propagate/generate signals, gate delays, or hardware implementation.
            4. Target Difficulty: $difficulty level questions (Easy: definitions and fundamental concepts; Medium: application, calculations, and intermediate analysis; Hard: advanced mechanics, edge cases, trade-offs, and critical problem solving).
            5. Provide a balanced mix of question types: Multiple Choice (MCQ), True/False (TRUE_FALSE), and Fill in the Blank (FILL_BLANK).
            6. For MCQ: provide 4 distinct, plausible options.
            7. For TRUE_FALSE: options must be ["True", "False"].
            8. For FILL_BLANK: options can be empty [].
            9. Format formulas and mathematical expressions cleanly in standard readable notation (e.g. E = mc², a/b, √x, P_i = A_i ⊕ B_i; do NOT output raw unparsed LaTeX formatting).
            
            Output strictly a valid JSON array of $questionCount questions in this format:
            [
              {
                "question": "Specific question testing $topicOrContent...",
                "options": ["Option A", "Option B", "Option C", "Option D"],
                "correctAnswer": "Option A",
                "type": "MCQ",
                "explanation": "Detailed explanation of why Option A is correct."
              }
            ]
        """.trimIndent()

        val response = GeminiClient.generateText(prompt = prompt, modelName = modelName, isJsonMode = true)
        if (response.startsWith("API Error") || response.startsWith("API Key Error")) {
            throw IllegalStateException(response)
        }

        val questions = mutableListOf<QuizQuestion>()

        try {
            val jsonArray = JSONArray(response.substringAfter("[").substringBeforeLast("]").let { "[$it]" })
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.getString("question")
                val type = obj.optString("type", "MCQ")
                val ans = obj.getString("correctAnswer")
                val exp = obj.optString("explanation", "Correct answer is $ans.")
                val optList = mutableListOf<String>()
                if (obj.has("options")) {
                    val optsArray = obj.getJSONArray("options")
                    for (j in 0 until optsArray.length()) {
                        optList.add(optsArray.getString(j))
                    }
                }
                
                // Shuffle options for MCQ if option list contains correct answer
                val finalOptions = if (type == "MCQ" && optList.contains(ans)) {
                    optList.shuffled()
                } else optList

                questions.add(QuizQuestion(question = q, options = finalOptions, correctAnswer = ans, type = type, explanation = exp))
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse quiz response: ${e.message}")
        }
        return questions.shuffled()
    }

    // --- STUDY TASKS ---
    fun getAllTasks(): Flow<List<StudyTaskEntity>> = studyTaskDao.getAllTasks()
    suspend fun saveTask(task: StudyTaskEntity) = studyTaskDao.insertTask(task)
    suspend fun updateTask(task: StudyTaskEntity) = studyTaskDao.updateTask(task)
    suspend fun deleteTask(id: Long) = studyTaskDao.deleteTaskById(id)

    // --- POMODORO ---
    fun getAllPomodoroLogs(): Flow<List<PomodoroLogEntity>> = pomodoroLogDao.getAllLogs()
    fun getTotalFocusMinutes(): Flow<Int?> = pomodoroLogDao.getTotalFocusMinutes()
    suspend fun logPomodoroSession(minutesSpent: Int, subject: String) {
        pomodoroLogDao.insertLog(PomodoroLogEntity(minutesSpent = minutesSpent, subject = subject))
    }
}
