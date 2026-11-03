package com.karelisio.bulle.core.update

import kotlinx.serialization.Serializable

/** État de la mise à jour in-app. */
@Serializable
data class UpdateSettings(
    /** Dernière vérification de releases/latest (epoch ms). */
    val lastCheck: Long = 0,
    /** Version que l'utilisateur a choisi d'ignorer. */
    val skippedVersion: String? = null,
)
