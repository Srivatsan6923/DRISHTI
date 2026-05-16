
package com.google.ai.edge.gallery.ui.common.chat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.MarkdownText
import com.google.ai.edge.gallery.ui.theme.customColors

/**
 * Composable function to display error message content within a chat.
 *
 * Supports markdown.
 */
@Composable
fun MessageBodyError(message: ChatMessageError) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Box(
      modifier =
        Modifier.clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.customColors.errorContainerColor)
    ) {
      MarkdownText(
        text = message.content,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        smallFontSize = true,
        textColor = MaterialTheme.customColors.errorTextColor,
      )
    }
  }
}
