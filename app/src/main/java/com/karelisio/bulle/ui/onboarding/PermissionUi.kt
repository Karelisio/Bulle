package com.karelisio.bulle.ui.onboarding

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.karelisio.bulle.R
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.ui.components.MorphShape
import com.karelisio.bulle.ui.components.rememberReduceMotion
import android.os.Build

/** Textes et icône d'une autorisation. */
data class PermissionTexts(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val desc: Int,
)

fun PermissionKey.texts(family: OemSettings.Family = OemSettings.family): PermissionTexts = when (this) {
    PermissionKey.RESTRICTED -> PermissionTexts(Icons.Rounded.LockOpen, R.string.perm_restricted_title, R.string.perm_restricted_desc)
    PermissionKey.ACCESSIBILITY -> PermissionTexts(Icons.Rounded.Accessibility, R.string.perm_accessibility_title, R.string.perm_accessibility_desc)
    PermissionKey.NOTIFICATION_ACCESS -> PermissionTexts(Icons.Rounded.NotificationsActive, R.string.perm_notif_access_title, R.string.perm_notif_access_desc)
    PermissionKey.POST_NOTIFICATIONS -> PermissionTexts(Icons.Rounded.Notifications, R.string.perm_post_notif_title, R.string.perm_post_notif_desc)
    PermissionKey.INSTALL_PACKAGES -> PermissionTexts(Icons.Rounded.SystemUpdate, R.string.perm_install_title, R.string.perm_install_desc)
    PermissionKey.BATTERY -> PermissionTexts(Icons.Rounded.BatteryChargingFull, R.string.perm_battery_title, R.string.perm_battery_desc)
    PermissionKey.OEM_LAUNCH -> PermissionTexts(
        Icons.Rounded.RocketLaunch,
        if (family == OemSettings.Family.HONOR) R.string.perm_oem_title_honor else R.string.perm_oem_title,
        R.string.perm_oem_desc,
    )
    PermissionKey.BLUETOOTH -> PermissionTexts(Icons.Rounded.Bluetooth, R.string.perm_bluetooth_title, R.string.perm_bluetooth_desc)
    PermissionKey.LOCATION -> PermissionTexts(Icons.Rounded.LocationOn, R.string.perm_location_title, R.string.perm_location_desc)
    PermissionKey.MEDIA_IMAGES -> PermissionTexts(Icons.Rounded.Image, R.string.perm_media_title, R.string.perm_media_desc)
    PermissionKey.WRITE_SETTINGS -> PermissionTexts(Icons.Rounded.Settings, R.string.perm_write_settings_title, R.string.perm_write_settings_desc)
    PermissionKey.SECURE_SETTINGS -> PermissionTexts(Icons.Rounded.Terminal, R.string.perm_secure_title, R.string.perm_secure_desc)
    PermissionKey.DND_ACCESS -> PermissionTexts(Icons.Rounded.DoNotDisturbOn, R.string.perm_dnd_title, R.string.perm_dnd_desc)
}

/**
 * État en direct des autorisations : relu à chaque retour au premier plan (retour des
 * réglages système) et après chaque demande d'autorisation d'exécution.
 */
@Stable
class PermissionController internal constructor(private val context: Context) {
    var granted by mutableStateOf(PermissionChecks.snapshot(context))
        private set

    internal var launch: (String) -> Unit = {}
    internal var shouldShowRationale: (String) -> Boolean = { true }

    /** Incrémenté à chaque relecture, pour que les lectures annexes se recomposent. */
    private var revision by mutableIntStateOf(0)

    operator fun get(key: PermissionKey): Boolean = granted[key] == true

    /** Case « C'est fait » cochée pour cette étape manuelle ? */
    fun isConfirmed(key: PermissionKey): Boolean {
        revision
        return PermissionChecks.isConfirmed(context, key)
    }

    fun refresh() {
        granted = PermissionChecks.snapshot(context)
        revision++
    }

    fun setConfirmed(key: PermissionKey, done: Boolean) {
        PermissionChecks.setConfirmed(context, key, done)
        refresh()
    }

    /**
     * Demande l'autorisation : boîte de dialogue système pour une autorisation d'exécution
     * (ou fiche de l'appli si elle a été refusée définitivement), sinon écran de réglages.
     */
    fun request(key: PermissionKey) {
        val runtime = PermissionLogic.runtimePermission(key, Build.VERSION.SDK_INT)
        if (runtime == null) {
            PermissionChecks.openSettings(context, key)
            return
        }
        val blocked = this[key] ||
            (PermissionChecks.wasRequested(context, key) && !shouldShowRationale(runtime))
        if (blocked) {
            OemSettings.openSafely(context, OemSettings.appDetailsIntent(context))
        } else {
            PermissionChecks.markRequested(context, key)
            launch(runtime)
        }
    }
}

