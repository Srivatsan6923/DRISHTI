
package com.google.ai.edge.gallery.ui.llmchat
import kotlin.math.sqrt
import ArcFace
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MapsUgc
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VoiceOverOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.GalleryApplication
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatView
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class StreamMode {
  UNDECIDED,
  FUNC,
  NORMAL
}

private var streamMode = StreamMode.UNDECIDED
private val streamBuffer = StringBuilder()

@Composable
fun ChatViewWrapper(
  viewModel: LlmChatViewModelBase,
  modelManagerViewModel: ModelManagerViewModel,
  taskId: String,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val task = modelManagerViewModel.getTaskById(id = taskId)
  val arcFace = ArcFace(context)

  val embeddings = mutableListOf<FloatArray>()


  // Get DataStoreRepository from Application
  val dataStoreRepository = remember {
    (context.applicationContext as GalleryApplication).dataStoreRepository
  }
  
  // TTS Setup
  val ttsManager = rememberTtsManager(context)

  // Slow Alarm Setup (for stopping alarms that may have been triggered from Fall Detection screen)
  val slowAlarmManager = rememberSlowAlarmManager(context, dataStoreRepository)
  
  // Return early if task is not available yet
  if (task == null) {
    return
  }

  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val uiState by viewModel.uiState.collectAsState()
  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  val isModelInitialized = modelInitializationStatus?.status == com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED
  val isModelInitializing = modelInitializationStatus?.status == com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZING
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  val downloadSucceeded = curDownloadStatus?.status == com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED
  val showConfigButton = selectedModel.configs.isNotEmpty() && downloadSucceeded
  val enableConfigButton = !isModelInitializing && !uiState.inProgress && isModelInitialized
  val enableResetButton = !isModelInitializing && !uiState.preparing && !uiState.inProgress && isModelInitialized
  val options = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .setMinFaceSize(0.05f)  // smaller faces
    .build()

//  val personEmbeddings = mutableMapOf<String, FloatArray>()
  val detector = FaceDetection.getClient(options)
  fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
    val x = boundingBox.left.coerceAtLeast(0)
    val y = boundingBox.top.coerceAtLeast(0)

    val width = boundingBox.width().coerceAtMost(bitmap.width - x)
    val height = boundingBox.height().coerceAtMost(bitmap.height - y)
//    Log.d("FaceDetection", "NEW CROPPED $width - $height")
    return Bitmap.createBitmap(bitmap, x, y, width, height)
  }


  Log.d("FaceDetection", "hi");
  val assetManager = context.assets
  val membersFolder = "members"

// List all people
  val people = assetManager.list(membersFolder) ?: arrayOf()

  val personEmbeddings = remember { mutableStateMapOf<String, FloatArray>() }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.Default) {
      val assetManager = context.assets
      val membersFolder = "members"
      val people = assetManager.list(membersFolder) ?: return@withContext

      for (personName in people) {
        val personFolder = "$membersFolder/$personName"
        val imageFiles = assetManager.list(personFolder) ?: continue
        val embeddingsList = mutableListOf<FloatArray>()

        for (fileName in imageFiles) {
          val bitmap = assetManager.open("$personFolder/$fileName").use {
            BitmapFactory.decodeStream(it)
          }

          val image = InputImage.fromBitmap(bitmap, 0)

          try {
            val faces = Tasks.await(detector.process(image))
            if (faces.isEmpty()) continue

            val face = faces.first()
            val croppedFace = cropFace(bitmap, face.boundingBox)
            val embedding = arcFace.getEmbedding(croppedFace)
            embeddingsList.add(embedding)

          } catch (e: Exception) {
            Log.e("FaceDetection", "Failed on $fileName", e)
          }
        }

        if (embeddingsList.isNotEmpty()) {
          val avg = FloatArray(embeddingsList.first().size)
          embeddingsList.forEach { emb ->
            for (i in emb.indices) avg[i] += emb[i]
          }
          for (i in avg.indices) avg[i] /= embeddingsList.size

          personEmbeddings[personName] = avg
          Log.d("FaceDetection", "Stored avg embedding for $personName")
        }
      }

      Log.d("FaceDetection", "FINAL personEmbeddings = $personEmbeddings")
    }
  }



  fun dist(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
      dot += a[i] * b[i]
      normA += a[i] * a[i]
      normB += b[i] * b[i]
    }
    return dot / (sqrt(normA) * sqrt(normB))

    // euclid:
