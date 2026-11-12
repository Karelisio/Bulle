package com.karelisio.bulle.modules.alerts.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* Petits glyphes animés des alertes, dessinés au Canvas.
 * Les valeurs animées sont lues dans les lambdas de dessin (phase draw seulement),
 * et toute animation infinie est coupée si l'utilisateur a supprimé les animations. */

/** Animations coupées : réglage système, ou « aucune animation » choisie pour cette alerte. */
@Composable
internal fun rememberGlyphStill(): Boolean = rememberReduceMotion() || LocalAlertStill.current

/** Valeur infinie aller-retour 0..1, figée à [still] si les animations sont coupées. */
@Composable
internal fun rememberPulse(periodMs: Int, enabled: Boolean = true, still: Float = 1f): State<Float> {
    val reduce = rememberGlyphStill()
    if (reduce || !enabled) return remember(still) { mutableFloatStateOf(still) }
    val t = rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
}

/** Valeur 0→1 jouée une seule fois à l'apparition (ressort ou durée), immédiate si animations coupées. */
@Composable
internal fun rememberEntrance(durationMs: Int = 0, delayMs: Int = 0, from: Float = 0f): State<Float> {
    val reduce = rememberGlyphStill()
    val a = remember { Animatable(if (reduce) 1f else from) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        if (durationMs > 0) {
            a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        } else {
            a.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    return a.asState()
}

/**
 * Batterie horizontale. [percent] 0..100 ; remplissage animé de 0 au niveau si [animateFill],
 * éclair si [charging], clignotement doux si [blink].
 */
@Composable
fun BatteryGlyph(
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier,
    charging: Boolean = false,
    blink: Boolean = false,
    animateFill: Boolean = false,
    width: Dp = 26.dp,
    /** Suit l'accent personnalisé de l'alerte (faux pour une batterie d'accessoire). */
    followTint: Boolean = true,
) {
    val color = if (followTint) tinted(color) else color
    val fill = if (animateFill) rememberEntrance(durationMs = 1100) else remember { mutableFloatStateOf(1f) }
    val pulse = rememberPulse(900, enabled = blink, still = 1f)
    val outline = Color.White.copy(alpha = 0.55f)
    Canvas(modifier.size(width, width * 0.52f)) {
        val alpha = if (blink) 0.35f + 0.65f * pulse.value else 1f
        val stroke = size.height * 0.11f
        val nub = size.width * 0.08f
        val body = Size(size.width - nub - stroke, size.height - stroke)
        val r = CornerRadius(size.height * 0.28f)
        drawRoundRect(outline, Offset(stroke / 2, stroke / 2), body, r, style = Stroke(stroke))
        drawRoundRect(
            outline,
            Offset(size.width - nub, size.height * 0.32f),
            Size(nub, size.height * 0.36f),
            CornerRadius(nub / 2),
        )
        val inset = stroke * 1.6f
        val innerW = body.width - inset * 2 + stroke
        val level = (percent / 100f).coerceIn(0.04f, 1f) * fill.value
        drawRoundRect(
            color.copy(alpha = alpha),
            Offset(inset, inset),
            Size(innerW * level, size.height - inset * 2),
            CornerRadius(size.height * 0.16f),
        )
        if (charging) drawBolt(Offset(body.width / 2 + stroke / 2, size.height / 2), size.height * 0.8f, Color.White)
    }
}

/** Éclair centré sur [center], de hauteur [h]. */
internal fun DrawScope.drawBolt(center: Offset, h: Float, color: Color) {
    val w = h * 0.62f
    val p = Path().apply {
        moveTo(center.x + w * 0.12f, center.y - h / 2)
        lineTo(center.x - w * 0.42f, center.y + h * 0.06f)
        lineTo(center.x - w * 0.02f, center.y + h * 0.06f)
        lineTo(center.x - w * 0.14f, center.y + h / 2)
        lineTo(center.x + w * 0.42f, center.y - h * 0.08f)
        lineTo(center.x + w * 0.02f, center.y - h * 0.08f)
        close()
    }
    drawPath(p, Color.Black.copy(alpha = 0.55f), style = Stroke(h * 0.12f, join = StrokeJoin.Round))
    drawPath(p, color)
}

/** Éclair seul, qui « tombe » avec un ressort à l'apparition. */
@Composable
fun BoltGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val color = tinted(color)
    val e = rememberEntrance(from = 0.3f)
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { scaleX = e.value; scaleY = e.value; alpha = e.value.coerceIn(0f, 1f) },
    ) { drawBolt(center, this.size.height, color) }
}

