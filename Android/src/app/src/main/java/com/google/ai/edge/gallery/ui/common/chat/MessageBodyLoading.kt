
package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.RotationalLoader

/** Composable function to display a loading indicator. */
@Composable
fun MessageBodyLoading() {
  RotationalLoader(size = 32.dp)
}
