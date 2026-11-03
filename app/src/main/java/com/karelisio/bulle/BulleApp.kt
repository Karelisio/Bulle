package com.karelisio.bulle

import android.app.Application
import com.karelisio.bulle.system.Reliability

class BulleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reliability.scheduleWatchdog(this)
    }
}
