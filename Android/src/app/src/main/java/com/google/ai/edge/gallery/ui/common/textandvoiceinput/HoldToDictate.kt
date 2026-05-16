package com.google.ai.edge.gallery.ui.common.textandvoiceinput

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.getTaskIconColor
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AGHoldToDictate"

/**
 * A Composable that provides a "Hold to Dictate" functionality.
 *
 * This composable requests RECORD_AUDIO permission and, once granted, displays a button. The user
 * can press and hold the button to start speech recognition. Releasing the button stops the
 * recognition. Moving the finger off the button while holding will cancel the recognition.
 */
@Composable
fun HoldToDictate(
  task: Task,
  viewModel: HoldToDictateViewModel,
  onDone: (String) -> Unit,
  onAmplitudeChanged: (Int) -> Unit,
  enabled: Boolean,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  var recordAudioPermissionGranted by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val recordAudioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      permissionGranted ->
      if (permissionGranted) {
        recordAudioPermissionGranted = true
      }
    }

  LaunchedEffect(Unit) {
    // Check permission
    when (PackageManager.PERMISSION_GRANTED) {
      // Already got permission.
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
        recordAudioPermissionGranted = true
      }

      // Otherwise, ask for permission
      else -> {
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    }
  }

  if (recordAudioPermissionGranted) {
    val scale by animateFloatAsState(
      targetValue = if (uiState.recognizing) 1.2f else 1f,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
      ),
      label = "mic_button_scale"
    )

    Box(
      modifier =
        modifier
          .then(
            if (enabled) {
              Modifier.pointerInput(Unit) {
                detectTapGestures(
                  onPress = {
                    viewModel.startSpeechRecognition(
                      onDone = onDone,
                      onAmplitudeChanged = onAmplitudeChanged,
                    )
                    try {
                      awaitRelease()
                    } catch (e: CancellationException) {
                      // Move out of the button to cancel it.
                      viewModel.cancelSpeechRecognition()
                      return@detectTapGestures
                    }

                    // Release to stop recognition.
                    viewModel.stopSpeechRecognition()
                  }
                )
              }
            } else {
              Modifier
            }
          )
          .scale(scale)
          .size(48.dp)
          .clip(CircleShape)
          .background(
            if (uiState.recognizing) {
              MaterialTheme.colorScheme.secondaryContainer
            } else {
              getTaskIconColor(task = task)
            }
          ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Rounded.Mic,
        contentDescription =
          stringResource(
            if (uiState.recognizing) R.string.listening else R.string.hold_down_to_talk
          ),
        tint =
          if (uiState.recognizing) {
            MaterialTheme.colorScheme.primary
          } else {
            Color.White
          },
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
