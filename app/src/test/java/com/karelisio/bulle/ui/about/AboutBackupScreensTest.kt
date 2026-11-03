package com.karelisio.bulle.ui.about

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.backup.BackupScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/** Compose les écrans Sauvegarde et À propos sans planter (sans réseau). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutBackupScreensTest {

    @Before
    fun noAnimations() {
        val cr = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun compose(content: @androidx.compose.runtime.Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { content() } }
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }

    @Test
    fun backupScreenComposes() = compose { BackupScreen(onBack = {}) }

    @Test
    fun aboutScreenComposes() = compose { AboutScreen(onBack = {}, onOpenPermissions = {}) }

    @Test
    fun changelogAssetIsBundled() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val text = ctx.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        assertTrue(Changelog.sections(text).isNotEmpty())
    }
}
