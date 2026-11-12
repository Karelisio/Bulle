package com.karelisio.bulle.modules.alerts.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded._5g
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertEntrance
import com.karelisio.bulle.modules.alerts.AlertStyle
import com.karelisio.bulle.modules.alerts.sources.AlarmContent
import com.karelisio.bulle.modules.echo.EchoContent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/* Personnalisation par alerte (icône, couleur, animation d'entrée) appliquée au rendu.
 * Les glyphes lisent ces CompositionLocal : sans style, ils valent null/false et rien ne change. */

/** Couleur d'accent imposée par le style de l'alerte (null = couleur propre au glyphe). */
val LocalAlertTint = staticCompositionLocalOf<Color?> { null }

/** Vrai si l'utilisateur a choisi « aucune animation » pour l'alerte : glyphes immobiles. */
val LocalAlertStill = staticCompositionLocalOf { false }

/** Icône choisie remplaçant le glyphe (vue étendue, via AlertExpanded). */
val LocalAlertIcon = staticCompositionLocalOf<ImageVector?> { null }

/** Animation d'entrée choisie (pastille de la vue étendue, via AlertExpanded). */
val LocalAlertEntrance = staticCompositionLocalOf<AlertEntrance?> { null }

/** [c], ou la couleur d'accent du style courant. */
@Composable
@ReadOnlyComposable
fun tinted(c: Color): Color = LocalAlertTint.current ?: c

/** Couleur Compose d'un accent stocké en ARGB. */
fun accentColor(argb: Long?): Color? = argb?.let { Color(it) }

/** Identifiant du réglage d'un contenu d'alerte (null si ce n'est pas une alerte personnalisable). */
fun alertIdOf(content: IslandContent): String? = when (content) {
    is StyledAlert -> alertIdOf(content.inner)
    is AlertContent -> content.alertId
    is AlarmContent -> "alarm"
    is EchoContent -> "echo"
    else -> null
}

/** Emballe [content] avec son style s'il en a un ; sinon le rend tel quel (aucun changement). */
fun styled(content: IslandContent, styles: Map<String, AlertStyle>): IslandContent {
    if (content is StyledAlert) return content
    val style = alertIdOf(content)?.let { styles[it] } ?: return content
    return if (style.isDefault) content else StyledAlert(content, style)
}

/**
 * Contenu d'alerte habillé par le style de l'utilisateur. Tout est délégué au contenu d'origine
 * (clé, priorité, actions) ; seuls l'accent, l'icône, l'animation et la durée changent.
 */
data class StyledAlert(val inner: IslandContent, val style: AlertStyle) : IslandContent() {
    override val key: String get() = inner.key
    override val moduleId: String get() = inner.moduleId
    override val priority get() = inner.priority
    override val postedAt: Long get() = inner.postedAt
    override val timeoutMs: Long? get() = inner.timeoutMs?.let { style.durationMs ?: it }
    override val autoExpandMs: Long? get() = inner.autoExpandMs
    override val accent: Color? get() = accentColor(style.accent) ?: inner.accent
    override val compactSlotWidth: Dp get() = inner.compactSlotWidth
    override val canBeSecondary: Boolean get() = inner.canBeSecondary
    override val expandable: Boolean get() = inner.expandable
    override val notificationKey: String? get() = inner.notificationKey
    override val showWhenHidden: Boolean get() = inner.showWhenHidden

    private val icon: ImageVector? get() = AlertIcons.vector(style.icon)

