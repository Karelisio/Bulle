package com.karelisio.bulle.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.system.Reliability
import com.karelisio.bulle.ui.components.cascade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Diagnostic de fiabilité : détecte un service d'accessibilité coupé par le système,
 * propose de le réactiver, montre l'état des garde-fous et la commande adb.
 */
@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val permissions = rememberPermissionController()
    val running by IslandAccessibilityService.running.collectAsStateWithLifecycle()
    val notifConnected by NotificationRepository.connected.collectAsStateWithLifecycle()
    // Relu au retour des réglages et après une action (le compteur force la relecture).
    var tick by remember { mutableIntStateOf(0) }
    var keepAlive by remember { mutableStateOf(PermissionChecks.isKeepAliveRunning(context)) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    LaunchedEffect(tick) {
        permissions.refresh()
        keepAlive = PermissionChecks.isKeepAliveRunning(context)
    }
    val enabledInSystem = permissions[PermissionKey.ACCESSIBILITY]
    val canSecure = permissions[PermissionKey.SECURE_SETTINGS]
    val health = PermissionLogic.diagnose(enabledInSystem, running)
    val family = remember { OemSettings.family }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val copied = stringResource(R.string.rel_copied)
    val reenabled = stringResource(R.string.rel_reenabled)
    val refused = stringResource(R.string.rel_reenable_failed)

    fun reenable() {
        val ok = Reliability.reenableAccessibility(context, forceRebind = true)
        haptic.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        scope.launch {
            snackbar.showSnackbar(if (ok) reenabled else refused)
        }
        scope.launch {
            // Laisse au système le temps de relier le service avant de relire l'état.
            delay(1500)
            tick++
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.reliability_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("hero") {
                HealthCard(
                    health = health,
                    canSecure = canSecure,
                    onOpenAccessibility = { PermissionChecks.openSettings(context, PermissionKey.ACCESSIBILITY) },
                    onReenable = ::reenable,
                    modifier = Modifier.cascade(0),
                )
            }
            item("status") {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().cascade(1),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.rel_status_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        StatusLine(
                            Icons.Rounded.Accessibility,
                            stringResource(R.string.rel_accessibility),
                            when (health) {
                                ServiceHealth.RUNNING -> PillState.GRANTED
                                ServiceHealth.KILLED -> PillState.PENDING
                                ServiceHealth.DISABLED -> PillState.DENIED
                            },
                            stringResource(
                                when (health) {
                                    ServiceHealth.RUNNING -> R.string.rel_state_active
                                    ServiceHealth.KILLED -> R.string.rel_state_killed
                                    ServiceHealth.DISABLED -> R.string.rel_state_off
                                },
                            ),
                        )
                        val notifGranted = permissions[PermissionKey.NOTIFICATION_ACCESS]
                        StatusLine(
                            Icons.Rounded.NotificationsActive,
                            stringResource(R.string.rel_notifications),
                            when {
                                notifGranted && notifConnected -> PillState.GRANTED
                                notifGranted -> PillState.PENDING
                                else -> PillState.DENIED
                            },
                            when {
                                notifGranted && notifConnected -> stringResource(R.string.rel_notif_connected)
                                notifGranted -> stringResource(R.string.rel_notif_waiting)
                                else -> null
                            },
                            action = if (notifGranted) null else ({ permissions.request(PermissionKey.NOTIFICATION_ACCESS) }),
                        )
                        val exempt = permissions[PermissionKey.BATTERY]
                        StatusLine(
                            Icons.Rounded.BatteryChargingFull,
                            stringResource(R.string.rel_battery),
                            if (exempt) PillState.GRANTED else PillState.DENIED,
                            stringResource(if (exempt) R.string.rel_battery_exempt else R.string.rel_battery_active),
                            action = if (exempt) null else ({ permissions.request(PermissionKey.BATTERY) }),
                        )
                        StatusLine(
                            Icons.Rounded.Favorite,
                            stringResource(R.string.rel_keepalive),
                            if (keepAlive) PillState.GRANTED else PillState.DENIED,
                            stringResource(if (keepAlive) R.string.rel_state_active else R.string.rel_keepalive_stopped),
                            action = if (keepAlive) null else ({
                                Reliability.ensureRunning(context)
                                scope.launch {
                                    delay(800)
                                    tick++
                                }
                            }),
                            actionLabel = stringResource(R.string.rel_restart),
                        )
                    }
                }
            }
            item("adb") {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().cascade(2),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PermissionGlyph(Icons.Rounded.Terminal, canSecure, size = 40.dp, animated = false)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.rel_adb_section),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(if (canSecure) PillState.GRANTED else PillState.OPTIONAL)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.rel_adb_text), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        AdbCommandBlock(onCopy = { copyAdbCommand(context, snackbar, scope, copied) })
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.rel_adb_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (family != OemSettings.Family.STOCK) {
                item("oem") {
                    OemCard(family, Modifier.cascade(3))
                }
            }
            item("journal") {
                IslandJournalCard(snackbar, Modifier.cascade(4))
            }
        }
    }
}

