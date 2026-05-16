package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageBenchmarkLlmResult
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageError
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageLoading
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageType
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageWarning
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatViewModel
import com.google.ai.edge.gallery.ui.common.chat.Stat
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.ExperimentalApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGLlmChatViewModel"
private val STATS =
  listOf(
    Stat(id = "time_to_first_token", label = "1st token", unit = "sec"),
    Stat(id = "prefill_speed", label = "Prefill speed", unit = "tokens/s"),
    Stat(id = "decode_speed", label = "Decode speed", unit = "tokens/s"),
    Stat(id = "latency", label = "Latency", unit = "sec"),
  )

open class LlmChatViewModelBase() : ChatViewModel() {
  fun generateResponse(
    context: Context,
    model: Model,
    input: String,
    images: List<Bitmap> = listOf(),
    audioMessages: List<ChatMessageAudioClip> = listOf(),
    onError: (String) -> Unit,
    onStreamingText: ((String) -> Unit)? = null,
    modelManagerViewModel: ModelManagerViewModel? = null,
  ) {
    val accelerator = model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = "")
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      setPreparing(true)

      // Loading.
      addMessage(model = model, message = ChatMessageLoading(accelerator = accelerator))

      // Get Gemini API key from BuildConfig
      val geminiApiKey = com.google.ai.edge.gallery.BuildConfig.GEMINI_API_KEY
      
      // Get forceGemma preference from DataStore
      val dataStoreRepository = (context.applicationContext as com.google.ai.edge.gallery.GalleryApplication).dataStoreRepository
      val forceGemma = dataStoreRepository.readForceGemma()
      
      // Check if we'll try Gemini API first (has network and valid API key - not placeholder, and not forcing Gemma)
      val willTryGeminiApi = !forceGemma &&
                             com.google.ai.edge.gallery.common.NetworkUtils.isNetworkAvailable(context) && 
                             geminiApiKey.isNotEmpty() && 
                             geminiApiKey != "YOUR_GEMINI_API_KEY_HERE" &&
                             images.isEmpty() &&
                             audioMessages.isEmpty()
      
      // Only wait for Gemma instance initialization if we won't try Gemini API first
      // (i.e., if we have images/audio, no network, no API key, or forcing Gemma)
      if (!willTryGeminiApi) {
        while (model.instance == null) {
          delay(100)
        }
        delay(500)
      }
      
      // Run inference - will try Gemini API by default, fallback to Gemma if needed
      val instance = model.instance as? LlmModelInstance
      var prefillTokens = images.size * 257
      val audioClips: MutableList<ByteArray> = mutableListOf()
      for (audioMessage in audioMessages) {
        audioClips.add(audioMessage.genByteArrayForWav())
        // 150ms = 1 audio token
        val duration = audioMessage.getDurationInSeconds()
        prefillTokens += (duration * 1000f / 150f).toInt()
      }

      var firstRun = true
      var timeToFirstToken = 0f
      var firstTokenTs = 0L
      var decodeTokens = 0
      var prefillSpeed = 0f
      var decodeSpeed: Float
      var currentModelType = if (willTryGeminiApi) "Gemini API" else "Gemma" // Default based on what we'll try first
      val start = System.currentTimeMillis()