@Composable
fun rememberPermissionController(): PermissionController {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val controller = remember(context) { PermissionController(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { controller.refresh() }
    controller.launch = { launcher.launch(it) }
    controller.shouldShowRationale = { perm -> activity?.shouldShowRequestPermissionRationale(perm) ?: true }
    LifecycleResumeEffect(controller) {
        controller.refresh()
        onPauseOrDispose { }
    }
    return controller
}

/** Vibration de confirmation quand [granted] passe de faux à vrai. */
@Composable
fun ConfirmHaptic(granted: Boolean) {
    val haptic = LocalHapticFeedback.current
    var previous by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) {
        if (granted && !previous) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        previous = granted
    }
}

/**
 * Coche qui se dessine : le cercle se trace, puis le crochet, avec un léger rebond.
 */
@Composable
fun AnimatedCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(if (checked) 1f else 0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (reduce) {
            progress.snapTo(target)
        } else if (checked) {
            progress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
            pop.snapTo(1.18f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        } else {
            progress.animateTo(0f, tween(220))
        }
    }
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    ) {
        val p = progress.value
        if (p <= 0f) return@Canvas
        val stroke = this.size.minDimension * 0.1f
        val inset = stroke / 2f
        val circle = (p / 0.55f).coerceIn(0f, 1f)
        drawCircle(color.copy(alpha = 0.16f * circle), radius = this.size.minDimension / 2f)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * circle,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val tick = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
        if (tick > 0f) {
            val w = this.size.width
            val h = this.size.height
            path.reset()
            path.moveTo(w * 0.29f, h * 0.52f)
            path.lineTo(w * 0.44f, h * 0.67f)
            path.lineTo(w * 0.72f, h * 0.37f)
            measure.setPath(path, false)
            segment.reset()
            measure.getSegment(0f, measure.length * tick, segment, true)
            drawPath(segment, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Icône d'autorisation dans une forme M3 Expressive : un « cookie » qui tourne doucement
 * tant que l'autorisation manque, puis se change en cercle au ressort une fois accordée.
 */
@Composable
fun PermissionGlyph(
    icon: ImageVector,
    granted: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animated: Boolean = true,
) {
    val reduce = rememberReduceMotion()
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val progress by animateFloatAsState(
        targetValue = if (granted) 1f else 0f,
        animationSpec = if (reduce) tween(0) else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "glyph",
    )
    val spin = if (animated && !reduce && !granted) {
        val transition = rememberInfiniteTransition(label = "spin")
        val r by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
            label = "rotation",
        )
        r
    } else {
        0f
    }
    val container by animateColorAsState(
        if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        label = "container",
    )
    val content by animateColorAsState(
        if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        label = "content",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { rotationZ = spin }
            .clip(MorphShape(morph, progress))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(size * 0.46f)
                .graphicsLayer { rotationZ = -spin },
        )
    }
}

/** Nature de la pastille d'état. */
enum class PillState { GRANTED, DENIED, PENDING, OPTIONAL }

/** Pastille d'état animée (couleur au ressort, texte en fondu, petit rebond). */
@Composable
fun StatusPill(state: PillState, modifier: Modifier = Modifier, label: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (state) {
        PillState.GRANTED -> scheme.primary to scheme.onPrimary
        PillState.DENIED -> scheme.errorContainer to scheme.onErrorContainer
        PillState.PENDING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        PillState.OPTIONAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    val container by animateColorAsState(bg, spring(stiffness = Spring.StiffnessMediumLow), label = "pillBg")
    val text by animateColorAsState(fg, label = "pillFg")
    val reduce = rememberReduceMotion()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (reduce) return@LaunchedEffect
        pop.snapTo(0.86f)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    val defaultLabel = stringResource(
        when (state) {
            PillState.GRANTED -> R.string.perm_state_granted
            PillState.DENIED -> R.string.perm_state_denied
            PillState.PENDING -> R.string.perm_state_pending
            PillState.OPTIONAL -> R.string.perm_state_optional
        },
    )
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = text,
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
    ) {
        AnimatedContent(
            targetState = state to (label ?: defaultLabel),
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.8f)) },
            label = "pill",
        ) { (s, l) ->
            Row(
                Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    if (s == PillState.GRANTED) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(l, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Liste d'étapes numérotées, chaque numéro dans une forme expressive. */
@Composable
fun StepList(steps: List<String>, modifier: Modifier = Modifier) {
    val shapes = listOf(MaterialShapes.Cookie4Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(shapes[i % shapes.size].toShape())
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
