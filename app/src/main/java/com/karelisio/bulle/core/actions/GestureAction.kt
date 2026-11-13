package com.karelisio.bulle.core.actions

import com.karelisio.bulle.core.island.IslandGesture
import kotlinx.serialization.Serializable

/** Toutes les actions qu'un geste (ou une règle) peut déclencher. */
@Serializable
enum class ActionType(val needsWriteSettings: Boolean = false) {
    NONE,
    TOGGLE_EXPAND,
    BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SCREENSHOT,
    PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK,
    VOLUME_UP, VOLUME_DOWN, MUTE,
    BRIGHTNESS_UP(needsWriteSettings = true), BRIGHTNESS_DOWN(needsWriteSettings = true),
    TORCH, ROTATION(needsWriteSettings = true), WIFI, BLUETOOTH, BATTERY_SAVER,
    OPEN_APP, LAUNCH_SHORTCUT, APP_SEARCH, OPEN_DECK, SWAP_BUBBLES,
    TIMER,
    ECHO_THOUGHT, EXIT_CRAVING, RESERVE_FUEL,
    SCAN_QR, MEMO, SCREEN_RECORD,
}

/**
 * Action configurée : un type + ses paramètres éventuels
 * (paquet pour OPEN_APP, intent sérialisé pour LAUNCH_SHORTCUT).
 */
@Serializable
data class GestureAction(
    val type: ActionType = ActionType.NONE,
    val packageName: String? = null,
    val intentUri: String? = null,
    val label: String? = null,
)

/** Association geste → action. Le glisser vers le haut replie toujours l'île. */
@Serializable
data class GestureSettings(
    val tap: GestureAction = GestureAction(ActionType.TOGGLE_EXPAND),
    val doubleTap: GestureAction = GestureAction(ActionType.NONE),
    val longPress: GestureAction = GestureAction(ActionType.OPEN_DECK),
    val swipeLeft: GestureAction = GestureAction(ActionType.PREVIOUS_TRACK),
    val swipeRight: GestureAction = GestureAction(ActionType.NEXT_TRACK),
    val swipeDown: GestureAction = GestureAction(ActionType.TOGGLE_EXPAND),
) {
    fun actionFor(gesture: IslandGesture): GestureAction? = when (gesture) {
        IslandGesture.TAP -> tap
        IslandGesture.DOUBLE_TAP -> doubleTap
        IslandGesture.LONG_PRESS -> longPress
        IslandGesture.SWIPE_LEFT -> swipeLeft
        IslandGesture.SWIPE_RIGHT -> swipeRight
        IslandGesture.SWIPE_DOWN -> swipeDown
        IslandGesture.SWIPE_UP -> null
    }

    fun with(gesture: IslandGesture, action: GestureAction): GestureSettings = when (gesture) {
        IslandGesture.TAP -> copy(tap = action)
        IslandGesture.DOUBLE_TAP -> copy(doubleTap = action)
        IslandGesture.LONG_PRESS -> copy(longPress = action)
        IslandGesture.SWIPE_LEFT -> copy(swipeLeft = action)
        IslandGesture.SWIPE_RIGHT -> copy(swipeRight = action)
        IslandGesture.SWIPE_DOWN -> copy(swipeDown = action)
        IslandGesture.SWIPE_UP -> this
    }

    companion object {
        /** Gestes configurables, dans l'ordre d'affichage. */
        val configurable = listOf(
            IslandGesture.TAP, IslandGesture.DOUBLE_TAP, IslandGesture.LONG_PRESS,
            IslandGesture.SWIPE_LEFT, IslandGesture.SWIPE_RIGHT, IslandGesture.SWIPE_DOWN,
        )
    }
}
