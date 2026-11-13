package com.karelisio.bulle.core.island

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.core.a11y.IslandSpeech

/** Action proposée à TalkBack sur l'île (libellé lu + action). */
class IslandAccessibilityAction(val label: String, val run: () -> Unit)

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
     * Si vrai, un toucher hors de l'île étendue ne la replie pas : seul un toucher sur l'île
     * (hors boutons) ou un glisser vers le haut la ferme. Ex. : paroles synchronisées en cours.
     */
    open val stayExpandedOnOutsideTouch: Boolean = false

    /**
     * Petite seconde ligne optionnelle sous [Trailing] en mode compact (réglage « deux lignes »),
     * affichée seulement si la hauteur de l'île le permet. null = une seule ligne.
     */
    open val compactSubtitle: String? = null

    /**
     * Résumé lu par TalkBack (ex. « Musique : Titre, Artiste, en lecture »). null = repli sur
     * [describe], puis sur [compactSubtitle] et un libellé générique.
     */
    open val accessibilityLabel: String? = null

    /**
     * Résumé TalkBack construit avec les chaînes localisées (les contenus n'ont pas de Context).
     * Par défaut : [accessibilityLabel].
     */
    open fun describe(res: IslandSpeech.Res): String? = accessibilityLabel

    /**
     * Actions principales proposées par TalkBack (actions personnalisées) : lecture/pause, suivant,
     * répondre, raccrocher… Déplier/Replier est ajouté par l'île elle-même.
     */
    open fun accessibilityActions(host: IslandHost, res: IslandSpeech.Res): List<IslandAccessibilityAction> = emptyList()

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
