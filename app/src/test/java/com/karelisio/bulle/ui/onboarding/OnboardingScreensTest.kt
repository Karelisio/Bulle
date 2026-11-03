package com.karelisio.bulle.ui.onboarding

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.ui.theme.BulleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Compose chaque écran (prise en main, autorisations, fiabilité) : aucun ne doit planter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingScreensTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Animations coupées : les animations infinies ne bouclent pas dans Robolectric.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun render(content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { content() } }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
    }

    @Test
    fun onboardingRenders() = render { OnboardingScreen(onFinished = {}) }

    @Test
    fun permissionsRenders() = render { PermissionsScreen(onBack = {}) }

    @Test
    fun reliabilityRenders() = render { ReliabilityScreen(onBack = {}) }

    @Test
    fun snapshotCoversEveryPermission() {
        val snapshot = PermissionChecks.snapshot(context)
        assertEquals(PermissionKey.entries.size, snapshot.size)
        assertFalse(snapshot.getValue(PermissionKey.ACCESSIBILITY))
        assertFalse(snapshot.getValue(PermissionKey.OEM_LAUNCH))
        // « C'est fait » est mémorisé.
        PermissionChecks.setConfirmed(context, PermissionKey.OEM_LAUNCH, true)
        assertTrue(PermissionChecks.isGranted(context, PermissionKey.OEM_LAUNCH))
    }

    @Test
    fun everyPermissionHasASettingsRouteOrIsSpecial() {
        PermissionKey.entries.forEach { key ->
            val special = key == PermissionKey.SECURE_SETTINGS || key == PermissionKey.OEM_LAUNCH
            if (!special) assertNotNull(key.name, PermissionChecks.settingsIntent(context, key))
        }
    }
}
