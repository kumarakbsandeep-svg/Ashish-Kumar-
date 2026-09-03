package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

class ZoyaAudioEngine(
  private val context: Context,
  private val onSpeechRecognized: (String) -> Unit,
  private val onSpeechDone: () -> Unit
) : TextToSpeech.OnInitListener {

  companion object {
    private const val TAG = "ZoyaAudioEngine"
    private const val UTTERANCE_ID = "ZOYA_SPEECH"
  }

  private var textToSpeech: TextToSpeech? = null
  private var isTtsReady = false

  private var speechRecognizer: SpeechRecognizer? = null
  private var isListening = false

  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private var amplitudeAnimationJob: Job? = null

  private val _audioAmplitude = MutableStateFlow(0f)
  val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _isMicActive = MutableStateFlow(false)
  val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

  init {
    textToSpeech = TextToSpeech(context, this)
    setupSpeechRecognizer()
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      textToSpeech?.let { tts ->
        val result = tts.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          Log.w(TAG, "Locale US not supported in TTS, trying default")
          tts.language = Locale.getDefault()
        }
        // Personality tuning for Zoya: bright, expressive, youthful
        tts.setPitch(1.16f)
        tts.setSpeechRate(1.05f)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            _isSpeaking.value = true
            startSpeakingAmplitudeSimulation()
          }

          override fun onDone(utteranceId: String?) {
            _isSpeaking.value = false
            stopAmplitudeSimulation()
            scope.launch {
              onSpeechDone()
            }
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            _isSpeaking.value = false
            stopAmplitudeSimulation()
          }
        })
        isTtsReady = true
      }
    } else {
      Log.e(TAG, "TextToSpeech init failed with status: $status")
    }
  }

  private fun setupSpeechRecognizer() {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      Log.w(TAG, "Speech recognition is not available on this device")
      return
    }

    try {
      speechRecognizer?.destroy()
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            _isMicActive.value = true
          }

          override fun onBeginningOfSpeech() {
            _isMicActive.value = true
          }

          override fun onRmsChanged(rmsdB: Float) {
            // Normalize rmsdB (-2 to 10 typical) to 0f..1f for visualizer
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            _audioAmplitude.value = normalized
          }

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            _isMicActive.value = false
            _audioAmplitude.value = 0f
          }

          override fun onError(error: Int) {
            _isMicActive.value = false
            isListening = false
            _audioAmplitude.value = 0f
            Log.d(TAG, "SpeechRecognizer error: $error")
          }

          override fun onResults(results: Bundle?) {
            _isMicActive.value = false
            isListening = false
            _audioAmplitude.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()
            if (!recognizedText.isNullOrBlank()) {
              onSpeechRecognized(recognizedText)
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim()
            if (!partial.isNullOrBlank()) {
              _audioAmplitude.value = 0.6f
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) {}
        })
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error setting up SpeechRecognizer", e)
    }
  }

  fun startListening() {
    // Interruption check: If currently speaking, cut off immediately
    interruptSpeaking()

    if (speechRecognizer == null) {
      setupSpeechRecognizer()
    }

    try {
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
          RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
      }
      speechRecognizer?.startListening(intent)
      isListening = true
      _isMicActive.value = true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start listening", e)
      isListening = false
      _isMicActive.value = false
    }
  }

  fun stopListening() {
    try {
      speechRecognizer?.stopListening()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping listening", e)
    }
    isListening = false
    _isMicActive.value = false
    _audioAmplitude.value = 0f
  }

  fun speak(text: String) {
    if (!isTtsReady || textToSpeech == null) {
      Log.w(TAG, "TTS not ready to speak")
      return
    }

    // Stop previous speech if any
    interruptSpeaking()

    val params = Bundle().apply {
      putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
    }
    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
  }

  /**
   * Immediate interruption: Stops audio playback and resets speech state immediately.
   */
  fun interruptSpeaking() {
    textToSpeech?.stop()
    _isSpeaking.value = false
    stopAmplitudeSimulation()
  }

  private fun startSpeakingAmplitudeSimulation() {
    stopAmplitudeSimulation()
    amplitudeAnimationJob = scope.launch {
      var step = 0f
      while (isActive && _isSpeaking.value) {
        step += 0.25f
        // Dynamic rhythmic waveform reflecting voice cadence
        val wave = ((sin(step.toDouble()) * 0.45) + (sin(step * 2.3) * 0.25) + 0.55).toFloat()
        _audioAmplitude.value = wave.coerceIn(0.15f, 1f)
        delay(40)
      }
      _audioAmplitude.value = 0f
    }
  }

  private fun stopAmplitudeSimulation() {
    amplitudeAnimationJob?.cancel()
    amplitudeAnimationJob = null
    _audioAmplitude.value = 0f
  }

  fun release() {
    interruptSpeaking()
    stopListening()
    try {
      speechRecognizer?.destroy()
    } catch (e: Exception) {
      Log.e(TAG, "Error destroying recognizer", e)
    }
    speechRecognizer = null

    try {
      textToSpeech?.shutdown()
    } catch (e: Exception) {
      Log.e(TAG, "Error shutting down TTS", e)
    }
    textToSpeech = null
  }
}
