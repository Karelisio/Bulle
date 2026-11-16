package com.karelisio.bulle.modules.halo

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.island.HiddenReason
import com.karelisio.bulle.island.ScreenEnv
import com.karelisio.bulle.modules.volet.media.MediaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloLogicTest {

    private val on = HaloSettings(enabled = true)
    private fun c(
        pkg: String = "com.chat",
        ongoing: Boolean = false,
        silent: Boolean = false,
        conversation: Boolean = false,
        summary: Boolean = false,
        media: Boolean = false,
        update: Boolean = false,
    ) = HaloCandidate(pkg, ongoing, silent, conversation, summary, media, update)

    private fun decide(cand: HaloCandidate, s: HaloSettings = on, dnd: Boolean = false, block: HiddenReason? = null, screenOn: Boolean = true, allowed: Boolean = true) =
        HaloFilter.decide(cand, s, dnd, block, screenOn, allowed)

    @Test
    fun ordinaryNotificationShowsAHalo() {
        assertEquals(HaloVerdict.SHOW, decide(c()))
        assertEquals(HaloVerdict.SKIP_DISABLED, decide(c(), HaloSettings()))
    }

    @Test
    fun filtersSilentOngoingSummariesAndUpdates() {
        assertEquals(HaloVerdict.SKIP_SILENT, decide(c(silent = true)))
        assertEquals(HaloVerdict.SKIP_ONGOING, decide(c(ongoing = true)))
        assertEquals(HaloVerdict.SKIP_ONGOING, decide(c(media = true)))
        assertEquals(HaloVerdict.SKIP_SUMMARY, decide(c(summary = true)))
        assertEquals(HaloVerdict.SKIP_ONGOING, decide(c(update = true)))
        // Filtres coupés : silencieuses et en cours passent.
        val loose = on.copy(ignoreSilent = false, ignoreOngoing = false)
        assertEquals(HaloVerdict.SHOW, decide(c(silent = true, ongoing = true), loose))
    }

    @Test
    fun selectedAppsAndConversationsOnly() {
        val selected = on.copy(appFilter = HaloAppFilter.SELECTED, selectedApps = setOf("com.chat"))
        assertEquals(HaloVerdict.SHOW, decide(c("com.chat"), selected))
        assertEquals(HaloVerdict.SKIP_APP, decide(c("com.mail"), selected))
        val convo = on.copy(conversationsOnly = true)
        assertEquals(HaloVerdict.SKIP_NOT_CONVERSATION, decide(c(), convo))
        assertEquals(HaloVerdict.SHOW, decide(c(conversation = true), convo))
    }

    @Test
    fun doNotDisturbFullscreenAndHiddenApps() {
        assertEquals(HaloVerdict.SKIP_DND, decide(c(), dnd = true))
        assertEquals(HaloVerdict.SHOW, decide(c(), on.copy(muteInDnd = false), dnd = true))
        assertEquals(HaloVerdict.SKIP_FULLSCREEN, decide(c(), block = HiddenReason.FULLSCREEN))
        assertEquals(HaloVerdict.SKIP_HIDDEN_APP, decide(c(), block = HiddenReason.HIDDEN_APP))
    }

    @Test
    fun screenOffNeedsTheOptionAndTheRateLimit() {
        assertEquals(HaloVerdict.SKIP_SCREEN_OFF, decide(c(), screenOn = false))
        val night = on.copy(screenOff = true)
        assertEquals(HaloVerdict.SHOW_SCREEN_OFF, decide(c(), night, screenOn = false))
        assertEquals(HaloVerdict.SKIP_RATE_LIMIT, decide(c(), night, screenOn = false, allowed = false))
    }

    @Test
    fun rateLimiterKeepsAGapAndAnHourlyBudget() {
        val l = HaloRateLimiter(windowMs = 3_600_000L, minGapMs = 20_000L)
        assertTrue(l.tryAcquire(0, 2))
        assertFalse("écart minimal", l.tryAcquire(10_000, 2))
        assertTrue(l.tryAcquire(30_000, 2))
        assertFalse("budget atteint", l.tryAcquire(60_000, 2))
        assertTrue("fenêtre glissante", l.tryAcquire(3_600_001L, 2))
    }

    @Test
    fun fullscreenBlocksTheHaloEvenInNativeLandscape() {
        val s = BulleSettings()
        val video = ScreenEnv(landscape = true, statusBarVisible = false)
        assertEquals(HiddenReason.FULLSCREEN, HaloFilter.screenBlock(s, video, "com.video", null))
        val hidden = s.copy(visibility = s.visibility.copy(hiddenApps = setOf("com.game")))
        assertEquals(HiddenReason.HIDDEN_APP, HaloFilter.screenBlock(hidden, ScreenEnv(), "com.game", null))
        // L'île désactivée ou masquée sur l'écran verrouillé ne coupe pas le halo.
        val off = s.copy(islandEnabled = false, visibility = s.visibility.copy(hideOnLockscreen = true))
        assertNull(HaloFilter.screenBlock(off, ScreenEnv(locked = true), null, null))
    }

    @Test
    fun colourPriorities() {
        val dyn = 0xFF112233.toInt()
        val pink = 0xFFFF6FA5.toInt()
        val perApp = on.copy(appColors = mapOf("a" to 0xFF00FF00))
        assertEquals(0xFF00FF00.toInt(), HaloPalette.pick(perApp, "a", echo = true, artwork = 1, appIcon = 2, dynamic = dyn, echoPink = pink))
        assertEquals(pink, HaloPalette.pick(on, "e", echo = true, artwork = null, appIcon = null, dynamic = dyn, echoPink = pink))
        assertEquals(dyn, HaloPalette.pick(on.copy(colorMode = HaloColorMode.DYNAMIC), "x", false, null, null, dyn, pink))
        assertEquals(0xFF7FD3FF.toInt(), HaloPalette.pick(on.copy(colorMode = HaloColorMode.FIXED), "x", false, null, null, dyn, pink))
    }

    @Test
    fun glowMakesDarkColoursBright() {
        val navy = 0xFF0A1A40.toInt()
        val l = MediaColors.toHsl(HaloPalette.glow(navy))[2]
        assertTrue(l >= 0.57f)
    }

    @Test
    fun heartbeatFollowsTheEchoKeyframes() {
        assertEquals(1f, HaloTiming.heartbeat(130), 0.001f)
        assertTrue(HaloTiming.heartbeat(260) < HaloTiming.heartbeat(390))
        assertEquals(HaloTiming.heartbeat(130), HaloTiming.heartbeat(130 + HaloTiming.HEART_BEAT_MS), 0.001f)
    }

    @Test
    fun envelopeFadesInAndOut() {
        assertEquals(0f, HaloTiming.envelope(0, 1000), 0.001f)
        assertEquals(1f, HaloTiming.envelope(500, 1000), 0.001f)
        assertEquals(0f, HaloTiming.envelope(1000, 1000), 0.001f)
    }

    @Test
    fun lookTotalDurationCountsPassesAndSpeed() {
        val look = HaloLook.of(on.copy(passes = 2, durationMs = 1000, speed = 2f), 3f)
        assertEquals(1000L + HaloTiming.FADE_MS, look.totalMs(HaloKind.NOTIFICATION))
        assertEquals(12f, look.thicknessPx, 0.001f)
    }

    @Test
    fun sanitizeClampsEveryRange() {
        val s = HaloSettings(thicknessDp = 99f, durationMs = 1, passes = 40, intensity = Float.NaN, speed = 9f, screenOffMaxPerHour = 0).sanitized()
        assertEquals(12f, s.thicknessDp)
        assertEquals(600, s.durationMs)
        assertEquals(5, s.passes)
        assertEquals(0.9f, s.intensity)
        assertEquals(2f, s.speed)
        assertEquals(1, s.screenOffMaxPerHour)
    }
}
