package com.karelisio.bulle.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Info
import com.karelisio.bulle.ui.components.cascade
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.VisibilityOff
import com.karelisio.bulle.ui.settings.IslandPreview
import com.karelisio.bulle.ui.components.sharedElement
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.modules.push.DemoContent
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.system.Reliability
import com.karelisio.bulle.ui.components.MorphShape
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpen: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val running by IslandAccessibilityService.running.collectAsStateWithLifecycle()
    var enabledInSystem by remember { mutableStateOf(Reliability.isAccessibilityEnabled(context)) }
    var notifGranted by remember { mutableStateOf(NotificationRepository.isAccessGranted(context)) }
    LifecycleResumeEffect(Unit) {
        enabledInSystem = Reliability.isAccessibilityEnabled(context)
        notifGranted = NotificationRepository.isAccessGranted(context)
        onPauseOrDispose { }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            IslandPreview(settings.style, settings.motion, Modifier.sharedElement("island_preview").clickable { onOpen(com.karelisio.bulle.ui.Routes.STYLE) })
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            ServiceCard(
                running = running,
                enabledInSystem = enabledInSystem,
                onOpenAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
            Spacer(Modifier.height(12.dp))
            StatusCard(
                ok = notifGranted,
                title = stringResource(R.string.home_notif_title),
                text = stringResource(if (notifGranted) R.string.home_notif_on else R.string.home_notif_off),
                action = stringResource(R.string.home_open_notif_access),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotificationRepository.component(context).flattenToString())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_island_switch),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.islandEnabled,
                        onCheckedChange = { on ->
                            scope.launch { SettingsRepository.update(context) { it.copy(islandEnabled = on) } }
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(onOpen)
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                enabled = running && settings.islandEnabled,
                onClick = { PushModule.push(DemoContent()) },
            ) { Text(stringResource(R.string.home_test_island)) }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ServiceCard(running: Boolean, enabledInSystem: Boolean, onOpenAccessibility: () -> Unit) {
    val reason by com.karelisio.bulle.island.islandHiddenReason.collectAsStateWithLifecycle()
    val base = stringResource(
        when {
            running -> R.string.home_service_running
            enabledInSystem -> R.string.home_service_enabled_not_running
            else -> R.string.home_service_off
        },
    )
    // Diagnostic : pourquoi l'île est masquée en ce moment.
    val why = if (running) reason?.let { stringResource(R.string.home_hidden_reason, stringResource(it.labelRes())) } else null
    StatusCard(
        ok = running,
        title = stringResource(R.string.home_service_title),
        text = if (why != null) "$base\n$why" else base,
        action = stringResource(R.string.home_open_accessibility),
        onAction = onOpenAccessibility,
    )
}

@Composable
private fun StatusCard(ok: Boolean, title: String, text: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(ok, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "icon") { good ->
                    Icon(
                        if (good) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (!ok) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** Entrées des écrans de réglages, groupées. */
private data class SettingsEntry(val route: String, val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val settingsGroups: List<Pair<Int, List<SettingsEntry>>> = listOf(
    R.string.home_group_island to listOf(
        SettingsEntry(com.karelisio.bulle.ui.Routes.STYLE, R.string.home_entry_style, Icons.Rounded.Palette),
        SettingsEntry(com.karelisio.bulle.ui.Routes.POSITION, R.string.home_entry_position, Icons.Rounded.OpenWith),
        SettingsEntry(com.karelisio.bulle.ui.Routes.VISIBILITY, R.string.home_entry_visibility, Icons.Rounded.VisibilityOff),
        SettingsEntry(com.karelisio.bulle.ui.Routes.GESTURES, R.string.gestures_title, Icons.Rounded.TouchApp),
        SettingsEntry(com.karelisio.bulle.ui.Routes.DECK, R.string.deck_title, Icons.Rounded.Dashboard),
        SettingsEntry(com.karelisio.bulle.ui.Routes.RULES, R.string.rules_title, Icons.Rounded.AutoAwesome),
    ),
    R.string.home_group_contents to listOf(
        SettingsEntry(com.karelisio.bulle.ui.Routes.MUSIC, R.string.home_entry_music, Icons.Rounded.MusicNote),
        SettingsEntry(com.karelisio.bulle.ui.Routes.LIVE, R.string.home_entry_live, Icons.Rounded.Timer),
        SettingsEntry(com.karelisio.bulle.ui.Routes.CALLS, R.string.home_entry_calls, Icons.Rounded.Call),
        SettingsEntry(com.karelisio.bulle.ui.Routes.ALERTS, R.string.home_entry_alerts, Icons.Rounded.NotificationsActive),
    ),
    R.string.home_group_app to listOf(
        SettingsEntry(com.karelisio.bulle.ui.Routes.RELIABILITY, R.string.home_entry_reliability, Icons.Rounded.HealthAndSafety),
        SettingsEntry(com.karelisio.bulle.ui.Routes.PERMISSIONS, R.string.home_entry_permissions, Icons.Rounded.VerifiedUser),
        SettingsEntry(com.karelisio.bulle.ui.Routes.BACKUP, R.string.home_entry_backup, Icons.Rounded.SettingsBackupRestore),
        SettingsEntry(com.karelisio.bulle.ui.Routes.ABOUT, R.string.home_entry_about, Icons.Rounded.Info),
    ),
)

@Composable
private fun SettingsSection(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        settingsGroups.forEachIndexed { g, (titleRes, entries) ->
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = if (g == 0) 0.dp else 16.dp),
            )
            Card(Modifier.fillMaxWidth().cascade(g), shape = MaterialTheme.shapes.extraLarge) {
                entries.forEach { entry ->
                    ListItem(
                        headlineContent = { Text(stringResource(entry.titleRes)) },
                        leadingContent = { Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { onOpen(entry.route) },
                    )
                }
            }
        }
    }
}

private fun com.karelisio.bulle.island.HiddenReason.labelRes(): Int = when (this) {
    com.karelisio.bulle.island.HiddenReason.DISABLED -> R.string.hidden_disabled
    com.karelisio.bulle.island.HiddenReason.RULE -> R.string.hidden_rule
    com.karelisio.bulle.island.HiddenReason.FULLSCREEN -> R.string.hidden_fullscreen
    com.karelisio.bulle.island.HiddenReason.LANDSCAPE -> R.string.hidden_landscape
    com.karelisio.bulle.island.HiddenReason.LOCKSCREEN -> R.string.hidden_lockscreen
    com.karelisio.bulle.island.HiddenReason.HIDDEN_APP -> R.string.hidden_app
}
