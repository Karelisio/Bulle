package com.karelisio.bulle.modules.volet.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.volet.VoletController
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletCardDensity
import com.karelisio.bulle.modules.volet.notifications.NotifCardPreview
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZones
import com.karelisio.bulle.modules.volet.history.NotificationHistoryScreen
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.onboarding.PermissionGlyph
import com.karelisio.bulle.ui.onboarding.PermissionLogic
import com.karelisio.bulle.ui.onboarding.PillState
import com.karelisio.bulle.ui.onboarding.StatusPill
import com.karelisio.bulle.ui.onboarding.rememberPermissionController
import com.karelisio.bulle.ui.onboarding.texts
import com.karelisio.bulle.ui.settings.AppList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Réglages du module Volet. [onOpenHistory] : navigation vers l'historique ; si null,
 * l'historique s'ouvre au sein de cet écran.
 */
@Composable
fun VoletSettingsScreen(onBack: () -> Unit, onOpenHistory: (() -> Unit)? = null) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(
        targetState = showHistory && onOpenHistory == null,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "voletSettings",
    ) { history ->
        if (history) {
            BackHandler { showHistory = false }
            NotificationHistoryScreen(onBack = { showHistory = false })
        } else {
            VoletSettingsContent(onBack, onOpenHistory = { onOpenHistory?.invoke() ?: run { showHistory = true } })
        }
    }
}

