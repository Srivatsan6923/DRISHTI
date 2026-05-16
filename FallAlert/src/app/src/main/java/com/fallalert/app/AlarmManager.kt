
package com.fallalert.app

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "AlarmManager"
private const val ALARM_DURATION_MS = 12_000L // 12 seconds countdown before calling emergency
private const val VOLUME_UPDATE_INTERVAL_MS = 100L
private const val MIN_VOLUME = 0.5f
private const val MAX_VOLUME = 1.0f

class AlarmManager(
  private val context: Context,
  private val coroutineScope: CoroutineScope,
  private val onTimeout: () -> Unit
) {
  private var mediaPlayer: MediaPlayer? = null
  private var volumeUpdateJob: Job? = null
  private var isPlaying = false
  private var wasManuallyStopped = false
  
  fun startAlarm() {
    if (isPlaying) {
      Log.d(TAG, "Alarm already playing, ignoring start request")
      return
    }
    
    try {
      stopAlarm()
      
      // Load the alarm sound from assets
      val assetFileDescriptor: AssetFileDescriptor? = try {
        context.assets.openFd("fall-song.mp3")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to open fall-song.mp3 from assets", e)
        null
      }
      
      if (assetFileDescriptor == null) {
        Log.e(TAG, "Could not load fall-song.mp3 from assets")
        return
      }
      
      // Create a MediaPlayer with the asset file
      val player = MediaPlayer().apply {
        setDataSource(
          assetFileDescriptor.fileDescriptor,
          assetFileDescriptor.startOffset,
          assetFileDescriptor.length
        )
        assetFileDescriptor.close()
        
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()
        )
        
        isLooping = true
        prepare()
        setVolume(MAX_VOLUME, MAX_VOLUME) // Start at max volume for emergency
        start()
      }
      
      mediaPlayer = player
      isPlaying = true
      wasManuallyStopped = false
      
      // Start timeout countdown
      val startTime = System.currentTimeMillis()
      volumeUpdateJob = coroutineScope.launch(Dispatchers.Main) {
        while (isActive && isPlaying) {
          val elapsed = System.currentTimeMillis() - startTime
          
          if (elapsed >= ALARM_DURATION_MS) {
            // Alarm timed out - trigger emergency call if not manually stopped
            if (!wasManuallyStopped && isPlaying) {
              Log.w(TAG, "Alarm timed out - triggering emergency call")
              onTimeout()
            }
            break
          }
          
          delay(VOLUME_UPDATE_INTERVAL_MS)
        }
      }
      
      Log.d(TAG, "Alarm started - will trigger emergency call in ${ALARM_DURATION_MS / 1000} seconds if not dismissed")
      Log.i(TAG, "Fall detection countdown started: ${ALARM_DURATION_MS / 1000} seconds")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start alarm", e)
      isPlaying = false
      mediaPlayer?.release()
      mediaPlayer = null
    }
  }
  
  fun stopAlarm() {
    try {
      wasManuallyStopped = true
      volumeUpdateJob?.cancel()
      volumeUpdateJob = null
      
      mediaPlayer?.let { player ->
        if (player.isPlaying) {
          player.stop()
        }
        player.release()
      }
      mediaPlayer = null
      isPlaying = false
      Log.d(TAG, "Alarm stopped")
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping alarm", e)
    }
  }
  
  fun isAlarmPlaying(): Boolean = isPlaying
}
