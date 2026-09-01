package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiModelConfig
import com.example.data.database.entities.*
import com.example.data.pdf.PdfExtractionResult
import com.example.data.pdf.PdfExtractor
import com.example.data.preferences.UserPreferences
import com.example.data.speech.SpeechAssistant
import com.example.data.speech.VoiceState
import com.example.data.notifications.StudyNotificationHelper
import com.example.repository.QuizQuestion
import com.example.repository.StudyRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ChatSessionInfo(
    val sessionId: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long,
    val messageCount: Int
)

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

sealed interface AuthStatus {
    object Initializing : AuthStatus
    object Authenticated : AuthStatus
    object Unauthenticated : AuthStatus
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = StudyRepository(application)
    val preferences = UserPreferences(application)
    val speechAssistant = SpeechAssistant(application)
    private val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Throwable) {
        android.util.Log.e("MainViewModel", "FirebaseAuth init error: ${e.message}", e)
        null
    }

    private val isInitiallyLoggedIn = (firebaseAuth?.currentUser != null || preferences.isUserLoggedInSync())

    private val _authStatus = MutableStateFlow<AuthStatus>(
        if (isInitiallyLoggedIn) AuthStatus.Authenticated else AuthStatus.Initializing
    )
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    companion object {
        const val GOOGLE_WEB_CLIENT_ID = "27570870551-eguvsuft8802cov7emfigr16jfa416hg.apps.googleusercontent.com"
    }

    init {
        // Automatically restore session if Firebase user is already logged in
        val initialUser = try { firebaseAuth?.currentUser } catch (_: Throwable) { null }
        if (initialUser != null) {
            _authStatus.value = AuthStatus.Authenticated
            viewModelScope.launch(Dispatchers.IO) {
                val email = initialUser.email ?: ""
                val name = initialUser.displayName?.ifBlank { null }
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                val photoUrl = initialUser.photoUrl?.toString() ?: ""
                preferences.setAuth(
                    isLoggedIn = true,
                    email = email,
                    name = name,
                    photoUrl = photoUrl
                )
            }
        } else if (preferences.isUserLoggedInSync()) {
            _authStatus.value = AuthStatus.Authenticated
        }

        // Monitor DataStore login state
        viewModelScope.launch {
            preferences.isLoggedIn.collect { loggedIn ->
                val fbUser = try { firebaseAuth?.currentUser } catch (_: Throwable) { null }
                if (loggedIn || fbUser != null || preferences.isUserLoggedInSync()) {
                    _authStatus.value = AuthStatus.Authenticated
                } else {
                    _authStatus.value = AuthStatus.Unauthenticated
                }
            }
        }

        // Monitor Firebase Auth state changes
        try {
            firebaseAuth?.addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null) {
                    _authStatus.value = AuthStatus.Authenticated
                    viewModelScope.launch(Dispatchers.IO) {
                        val email = user.email ?: ""
                        val name = user.displayName?.ifBlank { null }
                            ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                        val photoUrl = user.photoUrl?.toString() ?: ""
                        preferences.setAuth(
                            isLoggedIn = true,
                            email = email,
                            name = name,
                            photoUrl = photoUrl
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainViewModel", "Failed to attach AuthStateListener: ${e.message}", e)
        }

        verifyAndMigrateModels()
    }

    private val _availableModels = MutableStateFlow(GeminiModelConfig.AVAILABLE_MODELS)
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private fun verifyAndMigrateModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = preferences.selectedModel.firstOrNull() ?: GeminiModelConfig.DEFAULT_MODEL
            val sanitized = GeminiModelConfig.sanitizeModelName(current)
            if (current != sanitized) {
                preferences.setSelectedModel(sanitized)
            }
        }
    }

    // User preferences state
    val isLoggedIn = _authStatus.map { it is AuthStatus.Authenticated }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            isInitiallyLoggedIn
        )
    val userEmail = preferences.userEmail.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val userPhotoUrl = preferences.userPhotoUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val themeMode = preferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")
    val appLanguage = preferences.appLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")
    val selectedModel = preferences.selectedModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeminiModelConfig.DEFAULT_MODEL)
    val userName = preferences.userName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Scholar")
    val dailyGoalMinutes = preferences.dailyGoalMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    // Data flows from Room DB
    val allNotes = repository.getAllNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTasks = repository.getAllTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allFlashcards = repository.getAllFlashcards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deckNames = repository.getDeckNames().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _selectedDeckName = MutableStateFlow<String?>(null)
    val selectedDeckName: StateFlow<String?> = _selectedDeckName
    val quizResults = repository.getAllQuizResults().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pdfSummaries = repository.getAllPdfSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalFocusMinutes = repository.getTotalFocusMinutes()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val allPomodoroLogs = repository.getAllPomodoroLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic AI Chat Sessions State
    private val _currentSessionId = MutableStateFlow("default")
    val currentSessionId: StateFlow<String> = _currentSessionId

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatMessages = _currentSessionId.flatMapLatest { sessionId ->
        repository.getChatMessages(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChatSessions = repository.getAllChatMessages().map { messages ->
        messages.filter { it.sessionId != "voice" }
            .groupBy { it.sessionId }
            .map { (sessionId, sessionMsgs) ->
                val sorted = sessionMsgs.sortedBy { it.timestamp }
                val firstUserMsg = sorted.firstOrNull { it.sender == "user" }?.text ?: sorted.firstOrNull()?.text ?: "New Chat"
                val title = if (firstUserMsg.length > 36) firstUserMsg.take(36) + "..." else firstUserMsg
                val lastMsg = sorted.lastOrNull()?.text ?: ""
                val timestamp = sorted.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                ChatSessionInfo(
                    sessionId = sessionId,
                    title = title,
                    lastMessage = lastMsg,
                    timestamp = timestamp,
                    messageCount = sorted.size
                )
            }.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States for features
    private val _chatState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val chatState: StateFlow<UiState<String>> = _chatState

    private val _pdfState = MutableStateFlow<UiState<PdfExtractionResult>>(UiState.Idle)
    val pdfState: StateFlow<UiState<PdfExtractionResult>> = _pdfState

    private val _pdfQnaState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val pdfQnaState: StateFlow<UiState<String>> = _pdfQnaState

    private val _ocrState = MutableStateFlow<UiState<Pair<String, String>>>(UiState.Idle) // Pair(ExtractedText, Solution)
    val ocrState: StateFlow<UiState<Pair<String, String>>> = _ocrState

    private val _ocrBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val ocrBitmap: StateFlow<android.graphics.Bitmap?> = _ocrBitmap

    fun setOcrBitmap(bitmap: android.graphics.Bitmap?) {
        _ocrBitmap.value = bitmap
    }

    private val _quizState = MutableStateFlow<UiState<List<QuizQuestion>>>(UiState.Idle)
    val quizState: StateFlow<UiState<List<QuizQuestion>>> = _quizState

    // Pomodoro Timer State
    private val _pomodoroTargetMinutes = MutableStateFlow(25)
    val pomodoroTargetMinutes: StateFlow<Int> = _pomodoroTargetMinutes

    private val _pomodoroSecondsLeft = MutableStateFlow(25 * 60)
    val pomodoroSecondsLeft: StateFlow<Int> = _pomodoroSecondsLeft

    private val _isPomodoroRunning = MutableStateFlow(false)
    val isPomodoroRunning: StateFlow<Boolean> = _isPomodoroRunning

    private var pomodoroJob: Job? = null
    private var sessionStartTimeMs: Long = 0L

    // --- AUTHENTICATION METHODS ---
    suspend fun signInWithGoogle(context: Context): Result<String> {
        return try {
            val credentialManager = CredentialManager.create(context)

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID)
                .build()

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            var idToken: String? = null

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    idToken = googleIdTokenCredential.idToken
                } catch (_: Exception) {
                    // Fallback below
                }
            }

            if (idToken.isNullOrBlank()) {
                idToken = credential.data.getString("androidx.credentials.BUNDLE_KEY_ID_TOKEN")
                    ?: credential.data.getString("id_token")
                    ?: credential.data.getString("google_id_token")
                    ?: credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
            }

            if (idToken.isNullOrBlank()) {
                return Result.failure(Exception("No Google ID token received from authentication. Please try again."))
            }

            val authCredential = GoogleAuthProvider.getCredential(idToken, null)

            val auth = firebaseAuth ?: return Result.failure(Exception("Authentication service is initializing. Please try again."))
            val authResult = suspendCancellableCoroutine { continuation ->
                auth.signInWithCredential(authCredential)
                    .addOnSuccessListener { res ->
                        if (continuation.isActive) continuation.resume(res)
                    }
                    .addOnFailureListener { ex ->
                        if (continuation.isActive) continuation.resumeWith(Result.failure(ex))
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.cancel()
                    }
            }

            val user = authResult.user
            if (user != null) {
                val email = user.email ?: ""
                val name = user.displayName?.ifBlank { null }
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                val photoUrl = user.photoUrl?.toString() ?: ""

                preferences.setAuth(
                    isLoggedIn = true,
                    email = email,
                    name = name,
                    photoUrl = photoUrl
                )
                Result.success(name)
            } else {
                Result.failure(Exception("Firebase did not return a user session."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Google Sign-In was cancelled."))
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            Result.failure(Exception("No Google accounts found on device."))
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Result.failure(Exception("An account already exists with this email address."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception(e.localizedMessage ?: "Invalid Google credentials."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception(e.localizedMessage ?: "User account is disabled or deleted."))
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Result.failure(Exception("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            val rawMsg = e.localizedMessage ?: e.message ?: ""
            val friendlyMsg = when {
                rawMsg.contains("16", ignoreCase = true) || rawMsg.contains("canceled", ignoreCase = true) || rawMsg.contains("cancelled", ignoreCase = true) ->
                    "Google sign-in was cancelled."
                rawMsg.contains("network", ignoreCase = true) ->
                    "Network error. Please check your internet connection."
                rawMsg.contains("unauthorized", ignoreCase = true) ->
                    "Unauthorized Google OAuth client or domain."
                rawMsg.contains("developer_error", ignoreCase = true) || rawMsg.contains("10:", ignoreCase = true) ->
                    "Google Sign-In configuration error. Please ensure SHA-1 fingerprint is added in Firebase Console."
                rawMsg.isNotBlank() -> rawMsg
                else -> "Failed to sign in with Google."
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun registerWithEmail(email: String, password: String, name: String): Result<String> {
        val auth = firebaseAuth ?: return Result.failure(Exception("Authentication service is initializing. Please try again."))
        return try {
            val authResult = suspendCancellableCoroutine { continuation ->
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { res -> continuation.resume(res) }
                    .addOnFailureListener { ex -> continuation.resumeWith(Result.failure(ex)) }
                    .addOnCanceledListener { continuation.cancel() }
            }
            val user = authResult.user
            if (user != null) {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(name.ifBlank { "Scholar" })
                    .build()
                user.updateProfile(profileUpdates)

                val userEmail = user.email ?: email
                val displayName = name.ifBlank { userEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }
                preferences.setAuth(
                    isLoggedIn = true,
                    email = userEmail,
                    name = displayName,
                    photoUrl = ""
                )
                Result.success(displayName)
            } else {
                Result.failure(Exception("Failed to create user account."))
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Result.failure(Exception("An account already exists with this email."))
        } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("Password is too weak. Please use at least 6 characters."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("The email address format is invalid."))
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Result.failure(Exception("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Registration failed. Please try again."))
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<String> {
        val auth = firebaseAuth ?: return Result.failure(Exception("Authentication service is initializing. Please try again."))
        return try {
            val authResult = suspendCancellableCoroutine { continuation ->
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { res -> continuation.resume(res) }
                    .addOnFailureListener { ex -> continuation.resumeWith(Result.failure(ex)) }
                    .addOnCanceledListener { continuation.cancel() }
            }
            val user = authResult.user
            if (user != null) {
                val userEmail = user.email ?: email
                val userName = user.displayName?.ifBlank { null }
                    ?: userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                val photoUrl = user.photoUrl?.toString() ?: ""
                preferences.setAuth(
                    isLoggedIn = true,
                    email = userEmail,
                    name = userName,
                    photoUrl = photoUrl
                )
                Result.success(userName)
            } else {
                Result.failure(Exception("Unable to sign in."))
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception("Account not found. Please register."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("user", ignoreCase = true) && msg.contains("not found", ignoreCase = true)) {
                Result.failure(Exception("Account not found. Please register."))
            } else {
                Result.failure(Exception("Incorrect password. Please try again."))
            }
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Result.failure(Exception("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Failed to sign in."
            if (msg.contains("user-not-found", ignoreCase = true) || msg.contains("no user record", ignoreCase = true)) {
                Result.failure(Exception("Account not found. Please register."))
            } else if (msg.contains("wrong-password", ignoreCase = true) || msg.contains("invalid-credential", ignoreCase = true)) {
                Result.failure(Exception("Incorrect password. Please try again."))
            } else {
                Result.failure(Exception(msg))
            }
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return Result.failure(Exception("Please enter your registered email address."))
        }
        val auth = firebaseAuth ?: return Result.failure(Exception("Authentication service is initializing. Please try again."))
        return try {
            suspendCancellableCoroutine { continuation ->
                auth.sendPasswordResetEmail(cleanEmail)
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { ex ->
                        if (continuation.isActive) continuation.resume(Result.failure(ex))
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.cancel()
                    }
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception("No account found with this email address."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("The email address format is invalid."))
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Result.failure(Exception("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to send reset email. Please try again."))
        }
    }

    fun logout() {
        _authStatus.value = AuthStatus.Unauthenticated
        viewModelScope.launch {
            try {
                firebaseAuth?.signOut()
            } catch (_: Exception) {}
            preferences.logout()
        }
    }

    // --- AI CHAT METHODS (ChatGPT-style history) ---
    fun startNewChat() {
        val newSessionId = "chat_${System.currentTimeMillis()}"
        _currentSessionId.value = newSessionId
        _chatState.value = UiState.Idle
    }

    fun switchChatSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _chatState.value = UiState.Idle
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            repository.clearChatSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                startNewChat()
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        val sessionId = _currentSessionId.value
        viewModelScope.launch {
            _chatState.value = UiState.Loading
            val model = selectedModel.value
            val response = repository.sendChatMessage(sessionId, userText, model)
            if (response.startsWith("API Error") || response.startsWith("API Key Error")) {
                _chatState.value = UiState.Error(response)
            } else {
                _chatState.value = UiState.Success(response)
            }
        }
    }

    fun clearChat() {
        val current = _currentSessionId.value
        viewModelScope.launch {
            repository.clearChatSession(current)
            _chatState.value = UiState.Idle
        }
    }

    // --- VOICE ASSISTANT METHODS ---
    fun startVoiceListening() {
        speechAssistant.startListening()
    }

    fun stopVoiceListening() {
        speechAssistant.stopListening()
    }

    fun processVoiceQuery(queryText: String) {
        if (queryText.isBlank()) return
        viewModelScope.launch {
            speechAssistant.setThinkingState()
            val model = selectedModel.value
            val answer = repository.sendChatMessage("voice", queryText, model)
            if (!answer.startsWith("API Error") && !answer.startsWith("API Key Error")) {
                speechAssistant.speakText(answer)
            } else {
                speechAssistant.speakText("Sorry, I encountered an issue reaching the AI service.")
            }
        }
    }

    // --- PDF ASSISTANT METHODS ---
    fun loadPdf(uri: Uri) {
        _pdfQnaState.value = UiState.Idle
        viewModelScope.launch {
            _pdfState.value = UiState.Loading
            val model = selectedModel.value
            val result = repository.extractPdfText(getApplication(), uri, model)
            if (result.extractedText.startsWith("PDF Extraction Error")) {
                _pdfState.value = UiState.Error(result.extractedText)
            } else {
                _pdfState.value = UiState.Success(result)
            }
        }
    }

    fun savePdfToLibrary(pdfResult: PdfExtractionResult, summaryText: String = "") {
        viewModelScope.launch {
            repository.savePdfToLibraryWithContext(getApplication(), pdfResult, summaryText)
        }
    }

    fun openSavedPdf(summary: PdfSummaryEntity) {
        _pdfQnaState.value = UiState.Idle
        viewModelScope.launch {
            _pdfState.value = UiState.Loading
            val model = selectedModel.value
            val file = if (!summary.localFilePath.isNullOrBlank()) java.io.File(summary.localFilePath) else null
            
            val result = if (file != null && file.exists()) {
                PdfExtractor.extractTextFromFile(getApplication(), file, model)
            } else {
                PdfExtractionResult(
                    fileName = summary.fileName,
                    pageCount = summary.pageCount,
                    extractedText = summary.extractedText,
                    localFilePath = summary.localFilePath
                )
            }
            _pdfState.value = UiState.Success(result)
        }
    }

    fun askPdfQuestion(pdfText: String, question: String) {
        if (pdfText.isBlank() || question.isBlank()) return
        viewModelScope.launch {
            _pdfQnaState.value = UiState.Loading
            val model = selectedModel.value
            val answer = repository.askPdfQuestion(pdfText, question, model)
            if (answer.startsWith("API Error") || answer.startsWith("API Key Error")) {
                _pdfQnaState.value = UiState.Error(answer)
            } else {
                _pdfQnaState.value = UiState.Success(answer)
            }
        }
    }

    fun generatePdfSummary(fileName: String, pdfText: String) {
        if (pdfText.isBlank()) return
        viewModelScope.launch {
            _pdfQnaState.value = UiState.Loading
            val model = selectedModel.value
            val summary = repository.generatePdfSummary(fileName, pdfText, model)
            if (summary.startsWith("API Error") || summary.startsWith("API Key Error")) {
                _pdfQnaState.value = UiState.Error(summary)
            } else {
                _pdfQnaState.value = UiState.Success(summary)
            }
        }
    }

    fun deletePdfSummary(id: Long) {
        viewModelScope.launch {
            repository.deletePdfSummary(id)
        }
    }

    private fun getLanguageDisplayName(langCode: String): String {
        return when (langCode.lowercase().trim()) {
            "ta" -> "Tamil (தமிழ்)"
            "hi" -> "Hindi (हिन्दी)"
            "te" -> "Telugu (తెలుగు)"
            "ml" -> "Malayalam (മലയാളം)"
            "kn" -> "Kannada (ಕನ್ನಡ)"
            "bn" -> "Bengali (বাংলা)"
            "mr" -> "Marathi (मराठी)"
            "gu" -> "Gujarati (ગુજરાતી)"
            "pa" -> "Punjabi (ਪੰਜਾਬੀ)"
            "ur" -> "Urdu (اردو)"
            "es" -> "Spanish (Español)"
            "fr" -> "French (Français)"
            "de" -> "German (Deutsch)"
            "zh" -> "Chinese (中文)"
            "ja" -> "Japanese (日本語)"
            "ko" -> "Korean (한국어)"
            "ar" -> "Arabic (العربية)"
            "pt" -> "Portuguese (Português)"
            "ru" -> "Russian (Русский)"
            else -> "English"
        }
    }

    // --- OCR METHODS ---
    fun processImage(imageBase64: String, userInstruction: String) {
        if (imageBase64.isBlank()) return
        viewModelScope.launch {
            _ocrState.value = UiState.Loading
            val model = selectedModel.value
            val langName = getLanguageDisplayName(appLanguage.value)
            val result = repository.analyzeImage(imageBase64, userInstruction, model, langName)
            if (result.startsWith("API Error") || result.startsWith("API Key Error")) {
                _ocrState.value = UiState.Error(result)
            } else {
                _ocrState.value = UiState.Success(Pair("Extracted Image Content", result))
            }
        }
    }

    // --- NOTES METHODS ---
    fun saveNote(title: String, content: String, pdfFileName: String? = null) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            repository.saveNoteWithContext(
                getApplication(),
                NoteEntity(title = title.ifBlank { "Untitled Note" }, content = content, pdfFileName = pdfFileName)
            )
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNoteWithContext(getApplication(), note)
        }
    }

    fun generateAiNote(title: String, rawContent: String, onGenerated: (NoteEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val model = selectedModel.value
                val note = repository.generateNoteFromContent(title, rawContent, model)
                onGenerated(note)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun toggleNotePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    // --- FLASHCARDS METHODS ---
    fun setSelectedDeck(deckName: String?) {
        _selectedDeckName.value = deckName
    }

    fun generateFlashcards(deckName: String, topicOrContent: String, count: Int = 5) {
        if (topicOrContent.isBlank()) return
        viewModelScope.launch {
            val model = selectedModel.value
            val effectiveDeckName = deckName.ifBlank { "General Deck" }
            _selectedDeckName.value = effectiveDeckName
            repository.generateFlashcards(effectiveDeckName, topicOrContent, count, model)
        }
    }

    fun addManualFlashcard(deckName: String, question: String, answer: String) {
        if (question.isBlank() || answer.isBlank()) return
        viewModelScope.launch {
            val effectiveDeckName = deckName.ifBlank { "General Deck" }
            _selectedDeckName.value = effectiveDeckName
            repository.saveFlashcard(
                FlashcardEntity(deckName = effectiveDeckName, question = question, answer = answer)
            )
        }
    }

    fun deleteFlashcard(flashcard: FlashcardEntity) {
        viewModelScope.launch {
            repository.deleteFlashcard(flashcard)
        }
    }

    fun deleteDeck(deckName: String) {
        viewModelScope.launch {
            repository.deleteDeck(deckName)
            if (_selectedDeckName.value == deckName) {
                _selectedDeckName.value = null
            }
        }
    }

    // --- AI QUIZ METHODS ---
    fun generateQuiz(
        topicOrContent: String,
        subject: String = "",
        difficulty: String = "Medium",
        questionCount: Int = 5
    ) {
        if (topicOrContent.isBlank()) return
        viewModelScope.launch {
            _quizState.value = UiState.Loading
            try {
                val model = selectedModel.value
                val questions = repository.generateQuiz(
                    subject = subject,
                    topicOrContent = topicOrContent,
                    difficulty = difficulty,
                    questionCount = questionCount,
                    modelName = model
                )
                if (questions.isNotEmpty()) {
                    _quizState.value = UiState.Success(questions)
                } else {
                    _quizState.value = UiState.Error("Failed to generate quiz questions.")
                }
            } catch (e: Exception) {
                _quizState.value = UiState.Error(e.message ?: "Failed to generate quiz questions.")
            }
        }
    }

    fun resetQuizState() {
        _quizState.value = UiState.Idle
    }

    fun saveQuizScore(topic: String, score: Int, totalQuestions: Int) {
        viewModelScope.launch {
            repository.saveQuizResult(topic, score, totalQuestions)
        }
    }

    fun deleteQuizResult(id: Long) {
        viewModelScope.launch {
            repository.deleteQuizResult(id)
        }
    }

    // --- STUDY TIMETABLE & PLANNER METHODS ---
    fun addTask(title: String, subject: String, dueDate: String, dueTime: String = "10:00 AM", priority: String = "Medium") {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = StudyTaskEntity(
                title = title.trim(),
                subject = subject.ifBlank { "General" }.trim(),
                dueDate = dueDate.trim(),
                dueTime = dueTime.trim(),
                priority = priority,
                isCompleted = false
            )
            val taskId = repository.saveTask(task)
            val lang = preferences.appLanguage.firstOrNull() ?: "en"
            StudyNotificationHelper.scheduleTaskReminder(
                context = getApplication(),
                task = task.copy(id = taskId),
                languageCode = lang
            )
        }
    }

    fun updateTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
            if (task.isCompleted) {
                StudyNotificationHelper.cancelTaskReminder(getApplication(), task.id)
            } else {
                val lang = preferences.appLanguage.firstOrNull() ?: "en"
                StudyNotificationHelper.scheduleTaskReminder(
                    context = getApplication(),
                    task = task,
                    languageCode = lang
                )
            }
        }
    }

    fun toggleTaskCompleted(task: StudyTaskEntity) {
        val updated = task.copy(isCompleted = !task.isCompleted)
        viewModelScope.launch {
            repository.updateTask(updated)
            if (updated.isCompleted) {
                StudyNotificationHelper.cancelTaskReminder(getApplication(), task.id)
            } else {
                val lang = preferences.appLanguage.firstOrNull() ?: "en"
                StudyNotificationHelper.scheduleTaskReminder(
                    context = getApplication(),
                    task = updated,
                    languageCode = lang
                )
            }
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            StudyNotificationHelper.cancelTaskReminder(getApplication(), id)
            repository.deleteTask(id)
        }
    }

    fun triggerUnfinishedTasksNotificationCheck() {
        viewModelScope.launch {
            val tasks = repository.getAllTasks().firstOrNull() ?: return@launch
            val lang = preferences.appLanguage.firstOrNull() ?: "en"
            StudyNotificationHelper.checkAndNotifyUnfinishedTasks(getApplication(), tasks, lang)
        }
    }

    // --- POMODORO TIMER METHODS ---
    fun setPomodoroDuration(minutes: Int) {
        pausePomodoro()
        _pomodoroTargetMinutes.value = minutes
        _pomodoroSecondsLeft.value = minutes * 60
    }

    fun startPomodoro() {
        if (_isPomodoroRunning.value) return
        _isPomodoroRunning.value = true
        sessionStartTimeMs = System.currentTimeMillis()
        val initialSeconds = _pomodoroSecondsLeft.value
        val targetSessionMinutes = _pomodoroTargetMinutes.value

        pomodoroJob?.cancel()
        pomodoroJob = viewModelScope.launch {
            while (_pomodoroSecondsLeft.value > 0 && _isPomodoroRunning.value) {
                delay(1000)
                if (_isPomodoroRunning.value) {
                    val elapsedSeconds = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000).toInt()
                    val newSecondsLeft = (initialSeconds - elapsedSeconds).coerceAtLeast(0)
                    _pomodoroSecondsLeft.value = newSecondsLeft
                }
            }
            if (_pomodoroSecondsLeft.value == 0 && _isPomodoroRunning.value) {
                // Completed full focus session - automatically log and update total
                _isPomodoroRunning.value = false
                repository.logPomodoroSession(targetSessionMinutes, "Pomodoro Focus (${targetSessionMinutes}m)")
                _pomodoroSecondsLeft.value = _pomodoroTargetMinutes.value * 60
            }
        }
    }

    fun pausePomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
    }

    fun resetPomodoro() {
        pausePomodoro()
        _pomodoroSecondsLeft.value = _pomodoroTargetMinutes.value * 60
    }

    fun stopAndLogCurrentFocus() {
        val totalSecondsInTarget = _pomodoroTargetMinutes.value * 60
        val secondsStudied = (totalSecondsInTarget - _pomodoroSecondsLeft.value).coerceAtLeast(0)
        // If studied for at least 30 seconds, round to minutes; if less but >0, count 1 min
        val minutesStudied = when {
            secondsStudied >= 45 -> (secondsStudied + 30) / 60
            secondsStudied >= 10 -> 1
            else -> 0
        }
        pausePomodoro()
        if (minutesStudied > 0) {
            viewModelScope.launch {
                repository.logPomodoroSession(minutesStudied, "Focus Session (${minutesStudied}m)")
            }
        }
        resetPomodoro()
    }

    // --- PREFERENCES METHODS ---
    fun updateThemeMode(mode: String) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun updateAppLanguage(languageCode: String) {
        viewModelScope.launch { preferences.setAppLanguage(languageCode) }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch { preferences.setSelectedModel(model) }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch { preferences.setUserName(name) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            preferences.clearAll()
            // clear tables
            repository.clearChatSession("default")
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechAssistant.destroy()
    }
}
