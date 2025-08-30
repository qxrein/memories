package com.rein.memories

import android.app.Application
import androidx.work.Configuration

class MemoriesApplication : Application(), Configuration.Provider {
    
    override fun onCreate() {
        super.onCreate()
        // WorkManager is automatically initialized when using Configuration.Provider
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
} 