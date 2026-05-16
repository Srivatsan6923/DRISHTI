
package com.google.ai.edge.gallery.data

import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.proto.AccessTokenData
import com.google.ai.edge.gallery.proto.ImportedModel
import com.google.ai.edge.gallery.proto.Settings
import com.google.ai.edge.gallery.proto.Theme
import com.google.ai.edge.gallery.proto.UserData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// TODO(b/423700720): Change to async (suspend) functions
interface DataStoreRepository {
  fun saveTextInputHistory(history: List<String>)

  fun readTextInputHistory(): List<String>

  fun saveTheme(theme: Theme)

  fun readTheme(): Theme

  fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long)

  fun clearAccessTokenData()

  fun readAccessTokenData(): AccessTokenData?

  fun saveImportedModels(importedModels: List<ImportedModel>)

  fun readImportedModels(): List<ImportedModel>

  fun isTosAccepted(): Boolean

  fun acceptTos()

  fun getHasRunTinyGarden(): Boolean

  fun setHasRunTinyGarden(hasRun: Boolean)

  fun saveGeminiApiKey(apiKey: String)

  fun readGeminiApiKey(): String

  fun saveEmergencyContact(phoneNumber: String)

  fun readEmergencyContact(): String

  fun saveForceGemma(forceGemma: Boolean)

  fun readForceGemma(): Boolean
}

/** Repository for managing data using Proto DataStore. */
class DefaultDataStoreRepository(
  private val dataStore: DataStore<Settings>,
  private val userDataDataStore: DataStore<UserData>,
) : DataStoreRepository {
  override fun saveTextInputHistory(history: List<String>) {
    runBlocking {
      dataStore.updateData { settings ->
        settings.toBuilder().clearTextInputHistory().addAllTextInputHistory(history).build()
      }
    }
  }

  override fun readTextInputHistory(): List<String> {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.textInputHistoryList
    }
  }

  override fun saveTheme(theme: Theme) {
    runBlocking {
      dataStore.updateData { settings -> settings.toBuilder().setTheme(theme).build() }
    }
  }

  override fun readTheme(): Theme {
    return runBlocking {
      val settings = dataStore.data.first()
      val curTheme = settings.theme
      // Use "auto" as the default theme.
      if (curTheme == Theme.THEME_UNSPECIFIED) Theme.THEME_AUTO else curTheme
    }
  }

  override fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) {
    runBlocking {
      // Clear the entry in old data store.
      dataStore.updateData { settings ->
        settings.toBuilder().setAccessTokenData(AccessTokenData.getDefaultInstance()).build()
      }

      userDataDataStore.updateData { userData ->
        userData
          .toBuilder()
          .setAccessTokenData(
            AccessTokenData.newBuilder()
              .setAccessToken(accessToken)
              .setRefreshToken(refreshToken)
              .setExpiresAtMs(expiresAt)
              .build()
          )
          .build()
      }
    }
  }

  override fun clearAccessTokenData() {
    runBlocking {
      dataStore.updateData { settings -> settings.toBuilder().clearAccessTokenData().build() }
      userDataDataStore.updateData { userData ->
        userData.toBuilder().clearAccessTokenData().build()
      }
    }
  }

  override fun readAccessTokenData(): AccessTokenData? {
    return runBlocking {
      val userData = userDataDataStore.data.first()
      userData.accessTokenData
    }
  }

  override fun saveImportedModels(importedModels: List<ImportedModel>) {
    runBlocking {
      dataStore.updateData { settings ->
        settings.toBuilder().clearImportedModel().addAllImportedModel(importedModels).build()
      }
    }
  }

  override fun readImportedModels(): List<ImportedModel> {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.importedModelList
    }
  }

  override fun isTosAccepted(): Boolean {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.isTosAccepted
    }
  }

  override fun acceptTos() {
    runBlocking {
      dataStore.updateData { settings -> settings.toBuilder().setIsTosAccepted(true).build() }
    }
  }

  override fun getHasRunTinyGarden(): Boolean {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.hasRunTinyGarden
    }
  }

  override fun setHasRunTinyGarden(hasRun: Boolean) {
    runBlocking {
      dataStore.updateData { settings -> settings.toBuilder().setHasRunTinyGarden(hasRun).build() }
    }
  }

  override fun saveGeminiApiKey(apiKey: String) {
    runBlocking {
      dataStore.updateData { settings ->
        settings.toBuilder().setGeminiApiKey(apiKey).build()
      }
    }
  }

  override fun readGeminiApiKey(): String {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.geminiApiKey
    }
  }

  override fun saveEmergencyContact(phoneNumber: String) {
    runBlocking {
      // Normalize phone number by removing spaces, dashes, and parentheses before saving
      val normalized = phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), "")
      dataStore.updateData { settings ->
        settings.toBuilder().setEmergencyContact(normalized).build()
      }
    }
  }

  override fun readEmergencyContact(): String {
    return runBlocking {
      val settings = dataStore.data.first()
      val contact = settings.emergencyContact
      // Return default if empty, otherwise return the configured contact
      // Normalize phone number by removing spaces, dashes, and parentheses
      val normalized = if (contact.isEmpty()) {
        "+14086639097" // Default emergency contact
      } else {
        contact.replace(Regex("[\\s\\-\\(\\)]"), "")
      }
      normalized
    }
  }

  override fun saveForceGemma(forceGemma: Boolean) {
    runBlocking {
      dataStore.updateData { settings ->
        settings.toBuilder().setForceGemma(forceGemma).build()
      }
    }
  }

  override fun readForceGemma(): Boolean {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.forceGemma
    }
  }
}
