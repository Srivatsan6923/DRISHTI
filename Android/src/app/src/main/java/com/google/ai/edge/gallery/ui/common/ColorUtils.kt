
package com.google.ai.edge.gallery.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.theme.customColors

@Composable
fun getTaskBgColor(task: Task): Color {
  val size = MaterialTheme.customColors.taskBgColors.size
  val colorIndex: Int = ((task.index % size) + size) % size
  return MaterialTheme.customColors.taskBgColors[colorIndex]
}

@Composable
fun getTaskBgGradientColors(task: Task): List<Color> {
  val size = MaterialTheme.customColors.taskBgColors.size
  val colorIndex: Int = ((task.index % size) + size) % size
  return MaterialTheme.customColors.taskBgGradientColors[colorIndex]
}

@Composable
fun getTaskIconColor(task: Task): Color {
  val size = MaterialTheme.customColors.taskIconColors.size
  val colorIndex: Int = ((task.index % size) + size) % size
  return MaterialTheme.customColors.taskIconColors[colorIndex]
}

@Composable
fun getTaskIconColor(index: Int): Color {
  val size = MaterialTheme.customColors.taskIconColors.size
  val colorIndex: Int = ((index % size) + size) % size
  return MaterialTheme.customColors.taskIconColors[colorIndex]
}
