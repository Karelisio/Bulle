package com.karelisio.bulle.modules.edges.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.edges.EdgeBus
import com.karelisio.bulle.modules.edges.EdgeGesture
import com.karelisio.bulle.modules.edges.EdgeSettings
import com.karelisio.bulle.modules.edges.EdgeSlider
import com.karelisio.bulle.modules.edges.EdgeSlot
import com.karelisio.bulle.modules.edges.EdgeZone
import com.karelisio.bulle.ui.components.ModuleCard
import com.karelisio.bulle.ui.components.ModuleChoices
import com.karelisio.bulle.ui.components.ModuleFormat
import com.karelisio.bulle.ui.components.ModuleNote
import com.karelisio.bulle.ui.components.ModuleSection
import com.karelisio.bulle.ui.components.ModuleSettingsScaffold
import com.karelisio.bulle.ui.components.ModuleSlider
import com.karelisio.bulle.ui.components.ModuleSwitch
import com.karelisio.bulle.ui.components.ModuleWarning
import com.karelisio.bulle.ui.settings.ActionList
import com.karelisio.bulle.ui.settings.AppList
import com.karelisio.bulle.ui.settings.ShortcutList
import com.karelisio.bulle.ui.settings.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class EdgePicker { ACTION, APP, SHORTCUT, DISABLED_APP }

@Composable
private fun slotTitle(slot: EdgeSlot): String = stringResource(
    when (slot) {
        EdgeSlot.LEFT -> R.string.edges_slot_left
        EdgeSlot.RIGHT -> R.string.edges_slot_right
        EdgeSlot.LEFT_LOWER -> R.string.edges_slot_left_lower
        EdgeSlot.RIGHT_LOWER -> R.string.edges_slot_right_lower
    },
)

@Composable
private fun gestureTitle(g: EdgeGesture): String = stringResource(
    when (g) {
        EdgeGesture.SWIPE_UP -> R.string.edges_g_swipe_up
        EdgeGesture.SWIPE_DOWN -> R.string.edges_g_swipe_down
        EdgeGesture.DOUBLE_TAP -> R.string.edges_g_double_tap
        EdgeGesture.LONG_PRESS -> R.string.edges_g_long_press
        EdgeGesture.SWIPE_IN -> R.string.edges_g_swipe_in
    },
)

