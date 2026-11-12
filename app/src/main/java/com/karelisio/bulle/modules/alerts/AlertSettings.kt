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
    // Alertes ajoutées en 0.3.0 : toutes désactivées par défaut (aucun changement visible).
    /** Perte et retour du réseau mobile. */
    val cellular: Boolean = false,
    /** Passage en 5G (READ_PHONE_STATE demandée à l'activation). */
    val fiveG: Boolean = false,
    val vpn: Boolean = false,
    /** Point d'accès (partage de connexion) activé / désactivé. */
    val hotspot: Boolean = false,
    /** Silencieux / vibreur / sonnerie. */
    val ringer: Boolean = false,
    /** Volume média, regroupé en une seule alerte par rafale. */
    val mediaVolume: Boolean = false,
    /** Câble USB de données (ordinateur) ou périphérique USB branché / débranché. */
    val usb: Boolean = false,
    /** Caméra utilisée par une appli : pastille verte persistante. */
    val cameraInUse: Boolean = false,
    /** Micro utilisé par une appli : pastille orange persistante. */
    val micInUse: Boolean = false,
    /** Personnalisation par alerte (clé = identifiant de l'alerte). Absent = style par défaut. */
    val styles: Map<String, AlertStyle> = emptyMap(),
) {
    /** Style d'une alerte, jamais null. */
    fun styleOf(alertId: String): AlertStyle = styles[alertId] ?: AlertStyle()

    /** Remplace le style d'une alerte ; un style vide est retiré de la table (JSON minimal). */
    fun withStyle(alertId: String, style: AlertStyle): AlertSettings =
        copy(styles = if (style.isDefault) styles - alertId else styles + (alertId to style))
}
