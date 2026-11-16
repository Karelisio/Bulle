package com.karelisio.bulle.modules.standby.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.bulle.modules.standby.ClockWords
import com.karelisio.bulle.modules.standby.StandbyClock
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Heure courante, mise à jour à chaque seconde (ou chaque minute si [seconds] est faux). */
@Composable
fun rememberNow(seconds: Boolean = true): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(seconds) {
        while (true) {
            now = System.currentTimeMillis()
            val step = if (seconds) 1000L else 60_000L
            delay(step - now % step)
        }
    }
    return now
}

private fun fields(now: Long): Triple<Int, Int, Int> {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    return Triple(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
}

/** Chiffres à chasse fixe (l'heure ne « danse » pas en changeant). */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun StandbyClockView(style: StandbyClock, now: Long, modifier: Modifier = Modifier) {
    val (h, m, _) = fields(now)
    val label = String.format(Locale.getDefault(), "%02d:%02d", h, m)
    Box(modifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        when (style) {
            StandbyClock.DIGITAL -> DigitalClock(now)
            StandbyClock.ANALOG -> AnalogClock(now)
            StandbyClock.WORDS -> WordsClock(now)
            StandbyClock.FLIP -> FlipClock(now)
        }
    }
}

/** Numérique géante, police expressive de l'appli, couleurs dynamiques. */
@Composable
fun DigitalClock(now: Long, modifier: Modifier = Modifier) {
    val (h, m, s) = fields(now)
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = with(LocalDensity.current) { (maxWidth.toPx() / 3.1f).toSp() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format(Locale.getDefault(), "%02d", h),
                style = MaterialTheme.typography.displayLarge.merge(Tabular),
                fontSize = size, fontWeight = FontWeight.Black, color = scheme.primary,
            )
            Text(
                ":",
                style = MaterialTheme.typography.displayLarge,
                fontSize = size, fontWeight = FontWeight.Black,
                color = scheme.tertiary.copy(alpha = if (s % 2 == 0) 1f else 0.35f),
            )
            Text(
                String.format(Locale.getDefault(), "%02d", m),
                style = MaterialTheme.typography.displayLarge.merge(Tabular),
                fontSize = size, fontWeight = FontWeight.Black, color = scheme.secondary,
            )
        }
    }
}

/** Analogique : cadran à 60 graduations, aiguilles aux couleurs dynamiques, trotteuse continue. */
@Composable
fun AnalogClock(now: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val sec = c.get(Calendar.SECOND) + (now % 1000) / 1000f
    val min = c.get(Calendar.MINUTE) + sec / 60f
    val hour = c.get(Calendar.HOUR) + min / 60f
    Canvas(modifier.fillMaxSize().padding(12.dp)) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(scheme.surfaceContainerHigh, r, center)
        for (i in 0 until 60) {
            val major = i % 5 == 0
            rotate(i * 6f, center) {
                drawLine(
                    if (major) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                    Offset(center.x, center.y - r * 0.93f),
                    Offset(center.x, center.y - r * (if (major) 0.80f else 0.87f)),
                    strokeWidth = if (major) r * 0.025f else r * 0.01f,
                    cap = StrokeCap.Round,
                )
            }
        }
        rotate(hour * 30f, center) {
            drawLine(scheme.primary, center, Offset(center.x, center.y - r * 0.5f), r * 0.07f, StrokeCap.Round)
        }
        rotate(min * 6f, center) {
            drawLine(scheme.secondary, center, Offset(center.x, center.y - r * 0.75f), r * 0.045f, StrokeCap.Round)
        }
        rotate(sec * 6f, center) {
            drawLine(scheme.tertiary, Offset(center.x, center.y + r * 0.12f), Offset(center.x, center.y - r * 0.85f), r * 0.015f, StrokeCap.Round)
        }
        drawCircle(scheme.tertiary, r * 0.045f, center)
    }
}

/** L'heure en toutes lettres. */
@Composable
fun WordsClock(now: Long, modifier: Modifier = Modifier) {
    val (h, m, _) = fields(now)
    val french = Locale.getDefault().language == "fr"
    val text = if (french) ClockWords.french(h, m) else ClockWords.english(h, m)
    val scheme = MaterialTheme.colorScheme
    val words = text.split(' ')
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        words.forEachIndexed { i, w ->
            Text(
                w,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = if (i == 0 || i == 1) FontWeight.Medium else FontWeight.Black,
                color = if (i < 2) scheme.onSurfaceVariant else if (i % 2 == 0) scheme.primary else scheme.tertiary,
                lineHeight = 46.sp,
            )
        }
    }
}

/** Horloge à volets : chaque chiffre bascule quand il change. */
@Composable
fun FlipClock(now: Long, modifier: Modifier = Modifier) {
    val (h, m, _) = fields(now)
    val digits = String.format(Locale.getDefault(), "%02d%02d", h, m)
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        FlipDigit(digits[0])
        FlipDigit(digits[1])
        Box(Modifier.width(18.dp))
        FlipDigit(digits[2])
        FlipDigit(digits[3])
    }
}

@Composable
private fun FlipDigit(d: Char) {
    val reduce = rememberReduceMotion()
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(4.dp)
            .size(width = 96.dp, height = 140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            d,
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it } + fadeIn()) togetherWith
                        (slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(200)))
                }
            },
            label = "flip",
        ) { c ->
            Text(
                c.toString(),
                style = MaterialTheme.typography.displayLarge.merge(Tabular),
                fontSize = 104.sp, fontWeight = FontWeight.Black, color = scheme.primary,
            )
        }
        // Pli du volet.
        Box(Modifier.fillMaxWidth().size(height = 2.dp, width = 96.dp).background(Color.Black.copy(alpha = 0.6f)))
    }
}

/** Date du jour et petite horloge (panneau d'accompagnement). */
@Composable
fun DatePanel(now: Long, modifier: Modifier = Modifier, showTime: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val day = c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).orEmpty().replaceFirstChar { it.titlecase(Locale.getDefault()) }
    val date = DateFormat.getDateInstance(DateFormat.LONG).format(Date(now))
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showTime) {
            val (h, m, _) = fields(now)
            Text(
                String.format(Locale.getDefault(), "%02d:%02d", h, m),
                style = MaterialTheme.typography.displayLarge.merge(Tabular),
                fontWeight = FontWeight.Black, color = scheme.primary,
            )
        }
        Text(day, style = MaterialTheme.typography.headlineLarge, color = scheme.tertiary, fontWeight = FontWeight.Bold)
        Text(date, style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
