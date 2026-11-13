package com.karelisio.bulle.modules.live.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.karelisio.bulle.ui.components.switchRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.live.LiveModule
import com.karelisio.bulle.modules.live.LiveParser
import com.karelisio.bulle.modules.live.LiveSettings
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.launch

/** Réglages du module « Activités en cours ». */
@Composable
fun LiveSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val active by NotificationRepository.active.collectAsStateWithLifecycle()
    val live = settings.live
    val enabled = LiveModule.isEnabled(settings)

    fun update(transform: (LiveSettings) -> LiveSettings) {
        scope.launch { SettingsRepository.update(context) { it.copy(live = transform(it.live)) } }
    }

    // Applis qui ont une activité en cours en ce moment (pour pouvoir les exclure).
    val livePackages = remember(active) {
        active.values.filter { LiveParser.parse(it) != null }.map { it.packageName }.distinct()
    }
    val candidates = livePackages.filter { it !in live.excludedPackages && it != context.packageName }
    val labels = remember(livePackages, live.excludedPackages) {
        (livePackages + live.excludedPackages).associateWith { LiveModule.appLabel(context, it) ?: it }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.live_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.live_settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.live_settings_enable),
                        summary = stringResource(R.string.live_settings_enable_summary),
                        checked = enabled,
                    ) { on ->
                        scope.launch {
                            SettingsRepository.update(context) {
                                it.copy(modules = it.modules + (LiveModule.ID to on), live = it.live.copy(enabled = on))
                            }
                        }
                    }
                    SwitchRow(
                        title = stringResource(R.string.live_settings_generic),
                        summary = stringResource(R.string.live_settings_generic_summary),
                        checked = live.showGenericProgress,
                        enabled = enabled,
                    ) { on -> update { it.copy(showGenericProgress = on) } }
                }
            }
            item { SectionHeader(stringResource(R.string.live_settings_now_header)) }
            item {
                SettingsCard {
                    if (candidates.isEmpty()) {
                        EmptyRow(stringResource(R.string.live_settings_now_empty))
                    }
                    candidates.forEach { pkg ->
                        AppRow(labels[pkg] ?: pkg, pkg, stringResource(R.string.live_settings_exclude)) {
                            update { it.copy(excludedPackages = it.excludedPackages + pkg) }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.live_settings_excluded_header)) }
            item {
                SettingsCard {
                    if (live.excludedPackages.isEmpty()) {
                        EmptyRow(stringResource(R.string.live_settings_excluded_empty))
                    }
                }
            }
            items(live.excludedPackages.sorted(), key = { it }) { pkg ->
                SettingsCard(Modifier.animateItem()) {
                    AppRow(labels[pkg] ?: pkg, pkg, stringResource(R.string.live_settings_include)) {
                        update { it.copy(excludedPackages = it.excludedPackages - pkg) }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.live_settings_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) { content() }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // Une seule cible TalkBack : rôle Switch et état.
        modifier = Modifier.switchRow(checked, enabled, onChange),
    )
}

@Composable
private fun AppRow(label: String, pkg: String, actionLabel: String, onAction: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (label != pkg) ({ Text(pkg) }) else null,
        trailingContent = { TextButton(onClick = onAction) { Text(actionLabel) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun EmptyRow(text: String) {
    ListItem(
        headlineContent = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
