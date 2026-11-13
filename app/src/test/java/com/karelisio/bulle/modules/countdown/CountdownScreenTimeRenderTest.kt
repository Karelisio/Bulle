package com.karelisio.bulle.modules.countdown

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.countdown.ui.CountdownEventEditor
import com.karelisio.bulle.modules.countdown.ui.CountdownSettingsScreen
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.modules.screentime.BreakAlertContent
import com.karelisio.bulle.modules.screentime.DayUsage
import com.karelisio.bulle.modules.screentime.LimitAlertContent
import com.karelisio.bulle.modules.screentime.ScreenTimeContent
import com.karelisio.bulle.modules.screentime.ScreenTimeDetails
import com.karelisio.bulle.modules.screentime.ScreenTimePrefs
import com.karelisio.bulle.modules.screentime.ScreenTimeReport
import com.karelisio.bulle.modules.screentime.ui.ScreenTimeSettingsScreen
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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
import java.time.ZonedDateTime

/** Rendu des contenus Compte à rebours / Temps d'écran (compact puis étendu) et de leurs écrans de réglages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownScreenTimeRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        CountdownPrefs.store.resetForTests()
        ScreenTimePrefs.store.resetForTests()
    }

    @After
    fun tearDown() {
        CountdownPrefs.store.resetForTests()
        ScreenTimePrefs.store.resetForTests()
    }

    private fun render(content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BulleTheme { content() } }
        idle()
        assertFalse(activity.isFinishing)
        return activity
    }

    private val now = ZonedDateTime.now()
    private val events = listOf(
        CountdownEvent("a", "Anniversaire de Léa", "🎂", now.toLocalDate().plusDays(12).toString(), yearly = true, createdAt = System.currentTimeMillis() - 86_400_000L),
        CountdownEvent("b", "Concert", "🎸", now.toLocalDate().toString(), color = 0xFF6AD7FFL),
        CountdownEvent("c", "Départ", "✈️", now.toLocalDate().plusDays(1).toString(), time = "08:30"),
    )
    private val upcoming = CountdownMath.upcoming(events, now)
    private val report = ScreenTimeReport(
        today = DayUsage(totalMs = 8_040_000, perApp = mapOf("com.example.a" to 5_000_000, "com.example.b" to 3_040_000), unlocks = 42),
        yesterdaySameTime = DayUsage(totalMs = 6_000_000),
        yesterdayTotal = DayUsage(totalMs = 12_000_000),
    )

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

        val contents: List<IslandContent> = upcoming.map { CountdownContent(it, upcoming) } + listOf(
            ScreenTimeContent(8_040_000),
            BreakAlertContent(30, breathing = true, postedAt = android.os.SystemClock.elapsedRealtime()),
        )
        for (c in contents) {
            PushModule.push(c)
            idle()
            assertEquals(c.key, controller.state.value.primary?.key)
            if (!controller.state.value.expanded) controller.onGesture(IslandGesture.TAP)
            idle()
            assertTrue(controller.state.value.expanded)
            controller.onOutsideTouch()
            idle()
            PushModule.remove(c.key)
            idle()
        }
        // Alertes non extensibles : compact seulement.
        val compactOnly = listOf(
            CountdownReminderContent(upcoming.first(), android.os.SystemClock.elapsedRealtime()),
            LimitAlertContent("com.example.a", "Exemple", 30, android.os.SystemClock.elapsedRealtime()),
        )
        for (c in compactOnly) {
            PushModule.push(c)
            idle()
            assertEquals(c.key, controller.state.value.primary?.key)
            PushModule.remove(c.key)
            idle()
        }
        scope.cancel()
    }

    @Test
    fun statusesAndCompactWidth() {
        val today = upcoming.first { it.event.id == "b" }
        assertEquals(CountdownStatus.Today, today.status)
        val content = CountdownContent(today, upcoming)
        assertTrue(content.compactSlotWidth > CountdownContent(upcoming.first { it.event.id == "a" }, upcoming).compactSlotWidth)
        assertEquals("Concert", content.compactSubtitle)
    }

    @Test
    fun detailBodiesRenderOnLightSurface() {
        render {
            Column {
                CountdownDetails(upcoming.first(), upcoming, onDark = false)
                CountdownDetails(upcoming.last(), listOf(upcoming.last()), onDark = false)
                ScreenTimeDetails(report, hasAccess = true, onDark = false)
                ScreenTimeDetails(null, hasAccess = false, onDark = false)
            }
        }
    }

    @Test
    fun countdownSettingsScreenRenders() {
        render { CountdownSettingsScreen(onBack = {}) }
        events.forEach { e -> CountdownPrefs.store.update(context) { CountdownPrefs.upsert(it, e) } }
        render { CountdownSettingsScreen(onBack = {}) }
        render { CountdownEventEditor(initial = events.last(), onDismiss = {}, onSave = {}) }
        render { CountdownEventEditor(initial = null, onDismiss = {}, onSave = {}) }
    }

    @Test
    fun screenTimeSettingsScreenRenders() {
        ScreenTimePrefs.store.update(context) { it.copy(appLimits = mapOf("com.example.a" to 30)) }
        render { ScreenTimeSettingsScreen(onBack = {}) }
    }
}
