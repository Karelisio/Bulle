package com.karelisio.bulle.modules.standby.ui

import android.app.Notification
import android.app.NotificationManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.graphics.drawable.toBitmap
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.modules.echo.EchoHeart
import com.karelisio.bulle.modules.echo.EchoPink
import com.karelisio.bulle.modules.focus.FocusModule
import com.karelisio.bulle.modules.live.LiveModule
import com.karelisio.bulle.modules.music.MusicContent
import com.karelisio.bulle.modules.standby.EchoThought
import com.karelisio.bulle.modules.standby.StandbyClock
import com.karelisio.bulle.modules.standby.StandbyLogic
import com.karelisio.bulle.modules.standby.StandbyPage
import com.karelisio.bulle.modules.standby.StandbySettings
import com.karelisio.bulle.notifications.NotificationEvent
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.math.roundToInt

/** Ce que l'écran Veille affiche (fourni par l'activité ou l'économiseur d'écran). */
data class StandbyInputs(
    val settings: StandbySettings,
    val night: Boolean,
    val contents: List<IslandContent> = emptyList(),
    val host: IslandHost? = null,
    val echo: EchoThought? = null,
    val locked: Boolean = true,
    /** Île au repos à l'écran (pixels), point de départ de l'animation signature ; null = inconnue. */
    val islandRect: Rect? = null,
)

/** Carte discrète d'une notification. */
private data class NotifCard(val id: Long, val app: String, val title: String?, val text: String?, val icon: ImageBitmap?)

/**
 * Écran Veille : pages horizontales, chacune avec deux panneaux qui défilent verticalement.
 * Animation signature : l'île descend de la caméra, gonfle jusqu'à remplir l'écran et devient
 * l'horloge ; [exiting] joue l'inverse puis appelle [onExited]. Sans animations : fondu simple.
 */
@Composable
fun StandbyScreen(
    inputs: StandbyInputs,
    exiting: Boolean,
    onExited: () -> Unit,
    onClockChange: (StandbyClock) -> Unit = {},
) {
    val reduce = rememberReduceMotion()
    val density = LocalDensity.current
    val reveal = remember { Animatable(0f) }
    val exited = rememberUpdatedState(onExited)
    LaunchedEffect(reduce) {
        reveal.animateTo(1f, if (reduce) tween(260) else spring(dampingRatio = 0.82f, stiffness = 95f))
    }
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        reveal.animateTo(0f, if (reduce) tween(220) else spring(dampingRatio = 1f, stiffness = 260f))
        exited.value()
    }
    val now = rememberNow(seconds = true)
    // Anti-marquage OLED : tout le contenu se décale de quelques pixels chaque minute.
    val shiftMax = with(density) { (if (inputs.night) 6.dp else 3.dp).toPx() }
    val (sx, sy) = StandbyLogic.pixelShift(now, shiftMax)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (inputs.night) Modifier.graphicsLayer { renderEffect = NightFilter; alpha = 0.55f } else Modifier),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val start = inputs.islandRect ?: with(density) {
            Rect(Offset(w / 2 - 52.dp.toPx(), 6.dp.toPx()), Size(104.dp.toPx(), 30.dp.toPx()))
        }
        val screenCorner = with(density) { 36.dp.toPx() }
        // Île → écran : descente (0 – 0,2) puis gonflement (0,2 – 1).
        fun morph(p: Float): Pair<Rect, Float> {
            val drop = with(density) { 22.dp.toPx() }
            if (p < 0.2f) {
                val d = (p / 0.2f)
                return start.translate(0f, drop * d) to start.height / 2
            }
            val q = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
            val s = start.translate(0f, drop)
            val r = Rect(lerp(s.left, 0f, q), lerp(s.top, 0f, q), lerp(s.right, w, q), lerp(s.bottom, h, q))
            return r to lerp(s.height / 2, screenCorner, q)
        }
        val scheme = MaterialTheme.colorScheme
        if (!reduce) {
            Canvas(Modifier.fillMaxSize()) {
                val p = reveal.value
                if (p >= 0.999f) return@Canvas
                val (r, c) = morph(p)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFF14141A), Color(0xFF0A0A0D)), r.top, r.bottom),
                    r.topLeft, r.size, CornerRadius(c, c),
                )
                drawRoundRect(
                    scheme.primary.copy(alpha = 0.55f * (1f - p)), r.topLeft, r.size, CornerRadius(c, c),
                    style = Stroke(1.5f * this.density),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(sx.roundToInt(), sy.roundToInt()) }
                .graphicsLayer {
                    val p = reveal.value
                    if (reduce) {
                        alpha = p
                    } else {
                        val a = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                        alpha = a
                        val s = 0.88f + 0.12f * a
                        scaleX = s
                        scaleY = s
                        // L'horloge naît dans l'île qui gonfle : contenu découpé à sa forme.
                        val (r, c) = morph(p)
                        shape = RoundShape(r, c)
                        clip = true
                    }
                },
        ) {
            Pages(inputs, now, onClockChange)
            NotificationCards(inputs)
            EchoOverlay(inputs.echo)
        }
    }
}

/** Forme d'un rectangle arrondi placé (découpe du contenu pendant l'animation signature). */
private class RoundShape(private val r: Rect, private val c: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(r, CornerRadius(c, c)))
}

