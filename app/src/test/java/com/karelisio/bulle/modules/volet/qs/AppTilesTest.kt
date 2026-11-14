package com.karelisio.bulle.modules.volet.qs

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZone
import com.karelisio.bulle.modules.volet.ui.VoletOrientation
import com.karelisio.bulle.modules.volet.ui.VoletPanelContent
import com.karelisio.bulle.ui.theme.BulleTheme
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

/** Hôte de test : états Compose modifiables, activités lancées enregistrées. */
private class AppTileTestHost(override val context: Context) : VoletHost {
    override var expansion by mutableFloatStateOf(1f)
    override val zone = VoletZone.QUICK_SETTINGS
    override var settings by mutableStateOf(VoletSettings(enabled = true))
    override val locked = false
    override val focusKey: String? = null
    override var editingTiles by mutableStateOf(false)
    val started = mutableListOf<Intent>()

    override fun close() = Unit
    override fun expandFull() { expansion = 2f }
    override fun collapseToHalf() { expansion = 1f }
    override fun dragBy(dyPx: Float) = Unit
    override fun settle(velocityPxPerSec: Float) = Unit
    override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean) = false
    override fun startActivity(intent: Intent) { started += intent }
    override fun runAction(action: GestureAction) = Unit
    override fun requestKeyboard(show: Boolean) = Unit
    override fun openSystemShade() = Unit
}

