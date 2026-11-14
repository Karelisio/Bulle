package com.karelisio.bulle.modules.volet

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandAccessibilityService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Ouverture réelle du Volet depuis le service (fenêtre + composition) : ne doit pas planter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoletOpenSmokeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) // animations réelles : ressorts du Volet
        com.karelisio.bulle.testing.ComposeDispatcherReset.unstick()
    }

    @Test
    fun openingTheVoletDoesNotCrash() {
        runBlocking { SettingsRepository.update(context) { it.copy(volet = it.volet.copy(enabled = true)) } }
        val controller = Robolectric.buildService(IslandAccessibilityService::class.java).create()
        val service = controller.get()
        val connect = IslandAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        connect.isAccessible = true
        connect.invoke(service)
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
        val volet = VoletController.current.value
        assertNotNull(volet)
        volet!!.openFor(null)
        repeat(30) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
        volet.expandFull()
        repeat(30) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
        volet.close()
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
        controller.destroy()
    }
}
