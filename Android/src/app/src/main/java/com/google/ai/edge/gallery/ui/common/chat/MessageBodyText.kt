
package com.google.ai.edge.gallery.ui.common.chat

// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
// import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.ui.common.MarkdownText

/** Composable function to display the text content of a ChatMessageText. */
@Composable
fun MessageBodyText(message: ChatMessageText, inProgress: Boolean) {
  if (message.side == ChatSide.USER) {
    MarkdownText(
      text = message.content,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
      textColor = Color.White,
      linkColor = Color.White.copy(alpha = 0.9f),
    )
  } else if (message.side == ChatSide.AGENT) {
    val cdResponse = stringResource(R.string.cd_model_response_text)
    if (message.isMarkdown) {
      MarkdownText(
        text = message.content,
        modifier =
          Modifier.padding(horizontal = 20.dp, vertical = 18.dp).semantics(mergeDescendants = true) {
            contentDescription = cdResponse
            // Only announce when message is complete.
            if (!inProgress) {
              liveRegion = LiveRegionMode.Polite
            }
          },
      )
    } else {
      Text(
        message.content,
        style = MaterialTheme.typography.bodyLarge.copy(
          lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
          Modifier.padding(horizontal = 20.dp, vertical = 18.dp).semantics {
            contentDescription = cdResponse
            // Only announce when message is complete.
            if (!inProgress) {
              liveRegion = LiveRegionMode.Polite
            }
          },
      )
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun MessageBodyTextPreview() {
//   GalleryTheme {
//     Column {
//       Row(modifier = Modifier.padding(16.dp).background(MaterialTheme.colorScheme.primary)) {
//         MessageBodyText(ChatMessageText(content = "Hello world", side = ChatSide.USER))
//       }
//       Row(
//         modifier = Modifier.padding(16.dp).background(MaterialTheme.colorScheme.surfaceContainer)
//       ) {
//         MessageBodyText(ChatMessageText(content = "yes hello world", side = ChatSide.AGENT))
//       }
//     }
//   }
// }
