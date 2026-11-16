package com.karelisio.bulle.modules.volet.qs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletHost
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mini-panneau de la tuile « Point d'accès » (appui long, avec Shizuku) : nom du réseau,
 * appareils connectés, accès aux réglages. Affiché sous la grille de tuiles (pas de boîte de
 * dialogue : le Volet est une fenêtre d'overlay).
 */
object HotspotPanel {
    val info = MutableStateFlow<TetherShell.Info?>(null)

    fun dismiss() {
        info.value = null
    }
}

@Composable
internal fun HotspotPanelCard(host: VoletHost) {
    val info by HotspotPanel.info.collectAsState()
    val i = info ?: return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(i.ssid ?: stringResource(R.string.volet_qs_hotspot), style = MaterialTheme.typography.titleSmall)
                    val status = when {
                        i.active == false -> stringResource(R.string.hotspot_off)
                        i.clients != null -> pluralStringResource(R.plurals.hotspot_clients, i.clients, i.clients)
                        else -> stringResource(R.string.hotspot_on)
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = HotspotPanel::dismiss) { Text(stringResource(R.string.hotspot_close)) }
                TextButton(onClick = {
                    HotspotPanel.dismiss()
                    host.startActivity(HotspotIntents.resolve(host.context))
                }) { Text(stringResource(R.string.hotspot_settings)) }
            }
        }
    }
}
