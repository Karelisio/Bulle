package com.karelisio.bulle.modules.network

import android.text.format.DateFormat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.ui.components.rememberReduceMotion
import java.time.ZoneId

/** Priorité « ambiante » : sous la musique, au-dessus de la météo au repos. */
val AmbientNetworkPriority = Priority(70)

val RxColor = Color(0xFF6AD7FF)
val TxColor = Color(0xFFFFB86A)

/** Suffixes de débit selon l'unité (ressources localisées). */
@Composable
fun rateSuffixes(unit: RateUnit): List<String> =
    stringArrayResource(if (unit == RateUnit.BITS) R.array.network_units_bits else R.array.network_units_bytes).toList()

/**
 * Gros transfert en cours : flèches ↓↑ et débit dominant en compact, l'autre sens en seconde
 * ligne ; vue étendue avec mini-graphe, connexion et consommation du jour.
 */
data class NetworkRateContent(
    val snapshot: NetworkSnapshot,
    val unit: RateUnit,
    override val postedAt: Long,
    /** Suffixes de débit (fournis par le module depuis les ressources), pour [compactSubtitle]. */
    val suffixes: List<String> = RateFormat.FR_BYTES_PER_S,
) : IslandContent() {
    override val key = "network:rate"
    override val moduleId = NetworkModule.ID
    override val priority = AmbientNetworkPriority
    override val accent: Color = RxColor
    override val compactSlotWidth: Dp = 66.dp

    private val downDominant: Boolean get() = snapshot.rx >= snapshot.tx

    override val compactSubtitle: String
        get() = if (downDominant) "↑ " + RateFormat.rate(snapshot.tx, unit, suffixes) else "↓ " + RateFormat.rate(snapshot.rx, unit, suffixes)

    @Composable
    override fun Leading() = TrafficArrows(down = snapshot.rx, up = snapshot.tx, size = 20.dp)

    @Composable
    override fun Trailing() {
        val value = if (downDominant) snapshot.rx else snapshot.tx
        Text(
            RateFormat.rate(value, unit, rateSuffixes(unit)),
            style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
            color = if (downDominant) RxColor else TxColor,
            maxLines = 1,
            softWrap = false,
        )
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        // La vue ouverte garde le contenu affiché et lit l'échantillonneur en direct.
        DisposableEffect(Unit) {
            NetworkMonitor.addViewer()
            onDispose { NetworkMonitor.removeViewer() }
        }
        val context = LocalContext.current
        val live by remember(context) { NetworkMonitor.snapshots(context) }.collectAsStateWithLifecycle(snapshot)
        val usage by remember(context) { NetworkUsageStore.flow(context) }.collectAsStateWithLifecycle(null)
        NetworkDetails(live, unit, usage)
    }
}

