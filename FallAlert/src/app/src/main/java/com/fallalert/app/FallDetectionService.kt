
package com.fallalert.app

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FallDetectionService : Service() {
  
  companion object {
    private const val TAG = "FallDetectionService"
  }
  
  private var fallDetectionObserver: FallDetectionObserver? = null
  private var alarmManager: AlarmManager? = null
  private var notificationManager: FallAlertNotificationManager? = null
  private var locationManager: LocationManager? = null
  private var isServiceRunning = false
  private var currentFallLocation: LocationInfo? = null
  private var isFallAlertActive = false // Prevent repeated fall alerts
  private var countdownHandler: android.os.Handler? = null
  private var countdownRunnable: Runnable? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  
  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Service onCreate")
    
    notificationManager = FallAlertNotificationManager(this)
    locationManager = LocationManager(this)
    alarmManager = AlarmManager(
      context = this,
      coroutineScope = serviceScope,
      onTimeout = {
        // Alarm timed out - call emergency contact
        callEmergencyContact()
      }
    )
  }
  
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      "ACTION_START" -> {
        startFallDetection()
      }
      "ACTION_STOP" -> {
        stopFallDetection()
        stopSelf()
      }
      "ACTION_DISMISS_FALL" -> {
        dismissFallAlert()
      }
      else -> {
        // Start by default
        startFallDetection()
      }
    }
    
    return START_STICKY // Restart if killed
  }
  
  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
  
  private fun startFallDetection() {
    if (isServiceRunning) {
      Log.d(TAG, "Service already running")
      return
    }
    
    Log.d(TAG, "Starting fall detection service")
    
    // Start foreground service
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
      this,
      0,
      intent,
      android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      android.app.Notification.Builder(this, "fall_detection_channel")
        .setContentTitle(getString(R.string.service_running))
        .setContentText(getString(R.string.service_active))
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()
    } else {
      @Suppress("DEPRECATION")
      android.app.Notification.Builder(this)
        .setContentTitle(getString(R.string.service_running))
        .setContentText(getString(R.string.service_active))
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()
    }
    
    // Start foreground service with health type for Android 14+
    val hasActivityRecognition = ContextCompat.checkSelfPermission(
      this,
      android.Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      if (hasActivityRecognition) {
        try {
          startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } catch (e: SecurityException) {
          Log.e(TAG, "Failed to start foreground service with health type", e)
          try {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            Log.d(TAG, "Started with special use type as fallback")
          } catch (e2: SecurityException) {
            Log.e(TAG, "Failed to start with special use type", e2)
            throw e2
          }
        }
      } else {
        try {
          startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
          Log.d(TAG, "Started with special use type (no ACTIVITY_RECOGNITION permission)")
        } catch (e: SecurityException) {
          Log.e(TAG, "Failed to start foreground service - permission required", e)
          throw e
        }
      }
    } else {
      startForeground(1, notification)
    }
    notificationManager?.showServiceNotification()
    
    // Initialize fall detection
    fallDetectionObserver = FallDetectionObserver(this) {
      onFallDetected()
    }
    fallDetectionObserver?.start()
    
    isServiceRunning = true
    Log.d(TAG, "Fall detection service started")
  }
  
  private fun stopFallDetection() {
    if (!isServiceRunning) {
      return
    }
    
    Log.d(TAG, "Stopping fall detection service")
    
    // Stop countdown
    countdownRunnable?.let {
      countdownHandler?.removeCallbacks(it)
    }
    countdownHandler = null
    countdownRunnable = null
    
    fallDetectionObserver?.stop()
    fallDetectionObserver = null
    alarmManager?.stopAlarm()
    notificationManager?.cancelServiceNotification()
    notificationManager?.cancelFallNotification()
    
    isFallAlertActive = false
    isServiceRunning = false
    Log.d(TAG, "Fall detection service stopped")
  }
  
  private fun onFallDetected() {
    // Prevent repeated fall alerts
    if (isFallAlertActive) {
      Log.d(TAG, "Fall alert already active, ignoring new detection")
      return
    }
    
    Log.w(TAG, "🚨 FALL DETECTED - Event logged at ${System.currentTimeMillis()}")
    
    // Mark fall alert as active
    isFallAlertActive = true
    
    // Ensure service is running as foreground
    if (!isServiceRunning) {
      Log.d(TAG, "Service not running, starting fall detection service")
      startFallDetection()
    }
    
    // Get GPS location immediately (non-blocking)
    serviceScope.launch(Dispatchers.IO) {
      try {
        currentFallLocation = locationManager?.getCurrentLocation()
        if (currentFallLocation != null) {
          Log.i(TAG, "Location obtained: ${currentFallLocation?.latitude}, ${currentFallLocation?.longitude}")
          Log.i(TAG, "Address: ${currentFallLocation?.address}")
        } else {
          Log.w(TAG, "Could not obtain location")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error getting location", e)
      }
    }
    
    // Log the fall detection event
    logFallEvent()
    
    // Show high-priority notification with countdown
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    countdownHandler = handler
    var secondsRemaining = 12
    
    countdownRunnable = object : Runnable {
      override fun run() {
        if (secondsRemaining > 0 && isFallAlertActive) {
          notificationManager?.updateFallNotificationCountdown(secondsRemaining)
          secondsRemaining--
          handler.postDelayed(this, 1000)
        }
      }
    }
    
    notificationManager?.updateFallNotificationCountdown(secondsRemaining)
    handler.post(countdownRunnable!!)
    
    // Start alarm with countdown
    alarmManager?.startAlarm()
  }
  
  private fun logFallEvent() {
    val timestamp = System.currentTimeMillis()
    val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    Log.i(TAG, "========================================")
    Log.i(TAG, "FALL DETECTION EVENT LOGGED")
    Log.i(TAG, "Timestamp: $timestamp")
    Log.i(TAG, "DateTime: $dateTime")
    Log.i(TAG, "Service Running: $isServiceRunning")
    Log.i(TAG, "========================================")
  }
  
  private fun dismissFallAlert() {
    val timestamp = System.currentTimeMillis()
    val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    Log.i(TAG, "========================================")
    Log.i(TAG, "FALL ALERT DISMISSED BY USER")
    Log.i(TAG, "Timestamp: $timestamp")
    Log.i(TAG, "DateTime: $dateTime")
    Log.i(TAG, "========================================")
    
    // Stop countdown
    countdownRunnable?.let {
      countdownHandler?.removeCallbacks(it)
    }
    countdownHandler = null
    countdownRunnable = null
    
    // Stop alarm
    alarmManager?.stopAlarm()
    
    // Cancel notification
    notificationManager?.cancelFallNotification()
    
    // Reset fall alert flag after a delay to allow new detections
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
      isFallAlertActive = false
      Log.d(TAG, "Fall alert flag reset - ready for new detections")
    }, 5000) // Wait 5 seconds before allowing new fall detection
  }
  
  private fun callEmergencyContact() {
    // Only proceed if fall alert is still active (not dismissed)
    if (!isFallAlertActive) {
      Log.d(TAG, "Fall alert was dismissed, not calling emergency contact")
      return
    }
    
    val timestamp = System.currentTimeMillis()
    val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    Log.w(TAG, "========================================")
    Log.w(TAG, "EMERGENCY CALL TRIGGERED")
    Log.w(TAG, "Timestamp: $timestamp")
    Log.w(TAG, "DateTime: $dateTime")
    Log.w(TAG, "Reason: Countdown expired - no user cancellation")
    if (currentFallLocation != null) {
      Log.w(TAG, "Location: ${currentFallLocation?.latitude}, ${currentFallLocation?.longitude}")
      Log.w(TAG, "Address: ${currentFallLocation?.address}")
    }
    Log.w(TAG, "========================================")
    
    // Stop countdown
    countdownRunnable?.let {
      countdownHandler?.removeCallbacks(it)
    }
    countdownHandler = null
    countdownRunnable = null
    
    serviceScope.launch {
      val repository = (application as FallAlertApplication).dataStoreRepository
      val emergencyContact = repository.readEmergencyContact()
      
      if (emergencyContact.isNotEmpty()) {
        Log.i(TAG, "Emergency contact found: $emergencyContact")
        
        // Wait a bit for location if still being fetched
        if (currentFallLocation == null) {
          Log.d(TAG, "Waiting for location...")
          withContext(Dispatchers.IO) {
            var attempts = 0
            while (currentFallLocation == null && attempts < 10) {
              delay(500)
              currentFallLocation = locationManager?.getCurrentLocation()
              attempts++
            }
          }
        }
        
        // Send SMS with location if available
        if (currentFallLocation != null) {
          Log.i(TAG, "Sending SMS with location to $emergencyContact")
          sendLocationSMS(emergencyContact, currentFallLocation!!)
        } else {
          Log.w(TAG, "Location not available for SMS, sending SMS without location")
          // Send SMS without location as fallback
          sendSMSWithoutLocation(emergencyContact)
        }
        
        // Use full-screen intent notification to launch EmergencyCallActivity
        // This is allowed from background services for critical notifications
        notificationManager?.showEmergencyCallNotification(emergencyContact, currentFallLocation)
        
        Log.d(TAG, "Emergency call notification shown for: $emergencyContact")
      } else {
        Log.e(TAG, "No emergency contact configured - cannot make call")
      }
    }
  }
  
  private fun sendLocationSMS(phoneNumber: String, locationInfo: LocationInfo) {
    serviceScope.launch(Dispatchers.IO) {
      try {
        if (ContextCompat.checkSelfPermission(
            this@FallDetectionService,
            android.Manifest.permission.SEND_SMS
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          val smsManager = android.telephony.SmsManager.getDefault()
          val message = locationManager?.formatLocationForSMS(locationInfo) ?: 
            "FALL DETECTED! A fall has been detected and the person has not responded. Coordinates: ${locationInfo.latitude}, ${locationInfo.longitude}"
          
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
              this@FallDetectionService,
              0,
              Intent("SMS_SENT"),
              android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val deliveryIntent = android.app.PendingIntent.getBroadcast(
              this@FallDetectionService,
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
            this@FallDetectionService,
            android.Manifest.permission.SEND_SMS
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          val smsManager = android.telephony.SmsManager.getDefault()
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
              this@FallDetectionService,
              0,
              Intent("SMS_SENT"),
              android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val deliveryIntent = android.app.PendingIntent.getBroadcast(
              this@FallDetectionService,
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
  
  override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "Service onDestroy")
    serviceScope.cancel()
    stopFallDetection()
  }
}
