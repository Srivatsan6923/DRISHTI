
package com.fallalert.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fallalert_settings")

private val EMERGENCY_CONTACT_KEY = stringPreferencesKey("emergency_contact")

class DataStoreRepository(private val context: Context) {
  
  suspend fun saveEmergencyContact(phoneNumber: String) {
    // Normalize phone number by removing spaces, dashes, and parentheses
    val normalized = phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), "")
    context.dataStore.edit { preferences ->
      preferences[EMERGENCY_CONTACT_KEY] = normalized
    }
  }
  
  suspend fun readEmergencyContact(): String {
    val flow: Flow<Preferences> = context.dataStore.data
    val preferences = flow.first()
    val contact = preferences[EMERGENCY_CONTACT_KEY] ?: ""
    // Normalize phone number
    return if (contact.isEmpty()) {
      "" // Return empty if not set
    } else {
      contact.replace(Regex("[\\s\\-\\(\\)]"), "")
    }
  }
}
