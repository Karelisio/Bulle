package com.karelisio.bulle.island.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.IslandFont
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.settings.OpenDirection
import com.karelisio.bulle.core.settings.PositionTweak
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.island.CutoutLocator
import com.karelisio.bulle.island.ScreenInfo
import com.karelisio.bulle.ui.settings.PixelNudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Logique pure des nouvelles options de l'île : angles, sens d'ouverture, texte, réglage au pixel, réglages. */
class IslandOptionsLogicTest {

    // --- Angles lissés ---

    @Test
    fun circleHandleIsClassicQuarterCircleApproximation() {
        assertEquals(2f, CornerSmoothing.exponent(0f), 0f)
        assertEquals(0.5523f, CornerSmoothing.handleFraction(2f), 0.0005f)
    }

    @Test
    fun smoothingIsMonotonicAndBounded() {
        assertEquals(4.5f, CornerSmoothing.exponent(1f), 1e-4f)
        assertEquals(4.5f, CornerSmoothing.exponent(3f), 1e-4f) // borné
        var last = 0f
        for (i in 0..10) {
            val f = CornerSmoothing.handleFraction(CornerSmoothing.exponent(i / 10f))
            assertTrue("poignées croissantes", f >= last)
            assertTrue("poignée < 1", f < 1f)
            last = f
        }
    }

    @Test
    fun bezierMidpointHitsSuperellipseDiagonal() {
        // Bézier P0=(L,0) P1=(L(1-f),0) P2=(0,L(1-f)) P3=(0,L) : le milieu doit valoir le point à 45°.
        for (s in listOf(0f, 0.3f, 0.7f, 1f)) {
            val n = CornerSmoothing.exponent(s)
            val l = 50f
            val f = CornerSmoothing.handleFraction(n)
            val mid = (l + 3 * l * (1 - f)) / 8f
            assertEquals(CornerSmoothing.diagonalInset(l, n), mid, 1e-3f)
            // Et ce point est bien sur la superellipse |x|ⁿ + |y|ⁿ = Lⁿ centrée en (L, L).
            val d = l - mid
            assertEquals(l.pow(n), 2 * d.pow(n), l.pow(n) * 1e-3f)
        }
    }

    @Test
    fun extentGrowsWithSmoothingButStaysInsideTheShape() {
        assertEquals(20f, CornerSmoothing.extent(20f, 0f, 100f), 0f)
        assertEquals(32f, CornerSmoothing.extent(20f, 1f, 100f), 1e-4f)
        assertEquals(25f, CornerSmoothing.extent(40f, 1f, 25f), 0f)
        assertEquals(0f, CornerSmoothing.extent(-5f, 0.5f, 25f), 0f)
    }

    // --- Sens d'ouverture ---

    @Test
    fun centeredOpeningKeepsZeroOffset() {
        assertEquals(0f, IslandLayout.openingOffsetX(OpenDirection.CENTER, 180f, 420f, 540f, 1080f, 0f, false), 0f)
        // Carte étendue centrée : identique à l'ancien calcul (centre gardé dans l'écran).
        val old = 540f.coerceIn(1000f / 2 + 30f, 1080f - 1000f / 2 - 30f) - 540f
        assertEquals(old, IslandLayout.openingOffsetX(OpenDirection.CENTER, 180f, 1000f, 540f, 1080f, 30f, true), 0f)
    }

    @Test
    fun leftOpeningKeepsRightEdgeFixed() {
        val base = 120f
        val target = 300f
        val cam = 800f
        val dx = IslandLayout.openingOffsetX(OpenDirection.LEFT, base, target, cam, 1080f, 0f, true)
        assertEquals(cam + base / 2, cam + dx + target / 2, 1e-3f)
    }

    @Test
    fun rightOpeningKeepsLeftEdgeFixed() {
        val base = 120f
        val target = 300f
        val cam = 250f
        val dx = IslandLayout.openingOffsetX(OpenDirection.RIGHT, base, target, cam, 1080f, 0f, true)
        assertEquals(cam - base / 2, cam + dx - target / 2, 1e-3f)
    }

    @Test
    fun openingStaysOnScreen() {
        // Caméra près du bord gauche, ouverture vers la gauche : l'île est repoussée dans l'écran.
        val dx = IslandLayout.openingOffsetX(OpenDirection.LEFT, 100f, 400f, 120f, 1080f, 10f, true)
        assertEquals(10f, 120f + dx - 400f / 2, 1e-3f)
        // Plus large que l'écran : centrée sur l'écran.
        assertEquals(540f - 120f, IslandLayout.openingOffsetX(OpenDirection.RIGHT, 100f, 2000f, 120f, 1080f, 10f, true), 0f)
    }

    @Test
    fun twoLineNeedsEnoughHeight() {
        assertTrue(IslandLayout.twoLineFits(heightPx = 110, density = 3f, textScale = 1f))
        assertFalse(IslandLayout.twoLineFits(heightPx = 80, density = 3f, textScale = 1f))
        assertFalse(IslandLayout.twoLineFits(heightPx = 92, density = 3f, textScale = 1.25f))
        assertFalse(IslandLayout.twoLineFits(heightPx = 92, density = 0f, textScale = 1f))
    }

