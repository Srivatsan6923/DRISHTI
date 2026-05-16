
package com.fallalert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
  
  companion object {
    private const val TAG = "BootReceiver"
  }
  
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
        intent.action == "android.intent.action.QUICKBOOT_POWERON") {
      Log.d(TAG, "Boot completed - starting fall detection service")
      
      val serviceIntent = Intent(context, FallDetectionService::class.java).apply {
        action = "ACTION_START"
      }
      
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
    }
  }
}
