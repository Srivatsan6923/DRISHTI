
package com.google.ai.edge.gallery.ui.llmchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ai.edge.gallery.data.DataStoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Fall Detection Observer that monitors accelerometer and gyroscope data to detect falls
 * equivalent to a person falling. A fall is detected when there's a sudden change in
 * acceleration followed by impact, similar to how a person would fall.
 */
class FallDetectionObserver(
  context: Context,
  private val dataStoreRepository: DataStoreRepository,
  private val onFallDetected: () -> Unit
) : DefaultLifecycleObserver, SensorEventListener {
  
  companion object {
    private const val TAG = "FallDetectionObserver"
  }
  
  private val context: Context = context.applicationContext
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
  private val locationManager = LocationManager(context)
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  
  // Fall detection parameters
  private var lastAcceleration = 0.0f
  private var lastGyroMagnitude = 0.0f
  private var fallStartTime = 0L
  private var isFreeFalling = false
  private var lastUpdateTime = 0L
  
  // Thresholds for fall detection (very sensitive for testing)
  private val freeFallThreshold = 8.0f // Below this indicates free fall (normal gravity ~9.8)
  private val impactThreshold = 12.0f // Above this indicates impact (very sensitive)
  private val gyroThreshold = 2.0f // Rotation indicating tumbling (very sensitive)
  private val fallDurationMin = 200L // Minimum fall duration in ms (very short)
  private val fallDurationMax = 1000L // Maximum fall duration in ms (shorter window)
  
  override fun onResume(owner: LifecycleOwner) {
    super.onResume(owner)
        accelerometer?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
          } ?: Log.w(TAG, "onResume: Accelerometer not available")
    
    gyroscope?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
          } ?: Log.w(TAG, "onResume: Gyroscope not available")
  }
  
  override fun onPause(owner: LifecycleOwner) {
    super.onPause(owner)
        sensorManager.unregisterListener(this)
  }
  
  override fun onSensorChanged(event: SensorEvent?) {
    if (event == null) {
      Log.w(TAG, "onSensorChanged: Received null sensor event")
      return
    }
    
    val currentTime = System.currentTimeMillis()
    
    when (event.sensor?.type) {
      Sensor.TYPE_ACCELEROMETER -> {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        // Calculate total acceleration magnitude
        val acceleration = sqrt(x * x + y * y + z * z)
        
        detectFallPattern(acceleration, currentTime)
        lastAcceleration = acceleration
      }
      
      Sensor.TYPE_GYROSCOPE -> {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        // Calculate gyroscope magnitude (rotation)
        val gyroMagnitude = sqrt(x * x + y * y + z * z)
         
        lastGyroMagnitude = gyroMagnitude
      }
      
      else -> {
              }
    }
    
    lastUpdateTime = currentTime
  }
  
  private fun detectFallPattern(acceleration: Float, currentTime: Long) {
    // Log current state every 500ms for debugging
    if (currentTime - lastUpdateTime > 500) {
          }
    
    // Phase 1: Detect start of free fall (low acceleration)
    if (!isFreeFalling && acceleration < freeFallThreshold) {
      isFreeFalling = true
      fallStartTime = currentTime
      Log.w(TAG, "🆘 FREE FALL STARTED: acceleration=$acceleration < threshold=$freeFallThreshold at time=$currentTime")
      return
    }
    
    // Phase 2: Detect impact after free fall (high acceleration + rotation)
    if (isFreeFalling) {
      val fallDuration = currentTime - fallStartTime
      
      // Check if we have impact with rotation (indicating tumbling like a person)
      val hasImpact = acceleration > impactThreshold
      val hasRotation = lastGyroMagnitude > gyroThreshold
      val validDuration = fallDuration in fallDurationMin..fallDurationMax
      
                              
      if (hasImpact && hasRotation && validDuration) {
        // Fall detected! Reset state and trigger callback
        isFreeFalling = false
        fallStartTime = 0L
        Log.w(TAG, "🚨 FALL DETECTED! Duration=${fallDuration}ms, Impact=${acceleration}m/s², Rotation=${lastGyroMagnitude}rad/s")
        handleFallDetected()
        onFallDetected()
      } else if (fallDuration > fallDurationMax) {
        // Too long, probably not a fall, reset
        isFreeFalling = false
        fallStartTime = 0L
        Log.i(TAG, "Fall timeout: duration=${fallDuration}ms > max=${fallDurationMax}ms - resetting state")
      }
    }
    
    // Reset if acceleration returns to normal without impact
    if (isFreeFalling && acceleration > freeFallThreshold && acceleration < impactThreshold) {
      val fallDuration = currentTime - fallStartTime
      if (fallDuration > 100L) { // Give some time for the fall to develop
        isFreeFalling = false
        fallStartTime = 0L
        Log.i(TAG, "False alarm: acceleration returned to normal without impact after ${fallDuration}ms - resetting state")
      }
    }
  }
  
  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
      }
  
  private fun handleFallDetected() {
    serviceScope.launch {
      try {
        val emergencyContact = dataStoreRepository.readEmergencyContact()
        
        if (emergencyContact.isNotEmpty()) {
          Log.i(TAG, "Emergency contact found: $emergencyContact")
          
          // Get location
          var locationInfo: LocationInfo? = null
          withContext(Dispatchers.IO) {
            var attempts = 0
            while (locationInfo == null && attempts < 10) {
              locationInfo = locationManager.getCurrentLocation()
              if (locationInfo == null) {
                delay(500)
                attempts++
              }
            }
          }
          
          // Send SMS with location if available
          val finalLocationInfo = locationInfo
          if (finalLocationInfo != null) {
            Log.i(TAG, "Sending SMS with location to $emergencyContact")
            sendLocationSMS(emergencyContact, finalLocationInfo)
          } else {
            Log.w(TAG, "Location not available for SMS, sending SMS without location")
            sendSMSWithoutLocation(emergencyContact)
          }
        } else {
          Log.e(TAG, "No emergency contact configured - cannot send SMS")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error handling fall detection", e)
      }
    }
  }
  
  private fun sendLocationSMS(phoneNumber: String, locationInfo: LocationInfo) {
    serviceScope.launch(Dispatchers.IO) {
      try {
        if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          @Suppress("DEPRECATION")
          val smsManager = SmsManager.getDefault()
          val message = locationManager.formatLocationForSMS(locationInfo)
          
          Log.i(TAG, "Attempting to send SMS to $phoneNumber")
          Log.i(TAG, "SMS content: $message")
          
          // Split message if too long (SMS limit is 160 chars per part, but Android handles this)
          val parts = smsManager.divideMessage(message)
          
          if (parts.size > 1) {
            // Send multipart SMS
            val sentIntents = ArrayList<android.app.PendingIntent>()
            val deliveryIntents = ArrayList<android.app.PendingIntent>()
            
            smsManager.sendMultipartTextMessage(
              phoneNumber,
              null,
              parts,
              sentIntents,
              deliveryIntents
            )
            Log.i(TAG, "Multipart SMS sent to $phoneNumber (${parts.size} parts)")
          } else {
            // Send single SMS
            val sentIntent = android.app.PendingIntent.getBroadcast(
              context,
              0,
              Intent("SMS_SENT"),
              android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val deliveryIntent = android.app.PendingIntent.getBroadcast(
              context,
              0,
              Intent("SMS_DELIVERED"),
              android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, deliveryIntent)
            Log.i(TAG, "Single SMS sent to $phoneNumber")
          }
          
          Log.i(TAG, "✅ SMS successfully sent to $phoneNumber")
        } else {
          Log.w(TAG, "❌ SEND_SMS permission not granted, cannot send location SMS")
        }
      } catch (e: SecurityException) {
        Log.e(TAG, "❌ SecurityException sending SMS - permission issue", e)
      } catch (e: Exception) {
        Log.e(TAG, "❌ Error sending location SMS", e)
        e.printStackTrace()
      }
    }
  }
  
  private fun sendSMSWithoutLocation(phoneNumber: String) {
    serviceScope.launch(Dispatchers.IO) {
      try {
        if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          @Suppress("DEPRECATION")
          val smsManager = SmsManager.getDefault()
          val message = "FALL DETECTED! A fall has been detected and the person has not responded to emergency alerts. Location unavailable. Please respond immediately."
          
          Log.i(TAG, "Attempting to send SMS (no location) to $phoneNumber")
          Log.i(TAG, "SMS content: $message")
          
          val parts = smsManager.divideMessage(message)
          
          if (parts.size > 1) {
            val sentIntents = ArrayList<android.app.PendingIntent>()
            val deliveryIntents = ArrayList<android.app.PendingIntent>()
            
            smsManager.sendMultipartTextMessage(
              phoneNumber,
              null,
              parts,
              sentIntents,
              deliveryIntents
            )
            Log.i(TAG, "Multipart SMS sent to $phoneNumber (${parts.size} parts)")
          } else {
            val sentIntent = android.app.PendingIntent.getBroadcast(
              context,
              0,
              Intent("SMS_SENT"),
              android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val deliveryIntent = android.app.PendingIntent.getBroadcast(
              context,
              0,
              Intent("SMS_DELIVERED"),
              android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, deliveryIntent)
            Log.i(TAG, "Single SMS sent to $phoneNumber")
          }
          
          Log.i(TAG, "✅ SMS successfully sent to $phoneNumber")
        } else {
          Log.w(TAG, "❌ SEND_SMS permission not granted, cannot send SMS")
        }
      } catch (e: SecurityException) {
        Log.e(TAG, "❌ SecurityException sending SMS - permission issue", e)
      } catch (e: Exception) {
        Log.e(TAG, "❌ Error sending SMS", e)
        e.printStackTrace()
      }
    }
  }
  
  init {
    Log.i(TAG, "FallDetectionObserver created with thresholds:")
    Log.i(TAG, "  - freeFallThreshold: $freeFallThreshold m/s²")
    Log.i(TAG, "  - impactThreshold: $impactThreshold m/s²")
    Log.i(TAG, "  - gyroThreshold: $gyroThreshold rad/s")
    Log.i(TAG, "  - fallDurationMin: ${fallDurationMin}ms")
    Log.i(TAG, "  - fallDurationMax: ${fallDurationMax}ms")
    Log.i(TAG, "  - accelerometer available: ${accelerometer != null}")
    Log.i(TAG, "  - gyroscope available: ${gyroscope != null}")
  }
}