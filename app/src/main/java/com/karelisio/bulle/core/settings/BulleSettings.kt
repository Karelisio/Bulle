package com.karelisio.bulle.core.settings

import kotlinx.serialization.Serializable

/**
 * Tous les réglages de Bulle, sérialisés en JSON (DataStore).
 * Chaque champ a une valeur par défaut : on peut en ajouter sans migration
 * et la sauvegarde/restauration JSON reste compatible.
 */
@Serializable
data class BulleSettings(
    val islandEnabled: Boolean = true,
    val position: PositionTweak = PositionTweak(),
)

/** Réglage fin manuel, en dp, appliqué par-dessus la position calée sur le cutout. */
@Serializable
data class PositionTweak(
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val extraWidthDp: Float = 0f,
    val extraHeightDp: Float = 0f,
)
