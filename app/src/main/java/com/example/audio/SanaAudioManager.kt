package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.GeminiConfigManager
import com.example.data.GeminiModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class AudioState {
    IDLE,
    LISTENING,
    SPEAKING,
    THINKING,
    ERROR
}

data class VoiceDiagnosticInfo(
    val ttsInitialized: Boolean,
    val ttsEngine: String,
    val selectedModelSupportsAudio: Boolean,
    val activeVoiceName: String,
    val isMicAvailable: Boolean,
    val isSpeakerAvailable: Boolean
)

class SanaAudioManager(
    private val context: Context,
    private val configManager: GeminiConfigManager
) : TextToSpeech.OnInitListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var mediaPlayer: MediaPlayer? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _audioState = MutableStateFlow(AudioState.IDLE)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _lastVoiceError = MutableStateFlow<String?>(null)
    val lastVoiceError: StateFlow<String?> = _lastVoiceError.asStateFlow()

    // Callbacks
    var onSpeechRecognized: ((String) -> Unit)? = null
    var onSpeechPartial: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null
    var onSpeakingFinished: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            _lastVoiceError.value = "Failed to initialize Android TTS engine: ${e.message}"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            applyVoiceSettings()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _audioState.value = AudioState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    _audioState.value = AudioState.IDLE
                    _audioLevel.value = 0f
                    abandonAudioFocus()
                    onSpeakingFinished?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _audioState.value = AudioState.ERROR
                    _audioLevel.value = 0f
                    abandonAudioFocus()
                    _lastVoiceError.value = "TTS playback error for utterance $utteranceId"
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _audioState.value = AudioState.ERROR
                    _audioLevel.value = 0f
                    abandonAudioFocus()
                    _lastVoiceError.value = "TTS playback error (code $errorCode)"
                }
            })
        } else {
            isTtsReady = false
            _lastVoiceError.value = "TextToSpeech init returned status: $status"
        }
    }

    fun applyVoiceSettings() {
        tts?.let { engine ->
            val langCode = configManager.getLanguageCode()
            val locale = when (langCode) {
                "ms-MY" -> Locale("ms", "MY")
                "ur-PK" -> Locale("ur", "PK")
                "hi-IN" -> Locale("hi", "IN")
                else -> Locale.US
            }

            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }

            // SANA target feminine, cute, natural acoustic parameters
            engine.setPitch(configManager.getSpeechPitch())
            engine.setSpeechRate(configManager.getSpeechRate())
        }
    }

    /**
     * Plays raw audio bytes synthesized by Gemini (WAV, MP3, or PCM container).
     */
    fun playGeminiAudio(audioBytes: ByteArray, onComplete: () -> Unit = {}): Boolean {
        stopPlayback()
        requestAudioFocus()

        try {
            // Write to temporary cache file
            val tempFile = File.createTempFile("sana_gemini_audio_", ".tmp", context.cacheDir)
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnPreparedListener { mp ->
                    _audioState.value = AudioState.SPEAKING
                    _lastVoiceError.value = null
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    tempFile.delete()
                    _audioState.value = AudioState.IDLE
                    _audioLevel.value = 0f
                    abandonAudioFocus()
                    onComplete()
                    onSpeakingFinished?.invoke()
                }
                setOnErrorListener { mp, what, extra ->
                    mp.release()
                    mediaPlayer = null
                    tempFile.delete()
                    _audioState.value = AudioState.ERROR
                    abandonAudioFocus()
                    _lastVoiceError.value = "MediaPlayer error: code=$what, extra=$extra"
                    false
                }
                prepareAsync()
            }
            return true
        } catch (e: Exception) {
            _lastVoiceError.value = "Failed to play Gemini audio: ${e.message}"
            abandonAudioFocus()
            return false
        }
    }

    /**
     * Speaks text using local Android TextToSpeech engine configured with SANA's cute pitch and voice parameters.
     */
    fun speakText(text: String) {
        stopPlayback()
        if (_isMuted.value) return

        if (!isTtsReady || tts == null) {
            _lastVoiceError.value = "TTS engine is not ready. Checking Android speech synthesis service..."
            initTts()
            return
        }

        requestAudioFocus()
        applyVoiceSettings()
        _audioState.value = AudioState.SPEAKING
        _lastVoiceError.value = null

        val utteranceId = "sana_utterance_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Instant interruption: stops any active audio immediately.
     */
    fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (ignored: Exception) {}

        try {
            tts?.stop()
        } catch (ignored: Exception) {}

        if (_audioState.value == AudioState.SPEAKING) {
            _audioState.value = AudioState.IDLE
        }
        _audioLevel.value = 0f
        abandonAudioFocus()
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        if (newMuted) {
            stopPlayback()
        }
    }

    fun startListening() {
        stopPlayback()
        if (_isMuted.value) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _lastVoiceError.value = "Speech recognition is not available on this device."
            onSpeechError?.invoke("Speech recognition is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _audioState.value = AudioState.LISTENING
                        _lastVoiceError.value = null
                    }

                    override fun onBeginningOfSpeech() {
                        _audioState.value = AudioState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize -2dB..10dB to 0..1f for UI waveform
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                        _audioLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _audioState.value = AudioState.THINKING
                        _audioLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _audioState.value = AudioState.IDLE
                        _audioLevel.value = 0f
                        val errMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client speech recognizer error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout"
                            else -> "Speech recognition error code: $error"
                        }
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            _lastVoiceError.value = errMsg
                        }
                        onSpeechError?.invoke(errMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        _audioState.value = AudioState.IDLE
                        _audioLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            onSpeechRecognized?.invoke(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            onSpeechPartial?.invoke(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val lang = configManager.getLanguageCode()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _audioState.value = AudioState.ERROR
            _lastVoiceError.value = "Failed to start microphone: ${e.message}"
            onSpeechError?.invoke("Failed to start microphone: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (ignored: Exception) {}
        if (_audioState.value == AudioState.LISTENING) {
            _audioState.value = AudioState.IDLE
        }
        _audioLevel.value = 0f
    }

    fun getDiagnostics(): VoiceDiagnosticInfo {
        val selectedModel = configManager.getSelectedModel()
        val supportsAudio = selectedModel == GeminiModels.MODEL_TTS ||
                selectedModel == GeminiModels.MODEL_REALTIME_AUDIO

        val isMicAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val isSpeakerAvailable = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0

        return VoiceDiagnosticInfo(
            ttsInitialized = isTtsReady,
            ttsEngine = tts?.defaultEngine ?: "Default Android Engine",
            selectedModelSupportsAudio = supportsAudio,
            activeVoiceName = configManager.getSelectedVoice(),
            isMicAvailable = isMicAvailable,
            isSpeakerAvailable = isSpeakerAvailable
        )
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun release() {
        stopPlayback()
        stopListening()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (ignored: Exception) {}
        try {
            tts?.shutdown()
            tts = null
        } catch (ignored: Exception) {}
    }
}