/** Tuiles des applis tierces : découverte, commande Shizuku, réglages, éditeur et volet en paysage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppTilesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun service(
        pkg: String,
        cls: String,
        permission: String? = Manifest.permission.BIND_QUICK_SETTINGS_TILE,
        exported: Boolean = true,
        enabled: Boolean = true,
    ) = ServiceInfo().apply {
        packageName = pkg
        name = cls
        this.permission = permission
        this.exported = exported
        this.enabled = enabled
        applicationInfo = ApplicationInfo().apply { packageName = pkg; this.enabled = true }
    }

    @Test
    fun filterKeepsValidThirdPartyTiles() {
        val list = listOf(
            service("com.zeta", "com.zeta.ZTile"),
            service("com.alpha", "com.alpha.Tile\$Inner"),
            service(context.packageName, "com.karelisio.bulle.SomeTile"), // Bulle elle-même
            service("com.noperm", "com.noperm.Tile", permission = null), // ignorée par le système
            service("com.hidden", "com.hidden.Tile", exported = false),
            service("com.off", "com.off.Tile", enabled = false), // activable à l'exécution : gardée
            service("com.zeta", "com.zeta.ZTile"), // doublon
            service("com.bad", "com.bad.Tile;rm -rf"), // nom suspect
        )
        val kept = AppTiles.filter(list, context.packageName) { it.packageName }
        assertEquals(listOf("com.alpha", "com.off", "com.zeta"), kept.map { it.packageName })
    }

    @Test
    fun clickCommand() {
        assertEquals(
            "cmd statusbar click-tile 'com.foo/com.foo.Tile\$Inner'",
            AppTiles.clickCommand(ComponentName("com.foo", "com.foo.Tile\$Inner")),
        )
        assertNull(AppTiles.clickCommand("com.foo", "x'; reboot; '"))
        assertNull(AppTiles.clickCommand("", "com.foo.Tile"))
        val tile = AppTile(ComponentName("com.foo", "com.foo.Tile"), "Tuile", "Foo", hasOwnIcon = true)
        assertEquals("com.foo/com.foo.Tile", tile.id)
        assertEquals(AppTiles.clickCommand(tile.component), tile.shizukuCommand(true))
        assertEquals(tile.shizukuCommand(true), tile.shizukuCommand(false))
        assertTrue(tile.stateless)
        // Sans Shizuku : ouverture de l'appli (ou de sa fiche), jamais une bascule impossible.
        assertEquals(ToggleMethod.SETTINGS, ToggleChooser.choose(tile, context, ShizukuStatus.UNAVAILABLE))
        assertEquals(ToggleMethod.SHIZUKU, ToggleChooser.choose(tile, context, ShizukuStatus.READY))
    }

    @Test
    fun idsAndOrder() {
        assertTrue(AppTiles.isAppTileId("com.foo/com.foo.Tile"))
        assertFalse(AppTiles.isAppTileId("wifi"))
        val app = AppTile(ComponentName("com.foo", "com.foo.Tile"), "Tuile", "Foo", hasOwnIcon = true)
        // Tuile d'appli enregistrée : placée à son rang ; désinstallée : ignorée sans tout réinitialiser.
        val saved = listOf("wifi", app.id, "rotation")
        val withApp = QsTileRegistry.current(context, saved, listOf(app)).map { it.id }
        assertEquals(listOf("wifi", app.id, "rotation"), withApp.filter { it in saved })
        val without = QsTileRegistry.current(context, saved, emptyList()).map { it.id }
        assertFalse(app.id in without)
        assertTrue("wifi" in without)
        assertEquals(listOf(app), QsTileRegistry.availableApps(listOf(app), listOf("wifi")))
        assertTrue(QsTileRegistry.availableApps(listOf(app), saved).isEmpty())
    }

    @Test
    fun settingsJsonCompatible() {
        // Ancien JSON sans tuiles : valeurs par défaut, aucune tuile d'appli.
        val old = BulleJson.decodeFromString(VoletSettings.serializer(), """{"enabled":true,"collapsedTileCount":5}""")
        assertTrue(old.tiles.isEmpty())
        assertEquals(5, old.collapsedTileCount)
        // Identifiant = composant aplati, conservé tel quel.
        val s = VoletSettings(tiles = listOf("wifi", "com.foo/com.foo.Tile\$Inner"))
        val back = BulleJson.decodeFromString(VoletSettings.serializer(), BulleJson.encodeToString(VoletSettings.serializer(), s))
        assertEquals(s, back)
    }

    /** Déclare une fausse tuile d'appli dans le gestionnaire de paquets de Robolectric. */
    private fun installFakeTile(): ComponentName {
        val pkg = "com.example.tiles"
        val component = ComponentName(pkg, "$pkg.CoffeeTile")
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
        })
        pm.addOrUpdateService(service(pkg, component.className))
        pm.addIntentFilterForService(component, IntentFilter(TileService.ACTION_QS_TILE))
        return component
    }

    @Test
    fun discoveryAndIntents() {
        val component = installFakeTile()
        val found = AppTiles.discover(context)
        assertEquals(listOf(component), found.map { it.component })
        assertTrue(found.none { it.component.packageName == context.packageName })
        val tile = found.single()
        // Pas d'activité de préférences : appui long → fiche de l'appli.
        assertNull(AppTiles.preferencesIntent(context, component))
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, tile.longPressIntent(context)?.action)
        assertNotNull(tile.settingsIntent(context))
        // Toucher sans Shizuku : ouvre (pas de bascule).
        val host = AppTileTestHost(context)
        QsToggler(host).tap(tile, TileState(known = false))
        assertEquals(1, host.started.size)
    }

    @Test
    fun editorShowsAppsSection() {
        installFakeTile()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = AppTileTestHost(activity)
        host.editingTiles = true
        activity.setContent { BulleTheme { QuickSettingsSection(host, Modifier.fillMaxWidth()) } }
        idle()
        // Ajout d'une tuile d'appli, puis rendu du volet avec elle (rangée et grille).
        val app = AppTiles.discover(activity).single()
        host.settings = host.settings.copy(tiles = QsTileRegistry.defaultOrder.take(3) + app.id)
        host.editingTiles = false
        idle()
        for (e in listOf(1f, 2f, 1.5f)) {
            host.expansion = e
            idle()
        }
        assertTrue(QsTileRegistry.current(activity, host.settings.tiles).any { it.id == app.id })
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun landscapePanelRenders() {
        installFakeTile()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = AppTileTestHost(activity)
        val seen = mutableListOf<VoletOrientation>()
        activity.setContent { BulleTheme { VoletPanelContent(host, { 0f }) { seen += it } } }
        idle()
        for (e in listOf(1f, 2f, 1.3f, 1f)) {
            host.expansion = e
            idle()
        }
        // Toutes les tuiles : la grille paysage passe en pages.
        host.settings = host.settings.copy(tiles = QsTileRegistry.current(activity, emptyList()).map { it.id })
        host.expansion = 2f
        idle()
        host.editingTiles = true
        idle()
        assertEquals(VoletOrientation.LANDSCAPE, seen.last())
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port")
    fun portraitPanelRenders() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val host = AppTileTestHost(activity)
        val seen = mutableListOf<VoletOrientation>()
        activity.setContent { BulleTheme { VoletPanelContent(host, { 0f }) { seen += it } } }
        idle()
        host.expansion = 2f
        idle()
        assertEquals(VoletOrientation.PORTRAIT, seen.last())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
