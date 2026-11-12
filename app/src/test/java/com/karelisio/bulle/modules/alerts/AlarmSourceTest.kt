package com.karelisio.bulle.modules.alerts

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.modules.alerts.sources.AlarmSource
import com.karelisio.bulle.modules.live.LiveKind
import com.karelisio.bulle.modules.live.LiveParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Alarme qui sonne vs minuteur qui tourne : chaque notification n'est reprise que par un module. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun builder() = Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Horloge")
        .setCategory(Notification.CATEGORY_ALARM)

    private fun intent() = PendingIntent.getBroadcast(context, 1, Intent("test.alarm"), PendingIntent.FLAG_IMMUTABLE)

    @Test
    fun ringingAlarmIsAnAlarm() {
        val ringing = builder().setOngoing(true).setFullScreenIntent(intent(), true).build()
        assertTrue(AlarmSource.isRinging(ringing))
        assertNull(LiveParser.parse("k", "com.example", ringing))
        // Une alarme qui sonne sans plein écran (notification en cours) reste une alarme.
        assertTrue(AlarmSource.isRinging(builder().setOngoing(true).build()))
    }

    @Test
    fun runningTimerIsLeftToLiveModule() {
        val timer = builder().setOngoing(true)
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(System.currentTimeMillis() + 60_000).build()
        assertFalse(AlarmSource.isRinging(timer))
        assertEquals(LiveKind.TIMER, LiveParser.parse("k", "com.example", timer)?.kind)
    }

    @Test
    fun reminderIsNotRinging() {
        assertFalse(AlarmSource.isRinging(builder().build()))
    }
}
