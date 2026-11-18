package com.karelisio.bulle.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.edges.EdgeBus
import com.karelisio.bulle.modules.halo.HaloBus
import com.karelisio.bulle.modules.profiles.Profiles
import com.karelisio.bulle.system.ModuleSwitch
import com.karelisio.bulle.ui.Routes
import kotlinx.coroutines.launch

/** Une tuile du tableau de bord (logique pure : état affiché). */
enum class DashState { ON, OFF, STOPPED }

/** État d'un module : coupé, actif, ou activé mais arrêté (service de l'île absent…). */
fun dashState(enabled: Boolean, running: Boolean?): DashState = when {
    !enabled -> DashState.OFF
    running == false -> DashState.STOPPED
    else -> DashState.ON
}

/**
 * Tableau de bord de l'accueil : les modules principaux en tuiles (état, interrupteur, toucher
 * = réglages) et les profils en un toucher. Les réglages détaillés restent dans les onglets.
 */
@Composable
fun Dashboard(settings: BulleSettings, serviceRunning: Boolean, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haloRunning by HaloBus.running.collectAsStateWithLifecycle()
    val edgesRunning by EdgeBus.running.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.dash_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            DashTile(
                Icons.Rounded.BlurOn, stringResource(R.string.halo_title),
                dashState(settings.halo.enabled, if (serviceRunning) haloRunning else null),
                onToggle = { ModuleSwitch.HALO.toggle(context, it) }, onOpen = { onOpen(Routes.HALO) }, modifier = Modifier.weight(1f),
            )
            DashTile(
                Icons.Rounded.VerticalSplit, stringResource(R.string.edges_title),
                dashState(settings.edges.enabled, if (serviceRunning) edgesRunning else null),
                onToggle = { ModuleSwitch.EDGES.toggle(context, it) }, onOpen = { onOpen(Routes.EDGES) }, modifier = Modifier.weight(1f),
            )
            DashTile(
                Icons.Rounded.Nightlight, stringResource(R.string.standby_title),
                dashState(settings.standby.enabled, null),
                onToggle = { ModuleSwitch.STANDBY.toggle(context, it) }, onOpen = { onOpen(Routes.STANDBY) }, modifier = Modifier.weight(1f),
            )
            DashTile(
                Icons.Rounded.Notifications, stringResource(R.string.home_entry_volet),
                dashState(settings.volet.enabled, if (serviceRunning) null else false),
                onToggle = { on -> scope.launch { SettingsRepository.update(context) { it.copy(volet = it.volet.copy(enabled = on)) } } },
                onOpen = { onOpen(Routes.VOLET) }, modifier = Modifier.weight(1f),
            )
        }
        if (settings.profiles.list.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.profiles_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.profiles.list.forEach { p ->
                    FilterChip(
                        selected = settings.profiles.activeId == p.id,
                        onClick = { Profiles.apply(context, p.id) },
                        label = { Text("${p.emoji} ${p.name}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashTile(
    icon: ImageVector,
    title: String,
    state: DashState,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = when (state) {
        DashState.ON -> scheme.primaryContainer to scheme.onPrimaryContainer
        DashState.OFF -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        DashState.STOPPED -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = container, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(onContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.weight(1f))
                Switch(checked = state != DashState.OFF, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    when (state) {
                        DashState.ON -> R.string.dash_on
                        DashState.OFF -> R.string.dash_off
                        DashState.STOPPED -> R.string.dash_stopped
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.8f),
            )
        }
    }
}
