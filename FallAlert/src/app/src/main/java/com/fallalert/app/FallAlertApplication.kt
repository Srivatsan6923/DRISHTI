
package com.fallalert.app

import android.app.Application

class FallAlertApplication : Application() {
  
  val dataStoreRepository: DataStoreRepository by lazy {
    DataStoreRepository(this)
  }
  
  override fun onCreate() {
    super.onCreate()
  }
}
