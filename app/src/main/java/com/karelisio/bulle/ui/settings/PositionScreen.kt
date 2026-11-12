package com.karelisio.bulle.ui.settings

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.OpenDirection
import com.karelisio.bulle.core.settings.PositionTweak
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.CutoutLocator
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberLiveValue
import com.karelisio.bulle.ui.components.rememberReduceMotion
import com.karelisio.bulle.ui.components.sharedElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Bornes des décalages (dp), partagées par les curseurs et le réglage au pixel. */
private val RangeX = -60f..60f
private val RangeY = -20f..40f

/** Réglage au pixel : conversions px ↔ dp et cadence de répétition (logique pure, testée). */
object PixelNudge {
    fun dpToPx(dp: Float, density: Float): Int = (dp * density).roundToInt()

    fun pxToDp(px: Int, density: Float): Float = px / density

    /**
     * Nouvelle valeur (dp) après un pas de [stepPx] pixels réels : on repart du pixel affiché
     * (le décalage est appliqué arrondi au pixel par CutoutLocator), puis on borne.
     */
    fun nudge(currentDp: Float, stepPx: Int, density: Float, range: ClosedFloatingPointRange<Float>): Float =
        pxToDp(dpToPx(currentDp, density) + stepPx, density).coerceIn(range)

    /**
     * Délai avant la répétition n° [repeat] quand on maintient le doigt : une pause franche
     * avant la première répétition, puis une accélération progressive jusqu'à un plancher.
     */
    fun repeatDelayMs(repeat: Int): Long =
        if (repeat <= 0) 380L else max(28L, (140.0 * 0.86.pow(repeat - 1)).toLong())
}

/**
 * Position : l'île est calée automatiquement sur la caméra, ces réglages la décalent finement.
 * Aperçu en direct : la vraie île se déplace pendant le réglage.
 */
@Composable
fun PositionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val p = settings.position
    val cutout = remember { CutoutLocator.screenInfo(context) }

    suspend fun save(t: (PositionTweak) -> PositionTweak) =
        SettingsRepository.update(context) { it.copy(position = t(it.position)) }

    fun edit(t: (PositionTweak) -> PositionTweak) = scope.launch { save(t) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.position_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IslandPreview(settings.style, settings.motion, Modifier.sharedElement("island_preview"), height = 150.dp, openDirection = p.openDirection)
            Card(
                Modifier.fillMaxWidth().cascade(0),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.position_live_hint), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    val c = cutout.cutout
                    Text(
                        if (c != null) stringResource(
                            R.string.position_cutout_found,
                            (c.width() / cutout.density).roundToInt(), (c.height() / cutout.density).roundToInt(),
                        ) else stringResource(R.string.position_cutout_none),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            DirectionCard(p.openDirection, index = 1) { d -> edit { it.copy(openDirection = d) } }
            PixelPad(p, index = 2, persistX = { v -> save { it.copy(offsetXDp = v) } }, persistY = { v -> save { it.copy(offsetYDp = v) } })
            TweakSlider(stringResource(R.string.position_x), p.offsetXDp, RangeX, 3) { v -> save { it.copy(offsetXDp = v) } }
            TweakSlider(stringResource(R.string.position_y), p.offsetYDp, RangeY, 4) { v -> save { it.copy(offsetYDp = v) } }
            TweakSlider(stringResource(R.string.position_width), p.extraWidthDp, -30f..120f, 5) { v -> save { it.copy(extraWidthDp = v) } }
            TweakSlider(stringResource(R.string.position_height), p.extraHeightDp, -12f..30f, 6) { v -> save { it.copy(extraHeightDp = v) } }
            FilledTonalButton(onClick = { edit { PositionTweak() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.CenterFocusStrong, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.position_reset))
            }
        }
    }
}

/** Sélecteur du sens d'ouverture : trois cartes avec une mini-île qui s'ouvre en boucle. */
@Composable
private fun DirectionCard(selected: OpenDirection, index: Int, onSelect: (OpenDirection) -> Unit) {
    val reduceMotion = rememberReduceMotion()
    // Progression partagée 0 → 1 → 0 ; figée ouverte si « réduire les animations ».
    val progress: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "direction").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, delayMillis = 350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "open",
        )
    }
    Card(
        Modifier.fillMaxWidth().cascade(index),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.position_direction), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.position_direction_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OpenDirection.entries.forEach { d ->
                    DirectionOption(d, selected == d, progress, Modifier.weight(1f)) { onSelect(d) }
                }
            }
        }
    }
}

