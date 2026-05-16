
package com.fallalert.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  
  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    // Handle permission results
    permissions.entries.forEach {
      // Permission granted/denied
    }
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    requestPermissions()
    
    setContent {
      MainScreen()
    }
  }
  
  private fun requestPermissions() {
    val permissions = mutableListOf<String>().apply {
      add(Manifest.permission.POST_NOTIFICATIONS)
      add(Manifest.permission.CALL_PHONE)
      add(Manifest.permission.USE_FULL_SCREEN_INTENT)
      add(Manifest.permission.ACTIVITY_RECOGNITION)
      add(Manifest.permission.ACCESS_FINE_LOCATION)
      add(Manifest.permission.ACCESS_COARSE_LOCATION)
      add(Manifest.permission.SEND_SMS)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.USE_EXACT_ALARM)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.SCHEDULE_EXACT_ALARM)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
      }
    }
    
    val permissionsToRequest = permissions.filter {
      ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }
    
    if (permissionsToRequest.isNotEmpty()) {
      requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
  }
}

@Composable
fun MainScreen() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val application = context.applicationContext as FallAlertApplication
  val repository = application.dataStoreRepository
  val coroutineScope = rememberCoroutineScope()
  
  var emergencyContact by remember { mutableStateOf("") }
  var isServiceRunning by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  
  // Permission launcher for multiple permissions
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    // Check if all required permissions are granted
    val allGranted = permissions.all { it.value }
    if (allGranted) {
      // Start service after permissions granted
      val intent = Intent(context, FallDetectionService::class.java).apply {
        action = "ACTION_START"
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
      isServiceRunning = true
    }
  }
  
  LaunchedEffect(Unit) {
    emergencyContact = repository.readEmergencyContact()
    isServiceRunning = isServiceRunning(context)
  }
  
  MaterialTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          text = "FallAlert",
          style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Service Status
        Card(
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "Service Status",
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = if (isServiceRunning) "Active" else "Inactive",
              style = MaterialTheme.typography.bodyLarge,
              color = if (isServiceRunning) 
                MaterialTheme.colorScheme.primary 
              else 
                MaterialTheme.colorScheme.onSurface
            )
          }
        }
        
        // Emergency Contact Input
        OutlinedTextField(
          value = emergencyContact,
          onValueChange = { emergencyContact = it },
          label = { Text("Emergency Contact") },
          placeholder = { Text("Enter phone number") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        
        // Save Button
        Button(
          onClick = {
            isLoading = true
            coroutineScope.launch {
              repository.saveEmergencyContact(emergencyContact)
              isLoading = false
            }
          },
          modifier = Modifier.fillMaxWidth(),
          enabled = !isLoading
        ) {
          if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
          } else {
            Text("Save Emergency Contact")
          }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Start/Stop Service Button
        Button(
          onClick = {
            if (isServiceRunning) {
              // Stop the service
              val intent = Intent(context, FallDetectionService::class.java).apply {
                action = "ACTION_STOP"
              }
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
              } else {
                context.startService(intent)
              }
              isServiceRunning = false
            } else {
              // Check required permissions before starting
              val hasActivityRecognition = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
              ) == PackageManager.PERMISSION_GRANTED
              
              val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
              ) == PackageManager.PERMISSION_GRANTED || 
              ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
              ) == PackageManager.PERMISSION_GRANTED
              
              val permissionsToRequest = mutableListOf<String>()
              if (!hasActivityRecognition) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
              }
              if (!hasLocation) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
              }
              
              if (permissionsToRequest.isNotEmpty()) {
                // Request permissions if not granted
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
              } else {
                // All permissions granted, start the service
                val intent = Intent(context, FallDetectionService::class.java).apply {
                  action = "ACTION_START"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  context.startForegroundService(intent)
                } else {
                  context.startService(intent)
                }
                isServiceRunning = true
              }
            }
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isServiceRunning) 
              MaterialTheme.colorScheme.error 
            else 
              MaterialTheme.colorScheme.primary
          )
        ) {
          Text(if (isServiceRunning) "Stop Service" else "Start Service")
        }
      }
    }
  }
}

private fun isServiceRunning(context: android.content.Context): Boolean {
  val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
  return activityManager.getRunningServices(Integer.MAX_VALUE)
    .any { it.service.className == FallDetectionService::class.java.name }
}