    @Test
    fun geometryCarriesOpenDirection() {
        val info = ScreenInfo(width = 1080, height = 2400, statusBarHeight = 90, cutout = null, density = 3f, rotation = 0)
        assertEquals(OpenDirection.CENTER, CutoutLocator.geometry(info, PositionTweak()).openDirection)
        assertEquals(OpenDirection.LEFT, CutoutLocator.geometry(info, PositionTweak(openDirection = OpenDirection.LEFT)).openDirection)
    }

    // --- Typographie ---

    @Test
    fun defaultTypographyIsUntouched() {
        val base = Typography()
        assertSame(base, islandTypography(base, null, 1f))
    }

    @Test
    fun typographyScalesAndChangesFamily() {
        val base = Typography()
        val t = islandTypography(base, FontFamily.Serif, 1.2f)
        assertEquals(base.bodyMedium.fontSize.value * 1.2f, t.bodyMedium.fontSize.value, 1e-3f)
        assertEquals(base.labelSmall.lineHeight.value * 1.2f, t.labelSmall.lineHeight.value, 1e-3f)
        assertEquals(FontFamily.Serif, t.titleMedium.fontFamily)
        // Échelle bornée.
        val big = islandTypography(base, null, 3f)
        assertEquals(base.bodyLarge.fontSize.value * 1.25f, big.bodyLarge.fontSize.value, 1e-3f)
    }

    // --- Réglage au pixel ---

    @Test
    fun pixelDpConversions() {
        assertEquals(3, PixelNudge.dpToPx(1f, 3f))
        assertEquals(1f / 3f, PixelNudge.pxToDp(1, 3f), 1e-6f)
        // Un pas d'un pixel réel, réappliqué comme le fait CutoutLocator (arrondi au pixel).
        var dp = 0f
        repeat(7) { dp = PixelNudge.nudge(dp, 1, 2.75f, -60f..60f) }
        assertEquals(7, PixelNudge.dpToPx(dp, 2.75f))
        repeat(9) { dp = PixelNudge.nudge(dp, -1, 2.75f, -60f..60f) }
        assertEquals(-2, PixelNudge.dpToPx(dp, 2.75f))
        // Bornes.
        assertEquals(40f, PixelNudge.nudge(40f, 1, 3f, -20f..40f), 0f)
    }

    @Test
    fun holdToRepeatAccelerates() {
        val first = PixelNudge.repeatDelayMs(0)
        assertTrue("pause avant la première répétition", first > PixelNudge.repeatDelayMs(1))
        for (i in 1..40) assertTrue(PixelNudge.repeatDelayMs(i + 1) <= PixelNudge.repeatDelayMs(i))
        assertEquals(28L, PixelNudge.repeatDelayMs(200))
        assertTrue(PixelNudge.repeatDelayMs(5) < PixelNudge.repeatDelayMs(1))
    }

    // --- Animations ---

    @Test
    fun playfulOvershootsMoreThanOthers() {
        val playful = IslandMotion(MotionSettings(style = MotionStyle.PLAYFUL), reduceMotion = false).overshoot
        MotionStyle.entries.filter { it != MotionStyle.PLAYFUL }.forEach {
            assertTrue(playful > IslandMotion(MotionSettings(style = it), reduceMotion = false).overshoot)
        }
        assertEquals(MotionStyle.PLAYFUL, MotionStyle.entries.last())
    }

    // --- Sérialisation ---

    @Test
    fun newSettingsRoundTrip() {
        val s = BulleSettings(
            position = PositionTweak(offsetXDp = 1f / 3f, openDirection = OpenDirection.RIGHT),
            motion = MotionSettings(style = MotionStyle.PLAYFUL, blurTransitions = false, haptics = false),
            style = StyleSettings(cornerSmoothing = 0.6f, font = IslandFont.MONO, textScale = 1.15f, marquee = true, twoLineCompact = true),
        )
        val back = BulleJson.decodeFromString(BulleSettings.serializer(), BulleJson.encodeToString(BulleSettings.serializer(), s))
        assertEquals(s, back)
    }

    @Test
    fun oldJsonKeepsCurrentBehaviour() {
        val old = BulleJson.decodeFromString(
            BulleSettings.serializer(),
            """{"position": {"offsetXDp": 2.0}, "motion": {"style": "SNAPPY"}, "style": {"shape": "DROP"}}""",
        )
        assertEquals(OpenDirection.CENTER, old.position.openDirection)
        assertTrue(old.motion.blurTransitions)
        assertTrue(old.motion.haptics)
        assertEquals(0f, old.style.cornerSmoothing, 0f)
        assertEquals(IslandFont.DEFAULT, old.style.font)
        assertEquals(1f, old.style.textScale, 0f)
        assertFalse(old.style.marquee)
        assertFalse(old.style.twoLineCompact)
    }

    @Test
    fun unknownNewEnumsFallBackToDefaults() {
        val s = BulleJson.decodeFromString(
            BulleSettings.serializer(),
            """{"position": {"openDirection": "DIAGONAL"}, "style": {"font": "COMIC", "size": 1.2}}""",
        )
        assertEquals(OpenDirection.CENTER, s.position.openDirection)
        assertEquals(IslandFont.DEFAULT, s.style.font)
        assertEquals(1.2f, s.style.size, 0f)
    }
}
