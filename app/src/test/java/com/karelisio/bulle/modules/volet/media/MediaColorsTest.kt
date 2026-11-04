package com.karelisio.bulle.modules.volet.media

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Choix des couleurs de la carte média et de la sortie audio (logique pure). */
class MediaColorsTest {

    private val seeds = listOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(),
        0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
        0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFF1A237E.toInt(), 0xFFFFF59D.toInt(), 0xFF3E2723.toInt(),
    )

    @Test
    fun textAndControlsStayReadableOnEveryArtwork() {
        for (dark in listOf(true, false)) {
            for (seed in seeds) for (vivid in seeds + listOf<Int?>(null)) {
                val s = MediaColors.scheme(seed, vivid, dark, fallback = 0xFF6750A4.toInt())
                for (bg in MediaColors.backdrops(s.container)) {
                    assertTrue("texte $seed/$dark", MediaColors.contrast(s.onContainer, bg) >= MediaColors.TEXT_CONTRAST)
                    assertTrue("secondaire $seed/$dark", MediaColors.contrast(s.onContainerVariant, bg) >= MediaColors.UI_CONTRAST)
                    assertTrue("accent $seed/$vivid/$dark", MediaColors.contrast(s.accent, bg) >= MediaColors.UI_CONTRAST)
                }
                assertTrue("icône sur l'accent", MediaColors.contrast(s.onAccent, s.accent) >= MediaColors.UI_CONTRAST)
            }
        }
    }

    @Test
    fun schemeFollowsThemeBrightness() {
        val dark = MediaColors.scheme(0xFF2196F3.toInt(), null, dark = true, fallback = 0)
        val light = MediaColors.scheme(0xFF2196F3.toInt(), null, dark = false, fallback = 0)
        assertTrue(MediaColors.luminance(dark.container) < 0.1)
        assertTrue(MediaColors.luminance(light.container) > 0.6)
        // La teinte de la pochette est conservée dans le fond.
        assertEquals(MediaColors.toHsl(0xFF2196F3.toInt())[0], MediaColors.toHsl(dark.container)[0], 6f)
    }

    @Test
    fun hslRoundTrip() {
        for (c in seeds) {
            val hsl = MediaColors.toHsl(c)
            val back = MediaColors.fromHsl(hsl[0], hsl[1], hsl[2])
            for (shift in listOf(0, 8, 16)) {
                assertTrue(kotlin.math.abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
            }
        }
        assertEquals(21.0, MediaColors.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun outputPriority() {
        val speaker = OutputCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone")
        val wired = OutputCandidate(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, null)
        val bt = OutputCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, " Buds ")
        assertEquals(AudioOutput(OutputKind.PHONE, null), MediaOutput.pick(listOf(speaker)))
        assertEquals(OutputKind.HEADPHONES, MediaOutput.pick(listOf(speaker, wired)).kind)
        assertEquals(AudioOutput(OutputKind.BLUETOOTH, "Buds"), MediaOutput.pick(listOf(speaker, wired, bt)))
        assertEquals(AudioOutput(OutputKind.BLUETOOTH, null), MediaOutput.pick(listOf(OutputCandidate(AudioDeviceInfo.TYPE_BLE_HEADSET, ""))))
    }
}
