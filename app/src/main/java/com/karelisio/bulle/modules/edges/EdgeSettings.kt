package com.karelisio.bulle.modules.edges

import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import kotlinx.serialization.Serializable

/** Bord de l'écran. */
@Serializable
enum class EdgeSide { LEFT, RIGHT }

/** Glissement vertical en mode curseur : réglage continu. */
@Serializable
enum class EdgeSlider { OFF, BRIGHTNESS, VOLUME }

/** Une longue barre par bord, ou deux petites (haut et bas). */
@Serializable
enum class EdgeBars { SINGLE, DOUBLE }

/** Gestes reconnus sur une zone de bord. */
enum class EdgeGesture { SWIPE_UP, SWIPE_DOWN, DOUBLE_TAP, LONG_PRESS, SWIPE_IN }

/**
 * Une zone invisible le long d'un bord. [start] et [length] sont des fractions de la hauteur
 * de l'écran (0 = haut). Par défaut : courte, dans le tiers supérieur.
 */
@Serializable
data class EdgeZone(
    val enabled: Boolean = true,
    val start: Float = 0.12f,
    val length: Float = 0.21f,
    /** Épaisseur tactile, 8 à 24 dp. */
    val thicknessDp: Float = 12f,
    val swipeUp: GestureAction = GestureAction(ActionType.NONE),
    val swipeDown: GestureAction = GestureAction(ActionType.NONE),
    val doubleTap: GestureAction = GestureAction(ActionType.NONE),
    val longPress: GestureAction = GestureAction(ActionType.NONE),
    /** Glissement vers l'intérieur avec maintien (désactivé par défaut : risque pour le geste Retour). */
    val swipeIn: GestureAction = GestureAction(ActionType.NONE),
    /** Glissement vertical en mode curseur (remplace les glissements haut / bas). */
    val slider: EdgeSlider = EdgeSlider.OFF,
) {
    fun actionFor(g: EdgeGesture): GestureAction = when (g) {
        EdgeGesture.SWIPE_UP -> swipeUp
        EdgeGesture.SWIPE_DOWN -> swipeDown
        EdgeGesture.DOUBLE_TAP -> doubleTap
        EdgeGesture.LONG_PRESS -> longPress
        EdgeGesture.SWIPE_IN -> swipeIn
    }

    fun with(g: EdgeGesture, a: GestureAction): EdgeZone = when (g) {
        EdgeGesture.SWIPE_UP -> copy(swipeUp = a)
        EdgeGesture.SWIPE_DOWN -> copy(swipeDown = a)
        EdgeGesture.DOUBLE_TAP -> copy(doubleTap = a)
        EdgeGesture.LONG_PRESS -> copy(longPress = a)
        EdgeGesture.SWIPE_IN -> copy(swipeIn = a)
    }

    fun sanitized(d: EdgeZone): EdgeZone = copy(
        start = start.orElse(d.start).coerceIn(0f, 1f - MIN_LENGTH),
        length = length.orElse(d.length).coerceIn(MIN_LENGTH, MAX_LENGTH),
        thicknessDp = thicknessDp.orElse(d.thicknessDp).coerceIn(THICKNESS),
    )

    companion object {
        val THICKNESS = 8f..24f
        const val MIN_LENGTH = 0.08f
        /** Une zone peut couvrir toute la hauteur (les bandes réservées restent épargnées). */
        const val MAX_LENGTH = 1f
    }
}

/** Emplacement d'une zone : bord et moitié (haut / bas en option). */
enum class EdgeSlot(val side: EdgeSide, val lower: Boolean) {
    LEFT(EdgeSide.LEFT, false),
    RIGHT(EdgeSide.RIGHT, false),
    LEFT_LOWER(EdgeSide.LEFT, true),
    RIGHT_LOWER(EdgeSide.RIGHT, true),
}

/** Réglages du module Bords (gestes sur les bords de l'écran). */
@Serializable
data class EdgeSettings(
    val enabled: Boolean = false,
    /** Une longue barre par bord (zones hautes seules) ou deux petites (haut + bas). */
    val bars: EdgeBars = EdgeBars.SINGLE,
    val left: EdgeZone = EdgeZone(
        swipeDown = GestureAction(ActionType.NOTIFICATIONS),
        swipeUp = GestureAction(ActionType.OPEN_DECK),
    ),
    val right: EdgeZone = EdgeZone(
        swipeDown = GestureAction(ActionType.QUICK_SETTINGS),
        swipeUp = GestureAction(ActionType.APP_SEARCH),
    ),
    val leftLower: EdgeZone = EdgeZone(enabled = false, start = 0.55f, length = 0.25f),
    val rightLower: EdgeZone = EdgeZone(enabled = false, start = 0.55f, length = 0.25f),
    /** Autorise le glissement vers l'intérieur (avertissement : peut gêner le geste Retour). */
    val swipeInAllowed: Boolean = false,
    /** Goutte liquide sous le doigt (coupée si « réduire les animations »). */
    val liquidFeedback: Boolean = true,
    /** Applis où les zones sont désactivées (jeux, dessin, cartes). */
    val disabledApps: Set<String> = emptySet(),
) {
    /** Emplacements utilisés selon [bars]. */
    val slots: List<EdgeSlot>
        get() = if (bars == EdgeBars.SINGLE) listOf(EdgeSlot.LEFT, EdgeSlot.RIGHT) else EdgeSlot.entries

    fun zone(slot: EdgeSlot): EdgeZone = when (slot) {
        EdgeSlot.LEFT -> left
        EdgeSlot.RIGHT -> right
        EdgeSlot.LEFT_LOWER -> leftLower
        EdgeSlot.RIGHT_LOWER -> rightLower
    }

    fun with(slot: EdgeSlot, z: EdgeZone): EdgeSettings = when (slot) {
        EdgeSlot.LEFT -> copy(left = z)
        EdgeSlot.RIGHT -> copy(right = z)
        EdgeSlot.LEFT_LOWER -> copy(leftLower = z)
        EdgeSlot.RIGHT_LOWER -> copy(rightLower = z)
    }

    fun sanitized(): EdgeSettings {
        val d = EdgeSettings()
        return copy(
            left = left.sanitized(d.left),
            right = right.sanitized(d.right),
            leftLower = leftLower.sanitized(d.leftLower),
            rightLower = rightLower.sanitized(d.rightLower),
        )
    }
}

private fun Float.orElse(d: Float) = if (isNaN()) d else this