@Composable
private fun VoletSettingsContent(onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val saved by remember { SettingsRepository.flow(context).map { it.volet } }.collectAsStateWithLifecycle(null)
    // Brouillon local : les curseurs bougent sans attendre l'écriture des réglages.
    var draft by remember { mutableStateOf(VoletSettings()) }
    LaunchedEffect(saved) { saved?.let { draft = it } }
    var picking by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = AppCatalog.apps(context) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Zones de déclenchement teintées à l'écran tant que ces réglages sont affichés
    // (au premier plan seulement : après « Accueil », elles ne doivent pas rester teintées sur les autres applis).
    LifecycleStartEffect(Unit) {
        VoletController.previewFlow.value = true
        onStopOrDispose { VoletController.previewFlow.value = false }
    }

    /** Applique [t] au brouillon et l'enregistre. */
    fun commit(t: (VoletSettings) -> VoletSettings) {
        draft = t(draft)
        scope.launch { SettingsRepository.update(context) { it.copy(volet = t(it.volet)) } }
    }

    /** Curseur : brouillon pendant le geste, enregistrement au relâchement. */
    fun live(t: (VoletSettings) -> VoletSettings) {
        draft = t(draft)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.vset_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val s = draft
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("enable") {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = if (s.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().cascade(0),
                ) {
                    Row(
                        Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                commit { it.copy(enabled = !it.enabled) }
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.vset_enable), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.vset_enable_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = s.enabled, onCheckedChange = { on -> commit { it.copy(enabled = on) } })
                    }
                }
            }
            item("reliability") { VoletReliabilityBanner() }

            item("zones") {
                SectionCard(Icons.Rounded.SwipeDown, stringResource(R.string.vset_zones), 1) {
                    Text(
                        stringResource(R.string.vset_zones_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    val zoneLabels = listOf(
                        VoletZones.SPLIT to R.string.vset_zones_split,
                        VoletZones.SPLIT_REVERSED to R.string.vset_zones_reversed,
                        VoletZones.SINGLE to R.string.vset_zones_single,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        zoneLabels.forEachIndexed { i, (zone, label) ->
                            SegmentedButton(
                                selected = s.zones == zone,
                                onClick = { commit { it.copy(zones = zone) } },
                                shape = SegmentedButtonDefaults.itemShape(i, zoneLabels.size),
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                    Text(
                        stringResource(
                            when (s.zones) {
                                VoletZones.SPLIT -> R.string.vset_zones_split_desc
                                VoletZones.SPLIT_REVERSED -> R.string.vset_zones_reversed_desc
                                VoletZones.SINGLE -> R.string.vset_zones_single_desc
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    SliderRow(
                        label = stringResource(R.string.vset_trigger_height),
                        value = s.triggerHeightDp,
                        valueText = if (s.triggerHeightDp <= 0f) stringResource(R.string.vset_auto) else stringResource(R.string.vset_dp, s.triggerHeightDp.roundToInt()),
                        range = 0f..VoletSettingsBounds.TRIGGER_MAX,
                        onChange = { v -> live { it.copy(triggerHeightDp = VoletSettingsBounds.triggerHeight(v)) } },
                        onDone = { commit { it.copy(triggerHeightDp = draft.triggerHeightDp) } },
                    )
                    SliderRow(
                        label = stringResource(R.string.vset_landscape_height),
                        value = s.landscapeTriggerHeightDp,
                        valueText = if (s.landscapeTriggerHeightDp <= 0f) stringResource(R.string.vset_landscape_off) else stringResource(R.string.vset_dp, s.landscapeTriggerHeightDp.roundToInt()),
                        range = 0f..VoletSettingsBounds.LANDSCAPE_MAX,
                        onChange = { v -> live { it.copy(landscapeTriggerHeightDp = VoletSettingsBounds.landscapeHeight(v)) } },
                        onDone = { commit { it.copy(landscapeTriggerHeightDp = draft.landscapeTriggerHeightDp) } },
                    )
                    Text(
                        stringResource(R.string.vset_landscape_height_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SliderRow(
                        label = stringResource(R.string.vset_edge_inset),
                        value = s.edgeInsetDp,
                        valueText = stringResource(R.string.vset_dp, s.edgeInsetDp.roundToInt()),
                        range = 0f..VoletSettingsBounds.EDGE_MAX,
                        onChange = { v -> live { it.copy(edgeInsetDp = v.roundToInt().toFloat()) } },
                        onDone = { commit { it.copy(edgeInsetDp = draft.edgeInsetDp) } },
                    )
                    if (s.enabled) {
                        Text(
                            stringResource(R.string.vset_zones_preview_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            item("look") {
                SectionCard(Icons.Rounded.Palette, stringResource(R.string.vset_look), 2) {
                    VoletMiniPreview(s)
                    Spacer(Modifier.height(8.dp))
                    SliderRow(
                        label = stringResource(R.string.vset_blur),
                        value = s.blurRadiusDp,
                        valueText = stringResource(R.string.vset_dp, s.blurRadiusDp.roundToInt()),
                        range = 0f..VoletSettingsBounds.BLUR_MAX,
                        onChange = { v -> live { it.copy(blurRadiusDp = v.roundToInt().toFloat()) } },
                        onDone = { commit { it.copy(blurRadiusDp = draft.blurRadiusDp) } },
                    )
                    SliderRow(
                        label = stringResource(R.string.vset_scrim),
                        value = s.scrimOpacity,
                        valueText = stringResource(R.string.vset_percent, (s.scrimOpacity * 100).roundToInt()),
                        range = VoletSettingsBounds.SCRIM_MIN..VoletSettingsBounds.SCRIM_MAX,
                        onChange = { v -> live { it.copy(scrimOpacity = (v * 100).roundToInt() / 100f) } },
                        onDone = { commit { it.copy(scrimOpacity = draft.scrimOpacity) } },
                    )
                    SliderRow(
                        label = stringResource(R.string.vset_tiles),
                        value = s.collapsedTileCount.toFloat(),
                        valueText = s.collapsedTileCount.toString(),
                        range = 4f..6f,
                        steps = 1,
                        onChange = { v -> live { it.copy(collapsedTileCount = v.roundToInt().coerceIn(4, 6)) } },
                        onDone = { commit { it.copy(collapsedTileCount = draft.collapsedTileCount) } },
                    )
                    Text(
                        stringResource(R.string.vset_density),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                    val densities = listOf(VoletDensity.COMFORTABLE to R.string.vset_density_comfortable, VoletDensity.COMPACT to R.string.vset_density_compact)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        densities.forEachIndexed { i, (d, label) ->
                            SegmentedButton(
                                selected = s.density == d,
                                onClick = { commit { it.copy(density = d) } },
                                shape = SegmentedButtonDefaults.itemShape(i, densities.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
            }

            item("cards") {
                SectionCard(Icons.Rounded.FormatSize, stringResource(R.string.vset_cards), 3) {
                    Text(
                        stringResource(R.string.vset_cards_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Aperçu en direct d'une conversation et d'une notification.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .padding(10.dp),
                    ) {
                        NotifCardPreview(s, Modifier.animateContentSize())
                    }
                    Text(
                        stringResource(R.string.vset_cards_density),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    val cardDensities = listOf(
                        VoletCardDensity.COMPACT to R.string.vset_cards_compact,
                        VoletCardDensity.NORMAL to R.string.vset_cards_normal,
                        VoletCardDensity.AIRY to R.string.vset_cards_airy,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        cardDensities.forEachIndexed { i, (d, label) ->
                            SegmentedButton(
                                selected = s.effectiveCardDensity == d,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    commit { it.copy(cardDensity = d) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, cardDensities.size),
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                    SliderRow(
                        label = stringResource(R.string.vset_cards_text),
                        value = s.cardTextScale,
                        valueText = stringResource(R.string.vset_percent, (s.cardTextScale * 100).roundToInt()),
                        range = VoletSettingsBounds.TEXT_SCALE_MIN..VoletSettingsBounds.TEXT_SCALE_MAX,
                        steps = 7,
                        onChange = { v -> live { it.copy(cardTextScale = VoletSettingsBounds.textScale(v)) } },
                        onDone = { commit { it.copy(cardTextScale = draft.cardTextScale) } },
                    )
                    SliderRow(
                        label = stringResource(R.string.vset_corners),
                        value = s.cornerRadiusDp,
                        valueText = stringResource(R.string.vset_dp, s.cornerRadiusDp.roundToInt()),
                        range = VoletSettingsBounds.CORNER_MIN..VoletSettingsBounds.CORNER_MAX,
                        onChange = { v -> live { it.copy(cornerRadiusDp = v.roundToInt().toFloat()) } },
                        onDone = { commit { it.copy(cornerRadiusDp = draft.cornerRadiusDp) } },
                    )
                    SliderRow(
                        label = stringResource(R.string.vset_cards_lines),
                        value = s.cardCollapsedLines.toFloat(),
                        valueText = s.cardCollapsedLines.toString(),
                        range = 1f..4f,
                        steps = 2,
                        onChange = { v -> live { it.copy(cardCollapsedLines = v.roundToInt().coerceIn(1, 4)) } },
                        onDone = { commit { it.copy(cardCollapsedLines = draft.cardCollapsedLines) } },
                    )
                }
            }

            item("behaviour") {
                SectionCard(Icons.Rounded.Block, stringResource(R.string.vset_behaviour), 4) {
                    SwitchRow(
                        stringResource(R.string.vset_lockscreen),
                        stringResource(R.string.vset_lockscreen_desc),
                        s.showOnLockscreen,
                    ) { on -> commit { it.copy(showOnLockscreen = on) } }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.vset_excluded), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.vset_excluded_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { picking = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text(stringResource(R.string.vset_add))
                        }
                    }
                    // Notifications masquées dans Bulle (refusées par le système ou masquées depuis le menu).
                    val hiddenNotifs by com.karelisio.bulle.notifications.NotifLocalHide.keys.collectAsState()
                    androidx.compose.runtime.LaunchedEffect(Unit) { com.karelisio.bulle.notifications.NotifLocalHide.init(context) }
                    if (hiddenNotifs.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.vset_hidden_notifs), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.vset_hidden_notifs_desc, hiddenNotifs.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { com.karelisio.bulle.notifications.NotifLocalHide.show(hiddenNotifs) }) {
                                Text(stringResource(R.string.vset_hidden_notifs_show))
                            }
                        }
                    }
                    // Applis masquées dans « Applis en fond ».
                    if (s.bgHiddenApps.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.volet_bg_hidden_title), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.volet_bg_hidden_desc, s.bgHiddenApps.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { commit { it.copy(bgHiddenApps = emptySet()) } }) {
                                Text(stringResource(R.string.volet_bg_hidden_reset))
                            }
                        }
                    }
                    s.excludedApps.sorted().forEach { pkg ->
                        val app = apps.firstOrNull { it.packageName == pkg }
                        ListItem(
                            headlineContent = { Text(app?.label ?: pkg) },
                            leadingContent = { if (app != null) AppIcon(app, 36.dp) },
                            trailingContent = {
                                IconButton(onClick = { commit { it.copy(excludedApps = it.excludedApps - pkg) } }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.vset_remove))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }

            item("history") {
                SectionCard(Icons.Rounded.History, stringResource(R.string.vset_history), 5) {
                    SwitchRow(
                        stringResource(R.string.vset_history_enable),
                        stringResource(R.string.vset_history_desc),
                        s.historyEnabled,
                    ) { on -> commit { it.copy(historyEnabled = on) } }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(MaterialTheme.shapes.large)
                            .clickable(onClick = onOpenHistory)
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.vset_history_open), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            item("permissions") {
                SectionCard(Icons.Rounded.Shield, stringResource(R.string.vset_permissions), 6) {
                    VoletPermissions()
                }
            }
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AppList { app ->
                commit { it.copy(excludedApps = it.excludedApps + app.packageName) }
                picking = false
            }
        }
    }
}
