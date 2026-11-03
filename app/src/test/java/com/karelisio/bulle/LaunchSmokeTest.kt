package com.karelisio.bulle

import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import com.karelisio.bulle.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vérifie que l'appli se lance sans planter (thème, splash, Compose, services). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchSmokeTest {
    @Test
    fun mainActivityStarts() {
        // Animations coupées : les animations infinies ne bouclent pas dans Robolectric.
        val cr = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
