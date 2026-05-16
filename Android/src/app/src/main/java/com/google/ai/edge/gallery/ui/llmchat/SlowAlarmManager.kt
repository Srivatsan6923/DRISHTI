
package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.DataStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "SlowAlarmManager"
private const val ALARM_DURATION_MS = 10_000L // 3 minutes
private const val VOLUME_UPDATE_INTERVAL_MS = 100L // Update volume every 100ms
private const val MIN_VOLUME = 0.1f
private const val MAX_VOLUME = 0.4f

/**
 * Slow Alarm Manager that plays an alarm sound and gradually increases volume over 3 minutes.
 * If the alarm runs until timeout, it will call the emergency contact.
 */
@Composable
fun rememberSlowAlarmManager(
    context: Context,
    dataStoreRepository: DataStoreRepository
): SlowAlarmManagerState {
    var isPlaying by remember { mutableStateOf(false) }
    var currentVolume by remember { mutableStateOf(0.0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var volumeUpdateJob by remember { mutableStateOf<Job?>(null) }
    var wasManuallyStopped by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun callEmergencyContact(phoneNumber: String) {
        try {
            // Check if we have CALL_PHONE permission
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Emergency contact called: $phoneNumber")
            } else {
                Log.w(TAG, "CALL_PHONE permission not granted, cannot call emergency contact")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling emergency contact", e)
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
            currentVolume = 0.0f
            Log.d(TAG, "Alarm stopped manually")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm", e)
        }
    }

    fun startAlarm() {
        if (isPlaying) {
            Log.d(TAG, "Alarm already playing, ignoring start request")
            return
        }

        try {
            // Stop any existing alarm
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
                // Start at low volume and gradually increase
                setVolume(MIN_VOLUME, MIN_VOLUME)
                start()
            }

            mediaPlayer = player
            isPlaying = true
            currentVolume = MIN_VOLUME // Start at low volume

            // Start volume ramp-up and timeout countdown
            val startTime = System.currentTimeMillis()
            wasManuallyStopped = false
            volumeUpdateJob = coroutineScope.launch(Dispatchers.Main) {
                while (isActive && isPlaying) {
                    val elapsed = System.currentTimeMillis() - startTime
                    
                    if (elapsed >= ALARM_DURATION_MS) {
                        // Alarm timed out - call emergency contact if not manually stopped
                        if (!wasManuallyStopped && isPlaying) {
                            val emergencyContact = dataStoreRepository.readEmergencyContact()
                            if (emergencyContact.isNotEmpty()) {
                                Log.w(TAG, "Alarm timed out - calling emergency contact: $emergencyContact")
                                callEmergencyContact(emergencyContact)
                            } else {
                                Log.w(TAG, "Alarm timed out but no emergency contact configured")
                            }
                        }
                        break
                    }
                    
                    // Gradually increase volume from MIN_VOLUME to MAX_VOLUME over ALARM_DURATION_MS
                    val progress = (elapsed.toFloat() / ALARM_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                    val newVolume = MIN_VOLUME + (MAX_VOLUME - MIN_VOLUME) * progress
                    
                    mediaPlayer?.setVolume(newVolume, newVolume)
                    currentVolume = newVolume
                    
                    delay(VOLUME_UPDATE_INTERVAL_MS)
                }
            }

            Log.d(TAG, "Alarm started at low volume - will gradually increase to max and call emergency contact in ${ALARM_DURATION_MS / 1000} seconds if not stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm", e)
            isPlaying = false
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }


    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            stopAlarm()
        }
    }

    return SlowAlarmManagerState(
        isPlaying = isPlaying,
        currentVolume = currentVolume,
        startAlarm = ::startAlarm,
        stopAlarm = ::stopAlarm
    )
}

/**
 * State holder for Slow Alarm Manager
 */
data class SlowAlarmManagerState(
    val isPlaying: Boolean,
    val currentVolume: Float,
    val startAlarm: () -> Unit,
    val stopAlarm: () -> Unit
)
