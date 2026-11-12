package com.karelisio.bulle.island

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Plein écran détecté via la liste des fenêtres : la barre d'état est une fenêtre système
 * collée en haut, de pleine largeur et peu haute. Absente = plein écran.
 * Seules la position et le type des fenêtres sont consultés, jamais leur contenu.
 */
object FullscreenDetector {

    /** Vrai si l'événement concerne une de nos propres fenêtres d'overlay (île, volet). */
    fun isOwnOverlay(service: AccessibilityService, windowId: Int): Boolean {
        if (windowId < 0) return false
        val windows = runCatching { service.windows }.getOrNull() ?: return false
        return windows.any { it.id == windowId && it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
    }

    /** null si la liste est indisponible (on garde alors l'autre signal). */
    fun statusBarVisible(service: AccessibilityService, screenWidth: Int, statusBarHeight: Int): Boolean? {
        val windows = runCatching { service.windows }.getOrNull()
        if (windows.isNullOrEmpty()) return null
        val r = Rect()
        // Certaines surcouches ont une fenêtre de barre d'état plus haute que la barre elle-même.
        val maxH = (statusBarHeight * 4).coerceAtLeast(1)
        return windows.any { w ->
            w.getBoundsInScreen(r)
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && r.top <= 0 &&
                r.width() >= screenWidth * 0.8f && r.height() in 1..maxH
        }
    }
}
