package com.karelisio.bulle.modules.screentime

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.external.BreathPattern
import com.karelisio.bulle.modules.external.ui.BreathingPanel
import com.karelisio.bulle.modules.live.ui.CompactDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Priorité « ambiante » : la plus basse des contenus de repos (sous la météo). */
val AmbientScreenTimePriority = Priority(40)

val ScreenTimeColor = Color(0xFF9FA8FF)
val BreakColor = Color(0xFF7BE0A0)
val LimitColor = Color(0xFFFF8A80)

/** Recalcul de la vue étendue pendant qu'elle est visible. */
const val SCREEN_TIME_REFRESH_MS = 5 * 60_000L

/** Découpage d'une durée en heures et minutes (arrondi à la minute inférieure). */
fun hoursMinutes(ms: Long): Pair<Int, Int> {
    val minutes = (ms.coerceAtLeast(0) / 60_000L).toInt()
    return minutes / 60 to minutes % 60
}

/** « 2 h 14 » ou « 45 min ». */
@Composable
fun formatScreenDuration(ms: Long): String {
    val (h, m) = hoursMinutes(ms)
    return if (h > 0) stringResource(R.string.screentime_duration_hm, h, m) else stringResource(R.string.screentime_duration_m, m)
}

/** Temps d'écran du jour, discret au repos (désactivé par défaut). */
data class ScreenTimeContent(val totalMs: Long) : IslandContent() {
    override val key = KEY
    override val moduleId = ScreenTimeModule.ID
    override val priority = AmbientScreenTimePriority
    override val accent: Color = ScreenTimeColor
    override val compactSlotWidth: Dp = 64.dp

    @Composable
    override fun Leading() = Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = ScreenTimeColor, modifier = Modifier.size(20.dp))

    @Composable
    override fun Trailing() = Text(
        formatScreenDuration(totalMs),
        style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
        color = ScreenTimeColor,
        maxLines = 1,
        softWrap = false,
    )

    @Composable
    override fun Expanded(host: IslandHost) = LiveScreenTimeDetails()

    companion object {
        const val KEY = "screentime:today"
    }
}

/** Rappel ponctuel « Fais une pause », avec une courte respiration en option. */
data class BreakAlertContent(
    val minutes: Int,
    val breathing: Boolean,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "screentime:break:$postedAt"
    override val moduleId = ScreenTimeModule.ID
    override val priority = Priority.ALERT
    override val timeoutMs: Long = 8_000L
    override val expandable: Boolean = breathing
    override val canBeSecondary: Boolean = false
    override val accent: Color = BreakColor
    override val compactSlotWidth: Dp = 96.dp

    override val compactSubtitle: String? = null

    @Composable
    override fun Leading() = Icon(Icons.Rounded.SelfImprovement, contentDescription = null, tint = BreakColor, modifier = Modifier.size(22.dp))

    @Composable
    override fun Trailing() = Text(
        stringResource(R.string.screentime_break_title),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = BreakColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    @Composable
    override fun Expanded(host: IslandHost) {
        val haptic = LocalHapticFeedback.current
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(BreakColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.SelfImprovement, contentDescription = null, tint = BreakColor, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.screentime_break_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        pluralStringResource(R.plurals.screentime_break_body, minutes, minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            if (breathing) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    host.openPanel(BreathingPanel(BreathPattern.RELAX_478, cycles = BREATH_CYCLES))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.screentime_break_breathe))
                }
            }
        }
    }

    companion object {
        /** Respiration courte : deux cycles 4-7-8 (≈ 40 s). */
        const val BREATH_CYCLES = 2
    }
}

