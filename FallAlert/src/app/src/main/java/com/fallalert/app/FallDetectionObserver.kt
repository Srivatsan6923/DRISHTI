
package com.fallalert.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Fall Detection Observer that monitors accelerometer and gyroscope data to detect falls
 * equivalent to a person falling. A fall is detected when there's a sudden change in
 * acceleration followed by impact, similar to how a person would fall.
 */
class FallDetectionObserver(
  context: Context,
  private val onFallDetected: () -> Unit
) : SensorEventListener {
  
  companion object {
    private const val TAG = "FallDetectionObserver"
  }
  
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
  
  // Fall detection parameters
  private var lastAcceleration = 0.0f
  private var lastGyroMagnitude = 0.0f
  private var fallStartTime = 0L
  private var isFreeFalling = false
  private var lastUpdateTime = 0L
  
  // Thresholds for fall detection
  private val freeFallThreshold = 8.0f // Below this indicates free fall (normal gravity ~9.8)
  private val impactThreshold = 12.0f // Above this indicates impact
  private val gyroThreshold = 2.0f // Rotation indicating tumbling
  private val fallDurationMin = 200L // Minimum fall duration in ms
  private val fallDurationMax = 1000L // Maximum fall duration in ms
  
  fun start() {
    accelerometer?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
    } ?: Log.w(TAG, "Accelerometer not available")
    
    gyroscope?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
    } ?: Log.w(TAG, "Gyroscope not available")
    
    Log.i(TAG, "FallDetectionObserver started")
  }
  
  fun stop() {
    sensorManager.unregisterListener(this)
    Log.i(TAG, "FallDetectionObserver stopped")
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
    }
    
    lastUpdateTime = currentTime
  }
  
  private fun detectFallPattern(acceleration: Float, currentTime: Long) {
    // Phase 1: Detect start of free fall (low acceleration)
    if (!isFreeFalling && acceleration < freeFallThreshold) {
      isFreeFalling = true
      fallStartTime = currentTime
      Log.w(TAG, "🆘 FREE FALL STARTED: acceleration=$acceleration < threshold=$freeFallThreshold")
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
    // Not used
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
