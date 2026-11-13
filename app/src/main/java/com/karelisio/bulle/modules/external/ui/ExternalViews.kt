package com.karelisio.bulle.modules.external.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.modules.alerts.sources.sendFromIsland
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.echo.EchoHeart
import com.karelisio.bulle.modules.external.ExternalContent
import com.karelisio.bulle.modules.external.ExternalIcons
import com.karelisio.bulle.modules.external.ExternalPriority
import com.karelisio.bulle.modules.external.ExternalSpec
import com.karelisio.bulle.modules.external.ExternalStatus
import com.karelisio.bulle.modules.external.ExternalTemplate
import com.karelisio.bulle.modules.external.ExternalTime
import com.karelisio.bulle.modules.live.LiveProgress
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.LiveProgressBar
import com.karelisio.bulle.modules.live.ui.ProgressRing
import com.karelisio.bulle.modules.live.ui.PulseDot
import com.karelisio.bulle.modules.live.ui.RollingText
import com.karelisio.bulle.modules.live.ui.rememberWallClock
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/* Rendu des modèles de l'API d'intégration dans l'île : vue compacte (gauche / droite) et étendue. */

/** Nom de l'appli émettrice (mémorisé). */
@Composable
internal fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) { ExternalIcons.appLabel(context, packageName) }
}

/** Icône du contenu (fournie par l'appli, sinon icône de l'appli), chargée hors du fil principal. */
@Composable
internal fun ExternalIcon(spec: ExternalSpec, size: Dp, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.toPx().roundToInt() }
    val icon = remember(spec.icon, spec.iconRes, spec.packageName) { ExternalIcons.iconOf(spec) }
    val bitmap by produceState<ImageBitmap?>(null, icon, spec.packageName, px) {
        value = withContext(Dispatchers.IO) {
            (icon?.let { ExternalIcons.load(context, it, px) } ?: ExternalIcons.appIcon(context, spec.packageName, px))?.asImageBitmap()
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
        } else {
            Icon(Icons.Rounded.Widgets, contentDescription = null, tint = accent, modifier = Modifier.size(size * 0.8f))
        }
    }
}

/** Pastille d'état : verte (ok), ambre (attention), rouge (en panne) ; halo pulsé sauf si tout va bien. */
@Composable
internal fun StatusDot(status: ExternalStatus?, size: Dp, modifier: Modifier = Modifier) {
    val color = ExternalContent.statusColor(status)
    val reduce = rememberReduceMotion()
    val pulse = if (!reduce && status != null && status != ExternalStatus.OK) {
        val t = rememberInfiniteTransition(label = "status")
        val period = if (status == ExternalStatus.DOWN) 900 else 1600
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(period)), label = "p")
    } else {
        null
    }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val p = pulse?.value ?: 0f
        if (pulse != null) drawCircle(color.copy(alpha = 0.45f * (1f - p)), radius = r * (0.5f + 0.5f * p))
        drawCircle(color.copy(alpha = 0.22f), radius = r * 0.62f)
        drawCircle(color, radius = r * 0.42f)
    }
}

/** Petit cercle qui respire lentement (modèle `breathing`, vue compacte). Figé si les animations sont coupées. */
@Composable
internal fun BreathingOrb(size: Dp, accent: Color, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val breath = if (!reduce) {
        val t = rememberInfiniteTransition(label = "orb")
        t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "s")
    } else {
        null
    }
    val brush = remember(accent) { Brush.radialGradient(listOf(accent, accent.copy(alpha = 0.35f))) }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val s = breath?.value ?: 0.8f
        drawCircle(accent.copy(alpha = 0.18f), radius = r)
        drawCircle(brush, radius = r * s)
    }
}

