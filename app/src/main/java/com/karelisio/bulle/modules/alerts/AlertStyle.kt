package com.karelisio.bulle.modules.alerts

import com.karelisio.bulle.core.feedback.FeedbackSound
import com.karelisio.bulle.core.feedback.HapticPattern
import kotlinx.serialization.Serializable

/** Animation d'entrée choisie pour une alerte (null dans [AlertStyle] = animation propre à l'alerte). */
@Serializable
enum class AlertEntrance {
    /** Aucune : les glyphes de l'alerte restent immobiles. */
    NONE,

    /** Le contenu surgit avec un rebond élastique. */
    BOUNCE,

    /** Deux battements rapides, comme un cœur. */
    PULSE,

    /** Le contenu glisse depuis la caméra. */
    SLIDE,
}

/**
 * Personnalisation d'UNE alerte. Chaque champ null = comportement par défaut de l'alerte,
 * pour qu'un style vide ne change rien à l'affichage actuel.
 */
@Serializable
data class AlertStyle(
    /** Clé d'icône (voir AlertIcons) remplaçant le glyphe animé de l'alerte. */
    val icon: String? = null,
    /** Couleur d'accent ARGB (0xFFRRGGBB). */
    val accent: Long? = null,
    val entrance: AlertEntrance? = null,
    /** Durée d'affichage en secondes ([MIN_SECONDS]..[MAX_SECONDS]), alertes ponctuelles seulement. */
    val durationSec: Int? = null,
    /** Motif haptique joué à l'apparition de l'alerte (aucun par défaut, comme avant). */
    val haptic: HapticPattern = HapticPattern.NONE,
    /** Son joué à l'apparition de l'alerte (aucun par défaut). */
    val sound: FeedbackSound = FeedbackSound.None,
) {
    val isDefault: Boolean
        get() = icon == null && accent == null && entrance == null && durationSec == null && !hasFeedback

    /** Vrai si un retour (vibration ou son) est choisi pour cette alerte. */
    val hasFeedback: Boolean get() = haptic != HapticPattern.NONE || !sound.isNone

    /** Durée effective en millisecondes, bornée, ou null si non personnalisée. */
    val durationMs: Long? get() = durationSec?.coerceIn(MIN_SECONDS, MAX_SECONDS)?.times(1_000L)

    companion object {
        const val MIN_SECONDS = 2
        const val MAX_SECONDS = 10
    }
}

/**
 * Carnet des styles en vigueur côté île, tenu à jour par les modules Alertes et Écho.
 *
 * Il sert uniquement à la durée d'affichage : la durée doit être connue du contenu lui-même
 * (et non d'un emballage posé après coup), car `latestAlive` retire l'alerte de la liste
 * au terme de SA durée. Les autres réglages (icône, couleur, animation) passent par StyledAlert.
 */
object AlertStyleBook {
    @Volatile
    var styles: Map<String, AlertStyle> = emptyMap()

    /** Durée de vie d'une alerte : personnalisée si définie, sinon [base]. Null (persistante) reste null. */
    fun timeout(alertId: String?, base: Long?): Long? {
        if (base == null || alertId == null) return base
        return styles[alertId]?.durationMs ?: base
    }
}
