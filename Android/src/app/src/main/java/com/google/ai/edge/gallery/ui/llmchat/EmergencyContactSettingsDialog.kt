
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
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.ai.edge.gallery.data.DataStoreRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

// Simple phone number validation helper
private fun isValidPhoneNumber(phone: String): Boolean {
  val trimmed = phone.trim()
  if (trimmed.isEmpty()) return false
  // Basic validation: should start with + and contain only digits and spaces/dashes
  val phoneRegex = Regex("^\\+?[0-9\\s\\-()]{7,}$")
  return phoneRegex.matches(trimmed)
}

@Composable
fun EmergencyContactSettingsDialog(
  dataStoreRepository: DataStoreRepository,
  onDismiss: () -> Unit,
) {
  var emergencyContact by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  var saveSuccess by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  val coroutineScope = rememberCoroutineScope()

  // Load current emergency contact when dialog opens
  LaunchedEffect(Unit) {
    isLoading = true
    emergencyContact = dataStoreRepository.readEmergencyContact()
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
            imageVector = Icons.Rounded.Phone,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Text(
            "Emergency Contact Settings",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        // Description
        Text(
          "Enter the phone number that will receive SMS alerts when a fall is detected. Include country code (e.g., +1234567890).",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Emergency Contact Input with enhanced styling
        OutlinedTextField(
          value = emergencyContact,
          onValueChange = { emergencyContact = it },
          label = { 
            Text(
              "Phone Number",
              style = MaterialTheme.typography.bodyMedium
            ) 
          },
          placeholder = { Text("+1234567890") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          enabled = !isLoading && !isSaving,
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Phone
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
          ),
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Phone,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
          },
          isError = errorMessage.isNotEmpty() && emergencyContact.isNotEmpty(),
          supportingText = if (errorMessage.isNotEmpty() && emergencyContact.isNotEmpty()) {
            { Text(errorMessage) }
          } else if (emergencyContact.isNotEmpty() && isValidPhoneNumber(emergencyContact)) {
            { 
              Text(
                "✓ Valid phone number",
                color = MaterialTheme.colorScheme.primary
              ) 
            }
          } else null
        )

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
              "Emergency contact saved successfully",
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
              val trimmed = emergencyContact.trim()
              if (isValidPhoneNumber(trimmed)) {
                errorMessage = ""
                isSaving = true
                saveSuccess = false
                coroutineScope.launch {
                  dataStoreRepository.saveEmergencyContact(trimmed)
                  isSaving = false
                  saveSuccess = true
                  // Auto-dismiss after a short delay
                  delay(1500)
                  onDismiss()
                }
              } else {
                errorMessage = "Please enter a valid phone number (e.g., +1234567890)"
              }
            },
            enabled = !isLoading && !isSaving && emergencyContact.trim().isNotBlank() && isValidPhoneNumber(emergencyContact.trim()),
            modifier = Modifier
              .shadow(
                elevation = if (!isLoading && !isSaving && emergencyContact.trim().isNotBlank()) 4.dp else 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
              ),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary
            )
          ) {
            if (isSaving) {
              androidx.compose.material3.CircularProgressIndicator(
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