    @Composable
    private fun Styled(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalAlertTint provides accentColor(style.accent),
            LocalAlertStill provides (style.entrance == AlertEntrance.NONE),
            LocalAlertIcon provides icon,
            LocalAlertEntrance provides style.entrance,
            content = content,
        )
    }

    @Composable
    private fun Compact(size: Dp, content: @Composable () -> Unit) = Styled {
        Box(Modifier.alertEntrance(style.entrance, inner.key), contentAlignment = Alignment.Center) {
            val i = icon
            if (i != null) Icon(i, null, tint = tinted(Color.White), modifier = Modifier.size(size)) else content()
        }
    }

    @Composable
    override fun Leading() = Compact(20.dp) { inner.Leading() }

    @Composable
    override fun Trailing() = Styled {
        Box(Modifier.alertEntrance(style.entrance, inner.key, delayMs = 90), contentAlignment = Alignment.Center) { inner.Trailing() }
    }

    @Composable
    override fun Minimal() = Compact(18.dp) { inner.Minimal() }

    @Composable
    override fun Expanded(host: IslandHost) = Styled { inner.Expanded(host) }

    override fun onExpandedTap(host: IslandHost): Boolean = inner.onExpandedTap(host)
}

/**
 * Modificateur d'animation d'entrée : joué une fois par contenu ([replayKey]), lu en phase
 * graphicsLayer uniquement. Rien si aucune animation choisie ou si les animations sont coupées.
 */
fun Modifier.alertEntrance(entrance: AlertEntrance?, replayKey: Any?, delayMs: Int = 0): Modifier = composed {
    if (entrance == null || entrance == AlertEntrance.NONE) return@composed Modifier
    val reduce = com.karelisio.bulle.ui.components.rememberReduceMotion()
    if (reduce) return@composed Modifier
    val p = remember(entrance, replayKey) { Animatable(0f) }
    LaunchedEffect(entrance, replayKey) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        when (entrance) {
            AlertEntrance.BOUNCE -> p.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
            AlertEntrance.PULSE -> p.animateTo(1f, tween(760, easing = LinearEasing))
            AlertEntrance.SLIDE -> p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
            AlertEntrance.NONE -> p.snapTo(1f)
        }
    }
    val slide = with(LocalDensity.current) { 22.dp.toPx() }
    Modifier.graphicsLayer {
        val v = p.value
        when (entrance) {
            AlertEntrance.BOUNCE -> {
                val s = 0.3f + 0.7f * v
                scaleX = s; scaleY = s
                alpha = v.coerceIn(0f, 1f)
            }
            AlertEntrance.PULSE -> {
                val s = 1f + 0.3f * EntranceCurves.heartbeat(v)
                scaleX = s; scaleY = s
            }
            AlertEntrance.SLIDE -> {
                translationX = -slide * (1f - v)
                alpha = v.coerceIn(0f, 1f)
            }
            AlertEntrance.NONE -> Unit
        }
    }
}

/** Courbes pures des animations d'entrée (testables). */
object EntranceCurves {
    /** Deux battements qui s'amortissent sur 0..1, retour à 0 au bout. */
    fun heartbeat(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return abs(sin(x * 2f * PI.toFloat())) * (1f - x)
    }
}

