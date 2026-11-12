package com.karelisio.bulle.modules

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.ModuleRegistry
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.focus.Focus
import com.karelisio.bulle.modules.focus.FocusAlert
import com.karelisio.bulle.modules.focus.FocusAlertContent
import com.karelisio.bulle.modules.focus.FocusContent
import com.karelisio.bulle.modules.focus.FocusModule
import com.karelisio.bulle.modules.focus.FocusPanel
import com.karelisio.bulle.modules.focus.FocusPhase
import com.karelisio.bulle.modules.focus.FocusPreset
import com.karelisio.bulle.modules.focus.FocusStore
import com.karelisio.bulle.modules.focus.FocusTimer
import com.karelisio.bulle.modules.focus.ui.FocusSettingsScreen
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.modules.volume.VolumeContent
import com.karelisio.bulle.modules.volume.VolumeKeys
import com.karelisio.bulle.modules.volume.VolumeModule
import com.karelisio.bulle.modules.volume.VolumeOverlay
import com.karelisio.bulle.modules.volume.VolumePrefs
import com.karelisio.bulle.modules.volume.VolumeStream
import com.karelisio.bulle.modules.volume.ui.VolumeSettingsScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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
import java.io.File

/** Minuteur et volume : rendu dans l'île (compact, étendu, panneau), écrans de réglages, persistance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusVolumeRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        Focus.resetForTests()
        VolumePrefs.resetForTests()
        VolumeKeys.hide()
    }

    @After
    fun tearDown() {
        Focus.resetForTests()
        VolumeKeys.hide()
    }

    @Test
    fun modulesDefaults() {
        Modules.registerAll()
        ModuleRegistry.register(FocusModule)
        ModuleRegistry.register(VolumeModule)
        assertTrue(FocusModule.enabledByDefault)
        assertFalse(VolumeModule.enabledByDefault)
    }

    @Test
    fun contentsRenderCompactAndExpanded() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()

        val now = System.currentTimeMillis()
        val work = FocusTimer.start(FocusPreset.Pomodoro, Focus.data.value.settings, now)
        val contents: List<IslandContent> = listOf(
            FocusContent(work, postedAt = 1),
            FocusContent(FocusTimer.pause(FocusTimer.start(FocusPreset.Minutes(90), Focus.data.value.settings, now), now), postedAt = 2),
            FocusAlertContent(FocusAlert(FocusPhase.WORK, FocusTimer.next(work, Focus.data.value.settings, now, true), SystemClock.elapsedRealtime())),
            VolumeContent(VolumeOverlay(VolumeStream.MEDIA, 7, 0, 15, muted = false, shownAt = SystemClock.elapsedRealtime()), 2_500),
        )
        for (c in contents) {
            PushModule.push(c)
            idle()
            assertEquals(c.key, controller.state.value.primary?.key)
            controller.onGesture(IslandGesture.TAP)
            idle()
            controller.onOutsideTouch()
            idle()
            PushModule.remove(c.key)
            idle()
        }

        // Panneau « Minuteur » : choix rapides, puis commandes du minuteur en cours.
        controller.openPanel(FocusPanel)
        idle()
        assertTrue(controller.state.value.expanded)
        Focus.start(activity, FocusPreset.Minutes(5))
        idle()
        assertNotNull(Focus.data.value.state)
        controller.closePanel()
        idle()
        scope.cancel()
    }

    @Test
    fun timerStatePersistsAndPhasesChain() {
        Focus.start(context, FocusPreset.Pomodoro)
        val started = Focus.data.value.state!!
        Focus.flushForTests()
        val file = File(context.filesDir, FocusStore.FILE_NAME)
        // « Redémarrage » : un nouvel état chargé depuis le fichier.
        Focus.resetForTests(file)
        assertEquals(started, Focus.data.value.state)

        // Fin de la séance de travail : pause courte enchaînée, alerte émise.
        val end = started.endAtWall!!
        assertFalse(Focus.completeIfDue(context, haptics = true, now = end - 1))
        assertTrue(Focus.completeIfDue(context, haptics = true, now = end + 10))
        val next = Focus.data.value.state!!
        assertEquals(FocusPhase.SHORT_BREAK, next.phase)
        assertTrue(next.running)
        assertNotNull(Focus.alert.value)

        // Fin manquée depuis longtemps (service arrêté) : rattrapée sans alerte.
        Focus.stop(context)
        assertNull(Focus.alert.value)
        Focus.start(context, FocusPreset.Minutes(5))
        val late = Focus.data.value.state!!.endAtWall!! + Focus.LATE_MS + 1
        assertTrue(Focus.completeIfDue(context, haptics = false, now = late))
        assertNull(Focus.data.value.state)
        assertNull(Focus.alert.value)
    }

    @Test
    fun volumeKeysNeverConsumedWithoutRunningIsland() {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)
        assertFalse(VolumeKeys.onKeyEvent(context, down))
        assertFalse(VolumeKeys.onKeyEvent(context, up))
        assertFalse(VolumeKeys.onKeyEvent(context, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
        assertFalse(VolumeKeys.conditions(context).moduleEnabled)
        assertNull(VolumeKeys.overlay.value)
    }

    @Test
    fun volumeAdjustPublishesOverlay() {
        VolumeKeys.adjust(context, android.media.AudioManager.ADJUST_RAISE)
        val o = VolumeKeys.overlay.value
        assertNotNull(o)
        VolumeKeys.select(context, VolumeStream.ALARM)
        assertEquals(VolumeStream.ALARM, VolumeKeys.overlay.value?.stream)
        // Le flux choisi reste en vigueur tant que le curseur est affiché.
        VolumeKeys.adjust(context, android.media.AudioManager.ADJUST_LOWER)
        assertEquals(VolumeStream.ALARM, VolumeKeys.overlay.value?.stream)
    }

    private fun render(content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { content() } }
        idle()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
        idle()
    }

    @Test
    fun settingsScreensRender() {
        render { FocusSettingsScreen(onBack = {}) }
        render { VolumeSettingsScreen(onBack = {}) }
    }
}
