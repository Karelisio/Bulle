package com.karelisio.bulle

import android.app.Application
import androidx.work.Configuration
import com.karelisio.bulle.system.Reliability

class BulleApp : Application(), Configuration.Provider {

    // WorkManager initialisé à la demande (initialiseur App Startup retiré du manifeste).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        Reliability.scheduleWatchdog(this)
    }
}
