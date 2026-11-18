package com.karelisio.bulle.modules.standby.ui

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.modules.halo.HaloDevice
import com.karelisio.bulle.modules.music.MusicContent
import com.karelisio.bulle.modules.standby.SceneLogic
import com.karelisio.bulle.modules.standby.ScenePalette
import com.karelisio.bulle.modules.standby.SceneSettings
import com.karelisio.bulle.modules.standby.StandbyScene
import com.karelisio.bulle.notifications.NotificationEvent
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Heure « HH:MM » d'un instant. */
private fun hhmm(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(Locale.getDefault(), "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** Changement de minute de l'horloge liquide (hors état Compose : lu au dessin de chaque image). */
private class MorphClock(var current: String, var previous: String? = null, var changedAt: Long = 0L)

/**
 * Scène animée de Veille. [fullScreen] : page Scène (horloge discrète par-dessus si réglée) ;
 * sinon widget dans un panneau de la vue scindée. Les images s'arrêtent d'elles-mêmes quand la
 * page n'est pas affichée ou l'écran éteint (plus d'images demandées par le système).
 */
@Composable
fun StandbySceneView(settings: SceneSettings, music: MusicContent?, fullScreen: Boolean, modifier: Modifier = Modifier) {
    val s = settings.sanitized()
    val context = LocalContext.current
    val reduce = rememberReduceMotion()
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    LaunchedEffect(power) {
        while (true) {
            delay(30_000)
            powerSave = power?.isPowerSaveMode == true
        }
    }
    val interval = SceneLogic.frameIntervalMs(powerSave, s.eco, reduce)
    val colors = sceneColors(context, s.palette, music)

    var t by remember { mutableFloatStateOf(0f) }
    var frameUptime by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val morph = remember { MorphClock(hhmm(System.currentTimeMillis())) }
    val speed by rememberUpdatedState(s.speed * if (reduce) 0.2f else 1f)
    LaunchedEffect(interval) {
        var last = 0L
        while (true) {
            val frame = withFrameMillis { it }
            if (last != 0L) t += (frame - last).coerceIn(0L, max(100L, interval + 50L)) / 1000f * speed
            last = frame
            frameUptime = SystemClock.uptimeMillis()
            val label = hhmm(System.currentTimeMillis())
            if (label != morph.current) {
                morph.previous = morph.current
                morph.current = label
                morph.changedAt = frameUptime
            }
            if (interval > 0) delay(interval)
        }
    }

    // Notification : pulsation de la couleur de l'icône de l'appli.
    var pulseAt by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    var pulseColor by remember { mutableStateOf(Color.White) }
    val fallback by rememberUpdatedState(colors.first())
    LaunchedEffect(s.react) {
        if (!s.react) return@LaunchedEffect
        NotificationRepository.events.filterIsInstance<NotificationEvent.Posted>().collect { e ->
            val n = e.sbn.notification
            if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONLY_ALERT_ONCE) != 0) return@collect
            val ranking = NotificationRepository.ranking(e.sbn.key)
            if (ranking != null && ranking.importance <= NotificationManager.IMPORTANCE_LOW) return@collect
            pulseColor = HaloDevice.iconColor(context, e.sbn.packageName)?.let { Color(it) } ?: fallback
            pulseAt = SystemClock.uptimeMillis()
        }
    }

    val scene = SceneLogic.sceneAt(s, System.currentTimeMillis())
    val dens = SceneLogic.effectiveDensity(s, powerSave)
    val playing = music?.playback?.isPlaying == true
    val px = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val label = standbySceneLabel(scene)

    BoxWithConstraints(modifier.fillMaxSize().semantics { contentDescription = label }) {
        val blur = with(px) { (if (fullScreen) 18.dp else 12.dp).toPx() }
        fun frame() = SceneFrame(t, colors, dens, SceneLogic.pulse(frameUptime - pulseAt), pulseColor, playing)
        when (scene) {
            StandbyScene.LAVA -> Canvas(Modifier.fillMaxSize().gooey(blur)) { drawLava(frame()) }
            StandbyScene.BUBBLES -> Canvas(Modifier.fillMaxSize()) { drawBubbles(frame()) }
            StandbyScene.AURORA -> {
                Canvas(Modifier.fillMaxSize()) { drawTwinkles(frame(), SceneLogic.count(40, dens)) }
                Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = softBlur(blur * 0.6f) }) { drawAurora(frame()) }
            }
            StandbyScene.WAVES -> Canvas(Modifier.fillMaxSize()) { drawWaves(frame()) }
            StandbyScene.STARS -> Canvas(Modifier.fillMaxSize()) { drawStars(frame()) }
            StandbyScene.LIQUID_CLOCK -> {
                val fontPx = minOf(constraints.maxWidth / 3.1f, constraints.maxHeight * 0.55f)
                val style = TextStyle(fontSize = with(px) { fontPx.toSp() }, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum")
                Canvas(Modifier.fillMaxSize().gooey(blur)) {
                    val f = frame()
                    val cur = measurer.measure(morph.current, style)
                    val prev = morph.previous?.let { measurer.measure(it, style) }
                    val m = ((frameUptime - morph.changedAt) / 1400f).coerceIn(0f, 1f)
                    drawLiquidClock(f, cur, prev, m)
                }
                if (fullScreen) {
                    Text(
                        DateFormat.getDateInstance(DateFormat.FULL).format(Date()),
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawPulse(frame()) }
        if (fullScreen && s.clock && scene != StandbyScene.LIQUID_CLOCK) SceneClockOverlay()
    }
}

/** Calque liquide (flou + seuil), dessiné à part pour que le seuil ne touche que la scène. */
private fun Modifier.gooey(blurPx: Float): Modifier = graphicsLayer {
    renderEffect = gooeyEffect(blurPx)
    compositingStrategy = CompositingStrategy.Offscreen
}

/** Horloge discrète qui change de coin toutes les 5 minutes (anti-marquage). */
@Composable
private fun SceneClockOverlay() {
    val now = rememberNow(seconds = false)
    val (ax, ay) = SceneLogic.clockAnchor(now)
    val x by animateFloatAsState(ax, tween(2_000), label = "sceneClockX")
    val y by animateFloatAsState(ay, tween(2_000), label = "sceneClockY")
    Column(
        Modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val px = (constraints.maxWidth * x - p.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0))
                val py = (constraints.maxHeight * y - p.height / 2f).roundToInt().coerceIn(0, (constraints.maxHeight - p.height).coerceAtLeast(0))
                p.place(px, py)
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(hhmm(now), style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.85f))
        Text(
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now)),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

/** Couleurs d'une scène selon la palette (Material You, pochette ou fixe). */
@Composable
private fun sceneColors(context: Context, p: ScenePalette, music: MusicContent?): List<Color> {
    val accent = music?.artwork?.accent
    return remember(p, accent) {
        SceneLogic.fixedPalette(p)?.map { Color(it) }
            ?: if (p == ScenePalette.ARTWORK && accent != null) harmonies(accent) else dynamicColors(context)
    }
}

/** Accents Material You clairs (brillent sur le noir). */
private fun dynamicColors(context: Context): List<Color> = listOf(
    android.R.color.system_accent1_300,
    android.R.color.system_accent3_300,
    android.R.color.system_accent2_200,
    android.R.color.system_accent1_500,
).map { id -> runCatching { Color(context.getColor(id)) }.getOrDefault(Color(0xFF8AB4F8)) }

/** Quatre teintes voisines de la couleur de la pochette, saturées et lumineuses. */
private fun harmonies(c: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
    val s = hsv[1].coerceAtLeast(0.5f)
    val v = hsv[2].coerceAtLeast(0.85f)
    return listOf(0f, 28f, -28f, 56f).map { d ->
        Color(android.graphics.Color.HSVToColor(floatArrayOf(((hsv[0] + d) % 360f + 360f) % 360f, s, v)))
    }
}

/** Carte du widget Scène (vue scindée). */
@Composable
fun SceneWidget(settings: SceneSettings, music: MusicContent?) {
    StandbyCard {
        Box(Modifier.fillMaxSize()) { StandbySceneView(settings, music, fullScreen = false) }
    }
}
