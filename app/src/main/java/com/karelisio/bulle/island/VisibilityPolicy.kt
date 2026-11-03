package com.karelisio.bulle.island

import com.karelisio.bulle.core.settings.BulleSettings

/** État de l'écran utile au masquage automatique. */
data class ScreenEnv(
    val geometry: IslandGeometry = IslandGeometry.Empty,
    val landscape: Boolean = false,
    val statusBarVisible: Boolean = true,
    val locked: Boolean = false,
    /** Imposé par une règle automatique : true = afficher, false = masquer. */
    val ruleVisibility: Boolean? = null,
)

/** Décide si l'île doit être visible. */
/** Pourquoi l'île est masquée (null = visible). */
enum class HiddenReason { DISABLED, RULE, FULLSCREEN, LANDSCAPE, LOCKSCREEN, HIDDEN_APP }

object VisibilityPolicy {
    fun isVisible(settings: BulleSettings, screen: ScreenEnv, foregroundPackage: String?): Boolean =
        hiddenReason(settings, screen, foregroundPackage) == null

    fun hiddenReason(settings: BulleSettings, screen: ScreenEnv, foregroundPackage: String?): HiddenReason? {
        if (!settings.islandEnabled) return HiddenReason.DISABLED
        screen.ruleVisibility?.let { return if (it) null else HiddenReason.RULE }
        val v = settings.visibility
        if (v.hideInFullscreen && !screen.statusBarVisible && !screen.locked) return HiddenReason.FULLSCREEN
        if (v.hideInLandscape && screen.landscape) return HiddenReason.LANDSCAPE
        if (v.hideOnLockscreen && screen.locked) return HiddenReason.LOCKSCREEN
        if (!screen.locked && foregroundPackage != null && foregroundPackage in v.hiddenApps) return HiddenReason.HIDDEN_APP
        return null
    }
}
