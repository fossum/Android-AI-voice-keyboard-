package com.fossum.voicekeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The core Input Method Service that provides the AI-enhanced voice keyboard.
 *
 * Flow:
 *  1. User taps the microphone button → [SpeechRecognizer] starts listening.
 *  2. Android's on-device speech-to-text converts audio → raw transcript.
 *  3. The raw transcript is sent to [GeminiService] which calls the Gemini API
 *     using the OAuth2 token from the user's Google account ([TokenStore]).
 *  4. The polished sentence returned by Gemini is committed to the focused
 *     input field.
 */
class VoiceKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "VoiceKeyboardService"
    }

    // ── UI references ────────────────────────────────────────────────────────
    private var keyboardView: View? = null
    private var btnMic: ImageButton? = null
    private var tvStatus: TextView? = null
    private var tvPreview: TextView? = null
    private var progressBar: ProgressBar? = null
    private var btnSettings: ImageButton? = null

    // ── Speech recognition ───────────────────────────────────────────────────
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // ── Coroutines ───────────────────────────────────────────────────────────
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_view, null)

        keyboardView = view
        btnMic = view.findViewById(R.id.btn_mic)
        tvStatus = view.findViewById(R.id.tv_status)
        tvPreview = view.findViewById(R.id.tv_preview)
        progressBar = view.findViewById(R.id.progress_bar)
        btnSettings = view.findViewById(R.id.btn_settings)

        btnMic?.setOnClickListener { onMicClicked() }
        btnSettings?.setOnClickListener { openSettings() }

        initSpeechRecognizer()
        updateIdleState()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Warn the user if no Google account token is available yet
        if (!TokenStore.hasValidToken(this)) {
            tvStatus?.text = getString(R.string.status_sign_in_required)
        }
    }

    override fun onDestroy() {
        destroySpeechRecognizer()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Mic button ───────────────────────────────────────────────────────────

    private fun onMicClicked() {
        if (!TokenStore.hasValidToken(this)) {
            Toast.makeText(this, getString(R.string.please_sign_in), Toast.LENGTH_LONG).show()
            openSettings()
            return
        }
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    // ── Speech recognizer ────────────────────────────────────────────────────

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus?.text = getString(R.string.speech_not_available)
            btnMic?.isEnabled = false
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
    }

    private fun destroySpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isListening = true
        updateListeningState()
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        updateIdleState()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            tvStatus?.text = getString(R.string.listening)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            tvStatus?.text = getString(R.string.processing)
            progressBar?.visibility = View.VISIBLE
            btnMic?.isEnabled = false
        }

        override fun onError(error: Int) {
            isListening = false
            val message = speechErrorMessage(error)
            Log.w(TAG, "Speech recognition error: $error – $message")
            updateIdleState()
            Toast.makeText(this@VoiceKeyboardService, message, Toast.LENGTH_SHORT).show()
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull()
            if (transcript.isNullOrBlank()) {
                updateIdleState()
                return
            }
            tvPreview?.text = transcript
            processWithGemini(transcript)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                tvPreview?.text = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ── Gemini AI enhancement ────────────────────────────────────────────────

    private fun processWithGemini(rawTranscript: String) {
        val token = TokenStore.getToken(this)
        if (token.isNullOrEmpty()) {
            // Fallback: commit the raw transcript without AI enhancement
            Log.w(TAG, "No OAuth token; committing raw transcript")
            commitText(rawTranscript)
            updateIdleState()
            return
        }

        serviceScope.launch {
            try {
                val geminiService = GeminiService(token)
                val enhanced = geminiService.enhance(rawTranscript)
                Log.d(TAG, "Gemini enhanced: \"$enhanced\"")
                withContext(Dispatchers.Main) {
                    tvPreview?.text = enhanced
                    commitText(enhanced)
                }
            } catch (e: GeminiException) {
                Log.e(TAG, "Gemini error; falling back to raw transcript", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VoiceKeyboardService,
                        getString(R.string.gemini_error_fallback),
                        Toast.LENGTH_SHORT
                    ).show()
                    commitText(rawTranscript)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    updateIdleState()
                }
            }
        }
    }

    /** Inserts [text] at the current cursor position in the connected input field. */
    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private fun updateIdleState() {
        isListening = false
        btnMic?.isEnabled = true
        btnMic?.setImageResource(R.drawable.ic_mic)
        tvStatus?.text = getString(R.string.tap_to_speak)
        progressBar?.visibility = View.GONE
    }

    private fun updateListeningState() {
        btnMic?.setImageResource(R.drawable.ic_mic_active)
        tvStatus?.text = getString(R.string.listening)
        tvPreview?.text = ""
        progressBar?.visibility = View.GONE
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> getString(R.string.error_audio)
        SpeechRecognizer.ERROR_CLIENT -> getString(R.string.error_client)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            getString(R.string.error_permissions)
        SpeechRecognizer.ERROR_NETWORK -> getString(R.string.error_network)
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> getString(R.string.error_network_timeout)
        SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.error_no_match)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> getString(R.string.error_recognizer_busy)
        SpeechRecognizer.ERROR_SERVER -> getString(R.string.error_server)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.error_speech_timeout)
        else -> getString(R.string.error_unknown)
    }
}
