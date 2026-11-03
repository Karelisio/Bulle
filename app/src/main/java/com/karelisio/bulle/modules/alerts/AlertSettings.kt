package com.karelisio.bulle.modules.alerts

import kotlinx.serialization.Serializable

/** Réglages du module « alerts ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class AlertSettings(
    val enabled: Boolean = true,
)
