package com.karelisio.bulle.core.settings

import kotlinx.serialization.Serializable

/** Niveau des annonces TalkBack de l'île (changements de contenu lus sans toucher l'île). */
@Serializable
enum class IslandAnnouncements {
    /** Tout changement de contenu (et ses mises à jour, au plus toutes les 10 s). */
    ALL,
    /** Seulement les contenus ponctuels importants : alertes, messages, appels, alarmes. */
    IMPORTANT,
    /** Aucune annonce spontanée (l'île reste lisible en la touchant). */
    NONE,
}

/**
 * Réglages d'accessibilité. Valeurs par défaut = comportement historique : rien ne change à
 * l'écran tant que l'utilisateur (ou le réglage système « Texte à contraste élevé ») ne l'active pas.
 */
@Serializable
data class AccessibilitySettings(
    /** Schéma de couleurs à contraste renforcé (appli et île). */
    val highContrast: Boolean = false,
    /** Active aussi le contraste élevé quand le réglage système « Texte à contraste élevé » est actif. */
    val followSystemContrast: Boolean = true,
    /** En contraste élevé : fin contour clair autour de l'île. */
    val islandOutline: Boolean = true,
    /** Annonces TalkBack spontanées de l'île. */
    val announcements: IslandAnnouncements = IslandAnnouncements.IMPORTANT,
) {
    /** Contraste élevé effectif, compte tenu du réglage système. */
    fun highContrastActive(systemHighContrast: Boolean): Boolean =
        highContrast || (followSystemContrast && systemHighContrast)
}