/** Jeu d'icônes Material proposées pour personnaliser chaque alerte. */
object AlertIcons {
    private val all: Map<String, ImageVector> = linkedMapOf(
        "battery_alert" to Icons.Rounded.BatteryAlert,
        "battery_charging" to Icons.Rounded.BatteryChargingFull,
        "bolt" to Icons.Rounded.Bolt,
        "power" to Icons.Rounded.Power,
        "speed" to Icons.Rounded.Speed,
        "bluetooth" to Icons.Rounded.Bluetooth,
        "headphones" to Icons.Rounded.Headphones,
        "wifi" to Icons.Rounded.Wifi,
        "router" to Icons.Rounded.Router,
        "signal" to Icons.Rounded.SignalCellularAlt,
        "network_cell" to Icons.Rounded.NetworkCell,
        "five_g" to Icons.Rounded._5g,
        "vpn" to Icons.Rounded.VpnLock,
        "shield" to Icons.Rounded.Shield,
        "lock" to Icons.Rounded.Lock,
        "tethering" to Icons.Rounded.WifiTethering,
        "hub" to Icons.Rounded.Hub,
        "airplane" to Icons.Rounded.AirplanemodeActive,
        "flight" to Icons.Rounded.Flight,
        "alarm" to Icons.Rounded.Alarm,
        "access_alarm" to Icons.Rounded.AccessAlarm,
        "bell" to Icons.Rounded.Notifications,
        "torch" to Icons.Rounded.FlashlightOn,
        "bulb" to Icons.Rounded.Lightbulb,
        "dnd" to Icons.Rounded.DoNotDisturbOn,
        "bedtime" to Icons.Rounded.Bedtime,
        "night" to Icons.Rounded.NightsStay,
        "screenshot" to Icons.Rounded.Screenshot,
        "photo" to Icons.Rounded.PhotoCamera,
        "crop" to Icons.Rounded.Crop,
        "install" to Icons.Rounded.InstallMobile,
        "apps" to Icons.Rounded.Apps,
        "download" to Icons.Rounded.Download,
        "copy" to Icons.Rounded.ContentCopy,
        "paste" to Icons.Rounded.ContentPaste,
        "volume" to Icons.AutoMirrored.Rounded.VolumeUp,
        "music" to Icons.Rounded.MusicNote,
        "vibration" to Icons.Rounded.Vibration,
        "usb" to Icons.Rounded.Usb,
        "cable" to Icons.Rounded.Cable,
        "computer" to Icons.Rounded.Computer,
        "videocam" to Icons.Rounded.Videocam,
        "camera" to Icons.Rounded.CameraAlt,
        "mic" to Icons.Rounded.Mic,
        "voice" to Icons.Rounded.KeyboardVoice,
        "heart" to Icons.Rounded.Favorite,
        "star" to Icons.Rounded.Star,
        "sparkle" to Icons.Rounded.AutoAwesome,
    )

    /** Icônes adaptées à chaque alerte (la première option de l'écran reste « par défaut »). */
    private val choices: Map<String, List<String>> = mapOf(
        "lowBattery" to listOf("battery_alert", "power", "bolt"),
        "charging" to listOf("battery_charging", "bolt", "power", "speed"),
        "bluetooth" to listOf("bluetooth", "headphones", "hub"),
        "wifi" to listOf("wifi", "router", "hub"),
        "cellular" to listOf("signal", "network_cell", "hub"),
        "fiveG" to listOf("five_g", "signal", "speed"),
        "vpn" to listOf("vpn", "shield", "lock"),
        "hotspot" to listOf("tethering", "router", "hub"),
        "airplane" to listOf("airplane", "flight"),
        "alarm" to listOf("alarm", "access_alarm", "bell"),
        "torch" to listOf("torch", "bulb", "sparkle"),
        "dnd" to listOf("dnd", "bedtime", "night"),
        "ringer" to listOf("bell", "vibration", "volume"),
        "mediaVolume" to listOf("volume", "music", "headphones"),
        "usb" to listOf("usb", "cable", "computer"),
        "cameraInUse" to listOf("videocam", "camera", "photo"),
        "micInUse" to listOf("mic", "voice"),
        "screenshot" to listOf("screenshot", "photo", "crop"),
        "packages" to listOf("install", "apps", "download"),
        "clipboard" to listOf("copy", "paste"),
        "echo" to listOf("heart", "star", "sparkle"),
    )

    fun vector(key: String?): ImageVector? = key?.let { all[it] }

    fun choicesFor(alertId: String): List<String> = choices[alertId].orEmpty()

    /** Toutes les clés connues (test de cohérence). */
    val keys: Set<String> get() = all.keys
}

/** Nuancier proposé pour l'accent d'une alerte (ARGB). */
val AlertSwatches: List<Long> = listOf(
    0xFFFF5D5D, 0xFFFFA24C, 0xFFFFE07A, 0xFF5BE38A, 0xFF5ED6E8,
    0xFF6AB0FF, 0xFFB39CFF, 0xFFFF6FA5, 0xFFE8ECF4,
)
