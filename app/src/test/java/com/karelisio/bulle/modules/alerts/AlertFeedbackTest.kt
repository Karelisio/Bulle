package com.karelisio.bulle.modules.alerts

import com.karelisio.bulle.core.feedback.FeedbackSound
import com.karelisio.bulle.core.feedback.HapticPattern
import com.karelisio.bulle.core.feedback.SoundKind
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.alerts.sources.ClipboardContent
import com.karelisio.bulle.modules.alerts.ui.StyledAlert
import com.karelisio.bulle.modules.alerts.ui.styled
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFeedbackTest {

    private val buzz = AlertStyle(haptic = HapticPattern.DOUBLE_TAP, sound = FeedbackSound(SoundKind.CHIME))

    @Test
    fun oldStylesWithoutFeedbackFieldsDecodeToSilence() {
        val back = BulleJson.decodeFromString<AlertSettings>(
            """{"styles":{"wifi":{"icon":"router","accent":4285182975,"entrance":"BOUNCE","durationSec":4}}}""",
        )
        val s = back.styleOf("wifi")
        assertEquals(HapticPattern.NONE, s.haptic)
        assertTrue(s.sound.isNone)
        assertFalse(s.hasFeedback)
        assertEquals("router", s.icon)
    }

    @Test
    fun defaultStyleStaysDefault() {
        assertTrue(AlertStyle().isDefault)
        assertFalse(AlertStyle(haptic = HapticPattern.LIGHT).isDefault)
        assertFalse(AlertStyle(sound = FeedbackSound(SoundKind.POP)).isDefault)
    }

    @Test
    fun feedbackRoundTripsAndUnknownValuesFallBack() {
        val sys = AlertStyle(sound = FeedbackSound(SoundKind.SYSTEM, "content://media/internal/audio/media/12", "Tri-tone"))
        val alerts = AlertSettings().withStyle("wifi", buzz).withStyle("usb", sys)
        val back = BulleJson.decodeFromString<BulleSettings>(BulleJson.encodeToString(BulleSettings(alerts = alerts)))
        assertEquals(alerts.styles, back.alerts.styles)

        val future = BulleJson.decodeFromString<AlertSettings>(
            """{"styles":{"wifi":{"haptic":"EARTHQUAKE","sound":{"kind":"LASER"},"durationSec":3}}}""",
        )
        assertEquals(HapticPattern.NONE, future.styleOf("wifi").haptic)
        assertEquals(SoundKind.NONE, future.styleOf("wifi").sound.kind)
        assertEquals(3, future.styleOf("wifi").durationSec)
    }

    @Test
    fun feedbackOnlyStyleWrapsContentWithoutVisualChange() {
        val c = ClipboardContent(1)
        val s = styled(c, mapOf("clipboard" to buzz)) as StyledAlert
        assertEquals(c.accent, s.accent)
        assertEquals(c.timeoutMs, s.timeoutMs)
    }

    @Test
    fun feedbackPlaysOncePerAppearanceNotOnUpdates() {
        val arrivals = AlertArrivals()
        val first = styled(ClipboardContent(postedAt = 1_000), mapOf("clipboard" to buzz))
        assertEquals(listOf(buzz), arrivals.onContents(listOf(first), now = 1_100))
        // Même clé ré-émise (mise à jour) : rien.
        assertTrue(arrivals.onContents(listOf(first), now = 1_500).isEmpty())
        // Disparition puis nouvelle alerte : rejouée.
        assertTrue(arrivals.onContents(emptyList(), now = 2_000).isEmpty())
        val second = styled(ClipboardContent(postedAt = 3_000), mapOf("clipboard" to buzz))
        assertEquals(listOf(buzz), arrivals.onContents(listOf(second), now = 3_050))
    }

    @Test
    fun staleOrUnstyledAlertsDoNotPlay() {
        val arrivals = AlertArrivals()
        val old = styled(ClipboardContent(postedAt = 0), mapOf("clipboard" to buzz))
        assertTrue(arrivals.onContents(listOf(old), now = AlertArrivals.FRESH_MS + 1_000).isEmpty())
        val plain = ClipboardContent(postedAt = 10_000)
        assertTrue(arrivals.onContents(listOf(plain), now = 10_010).isEmpty())
        val visualOnly = styled(ClipboardContent(postedAt = 20_000), mapOf("clipboard" to AlertStyle(icon = "copy")))
        assertTrue(arrivals.onContents(listOf(visualOnly), now = 20_010).isEmpty())
    }
}