/**
 * Journal de l'île (en mémoire) : apparitions, masquages et leur raison, mises à jour de la fenêtre.
 * À copier et envoyer pour diagnostiquer un scintillement.
 */
@Composable
private fun IslandJournalCard(snackbar: SnackbarHostState, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by com.karelisio.bulle.island.IslandLog.entries.collectAsStateWithLifecycle()
    val copied = stringResource(R.string.journal_copied)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.journal_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            val last = entries.takeLast(12).asReversed()
            if (last.isEmpty()) {
                Text(stringResource(R.string.journal_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                last.forEach { e ->
                    Text(
                        e.format(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                androidx.compose.material3.FilledTonalButton(onClick = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Journal Bulle", com.karelisio.bulle.island.IslandLog.dump()))
                    scope.launch { snackbar.showSnackbar(copied) }
                }) { Text(stringResource(R.string.journal_copy)) }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.TextButton(onClick = { com.karelisio.bulle.island.IslandLog.clear() }) {
                    Text(stringResource(R.string.journal_clear))
                }
            }
        }
    }
}

/** Grande carte d'état du service, avec les actions de réparation. */
@Composable
private fun HealthCard(
    health: ServiceHealth,
    canSecure: Boolean,
    onOpenAccessibility: () -> Unit,
    onReenable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ok = health == ServiceHealth.RUNNING
    val container by animateColorAsState(
        if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "health",
    )
    val content by animateColorAsState(
        if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        label = "healthFg",
    )
    ConfirmHaptic(ok)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PermissionGlyph(Icons.Rounded.Accessibility, ok, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                AnimatedContent(health, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "healthTitle") { h ->
                    Text(
                        stringResource(
                            when (h) {
                                ServiceHealth.RUNNING -> R.string.rel_running_title
                                ServiceHealth.KILLED -> R.string.rel_killed_title
                                ServiceHealth.DISABLED -> R.string.rel_disabled_title
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                AnimatedCheck(ok, size = 32.dp, color = content)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    when (health) {
                        ServiceHealth.RUNNING -> R.string.rel_running_text
                        ServiceHealth.KILLED -> R.string.rel_killed_text
                        ServiceHealth.DISABLED -> R.string.rel_disabled_text
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            AnimatedVisibility(visible = !ok, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                FlowRow(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canSecure) {
                        Button(onClick = onReenable) { Text(stringResource(R.string.rel_reenable_now)) }
                        OutlinedButton(onClick = onOpenAccessibility) { Text(stringResource(R.string.rel_open_accessibility)) }
                    } else {
                        Button(onClick = onOpenAccessibility) { Text(stringResource(R.string.rel_open_accessibility)) }
                    }
                }
            }
            if (ok && canSecure) {
                TextButton(onClick = onReenable, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.rel_force_rebind))
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    icon: ImageVector,
    title: String,
    state: PillState,
    label: String?,
    action: (() -> Unit)? = null,
    actionLabel: String = stringResource(R.string.perm_open),
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = action) { Text(actionLabel) }
        }
        StatusPill(state, label = label)
    }
}

/** Rappel « Lancement d'applis » (MagicOS) ou réglages du fabricant. */
@Composable
internal fun OemCard(family: OemSettings.Family, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val honor = family == OemSettings.Family.HONOR
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (honor) R.string.rel_oem_section else R.string.perm_oem_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (honor) R.string.perm_oem_body_honor else R.string.perm_oem_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (honor) {
                Spacer(Modifier.height(12.dp))
                StepList(
                    listOf(
                        stringResource(R.string.perm_oem_step1_honor),
                        stringResource(R.string.perm_oem_step2_honor),
                        stringResource(R.string.perm_oem_step3_honor),
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { OemSettings.openLaunchManager(context) }) {
                Text(stringResource(if (honor) R.string.perm_oem_open_honor else R.string.perm_oem_open))
            }
        }
    }
}
