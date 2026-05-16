
package com.google.ai.edge.gallery.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

private const val TAG = "AGNetworkUtils"

object NetworkUtils {
  /**
   * Checks if the device has an active network connection.
   * @param context The application context
   * @return true if network is available, false otherwise
   */
  fun isNetworkAvailable(context: Context): Boolean {
    return try {
      val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      val network = connectivityManager.activeNetwork ?: return false
      val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
      
      val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      
      Log.w(TAG, "Network available: $hasInternet")
      hasInternet
    } catch (e: Exception) {
      Log.e(TAG, "Error checking network availability", e)
      false
    }
  }
}
