package com.karelisio.bulle.modules.volet.qs

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import com.karelisio.bulle.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Tuile de réglages rapides fournie par une appli tierce (TileService).
 *
 * Une appli ne peut pas se lier à un TileService (BIND_QUICK_SETTINGS_TILE est réservée au
 * système) : son état n'est donc pas lisible. Avec Shizuku, le toucher passe par
 * `cmd statusbar click-tile` (bascule réelle, comme dans le volet système) ; sans Shizuku,
 * il ouvre l'activité de préférences de la tuile, sinon l'appli.
 */
class AppTile(
    val component: ComponentName,
    /** Libellé du service (ou de l'appli). */
    val title: String,
    /** Nom de l'appli qui fournit la tuile. */
    val appName: String,
    /** Vrai si le service déclare sa propre icône (monochrome, à teinter). */
    val hasOwnIcon: Boolean,
) : QsTile {
    override val id: String = component.flattenToString()

    @get:androidx.annotation.StringRes
    override val label: Int get() = R.string.volet_qs_app_tile

    override val labelText: String get() = title

    override val icon: ImageVector get() = Icons.Rounded.Apps

    /** Pas d'état lisible : tuile neutre. */
    override val stateless: Boolean get() = true

    override fun shizukuCommand(enable: Boolean): String? = AppTiles.clickCommand(component)

    /** Toucher sans Shizuku (et repli) : préférences de la tuile, sinon l'appli, sinon sa fiche. */
    override fun settingsIntent(context: Context): Intent? =
        AppTiles.preferencesIntent(context, component)
            ?: context.packageManager.getLaunchIntentForPackage(component.packageName)
            ?: AppTiles.appDetails(component.packageName)

    /** Appui long : préférences de la tuile, sinon fiche de l'appli. */
    override fun longPressIntent(context: Context): Intent? =
        AppTiles.preferencesIntent(context, component) ?: AppTiles.appDetails(component.packageName)

    override fun loadIcon(context: Context, sizePx: Int): ImageBitmap? = runCatching {
        val pm = context.packageManager
        val info = pm.getServiceInfo(component, 0)
        info.loadIcon(pm).toBitmap(sizePx, sizePx).asImageBitmap()
    }.getOrNull()

    override fun observe(context: Context): Flow<TileState> = flowOf(TileState(active = false, known = false))

    override fun equals(other: Any?): Boolean = other is AppTile && other.component == component
    override fun hashCode(): Int = component.hashCode()
}

/** Découverte des tuiles des applis installées et commandes associées. */
object AppTiles {
    /** Permission que tout TileService doit exiger pour être lié par le système. */
    const val BIND_PERMISSION = Manifest.permission.BIND_QUICK_SETTINGS_TILE

    /** Caractères admis dans un nom de paquet ou de classe (y compris classes internes « $ »). */
    private val NAME = Regex("[A-Za-z0-9_.$]+")

    /** Vrai si l'identifiant sauvegardé désigne une tuile d'appli (composant aplati « paquet/classe »). */
    fun isAppTileId(id: String): Boolean = '/' in id

    /**
     * Tuiles des applis installées. La visibilité des paquets est assurée par QUERY_ALL_PACKAGES
     * (déjà déclarée) : aucune balise <queries> supplémentaire n'est nécessaire.
     */
    /** Dernière recherche : (services trouvés, tuiles retenues) — affiché si la liste est vide. */
    @Volatile
    var lastScan: Pair<Int, Int>? = null
        private set

    fun discover(context: Context): List<AppTile> = runCatching {
        val pm = context.packageManager
        val intent = Intent(TileService.ACTION_QS_TILE)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }
        val kept = filter(resolved.mapNotNull { it.serviceInfo }, context.packageName) { si ->
            runCatching { si.loadLabel(pm).toString() }.getOrDefault(si.name)
        }
        lastScan = resolved.size to kept.size
        com.karelisio.bulle.island.IslandLog.log("Tuiles d'applis : ${resolved.size} services trouvés, ${kept.size} retenus")
        kept.map { si ->
            val appName = runCatching { si.applicationInfo.loadLabel(pm).toString() }.getOrDefault(si.packageName)
            val label = runCatching { si.loadLabel(pm).toString() }.getOrDefault(appName)
            AppTile(ComponentName(si.packageName, si.name), label.ifBlank { appName }, appName, si.icon != 0)
        }
    }.getOrDefault(emptyList())

    // ----- Logique pure (testée) -----

    /**
     * Services retenus : activés, exportés, protégés par BIND_QUICK_SETTINGS_TILE (sinon le système
     * lui-même les ignore), hors Bulle, sans doublon ; triés par libellé.
     */
    fun filter(services: List<ServiceInfo>, selfPackage: String, label: (ServiceInfo) -> String): List<ServiceInfo> =
        services
            .asSequence()
            .filter { it.packageName != selfPackage }
            // « enabled » du manifeste ignoré : queryIntentServices exclut déjà les composants
            // désactivés, et une appli peut activer sa tuile à l'exécution.
            .filter { it.exported && it.applicationInfo?.enabled != false }
            .filter { it.permission == BIND_PERMISSION }
            .filter { NAME.matches(it.packageName) && NAME.matches(it.name) }
            .distinctBy { it.packageName + "/" + it.name }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { label(it) })
            .toList()

    /**
     * Commande shell qui « touche » la tuile comme le volet système (bascule réelle), ou null si le
     * composant contient des caractères inattendus. Le nom est entre apostrophes : « $ » des
     * classes internes n'est pas interprété par sh.
     */
    fun clickCommand(component: ComponentName): String? = clickCommand(component.packageName, component.className)

    fun clickCommand(packageName: String, className: String): String? {
        if (!NAME.matches(packageName) || !NAME.matches(className)) return null
        return "cmd statusbar click-tile '$packageName/$className'"
    }

    /** Activité de préférences de la tuile (ACTION_QS_TILE_PREFERENCES dans son paquet), ou null. */
    fun preferencesIntent(context: Context, component: ComponentName): Intent? = runCatching {
        val pm = context.packageManager
        val query = Intent(TileService.ACTION_QS_TILE_PREFERENCES).setPackage(component.packageName)
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(query, 0)
        }
        val activity = list.firstOrNull()?.activityInfo ?: return@runCatching null
        Intent(TileService.ACTION_QS_TILE_PREFERENCES)
            .setClassName(activity.packageName, activity.name)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    }.getOrNull()

    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
}
