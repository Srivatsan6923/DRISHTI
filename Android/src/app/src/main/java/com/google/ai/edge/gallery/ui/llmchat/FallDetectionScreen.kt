
package com.google.ai.edge.gallery.ui.llmchat

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.GalleryApplication
import com.google.ai.edge.gallery.data.DataStoreRepository
import kotlinx.coroutines.launch

@Composable
fun FallDetectionScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val dataStoreRepository = remember {
    (context.applicationContext as GalleryApplication).dataStoreRepository
  }
  val coroutineScope = rememberCoroutineScope()
  
  // State management
  var fallCount by remember { mutableIntStateOf(0) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var emergencyContact by remember { mutableStateOf("") }
  var isMonitoring by remember { mutableStateOf(true) }
  
  // Alarm and TTS managers
  val slowAlarmManager = rememberSlowAlarmManager(context, dataStoreRepository)
  
  // Register alarm stop callback for HTTP endpoint
  LaunchedEffect(slowAlarmManager.stopAlarm) {
    // Register the callback in AnviMainActivity so HTTP endpoint can stop the alarm
    try {
      // Try to find the Activity from context
      var currentContext: Context? = context
      var activity: Activity? = null
      
      // Traverse up the context chain to find an Activity
      while (currentContext != null) {
        if (currentContext is Activity) {
          activity = currentContext
          break
        }
        currentContext = if (currentContext is android.content.ContextWrapper) {
          currentContext.baseContext
        } else {
          null
        }
      }
      
      if (activity is com.google.ai.edge.gallery.anvi.AnviMainActivity) {
        com.google.ai.edge.gallery.anvi.AnviMainActivity.alarmStopCallback = slowAlarmManager.stopAlarm
        Log.d("FallDetectionScreen", "Alarm stop callback registered for HTTP endpoint")
      } else {
        Log.w("FallDetectionScreen", "Context is not AnviMainActivity, callback not registered")
      }
    } catch (e: Exception) {
      Log.w("FallDetectionScreen", "Could not register alarm stop callback: ${e.message}")
    }
  }
  
  // Unregister callback when screen is disposed
  DisposableEffect(Unit) {
    onDispose {
      com.google.ai.edge.gallery.anvi.AnviMainActivity.alarmStopCallback = null
      Log.d("FallDetectionScreen", "Alarm stop callback unregistered")
    }
  }
  
  // Fall detection observer
  val fallDetector = remember { 
    FallDetectionObserver(context, dataStoreRepository) { 
      fallCount++
      if (isMonitoring) {
        slowAlarmManager.startAlarm()
        Log.d("FallDetectionScreen", "Fall detected - starting slow alarm")
      }
    } 
  }
  
  // Load emergency contact
  LaunchedEffect(Unit) {
    emergencyContact = dataStoreRepository.readEmergencyContact()
  }
  
  // Lifecycle observer - properly manage registration based on isMonitoring state
  DisposableEffect(isMonitoring) {
    if (isMonitoring) {
      lifecycleOwner.lifecycle.addObserver(fallDetector)
      Log.d("FallDetectionScreen", "Fall detection monitoring started")
    } else {
      lifecycleOwner.lifecycle.removeObserver(fallDetector)
      Log.d("FallDetectionScreen", "Fall detection monitoring paused")
    }
    onDispose { 
      lifecycleOwner.lifecycle.removeObserver(fallDetector)
      Log.d("FallDetectionScreen", "Fall detection observer removed on dispose")
    }
  }
  
  // Animated pulse for monitoring indicator
  val pulseScale by animateFloatAsState(
    targetValue = if (isMonitoring) 1.1f else 1f,
    animationSpec = tween(durationMillis = 1000),
    label = "pulse"
  )
  
  // Scroll state for the scrollable content
  val scrollState = rememberScrollState()
  
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // Scrollable content
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
    Spacer(modifier = Modifier.height(16.dp))
    
    // Header
    Text(
      text = "Fall Detection",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Status Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = if (isMonitoring) 
          MaterialTheme.colorScheme.primaryContainer 
        else 
          MaterialTheme.colorScheme.surfaceVariant
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Monitoring indicator with pulse animation
        Box(
          modifier = Modifier
            .size(120.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(
              if (isMonitoring) 
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
              else 
                MaterialTheme.colorScheme.surfaceVariant
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isMonitoring) Icons.Rounded.Security else Icons.Rounded.SecurityUpdateWarning,
            contentDescription = "Monitoring Status",
            modifier = Modifier.size(64.dp),
            tint = if (isMonitoring) 
              MaterialTheme.colorScheme.primary 
            else 
              MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        
        Text(
          text = if (isMonitoring) "Monitoring Active" else "Monitoring Paused",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = if (isMonitoring) 
            MaterialTheme.colorScheme.onPrimaryContainer 
          else 
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
          text = if (isMonitoring) 
            "Your device is actively monitoring for falls" 
          else 
            "Fall detection is currently paused",
          style = MaterialTheme.typography.bodyMedium,
          color = if (isMonitoring) 
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
          else 
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          textAlign = TextAlign.Center
        )
        
        // Toggle button
        Button(
          onClick = { isMonitoring = !isMonitoring },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isMonitoring) 
              MaterialTheme.colorScheme.error 
            else 
              MaterialTheme.colorScheme.primary
          )
        ) {
          Icon(
            imageVector = if (isMonitoring) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(if (isMonitoring) "Pause Monitoring" else "Resume Monitoring")
        }
      }
    }
    
    // Fall Statistics Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Fall Statistics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          
          IconButton(
            onClick = { fallCount = 0 }
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = "Reset Count"
            )
          }
        }
        
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(CircleShape)
              .background(
                if (fallCount > 0) 
                  MaterialTheme.colorScheme.errorContainer 
                else 
                  MaterialTheme.colorScheme.surfaceVariant
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "$fallCount",
              style = MaterialTheme.typography.displayMedium,
              fontWeight = FontWeight.Bold,
              color = if (fallCount > 0) 
                MaterialTheme.colorScheme.error 
              else 
                MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        
        Text(
          text = if (fallCount == 0) 
            "No falls detected" 
          else if (fallCount == 1) 
            "1 fall detected" 
          else 
            "$fallCount falls detected",
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
      }
    }
    
    // Emergency Contact Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Emergency Contact",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          
          IconButton(
            onClick = {
            }
          ) {
            Icon(
              imageVector = Icons.Rounded.Settings,
              contentDescription = "Change Emergency Contact",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
        
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Phone,
            contentDescription = null,
            tint =
              MaterialTheme.colorScheme.primary
          )
          
          Text(
            text = if (emergencyContact.isNotEmpty()) 
              emergencyContact 
            else 
              "No emergency contact set",
            style = MaterialTheme.typography.bodyLarge,
            color = if (emergencyContact.isNotEmpty()) 
              MaterialTheme.colorScheme.onSurface 
            else 
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
          )
        }
        
        // Settings button - make it more prominent
        Button(
          onClick = { 
            showSettingsDialog = true
            Log.d("FallDetectionScreen", "Settings button clicked - opening dialog")
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
          )
        ) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (emergencyContact.isEmpty()) 
              "Set Emergency Contact" 
            else 
              "Change Emergency Contact"
          )
        }
        
        if (emergencyContact.isEmpty()) {
          Text(
            text = "Tap the button above to set an emergency contact. They will receive SMS alerts when a fall is detected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
    
    // Info text
    Text(
      text = "Fall detection uses your device's sensors to detect sudden movements and impacts. In case of a detected fall, an alarm will sound and your emergency contact will be notified.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )
    
    // Bottom padding for better scrolling experience
    Spacer(modifier = Modifier.height(16.dp))
    }
    
    // Alarm Stop Button Overlay (always visible when alarm is playing)
    if (slowAlarmManager.isPlaying) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
          )
          .padding(24.dp),
        contentAlignment = Alignment.BottomCenter
      ) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
              )
              Column(
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "🚨 ALARM ACTIVE",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                  text = "Fall detected! Alarm is sounding.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                )
              }
            }
            
            // Large, prominent stop button
            Button(
              onClick = { 
                slowAlarmManager.stopAlarm()
                Log.d("FallDetectionScreen", "Alarm stopped by user")
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
              ),
              contentPadding = PaddingValues(20.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = "Stop Alarm",
                modifier = Modifier.size(28.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                "STOP ALARM",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }
  
  // Settings Dialog
  if (showSettingsDialog) {
    EmergencyContactSettingsDialog(
      dataStoreRepository = dataStoreRepository,
      onDismiss = { 
        showSettingsDialog = false
        coroutineScope.launch {
          emergencyContact = dataStoreRepository.readEmergencyContact()
        }
      }
    )
  }
}
