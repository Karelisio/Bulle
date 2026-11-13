package com.karelisio.bulle.modules.external

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.ecosystem.Ecosystem
import com.karelisio.bulle.modules.ecosystem.KarelisioApp
import com.karelisio.bulle.modules.external.ui.BreathingExercise
import com.karelisio.bulle.modules.external.ui.BreathingPanel
import com.karelisio.bulle.modules.external.ui.IntegrationsSettingsScreen
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.karelisio.bulle.modules.external.ExternalApi as Api

/** API d'intégration : récepteur, module, rendu de chaque modèle, panneau de respiration, écran, écosystème. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalRobolectricTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        ExternalModule.resetForTests(context)
    }

    @After
    fun tearDown() = ExternalModule.resetForTests(context)

    private class RecordingHost(override val context: Context) : IslandHost {
        var panel: IslandPanel? = null
        val dismissed = mutableListOf<String>()
        override fun expand(key: String) = Unit
        override fun collapse() = Unit
        override fun dismiss(key: String) { dismissed += key }
        override fun openPanel(panel: IslandPanel) { this.panel = panel }
        override fun closePanel() { panel = null }
        override fun requestInput(enabled: Boolean) = Unit
        override fun runAction(action: GestureAction) = Unit
    }

    private fun identity(): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent("bulle.identity").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)

    private fun broadcast(action: String, vararg extras: Pair<String, Any>, sender: PendingIntent? = identity()): Intent {
        val intent = Intent(action).setPackage(context.packageName)
        sender?.let { intent.putExtra(Api.EXTRA_SENDER, it) }
        extras.forEach { (k, v) ->
            when (v) {
                is String -> intent.putExtra(k, v)
                is Int -> intent.putExtra(k, v)
                is Long -> intent.putExtra(k, v)
                is Boolean -> intent.putExtra(k, v)
                is Array<*> -> if (v.isArrayOf<String>()) intent.putExtra(k, v as Array<String>) else intent.putExtra(k, v.filterIsInstance<PendingIntent>().toTypedArray())
                is PendingIntent -> intent.putExtra(k, v)
            }
        }
        return intent
    }

    @Test
    fun receiverShowsUpdatesAndDismisses() {
        val receiver = ExternalReceiver()
        val actions = Array(4) { i -> PendingIntent.getBroadcast(context, i + 1, Intent("a$i").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE) }
        receiver.onReceive(
            context,
            broadcast(
                Api.ACTION_SHOW,
                Api.EXTRA_ID to "build",
                Api.EXTRA_TEMPLATE to "progress",
                Api.EXTRA_TITLE to "Compilation",
                Api.EXTRA_PROGRESS to 30,
                Api.EXTRA_PRIORITY to "call",
                Api.EXTRA_ACTION_LABELS to arrayOf("Un", "Deux", "Trois", "Quatre"),
                Api.EXTRA_ACTION_INTENTS to actions,
                Api.EXTRA_CONTENT_INTENT to actions[0],
            ),
        )
        val key = "${context.packageName}/build"
        val shown = ExternalModule.board.value.items.single()
        assertEquals(key, shown.key)
        assertEquals(ExternalTemplate.PROGRESS, shown.template)
        assertEquals(ExternalPriority.LIVE, shown.priority)
        assertEquals(Api.MAX_ACTIONS, shown.actions.size)
        assertEquals(listOf("Un", "Deux", "Trois"), shown.actions.map { it.label })
        assertTrue(shown.contentIntent != null)
        assertTrue(ExternalModule.apps.value.containsKey(context.packageName))

        receiver.onReceive(context, broadcast(Api.ACTION_UPDATE, Api.EXTRA_ID to "build", Api.EXTRA_TEMPLATE to "progress", Api.EXTRA_PROGRESS to 80))
        assertEquals(80, ExternalModule.board.value.items.single().progress)

        receiver.onReceive(context, broadcast(Api.ACTION_DISMISS, Api.EXTRA_ID to "build"))
        assertTrue(ExternalModule.board.value.items.isEmpty())
    }

    @Test
    fun receiverRejectsUnknownCaller() {
        val receiver = ExternalReceiver()
        // Sans jeton d'identité : ignoré.
        receiver.onReceive(context, broadcast(Api.ACTION_SHOW, Api.EXTRA_ID to "x", sender = null))
        assertTrue(ExternalModule.board.value.items.isEmpty())
        // Identité système contradictoire avec le jeton : refusé.
        val intent = broadcast(Api.ACTION_SHOW, Api.EXTRA_ID to "x")
        assertNull(ExternalReceiver.resolveCaller(context, intent, "com.other.app"))
        // Appli tierce sans la permission : refusée.
        assertNull(ExternalReceiver.resolveCaller(context, Intent(Api.ACTION_SHOW), "com.other.app"))
        assertEquals(context.packageName, ExternalReceiver.resolveCaller(context, intent, null))
        // id manquant : rien.
        receiver.onReceive(context, broadcast(Api.ACTION_SHOW, Api.EXTRA_TITLE to "sans id"))
        assertTrue(ExternalModule.board.value.items.isEmpty())
    }

    @Test
    fun moduleIgnoresBlockedAppsAndLimitsPerApp() {
        val pkg = "com.example.client"
        fun show(id: String) = ExternalCommand.Show(ExternalSpec(pkg, id, receivedAt = 1))
        for (i in 1..5) assertTrue(ExternalModule.handle(context, show("$i"), nowElapsed = i.toLong()))
        assertEquals(Api.MAX_PER_APP, ExternalModule.board.value.forApp(pkg).size)

        ExternalModule.setBlocked(context, pkg, true)
        assertTrue(ExternalModule.board.value.forApp(pkg).isEmpty())
        assertFalse(ExternalModule.handle(context, show("6")))
        assertTrue(ExternalModule.board.value.items.isEmpty())
        assertTrue(ExternalModule.apps.value.getValue(pkg).blocked)

        // Persistance : rechargée depuis les préférences.
        ExternalModule.resetForTests()
        ExternalModule.ensureLoaded(context)
        assertTrue(ExternalModule.apps.value.getValue(pkg).blocked)

        ExternalModule.setBlocked(context, pkg, false)
        assertTrue(ExternalModule.handle(context, show("7")))
        ExternalModule.forget(context, pkg)
        assertFalse(ExternalModule.apps.value.containsKey(pkg))
        assertTrue(ExternalModule.board.value.items.isEmpty())
        assertTrue(ExternalModule.enabledByDefault)
        assertEquals("external", ExternalModule.id)
    }

    @Test
    fun contentMapsPriorityAndKey() {
        val now = System.currentTimeMillis()
        val down = ExternalContent(ExternalSamples.of(context, ExternalTemplate.STATUS, now))
        assertEquals(Priority.ALERT, down.priority)
        assertEquals("external:${context.packageName}/demo-status", down.key)
        assertEquals(ExternalContent.StatusDown, ExternalContent.statusColor(ExternalStatus.DOWN))
        val live = ExternalContent(ExternalSamples.of(context, ExternalTemplate.PROGRESS, now))
        assertEquals(Priority.LIVE, live.priority)
        assertNull(live.autoExpandMs)
        assertFalse(ExternalContent(ExternalSamples.of(context, ExternalTemplate.BREATHING, now)).canBeSecondary)
    }

    @Test
    fun everyTemplateRendersCompactAndExpanded() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = RecordingHost(activity)
        val now = System.currentTimeMillis()
        val specs = ExternalSamples.all(activity, now) + listOf(
            ExternalSamples.of(activity, ExternalTemplate.PROGRESS, now).copy(indeterminate = true, id = "ind"),
            ExternalSamples.of(activity, ExternalTemplate.STATUS, now).copy(status = ExternalStatus.OK, id = "ok"),
            ExternalSamples.of(activity, ExternalTemplate.STATUS, now).copy(status = ExternalStatus.WARN, id = "warn"),
            ExternalSamples.of(activity, ExternalTemplate.COUNTDOWN, now).copy(startTime = null, id = "nostart"),
            ExternalSpec(packageName = "com.absent.app", id = "bare", local = false),
        )
        val contents = specs.map { ExternalContent(it) }
        activity.setContent {
            BulleTheme {
                Column {
                    contents.forEach { c ->
                        Row { c.Leading(); c.Trailing(); c.Minimal() }
                        c.Expanded(host)
                    }
                }
            }
        }
        idle()
        assertFalse(activity.isFinishing)
    }

    @Test
    fun templatesRenderInsideTheIsland() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()
        for (spec in ExternalSamples.all(activity, System.currentTimeMillis())) {
            val c = ExternalContent(spec.copy(receivedAt = android.os.SystemClock.elapsedRealtime()))
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
        controller.openPanel(BreathingPanel(BreathPattern.BOX))
        idle()
        assertTrue(controller.state.value.expanded)
        controller.closePanel()
        idle()
        scope.cancel()
    }

    @Test
    fun breathingPanelRenders() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var closed = false
        activity.setContent { BulleTheme { BreathingExercise(BreathPattern.RELAX_478, 4, onClose = { closed = true }) } }
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        assertFalse(closed)
        val host = RecordingHost(activity)
        activity.setContent { BulleTheme { BreathingPanel().Content(host) } }
        idle()
        assertEquals("breathing", BreathingPanel().id)
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
    fun integrationsScreenRenders() {
        render { IntegrationsSettingsScreen(onBack = {}) }
        ExternalModule.handle(context, ExternalCommand.Show(ExternalSpec("com.example.client", "a", title = "Sauvegarde", receivedAt = 1)))
        ExternalModule.handle(context, ExternalCommand.Show(ExternalSpec("com.example.other", "b", receivedAt = 1)))
        ExternalModule.setBlocked(context, "com.example.other", true)
        render { IntegrationsSettingsScreen(onBack = {}) }
    }

    @Test
    fun ecosystemActions() {
        val app = context as android.app.Application
        // Écho absente : rien n'est envoyé, retour visuel local.
        assertFalse(Ecosystem.sendThought(context))
        val feedback = ExternalModule.board.value.items.single()
        assertTrue(feedback.local)
        assertEquals(KarelisioApp.ECHO.packageName, feedback.packageName)
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == Ecosystem.ACTION_SEND_THOUGHT })

        // Écho installée : broadcast explicite vers son paquet.
        install(KarelisioApp.ECHO.packageName)
        install(KarelisioApp.EXIT.packageName)
        install(KarelisioApp.RESERVE.packageName)
        assertTrue(Ecosystem.sendThought(context))
        val sent = shadowOf(app).broadcastIntents.last { it.action == Ecosystem.ACTION_SEND_THOUGHT }
        assertEquals(KarelisioApp.ECHO.packageName, sent.`package`)
        assertEquals(context.packageName, sent.getStringExtra(Ecosystem.EXTRA_SOURCE))

        assertTrue(Ecosystem.requestFuel(context))
        assertTrue(shadowOf(app).broadcastIntents.any { it.action == Ecosystem.ACTION_REQUEST_FUEL && it.`package` == KarelisioApp.RESERVE.packageName })

        val host = RecordingHost(context)
        assertTrue(Ecosystem.openCraving(host))
        assertTrue(host.panel is BreathingPanel)
        assertTrue(shadowOf(app).broadcastIntents.any { it.action == Ecosystem.ACTION_CRAVING && it.`package` == KarelisioApp.EXIT.packageName })

        assertEquals(ExternalTemplate.STATUS, Ecosystem.presetFor(KarelisioApp.VIGIE.packageName)?.template)
        assertNull(Ecosystem.presetFor("com.example.unknown"))
    }

    private fun install(pkg: String) {
        val info = PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        }
        shadowOf(context.packageManager).installPackage(info)
    }
}
