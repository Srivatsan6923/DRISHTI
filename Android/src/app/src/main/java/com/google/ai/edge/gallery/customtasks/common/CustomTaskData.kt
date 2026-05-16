
package com.google.ai.edge.gallery.customtasks.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * Data class to hold information passed to the `MainScreen` composable of a custom task.
 *
 * @param modelManagerViewModel The ViewModel providing access to the state of models and their
 *   management.
 * @param bottomPadding The bottom padding of the Scaffold's `innerPadding`. By default, your
 *   `MainScreen` will extend to the bottom edge. Use this value if you need to apply padding to the
 *   bottom of your screen's content to account for elements like a bottom navigation bar.
 * @param setAppBarControlsDisabled A callback function that the custom task screen can call to
 *   enable and disable controls (e.g. back button, configs, etc) in the app bar.
 * @param setTopBarVisible A callback function that the custom task screen can call to show and hide
 *   the top bar.
 */
data class CustomTaskData(
  val modelManagerViewModel: ModelManagerViewModel,
  val bottomPadding: Dp = 0.dp,
  val setAppBarControlsDisabled: (Boolean) -> Unit = {},
  val setTopBarVisible: (Boolean) -> Unit = {},
)

data class CustomTaskDataForBuiltinTask(
  val modelManagerViewModel: ModelManagerViewModel,
  val onNavUp: () -> Unit,
)