/** Icône Wi-Fi dont les ondes s'allument l'une après l'autre. */
@Composable
fun WifiGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = tinted(color)
    val e = rememberEntrance(durationMs = 900)
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height * 0.86f)
        val stroke = this.size.width * 0.11f
        drawCircle(color, stroke * 0.75f, c, alpha = (e.value * 4).coerceIn(0f, 1f))
        for (i in 1..3) {
            val local = (e.value * 4 - i).coerceIn(0f, 1f)
            val radius = this.size.width * (0.17f + 0.15f * i)
            drawArc(
                color = color.copy(alpha = local),
                startAngle = 225f + 45f * (1 - local) / 2,
                sweepAngle = 90f * (0.4f + 0.6f * local),
                useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Coche qui se trace dans un cercle (animation de tracé via PathMeasure). */
@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = tinted(color)
    val ring = rememberEntrance(durationMs = 450)
    val tick = rememberEntrance(durationMs = 380, delayMs = 300)
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.11f
        drawArc(
            color, -90f, 360f * ring.value, false,
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(s - stroke, s - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val check = Path().apply {
            moveTo(s * 0.28f, s * 0.52f)
            lineTo(s * 0.44f, s * 0.67f)
            lineTo(s * 0.73f, s * 0.36f)
        }
        measure.setPath(check, false)
        segment.reset()
        measure.getSegment(0f, measure.length * tick.value, segment, true)
        drawPath(segment, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Lampe torche stylisée dont les rayons respirent. */
@Composable
fun TorchGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, intensity: Float = 1f) {
    val color = tinted(color)
    val pulse = rememberPulse(1300, still = 0.6f)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2
        // Corps de la lampe (trapèze + manche), vu de profil, faisceau vers le haut.
        val head = Path().apply {
            moveTo(cx - s * 0.2f, s * 0.42f)
            lineTo(cx + s * 0.2f, s * 0.42f)
            lineTo(cx + s * 0.12f, s * 0.6f)
            lineTo(cx - s * 0.12f, s * 0.6f)
            close()
        }
        drawPath(head, Color.White)
        drawRoundRect(Color.White, Offset(cx - s * 0.1f, s * 0.6f), Size(s * 0.2f, s * 0.36f), CornerRadius(s * 0.05f))
        drawCircle(color, s * 0.035f, Offset(cx, s * 0.72f))
        // Rayons.
        val p = pulse.value
        val k = 0.55f + 0.45f * intensity.coerceIn(0f, 1f)
        for (i in -2..2) {
            val ang = (-90f + i * 26f) * (PI / 180f).toFloat()
            val r0 = s * 0.12f + s * 0.24f
            val len = s * (0.12f + 0.1f * p) * k
            val o = Offset(cx, s * 0.42f)
            val start = Offset(o.x + cos(ang) * (r0 - s * 0.24f), o.y + sin(ang) * (r0 - s * 0.24f))
            val end = Offset(start.x + cos(ang) * len, start.y + sin(ang) * len)
            drawLine(color.copy(alpha = 0.55f + 0.45f * p), start, end, s * 0.08f, StrokeCap.Round)
        }
    }
}

/** Icône qui tremble (sonnerie d'alarme). */
@Composable
fun ShakingIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val tint = tinted(tint)
    val reduce = rememberGlyphStill()
    val angle = if (reduce) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "shake").animateFloat(
            0f, 0f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 1200
                    -14f at 60; 14f at 140; -12f at 220; 12f at 300; -8f at 380; 0f at 460
                },
            ),
            label = "a",
        )
    }
    Icon(icon, null, tint = tint, modifier = modifier.size(size).graphicsLayer { rotationZ = angle.value })
}