/** Corps de la vue étendue (réutilisé par l'aperçu des réglages). */
@Composable
fun NetworkDetails(snapshot: NetworkSnapshot, unit: RateUnit, usage: UsageState?, modifier: Modifier = Modifier, onDark: Boolean = true) {
    val suffixes = rateSuffixes(unit)
    val primaryText = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryText = primaryText.copy(alpha = 0.65f)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(RxColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(connectionIcon(snapshot.connection.kind), contentDescription = null, tint = RxColor, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.network_module_title), style = MaterialTheme.typography.labelMedium, color = secondaryText)
                Text(
                    connectionLabel(snapshot.connection),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RateColumn("↓", stringResource(R.string.network_down), RateFormat.rate(snapshot.rx, unit, suffixes), RxColor, secondaryText, Modifier.weight(1f))
            RateColumn("↑", stringResource(R.string.network_up), RateFormat.rate(snapshot.tx, unit, suffixes), TxColor, secondaryText, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        RateGraph(snapshot.history, Modifier.fillMaxWidth().height(44.dp), baseline = primaryText.copy(alpha = 0.12f))
        if (usage != null) {
            Spacer(Modifier.height(10.dp))
            UsageLine(usage, primaryText, secondaryText)
        }
    }
}

@Composable
private fun RateColumn(arrow: String, label: String, value: String, color: Color, secondary: Color, modifier: Modifier) {
    Column(modifier) {
        Text("$arrow $label", style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.merge(CompactDigits),
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Consommation du jour : « Aujourd'hui · Wi-Fi 1,2 Go · Mobile 340 Mo », avec mention de l'activation. */
@Composable
fun UsageLine(usage: UsageState, primary: Color, secondary: Color) {
    val context = LocalContext.current
    val amounts = stringArrayResource(R.array.network_units_amount).toList()
    val today = remember(usage) { DailyUsage.today(usage, System.currentTimeMillis(), ZoneId.systemDefault()) }
    Column {
        Text(
            stringResource(R.string.network_usage_line, RateFormat.amount(today.wifiBytes, amounts), RateFormat.amount(today.mobileBytes, amounts)),
            style = MaterialTheme.typography.bodyMedium,
            color = primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (today.partial) {
                stringResource(R.string.network_usage_since, DateFormat.getTimeFormat(context).format(java.util.Date(today.since)))
            } else {
                stringResource(R.string.network_usage_today)
            },
            style = MaterialTheme.typography.labelSmall,
            color = secondary,
        )
    }
}

@Composable
fun connectionLabel(info: ConnectionInfo): String {
    val base = stringResource(
        when (info.kind) {
            NetKind.WIFI -> R.string.network_kind_wifi
            NetKind.MOBILE -> R.string.network_kind_mobile
            NetKind.ETHERNET -> R.string.network_kind_ethernet
            NetKind.NONE -> R.string.network_kind_none
        },
    )
    return if (info.vpn) "$base · ${stringResource(R.string.network_kind_vpn)}" else base
}

fun connectionIcon(kind: NetKind): ImageVector = when (kind) {
    NetKind.WIFI -> Icons.Rounded.Wifi
    NetKind.MOBILE -> Icons.Rounded.SignalCellularAlt
    NetKind.ETHERNET -> Icons.Rounded.Cable
    NetKind.NONE -> Icons.Rounded.WifiOff
}

/** Deux flèches ↓↑ : le sens dominant est vif, l'autre atténué. */
@Composable
fun TrafficArrows(down: Double, up: Double, size: Dp) {
    val downStrong = down >= up
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawArrow(Offset(w * 0.3f, h * 0.15f), Offset(w * 0.3f, h * 0.85f), RxColor.copy(alpha = if (downStrong) 1f else 0.4f))
        drawArrow(Offset(w * 0.7f, h * 0.85f), Offset(w * 0.7f, h * 0.15f), TxColor.copy(alpha = if (downStrong) 0.4f else 1f))
    }
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color) {
    val stroke = size.width * 0.13f
    drawLine(color, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
    val dir = if (to.y > from.y) -1f else 1f
    val head = size.width * 0.18f
    drawLine(color, to, Offset(to.x - head, to.y + dir * head), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, to, Offset(to.x + head, to.y + dir * head), strokeWidth = stroke, cap = StrokeCap.Round)
}

/** Mini-graphe des 60 dernières secondes : aire du débit descendant, trait du montant. */
@Composable
fun RateGraph(history: List<RatePoint>, modifier: Modifier = Modifier, baseline: Color = Color.White.copy(alpha = 0.12f)) {
    val reduceMotion = rememberReduceMotion()
    val target = (history.maxOfOrNull { maxOf(it.rx, it.tx) } ?: 0.0).coerceAtLeast(10_000.0).toFloat()
    // Échelle verticale qui glisse au ressort (lue au dessin, pas en composition).
    val scale = animateFloatAsState(
        target,
        animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessLow),
        label = "graphScale",
    )
    val description = stringResource(R.string.network_graph_description)
    Canvas(modifier.semantics { contentDescription = description }) {
        val max = scale.value
        val capacity = 60
        val step = size.width / (capacity - 1)
        val offset = (capacity - history.size).coerceAtLeast(0)
        fun y(v: Double) = size.height - (v / max).toFloat().coerceIn(0f, 1f) * size.height
        // Ligne de base discrète.
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (history.size < 2) return@Canvas
        val rxLine = Path()
        val txLine = Path()
        history.forEachIndexed { i, p ->
            val x = (offset + i) * step
            if (i == 0) {
                rxLine.moveTo(x, y(p.rx)); txLine.moveTo(x, y(p.tx))
            } else {
                rxLine.lineTo(x, y(p.rx)); txLine.lineTo(x, y(p.tx))
            }
        }
        val area = Path().apply {
            addPath(rxLine)
            lineTo((offset + history.lastIndex) * step, size.height)
            lineTo(offset * step, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(RxColor.copy(alpha = 0.45f), RxColor.copy(alpha = 0.02f))))
        drawPath(rxLine, RxColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(txLine, TxColor, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
