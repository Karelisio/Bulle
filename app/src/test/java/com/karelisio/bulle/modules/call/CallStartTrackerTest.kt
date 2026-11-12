package com.karelisio.bulle.modules.call

import com.karelisio.bulle.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Début d'appel stable même si l'appli reconstruit sa notification. */
class CallStartTrackerTest {

    @Test
    fun keepsEarliestStart() {
        val t = CallStartTracker()
        assertEquals(1_000L, t.resolve("ONGOING:k", 1_000L, nowWall = 5_000L, fallback = 4_000L))
        // Notification reconstruite : nouveau `when`, plus récent → la durée ne repart pas de zéro.
        assertEquals(1_000L, t.resolve("ONGOING:k", 3_000L, nowWall = 6_000L, fallback = 4_000L))
        // Notification sans `when` : on garde le début connu.
        assertEquals(1_000L, t.resolve("ONGOING:k", null, nowWall = 7_000L, fallback = 4_000L))
        // Un début encore plus ancien est retenu.
        assertEquals(500L, t.resolve("ONGOING:k", 500L, nowWall = 7_000L, fallback = 4_000L))
    }

    @Test
    fun fallbackAndFutureValuesIgnored() {
        val t = CallStartTracker()
        assertEquals(4_000L, t.resolve("k", null, nowWall = 5_000L, fallback = 4_000L))
        assertEquals(4_000L, t.resolve("k", 60_000L, nowWall = 5_000L, fallback = 4_000L))
        assertEquals(4_500L, t.resolve("k", 4_500L, nowWall = 5_000L, fallback = 4_000L))
    }

    @Test
    fun purgedWhenCallEnds() {
        val t = CallStartTracker()
        t.resolve("a", 1_000L, 5_000L, 0L)
        t.resolve("b", 2_000L, 5_000L, 0L)
        t.retain(setOf("b"))
        assertEquals(1, t.size)
        // Nouvel appel sous la même clé : repart de son propre début.
        assertEquals(3_000L, t.resolve("a", 3_000L, 5_000L, 0L))
    }

    private fun call(state: CallState, video: Boolean = false) = CallInfo(
        key = "k", packageName = "com.dialer", state = state, callerName = "Alice", isVideo = video,
        startWallMillis = null, answerIntent = null, declineIntent = null, hangUpIntent = null,
        contentIntent = null, color = 0, personIcon = null, largeIcon = null,
    )

    @Test
    fun compactSubtitle() {
        val status: (Int) -> String = { if (it == R.string.call_incoming) "Appel entrant" else "autre" }
        assertEquals("Appel entrant", CallContent.compactSubtitle(call(CallState.INCOMING), "Téléphone", status))
        assertEquals("Téléphone", CallContent.compactSubtitle(call(CallState.ONGOING), "Téléphone", status))
        assertNull(CallContent.compactSubtitle(call(CallState.ONGOING), null, status))
    }
}
