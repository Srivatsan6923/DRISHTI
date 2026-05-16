
package com.google.ai.edge.gallery.anvi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.textandvoiceinput.HoldToDictateViewModel
import com.google.ai.edge.gallery.ui.llmchat.FallDetectionScreen
import com.google.ai.edge.gallery.ui.llmchat.LlmAskImageScreen
import com.google.ai.edge.gallery.ui.llmchat.LlmAskImageViewModel
import com.google.ai.edge.gallery.ui.llmchat.rememberTtsManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

/** Top level composable representing the ANVI app with bottom navigation. */
@Composable
fun AnviApp(
  modelManagerViewModel: ModelManagerViewModel,
  receivedImagePath: androidx.compose.runtime.MutableState<String?>,
  showImageModal: androidx.compose.runtime.MutableState<Boolean>,
  onImageModalDismiss: () -> Unit,
) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  var selectedTab by remember { mutableStateOf(0) }
  
  // Set up the Gemma-3n E2B model once tasks are available
  LaunchedEffect(uiState.tasks.isNotEmpty()) {
    if (uiState.tasks.isNotEmpty()) {
      // Set up the Gemma-3n E2B model with the specified path
      val askImageTask = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_ASK_IMAGE)
      askImageTask?.let { task ->
        // Find or create the Gemma model
        var gemmaModel = task.models.find { it.name == "Gemma-3n-E2B" }
        
        if (gemmaModel == null) {
          gemmaModel = createGemmaModel()
          // Add model to task if not already present
          task.models.add(gemmaModel)
        }
        
        // Select the model
        modelManagerViewModel.selectModel(gemmaModel)
        
        // Mark model as downloaded since it's at a local path
        modelManagerViewModel.setDownloadStatus(
          curModel = gemmaModel,
          status = ModelDownloadStatus(status = ModelDownloadStatusType.SUCCEEDED),
        )
      }
    }
  }

  Scaffold(
    bottomBar = {
      ModernBottomNavigation(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it }
      )
    },
    containerColor = Color.Transparent
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (selectedTab) {
        0 -> {
          // Chat Screen
          val askImageTask = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_ASK_IMAGE)
          if (askImageTask != null) {
            LlmAskImageScreen(
              modelManagerViewModel = modelManagerViewModel,
              navigateUp = { /* No navigation up in ANVI - this is the main screen */ },
            )
          } else {
            // Show empty state while tasks are loading
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
              Text("Loading...")
            }
          }
        }
        1 -> {
          // Fall Detection Screen
          FallDetectionScreen()
        }
      }
      
      // Image Upload Modal
      if (showImageModal.value && receivedImagePath.value != null) {
        ImageQuestionModal(
          imagePath = receivedImagePath.value!!,
          modelManagerViewModel = modelManagerViewModel,
          onDismiss = onImageModalDismiss,
        )
      }
    }
  }
}

@Composable
private fun ModernBottomNavigation(
  selectedTab: Int,
  onTabSelected: (Int) -> Unit
) {
  val elevation by animateFloatAsState(
    targetValue = if (selectedTab >= 0) 8f else 0f,
    animationSpec = tween(300),
    label = "elevation"
  )
  
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    modifier = Modifier
      .shadow(
        elevation = elevation.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
      )
      .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
  ) {
    NavigationBarItem(
      icon = {
        val scale by animateFloatAsState(
          targetValue = if (selectedTab == 0) 1.15f else 1f,
          animationSpec = tween(300),
          label = "scale"
        )
        Icon(
          imageVector = if (selectedTab == 0) Icons.Rounded.Forum else Icons.Rounded.ChatBubbleOutline,
          contentDescription = "Chat",
          modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
        )
      },
      label = { 
        Text(
          "Chat",
          style = MaterialTheme.typography.labelSmall
        ) 
      },
      selected = selectedTab == 0,
      onClick = { onTabSelected(0) },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
      )
    )
    NavigationBarItem(
      icon = {
        val scale by animateFloatAsState(
          targetValue = if (selectedTab == 1) 1.15f else 1f,
          animationSpec = tween(300),
          label = "scale"
        )
        Icon(
          imageVector = if (selectedTab == 1) Icons.Rounded.Security else Icons.Rounded.SecurityUpdateWarning,
          contentDescription = "Fall Detection",
          modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
        )
      },
      label = { 
        Text(
          "Fall Detection",
          style = MaterialTheme.typography.labelSmall
        ) 
      },
      selected = selectedTab == 1,
      onClick = { onTabSelected(1) },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
      )
    )
  }
}

