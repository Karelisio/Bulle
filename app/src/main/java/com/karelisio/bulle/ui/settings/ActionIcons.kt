package com.karelisio.bulle.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.ActionType.*

/** Icône représentant une action. */
fun ActionType.icon(): ImageVector = when (this) {
    NONE -> Icons.Rounded.Block
    TOGGLE_EXPAND -> Icons.Rounded.OpenInFull
    BACK -> Icons.AutoMirrored.Rounded.ArrowBack
    HOME -> Icons.Rounded.Home
    RECENTS -> Icons.Rounded.ViewCarousel
    NOTIFICATIONS -> Icons.Rounded.Notifications
    QUICK_SETTINGS -> Icons.Rounded.Tune
    LOCK_SCREEN -> Icons.Rounded.Lock
    SCREENSHOT -> Icons.Rounded.Screenshot
    PLAY_PAUSE -> Icons.Rounded.PlayArrow
    NEXT_TRACK -> Icons.Rounded.SkipNext
    PREVIOUS_TRACK -> Icons.Rounded.SkipPrevious
    VOLUME_UP -> Icons.AutoMirrored.Rounded.VolumeUp
    VOLUME_DOWN -> Icons.AutoMirrored.Rounded.VolumeDown
    MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
    BRIGHTNESS_UP -> Icons.Rounded.BrightnessHigh
    BRIGHTNESS_DOWN -> Icons.Rounded.BrightnessLow
    TORCH -> Icons.Rounded.FlashlightOn
    ROTATION -> Icons.Rounded.ScreenRotation
    WIFI -> Icons.Rounded.Wifi
    BLUETOOTH -> Icons.Rounded.Bluetooth
    BATTERY_SAVER -> Icons.Rounded.BatterySaver
    OPEN_APP -> Icons.Rounded.Apps
    LAUNCH_SHORTCUT -> Icons.Rounded.Shortcut
    APP_SEARCH -> Icons.Rounded.Search
    OPEN_DECK -> Icons.Rounded.Dashboard
    SWAP_BUBBLES -> Icons.Rounded.SwapHoriz
    TIMER -> Icons.Rounded.HourglassTop
    ECHO_THOUGHT -> Icons.Rounded.Favorite
    EXIT_CRAVING -> Icons.Rounded.Air
    RESERVE_FUEL -> Icons.Rounded.LocalGasStation
    SCAN_QR -> Icons.Rounded.QrCodeScanner
    MEMO -> Icons.Rounded.Mic
    SCREEN_RECORD -> Icons.Rounded.Videocam
    FOCUS_MODE -> Icons.Rounded.Bedtime
    TRANSLATE -> Icons.Rounded.Translate
}
