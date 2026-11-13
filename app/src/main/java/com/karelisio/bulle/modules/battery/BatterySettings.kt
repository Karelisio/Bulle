package com.karelisio.bulle.modules.battery

import com.karelisio.bulle.core.settings.BulleSettings
import kotlinx.serialization.Serializable

/** Réglages du module « Batterie détaillée ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class BatterySettings(
    /** Contenu discret (niveau, temps de charge) pendant toute la charge. */
    val showWhileCharging: Boolean = true,
    /** Contenu discret aussi sur batterie (niveau, autonomie estimée), quand l'île est libre. */
    val showAlways: Boolean = false,
    /** Batteries des accessoires Bluetooth dans la vue détaillée (si l'accès Bluetooth est accordé). */
    val accessories: Boolean = true,
)

/**
 * Branchement sur [BulleSettings] : seul endroit à modifier quand le champ
 * `val battery: BatterySettings = BatterySettings()` est ajouté à BulleSettings.
 * Remplacer alors les deux corps par `settings.battery` et `settings.copy(battery = value)`.
 */
internal object BatterySettingsBinding {
    fun read(settings: BulleSettings): BatterySettings = settings.battery

    fun write(settings: BulleSettings, value: BatterySettings): BulleSettings = settings.copy(battery = value)
}
