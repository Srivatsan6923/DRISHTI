
package com.fallalert.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocationInfo(
  val latitude: Double,
  val longitude: Double,
  val address: String?,
  val accuracy: Float?
)

class LocationManager(private val context: Context) {
  
  companion object {
    private const val TAG = "LocationManager"
  }
  
  private val fusedLocationClient: FusedLocationProviderClient =
    LocationServices.getFusedLocationProviderClient(context)
  
  suspend fun getCurrentLocation(): LocationInfo? {
    // Check permissions
    if (!hasLocationPermission()) {
      Log.w(TAG, "Location permission not granted")
      return null
    }
    
    try {
      // Get last known location first (fast)
      val lastLocationTask = fusedLocationClient.lastLocation
      val lastLocation: Location? = lastLocationTask.await()
      
      if (lastLocation != null && isLocationRecent(lastLocation)) {
        Log.d(TAG, "Using last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
        return createLocationInfo(lastLocation)
      }
      
      // Request current location with high accuracy
      val cancellationToken = CancellationTokenSource()
      
      val locationTask = fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationToken.token
      )
      
      val location: Location? = locationTask.await()
      
      if (location != null) {
        Log.d(TAG, "Got current location: ${location.latitude}, ${location.longitude}")
        return createLocationInfo(location)
      } else {
        Log.w(TAG, "Failed to get current location")
        // Fallback to last known location even if old
        if (lastLocation != null) {
          Log.d(TAG, "Using last known location as fallback")
          return createLocationInfo(lastLocation)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error getting location", e)
    }
    
    return null
  }
  
  private fun hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }
  
  private fun isLocationRecent(location: Location): Boolean {
    val age = System.currentTimeMillis() - location.time
    return age < 60000 // Less than 1 minute old
  }
  
  private suspend fun createLocationInfo(location: Location): LocationInfo {
    val address = getAddressFromLocation(location.latitude, location.longitude)
    return LocationInfo(
      latitude = location.latitude,
      longitude = location.longitude,
      address = address,
      accuracy = location.accuracy
    )
  }
  
  private suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? {
    return withContext(Dispatchers.IO) {
      try {
        if (Geocoder.isPresent()) {
          val geocoder = Geocoder(context, Locale.getDefault())
          val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
          addresses?.firstOrNull()?.let { address ->
            val addressParts = mutableListOf<String>()
            
            address.getAddressLine(0)?.let { addressParts.add(it) }
            address.locality?.let { addressParts.add(it) }
            address.adminArea?.let { addressParts.add(it) }
            address.countryName?.let { addressParts.add(it) }
            
            if (addressParts.isEmpty()) {
              "${address.latitude}, ${address.longitude}"
            } else {
              addressParts.joinToString(", ")
            }
          } ?: "${latitude}, ${longitude}"
        } else {
          "${latitude}, ${longitude}"
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error getting address", e)
        "${latitude}, ${longitude}"
      }
    }
  }
  
  fun formatLocationForSMS(locationInfo: LocationInfo): String {
    val googleMapsLink = "https://maps.google.com/?q=${locationInfo.latitude},${locationInfo.longitude}"
    return if (locationInfo.address != null) {
      "FALL DETECTED! A fall has been detected and the person has not responded to emergency alerts.\n\nLocation: ${locationInfo.address}\nCoordinates: ${locationInfo.latitude}, ${locationInfo.longitude}\nMap: $googleMapsLink\n\nPlease respond immediately."
    } else {
      "FALL DETECTED! A fall has been detected and the person has not responded to emergency alerts.\n\nCoordinates: ${locationInfo.latitude}, ${locationInfo.longitude}\nMap: $googleMapsLink\n\nPlease respond immediately."
    }
  }
}
