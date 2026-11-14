package com.karelisio.bulle.modules.volet.qs

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.bulle.core.actions.GestureAction
import kotlinx.coroutines.flow.Flow

/** État affiché par une tuile. */
data class TileState(
    val active: Boolean = false,
    /** Faux quand l'état ne peut pas être lu (permission manquante…) : la tuile s'affiche inactive. */
    val known: Boolean = true,
)

/** Bascule par API publique (lampe, Ne pas déranger, rotation, luminosité auto). */
interface DirectToggle {
    /** Vrai si l'appli a le droit de basculer (accès Ne pas déranger, WRITE_SETTINGS…). */
    fun permitted(context: Context): Boolean

    /** Page système qui accorde ce droit. */
    fun grantIntent(context: Context): Intent

    fun set(context: Context, enable: Boolean)
}

/**
 * Une tuile de réglages rapides. Chaque tuile déclare ses moyens de bascule ; le choix
 * effectif est fait par [ToggleChooser] selon les droits et la présence de Shizuku.
 */
interface QsTile {
    /** Identifiant stable, sauvegardé dans VoletSettings.tiles. */
    val id: String

    @get:StringRes
    val label: Int

    val icon: ImageVector

    /** Libellé dynamique (tuiles d'applis) : prioritaire sur [label] quand il est fourni. */
    val labelText: String? get() = null

    /** Tuile d'action ponctuelle (capture, verrouillage) : pas d'état actif. */
    val action: GestureAction? get() = null

    /** Pas d'état actif affiché (actions ponctuelles, tuiles d'applis dont l'état est illisible). */
    val stateless: Boolean get() = action != null

    val direct: DirectToggle? get() = null

    /** Commande shell (via Shizuku) qui active ou désactive, ou null. */
    fun shizukuCommand(enable: Boolean): String? = null

    /** Panneau flottant des réglages (Settings.Panel), ou null. */
    fun panelIntent(): Intent? = null

    /** Page système de la tuile : appui long, et dernier recours au toucher (null : aucune). */
    fun settingsIntent(context: Context): Intent?

    /** Page ouverte par un appui long (par défaut : [settingsIntent]). */
    fun longPressIntent(context: Context): Intent? = settingsIntent(context)

    /** Icône bitmap (tuiles d'applis) : prioritaire sur [icon] quand elle se charge. */
    fun loadIcon(context: Context, sizePx: Int): ImageBitmap? = null

    /** Faux si le matériel manque (pas de NFC, pas de flash…) : la tuile n'est pas proposée. */
    fun isSupported(context: Context): Boolean = true

    /** État observé (rafraîchi par diffusions / observateurs tant qu'il est collecté). */
    fun observe(context: Context): Flow<TileState>
}

/** Moyen retenu pour basculer une tuile au toucher. */
enum class ToggleMethod {
    /** Action de geste (capture, verrouillage). */
    ACTION,
    /** API publique, droit accordé. */
    DIRECT,
    /** Commande shell via Shizuku (en demandant sa permission au besoin). */
    SHIZUKU,
    /** API publique mais droit manquant : ouvrir la page qui l'accorde. */
    GRANT,
    /** Panneau flottant Settings.Panel. */
    PANEL,
    /** Page de réglages système. */
    SETTINGS,
}

/** Disponibilité de Shizuku. */
enum class ShizukuStatus { UNAVAILABLE, NEEDS_PERMISSION, READY }

/** Choix de la méthode de bascule (logique pure, testée). */
object ToggleChooser {
    fun choose(
        hasAction: Boolean,
        hasDirect: Boolean,
        directPermitted: Boolean,
        hasShizukuCommand: Boolean,
        shizuku: ShizukuStatus,
        hasPanel: Boolean,
    ): ToggleMethod = when {
        hasAction -> ToggleMethod.ACTION
        hasDirect && directPermitted -> ToggleMethod.DIRECT
        hasShizukuCommand && shizuku != ShizukuStatus.UNAVAILABLE -> ToggleMethod.SHIZUKU
        hasDirect -> ToggleMethod.GRANT
        hasPanel -> ToggleMethod.PANEL
        else -> ToggleMethod.SETTINGS
    }

    /** Repli quand Shizuku échoue ou que sa permission est refusée. */
    fun fallback(hasDirect: Boolean, hasPanel: Boolean): ToggleMethod = when {
        hasDirect -> ToggleMethod.GRANT
        hasPanel -> ToggleMethod.PANEL
        else -> ToggleMethod.SETTINGS
    }

    fun choose(tile: QsTile, context: Context, shizuku: ShizukuStatus): ToggleMethod = choose(
        hasAction = tile.action != null,
        hasDirect = tile.direct != null,
        directPermitted = tile.direct?.permitted(context) == true,
        hasShizukuCommand = tile.shizukuCommand(true) != null,
        shizuku = shizuku,
        hasPanel = tile.panelIntent() != null,
    )
}
