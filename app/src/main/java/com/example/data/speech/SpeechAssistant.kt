package com.example.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Recognized(val text: String) : VoiceState()
    object Thinking : VoiceState()
    data class Speaking(val answer: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class SpeechAssistant(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState

    private val _spokenText = MutableStateFlow("")
    val spokenText: StateFlow<String> = _spokenText

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    init {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer?.setRecognitionListener(this)
            }
        } catch (e: Throwable) {
            android.util.Log.e("SpeechAssistant", "SpeechRecognizer init failed: ${e.message}", e)
        }
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            android.util.Log.e("SpeechAssistant", "TextToSpeech init failed: ${e.message}", e)
        }
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        if (next) {
            textToSpeech?.stop()
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            textToSpeech?.stop()
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.Error("Speech recognition is not available on this device.")
            return
        }
        stopSpeaking()
        _spokenText.value = ""
        _voiceState.value = VoiceState.Listening

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        if (_voiceState.value == VoiceState.Listening) {
            _voiceState.value = VoiceState.Idle
        }
    }

    fun speakText(text: String) {
        _voiceState.value = VoiceState.Speaking(text)
        if (!_isMuted.value && isTtsReady) {
            textToSpeech?.stop()
            textToSpeech?.speak(text.take(3000), TextToSpeech.QUEUE_FLUSH, null, "TTS_STUDY_AI")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    fun setThinkingState() {
        _voiceState.value = VoiceState.Thinking
    }

    fun setIdleState() {
        _voiceState.value = VoiceState.Idle
    }

    fun destroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            isTtsReady = true
        } else {
            isTtsReady = false
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        if (_voiceState.value == VoiceState.Listening) {
            _voiceState.value = VoiceState.Recognized(_spokenText.value)
        }
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please speak clearly."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            else -> "Speech recognition error ($error)"
        }
        _voiceState.value = VoiceState.Error(errorMessage)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _spokenText.value = text
            _voiceState.value = VoiceState.Recognized(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _spokenText.value = text
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
