package com.karelisio.bulle.island

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.VisibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exceptions au masquage plein écran (applis choisies, écran d'accueil). */
class VisibilityPolicyTest {
    private val fullscreen = ScreenEnv(statusBarVisible = false)
    private val launcher = "com.hihonor.android.launcher"

    private fun settings(v: VisibilitySettings = VisibilitySettings()) = BulleSettings(visibility = v)

    @Test
    fun fullscreenHidesByDefault() {
        assertEquals(HiddenReason.FULLSCREEN, VisibilityPolicy.hiddenReason(settings(), fullscreen, "com.video"))
        // Paquet du launcher inconnu : aucune exception.
        assertEquals(HiddenReason.FULLSCREEN, VisibilityPolicy.hiddenReason(settings(), fullscreen, launcher))
    }

    @Test
    fun ignoredAppKeepsIslandInFullscreen() {
        val s = settings(VisibilitySettings(fullscreenIgnoredApps = setOf("com.video")))
        assertNull(VisibilityPolicy.hiddenReason(s, fullscreen, "com.video"))
        assertEquals(HiddenReason.FULLSCREEN, VisibilityPolicy.hiddenReason(s, fullscreen, "com.game"))
        assertEquals(HiddenReason.FULLSCREEN, VisibilityPolicy.hiddenReason(s, fullscreen, null))
    }

    @Test
    fun ignoredAppDoesNotBypassOtherConditions() {
        val s = settings(VisibilitySettings(fullscreenIgnoredApps = setOf("com.video"), hiddenApps = setOf("com.video")))
        assertEquals(HiddenReason.HIDDEN_APP, VisibilityPolicy.hiddenReason(s, fullscreen, "com.video"))
        val landscape = settings(VisibilitySettings(fullscreenIgnoredApps = setOf("com.video")))
        assertEquals(
            HiddenReason.LANDSCAPE,
            VisibilityPolicy.hiddenReason(landscape, fullscreen.copy(landscape = true), "com.video"),
        )
    }

    @Test
    fun launcherIgnoresFullscreenWhenEnabled() {
        assertNull(VisibilityPolicy.hiddenReason(settings(), fullscreen, launcher, launcherPackage = launcher))
        assertEquals(
            HiddenReason.FULLSCREEN,
            VisibilityPolicy.hiddenReason(settings(), fullscreen, "com.video", launcherPackage = launcher),
        )
        val off = settings(VisibilitySettings(ignoreFullscreenOnLauncher = false))
        assertEquals(HiddenReason.FULLSCREEN, VisibilityPolicy.hiddenReason(off, fullscreen, launcher, launcherPackage = launcher))
        assertEquals(false, VisibilityPolicy.isVisible(off, fullscreen, launcher, launcher))
    }

    @Test
    fun ruleStillWins() {
        val hiddenByRule = fullscreen.copy(ruleVisibility = false)
        assertEquals(HiddenReason.RULE, VisibilityPolicy.hiddenReason(settings(), hiddenByRule, launcher, launcher))
    }
}
