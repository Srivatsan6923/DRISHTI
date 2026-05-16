
package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.common.GeminiApiService
import com.google.ai.edge.gallery.common.NetworkUtils
import com.google.ai.edge.gallery.common.cleanUpMediapipeTaskErrorMessage
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

private const val TAG = "AGLlmChatModelHelper"

typealias ResultListener = (partialResult: String, done: Boolean, modelType: String) -> Unit

typealias CleanUpListener = () -> Unit

data class LlmModelInstance(val engine: Engine, var conversation: Conversation)

object LlmChatModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

  @OptIn(ExperimentalApi::class) // opt-in experimental flags
  fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents? = null,
    tools: List<Any> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
  ) {
    // Prepare options.
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator =
      model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    Log.d(TAG, "Initializing...")
    val shouldEnableImage = supportImage
    val shouldEnableAudio = supportAudio
    Log.d(TAG, "Enable image: $shouldEnableImage, enable audio: $shouldEnableAudio")
    val preferredBackend =
      when (accelerator) {
        Accelerator.CPU.label -> Backend.CPU
        Accelerator.GPU.label -> Backend.GPU
        else -> Backend.CPU
      }
    Log.d(TAG, "Preferred backend: $preferredBackend")

    val modelPath = model.getPath(context = context)
    val engineConfig =
      EngineConfig(
        modelPath = modelPath,
        backend = preferredBackend,
        visionBackend = if (shouldEnableImage) Backend.GPU else null, // must be GPU for Gemma 3n
        audioBackend = if (shouldEnableAudio) Backend.CPU else null, // must be CPU for Gemma 3n
        maxNumTokens = maxTokens,
        cacheDir =
          if (modelPath.startsWith("/data/local/tmp"))
            context.getExternalFilesDir(null)?.absolutePath
          else null,
      )

    // Create an instance of LiteRT LM engine and conversation.
    try {
      val engine = Engine(engineConfig)
      engine.initialize()

      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      val conversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              SamplerConfig(
                topK = topK,
                topP = topP.toDouble(),
                temperature = temperature.toDouble(),
              ),
            systemInstruction = systemInstruction,
            tools = tools,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      model.instance = LlmModelInstance(engine = engine, conversation = conversation)
    } catch (e: Exception) {
      onDone(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error"))
      return
    }
    onDone("")
  }

  @OptIn(ExperimentalApi::class) // opt-in experimental flags
  fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents? = null,
    tools: List<Any> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: return
      instance.conversation.close()

      val engine = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
      val shouldEnableImage = supportImage
      val shouldEnableAudio = supportAudio
      Log.d(TAG, "Enable image: $shouldEnableImage, enable audio: $shouldEnableAudio")

      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      val newConversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              SamplerConfig(
                topK = topK,
                topP = topP.toDouble(),
                temperature = temperature.toDouble(),
              ),
            systemInstruction = systemInstruction,
            tools = tools,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      instance.conversation = newConversation

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset conversation", e)
    }
  }

  fun cleanUp(model: Model, onDone: () -> Unit) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmModelInstance

    try {
      instance.conversation.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the conversation: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  /**
   * Runs inference with Gemini API as primary and Gemma as fallback.
   * Tries Gemini API first if network is available (text and images), falls back to local Gemma model otherwise.
   * For audio, always uses Gemma.
   * 
   * @param forceGemma If true, forces use of Gemma model and skips Gemini API attempt.
   */
  fun runInference(
    context: Context,
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit = {},
    images: List<Bitmap> = listOf(),
    audioClips: List<ByteArray> = listOf(),
    geminiApiKey: String? = null,
    forceGemma: Boolean = false,
  ) {
    // Prepend this for functionality:
    val prefixedInput = """
You are a vision assistant observing the world through the user’s camera.

Rules:
- If the user asks to IDENTIFY, NAME, RECOGNIZE, or CONFIRM who a real person is respond ONLY with: <FUNC>
- else respond to the instruction.

User instruction:
$input
""".trimIndent()

    Log.d("DEBUG", "input: I AM SENDING NEW TEXT")

    // Check if network is available and API key is provided (not placeholder)
    val hasNetwork = NetworkUtils.isNetworkAvailable(context)
    val hasApiKey = !geminiApiKey.isNullOrEmpty() && 
                    geminiApiKey != "YOUR_GEMINI_API_KEY_HERE"
    
    Log.w(TAG, "hasNetwork: $hasNetwork, hasApiKey: $hasApiKey, forceGemma: $forceGemma")
    
    // Gemini API supports text and images - fallback to Gemma for audio
    val hasImages = images.isNotEmpty()
    val hasAudio = audioClips.isNotEmpty()
    
    // If forceGemma is true, skip Gemini API and use Gemma directly
    // Otherwise, try Gemini API first by default (for text and/or image requests)
    // Only fallback to Gemma if Gemini API fails, no network, no API key, or has audio
    if (!forceGemma && hasNetwork && hasApiKey && !hasAudio) {
      Log.d(TAG, "Attempting Gemini API by default (text/images request)")
      
      // Get model config values
      val temperature = model.getFloatConfigValue(
        key = ConfigKeys.TEMPERATURE,
        defaultValue = DEFAULT_TEMPERATURE
      )
      val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
      val maxTokens = model.getIntConfigValue(
        key = ConfigKeys.MAX_TOKENS,
        defaultValue = DEFAULT_MAX_TOKEN
      )

      // Try Gemini API first by default
      CoroutineScope(Dispatchers.IO).launch {
        val success = GeminiApiService.streamResponse(
          apiKey = geminiApiKey!!,
          textInput = prefixedInput,
          images = images,
          temperature = temperature,
          topK = topK,
          topP = topP,
          maxTokens = maxTokens,
          onChunk = { chunk ->
            Log.d(TAG, "Received chunk: '${chunk.take(50)}...' (length: ${chunk.length})")
            resultListener(chunk, false, "Gemini API")
          }
        )
        
        if (success) {
          // Gemini API succeeded
          resultListener("", true, "Gemini API")
          Log.d(TAG, "Gemini API request completed successfully")
        } else {
          // Gemini API failed, fallback to Gemma
          Log.d(TAG, "Gemini API failed, falling back to Gemma")
          // Ensure model instance is initialized before using Gemma
          while (model.instance == null) {
            kotlinx.coroutines.delay(100)
          }
          kotlinx.coroutines.delay(500)
          runGemmaInference(
            model = model,
            input = prefixedInput,
            resultListener = resultListener,
            cleanUpListener = cleanUpListener,
            onError = onError,
            images = images,
            audioClips = audioClips,
          )
        }
      }
    } else {
      // Force Gemma, no network, no API key, or has audio - use Gemma directly (fallback)
      if (forceGemma) {
        Log.d(TAG, "Force Gemma enabled, using Gemma model directly")
      } else if (!hasNetwork) {
        Log.d(TAG, "No network available, using Gemma fallback")
      } else if (!hasApiKey) {
        Log.d(TAG, "No API key provided, using Gemma fallback")
      } else if (hasAudio) {
        Log.d(TAG, "Audio detected, using Gemma fallback (Gemini API doesn't support audio)")
      }
      runGemmaInference(
        model = model,
        input = prefixedInput,
        resultListener = resultListener,
        cleanUpListener = cleanUpListener,
        onError = onError,
        images = images,
        audioClips = audioClips,
      )
    }
  }

  /**
   * Runs inference using the local Gemma model.
   */
  private fun runGemmaInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit = {},
    images: List<Bitmap> = listOf(),
    audioClips: List<ByteArray> = listOf(),
  ) {
    val instance = model.instance as LlmModelInstance

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    val conversation = instance.conversation

    val contents = mutableListOf<Content>()
    for (image in images) {
      contents.add(Content.ImageBytes(image.toPngByteArray()))
    }
    for (audioClip in audioClips) {
      contents.add(Content.AudioBytes(audioClip))
    }
    // add the text after image and audio for the accurate last token
    if (input.trim().isNotEmpty()) {
      contents.add(Content.Text(input))
    }

    conversation.sendMessageAsync(
      Contents.of(contents),
      object : MessageCallback {
        override fun onMessage(message: Message) {
          resultListener(message.toString(), false, "Gemma")
        }

        override fun onDone() {
          resultListener("", true, "Gemma")
        }

        override fun onError(throwable: Throwable) {
          if (throwable is CancellationException) {
            Log.i(TAG, "The inference is cancelled.")
            resultListener("", true, "Gemma")
          } else {
            Log.e(TAG, "onError", throwable)
            onError("Error: ${throwable.message}")
          }
        }
      },
    )
  }

  private fun Bitmap.toPngByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
  }
}
