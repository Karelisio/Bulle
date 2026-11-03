package com.karelisio.bulle.island

import com.karelisio.bulle.core.settings.BulleSettings

/** État de l'écran utile au masquage automatique. */
data class ScreenEnv(
    val geometry: IslandGeometry = IslandGeometry.Empty,
    val landscape: Boolean = false,
    val statusBarVisible: Boolean = true,
    val locked: Boolean = false,
)

/** Décide si l'île doit être visible. */
object VisibilityPolicy {
    fun isVisible(settings: BulleSettings, screen: ScreenEnv, foregroundPackage: String?): Boolean {
        if (!settings.islandEnabled) return false
        val v = settings.visibility
        if (v.hideInFullscreen && !screen.statusBarVisible && !screen.locked) return false
        if (v.hideInLandscape && screen.landscape) return false
        if (v.hideOnLockscreen && screen.locked) return false
        if (!screen.locked && foregroundPackage != null && foregroundPackage in v.hiddenApps) return false
        return true
    }
}