/** Apparition « pop » au ressort (échelle), lue dans graphicsLayer. */
@Composable
private fun Modifier.pop(key: Any?): Modifier {
    val reduce = rememberReduceMotion()
    val scale = remember(key) { Animatable(if (reduce) 1f else 0.6f) }
    LaunchedEffect(key) {
        if (!reduce) scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

private fun ExternalSpec.liveProgress() = LiveProgress(progress, max, indeterminate)
private fun ExternalSpec.percent(): Int = if (max <= 0) 0 else (progress * 100L / max).toInt()

@Composable
private fun durationText(ms: Long): String =
    ExternalTime.format(ms, stringResource(R.string.ext_unit_day), stringResource(R.string.ext_unit_hour))

@Composable
private fun statusLabel(status: ExternalStatus?): String = stringResource(
    when (status) {
        ExternalStatus.WARN -> R.string.ext_status_warn
        ExternalStatus.DOWN -> R.string.ext_status_down
        else -> R.string.ext_status_ok
    },
)

/** Vue compacte, à gauche de la caméra. */
@Composable
fun ExternalLeading(spec: ExternalSpec, accent: Color) {
    when (spec.template) {
        ExternalTemplate.HEART -> EchoHeart(size = 24.dp)
        ExternalTemplate.STATUS -> StatusDot(spec.status, 22.dp)
        ExternalTemplate.BREATHING -> BreathingOrb(22.dp, accent)
        ExternalTemplate.PROGRESS -> Box(contentAlignment = Alignment.Center) {
            ProgressRing(spec.liveProgress(), accent, size = 24.dp, stroke = 2.5.dp)
            ExternalIcon(spec, 14.dp, accent)
        }
        else -> ExternalIcon(spec, 22.dp, accent)
    }
}

/** Vue compacte, à droite de la caméra. */
@Composable
fun ExternalTrailing(spec: ExternalSpec, accent: Color) {
    val digits = MaterialTheme.typography.labelLarge.merge(CompactDigits)
    when (spec.template) {
        ExternalTemplate.PROGRESS ->
            if (spec.indeterminate) PulseDot(accent) else AlertLabel(stringResource(R.string.ext_percent, spec.percent()), color = accent)
        ExternalTemplate.COUNTDOWN -> {
            val now = rememberWallClock()
            RollingText(durationText(ExternalTime.remaining(spec.targetTime ?: now, now)), style = digits, color = Color.White, upward = false)
        }
        ExternalTemplate.CHRONOMETER -> {
            val now = rememberWallClock()
            RollingText(durationText(ExternalTime.elapsed(spec.startTime ?: now, now)), style = digits, color = Color.White)
        }
        ExternalTemplate.LIST -> AlertLabel(
            stringResource(R.string.ext_list_count, spec.items.count { it.checked }, spec.items.size),
            color = accent,
        )
        ExternalTemplate.STATUS -> AlertLabel(statusLabel(spec.status), color = ExternalContent.statusColor(spec.status))
        ExternalTemplate.BREATHING -> AlertLabel(spec.title ?: stringResource(R.string.ext_breath_title), color = accent)
        ExternalTemplate.HEART -> AlertLabel(spec.title ?: rememberAppLabel(spec.packageName), color = accent)
        ExternalTemplate.STANDARD -> AlertLabel(spec.title ?: spec.text ?: rememberAppLabel(spec.packageName))
    }
}

/** Vue étendue : en-tête (glyphe + appli + titre + texte), corps propre au modèle, boutons d'action. */
@Composable
fun ExternalExpanded(spec: ExternalSpec, accent: Color, host: IslandHost, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(spec, accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val app = rememberAppLabel(spec.packageName)
                Text(
                    listOfNotNull(app, spec.subtitle).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    spec.title ?: app,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                spec.text?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (spec.priority == ExternalPriority.ALERT || spec.local) {
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onDismiss() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ext_dismiss), tint = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        Body(spec, accent, host)
        if (spec.actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.actions.forEachIndexed { i, action ->
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            action.intent?.sendFromIsland()
                            host.collapse()
                        },
                        modifier = Modifier.weight(1f).cascade(i),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.22f), contentColor = Color.White),
                    ) {
                        Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Glyphe animé de l'en-tête, propre au modèle. */
@Composable
private fun Glyph(spec: ExternalSpec, accent: Color) {
    val box = Modifier.size(52.dp).pop(spec.key)
    when (spec.template) {
        ExternalTemplate.HEART -> EchoHeart(box, size = 52.dp, particles = true)
        ExternalTemplate.BREATHING -> BreathingOrb(52.dp, accent, Modifier.pop(spec.key))
        ExternalTemplate.STATUS -> Box(box.clip(CircleShape).background(ExternalContent.statusColor(spec.status).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            StatusDot(spec.status, 34.dp)
        }
        ExternalTemplate.PROGRESS -> Box(box, contentAlignment = Alignment.Center) {
            ProgressRing(spec.liveProgress(), accent, size = 52.dp, stroke = 4.dp)
            ExternalIcon(spec, 28.dp, accent)
        }
        ExternalTemplate.COUNTDOWN -> Box(box, contentAlignment = Alignment.Center) {
            val now = rememberWallClock()
            val fraction = ExternalTime.countdownFraction(spec.startTime, spec.targetTime ?: now, now)
            if (fraction != null) ProgressRing(LiveProgress((fraction * 1000).toInt(), 1000, false), accent, size = 52.dp, stroke = 4.dp)
            ExternalIcon(spec, if (fraction != null) 28.dp else 34.dp, accent)
        }
        else -> Box(box.clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            ExternalIcon(spec, 32.dp, accent)
        }
    }
}

/** Corps de la vue étendue selon le modèle. */
@Composable
private fun Body(spec: ExternalSpec, accent: Color, host: IslandHost) {
    val big = MaterialTheme.typography.headlineMedium.merge(CompactDigits)
    when (spec.template) {
        ExternalTemplate.PROGRESS -> Row(verticalAlignment = Alignment.CenterVertically) {
            LiveProgressBar(spec.liveProgress(), accent, Modifier.weight(1f))
            if (!spec.indeterminate) {
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.ext_percent, spec.percent()), style = MaterialTheme.typography.labelLarge.merge(CompactDigits), color = Color.White)
            }
        }
        ExternalTemplate.COUNTDOWN -> {
            val now = rememberWallClock()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.ext_countdown_in), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.width(8.dp))
                RollingText(durationText(ExternalTime.remaining(spec.targetTime ?: now, now)), style = big, color = Color.White, upward = false)
            }
        }
        ExternalTemplate.CHRONOMETER -> {
            val now = rememberWallClock()
            RollingText(durationText(ExternalTime.elapsed(spec.startTime ?: now, now)), style = big, color = accent)
        }
        ExternalTemplate.LIST -> ListBody(spec, accent)
        ExternalTemplate.STATUS -> {
            val color = ExternalContent.statusColor(spec.status)
            Text(
                statusLabel(spec.status),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        ExternalTemplate.BREATHING -> {
            val haptic = LocalHapticFeedback.current
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    host.openPanel(BreathingPanel(spec.breathPattern))
                },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.24f), contentColor = Color.White),
            ) {
                Icon(Icons.Rounded.Air, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ext_breath_start))
            }
        }
        ExternalTemplate.HEART, ExternalTemplate.STANDARD -> Unit
    }
}

/** Liste (jusqu'à 8 lignes) : en deux colonnes au-delà de 4, lignes cochées atténuées et barrées. */
@Composable
private fun ListBody(spec: ExternalSpec, accent: Color) {
    val rows = if (spec.items.size > 4) spec.items.chunked(2) else spec.items.map { listOf(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var index = 0
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Row(Modifier.weight(1f).cascade(index++), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (item.checked) accent else Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = if (item.checked) 0.5f else 0.92f),
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1 && rows.size > 1 && spec.items.size > 4) Spacer(Modifier.weight(1f))
            }
        }
    }
}
