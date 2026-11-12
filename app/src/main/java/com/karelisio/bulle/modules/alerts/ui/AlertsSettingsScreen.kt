package com.karelisio.bulle.modules.alerts.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.alerts.AlertCatalog
import com.karelisio.bulle.modules.alerts.AlertCategory
import com.karelisio.bulle.modules.alerts.AlertDef
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.CategoryCount
import com.karelisio.bulle.modules.alerts.hasPermission
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch

/** Seuils proposés pour la batterie faible. */
private val thresholdChoices = listOf(5, 10, 15, 20, 30)

/**
 * Écran des réglages des alertes système et d'Écho, rangées par catégories repliables.
 * Toucher une alerte ouvre la feuille de personnalisation (icône, couleur, animation, durée).
 */
@Composable
fun AlertsSettingsScreen(onBack: () -> Unit, initiallyExpanded: Set<AlertCategory> = emptySet()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val settings by SettingsRepository.flow(context).collectAsStateWithLifecycle(initialValue = BulleSettings())
    val alerts = settings.alerts

    fun update(transform: (AlertSettings) -> AlertSettings) {
        scope.launch { SettingsRepository.update(context) { it.copy(alerts = transform(it.alerts)) } }
    }

    // Les permissions et l'accès aux notifications peuvent changer hors de l'appli : relus au retour.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val notifAccess = remember(refresh) { NotificationRepository.isAccessGranted(context) }
    val granted: (String) -> Boolean = { context.hasPermission(it) }

    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded.map { it.name }.toSet()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val def = pending?.let(AlertCatalog::def) ?: return@rememberLauncherForActivityResult
        pending = null
        val ok = context.hasPermission(def.permissions.first())
        when {
            ok -> { denied = null; update { def.set(it, true) } }
            def.permissionOptional -> { denied = def.id; update { def.set(it, true) } }
            else -> { denied = def.id; update { def.set(it, false) } }
        }
        refresh++
    }

    // La permission n'est demandée qu'au moment où l'utilisateur active l'alerte.
    fun toggle(def: AlertDef, on: Boolean) {
        if (!on) {
            if (denied == def.id) denied = null
            update { def.set(it, false) }
            return
        }
        val missing = def.permissions.filterNot { context.hasPermission(it) }
        if (missing.isEmpty()) {
            update { def.set(it, true) }
        } else {
            pending = def.id
            launcher.launch(def.permissions.toTypedArray())
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.alerts_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.alerts_back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "master") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.alerts_master), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.alerts_master_desc)) },
                        trailingContent = {
                            Switch(checked = alerts.enabled, onCheckedChange = { v -> update { it.copy(enabled = v) } })
                        },
                    )
                }
            }
            if (!notifAccess) {
                item(key = "notif") {
                    NoticeCard(stringResource(R.string.alerts_notif_access_needed), stringResource(R.string.alerts_grant)) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
            AlertCategory.entries.forEach { category ->
                item(key = "cat:${category.name}") {
                    val count = remember(alerts, refresh, notifAccess) {
                        AlertCatalog.count(category, alerts, granted, notifAccess)
                    }
                    val open = category.name in expanded
                    CategoryCard(
                        category = category,
                        count = count,
                        expanded = open,
                        onToggle = {
                            haptics.performHapticFeedback(if (open) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            expanded = if (open) expanded - category.name else expanded + category.name
                        },
                    ) {
                        AlertCatalog.defsOf(category).forEach { def ->
                            val ui = AlertUis.of(def.id)
                            val checked = def.get(alerts)
                            val permMissing = remember(refresh, checked) {
                                checked && def.permissions.isNotEmpty() && !context.hasPermission(def.permissions.first())
                            }
                            AlertRow(
                                ui = ui,
                                checked = checked,
                                enabled = alerts.enabled,
                                customized = !alerts.styleOf(def.id).isDefault,
                                pushable = ui.pushable && !def.persistent,
                                note = when {
                                    denied == def.id -> stringResource(ui.deniedRes)
                                    permMissing -> stringResource(R.string.alerts_perm_missing)
                                    checked && def.needsNotificationAccess && !notifAccess -> stringResource(R.string.alerts_needs_notif_access)
                                    else -> null
                                },
                                onNoteClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.takeIf { denied == def.id || permMissing },
                                onToggle = { toggle(def, it) },
                                onEdit = { editing = def.id },
                                onPreview = { PushModule.push(styled(sampleOf(ui, context), alerts.styles)) },
                            ) {
                                if (def.id == "lowBattery" && checked) {
                                    ThresholdChips(alerts.lowBatteryLevels) { levels -> update { it.copy(lowBatteryLevels = levels) } }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "privacy") {
                Column(Modifier.padding(8.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.alerts_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.alerts_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { id ->
        AlertStyleSheet(
            ui = AlertUis.of(id),
            def = AlertCatalog.def(id),
            style = alerts.styleOf(id),
            onChange = { s -> update { it.withStyle(id, s) } },
            onDismiss = { editing = null },
        )
    }
}

/** Carte d'une catégorie : en-tête avec compteurs et chevron, liste dépliée au ressort. */
@Composable
private fun CategoryCard(
    category: AlertCategory,
    count: CategoryCount,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val ui = category.ui()
    val reduce = rememberReduceMotion()
    val turn by animateFloatAsState(
        if (expanded) 180f else 0f,
        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chevron",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.alerts_cat_expand), onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(ui.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(ui.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    pluralStringResource(R.plurals.alerts_cat_count, count.enabled, count.enabled, count.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (count.missing > 0) {
                    Text(
                        pluralStringResource(R.plurals.alerts_cat_missing, count.missing, count.missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Icon(Icons.Rounded.ExpandMore, null, Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduce) fadeIn(snap()) else expandVertically(
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = if (reduce) fadeOut(snap()) else shrinkVertically(
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            ) + fadeOut(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { content() }
        }
    }
}

@Composable
private fun AlertRow(
    ui: AlertUi,
    checked: Boolean,
    enabled: Boolean,
    customized: Boolean,
    pushable: Boolean,
    note: String?,
    onNoteClick: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    below: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.alerts_customize), onClick = onEdit),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(ui.tint.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(ui.icon, null, tint = MaterialTheme.colorScheme.onSurface) }
            },
            headlineContent = { Text(stringResource(ui.titleRes)) },
            supportingContent = {
                Column {
                    Text(stringResource(ui.descRes))
                    if (customized) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Rounded.Palette, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.alerts_style_customized),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pushable && checked && enabled) {
                        IconButton(onClick = onPreview) {
                            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.alerts_preview))
                        }
                    }
                    Switch(checked = checked, enabled = enabled, onCheckedChange = onToggle)
                }
            },
        )
        below()
        AnimatedVisibility(note != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    note.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (onNoteClick != null) {
                    TextButton(onClick = onNoteClick) { Text(stringResource(R.string.alerts_open_settings)) }
                }
            }
        }
    }
}

@Composable
private fun ThresholdChips(levels: List<Int>, onChange: (List<Int>) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Text(stringResource(R.string.alerts_set_thresholds), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            thresholdChoices.forEach { t ->
                val on = t in levels
                FilterChip(
                    selected = on,
                    onClick = { onChange(if (on) levels - t else (levels + t).sortedDescending()) },
                    label = { Text(stringResource(R.string.alerts_percent, t)) },
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}
