package com.karelisio.bulle.island

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.CoroutineScope

/**
 * Ce que le service d'accessibilité met à disposition d'un module « hors île » (Halo, Bords,
 * Veille…) : ses fenêtres d'overlay, sa portée de coroutines (annulée à la déconnexion) et
 * le contrôleur de l'île (réglages effectifs, état de l'écran, actions).
 */
class AddonEnv(
    val service: AccessibilityService,
    val scope: CoroutineScope,
    val controller: IslandController,
)

/**
 * Module qui vit dans le service d'accessibilité existant sans afficher de contenu dans l'île
 * (overlays plein écran, zones de bord, détection de la charge…). Aucun autre service n'est
 * créé : tout passe par celui de l'île. Enregistré dans `Modules.registerAll()`.
 */
interface IslandAddon {
    val id: String

    /**
     * Démarré à chaque connexion du service. Retourne de quoi tout libérer à la déconnexion
     * (fenêtres, récepteurs, capteurs).
     */
    fun start(env: AddonEnv): AutoCloseable
}

/** Registre des modules « hors île », démarrés par [IslandAccessibilityService]. */
object IslandAddons {
    private val _addons = linkedMapOf<String, IslandAddon>()
    val all: List<IslandAddon> get() = _addons.values.toList()

    fun register(addon: IslandAddon) {
        _addons[addon.id] = addon
    }
}
