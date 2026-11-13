package com.karelisio.bulle.modules.volume

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.modules.volume.ui.ThickSlider
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Prolongation de l'affichage du volume : logique pure, état partagé et glissement simulé. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VolumeKeepAliveTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        VolumeKeys.hide()
    }

    @After
    fun tearDown() = VolumeKeys.hide()

    private fun overlay(at: Long = SystemClock.elapsedRealtime()) =
        VolumeOverlay(VolumeStream.MEDIA, 5, 0, 15, muted = false, shownAt = at)

    @Test
    fun timeoutLogic() {
        assertEquals(2_500L, VolumeLogic.timeoutFor(2_500, holding = false, expanded = false))
        assertEquals(6_250L, VolumeLogic.timeoutFor(2_500, holding = false, expanded = true))
        assertEquals(6_000L, VolumeLogic.timeoutFor(1_000, holding = false, expanded = true))
        assertNull(VolumeLogic.timeoutFor(2_500, holding = true, expanded = false))
        assertNull(VolumeLogic.expiresAt(100, 2_500, holding = true, expanded = true))
        assertEquals(2_600L, VolumeLogic.expiresAt(100, 2_500, holding = false, expanded = false))
        assertEquals(6_350L, VolumeLogic.expiresAt(100, 2_500, holding = false, expanded = true))
    }

    @Test
    fun throttleLogic() {
        assertFalse(VolumeLogic.shouldKeepAlive(now = 1_300, lastBumpAt = 1_000, force = false))
        assertTrue(VolumeLogic.shouldKeepAlive(now = 1_400, lastBumpAt = 1_000, force = false))
        assertTrue(VolumeLogic.shouldKeepAlive(now = 1_001, lastBumpAt = 1_000, force = true))
    }

    @Test
    fun holdingSuspendsAndExpandedLengthens() {
        VolumeKeys.showForTests(overlay())
        VolumeKeys.setHolding(true)
        val held = VolumeContent(VolumeKeys.overlay.value!!, 2_500)
        assertNull(held.timeoutMs)
        assertNull(held.expiresAt)
        // Une relecture du niveau garde le geste en cours.
        VolumeKeys.refresh(context)
        assertTrue(VolumeKeys.overlay.value!!.holding)
        VolumeKeys.setHolding(false)
        VolumeKeys.setExpanded(true)
        val expanded = VolumeContent(VolumeKeys.overlay.value!!, 2_500)
        assertEquals(6_250L, expanded.timeoutMs)
        assertEquals(VolumeContent.KEY, expanded.key)
        VolumeKeys.setExpanded(false)
        assertEquals(2_500L, VolumeContent(VolumeKeys.overlay.value!!, 2_500).timeoutMs)
    }

    @Test
    fun keepAliveIsThrottledButForced() {
        val start = SystemClock.elapsedRealtime()
        VolumeKeys.showForTests(overlay(start - 10_000))
        VolumeKeys.keepAlive(force = true)
        val first = VolumeKeys.overlay.value!!.shownAt
        assertTrue(first >= start)
        VolumeKeys.keepAlive()
        assertEquals(first, VolumeKeys.overlay.value!!.shownAt)
    }

    @Test
    fun simulatedDragHoldsThenReleases() {
        VolumeKeys.showForTests(overlay())
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val levels = mutableListOf<Int>()
        activity.get().setContent {
            BulleTheme {
                ThickSlider(
                    fraction = 0.3f, min = 0, max = 15, enabled = true, fillColor = Color.White,
                    highlighted = false, accent = Color.Blue, icon = Icons.Rounded.MusicNote,
                    label = "Média", subtitle = null, appIcon = null, valueText = "30 %", stateText = null,
                    onLevel = { levels += it }, onRelease = {}, muteLabel = "Couper", onIconTap = null,
                    onHoldChange = VolumeKeys::setHolding,
                )
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        val root = activity.get().window.decorView
        val loc = IntArray(2)
        val content = root.findViewById<android.view.View>(android.R.id.content)
        content.getLocationInWindow(loc)
        val y = loc[1] + 20f
        val t0 = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, dt: Long) {
            val e = MotionEvent.obtain(t0, t0 + dt, action, x, y, 0)
            e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            (content as android.view.ViewGroup).getChildAt(0).dispatchTouchEvent(e)
            e.recycle()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        }
        send(MotionEvent.ACTION_DOWN, 100f, 0)
        for (i in 1..10) send(MotionEvent.ACTION_MOVE, 100f + i * 20f, i * 16L)
        val during = VolumeKeys.overlay.value
        assertNotNull(during)
        assertTrue("Doigt posé : pas d expiration", during!!.holding)
        assertNull(VolumeContent(during, 2_500).expiresAt)
        send(MotionEvent.ACTION_UP, 300f, 200)
        assertFalse(VolumeKeys.overlay.value!!.holding)
        assertTrue(levels.isNotEmpty())
        activity.pause().stop().destroy()
    }
}