/** Réglages des Bords. Les zones s'affichent en couleur sur l'écran tant que cet écran est ouvert. */
@Composable
fun EdgeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val saved = settings.edges
    var draft by remember(saved) { mutableStateOf(saved) }
    val placed by EdgeBus.placed.collectAsStateWithLifecycle()
    val running by EdgeBus.running.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = AppCatalog.apps(context) }

    // Aperçu coloré des zones pendant le réglage.
    DisposableEffect(Unit) {
        EdgeBus.setPreview(true)
        onDispose { EdgeBus.setPreview(false) }
    }

    fun save(t: (EdgeSettings) -> EdgeSettings) {
        draft = t(draft)
        scope.launch { SettingsRepository.update(context) { it.copy(edges = t(it.edges)) } }
    }

    // Curseurs de position : enregistrés en continu (regroupés) pour que la zone suive le doigt.
    var pending by remember { mutableStateOf<EdgeSettings?>(null) }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        delay(80)
        SettingsRepository.update(context) { it.copy(edges = p) }
    }
    fun live(t: (EdgeSettings) -> EdgeSettings) {
        draft = t(draft)
        pending = draft
    }

    var editingSlot by rememberSaveable { mutableStateOf<EdgeSlot?>(null) }
    var editingGesture by rememberSaveable { mutableStateOf<EdgeGesture?>(null) }
    var picker by rememberSaveable { mutableStateOf<EdgePicker?>(null) }

    fun assign(action: GestureAction) {
        val slot = editingSlot ?: return
        val g = editingGesture ?: return
        save { it.with(slot, it.zone(slot).with(g, action)) }
        picker = null
        editingGesture = null
    }

    val shortcutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val target: Intent? = data?.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java) }
        @Suppress("DEPRECATION")
        val name = data?.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            assign(GestureAction(ActionType.LAUNCH_SHORTCUT, intentUri = target.toUri(Intent.URI_INTENT_SCHEME), label = name))
        } else {
            picker = null
        }
    }

    ModuleSettingsScaffold(stringResource(R.string.edges_title), onBack) {
        item("enable") {
            ModuleCard {
                ModuleSwitch(stringResource(R.string.edges_enable), stringResource(R.string.edges_enable_summary), saved.enabled) { on ->
                    save { it.copy(enabled = on) }
                }
                ModuleNote(stringResource(if (running) R.string.edges_preview_note else R.string.edges_service_off))
            }
        }
        EdgeSlot.entries.forEach { slot ->
            item(slot.name) {
                val z = draft.zone(slot)
                val adjusted = placed.firstOrNull { it.slot == slot }?.adjusted == true
                ModuleSection(slotTitle(slot))
                ModuleCard {
                    ModuleSwitch(stringResource(R.string.edges_zone_enable), null, z.enabled) { on ->
                        save { it.with(slot, z.copy(enabled = on)) }
                    }
                    if (z.enabled) {
                        ModuleSlider(
                            stringResource(R.string.edges_position), z.start, 0f..(1f - EdgeZone.MIN_LENGTH), ModuleFormat::percent,
                            onPreview = { v -> live { it.with(slot, it.zone(slot).copy(start = v)) } },
                        ) { v -> save { it.with(slot, it.zone(slot).copy(start = v)) } }
                        ModuleSlider(
                            stringResource(R.string.edges_length), z.length, EdgeZone.MIN_LENGTH..EdgeZone.MAX_LENGTH, ModuleFormat::percent,
                            onPreview = { v -> live { it.with(slot, it.zone(slot).copy(length = v)) } },
                        ) { v -> save { it.with(slot, it.zone(slot).copy(length = v)) } }
                        ModuleSlider(
                            stringResource(R.string.edges_thickness), z.thicknessDp, EdgeZone.THICKNESS, ModuleFormat::dp, steps = 15,
                            onPreview = { v -> live { it.with(slot, it.zone(slot).copy(thicknessDp = v)) } },
                        ) { v -> save { it.with(slot, it.zone(slot).copy(thicknessDp = v)) } }
                        if (adjusted) ModuleNote(stringResource(R.string.edges_adjusted))
                        Text(
                            stringResource(R.string.edges_slider_mode),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                        ModuleChoices(
                            EdgeSlider.entries, z.slider,
                            {
                                stringResource(
                                    when (it) {
                                        EdgeSlider.OFF -> R.string.edges_slider_off
                                        EdgeSlider.BRIGHTNESS -> R.string.edges_slider_brightness
                                        EdgeSlider.VOLUME -> R.string.edges_slider_volume
                                    },
                                )
                            },
                        ) { k -> save { it.with(slot, it.zone(slot).copy(slider = k)) } }
                        val gestures = buildList {
                            if (z.slider == EdgeSlider.OFF) { add(EdgeGesture.SWIPE_UP); add(EdgeGesture.SWIPE_DOWN) }
                            add(EdgeGesture.DOUBLE_TAP)
                            add(EdgeGesture.LONG_PRESS)
                            if (draft.swipeInAllowed) add(EdgeGesture.SWIPE_IN)
                        }
                        gestures.forEach { g ->
                            ListItem(
                                headlineContent = { Text(gestureTitle(g)) },
                                supportingContent = { Text(z.actionFor(g).label(), color = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    editingSlot = slot
                                    editingGesture = g
                                    picker = EdgePicker.ACTION
                                },
                            )
                        }
                        if (z.slider != EdgeSlider.OFF) ModuleNote(stringResource(R.string.edges_slider_note))
                    }
                }
            }
        }
        item("options") {
            ModuleSection(stringResource(R.string.edges_section_options))
            ModuleCard {
                ModuleSwitch(stringResource(R.string.edges_liquid), stringResource(R.string.edges_liquid_summary), draft.liquidFeedback) { on ->
                    save { it.copy(liquidFeedback = on) }
                }
                ModuleSwitch(stringResource(R.string.edges_swipe_in), stringResource(R.string.edges_swipe_in_summary), draft.swipeInAllowed) { on ->
                    save { it.copy(swipeInAllowed = on) }
                }
                if (draft.swipeInAllowed) ModuleWarning(stringResource(R.string.edges_swipe_in_warning))
                ModuleNote(stringResource(R.string.edges_back_note))
            }
        }
        item("apps") {
            ModuleSection(stringResource(R.string.edges_section_apps))
            ModuleCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.edges_disabled_apps)) },
                    supportingContent = { Text(stringResource(R.string.edges_disabled_apps_summary)) },
                    trailingContent = {
                        TextButton(onClick = { picker = EdgePicker.DISABLED_APP }) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text(stringResource(R.string.deck_add))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                draft.disabledApps.sorted().forEach { pkg ->
                    val app = apps.firstOrNull { it.packageName == pkg }
                    ListItem(
                        headlineContent = { Text(app?.label ?: pkg) },
                        leadingContent = { if (app != null) AppIcon(app, 36.dp) },
                        trailingContent = {
                            IconButton(onClick = { save { it.copy(disabledApps = it.disabledApps - pkg) } }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.visibility_remove_app, app?.label ?: pkg))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                ModuleNote(stringResource(R.string.edges_visibility_note))
            }
        }
    }

    val p = picker ?: return
    ModalBottomSheet(
        onDismissRequest = { picker = null; editingGesture = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = p != EdgePicker.ACTION),
    ) {
        when (p) {
            EdgePicker.ACTION -> {
                val current = editingSlot?.let { s -> editingGesture?.let { g -> draft.zone(s).actionFor(g).type } }
                ActionList(current) { type ->
                    when (type) {
                        ActionType.OPEN_APP -> picker = EdgePicker.APP
                        ActionType.LAUNCH_SHORTCUT -> picker = EdgePicker.SHORTCUT
                        else -> assign(GestureAction(type))
                    }
                }
            }
            EdgePicker.APP -> AppList { app -> assign(GestureAction(ActionType.OPEN_APP, packageName = app.packageName, label = app.label)) }
            EdgePicker.SHORTCUT -> ShortcutList { component ->
                try {
                    shortcutLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show()
                }
            }
            EdgePicker.DISABLED_APP -> AppList { app ->
                save { it.copy(disabledApps = it.disabledApps + app.packageName) }
                picker = null
            }
        }
    }
}
