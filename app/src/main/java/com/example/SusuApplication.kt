package com.example

import android.app.Application

class SusuApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    com.example.service.CloudSyncWorker.schedule(this)
  }
}
