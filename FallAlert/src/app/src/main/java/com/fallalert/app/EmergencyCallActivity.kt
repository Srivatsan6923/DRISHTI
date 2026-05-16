
package com.fallalert.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class EmergencyCallActivity : ComponentActivity() {
  
  companion object {
    private const val TAG = "EmergencyCallActivity"
  }
  
  private var phoneNumber: String = ""
  private var locationInfo: LocationInfo? = null
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Make activity visible even when locked
    window.addFlags(
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
      WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
      WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    )
    
    phoneNumber = intent.getStringExtra("phone_number") ?: ""
    
    // Get location from intent
    val latitude = intent.getDoubleExtra("latitude", 0.0)
    val longitude = intent.getDoubleExtra("longitude", 0.0)
    val address = intent.getStringExtra("address")
    
    if (latitude != 0.0 && longitude != 0.0) {
      locationInfo = LocationInfo(
        latitude = latitude,
        longitude = longitude,
        address = address,
        accuracy = null
      )
    }
    
    if (phoneNumber.isEmpty()) {
      Log.e(TAG, "No phone number provided")
      finish()
      return
    }
    
    // Cancel the emergency call notification since we're showing the activity
    val notificationManager = FallAlertNotificationManager(this)
    notificationManager.cancelEmergencyCallNotification()
    
    setContent {
      EmergencyCallScreen(
        phoneNumber = phoneNumber,
        locationInfo = locationInfo,
        onCall = { makeCall() },
        onCancel = { finish() }
      )
    }
    
    // Automatically make the call after a short delay
    android.os.Handler(mainLooper).postDelayed({
      makeCall()
    }, 1000) // Start call after 1 second
  }
  
  private fun makeCall() {
    try {
      // Check if we have CALL_PHONE permission
      if (ContextCompat.checkSelfPermission(
          this,
          android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
      ) {
        val intent = Intent(Intent.ACTION_CALL).apply {
          data = Uri.parse("tel:$phoneNumber")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Log.d(TAG, "Emergency call initiated to: $phoneNumber")
      } else {
        Log.e(TAG, "CALL_PHONE permission not granted")
        // Try using ACTION_DIAL as fallback (requires user interaction)
        val intent = Intent(Intent.ACTION_DIAL).apply {
          data = Uri.parse("tel:$phoneNumber")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error making emergency call", e)
    }
  }
}

@Composable
fun EmergencyCallScreen(
  phoneNumber: String,
  locationInfo: LocationInfo?,
  onCall: () -> Unit,
  onCancel: () -> Unit
) {
  MaterialTheme {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.errorContainer),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        Text(
          text = "EMERGENCY",
          fontSize = 32.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
        
        Text(
          text = "Calling Emergency Contact",
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
        
        Text(
          text = phoneNumber,
          fontSize = 20.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
        
        locationInfo?.let { location ->
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Location:",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
          )
          Text(
            text = location.address ?: "${location.latitude}, ${location.longitude}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
          onClick = onCall,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
          )
        ) {
          Text("Call Now", fontSize = 18.sp)
        }
        
        OutlinedButton(
          onClick = onCancel,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Cancel")
        }
      }
    }
  }
}
