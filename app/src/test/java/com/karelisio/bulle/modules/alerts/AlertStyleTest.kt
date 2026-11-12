package com.karelisio.bulle.modules.alerts

import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.alerts.sources.ClipboardContent
import com.karelisio.bulle.modules.alerts.sources.TorchContent
import com.karelisio.bulle.modules.alerts.ui.AlertIcons
import com.karelisio.bulle.modules.alerts.ui.EntranceCurves
import com.karelisio.bulle.modules.alerts.ui.StyledAlert
import com.karelisio.bulle.modules.alerts.ui.alertIdOf
import com.karelisio.bulle.modules.alerts.ui.styled
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertStyleTest {

    @After
    fun resetBook() {
        AlertStyleBook.styles = emptyMap()
    }

    @Test
    fun stylesRoundTripThroughSettingsJson() {
        val alerts = AlertSettings()
            .withStyle("wifi", AlertStyle(icon = "router", accent = 0xFF6AB0FF, entrance = AlertEntrance.BOUNCE, durationSec = 7))
            .withStyle("torch", AlertStyle(entrance = AlertEntrance.NONE))
        val json = BulleJson.encodeToString(BulleSettings(alerts = alerts))
        val back = BulleJson.decodeFromString<BulleSettings>(json)
        assertEquals(alerts.styles, back.alerts.styles)
        assertEquals(0xFF6AB0FF, back.alerts.styleOf("wifi").accent)
    }

    @Test
    fun oldSettingsWithoutNewFieldsKeepDefaults() {
        val back = BulleJson.decodeFromString<AlertSettings>("""{"enabled":true,"wifi":false}""")
        assertTrue(back.styles.isEmpty())
        assertFalse(back.cellular || back.fiveG || back.vpn || back.hotspot || back.ringer)
        assertFalse(back.mediaVolume || back.usb || back.cameraInUse || back.micInUse)
        assertTrue(back.styleOf("wifi").isDefault)
    }

    @Test
    fun unknownEntranceFromNewerVersionFallsBackToDefault() {
        val back = BulleJson.decodeFromString<AlertSettings>(
            """{"styles":{"wifi":{"entrance":"SPIRAL","durationSec":3}}}""",
        )
        assertNull(back.styleOf("wifi").entrance)
        assertEquals(3, back.styleOf("wifi").durationSec)
    }

    @Test
    fun emptyStyleIsRemovedFromTable() {
        val a = AlertSettings().withStyle("dnd", AlertStyle(durationSec = 5))
        assertEquals(setOf("dnd"), a.styles.keys)
        assertTrue(a.withStyle("dnd", AlertStyle()).styles.isEmpty())
    }

    @Test
    fun durationIsClampedAndOnlyForTimedAlerts() {
        assertEquals(2_000L, AlertStyle(durationSec = 0).durationMs)
        assertEquals(10_000L, AlertStyle(durationSec = 60).durationMs)
        AlertStyleBook.styles = mapOf("clipboard" to AlertStyle(durationSec = 8), "torch" to AlertStyle(durationSec = 8))
        assertEquals(8_000L, ClipboardContent(1).timeoutMs)
        // Persistante : reste persistante quelle que soit la durée stockée.
        assertNull(TorchContent("0", 1, 1).timeoutMs)
        assertEquals(4_000L, AlertStyleBook.timeout("wifi", 4_000L))
    }

    @Test
    fun styledWrapsOnlyCustomizedAlerts() {
        val c = ClipboardContent(1)
        assertSame(c, styled(c, emptyMap()))
        assertSame(c, styled(c, mapOf("clipboard" to AlertStyle())))
        val s = styled(c, mapOf("clipboard" to AlertStyle(accent = 0xFFFF5D5D, durationSec = 6)))
        assertTrue(s is StyledAlert)
        assertEquals(c.key, s.key)
        assertEquals(6_000L, s.timeoutMs)
        assertEquals("clipboard", alertIdOf(s))
        // Pas d'emballage en double.
        assertSame(s, styled(s, mapOf("clipboard" to AlertStyle(accent = 0xFF000000))))
    }

    @Test
    fun everyIconChoiceExists() {
        AlertCatalog.defs.forEach { def ->
            val choices = AlertIcons.choicesFor(def.id)
            assertTrue("aucune icône pour ${def.id}", choices.isNotEmpty())
            choices.forEach { assertNotNull("${def.id} → $it", AlertIcons.vector(it)) }
        }
        assertNull(AlertIcons.vector("inconnue"))
    }

    @Test
    fun heartbeatStartsAndEndsAtRest() {
        assertEquals(0f, EntranceCurves.heartbeat(0f), 1e-4f)
        assertEquals(0f, EntranceCurves.heartbeat(1f), 1e-4f)
        assertTrue(EntranceCurves.heartbeat(0.25f) > 0.5f)
    }
}