//    require(a.size == b.size) { "Vectors must have the same length" }
//    var sum = 0f
//    for (i in a.indices) {
//      val diff = a[i] - b[i]
//      sum += diff * diff
//    }
//    Log.d("FaceDetection", sum.toString())
//    return sqrt(sum)

  }




  // Use Box to overlay fall detection UI on top of ChatView, positioned below header
  Box(modifier = modifier) {
    // Chat view fills the entire space
    ChatView(
      task = task,
      viewModel = viewModel,
      modelManagerViewModel = modelManagerViewModel,
      showTopBar = false, // Hide top bar for ANVI app
      onSendMessage = { model, messages ->
        for (message in messages) {
          viewModel.addMessage(model = model, message = message)
        }

        var text = ""
        val images: MutableList<Bitmap> = mutableListOf()
        val audioMessages: MutableList<ChatMessageAudioClip> = mutableListOf()
        var chatMessageText: ChatMessageText? = null
        for (message in messages) {
          if (message is ChatMessageText) {
            chatMessageText = message
            text = message.content
          } else if (message is ChatMessageImage) {
            images.addAll(message.bitmaps)
          } else if (message is ChatMessageAudioClip) {
            audioMessages.add(message)
          }
        }
        if ((text.isNotEmpty() && chatMessageText != null) || audioMessages.isNotEmpty()) {
          modelManagerViewModel.addTextInputHistory(text)
          
          // Reset TTS state when starting new generation
          ttsManager.stop()

          // Prepend this for functionality:
          val prefixedInput = """
You are a vision assistant observing the world through the user’s camera.

Rules:
- If the user asks to IDENTIFY, NAME, RECOGNIZE, or CONFIRM who a real person is respond ONLY with: <FUNC>
- else respond to the instruction.

User instruction:
$text
""".trimIndent()

          Log.d("DEBUG", "I AM SENDING NEW TEXT")

          viewModel.generateResponse(
            context = context,
            model = model,
            input = prefixedInput,
            images = images,
            audioMessages = audioMessages,
            onError = { errorMessage ->
              viewModel.handleError(
                context = context,
                task = task,
                model = model,
                errorMessage = errorMessage,
                modelManagerViewModel = modelManagerViewModel,
              )
            },
            onStreamingText = { chunk ->
              Log.d("DEBUG", "CHUNK IS $chunk")

              when (streamMode) {
                StreamMode.UNDECIDED -> {
                  streamBuffer.append(chunk)
                  val current = streamBuffer.toString().trim()

                  when {
                    // Full FUNC detected (even if split)
                    current.contains("<FUNC>") -> {
                      streamMode = StreamMode.FUNC
                      streamBuffer.clear()

                      ttsManager.stop() // stop TTS if it started
                      Log.d("DEBUG", "I AM GONNA DO FUNC")
//                      handleFuncCommand()


                      // ---------- Data class ----------
                      data class MatchScore(
                        val faceIndex: Int,
                        val personName: String,
                        val score: Float
                      )

// ---------- Inside <FUNC> block ----------
                      val allCroppedFaces = mutableListOf<Bitmap>()
                      val allEmbeddings = mutableListOf<FloatArray>()
                      val allScores = mutableListOf<MatchScore>()
                      var pendingImages = images.size

                      images.forEach { bitmap ->

                        Log.d(
                          "FaceDetection",
                          "Original image size: width=${bitmap.width}, height=${bitmap.height}"
                        )

                        val image = InputImage.fromBitmap(bitmap, 0)

                        detector.process(image)
                          .addOnSuccessListener { faces ->

                            if (faces.isEmpty()) {
                              Log.d("FaceDetection", "No faces detected")
                              return@addOnSuccessListener
                            }

                            faces.forEachIndexed { index, face ->

                              val croppedFace = cropFace(bitmap, face.boundingBox)

                              Log.d(
                                "FaceDetection",
                                "Face #$index size: ${croppedFace.width} x ${croppedFace.height}"
                              )

                              allCroppedFaces.add(croppedFace)

                              val emb = arcFace.getEmbedding(croppedFace)
                              allEmbeddings.add(emb)

                              Log.d(
                                "FaceDetection",
                                "Embedding size=${emb.size}, first5=${emb.take(5)}"
                              )

                              // Collect all distances
                              personEmbeddings.forEach { (personName, avgEmbedding) ->
                                val score = dist(emb, avgEmbedding)

                                allScores.add(
                                  MatchScore(
                                    faceIndex = index,
                                    personName = personName,
                                    score = score
                                  )
                                )

                                Log.d(
                                  "FaceDetection",
                                  "Face $index ↔ $personName = $score"
                                )
                              }
                            }
                          }
                          .addOnSuccessListener { faces ->
                            // process faces here

                            pendingImages--
                            if (pendingImages == 0) {

                                Log.d("SYNC", "All images processed — start matching")
                                val remainingFaces = allEmbeddings.indices.toMutableSet()
                                val remainingPeople = personEmbeddings.keys.toMutableSet()

                                val finalAssignments = mutableMapOf<Int, String>()

// Sort by best score (LOWEST distance first)
                                val sortedScores = allScores.sortedBy { it.score }

                                for (match in sortedScores) {

                                  if (
                                    match.faceIndex in remainingFaces &&
                                    match.personName in remainingPeople
                                  ) {
                                    finalAssignments[match.faceIndex] = match.personName

                                    remainingFaces.remove(match.faceIndex)
                                    remainingPeople.remove(match.personName)

                                    Log.d(
                                      "FaceMatch",
                                      "Assigned Face ${match.faceIndex} → ${match.personName} (score=${match.score})"
                                    )
                                    ttsManager.speakText(match.personName + "and") //TODO
                                    if (remainingFaces.isEmpty() || remainingPeople.isEmpty()) break
                                  }
                                }

                                // 👉 DO GLOBAL MATCHING HERE


                            }
                          }
                          .addOnFailureListener { e ->
                            e.printStackTrace()
                          }
                      }



                    }

                    // Definitely not FUNC → normal TTS
                    !"<FUNC>".startsWith(current) -> {
                      streamMode = StreamMode.NORMAL
                      Log.d("DEBUG", "NORMAL TTS")
                      ttsManager.speakText(streamBuffer.toString())
                      streamBuffer.clear()
                    }

                    // else → still undecided, wait for next chunk
                  }
                }

                StreamMode.NORMAL -> {
                  Log.d("DEBUG", "FALLBACK")
                  ttsManager.speakText(chunk)
                }

                StreamMode.FUNC -> {
                  // ignore all chunks after FUNC
                }
              }

              // Reset state if streaming is complete
              if (chunk == "__STREAMING_COMPLETE__") {
                Log.d("DEBUG", "RESETTING STREAM STATE")
                streamMode = StreamMode.UNDECIDED
                streamBuffer.clear()
              }
            }
,
                    modelManagerViewModel = modelManagerViewModel,
          )
        }
      },
      onRunAgainClicked = { model, message ->
        if (message is ChatMessageText) {
          viewModel.runAgain(
            context = context,
            model = model,
            message = message,
            onError = { errorMessage ->
              viewModel.handleError(
                context = context,
                task = task,
                model = model,
                errorMessage = errorMessage,
                modelManagerViewModel = modelManagerViewModel,
              )
            },
            onStreamingText = { streamingTextFromCallback ->
              ttsManager.speakText(streamingTextFromCallback)
            },
            modelManagerViewModel = modelManagerViewModel,
          )
        }
      },
      onBenchmarkClicked = { _, _, _, _ -> },
      onResetSessionClicked = { model -> viewModel.resetSession(task = task, model = model) },
      showStopButtonInInputWhenInProgress = true,
      onStopButtonClicked = { model -> viewModel.stopResponse(model = model) },
      navigateUp = navigateUp,
      modifier = Modifier.fillMaxSize(),
    )
    
    // Floating Top Bar with Settings and New Chat buttons
    var showConfigDialog by remember { mutableStateOf(false) }
    var showChatSettingsDialog by remember { mutableStateOf(false) }
    
    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 16.dp, end = 16.dp)
        .shadow(
          elevation = 8.dp,
          shape = RoundedCornerShape(28.dp),
          spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        .clip(RoundedCornerShape(28.dp))
        .background(
          MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
          shape = RoundedCornerShape(28.dp)
        )
        .padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Chat Settings Button (Gemini/Gemma toggle)
      IconButton(
        onClick = { showChatSettingsDialog = true },
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
      ) {
        Icon(
          imageVector = Icons.Rounded.Info,
          contentDescription = "Chat Settings",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp)
        )
      }
      
      // Chat Settings Dialog
      if (showChatSettingsDialog) {
        ChatSettingsDialog(
          dataStoreRepository = dataStoreRepository,
          onDismiss = { showChatSettingsDialog = false }
        )
      }
      
      // Model Config Button
      if (showConfigButton) {
        IconButton(
          onClick = { showConfigDialog = true },
          enabled = enableConfigButton,
          modifier = Modifier
            .size(40.dp)
            .alpha(if (!enableConfigButton) 0.5f else 1f),
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
          )
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
        }
        
        // Config Dialog
        if (showConfigDialog) {
          com.google.ai.edge.gallery.ui.common.ConfigDialog(
            title = "Model configs",
            configs = selectedModel.configs,
            initialValues = selectedModel.configValues,
            onDismissed = { showConfigDialog = false },
            onOk = { curConfigValues ->
              showConfigDialog = false
              // Handle config changes similar to ModelPageAppBar
              val oldConfigValues = selectedModel.configValues
              selectedModel.prevConfigValues = oldConfigValues
              selectedModel.configValues = curConfigValues
              modelManagerViewModel.updateConfigValuesUpdateTrigger()
            }
          )
        }
      }
      
      // New Chat Button
      if (uiState.isResettingSession) {
        Box(
          modifier = Modifier.size(40.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp)
          )
        }
      } else {
        IconButton(
          onClick = { viewModel.resetSession(task = task, model = selectedModel) },
          enabled = enableResetButton,
          modifier = Modifier
            .size(40.dp)
            .alpha(if (!enableResetButton) 0.5f else 1f),
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
          )
        ) {
          Icon(
            imageVector = Icons.Rounded.MapsUgc,
            contentDescription = "New Chat",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
        }
      }
      
      // TTS Control Button - moved to top bar
      IconButton(
        onClick = { 
          ttsManager.setEnabled(!ttsManager.isEnabled)
          if (!ttsManager.isEnabled) {
            ttsManager.stop()
          }
        },
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
          containerColor = if (ttsManager.isEnabled) {
            if (ttsManager.isInitialized) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer
            }
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          }
        )
      ) {
        Icon(
          imageVector = if (ttsManager.isEnabled) {
            Icons.Rounded.RecordVoiceOver
          } else {
            Icons.Rounded.VoiceOverOff
          },
          contentDescription = when {
            ttsManager.isEnabled && ttsManager.isInitialized && ttsManager.isSpeaking -> "TTS Speaking"
            ttsManager.isEnabled && ttsManager.isInitialized -> "TTS Ready"
            ttsManager.isEnabled && !ttsManager.isInitialized -> "TTS Initializing"
            else -> "TTS Disabled - Tap to Enable"
          },
          tint = if (ttsManager.isEnabled) {
            if (ttsManager.isInitialized) {
              if (ttsManager.isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSecondaryContainer
            }
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          modifier = Modifier.size(20.dp)
        )
      }
    }
    
    // Alarm Stop Button - positioned at bottom left, only visible when alarm is playing
    if (slowAlarmManager.isPlaying) {
      FloatingActionButton(
        onClick = { 
          slowAlarmManager.stopAlarm()
        },
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(16.dp)
          .padding(bottom = 80.dp), // Space above the input field
        containerColor = MaterialTheme.colorScheme.errorContainer
      ) {
        Icon(
          imageVector = Icons.Rounded.Stop,
          contentDescription = "Stop Alarm",
          tint = MaterialTheme.colorScheme.onErrorContainer
        )
      }
    }
  } // Close Box
}