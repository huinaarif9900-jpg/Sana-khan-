package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface WakeWordEngine {
    val engineName: String
    fun isAvailable(): Boolean
    fun startListening(onWakeWordDetected: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
    fun destroy()
}

/**
 * Replaceable WakeWordEngine implementation using Android SpeechRecognizer keyword spotting.
 */
class SpeechRecognizerWakeWordEngine(private val context: Context) : WakeWordEngine {

    override val engineName: String = "Android Keyword Spotter (SANA / Hey SANA)"
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var callback: ((String) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening(onWakeWordDetected: (String) -> Unit, onError: (String) -> Unit) {
        if (!isAvailable()) {
            onError("Wake-word engine is not available on this device.")
            return
        }

        callback = onWakeWordDetected
        errorCallback = onError
        isListening = true
        launchRecognizer()
    }

    private fun launchRecognizer() {
        if (!isListening) return

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        // Restart listener for continuous keyword spotting unless cancelled
                        if (isListening) {
                            try {
                                launchRecognizer()
                            } catch (e: Exception) {
                                errorCallback?.invoke("Wake word restart failed: ${e.message}")
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.joinToString(" ")?.lowercase().orEmpty()

                        if (text.contains("sana") || text.contains("hey sana") || text.contains("hi sana")) {
                            callback?.invoke(text)
                        } else if (isListening) {
                            launchRecognizer()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.joinToString(" ")?.lowercase().orEmpty()

                        if (text.contains("sana") || text.contains("hey sana") || text.contains("hi sana")) {
                            callback?.invoke(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            errorCallback?.invoke("Failed to start wake word listener: ${e.message}")
        }
    }

    override fun stopListening() {
        isListening = false
        try {
            recognizer?.stopListening()
        } catch (ignored: Exception) {}
    }

    override fun destroy() {
        stopListening()
        try {
            recognizer?.destroy()
            recognizer = null
        } catch (ignored: Exception) {}
    }
}