/** Limite quotidienne d'une appli atteinte. */
data class LimitAlertContent(
    val packageName: String,
    val label: String,
    val limitMinutes: Int,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "screentime:limit:$packageName"
    override val moduleId = ScreenTimeModule.ID
    override val priority = Priority.ALERT
    override val timeoutMs: Long = 6_000L
    override val expandable: Boolean = false
    override val canBeSecondary: Boolean = false
    override val accent: Color = LimitColor
    override val compactSlotWidth: Dp = 96.dp
    override val compactSubtitle: String = label

    @Composable
    override fun Leading() = AppIcon(packageName, 22.dp, fallbackTint = LimitColor)

    @Composable
    override fun Trailing() = Text(
        stringResource(R.string.screentime_limit_reached),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LimitColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Nom et icône d'une appli (mis en cache, chargés hors du fil principal). */
data class AppInfo(val label: String, val icon: ImageBitmap?)

internal object AppInfoCache {
    private val cache = LruCache<String, AppInfo>(48)

    fun peek(pkg: String): AppInfo? = cache.get(pkg)

    fun load(context: Context, pkg: String): AppInfo {
        cache.get(pkg)?.let { return it }
        val pm = context.packageManager
        val info = runCatching {
            val app = pm.getApplicationInfo(pkg, 0)
            AppInfo(pm.getApplicationLabel(app).toString(), runCatching { pm.getApplicationIcon(app).toBitmap(96, 96).asImageBitmap() }.getOrNull())
        }.getOrDefault(AppInfo(pkg, null))
        cache.put(pkg, info)
        return info
    }
}

@Composable
fun rememberAppInfo(pkg: String): AppInfo {
    val context = LocalContext.current
    val info by produceState(AppInfoCache.peek(pkg) ?: AppInfo(pkg, null), pkg) {
        value = withContext(Dispatchers.IO) { AppInfoCache.load(context, pkg) }
    }
    return info
}

@Composable
fun AppIcon(pkg: String, size: Dp, fallbackTint: Color = ScreenTimeColor) {
    val icon = rememberAppInfo(pkg).icon
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Icon(Icons.Rounded.Timer, contentDescription = null, tint = fallbackTint, modifier = Modifier.size(size))
    }
}

/** Vue étendue en direct : recalcul à l'ouverture puis toutes les 5 min tant qu'elle est visible. */
@Composable
fun LiveScreenTimeDetails(modifier: Modifier = Modifier, onDark: Boolean = true) {
    val context = LocalContext.current
    val state by produceState<Pair<Boolean, ScreenTimeReport?>?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { UsageAccess.hasAccess(context) to UsageAccess.report(context) }
            delay(SCREEN_TIME_REFRESH_MS)
        }
    }
    val (access, report) = state ?: (true to null)
    ScreenTimeDetails(report, hasAccess = access, modifier = modifier, onDark = onDark)
}

/** Corps de la vue étendue : total, comparaison avec hier, déverrouillages, barres par appli. */
@Composable
fun ScreenTimeDetails(report: ScreenTimeReport?, hasAccess: Boolean, modifier: Modifier = Modifier, onDark: Boolean = true) {
    val primary = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = primary.copy(alpha = 0.65f)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(ScreenTimeColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = ScreenTimeColor, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.screentime_today), style = MaterialTheme.typography.labelMedium, color = secondary)
                Text(
                    if (report != null) formatScreenDuration(report.today.totalMs) else "—",
                    style = MaterialTheme.typography.titleLarge.merge(CompactDigits),
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    maxLines = 1,
                )
            }
            if (report != null) {
                Text(
                    pluralStringResource(R.plurals.screentime_unlocks, report.today.unlocks, report.today.unlocks),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                    maxLines = 1,
                )
            }
        }
        if (!hasAccess) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.screentime_no_access), style = MaterialTheme.typography.bodyMedium, color = secondary)
            return@Column
        }
        if (report == null) return@Column
        report.yesterdaySameTime?.let { y ->
            Spacer(Modifier.height(4.dp))
            val diff = report.today.totalMs - y.totalMs
            val text = when {
                abs(diff) < 60_000L -> stringResource(R.string.screentime_vs_yesterday_same)
                diff > 0 -> stringResource(R.string.screentime_vs_yesterday_more, formatScreenDuration(diff))
                else -> stringResource(R.string.screentime_vs_yesterday_less, formatScreenDuration(-diff))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val top = report.today.top(5)
        if (top.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val max = top.first().second.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { (pkg, ms) -> AppBar(pkg, ms, ms.toFloat() / max, primary, secondary) }
            }
        }
    }
}

@Composable
private fun AppBar(pkg: String, ms: Long, fraction: Float, primary: Color, secondary: Color) {
    val info = rememberAppInfo(pkg)
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(pkg, 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.label, style = MaterialTheme.typography.bodyMedium, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatScreenDuration(ms), style = MaterialTheme.typography.labelMedium.merge(CompactDigits), color = secondary, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            val track = primary.copy(alpha = 0.12f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                drawRoundRect(ScreenTimeColor, size = Size(size.width * fraction.coerceIn(0.02f, 1f), size.height), cornerRadius = r)
            }
        }
    }
}
