package com.karelisio.bulle.core.island

import androidx.compose.runtime.Composable
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.VisibilitySettings
import com.karelisio.bulle.island.ScreenEnv
import com.karelisio.bulle.island.VisibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Fake(
    override val key: String,
    override val moduleId: String,
    override val priority: Priority,
    override val postedAt: Long = 0,
    override val canBeSecondary: Boolean = true,
) : IslandContent() {
    @Composable
    override fun Leading() = Unit
}

class IslandArbiterTest {

    private val media = Fake("media", "media", Priority.MEDIA)
    private val timer = Fake("timer", "live", Priority.LIVE)
    private val call = Fake("call", "call", Priority.CALL)
    private val alarm = Fake("alarm", "alarm", Priority.ALARM)
    private val alert = Fake("alert", "alerts", Priority.ALERT)

    @Test
    fun empty() {
        assertEquals(Arrangement.Empty, IslandArbiter.arrange(emptyList()))
    }

    @Test
    fun priorityOrder() {
        val arr = IslandArbiter.arrange(listOf(media, alert, timer, alarm, call))
        assertEquals("call", arr.primary?.key)
        assertEquals("alarm", arr.secondary?.key)
    }

    @Test
    fun liveBeatsMedia() {
        val arr = IslandArbiter.arrange(listOf(media, timer))
        assertEquals("timer", arr.primary?.key)
        assertEquals("media", arr.secondary?.key)
    }

    @Test
    fun newestWinsAtSamePriority() {
        val a = Fake("a", "x", Priority.LIVE, postedAt = 1)
        val b = Fake("b", "y", Priority.LIVE, postedAt = 2)
        assertEquals("b", IslandArbiter.arrange(listOf(a, b)).primary?.key)
    }

    @Test
    fun secondaryComesFromAnotherModule() {
        val t2 = Fake("timer2", "live", Priority.LIVE, postedAt = 5)
        val arr = IslandArbiter.arrange(listOf(timer, t2))
        assertEquals("timer2", arr.primary?.key)
        assertNull(arr.secondary)
    }

    @Test
    fun swapAppliesOnlyToSamePair() {
        val swapped = IslandArbiter.arrange(listOf(media, timer), "timer" to "media")
        assertEquals("media", swapped.primary?.key)
        assertEquals("timer", swapped.secondary?.key)
        val other = IslandArbiter.arrange(listOf(media, timer, call), "timer" to "media")
        assertEquals("call", other.primary?.key)
    }

    @Test
    fun visibilityPolicy() {
        val s = BulleSettings()
        assertTrue(VisibilityPolicy.isVisible(s, ScreenEnv(), "com.app"))
        assertFalse(VisibilityPolicy.isVisible(s.copy(islandEnabled = false), ScreenEnv(), null))
        assertFalse(VisibilityPolicy.isVisible(s, ScreenEnv(statusBarVisible = false), null))
        assertTrue(VisibilityPolicy.isVisible(s, ScreenEnv(landscape = true), null)) // mode paysage natif
        assertFalse(VisibilityPolicy.isVisible(s.copy(visibility = VisibilitySettings(landscapeNative = false)), ScreenEnv(landscape = true), null))
        assertTrue(VisibilityPolicy.isVisible(s, ScreenEnv(locked = true), null))
        val hidden = s.copy(visibility = VisibilitySettings(hiddenApps = setOf("com.game"), hideOnLockscreen = true))
        assertFalse(VisibilityPolicy.isVisible(hidden, ScreenEnv(), "com.game"))
        assertFalse(VisibilityPolicy.isVisible(hidden, ScreenEnv(locked = true), null))
    }
}