      try {
        LlmChatModelHelper.runInference(
          context = context,
          model = model,
          input = input,
          images = images,
          audioClips = audioClips,
          geminiApiKey = geminiApiKey.ifEmpty { null },
          forceGemma = forceGemma,
          resultListener = { partialResult, done, modelType ->
            currentModelType = modelType
            val curTs = System.currentTimeMillis()

            if (firstRun) {
              firstTokenTs = System.currentTimeMillis()
              timeToFirstToken = (firstTokenTs - start) / 1000f
              // Only get benchmark info if using local Gemma model (not Gemini API)
              if (modelType == "Gemma" && instance != null) {
                @OptIn(ExperimentalApi::class)
                try {
                  prefillTokens += instance.conversation.getBenchmarkInfo().lastPrefillTokenCount
                } catch (e: Exception) {
                  Log.d(TAG, "Could not get benchmark info: ${e.message}")
                }
              }
              prefillSpeed = prefillTokens / timeToFirstToken
              firstRun = false
              setPreparing(false)
            } else {
              decodeTokens++
            }

            // Remove the last message if it is a "loading" message.
            // This will only be done once.
            val lastMessage = getLastMessage(model = model)
            if (lastMessage?.type == ChatMessageType.LOADING) {
              removeLastMessage(model = model)

              // Add an empty message that will receive streaming results.
              addMessage(
                model = model,
                message =
                  ChatMessageText(
                    content = "",
                    side = ChatSide.AGENT,
                    accelerator = accelerator,
                    modelType = currentModelType,
                  ),
              )
            }

            // Incrementally update the streamed partial results.
            val latencyMs: Long = if (done) System.currentTimeMillis() - start else -1
            updateLastTextMessageContentIncrementally(
              model = model,
              partialContent = partialResult,
              latencyMs = latencyMs.toFloat(),
              modelType = currentModelType,
            )
            
            // Call TTS callback for streaming text
            if (partialResult.isNotEmpty()) {
              Log.d(TAG, "🔊 Calling onStreamingText with: '$partialResult'")
              onStreamingText?.invoke(partialResult)
            } else {
              Log.d(TAG, "🔊 Skipping onStreamingText - empty partial result")
            }

            if (done) {
              setInProgress(false)
              
              // Signal TTS that streaming is complete
              Log.d(TAG, "🔊 Streaming complete, signaling TTS")
              onStreamingText?.invoke("__STREAMING_COMPLETE__")

              decodeSpeed = decodeTokens / ((curTs - firstTokenTs) / 1000f)
              if (decodeSpeed.isNaN()) {
                decodeSpeed = 0f
              }

              if (lastMessage is ChatMessageText) {
                updateLastTextMessageLlmBenchmarkResult(
                  model = model,
                  llmBenchmarkResult =
                    ChatMessageBenchmarkLlmResult(
                      orderedStats = STATS,
                      statValues =
                        mutableMapOf(
                          "prefill_speed" to prefillSpeed,
                          "decode_speed" to decodeSpeed,
                          "time_to_first_token" to timeToFirstToken,
                          "latency" to (curTs - start).toFloat() / 1000f,
                        ),
                      running = false,
                      latencyMs = -1f,
                      accelerator = accelerator,
                    ),
                )
              }
            }
          },
          cleanUpListener = {
            setInProgress(false)
            setPreparing(false)
          },
          onError = { message ->
            Log.e(TAG, "Error occurred while running inference")
            setInProgress(false)
            setPreparing(false)
            onError(message)
          },
        )
      } catch (e: Exception) {
        Log.e(TAG, "Error occurred while running inference", e)
        setInProgress(false)
        setPreparing(false)
        onError(e.message ?: "")
      }
    }
  }

  fun stopResponse(model: Model) {
    Log.d(TAG, "Stopping response for model ${model.name}...")
    if (getLastMessage(model = model) is ChatMessageLoading) {
      removeLastMessage(model = model)
    }
    setInProgress(false)
    val instance = model.instance as LlmModelInstance
    instance.conversation.cancelProcess()
    Log.d(TAG, "Done stopping response")
  }

  fun resetSession(task: Task, model: Model) {
    viewModelScope.launch(Dispatchers.Default) {
      setIsResettingSession(true)
      clearAllMessages(model = model)
      stopResponse(model = model)

      while (true) {
        try {
          val supportImage =
            model.llmSupportImage &&
              task.id == com.google.ai.edge.gallery.data.BuiltInTaskId.LLM_ASK_IMAGE
          val supportAudio =
            model.llmSupportAudio &&
              task.id == com.google.ai.edge.gallery.data.BuiltInTaskId.LLM_ASK_AUDIO
          LlmChatModelHelper.resetConversation(
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
          )
          break
        } catch (e: Exception) {
          Log.d(TAG, "Failed to reset session. Trying again")
        }
        delay(200)
      }
      setIsResettingSession(false)
    }
  }

  fun runAgain(
    context: Context,
    model: Model,
    message: ChatMessageText,
    onError: (String) -> Unit,
    onStreamingText: ((String) -> Unit)? = null,
    modelManagerViewModel: ModelManagerViewModel? = null,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      // Wait for model to be initialized.
      while (model.instance == null) {
        delay(100)
      }

      // Clone the clicked message and add it.
      addMessage(model = model, message = message.clone())

      // Run inference.
      generateResponse(
        context = context,
        model = model,
        input = message.content,
        onError = onError,
        onStreamingText = onStreamingText,
        modelManagerViewModel = modelManagerViewModel,
      )
    }
  }

  fun handleError(
    context: Context,
    task: Task,
    model: Model,
    modelManagerViewModel: ModelManagerViewModel,
    errorMessage: String,
  ) {
    // Remove the "loading" message.
    if (getLastMessage(model = model) is ChatMessageLoading) {
      removeLastMessage(model = model)
    }

    // Show error message.
    addMessage(model = model, message = ChatMessageError(content = errorMessage))

    // Clean up and re-initialize.
    viewModelScope.launch(Dispatchers.Default) {
      modelManagerViewModel.cleanupModel(
        context = context,
        task = task,
        model = model,
        onDone = {
          modelManagerViewModel.initializeModel(context = context, task = task, model = model)

          // Add a warning message for re-initializing the session.
          addMessage(
            model = model,
            message = ChatMessageWarning(content = "Session re-initialized"),
          )
        },
      )
    }
  }
}

@HiltViewModel class LlmAskImageViewModel @Inject constructor() : LlmChatViewModelBase()
