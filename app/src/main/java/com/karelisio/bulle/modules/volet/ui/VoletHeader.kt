package com.karelisio.bulle.modules.volet.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletHost
import kotlinx.coroutines.delay
import java.util.Date

/**
 * En-tête du volet : heure, date, batterie, édition des tuiles, réglages.
 * [compact] (paysage) : heure plus petite, sur une seule ligne avec la date et la batterie.
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

    if (compact) {
        CompactHeader(host, now, battery)
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val time = DateFormat.getTimeFormat(context).format(Date(now))
            AnimatedContent(time, transitionSpec = {
                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
            }, label = "time") {
                Text(it, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateFormat.format("EEEE d MMMM", now).toString().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (battery.first >= 0) {
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        if (battery.second) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("${battery.first} %", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FilledTonalIconButton(onClick = { host.editingTiles = !host.editingTiles }) {
            Icon(
                if (host.editingTiles) Icons.Rounded.Check else Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.volet_edit_tiles),
            )
        }
        FilledTonalIconButton(onClick = { host.startActivity(Intent(Settings.ACTION_SETTINGS)) }) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.volet_open_settings))
        }
    }
}

/** En-tête paysage : une ligne (heure, date, batterie) et des boutons plus petits. */
@Composable
private fun CompactHeader(host: VoletHost, now: Long, battery: Pair<Int, Boolean>) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val time = DateFormat.getTimeFormat(context).format(Date(now))
            AnimatedContent(time, transitionSpec = {
                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
            }, label = "time") {
                Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                DateFormat.format("EEE d MMM", now).toString().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (battery.first >= 0) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (battery.second) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text("${battery.first} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
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
