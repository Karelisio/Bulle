package com.karelisio.bulle.ui.shell

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.ui.theme.BulleTheme
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

/** Rendu de chaque onglet de la coque, et retour système vers l'accueil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShellRenderTest {

    @Before
    fun noAnimations() {
        val cr = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    // Avance l’horloge : laisse passer quelques trames.
    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))

    private fun renderTab(tab: ShellTab) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { ShellScreen(selected = tab, onSelect = {}, onOpen = {}) } }
        idle()
        controller.pause().stop().destroy()
    }

    @Test fun homeTabRenders() = renderTab(ShellTab.HOME)
    @Test fun lookTabRenders() = renderTab(ShellTab.LOOK)
    @Test fun contentsTabRenders() = renderTab(ShellTab.CONTENTS)
    @Test fun settingsTabRenders() = renderTab(ShellTab.SETTINGS)

    @Test
    fun statefulShellStartsOnHome() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { BulleShell(onOpen = {}) } }
        idle()
        // À l'accueil, la coque n'intercepte pas « retour » : l'appli se ferme.
        assertFalse(controller.get().isFinishing)
        controller.get().onBackPressedDispatcher.onBackPressed()
        idle()
        assertTrue(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun backFromSecondaryTabGoesHomeWithoutQuitting() {
        // Note : la recomposition après coup n'est pas fiable dans Robolectric quand plusieurs
        // tests Compose partagent le bac à sable ; on vérifie donc l'effet du premier « retour »,
        // le relâchement à l'accueil étant couvert par statefulShellStartsOnHome et ShellNavigationTest.
        for (start in ShellTab.entries - ShellTab.HOME) {
            var tab by mutableStateOf(start)
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = controller.get()
            activity.setContent { BulleTheme { ShellScreen(selected = tab, onSelect = { tab = it }, onOpen = {}) } }
            idle()
            activity.onBackPressedDispatcher.onBackPressed()
            idle()
            assertEquals(ShellTab.HOME, tab)
            assertFalse(activity.isFinishing)
            controller.pause().stop().destroy()
        }
    }
}
