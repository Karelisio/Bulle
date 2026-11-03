package com.karelisio.bulle.modules.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.modules.live.LiveParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Détection des appels et formatage de la durée. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CallParserTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun intent(id: Int) =
        PendingIntent.getBroadcast(context, id, Intent("test.$id"), PendingIntent.FLAG_IMMUTABLE)

    private fun builder() = Notification.Builder(context, "calls").setSmallIcon(android.R.drawable.ic_menu_call)

    private val alice = Person.Builder().setName("Alice Martin").build()

    @Test
    fun callStyleIncoming() {
        val answer = intent(1)
        val decline = intent(2)
        val n = builder().setStyle(Notification.CallStyle.forIncomingCall(alice, decline, answer)).build()
        val c = CallParser.parse("k", "com.dialer", n)
        assertNotNull(c)
        assertEquals(CallState.INCOMING, c!!.state)
        assertEquals("Alice Martin", c.callerName)
        assertEquals(answer, c.answerIntent)
        assertEquals(decline, c.declineIntent)
        // Le module « live » ne doit pas reprendre un appel.
        assertNull(LiveParser.parse("k", "com.dialer", n))
    }

    @Test
    fun callStyleOngoing() {
        val hangUp = intent(3)
        val start = System.currentTimeMillis() - 42_000
        val n = builder().setWhen(start).setUsesChronometer(true)
            .setStyle(Notification.CallStyle.forOngoingCall(alice, hangUp)).build()
        val c = CallParser.parse("k", "com.dialer", n)
        assertEquals(CallState.ONGOING, c?.state)
        assertEquals(hangUp, c!!.hangUpIntent)
        assertEquals(start, c.startWallMillis)
    }

    @Test
    fun callStyleScreening() {
        val n = builder().setStyle(Notification.CallStyle.forScreeningCall(alice, intent(4), intent(5))).build()
        assertEquals(CallState.SCREENING, CallParser.parse("k", "com.dialer", n)?.state)
    }

    @Test
    fun fallbackOnCategoryAndActionLabels() {
        val answer = intent(6)
        val decline = intent(7)
        val n = builder().setCategory(Notification.CATEGORY_CALL).setOngoing(true).setContentTitle("Bob")
            .addAction(Notification.Action.Builder(null, "Refuser", decline).build())
            .addAction(Notification.Action.Builder(null, "Répondre", answer).build())
            .build()
        val c = CallParser.parse("k", "com.voip", n)
        assertEquals(CallState.INCOMING, c?.state)
        assertEquals("Bob", c!!.callerName)
        assertEquals(answer, c.answerIntent)
        assertEquals(decline, c.declineIntent)

        val hang = intent(8)
        val ongoing = builder().setCategory(Notification.CATEGORY_CALL).setOngoing(true).setContentTitle("Bob")
            .setUsesChronometer(true).setWhen(System.currentTimeMillis() - 5_000)
            .addAction(Notification.Action.Builder(null, "Raccrocher", hang).build())
            .build()
        val o = CallParser.parse("k", "com.voip", ongoing)
        assertEquals(CallState.ONGOING, o?.state)
        assertEquals(hang, o!!.hangUpIntent)
    }

    @Test
    fun notACall() {
        assertNull(CallParser.parse("k", "x", builder().setContentTitle("Hello").setOngoing(true).build()))
        // Catégorie « appel » sans action, ni plein écran, ni « en cours » : rien à afficher.
        assertNull(CallParser.parse("k", "x", builder().setCategory(Notification.CATEGORY_CALL).setContentTitle("Manqué").build()))
    }

    @Test
    fun durationFormat() {
        assertEquals("0:00", CallParser.formatDuration(0))
        assertEquals("0:00", CallParser.formatDuration(-3_000))
        assertEquals("0:09", CallParser.formatDuration(9_999))
        assertEquals("12:34", CallParser.formatDuration(754_000))
        assertEquals("1:00:00", CallParser.formatDuration(3_600_000))
        assertEquals("2:03:04", CallParser.formatDuration(7_384_000))
    }

    @Test
    fun initials() {
        assertEquals("AM", CallParser.initials("Alice Martin"))
        assertEquals("AD", CallParser.initials("alice de dupont"))
        assertEquals("É", CallParser.initials("Émile"))
        assertNull(CallParser.initials("+33 6 12 34 56 78"))
        assertNull(CallParser.initials(""))
        assertTrue(CallParser.initials("  Zoé  ") == "Z")
    }
}
