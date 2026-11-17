package com.karelisio.bulle.ui.home

/** Problème détecté par l'auto-test de l'accueil. */
enum class SelfTestIssue { HALO_STOPPED, EDGES_STOPPED, STANDBY_PERMISSIONS, STANDBY_PHOTOS, VOLET_PERMISSIONS }

/** Ce que l'auto-test lit (copie figée, testable). */
data class SelfTestInputs(
    val serviceRunning: Boolean,
    val haloEnabled: Boolean,
    val haloRunning: Boolean,
    val edgesEnabled: Boolean,
    val edgesRunning: Boolean,
    val standbyEnabled: Boolean,
    /** Autorisations MagicOS (écran de verrouillage, pop-up en arrière-plan) accordées ou sans objet. */
    val standbyPermissions: Boolean,
    val standbyPhotosNeeded: Boolean,
    val photosGranted: Boolean,
    val voletEnabled: Boolean,
    val voletPermissions: Boolean,
)

/**
 * Auto-test de l'accueil (logique pure) : modules activés qui ne tournent pas, autorisations
 * manquantes. Le service de l'île et l'accès aux notifications ont déjà leurs cartes.
 */
object SelfTest {
    fun issues(i: SelfTestInputs): List<SelfTestIssue> = buildList {
        if (i.serviceRunning && i.haloEnabled && !i.haloRunning) add(SelfTestIssue.HALO_STOPPED)
        if (i.serviceRunning && i.edgesEnabled && !i.edgesRunning) add(SelfTestIssue.EDGES_STOPPED)
        if (i.standbyEnabled && !i.standbyPermissions) add(SelfTestIssue.STANDBY_PERMISSIONS)
        if (i.standbyEnabled && i.standbyPhotosNeeded && !i.photosGranted) add(SelfTestIssue.STANDBY_PHOTOS)
        if (i.voletEnabled && !i.voletPermissions) add(SelfTestIssue.VOLET_PERMISSIONS)
    }
}
