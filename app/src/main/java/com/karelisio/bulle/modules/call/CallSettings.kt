package com.karelisio.bulle.modules.call

import kotlinx.serialization.Serializable

/** Réglages du module « call ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class CallSettings(
    val enabled: Boolean = true,
)