/** Icône qui apparaît avec un rebond et une petite rotation. */
@Composable
fun PopIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val tint = tinted(tint)
    val e = rememberEntrance(from = 0.2f)
    Icon(
        icon, null, tint = tint,
        modifier = modifier.size(size).graphicsLayer {
            scaleX = e.value; scaleY = e.value
            rotationZ = (1f - e.value) * -40f
            alpha = e.value.coerceIn(0f, 1f)
        },
    )
}

/** Anneau d'accent qui tourne lentement (attente d'une miniature, etc.). */
@Composable
fun SpinnerGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val color = tinted(color)
    val reduce = rememberGlyphStill()
    val turn = if (reduce) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "t",
        )
    }
    Canvas(modifier.size(size)) {
        rotate(turn.value) {
            drawArc(color, 0f, 270f, false, style = Stroke(this.size.minDimension * 0.12f, cap = StrokeCap.Round))
        }
    }
}

/** Texte compact d'une alerte (à droite de la caméra). */
@Composable
fun AlertLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        modifier = modifier,
        // Une couleur explicite suit l'accent personnalisé ; le blanc par défaut reste blanc.
        color = if (color == Color.Unspecified) color else tinted(color),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

/**
 * Barres de réseau mobile. [lost] : barres éteintes et trait qui se trace en travers ;
 * sinon les barres montent l'une après l'autre.
 */
@Composable
fun SignalGlyph(color: Color, lost: Boolean, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = tinted(color)
    val e = rememberEntrance(durationMs = 800)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val gap = s * 0.08f
        val w = (s - gap * 3) / 4
        for (i in 0 until 4) {
            val local = if (lost) 1f else (e.value * 4 - i).coerceIn(0f, 1f)
            val h = s * (0.3f + 0.7f * (i + 1) / 4f) * local
            drawRoundRect(
                if (lost) Color.White.copy(alpha = 0.28f) else color,
                Offset(i * (w + gap), s - h),
                Size(w, h),
                CornerRadius(w * 0.3f),
            )
        }
        if (lost) {
            val k = e.value.coerceIn(0f, 1f)
            val a = Offset(s * 0.06f, s * 0.12f)
            val b = Offset(s * 0.94f, s * 0.94f)
            drawLine(Color.Black.copy(alpha = 0.6f), a, a + (b - a) * k, s * 0.2f, StrokeCap.Round)
            drawLine(color, a, a + (b - a) * k, s * 0.11f, StrokeCap.Round)
        }
    }
}

/** Barre de niveau (volume) : la jauge glisse au ressort vers [fraction], lue au dessin. */
@Composable
fun LevelBar(fraction: Float, color: Color, modifier: Modifier = Modifier, width: Dp = 40.dp, height: Dp = 6.dp) {
    val color = tinted(color)
    val reduce = rememberGlyphStill()
    val target = fraction.coerceIn(0f, 1f)
    val level = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) level.snapTo(target) else level.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    Canvas(modifier.size(width, height)) {
        val r = CornerRadius(this.size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = r)
        val w = (this.size.width * level.value).coerceAtLeast(this.size.height)
        drawRoundRect(color, size = Size(w, this.size.height), cornerRadius = r)
    }
}

/** Pastille de confidentialité (caméra / micro) avec un halo qui respire doucement. */
@Composable
fun PrivacyDot(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val color = tinted(color)
    val pulse = rememberPulse(1400, still = 0f)
    val pop = rememberEntrance(from = 0.2f)
    Canvas(modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        val r = this.size.minDimension / 2
        drawCircle(color.copy(alpha = 0.18f + 0.22f * pulse.value), r)
        drawCircle(color, r * 0.58f)
    }
}
