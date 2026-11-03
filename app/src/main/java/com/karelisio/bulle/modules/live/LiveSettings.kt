package com.karelisio.bulle.modules.live

import kotlinx.serialization.Serializable

/** Réglages du module « live ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class LiveSettings(
    val enabled: Boolean = true,
    /** Paquets dont les activités en cours ne doivent jamais apparaître dans l'île. */
    val excludedPackages: Set<String> = emptySet(),
    /** Afficher aussi les téléchargements et progressions génériques (non promus). */
    val showGenericProgress: Boolean = true,
)
