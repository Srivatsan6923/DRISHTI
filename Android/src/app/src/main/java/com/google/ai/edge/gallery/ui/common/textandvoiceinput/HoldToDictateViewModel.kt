package com.google.ai.edge.gallery.ui.common.textandvoiceinput

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AGHTD"

private const val AUDIO_METER_MIN_DB = -2.0f
private const val AUDIO_METER_MAX_DB = 100.0f

/** The UI state of the HoldToDictateViewModel. */
data class HoldToDictateUiState(val recognizing: Boolean = false, val recognizedText: String = "")

@HiltViewModel
class HoldToDictateViewModel @Inject constructor(@ApplicationContext private val context: Context) :
  ViewModel(), RecognitionListener {
  protected val _uiState = MutableStateFlow(HoldToDictateUiState())
  val uiState = _uiState.asStateFlow()

  private val speechRecognizer: SpeechRecognizer
  private val recognizerIntent: Intent
  private var onRecognitionDone: ((String) -> Unit)? = null
  private var onAmplitudeChanged: ((Int) -> Unit)? = null
  private var autoStopOnEndOfSpeech: Boolean = false
  private var onErrorCallback: ((Int) -> Unit)? = null

  init {
    // Initialize SpeechRecognizer
    speechRecognizer =
      SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(this@HoldToDictateViewModel)
      }

    // Initialize Intent (used for language/model settings)
    recognizerIntent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      }
  }

  fun startSpeechRecognition(
    onDone: (String) -> Unit, 
    onAmplitudeChanged: (Int) -> Unit,
    autoStop: Boolean = false,
    onError: ((Int) -> Unit)? = null
  ) {
    onRecognitionDone = onDone
    this.onAmplitudeChanged = onAmplitudeChanged
    this.autoStopOnEndOfSpeech = autoStop
    this.onErrorCallback = onError

    try {
      speechRecognizer.startListening(recognizerIntent)
      setRecognizedText(text = "")
      setRecognizing(recognizing = true)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start speech recognition", e)
      setRecognizing(recognizing = false)
      onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
    }
  }

  fun stopSpeechRecognition() {
    viewModelScope.launch {
      delay(500)
      speechRecognizer.stopListening()
      setRecognizing(recognizing = false)
    }
  }

  fun cancelSpeechRecognition() {
    setRecognizing(recognizing = false)
  }

  fun setRecognizing(recognizing: Boolean) {
    _uiState.update { uiState.value.copy(recognizing = recognizing) }
  }

  fun setRecognizedText(text: String) {
    _uiState.update { uiState.value.copy(recognizedText = text) }
  }

  override fun onReadyForSpeech(params: Bundle?) {}

  override fun onBeginningOfSpeech() {}

  override fun onRmsChanged(rmsdB: Float) {
    onAmplitudeChanged?.invoke(convertRmsDbToAmplitude(rmsdB = rmsdB))
  }

  override fun onBufferReceived(buffer: ByteArray?) {}

  override fun onEndOfSpeech() {
    // Automatically stop recognition if auto-stop is enabled
    if (autoStopOnEndOfSpeech) {
      viewModelScope.launch {
        delay(300) // Small delay to ensure final results are processed
        speechRecognizer.stopListening()
      }
    }
  }

  override fun onError(error: Int) {
    Log.e(TAG, "Speech recognition error: $error")
    setRecognizing(recognizing = false)
    onErrorCallback?.invoke(error)
    // Clear callbacks to prevent stale callbacks
    onErrorCallback = null
  }

  override fun onResults(results: Bundle?) {
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (matches != null && matches.size > 0) {
      setRecognizedText(matches.get(0) ?: "")
    } else {
      setRecognizedText("")
    }

    val curOnRecognitionDone = onRecognitionDone
    if (curOnRecognitionDone != null) {
      curOnRecognitionDone(uiState.value.recognizedText)
    }

    setRecognizing(recognizing = false)
  }

  override fun onPartialResults(partialResults: Bundle?) {
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (matches != null && matches.size > 0) {
      setRecognizedText(matches.get(0) ?: "")
    } else {
      setRecognizedText("")
    }
  }

  override fun onEvent(eventType: Int, params: Bundle?) {}
}

private fun convertRmsDbToAmplitude(rmsdB: Float): Int {
  // Clamp the input value to the defined range
  var clampedRmsdB = Math.max(rmsdB, AUDIO_METER_MIN_DB)
  clampedRmsdB = Math.min(clampedRmsdB, AUDIO_METER_MAX_DB)

  // Linear scaling to a 0-65535 range
  return ((clampedRmsdB - AUDIO_METER_MIN_DB) * 65535f / (AUDIO_METER_MAX_DB - AUDIO_METER_MIN_DB))
    .toInt()
}
