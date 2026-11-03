package com.karelisio.bulle.modules.live

import kotlinx.serialization.Serializable

/** Réglages du module « live ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class LiveSettings(
    val enabled: Boolean = true,
)
