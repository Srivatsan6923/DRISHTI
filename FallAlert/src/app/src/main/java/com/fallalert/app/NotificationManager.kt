
package com.fallalert.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class FallAlertNotificationManager(private val context: Context) {
  
  companion object {
    private const val CHANNEL_ID = "fall_detection_channel"
    private const val CHANNEL_NAME = "Fall Detection Alerts"
    private const val NOTIFICATION_ID_SERVICE = 1
    private const val NOTIFICATION_ID_FALL = 2
    private const val COUNTDOWN_DURATION_SECONDS = 12
  }
  
  private val notificationManager = NotificationManagerCompat.from(context)
  
  init {
    createNotificationChannel()
  }
  
  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Critical notifications for fall detection and emergency calls"
        enableLights(true)
        enableVibration(true)
        setShowBadge(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          setAllowBubbles(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          setBypassDnd(true)
        }
      }
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        channel.importance = NotificationManager.IMPORTANCE_HIGH
      }
      
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }
  
  fun showEmergencyCallNotification(phoneNumber: String, locationInfo: LocationInfo? = null) {
    val intent = Intent(context, EmergencyCallActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra("phone_number", phoneNumber)
      locationInfo?.let {
        putExtra("latitude", it.latitude)
        putExtra("longitude", it.longitude)
        putExtra("address", it.address)
      }
    }
    
    val fullScreenIntent = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(context.resources.getString(R.string.calling_emergency))
      .setContentText(phoneNumber)
      .setSmallIcon(android.R.drawable.ic_dialog_alert)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setFullScreenIntent(fullScreenIntent, true)
      .setAutoCancel(true)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setVibrate(longArrayOf(0, 1000, 500, 1000))
      .setOngoing(true)
      .build()
    
    notificationManager.notify(3, notification)
  }
  
  fun showServiceNotification() {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(context.resources.getString(R.string.service_running))
      .setContentText(context.resources.getString(R.string.service_active))
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()
    
    notificationManager.notify(NOTIFICATION_ID_SERVICE, notification)
  }
  
  fun showFallDetectedNotification(onDismiss: () -> Unit) {
    val dismissIntent = Intent(context, FallDetectionService::class.java).apply {
      action = "ACTION_DISMISS_FALL"
    }
    val dismissPendingIntent = PendingIntent.getService(
      context,
      0,
      dismissIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(context.resources.getString(R.string.fall_detected))
      .setContentText(context.resources.getString(R.string.fall_detected_message))
      .setSmallIcon(android.R.drawable.ic_dialog_alert)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setAutoCancel(true)
      .setFullScreenIntent(null, true)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        context.resources.getString(R.string.dismiss),
        dismissPendingIntent
      )
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setVibrate(longArrayOf(0, 500, 500, 500))
      .build()
    
    notificationManager.notify(NOTIFICATION_ID_FALL, notification)
  }
  
  fun showFallDetectedNotificationWithCountdown(
    onDismiss: () -> Unit,
    onCountdownUpdate: (Int) -> Unit
  ) {
    // This method is now just for initial setup
    // The actual countdown is managed in FallDetectionService
    updateFallNotificationCountdown(COUNTDOWN_DURATION_SECONDS)
  }
  
  fun updateFallNotificationCountdown(secondsRemaining: Int) {
    val dismissIntent = Intent(context, FallDetectionService::class.java).apply {
      action = "ACTION_DISMISS_FALL"
    }
    val dismissPendingIntent = PendingIntent.getService(
      context,
      0,
      dismissIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    val message = if (secondsRemaining > 0) {
      context.resources.getString(R.string.emergency_call_countdown, secondsRemaining)
    } else {
      context.resources.getString(R.string.fall_detected_message)
    }
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(context.resources.getString(R.string.fall_detected))
      .setContentText(message)
      .setSmallIcon(android.R.drawable.ic_dialog_alert)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setAutoCancel(true)
      .setFullScreenIntent(null, true)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        context.resources.getString(R.string.dismiss),
        dismissPendingIntent
      )
      .setProgress(COUNTDOWN_DURATION_SECONDS, COUNTDOWN_DURATION_SECONDS - secondsRemaining, false)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setVibrate(longArrayOf(0, 500, 500, 500))
      .setOngoing(true)
      .build()
    
    notificationManager.notify(NOTIFICATION_ID_FALL, notification)
  }
  
  fun cancelFallNotification() {
    notificationManager.cancel(NOTIFICATION_ID_FALL)
  }
  
  fun cancelServiceNotification() {
    notificationManager.cancel(NOTIFICATION_ID_SERVICE)
  }
  
  fun cancelEmergencyCallNotification() {
    notificationManager.cancel(3)
  }
}
