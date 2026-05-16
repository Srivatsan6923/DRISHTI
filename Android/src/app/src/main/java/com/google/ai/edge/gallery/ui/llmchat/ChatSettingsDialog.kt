
package com.google.ai.edge.gallery.ui.llmchat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.ai.edge.gallery.data.DataStoreRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ChatSettingsDialog(
  dataStoreRepository: DataStoreRepository,
  onDismiss: () -> Unit,
) {
  var forceGemma by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  var saveSuccess by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  // Load current setting when dialog opens
  LaunchedEffect(Unit) {
    isLoading = true
    forceGemma = dataStoreRepository.readForceGemma()
    isLoading = false
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 8.dp,
          shape = RoundedCornerShape(24.dp),
          spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        ),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        // Dialog title with icon
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Text(
            "Chat Settings",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        // Description
        Text(
          "Choose which model to use for chat responses. By default, Gemini API is used when available, with automatic fallback to Gemma.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Force Gemma Toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              "Force Gemma Model",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              if (forceGemma) {
                "Gemma will be used directly. Fallback to Gemini API is disabled."
              } else {
                "Gemini API will be used by default when available, with automatic fallback to Gemma if needed."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = forceGemma,
            onCheckedChange = { forceGemma = it },
            enabled = !isLoading && !isSaving
          )
        }

        // Show current status with animation
        val successAlpha by animateFloatAsState(
          targetValue = if (saveSuccess) 1f else 0f,
          animationSpec = tween(300),
          label = "successAlpha"
        )
        val successScale by animateFloatAsState(
          targetValue = if (saveSuccess) 1f else 0.8f,
          animationSpec = tween(300),
          label = "successScale"
        )
        
        if (saveSuccess || successAlpha > 0f) {
          Row(
            modifier = Modifier
              .alpha(successAlpha)
              .scale(successScale)
              .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Text(
              "Settings saved successfully",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Button row with enhanced styling
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onDismiss,
            enabled = !isSaving,
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Text(
              "Cancel",
              style = MaterialTheme.typography.labelLarge
            )
          }

          Button(
            onClick = {
              isSaving = true
              saveSuccess = false
              coroutineScope.launch {
                dataStoreRepository.saveForceGemma(forceGemma)
                isSaving = false
                saveSuccess = true
                // Auto-dismiss after a short delay
                delay(1500)
                onDismiss()
              }
            },
            enabled = !isLoading && !isSaving,
            modifier = Modifier
              .shadow(
                elevation = if (!isLoading && !isSaving) 4.dp else 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
              ),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary
            )
          ) {
            if (isSaving) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.padding(start = 8.dp))
              Text(
                "Saving...",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
              )
            } else {
              Text(
                "Save",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
    }
  }
}
