package com.karelisio.bulle.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.ActionType.*
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.IslandGesture

@StringRes
fun ActionType.labelRes(): Int = when (this) {
    NONE -> R.string.action_none
    TOGGLE_EXPAND -> R.string.action_toggle_expand
    BACK -> R.string.action_back
    HOME -> R.string.action_home
    RECENTS -> R.string.action_recents
    NOTIFICATIONS -> R.string.action_notifications
    QUICK_SETTINGS -> R.string.action_quick_settings
    LOCK_SCREEN -> R.string.action_lock_screen
    SCREENSHOT -> R.string.action_screenshot
    PLAY_PAUSE -> R.string.action_play_pause
    NEXT_TRACK -> R.string.action_next_track
    PREVIOUS_TRACK -> R.string.action_previous_track
    VOLUME_UP -> R.string.action_volume_up
    VOLUME_DOWN -> R.string.action_volume_down
    MUTE -> R.string.action_mute
    BRIGHTNESS_UP -> R.string.action_brightness_up
    BRIGHTNESS_DOWN -> R.string.action_brightness_down
    TORCH -> R.string.action_torch
    ROTATION -> R.string.action_rotation
    WIFI -> R.string.action_wifi
    BLUETOOTH -> R.string.action_bluetooth
    BATTERY_SAVER -> R.string.action_battery_saver
    OPEN_APP -> R.string.action_open_app
    LAUNCH_SHORTCUT -> R.string.action_launch_shortcut
    APP_SEARCH -> R.string.action_app_search
    OPEN_DECK -> R.string.action_open_deck
    SWAP_BUBBLES -> R.string.action_swap_bubbles
    TIMER -> R.string.action_timer
    ECHO_THOUGHT -> R.string.action_echo_thought
    EXIT_CRAVING -> R.string.action_exit_craving
    RESERVE_FUEL -> R.string.action_reserve_fuel
    SCAN_QR -> R.string.action_scan_qr
    MEMO -> R.string.action_memo
    SCREEN_RECORD -> R.string.action_screen_record
    FOCUS_MODE -> R.string.action_focus_mode
    TRANSLATE -> R.string.action_translate
}

/** Groupes d'actions pour le sélecteur. */
val ActionGroups: List<Pair<Int, List<ActionType>>> = listOf(
    R.string.action_group_island to listOf(NONE, TOGGLE_EXPAND, OPEN_DECK, APP_SEARCH, TIMER, FOCUS_MODE, SWAP_BUBBLES),
    R.string.action_group_system to listOf(BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SCREENSHOT),
    R.string.action_group_media to listOf(PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK, VOLUME_UP, VOLUME_DOWN, MUTE),
    R.string.action_group_toggles to listOf(BRIGHTNESS_UP, BRIGHTNESS_DOWN, TORCH, ROTATION, WIFI, BLUETOOTH, BATTERY_SAVER),
    R.string.action_group_apps to listOf(OPEN_APP, LAUNCH_SHORTCUT),
    R.string.action_group_tools to listOf(SCAN_QR, TRANSLATE, MEMO, SCREEN_RECORD),
    R.string.action_group_ecosystem to listOf(ECHO_THOUGHT, EXIT_CRAVING, RESERVE_FUEL),
)

@Composable
fun GestureAction.label(): String = when {
    type == OPEN_APP && label != null -> stringResource(R.string.action_open_app_named, label)
    type == LAUNCH_SHORTCUT && label != null -> stringResource(R.string.action_shortcut_named, label)
    else -> stringResource(type.labelRes())
}

@StringRes
fun IslandGesture.labelRes(): Int = when (this) {
    IslandGesture.TAP -> R.string.gesture_tap
    IslandGesture.DOUBLE_TAP -> R.string.gesture_double_tap
    IslandGesture.LONG_PRESS -> R.string.gesture_long_press
    IslandGesture.SWIPE_LEFT -> R.string.gesture_swipe_left
    IslandGesture.SWIPE_RIGHT -> R.string.gesture_swipe_right
    IslandGesture.SWIPE_DOWN -> R.string.gesture_swipe_down
    IslandGesture.SWIPE_UP -> R.string.gesture_swipe_up_note
}
