package com.karelisio.bulle

import android.app.Application
import androidx.work.Configuration
import com.karelisio.bulle.core.backup.BackupScheduler
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.system.Reliability

class BulleApp : Application(), Configuration.Provider {

    // WorkManager initialisé à la demande (initialiseur App Startup retiré du manifeste).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        com.karelisio.bulle.system.CrashLog.install(this)
        Modules.registerAll()
        Reliability.scheduleWatchdog(this)
        BackupScheduler.schedule(this)
    }
}
