package com.karelisio.bulle.modules.calendar

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.calendar.ui.CalendarSettingsScreen
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.modules.weather.Forecast
import com.karelisio.bulle.modules.weather.Quarter
import com.karelisio.bulle.modules.weather.RainSoon
import com.karelisio.bulle.modules.weather.WeatherIdleContent
import com.karelisio.bulle.modules.weather.WeatherKind
import com.karelisio.bulle.modules.weather.WeatherRainContent
import com.karelisio.bulle.modules.weather.ui.WeatherGlyph
import com.karelisio.bulle.modules.weather.ui.WeatherSettingsScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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

/** Rendu des contenus Agenda et Météo dans l'île (compact puis étendu) et de leurs écrans de réglages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarWeatherRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun render(content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { content() } }
        idle()
        assertFalse(activity.isFinishing)
        return activity
    }

    @Test
    fun contentsRenderInIsland() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()

        val now = System.currentTimeMillis()
        val event = CalendarEvent(1, 10, "Réunion d'équipe", "10 rue de la Paix, Paris", now + 8 * 60_000L, now + 68 * 60_000L, color = 0xFF3366CC.toInt())
        val forecast = Forecast(
            fetchedAt = now, latitude = 48.86, longitude = 2.35, temperature = 11.4, weatherCode = 2,
            quarters = (1..8).map { Quarter(now + it * 15 * 60_000L, if (it > 2) 0.8 else 0.0) },
        )
        val contents: List<IslandContent> = listOf(
            CalendarReminderContent(event, postedAt = 1),
            CalendarReminderContent(event.copy(location = null, title = ""), postedAt = 2),
            CalendarOngoingContent(event.copy(begin = now - 600_000L), postedAt = 3),
            WeatherRainContent(RainSoon(now + 30 * 60_000L, 30, 0.8), forecast, postedAt = 4),
            WeatherIdleContent(forecast, place = "Paris"),
        )
        for (c in contents) {
            PushModule.push(c)
            idle()
            assertEquals(c.key, controller.state.value.primary?.key)
            // Les rappels et alertes s'étendent tout seuls ; sinon un toucher ouvre la vue étendue.
            if (!controller.state.value.expanded) controller.onGesture(IslandGesture.TAP)
            idle()
            assertTrue(controller.state.value.expanded)
            controller.onOutsideTouch()
            idle()
            PushModule.remove(c.key)
            idle()
        }
        scope.cancel()
    }

    @Test
    fun allWeatherGlyphsDraw() {
        render {
            androidx.compose.foundation.layout.Row {
                WeatherKind.entries.forEach { kind ->
                    WeatherGlyph(kind, isDay = true)
                    WeatherGlyph(kind, isDay = false)
                }
            }
        }
    }

    @Test
    fun settingsScreensRender() {
        render { CalendarSettingsScreen(onBack = {}) }
        render { WeatherSettingsScreen(onBack = {}) }
    }

    @Test
    fun intentsTargetGeoAndEvent() {
        assertEquals("geo:0,0?q=10%20rue%20de%20la%20Paix%2C%20Paris", CalendarIntents.directionsUri("10 rue de la Paix, Paris").toString())
        assertEquals("content://com.android.calendar/events/42", CalendarIntents.eventUri(42).toString())
        // Aucune appli capable : pas de plantage.
        CalendarIntents.openDirections(context, "Lyon")
        CalendarIntents.openEvent(context, 42)
    }

    @Test
    fun queryWithoutPermissionIsEmpty() {
        assertTrue(CalendarSource.query(context, 0, Long.MAX_VALUE / 2).isEmpty())
    }
}
