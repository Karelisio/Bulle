package com.karelisio.bulle.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.ui.components.switchRow
import kotlinx.coroutines.launch

private fun Usage.titleRes() = when (this) {
    Usage.MUSIC -> R.string.usage_music
    Usage.NOTIFICATIONS -> R.string.usage_notifications
    Usage.BEDSIDE -> R.string.usage_bedside
    Usage.GESTURES -> R.string.usage_gestures
}

private fun Usage.descRes() = when (this) {
    Usage.MUSIC -> R.string.usage_music_desc
    Usage.NOTIFICATIONS -> R.string.usage_notifications_desc
    Usage.BEDSIDE -> R.string.usage_bedside_desc
    Usage.GESTURES -> R.string.usage_gestures_desc
}

/** Assistant « Je veux… » : coche des usages, puis tout est activé et réglé d'un coup. */
@Composable
fun UsageSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(emptySet<Usage>()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(R.string.usage_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Usage.entries.forEach { u ->
                val on = u in chosen
                ListItem(
                    headlineContent = { Text(stringResource(u.titleRes())) },
                    supportingContent = { Text(stringResource(u.descRes())) },
                    leadingContent = { Checkbox(checked = on, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.switchRow(on) { chosen = if (it) chosen + u else chosen - u },
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.halo_close)) }
                Button(enabled = chosen.isNotEmpty(), onClick = {
                    scope.launch {
                        SettingsRepository.update(context) { UsageSetup.apply(it, chosen) }
                        IslandLog.log("Assistant : ${chosen.joinToString { it.name.lowercase() }}")
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.usage_apply)) }
            }
        }
    }
}
