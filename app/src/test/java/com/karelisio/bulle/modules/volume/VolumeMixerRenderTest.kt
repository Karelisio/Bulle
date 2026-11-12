package com.karelisio.bulle.modules.volume

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sources de volume : contexte système, lecture des niveaux, Ne pas déranger, rendu de la vue étendue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VolumeMixerRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val am: AudioManager get() = context.getSystemService(AudioManager::class.java)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        VolumeKeys.hide()
    }

    @After
    fun tearDown() = VolumeKeys.hide()

    /** Active Ne pas déranger (accès accordé le temps de le faire), puis retire l'accès. */
    private fun enableDndWithoutAccess(nm: NotificationManager) {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
    }

    @Test
    fun contextFollowsSystem() {
        val base = VolumeMixer.context(context, mediaPlaying = false)
        assertFalse(base.communication)
        assertFalse(base.notificationLinked)
        assertFalse(base.mediaPlaying)
        assertTrue(VolumeMixer.context(context, mediaPlaying = true).mediaPlaying)

        am.mode = AudioManager.MODE_IN_CALL
        assertTrue(VolumeMixer.context(context, false).communication)
        am.mode = AudioManager.MODE_NORMAL

        Settings.Secure.putInt(context.contentResolver, "volume_link_notification", 1)
        assertTrue(VolumeMixer.context(context, false).notificationLinked)
        Settings.Secure.putInt(context.contentResolver, "volume_link_notification", 0)
        assertFalse(VolumeMixer.context(context, false).notificationLinked)
    }

    @Test
    fun readsLevelsAndDndBlocking() {
        val streams = VolumeLogic.streamsToShow(VolumeMixer.context(context, false))
        assertTrue(VolumeMixer.setLevel(context, VolumeStream.ALARM, 3))
        val levels = VolumeMixer.read(context, streams)
        assertEquals(streams, levels.map { it.stream })
        assertEquals(3, levels.first { it.stream == VolumeStream.ALARM }.level)
        assertTrue(levels.none { it.blocked })

        // Ne pas déranger sans accès : sonnerie et notifications désactivées, pas le média.
        val nm = context.getSystemService(NotificationManager::class.java)
        enableDndWithoutAccess(nm)
        val dnd = VolumeMixer.read(context, streams).associateBy { it.stream }
        assertTrue(dnd.getValue(VolumeStream.RING).blocked)
        assertTrue(dnd.getValue(VolumeStream.NOTIFICATION).blocked)
        assertFalse(dnd.getValue(VolumeStream.MEDIA).blocked)
        assertFalse(dnd.getValue(VolumeStream.RING).adjustable)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        assertFalse(VolumeMixer.read(context, streams).any { it.blocked })
    }

    @Test
    fun mixerSetLevelKeepsKeyStream() {
        VolumeKeys.showForTests(VolumeOverlay(VolumeStream.MEDIA, 5, 0, 15, muted = false, shownAt = SystemClock.elapsedRealtime()))
        VolumeMixer.setLevel(context, VolumeStream.ALARM, 2)
        assertEquals(VolumeStream.MEDIA, VolumeKeys.overlay.value?.stream)
    }

    @Test
    fun expandedViewRenders() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val host = object : IslandHost {
            override val context: Context = activity.get()
            override fun expand(key: String) = Unit
            override fun collapse() = Unit
            override fun dismiss(key: String) = Unit
            override fun openPanel(panel: IslandPanel) = Unit
            override fun closePanel() = Unit
            override fun requestInput(enabled: Boolean) = Unit
            override fun runAction(action: GestureAction) = Unit
        }
        val content = VolumeContent(VolumeOverlay(VolumeStream.RING, 4, 0, 7, muted = false, shownAt = SystemClock.elapsedRealtime()), 2_500)
        val nm = context.getSystemService(NotificationManager::class.java)
        enableDndWithoutAccess(nm)
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        activity.get().setContent { BulleTheme { content.Expanded(host) } }
        idle()
        assertFalse(activity.get().isFinishing)
        am.mode = AudioManager.MODE_NORMAL
        // Changement de volume ailleurs : la vue se relit sans planter.
        context.sendBroadcast(android.content.Intent("android.media.VOLUME_CHANGED_ACTION"))
        idle()
        activity.pause().stop().destroy()
        idle()
    }
}
