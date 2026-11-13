package com.karelisio.bulle.modules.network

import com.karelisio.bulle.core.settings.BulleSettings
import kotlinx.serialization.Serializable

/** Réglages du module « Réseau ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class NetworkSettings(
    /** Débit en compact pendant un gros transfert (contenu de basse priorité). */
    val showCompact: Boolean = true,
    /** Seuil (Ko/s, montant + descendant) au-delà duquel le compact apparaît. */
    val thresholdKBps: Int = DEFAULT_THRESHOLD,
    val unit: RateUnit = RateUnit.BYTES,
) {
    val thresholdBytesPerS: Double get() = thresholdKBps * 1000.0

    companion object {
        const val DEFAULT_THRESHOLD = 500
        val THRESHOLD_CHOICES = listOf(100, 500, 1_000, 5_000)
    }
}

/**
 * Branchement sur [BulleSettings] : seul endroit à modifier quand le champ
 * `val network: NetworkSettings = NetworkSettings()` est ajouté à BulleSettings.
 * Remplacer alors les deux corps par `settings.network` et `settings.copy(network = value)`.
 */
internal object NetworkSettingsBinding {
    fun read(settings: BulleSettings): NetworkSettings = NetworkSettings()

    @Suppress("UNUSED_PARAMETER")
    fun write(settings: BulleSettings, value: NetworkSettings): BulleSettings = settings
}
