package com.karelisio.bulle.island

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.VisibilitySettings

/** État de l'écran utile au masquage automatique. */
data class ScreenEnv(
    val geometry: IslandGeometry = IslandGeometry.Empty,
    val landscape: Boolean = false,
    val statusBarVisible: Boolean = true,
    val locked: Boolean = false,
    /** Imposé par une règle automatique : true = afficher, false = masquer. */
    val ruleVisibility: Boolean? = null,
)

/** Pourquoi l'île est masquée (null = visible). */
enum class HiddenReason { DISABLED, RULE, FULLSCREEN, LANDSCAPE, LOCKSCREEN, HIDDEN_APP }

/**
 * Décide si l'île doit être visible. Fonction pure : [launcherPackage] (launcher par défaut,
 * voir [HomeApp]) est fourni par l'appelant ; null = inconnu, aucune exception « accueil ».
 */
object VisibilityPolicy {
    fun isVisible(
        settings: BulleSettings,
        screen: ScreenEnv,
        foregroundPackage: String?,
        launcherPackage: String? = null,
    ): Boolean = hiddenReason(settings, screen, foregroundPackage, launcherPackage) == null

    fun hiddenReason(
        settings: BulleSettings,
        screen: ScreenEnv,
        foregroundPackage: String?,
        launcherPackage: String? = null,
    ): HiddenReason? {
        if (!settings.islandEnabled) return HiddenReason.DISABLED
        screen.ruleVisibility?.let { return if (it) null else HiddenReason.RULE }
        val v = settings.visibility
        if (v.hideInFullscreen && !screen.statusBarVisible && !screen.locked &&
            !fullscreenIgnored(v, foregroundPackage, launcherPackage)
        ) return HiddenReason.FULLSCREEN
        if (v.hideInLandscape && screen.landscape) return HiddenReason.LANDSCAPE
        if (v.hideOnLockscreen && screen.locked) return HiddenReason.LOCKSCREEN
        if (!screen.locked && foregroundPackage != null && foregroundPackage in v.hiddenApps) return HiddenReason.HIDDEN_APP
        return null
    }

    /** L'appli au premier plan est une exception au masquage plein écran. */
    private fun fullscreenIgnored(v: VisibilitySettings, fg: String?, launcher: String?): Boolean {
        fg ?: return false
        if (fg in v.fullscreenIgnoredApps) return true
        return v.ignoreFullscreenOnLauncher && launcher != null && fg == launcher
    }
}
