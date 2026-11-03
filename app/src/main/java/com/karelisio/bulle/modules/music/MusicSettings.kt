package com.karelisio.bulle.modules.music

import kotlinx.serialization.Serializable

/** Réglages du module « music ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class MusicSettings(
    val enabled: Boolean = true,
)