/** Mode nuit : rouge monochrome (luminance vers le seul canal rouge). */
private val NightFilter by lazy {
    val m = ColorMatrix(
        floatArrayOf(
            0.30f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(m)).asComposeRenderEffect()
}

@Composable
private fun Pages(inputs: StandbyInputs, now: Long, onClockChange: (StandbyClock) -> Unit) {
    val pages = StandbyLogic.effectivePages(inputs.settings)
    val pager = rememberPagerState { pages.size }
    val music = inputs.contents.filterIsInstance<MusicContent>().firstOrNull()
    // Activité en cours (minuteur, navigation, livraison) : elle prend la place d'un panneau.
    val live = inputs.contents.firstOrNull { it.moduleId == LiveModule.ID || it.moduleId == FocusModule.ID }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { pages[it].name }) { i ->
            val page = pages[i]
            Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val (leftWeight, left, right) = panels(page, inputs, now, music, onClockChange)
                Box(Modifier.weight(leftWeight).fillMaxHeight()) { Panel(left) }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (live != null) LivePanel(live, inputs.host) else Panel(right)
                }
            }
        }
        if (pages.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == pager.currentPage) 0.9f else 0.35f)),
                    )
                }
            }
        }
    }
}

private typealias PanelItems = List<@Composable () -> Unit>

private fun items(vararg i: @Composable () -> Unit): PanelItems = i.toList()

/** Panneaux d'une page : poids du panneau gauche, éléments gauche et droite (défilement vertical). */
private fun panels(
    page: StandbyPage,
    inputs: StandbyInputs,
    now: Long,
    music: MusicContent?,
    onClockChange: (StandbyClock) -> Unit,
): Triple<Float, PanelItems, PanelItems> = when (page) {
    StandbyPage.CLOCKS -> Triple(
        1.7f,
        items({ ClockStack(inputs.settings.clock, now, onClockChange) }),
        items({ StandbyCard { DatePanel(now) } }, { WeatherPanel() }),
    )
    StandbyPage.WIDGETS -> Triple(
        1f,
        items({ WeatherPanel() }, { NextEventPanel(now) }),
        items({ BatteryPanel(now) }, { LivePanel(null, null) }),
    )
    StandbyPage.MUSIC -> Triple(
        1.3f,
        items({ MusicPanel(music) }),
        items({ LyricsPanel(music) }, { StandbyCard { DatePanel(now, showTime = true) } }),
    )
    StandbyPage.PHOTOS -> Triple(
        1.7f,
        items({ PhotosPanel(inputs.settings.albumId) }),
        items({ StandbyCard { DatePanel(now, showTime = true) } }, { WeatherPanel() }),
    )
    StandbyPage.ECHO -> Triple(
        1.2f,
        items({ EchoPanel(inputs.echo) }),
        items({ StandbyCard { DatePanel(now, showTime = true) } }),
    )
}

/** Un panneau : ses éléments défilent verticalement (un seul : affiché tel quel). */
@Composable
private fun Panel(items: PanelItems) {
    if (items.size == 1) {
        items[0]()
        return
    }
    val state = rememberPagerState { items.size }
    VerticalPager(state, Modifier.fillMaxSize(), pageSpacing = 16.dp) { i -> items[i]() }
}

/** Styles d'horloge, en défilement vertical ; le dernier choisi est retenu. */
@Composable
private fun ClockStack(initial: StandbyClock, now: Long, onChange: (StandbyClock) -> Unit) {
    val styles = StandbyClock.entries
    val state = rememberPagerState(initialPage = styles.indexOf(initial).coerceAtLeast(0)) { styles.size }
    val change = rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { change.value(styles[it]) }
    }
    VerticalPager(state, Modifier.fillMaxSize(), pageSpacing = 16.dp) { i ->
        StandbyCard { StandbyClockView(styles[i], now, Modifier.fillMaxSize()) }
    }
}

/** Notifications : petites cartes discrètes en haut, quelques secondes (contenu masqué si verrouillé). */
@Composable
private fun NotificationCards(inputs: StandbyInputs) {
    if (!inputs.settings.notifications) return
    val context = LocalContext.current
    var card by remember { mutableStateOf<NotifCard?>(null) }
    val locked = rememberUpdatedState(inputs.locked)
    LaunchedEffect(Unit) {
        NotificationRepository.events.filterIsInstance<NotificationEvent.Posted>().collect { e ->
            val n = e.sbn.notification
            if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONLY_ALERT_ONCE) != 0) return@collect
            val ranking = NotificationRepository.ranking(e.sbn.key)
            if (ranking != null && ranking.importance <= NotificationManager.IMPORTANCE_LOW) return@collect
            val pm = context.packageManager
            val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(e.sbn.packageName, 0)).toString() }.getOrDefault(e.sbn.packageName)
            val icon = runCatching { pm.getApplicationIcon(e.sbn.packageName).toBitmap(72, 72).asImageBitmap() }.getOrNull()
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val id = SystemClock.uptimeMillis()
            card = NotifCard(id, app, if (locked.value) null else title, if (locked.value) null else text, icon)
            delay(5_000)
            if (card?.id == id) card = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            card != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val c = card ?: return@AnimatedVisibility
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                c.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape)) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(c.title ?: c.app, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.text ?: c.app, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** « Pense à toi » reçu pendant Veille : le cœur d'Écho bat en grand, quelques secondes. */
@Composable
private fun EchoOverlay(echo: EchoThought?) {
    val startedAt = remember { System.currentTimeMillis() }
    var shown by remember { mutableStateOf<EchoThought?>(null) }
    LaunchedEffect(echo?.at) {
        if (echo == null || echo.at < startedAt) return@LaunchedEffect
        shown = echo
        delay(6_000)
        if (shown == echo) shown = null
    }
    AnimatedVisibility(shown != null, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
        val e = shown ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EchoHeart(size = 220.dp, particles = true)
                e.title?.let { Text(it, style = MaterialTheme.typography.headlineMedium, color = EchoPink, fontWeight = FontWeight.Bold) }
                e.text?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f)) }
            }
        }
    }
}
