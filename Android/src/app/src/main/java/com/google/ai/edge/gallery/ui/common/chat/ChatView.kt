
package com.google.ai.edge.gallery.ui.common.chat

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGChatView"

/**
 * A composable that displays a chat interface, allowing users to interact with different models
 * associated with a given task.
 *
 * This composable provides a horizontal pager for switching between models, a model selector for
 * configuring the selected model, and a chat panel for sending and receiving messages. It also
 * manages model initialization, cleanup, and download status, and handles navigation and system
 * back gestures.
 */
@Composable
fun ChatView(
  task: Task,
  viewModel: ChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  onSendMessage: (Model, List<ChatMessage>) -> Unit,
  onRunAgainClicked: (Model, ChatMessage) -> Unit,
  onBenchmarkClicked: (Model, ChatMessage, Int, Int) -> Unit,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onResetSessionClicked: (Model) -> Unit = {},
  onStreamImageMessage: (Model, ChatMessageImage) -> Unit = { _, _ -> },
  onStopButtonClicked: (Model) -> Unit = {},
  showStopButtonInInputWhenInProgress: Boolean = false,
  showTopBar: Boolean = true,
) {
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel

  // Image viewer related.
  var selectedImageIndex by remember { mutableIntStateOf(-1) }
  var allImageViewerImages by remember { mutableStateOf<List<Bitmap>>(listOf()) }
  var showImageViewer by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var navigatingUp by remember { mutableStateOf(false) }

  val handleNavigateUp = {
    navigatingUp = true
    navigateUp()

    // clean up all models.
    scope.launch(Dispatchers.Default) {
      for (model in task.models) {
        modelManagerViewModel.cleanupModel(context = context, task = task, model = model)
      }
    }
  }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(TAG, "Initializing model '${selectedModel.name}' from ChatView launched effect")
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }

  // Handle system's edge swipe.
  BackHandler {
    val modelInitializationStatus =
      modelManagerUiState.modelInitializationStatus[selectedModel.name]
    val isModelInitializing =
      modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
    if (!isModelInitializing && !uiState.inProgress) {
      handleNavigateUp()
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = if (showTopBar) {
      {
        ModelPageAppBar(
          task = task,
          model = selectedModel,
          modelManagerViewModel = modelManagerViewModel,
          canShowResetSessionButton = true,
          isResettingSession = uiState.isResettingSession,
          inProgress = uiState.inProgress,
          modelPreparing = uiState.preparing,
          onResetSessionClicked = onResetSessionClicked,
          onConfigChanged = { old, new ->
            viewModel.addConfigChangedMessage(
              oldConfigValues = old,
              newConfigValues = new,
              model = selectedModel,
            )
          },
          onBackClicked = { handleNavigateUp() },
        )
      }
    } else {
      { Box {} }
    },
  ) { innerPadding ->
    Box {
      // val curSelectedModel = task.models[pageIndex]
      val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]

      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(
            // Modern gradient background with subtle color transitions
            brush = Brush.verticalGradient(
              colors = listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.surfaceContainerLowest
              ),
              startY = 0f,
              endY = Float.POSITIVE_INFINITY
            )
          )
      ) {
        // ANVI always has the model available (local file), so show ChatPanel directly
        ChatPanel(
          modelManagerViewModel = modelManagerViewModel,
          task = task,
          selectedModel = selectedModel,
          viewModel = viewModel,
          innerPadding = innerPadding,
          navigateUp = navigateUp,
          onSendMessage = onSendMessage,
          onRunAgainClicked = onRunAgainClicked,
          onBenchmarkClicked = onBenchmarkClicked,
          onStreamImageMessage = onStreamImageMessage,
          onStreamEnd = { averageFps ->
            viewModel.addMessage(
              model = selectedModel,
              message =
                ChatMessageInfo(
                  content = "Live camera session ended. Average FPS: $averageFps"
                ),
            )
          },
          onStopButtonClicked = { onStopButtonClicked(selectedModel) },
          onImageSelected = { bitmaps, selectedBitmapIndex ->
            selectedImageIndex = selectedBitmapIndex
            allImageViewerImages = bitmaps
            showImageViewer = true
          },
          modifier = Modifier.weight(1f),
          showStopButtonInInputWhenInProgress = showStopButtonInInputWhenInProgress,
        )
      }

      // Image viewer.
      AnimatedVisibility(
        visible = showImageViewer,
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
      ) {
        val pagerState =
          rememberPagerState(
            pageCount = { allImageViewerImages.size },
            initialPage = selectedImageIndex,
          )
        val scrollEnabled = remember { mutableStateOf(true) }
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
          HorizontalPager(
            state = pagerState,
            userScrollEnabled = scrollEnabled.value,
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)),
          ) { page ->
            allImageViewerImages[page].let { image ->
              ZoomableImage(bitmap = image.asImageBitmap(), pagerState = pagerState)
            }
          }

          // Close button.
          IconButton(
            onClick = { showImageViewer = false },
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
              ),
            modifier = Modifier.offset(x = (-8).dp, y = 8.dp).align(Alignment.TopEnd),
          ) {
            Icon(
              Icons.Rounded.Close,
              contentDescription = stringResource(R.string.cd_close_image_viewer_icon),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

// @Preview
// @Composable
// fun ChatScreenPreview() {
//   GalleryTheme {
//     val context = LocalContext.current
//     val task = TASK_TEST1
//     ChatView(
//       task = task,
//       viewModel = PreviewChatModel(context = context),
//       modelManagerViewModel = PreviewModelManagerViewModel(context = context),
//       onSendMessage = { _, _ -> },
//       onRunAgainClicked = { _, _ -> },
//       onBenchmarkClicked = { _, _, _, _ -> },
//       navigateUp = {},
//     )
//   }
// }
