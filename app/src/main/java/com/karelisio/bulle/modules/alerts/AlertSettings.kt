package com.karelisio.bulle.modules.alerts

import kotlinx.serialization.Serializable

/** Réglages du module « alerts ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class AlertSettings(
    /** Interrupteur général des alertes système. */
    val enabled: Boolean = true,
    // Une case par alerte. Celles qui demandent une permission sont désactivées par défaut :
    // la permission n'est demandée qu'au moment où l'utilisateur les active.
    val lowBattery: Boolean = true,
    /** Seuils de batterie faible (en %), une alerte à chaque franchissement vers le bas. */
    val lowBatteryLevels: List<Int> = listOf(20, 10),
    val charging: Boolean = true,
    val bluetooth: Boolean = false,
    val wifi: Boolean = true,
    val alarm: Boolean = true,
    val torch: Boolean = true,
    val airplane: Boolean = true,
    val dnd: Boolean = true,
    val screenshot: Boolean = false,
    val packages: Boolean = true,
    val clipboard: Boolean = false,
    /** Animation spéciale pour les notifications de l'appli Écho (module « echo »). */
    val echoEnabled: Boolean = true,
)