private fun createGemmaModel(): com.google.ai.edge.gallery.data.Model {
  return com.google.ai.edge.gallery.data.Model(
    name = "Gemma-3n-E2B",
    displayName = "Gemma-3n E2B",
    info = "Gemma-3n E2B model for Ask Images",
    localModelFilePathOverride = "/data/local/tmp/modals/gemma.litertlm",
    llmSupportImage = true,
    llmSupportAudio = false,
    configs = com.google.ai.edge.gallery.data.createLlmChatConfigs(),
    downloadFileName = "gemma.litertlm",
    sizeInBytes = 0L, // Size not needed for local files
  ).apply {
    preProcess()
  }
}

@Composable
fun ImageQuestionModal(
  imagePath: String,
  modelManagerViewModel: ModelManagerViewModel,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val ttsManager = rememberTtsManager(context)
  val holdToDictateViewModel: HoldToDictateViewModel = hiltViewModel()
  val llmViewModel: LlmAskImageViewModel = hiltViewModel()
  val uiState by holdToDictateViewModel.uiState.collectAsState()
  val coroutineScope = rememberCoroutineScope()
  
  var questionState by remember { mutableStateOf<QuestionState>(QuestionState.WaitingForPrompt) }
  var recognizedQuestion by remember { mutableStateOf("") }
  var responseText by remember { mutableStateOf("") }
  var isProcessing by remember { mutableStateOf(false) }
  var lastSpeechTime by remember { mutableStateOf(0L) }
  var recognitionRetryCount by remember { mutableStateOf(0) }
  
  // Reset state when modal opens
  LaunchedEffect(imagePath) {
    questionState = QuestionState.WaitingForPrompt
    recognizedQuestion = ""
    responseText = ""
    isProcessing = false
    lastSpeechTime = 0L
    recognitionRetryCount = 0
  }
  
  // Cleanup when modal is dismissed
  DisposableEffect(Unit) {
    onDispose {
      ttsManager.stop()
      holdToDictateViewModel.cancelSpeechRecognition()
    }
  }
  
  val imageFile = File(imagePath)
  val bitmap = remember(imagePath) {
    if (imageFile.exists()) {
      BitmapFactory.decodeFile(imageFile.absolutePath)
    } else {
      null
    }
  }
  
  var ttsPromptDone by remember { mutableStateOf(false) }
  
  // Function to start speech recognition with retry logic
  fun startRecognitionWithRetry() {
    questionState = QuestionState.Listening
    var currentRetryCount = 0 // Local retry count for this attempt
    
    fun attemptRecognition() {
      holdToDictateViewModel.startSpeechRecognition(
        onDone = { text ->
          if (text.isNotEmpty() && questionState == QuestionState.Listening) {
            recognizedQuestion = text
            questionState = QuestionState.Processing
            isProcessing = true
            recognitionRetryCount = 0 // Reset retry count on success
            
            // Step 3: Send to model - use coroutine scope to handle async operations
            coroutineScope.launch {
              val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_ASK_IMAGE)
              val model = modelManagerViewModel.uiState.value.selectedModel
              
              if (task != null && model != null && bitmap != null) {
                // Ensure model is initialized
                val modelInitStatus = modelManagerViewModel.uiState.value.modelInitializationStatus[model.name]
                if (modelInitStatus?.status != com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED) {
                  // Initialize model if not already initialized
                  modelManagerViewModel.initializeModel(
                    context = context,
                    task = task,
                    model = model
                  )
                  // Wait a bit for initialization
                  delay(2000)
                }
                
                // Add image and question to chat
                llmViewModel.addMessage(model = model, message = ChatMessageImage(
                  bitmaps = listOf(bitmap),
                  imageBitMaps = listOf(bitmap.asImageBitmap()),
                  side = com.google.ai.edge.gallery.ui.common.chat.ChatSide.USER,
                ))
                llmViewModel.addMessage(model = model, message = ChatMessageText(
                  content = recognizedQuestion,
                  side = com.google.ai.edge.gallery.ui.common.chat.ChatSide.USER,
                ))
                
                // Generate response
                llmViewModel.generateResponse(
                  context = context,
                  model = model,
                  input = recognizedQuestion,
                  images = listOf(bitmap),
                  onError = { errorMessage ->
                    responseText = "Error: $errorMessage"
                    isProcessing = false
                    questionState = QuestionState.Error
                  },
                  onStreamingText = { streamingText ->
                    if (streamingText != "__STREAMING_COMPLETE__") {
                      // The streamingText contains the full accumulated response
                      responseText = streamingText
                      // Step 4: Use TTS to speak the response (TTS manager handles streaming)
                      ttsManager.speakText(streamingText)
                    } else {
                      isProcessing = false
                      questionState = QuestionState.Completed
                    }
                  },
                  modelManagerViewModel = modelManagerViewModel,
                )
              } else {
                // Error: missing task, model, or bitmap
                responseText = "Error: Model or image not available"
                isProcessing = false
                questionState = QuestionState.Error
              }
            }
          } else if (text.isEmpty() && questionState == QuestionState.Listening) {
            // No speech detected, try again or show error
            responseText = "No speech detected. Please try again."
            questionState = QuestionState.Error
          }
        },
        onAmplitudeChanged = { amplitude ->
          // Update last speech time when we detect audio input
          if (amplitude > 1000) { // Threshold for detecting speech
            lastSpeechTime = System.currentTimeMillis()
          }
        },
        autoStop = true, // Enable auto-stop when user stops talking
        onError = { errorCode ->
          // Handle recognition errors with retry logic
          if (currentRetryCount < 2 && questionState == QuestionState.Listening) {
            currentRetryCount++
            recognitionRetryCount = currentRetryCount
            coroutineScope.launch {
              delay(500) // Wait before retry to allow audio system to settle
              attemptRecognition()
            }
          } else {
            // Max retries reached or state changed
            responseText = "Failed to start listening. Please try again."
            questionState = QuestionState.Error
            recognitionRetryCount = 0
          }
        }
      )
    }
    
    attemptRecognition()
  }
  
  // Step 1: When modal opens, use TTS to ask user to ask a question
  LaunchedEffect(Unit) {
    delay(500) // Small delay to ensure TTS is ready
    ttsManager.speakText("Please ask a question about this image.")
    ttsPromptDone = true
    
    // Wait for TTS to actually finish before starting recognition
    while (ttsManager.isSpeaking) {
      delay(100) // Check every 100ms
    }
    
    // Additional small delay to ensure audio system is ready
    delay(300)
    
    // Start speech recognition
    startRecognitionWithRetry()
  }
  
  // Monitor for silence timeout as a backup - automatically stop if user stops talking for too long
  LaunchedEffect(uiState.recognizing, lastSpeechTime) {
    if (uiState.recognizing && questionState == QuestionState.Listening) {
      while (uiState.recognizing) {
        delay(500) // Check every 500ms
        val timeSinceLastSpeech = System.currentTimeMillis() - lastSpeechTime
        // If no speech for 5 seconds total, stop anyway (backup safety mechanism)
        if (timeSinceLastSpeech > 5000) {
          holdToDictateViewModel.stopSpeechRecognition()
          break
        }
      }
    }
  }
  
  // Handle speech recognition state changes
  LaunchedEffect(uiState.recognizing) {
    if (!uiState.recognizing && questionState == QuestionState.Listening && uiState.recognizedText.isEmpty()) {
      // Speech recognition stopped without text - might be timeout or error
      // The onDone callback will handle the actual processing
    }
  }
  
  // Modal UI
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      shape = RoundedCornerShape(16.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Close button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Image display
        bitmap?.let {
          Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Uploaded Image",
            modifier = Modifier
              .fillMaxWidth()
              .height(300.dp)
              .clip(RoundedCornerShape(8.dp))
          )
        } ?: Text("Loading image...")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Status text
        when (questionState) {
          QuestionState.WaitingForPrompt -> {
            Text("Preparing...")
          }
          QuestionState.Listening -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Listening for your question...")
              Spacer(modifier = Modifier.height(8.dp))
              if (uiState.recognizing) {
                Icon(
                  Icons.Filled.Mic,
                  contentDescription = "Listening",
                  modifier = Modifier.size(48.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  uiState.recognizedText.ifEmpty { "Speak now..." },
                  style = MaterialTheme.typography.bodyMedium
                )
              } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Preparing to listen...")
              }
            }
          }
          QuestionState.Processing -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator()
              Spacer(modifier = Modifier.height(8.dp))
              Text("Processing your question...")
            }
          }
          QuestionState.Completed -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Response received", style = MaterialTheme.typography.titleMedium)
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                "The response has been added to the chat.",
                style = MaterialTheme.typography.bodyMedium
              )
              Spacer(modifier = Modifier.height(16.dp))
              TextButton(onClick = onDismiss) {
                Text("Close")
              }
            }
          }
          QuestionState.Error -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Error occurred", color = MaterialTheme.colorScheme.error)
              Spacer(modifier = Modifier.height(16.dp))
              TextButton(onClick = onDismiss) {
                Text("Close")
              }
            }
          }
        }
      }
    }
  }
}

private enum class QuestionState {
  WaitingForPrompt,
  Listening,
  Processing,
  Completed,
  Error
}
