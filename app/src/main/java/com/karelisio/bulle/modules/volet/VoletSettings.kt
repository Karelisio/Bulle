package com.karelisio.bulle.modules.volet

import kotlinx.serialization.Serializable

/** Répartition des zones de déclenchement en haut de l'écran (de part et d'autre de l'île). */
@Serializable
enum class VoletZones {
    /** Gauche : notifications, droite : réglages rapides (comme MagicOS). */
    SPLIT,
    /** Gauche : réglages rapides, droite : notifications. */
    SPLIT_REVERSED,
    /** Les deux côtés ouvrent les notifications. */
    SINGLE,
}

@Serializable
enum class VoletDensity { COMFORTABLE, COMPACT }

/** Réglages du module Volet (volet de notifications et réglages rapides). */
@Serializable
data class VoletSettings(
    /** Désactivé par défaut : aucune fenêtre n'est créée tant qu'il est éteint. */
    val enabled: Boolean = false,
    val zones: VoletZones = VoletZones.SPLIT,
    /** Hauteur des zones de déclenchement en dp (0 = hauteur de la barre d'état). */
    val triggerHeightDp: Float = 0f,
    /** Marge laissée libre aux bords gauche et droit, en dp. */
    val edgeInsetDp: Float = 0f,
    val blurRadiusDp: Float = 36f,
    /** Opacité du voile derrière le volet (0 – 1). */
    val scrimOpacity: Float = 0.55f,
    val cornerRadiusDp: Float = 28f,
    val density: VoletDensity = VoletDensity.COMFORTABLE,
    /** Applis dont les notifications ne sont pas affichées dans le volet. */
    val excludedApps: Set<String> = emptySet(),
    val showOnLockscreen: Boolean = true,
    /** Historique des notifications (7 jours, local). */
    val historyEnabled: Boolean = false,
    /** Ordre des tuiles de réglages rapides (identifiants). Vide = ordre par défaut. */
    val tiles: List<String> = emptyList(),
    /** Nombre de tuiles visibles volet à demi ouvert (4 – 6). */
    val collapsedTileCount: Int = 6,
)
