package com.karelisio.bulle.modules.live

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Détection des activités en cours à partir de vraies notifications. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveParserTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun builder() = Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Titre")
        .setContentText("Texte")

    private fun intent(id: Int) =
        PendingIntent.getBroadcast(context, id, Intent("test.$id"), PendingIntent.FLAG_IMMUTABLE)

    private fun parse(n: Notification) = LiveParser.parse("k", "com.example", n)

    @Test
    fun countdownTimer() {
        val end = System.currentTimeMillis() + 300_000
        val a = parse(builder().setOngoing(true).setUsesChronometer(true).setChronometerCountDown(true).setWhen(end).build())
        assertNotNull(a)
        assertEquals(LiveKind.TIMER, a!!.kind)
        assertEquals(LiveChrono(end, countDown = true), a.chrono)
        assertEquals(60_000, a.chrono!!.valueAt(end - 60_000))
    }

    @Test
    fun stopwatch() {
        val start = System.currentTimeMillis() - 10_000
        val a = parse(builder().setOngoing(true).setUsesChronometer(true).setWhen(start).build())
        assertEquals(LiveKind.STOPWATCH, a?.kind)
        assertEquals(10_000, a!!.chrono!!.valueAt(start + 10_000))
    }

    @Test
    fun downloadProgress() {
        val a = parse(builder().setOngoing(true).setProgress(200, 50, false).build())
        assertEquals(LiveKind.PROGRESS, a?.kind)
        assertEquals(0.25f, a!!.progress!!.fraction, 0.001f)
        assertTrue(a.genericProgress)
    }

    @Test
    fun navigationWithoutNumbers() {
        val a = parse(builder().setOngoing(true).setCategory(Notification.CATEGORY_NAVIGATION).build())
        assertEquals(LiveKind.NAVIGATION, a?.kind)
        assertFalse(a!!.genericProgress)
    }

    @Test
    fun deliveryWithProgressAndCategoryIsNotGeneric() {
        val a = parse(
            builder().setOngoing(true).setCategory(Notification.CATEGORY_LOCATION_SHARING).setProgress(4, 2, false).build(),
        )
        assertEquals(LiveKind.PROGRESS, a?.kind)
        assertFalse(a!!.genericProgress)
    }

    @Test
    fun excludesMediaCallsAndSummaries() {
        assertNull(parse(builder().setOngoing(true).setProgress(10, 5, false).setStyle(Notification.MediaStyle()).build()))
        assertNull(parse(builder().setOngoing(true).setUsesChronometer(true).setCategory(Notification.CATEGORY_CALL).build()))
        assertNull(
            parse(builder().setOngoing(true).setProgress(10, 5, false).setGroup("g").setGroupSummary(true).build()),
        )
    }

    @Test
    fun alarmsAreLeftToTheAlarmModuleButRunningTimersStay() {
        val ringing = builder().setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(intent(1), true).build()
        assertNull(parse(ringing))
        val timer = builder().setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(System.currentTimeMillis() + 60_000).build()
        assertEquals(LiveKind.TIMER, parse(timer)?.kind)
    }

    @Test
    fun silentForegroundServiceIsIgnored() {
        assertNull(parse(builder().setOngoing(true).build()))
        // Chrono sans « ongoing » : pas une activité en cours.
        assertNull(parse(builder().setUsesChronometer(true).setWhen(System.currentTimeMillis()).build()))
    }

    @Test
    fun indeterminateProgressNeedsContext() {
        assertNull(parse(builder().setOngoing(true).setProgress(0, 0, true).build()))
        val a = parse(builder().setOngoing(true).setCategory(Notification.CATEGORY_PROGRESS).setProgress(0, 0, true).build())
        assertEquals(true, a?.progress?.indeterminate)
    }

    @Test
    fun actionsWithoutRemoteInput() {
        val reply = Notification.Action.Builder(null, "Répondre", intent(2))
            .addRemoteInput(RemoteInput.Builder("txt").build()).build()
        val stop = Notification.Action.Builder(null, "Arrêter", intent(3)).build()
        val a = parse(builder().setOngoing(true).setUsesChronometer(true).setActions(reply, stop).build())
        assertEquals(listOf("Arrêter"), a?.actions?.map { it.title })
    }

    @Test
    fun formatClock() {
        assertEquals("0:00", LiveFormat.clock(0))
        assertEquals("1:05", LiveFormat.clock(65_999))
        assertEquals("59:59", LiveFormat.clock(3_599_000))
        assertEquals("1:00:00", LiveFormat.clock(3_600_000))
        assertEquals("−0:05", LiveFormat.clock(-5_000))
        assertEquals(42, LiveFormat.percent(0.425f))
    }
}
