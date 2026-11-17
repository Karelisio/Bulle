package com.karelisio.bulle.modules.volet.ui

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery0Bar
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.background.BackgroundAppsChip
import com.karelisio.bulle.modules.volet.background.BackgroundAppsPanel
import com.karelisio.bulle.modules.volet.background.rememberBackgroundApps
import kotlinx.coroutines.delay
import java.util.Date

/** Icône de batterie qui suit le niveau réel (logique pure). */
internal fun batteryIcon(level: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Rounded.BatteryChargingFull
    level >= 90 -> Icons.Rounded.BatteryFull
    level >= 60 -> Icons.Rounded.Battery6Bar
    level >= 35 -> Icons.Rounded.Battery4Bar
    level >= 12 -> Icons.Rounded.Battery2Bar
    else -> Icons.Rounded.Battery0Bar
}

/**
 * Taille de l'heure : réduite avec une grande police système pour que la ligne ne déborde
 * jamais (logique pure : [fontScale] → facteur appliqué à la taille du style).
 */
internal fun timeScale(fontScale: Float): Float = (1.15f / fontScale.coerceAtLeast(1f)).coerceIn(0.7f, 1f)

/**
 * En-tête du volet : heure (appui long : réveils) et boutons sur une ligne, puis date (appui :
 * agenda) et batterie (appui : utilisation de la batterie) sur toute la largeur — rien n'est
 * jamais coupé. [compact] (paysage) : tout sur une ligne, plus petit.
 */
@Composable
fun VoletHeader(host: VoletHost, compact: Boolean = false) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 20)
        }
    }
    var battery by remember { mutableStateOf(-1 to false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                battery = (level * 100 / scale) to (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val bgApps = rememberBackgroundApps()
    var bgOpen by remember { mutableStateOf(false) }
    // Point sur la pastille quand une appli démarre en fond depuis la dernière ouverture de la liste.
    var seen by remember { mutableStateOf(bgApps.map { it.packageName }.toSet()) }
    val fresh = bgApps.any { it.packageName !in seen }
    val toggleBg = {
        bgOpen = !bgOpen
        seen = bgApps.map { it.packageName }.toSet()
    }

    if (compact) {
        CompactHeader(host, now, battery, bgApps.size, bgOpen, fresh, toggleBg)
        BackgroundAppsPanel(host, bgApps, bgOpen)
        return
    }
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val time = DateFormat.getTimeFormat(context).format(Date(now))
            val style = MaterialTheme.typography.displaySmall
            AnimatedContent(
                time,
                transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) },
                label = "time",
                modifier = Modifier.weight(1f).clickableTime(host),
            ) {
                Text(
                    it,
                    style = style.copy(fontSize = style.fontSize * timeScale(fontScale)),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            BackgroundAppsChip(bgApps.size, bgOpen, toggleBg, Modifier.padding(end = 4.dp), dot = fresh)
            FilledTonalIconButton(onClick = { host.editingTiles = !host.editingTiles }) {
                Icon(if (host.editingTiles) Icons.Rounded.Check else Icons.Rounded.Edit, contentDescription = stringResource(R.string.volet_edit_tiles))
            }
            FilledTonalIconButton(onClick = { host.startActivity(Intent(Settings.ACTION_SETTINGS)) }) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.volet_open_settings))
            }
        }
        // Date et batterie : toute la largeur, une seule ligne.
        DateBatteryRow(host, now, battery, MaterialTheme.typography.titleSmall, iconSize = 22)
        BackgroundAppsPanel(host, bgApps, bgOpen)
    }
}

/** Heure : appui long → réveils. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableTime(host: VoletHost): Modifier = combinedClickable(
    onClick = {},
    onLongClick = { host.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
)

@Composable
private fun DateBatteryRow(host: VoletHost, now: Long, battery: Pair<Int, Boolean>, style: TextStyle, iconSize: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            DateFormat.format("EEEE d MMMM", now).toString().replaceFirstChar { it.uppercase() },
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable {
                    // Agenda du jour.
                    val uri = ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), now).build()
                    host.startActivity(Intent(Intent.ACTION_VIEW).setData(uri))
                },
        )
        if (battery.first >= 0) {
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.clickable { host.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(batteryIcon(battery.first, battery.second), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize.dp))
                Text("${battery.first} %", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** En-tête paysage : une ligne (heure, date, batterie) et des boutons plus petits. */
@Composable
private fun CompactHeader(
    host: VoletHost,
    now: Long,
    battery: Pair<Int, Boolean>,
    bgCount: Int,
    bgOpen: Boolean,
    fresh: Boolean,
    onBg: () -> Unit,
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val time = DateFormat.getTimeFormat(context).format(Date(now))
            AnimatedContent(time, transitionSpec = {
                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
            }, label = "time", modifier = Modifier.clickableTime(host)) {
                Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(10.dp))
            DateBatteryRow(host, now, battery, MaterialTheme.typography.bodyMedium, iconSize = 18, modifier = Modifier.weight(1f, fill = false))
        }
        BackgroundAppsChip(bgCount, bgOpen, onBg, Modifier.padding(end = 4.dp), compact = true, dot = fresh)
        FilledTonalIconButton(onClick = { host.editingTiles = !host.editingTiles }, modifier = Modifier.size(36.dp)) {
            Icon(
                if (host.editingTiles) Icons.Rounded.Check else Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.volet_edit_tiles),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(onClick = { host.startActivity(Intent(Settings.ACTION_SETTINGS)) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.volet_open_settings), modifier = Modifier.size(18.dp))
        }
    }
}