@Composable
private fun DirectionOption(direction: OpenDirection, selected: Boolean, progress: State<Float>, modifier: Modifier, onClick: () -> Unit) {
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "lift")
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer { val s = 1f + 0.04f * lift; scaleX = s; scaleY = s },
        shape = RoundedCornerShape(if (selected) 24.dp else 16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.width(72.dp).height(26.dp)) {
                val p = progress.value
                val h = 16.dp.toPx()
                val base = 22.dp.toPx()
                val w = base + (size.width * 0.86f - base) * p
                // Caméra : au centre, ou du côté opposé à l'ouverture.
                val camX = when (direction) {
                    OpenDirection.CENTER -> size.width / 2
                    OpenDirection.LEFT -> size.width * 0.93f - base / 2
                    OpenDirection.RIGHT -> size.width * 0.07f + base / 2
                }
                val left = when (direction) {
                    OpenDirection.CENTER -> camX - w / 2
                    OpenDirection.LEFT -> camX + base / 2 - w
                    OpenDirection.RIGHT -> camX - base / 2
                }
                val top = (size.height - h) / 2
                drawRoundRect(Color.Black, Offset(left, top), Size(w, h), CornerRadius(h / 2))
                drawCircle(Color(0xFF2A3245), radius = 3.dp.toPx(), center = Offset(camX, size.height / 2))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (direction) {
                        OpenDirection.CENTER -> R.string.position_direction_center
                        OpenDirection.LEFT -> R.string.position_direction_left
                        OpenDirection.RIGHT -> R.string.position_direction_right
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Croix directionnelle : chaque appui décale l'île d'un pixel réel, l'appui maintenu accélère. */
@Composable
private fun PixelPad(p: PositionTweak, index: Int, persistX: suspend (Float) -> Unit, persistY: suspend (Float) -> Unit) {
    val density = LocalDensity.current.density
    val view = LocalView.current
    val x = rememberLiveValue(p.offsetXDp, persistX)
    val y = rememberLiveValue(p.offsetYDp, persistY)
    fun step(dxPx: Int, dyPx: Int) {
        if (dxPx != 0) x.set(PixelNudge.nudge(x.value, dxPx, density, RangeX))
        if (dyPx != 0) y.set(PixelNudge.nudge(y.value, dyPx, density, RangeY))
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    Card(
        Modifier.fillMaxWidth().cascade(index),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.position_nudge),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.position_nudge_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            NudgeButton(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.position_nudge_up)) { step(0, -1) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Suppress("DEPRECATION")
                NudgeButton(Icons.Rounded.KeyboardArrowLeft, stringResource(R.string.position_nudge_left)) { step(-1, 0) }
                Box(Modifier.width(132.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.position_nudge_value, signed(PixelNudge.dpToPx(x.value, density)), signed(PixelNudge.dpToPx(y.value, density))),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                @Suppress("DEPRECATION")
                NudgeButton(Icons.Rounded.KeyboardArrowRight, stringResource(R.string.position_nudge_right)) { step(1, 0) }
            }
            NudgeButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.position_nudge_down)) { step(0, 1) }
        }
    }
}

private fun signed(v: Int) = if (v > 0) "+$v" else v.toString()

/** Bouton de la croix : un pas à l'appui, puis répétition accélérée tant que le doigt reste posé. */
@Composable
private fun NudgeButton(icon: ImageVector, description: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    Surface(
        shape = CircleShape,
        color = if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { step(); true }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val press = PressInteraction.Press(down.position)
                        interaction.tryEmit(press)
                        step()
                        val repeat = launch {
                            var n = 0
                            while (true) {
                                delay(PixelNudge.repeatDelayMs(n))
                                n++
                                step()
                            }
                        }
                        val up = waitForUpOrCancellation()
                        repeat.cancel()
                        interaction.tryEmit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
    }
}

/** Curseur avec boutons −/+ au demi-dp près, pour un calage fin. */
@Composable
private fun TweakSlider(label: String, stored: Float, range: ClosedFloatingPointRange<Float>, index: Int, persist: suspend (Float) -> Unit) {
    // Valeur locale + écriture conflatée (voir LiveValue) ; les boutons −/+ partent de la
    // valeur affichée, sinon des appuis rapides se basaient sur une valeur pas encore relue.
    val live = rememberLiveValue(stored, persist)
    val value = live.value
    Card(
        Modifier.fillMaxWidth().cascade(index),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("%+.1f dp".format(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { live.set((value - 0.5f).coerceIn(range)) }) { Icon(Icons.Rounded.Remove, null) }
                Slider(
                    value = value,
                    onValueChange = { live.drag((it * 2).roundToInt() / 2f) },
                    onValueChangeFinished = live::finish,
                    valueRange = range,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                FilledTonalIconButton(onClick = { live.set((value + 0.5f).coerceIn(range)) }) { Icon(Icons.Rounded.Add, null) }
            }
        }
    }
}
