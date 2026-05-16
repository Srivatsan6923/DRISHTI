package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.*
import java.util.Locale
import java.util.UUID

/* =========================================================
   Production-grade Streaming Sentence TTS Manager
   ChatGPT-like behavior
   ========================================================= */

private const val TAG = "TtsManager"
private const val DEBOUNCE_DELAY_MS = 350L

/* =========================================================
   Composable creator
   ========================================================= */

@Composable
fun rememberTtsManager(context: Context): TtsManagerState {

    var enabled by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }

    var buffer by remember { mutableStateOf("") }

    val handler = remember { Handler(Looper.getMainLooper()) }
    var debounceRunnable by remember { mutableStateOf<Runnable?>(null) }

    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    /* =========================================================
       Sentence Streaming Logic (core)
       ========================================================= */

    fun speakNextSentence() {
        if (!enabled || !initialized || speaking) return

        val sentence = extractSentence(buffer) ?: return

        buffer = buffer.removePrefix(sentence)

        val id = UUID.randomUUID().toString()
        tts?.speak(sentence, TextToSpeech.QUEUE_ADD, null, id)
    }


    /* ================= INIT ================= */

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech

            val engine = tts ?: return@TextToSpeech

            engine.setLanguage(Locale.getDefault())
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.0f)

            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

                override fun onStart(id: String?) {
                    speaking = true
                }

                override fun onDone(id: String?) {
                    speaking = false
                    speakNextSentence()
                }

                override fun onError(id: String?) {
                    speaking = false
                }
            })

            initialized = true
            Log.d(TAG, "TTS ready")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }


    fun addText(text: String) {
        if (!enabled || !initialized) return

        if (text == "__STREAMING_COMPLETE__") {
            if (!speaking && buffer.isNotBlank()) {
                val id = UUID.randomUUID().toString()
                tts?.speak(buffer.trim(), TextToSpeech.QUEUE_ADD, null, id)
                buffer = ""
            }
            return
        }

        buffer += text

        /* immediate if full sentence */
        if (containsSentence(buffer)) {
            speakNextSentence()
            return
        }

        /* debounce for partial */
        debounceRunnable?.let { handler.removeCallbacks(it) }

        val r = Runnable {
            if (!speaking && buffer.isNotBlank()) {
                val id = UUID.randomUUID().toString()
                tts?.speak(buffer.trim(), TextToSpeech.QUEUE_ADD, null, id)
                buffer = ""
            }
        }

        debounceRunnable = r
        handler.postDelayed(r, DEBOUNCE_DELAY_MS)
    }

    fun stop() {
        buffer = ""
        tts?.stop()
    }

    return TtsManagerState(
        isEnabled = enabled,
        isInitialized = initialized,
        isSpeaking = speaking,
        setEnabled = { enabled = it },
        speakText = ::addText,
        stop = ::stop
    )
}

/* =========================================================
   State Holder
   ========================================================= */

data class TtsManagerState(
    val isEnabled: Boolean,
    val isInitialized: Boolean,
    val isSpeaking: Boolean,
    val setEnabled: (Boolean) -> Unit,
    val speakText: (String) -> Unit,
    val stop: () -> Unit
)

/* =========================================================
   Sentence helpers
   ========================================================= */

private val sentenceRegex = Regex("""[.!?]\s""")

private fun containsSentence(text: String): Boolean =
    sentenceRegex.containsMatchIn(text)

private fun extractSentence(text: String): String? {
    val match = sentenceRegex.find(text) ?: return null
    return text.substring(0, match.range.last + 1)
}
