package com.karelisio.bulle.island

import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Connexions / déconnexions répétées du service d'accessibilité. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IslandServiceLifecycleTest {

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun noAnimations() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    // onServiceConnected est protégée : appelée comme le ferait le système.
    private fun connect(s: IslandAccessibilityService) {
        IslandAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(s)
    }

    @Test
    fun reconnectDoesNotLeakAndUnbindStopsEverything() {
        val controller = Robolectric.buildService(IslandAccessibilityService::class.java).create()
        val service = controller.get()

        connect(service)
        idle()
        val first = IslandAccessibilityService.controller.value
        assertNotNull(first)
        assertTrue(IslandAccessibilityService.running.value)
        val receivers = shadowOf(app).registeredReceivers.size

        // Seconde connexion sans onUnbind : l'ancienne île est libérée, pas de doublon.
        connect(service)
        idle()
        val second = IslandAccessibilityService.controller.value
        assertNotNull(second)
        assertNotSame(first, second)
        assertEquals(receivers, shadowOf(app).registeredReceivers.size)

        service.onUnbind(Intent())
        idle()
        assertFalse(IslandAccessibilityService.running.value)
        assertNull(IslandAccessibilityService.controller.value)

        // Reconnexion après déconnexion : tout repart (nouvelle portée de coroutines).
        connect(service)
        idle()
        val third = IslandAccessibilityService.controller.value
        assertNotNull(third)
        assertTrue(IslandAccessibilityService.running.value)
        third!!.updateScreen { it.copy(geometry = IslandGeometry(540, 10, 180, 90, 1080, 3f)) }
        idle()
        assertEquals(180, third.state.value.geometry.width)

        controller.destroy()
        idle()
        assertFalse(IslandAccessibilityService.running.value)
    }
}
