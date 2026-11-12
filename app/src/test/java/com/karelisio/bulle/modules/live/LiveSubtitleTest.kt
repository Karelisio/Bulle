package com.karelisio.bulle.modules.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seconde ligne compacte des activités en cours. */
class LiveSubtitleTest {

    private fun activity(kind: LiveKind, title: String?, text: String?, short: String? = null) = LiveActivity(
        key = "k", packageName = "p", kind = kind, title = title, text = text, subText = null, shortText = short,
        progress = null, chrono = null, color = 0, promoted = false, genericProgress = false,
        actions = emptyList(), contentIntent = null,
    )

    @Test
    fun navigationShowsNextStep() {
        assertEquals("Tourner à droite rue Victor-Hugo", LiveFormat.compactSubtitle(activity(LiveKind.NAVIGATION, "200 m", "Tourner à droite rue Victor-Hugo")))
    }

    @Test
    fun deliveryShowsStatus() {
        assertEquals("Votre commande arrive", LiveFormat.compactSubtitle(activity(LiveKind.PROGRESS, "Votre commande arrive", "12:30")))
    }

    @Test
    fun skipsDuplicateOfShortTextAndBlank() {
        assertEquals("12:30", LiveFormat.compactSubtitle(activity(LiveKind.GENERIC, "5 min", "12:30", short = "5 min")))
        assertNull(LiveFormat.compactSubtitle(activity(LiveKind.GENERIC, " ", null)))
    }
}
