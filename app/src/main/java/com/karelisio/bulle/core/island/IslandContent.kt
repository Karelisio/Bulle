package com.karelisio.bulle.core.island

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Un contenu affichable par l'île (une musique, un minuteur, une alerte…).
 *
 * Un contenu fournit :
 *  - son identité ([key], [moduleId]) et sa [priority] ;
 *  - sa vue compacte, de part et d'autre de la caméra ([Leading], [Trailing]) ;
 *  - sa vue minimale pour la bulle secondaire ([Minimal]) ;
 *  - sa vue étendue ([Expanded]).
 *
 * Les implémentations sont idéalement des `data class` : l'égalité sert à détecter
 * les changements sans recomposer inutilement.
 */
abstract class IslandContent {
    /** Identifiant stable et unique (ex. "media:com.spotify.music"). */
    abstract val key: String
    abstract val moduleId: String
    abstract val priority: Priority

    /** Instant de publication (elapsedRealtime) : départage à priorité égale, base de [timeoutMs]. */
    open val postedAt: Long = SystemClock.elapsedRealtime()

    /** Durée de vie d'un contenu ponctuel (alerte). null = tant que le module l'émet. */
    open val timeoutMs: Long? = null

    /** Si non null, l'île s'étend toute seule à l'arrivée du contenu pendant cette durée. */
    open val autoExpandMs: Long? = null

    /** Couleur d'accent (éclats, reflets). */
    open val accent: Color? = null

    /** Largeur de chaque emplacement compact (à gauche et à droite de la caméra). */
    open val compactSlotWidth: Dp = 40.dp

    /** Peut s'afficher dans la bulle secondaire détachée. */
    open val canBeSecondary: Boolean = true

    /** Possède une vue étendue. */
    open val expandable: Boolean = true

    /** Clé de la notification d'origine : l'appui long ouvre le Volet dessus. */
    open val notificationKey: String? = null

    /** Reste visible même quand l'île est masquée (plein écran, appli exclue…). Ex. : appel entrant. */
    open val showWhenHidden: Boolean = false

    /**
     * Petite seconde ligne optionnelle sous [Trailing] en mode compact (réglage « deux lignes »),
     * affichée seulement si la hauteur de l'île le permet. null = une seule ligne.
     */
    open val compactSubtitle: String? = null

    val expiresAt: Long? get() = timeoutMs?.let { postedAt + it }

    @Composable
    abstract fun Leading()

    @Composable
    open fun Trailing() = Unit

    @Composable
    open fun Minimal() = Leading()

    @Composable
    open fun Expanded(host: IslandHost) = Unit

    /** Toucher sur la vue étendue (hors boutons). Retourne true si géré, sinon l'île se replie. */
    open fun onExpandedTap(host: IslandHost): Boolean = false
}
