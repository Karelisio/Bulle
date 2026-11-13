package com.karelisio.bulle.modules.rules.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.ModuleRegistry
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.modules.rules.Rule
import com.karelisio.bulle.modules.rules.RuleAction
import com.karelisio.bulle.modules.rules.RuleCondition
import com.karelisio.bulle.modules.rules.RuleLocation
import com.karelisio.bulle.modules.rules.RuleLogic
import com.karelisio.bulle.modules.rules.RuleMatch
import com.karelisio.bulle.modules.rules.RulePermissions
import com.karelisio.bulle.modules.rules.RuleTrigger
import com.karelisio.bulle.modules.rules.RuleWorld
import com.karelisio.bulle.ui.components.rememberReduceMotion
import com.karelisio.bulle.ui.settings.ActionList
import com.karelisio.bulle.ui.settings.AppList
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Types de conditions proposés à l'ajout. */
internal enum class TriggerKind { APP, HEADSET, CHARGING, TIME, WIFI, BATTERY, BLUETOOTH, DARK, PLACE, LOCKED, CALL, DAYS }

/** Types d'actions proposés à l'ajout. */
internal enum class ActionKind { SHOW, HIDE, STYLE, RUN, MODULE, FOCUS }

/** Feuille secondaire ouverte depuis l'éditeur ; [index] = élément modifié, null = ajout. */
private sealed interface Picker {
    data object AddCondition : Picker
    data object AddAction : Picker
    data class App(val index: Int?) : Picker
    data class Run(val index: Int?) : Picker
    data class Bluetooth(val index: Int?) : Picker
    data class Module(val index: Int?) : Picker
    data class Time(val index: Int, val start: Boolean) : Picker
}

/**
 * Éditeur d'une règle : conditions (ET/OU, négation), actions, résumé lisible et aperçu
 * « active maintenant » calculé en direct sur [world].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RuleEditorSheet(
    initial: Rule?,
    world: RuleWorld,
    onDraft: (Rule) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val baseId = remember { initial?.id ?: Rule().id }
    var conditions by remember { mutableStateOf(initial?.conditions ?: listOf(RuleCondition(RuleTrigger.Charging))) }
    var match by remember { mutableStateOf(initial?.match ?: RuleMatch.ALL) }
    var actions by remember { mutableStateOf(initial?.actions ?: listOf<RuleAction>(RuleAction.HideIsland)) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var locating by remember { mutableStateOf(false) }

    val draft = Rule(id = baseId, enabled = initial?.enabled ?: true, conditions = conditions, match = match, actions = actions)
    // Publié après chaque composition appliquée (aperçu en direct, géorepérage du brouillon).
    SideEffect { onDraft(draft) }

    fun setCondition(i: Int, c: RuleCondition) {
        conditions = conditions.toMutableList().also { if (i in it.indices) it[i] = c else it += c }
    }
    fun setAction(i: Int?, a: RuleAction) {
        actions = actions.toMutableList().also { if (i != null && i in it.indices) it[i] = a else it += a }
    }

    fun addPlaceHere() {
        locating = true
        scope.launch {
            val fix = RuleLocation.current(context)
            locating = false
            val place = RuleTrigger.Place(fix?.latitude ?: 0.0, fix?.longitude ?: 0.0)
            conditions = conditions + RuleCondition(place)
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) addPlaceHere()
    }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var pendingBt by remember { mutableStateOf<Int?>(-1) }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) picker = Picker.Bluetooth(pendingBt?.takeIf { it >= 0 })
    }
    fun openBluetooth(index: Int?) {
        if (RulePermissions.bluetooth(context)) {
            picker = Picker.Bluetooth(index)
        } else {
            pendingBt = index ?: -1
            btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val sizeSpec = if (reduceMotion) snap() else spring<androidx.compose.ui.unit.IntSize>(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp).animateContentSize(sizeSpec),
        ) {
            // Résumé lisible, animé à chaque changement, et aperçu en direct.
            val preview = draft.sentence()
            AnimatedContent(preview, transitionSpec = {
                if (reduceMotion) fadeIn(snap()).togetherWith(fadeOut(snap()))
                else (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut())
            }, label = "sentence") {
                Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            LiveBadge(active = RuleLogic.isActive(draft, world))

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.rules_conditions), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (conditions.size >= 2) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    RuleMatch.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = match == m,
                            onClick = {
                                if (match != m) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                match = m
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, RuleMatch.entries.size),
                        ) { Text(stringResource(if (m == RuleMatch.ALL) R.string.rules_match_all else R.string.rules_match_any)) }
                    }
                }
            }
            conditions.forEachIndexed { i, c ->
                ConditionCard(
                    condition = c,
                    live = RuleLogic.isActive(c, world),
                    onChange = { setCondition(i, it) },
                    onRemove = { conditions = conditions.filterIndexed { j, _ -> j != i } },
                    onPickApp = { picker = Picker.App(i) },
                    onPickBluetooth = { openBluetooth(i) },
                    onPickTime = { start -> picker = Picker.Time(i, start) },
                    onPlaceHere = {
                        locating = true
                        scope.launch {
                            RuleLocation.current(context)?.let { fix ->
                                (conditions.getOrNull(i)?.trigger as? RuleTrigger.Place)?.let { p ->
                                    setCondition(i, c.copy(trigger = p.copy(latitude = fix.latitude, longitude = fix.longitude)))
                                }
                            }
                            locating = false
                        }
                    },
                    onBackgroundLocation = { backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                    locating = locating,
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { picker = Picker.AddCondition }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.rules_add_condition))
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.rules_actions), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            actions.forEachIndexed { i, a ->
                ActionCard(
                    action = a,
                    onChange = { setAction(i, it) },
                    onRemove = { actions = actions.filterIndexed { j, _ -> j != i } },
                    onPickRun = { picker = Picker.Run(i) },
                    onPickModule = { picker = Picker.Module(i) },
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { picker = Picker.AddAction }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.rules_add_action))
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(draft.sanitized()) }) { Text(stringResource(R.string.save)) }
            }
        }
    }

    when (val p = picker) {
        null -> Unit
        Picker.AddCondition -> ChoiceSheet(
            title = stringResource(R.string.rules_pick_condition),
            entries = TriggerKind.entries.map { it to stringResource(it.labelRes()) },
            onDismiss = { picker = null },
        ) { k ->
            picker = null
            when (k) {
                TriggerKind.APP -> picker = Picker.App(null)
                TriggerKind.BLUETOOTH -> openBluetooth(null)
                TriggerKind.PLACE -> if (RulePermissions.location(context)) {
                    addPlaceHere()
                } else {
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                else -> conditions = conditions + RuleCondition(k.defaultTrigger())
            }
        }
        Picker.AddAction -> ChoiceSheet(
            title = stringResource(R.string.rules_pick_action),
            entries = ActionKind.entries.map { it to stringResource(it.labelRes()) },
            onDismiss = { picker = null },
        ) { k ->
            picker = null
            when (k) {
                ActionKind.RUN -> picker = Picker.Run(null)
                ActionKind.MODULE -> picker = Picker.Module(null)
                ActionKind.SHOW -> setAction(null, RuleAction.ShowIsland)
                ActionKind.HIDE -> setAction(null, RuleAction.HideIsland)
                ActionKind.STYLE -> setAction(null, RuleAction.SetMotionStyle(MotionStyle.SOBER))
                ActionKind.FOCUS -> setAction(null, RuleAction.FocusMode)
            }
        }
        is Picker.App -> ModalBottomSheet(onDismissRequest = { picker = null }) {
            AppList { app ->
                val c = RuleCondition(RuleTrigger.AppOpened(app.packageName, app.label), conditions.getOrNull(p.index ?: -1)?.negated ?: false)
                setCondition(p.index ?: -1, c)
                picker = null
            }
        }
        is Picker.Run -> ModalBottomSheet(onDismissRequest = { picker = null }) {
            ActionList(current = (actions.getOrNull(p.index ?: -1) as? RuleAction.Run)?.action?.type) { type ->
                if (type != ActionType.OPEN_APP && type != ActionType.LAUNCH_SHORTCUT) setAction(p.index, RuleAction.Run(GestureAction(type)))
                picker = null
            }
        }
        is Picker.Bluetooth -> BluetoothSheet(onDismiss = { picker = null }) { address, name ->
            setCondition(p.index ?: -1, RuleCondition(RuleTrigger.BluetoothDevice(address, name), conditions.getOrNull(p.index ?: -1)?.negated ?: false))
            picker = null
        }
        is Picker.Module -> ChoiceSheet(
            title = stringResource(R.string.rules_module_pick),
            entries = ModuleRegistry.modules.map { it.id to stringResource(it.titleRes) },
            onDismiss = { picker = null },
        ) { id ->
            val previous = actions.getOrNull(p.index ?: -1) as? RuleAction.SetModule
            setAction(p.index, RuleAction.SetModule(id, previous?.enabled ?: false))
            picker = null
        }
        is Picker.Time -> {
            val t = conditions.getOrNull(p.index)?.trigger as? RuleTrigger.TimeRange
            if (t == null) {
                LaunchedEffect(p) { picker = null }
            } else {
                val initialMin = if (p.start) t.startMinute else t.endMinute
                val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = true)
                AlertDialog(
                    onDismissRequest = { picker = null },
                    confirmButton = {
                        TextButton(onClick = {
                            val m = state.hour * 60 + state.minute
                            val nt = if (p.start) t.copy(startMinute = m) else t.copy(endMinute = m)
                            setCondition(p.index, conditions[p.index].copy(trigger = nt))
                            picker = null
                        }) { Text(stringResource(R.string.save)) }
                    },
                    dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
                    text = { TimePicker(state = state) },
                )
            }
        }
    }
}

/** Pastille « active maintenant » / « inactive ». */
@Composable
internal fun LiveBadge(active: Boolean) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "live",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (active) R.string.rules_active_now else R.string.rules_inactive_now),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionCard(
    condition: RuleCondition,
    live: Boolean,
    onChange: (RuleCondition) -> Unit,
    onRemove: () -> Unit,
    onPickApp: () -> Unit,
    onPickBluetooth: () -> Unit,
    onPickTime: (start: Boolean) -> Unit,
    onPlaceHere: () -> Unit,
    onBackgroundLocation: () -> Unit,
    locating: Boolean,
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).background(
                        if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Text(condition.text(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = condition.negated,
                    onClick = { onChange(condition.copy(negated = !condition.negated)) },
                    label = { Text(stringResource(R.string.rules_not)) },
                )
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.rules_remove)) }
            }
            Column(Modifier.padding(end = 12.dp)) {
                when (val t = condition.trigger) {
                    is RuleTrigger.AppOpened -> TextButton(onClick = onPickApp) { Text(stringResource(R.string.rules_change_app)) }
                    is RuleTrigger.TimeRange -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onPickTime(true) }) { Text(stringResource(R.string.rules_from, hhmm(t.startMinute))) }
                        OutlinedButton(onClick = { onPickTime(false) }) { Text(stringResource(R.string.rules_to, hhmm(t.endMinute))) }
                    }
                    is RuleTrigger.WifiConnected -> OutlinedTextField(
                        value = t.ssid.orEmpty(),
                        onValueChange = { onChange(condition.copy(trigger = RuleTrigger.WifiConnected(it.ifBlank { null }))) },
                        label = { Text(stringResource(R.string.rules_wifi_name)) },
                        supportingText = { Text(stringResource(R.string.rules_wifi_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is RuleTrigger.BatteryLevel -> {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            listOf(true, false).forEachIndexed { i, below ->
                                SegmentedButton(
                                    selected = t.below == below,
                                    onClick = { onChange(condition.copy(trigger = t.copy(below = below))) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                ) { Text(stringResource(if (below) R.string.rules_battery_below else R.string.rules_battery_above)) }
                            }
                        }
                        Text(stringResource(R.string.rules_battery_threshold, t.threshold), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        Slider(
                            value = t.threshold.toFloat(),
                            onValueChange = { onChange(condition.copy(trigger = t.copy(threshold = ((it / 5).roundToInt() * 5).coerceIn(5, 100)))) },
                            valueRange = 5f..100f,
                            steps = 18,
                        )
                    }
                    is RuleTrigger.BluetoothDevice -> TextButton(onClick = onPickBluetooth) { Text(stringResource(R.string.rules_pick_bt)) }
                    is RuleTrigger.DaysOfWeek -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = d in t.days,
                                onClick = { onChange(condition.copy(trigger = t.copy(days = if (d in t.days) t.days - d else t.days + d))) },
                                label = { Text(dayShort(d)) },
                            )
                        }
                    }
                    is RuleTrigger.Place -> PlaceEditor(
                        place = t,
                        locating = locating,
                        backgroundGranted = RulePermissions.backgroundLocation(context),
                        onChange = { onChange(condition.copy(trigger = it)) },
                        onHere = onPlaceHere,
                        onBackground = onBackgroundLocation,
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun PlaceEditor(
    place: RuleTrigger.Place,
    locating: Boolean,
    backgroundGranted: Boolean,
    onChange: (RuleTrigger.Place) -> Unit,
    onHere: () -> Unit,
    onBackground: () -> Unit,
) {
    var latText by remember(place.id) { mutableStateOf("%.6f".format(java.util.Locale.ROOT, place.latitude)) }
    var lonText by remember(place.id) { mutableStateOf("%.6f".format(java.util.Locale.ROOT, place.longitude)) }
    LaunchedEffect(place.latitude, place.longitude) {
        if (latText.toDoubleOrNull() != place.latitude) latText = "%.6f".format(java.util.Locale.ROOT, place.latitude)
        if (lonText.toDoubleOrNull() != place.longitude) lonText = "%.6f".format(java.util.Locale.ROOT, place.longitude)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = place.label,
            onValueChange = { onChange(place.copy(label = it)) },
            label = { Text(stringResource(R.string.rules_place_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { s -> latText = s; s.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { onChange(place.copy(latitude = it)) } },
                label = { Text(stringResource(R.string.rules_place_lat)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { s -> lonText = s; s.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { onChange(place.copy(longitude = it)) } },
                label = { Text(stringResource(R.string.rules_place_lon)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(onClick = onHere, enabled = !locating) {
            Icon(Icons.Rounded.MyLocation, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (locating) R.string.rules_place_locating else R.string.rules_place_here))
        }
        Text(stringResource(R.string.rules_place_radius, place.radiusMeters), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = place.radiusMeters.toFloat(),
            onValueChange = { onChange(place.copy(radiusMeters = ((it / 50).roundToInt() * 50).coerceIn(RuleTrigger.Place.MIN_RADIUS_M, 2_000))) },
            valueRange = RuleTrigger.Place.MIN_RADIUS_M.toFloat()..2_000f,
        )
        if (!backgroundGranted) {
            Text(stringResource(R.string.rules_place_background_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBackground) { Text(stringResource(R.string.rules_place_background)) }
        }
    }
}

@Composable
private fun ActionCard(
    action: RuleAction,
    onChange: (RuleAction) -> Unit,
    onRemove: () -> Unit,
    onPickRun: () -> Unit,
    onPickModule: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action.text().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.rules_remove)) }
            }
            Column(Modifier.padding(end = 12.dp)) {
                when (action) {
                    is RuleAction.SetMotionStyle -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MotionStyle.entries.forEach { st ->
                            FilterChip(selected = action.style == st, onClick = { onChange(RuleAction.SetMotionStyle(st)) }, label = { Text(stringResource(st.labelRes())) })
                        }
                    }
                    is RuleAction.Run -> TextButton(onClick = onPickRun) { Text(stringResource(R.string.rules_a_run)) }
                    is RuleAction.SetModule -> {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(true, false).forEachIndexed { i, on ->
                                SegmentedButton(
                                    selected = action.enabled == on,
                                    onClick = { onChange(action.copy(enabled = on)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                ) { Text(stringResource(if (on) R.string.rules_module_enable else R.string.rules_module_disable)) }
                            }
                        }
                        TextButton(onClick = onPickModule) { Text(stringResource(R.string.rules_module_pick)) }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** Feuille de choix simple (liste de puces). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSheet(title: String, entries: List<Pair<T, String>>, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { (value, label) ->
                    AssistChip(onClick = { onPick(value) }, label = { Text(label) })
                }
            }
        }
    }
}

/** Appareils Bluetooth appairés (autorisation BLUETOOTH_CONNECT accordée avant l'ouverture). */
@Composable
private fun BluetoothSheet(onDismiss: () -> Unit, onPick: (address: String, name: String) -> Unit) {
    val context = LocalContext.current
    val devices = remember { bondedDevices(context) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    stringResource(R.string.rules_bt_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            if (devices.isEmpty()) {
                item { Text(stringResource(R.string.rules_bt_empty), modifier = Modifier.padding(24.dp)) }
            }
            items(devices, key = { it.first }) { (address, name) ->
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(address) },
                    modifier = Modifier.clickable { onPick(address, name) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }
    }
}

@SuppressLint("MissingPermission") // Vérifiée par RulePermissions.bluetooth.
private fun bondedDevices(context: Context): List<Pair<String, String>> {
    if (!RulePermissions.bluetooth(context)) return emptyList()
    return runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .map { d -> d.address to (runCatching { d.alias ?: d.name }.getOrNull() ?: d.address) }
            .sortedBy { it.second.lowercase() }
    }.getOrDefault(emptyList())
}

internal fun TriggerKind.defaultTrigger(): RuleTrigger = when (this) {
    TriggerKind.APP -> RuleTrigger.AppOpened("", "")
    TriggerKind.HEADSET -> RuleTrigger.HeadsetConnected
    TriggerKind.CHARGING -> RuleTrigger.Charging
    TriggerKind.TIME -> RuleTrigger.TimeRange(22 * 60, 7 * 60)
    TriggerKind.WIFI -> RuleTrigger.WifiConnected(null)
    TriggerKind.BATTERY -> RuleTrigger.BatteryLevel(20, below = true)
    TriggerKind.BLUETOOTH -> RuleTrigger.BluetoothDevice("", "")
    TriggerKind.DARK -> RuleTrigger.DarkMode
    TriggerKind.PLACE -> RuleTrigger.Place(0.0, 0.0)
    TriggerKind.LOCKED -> RuleTrigger.ScreenLocked
    TriggerKind.CALL -> RuleTrigger.InCall
    TriggerKind.DAYS -> RuleTrigger.DaysOfWeek()
}

internal fun TriggerKind.labelRes(): Int = when (this) {
    TriggerKind.APP -> R.string.rules_t_app
    TriggerKind.HEADSET -> R.string.rules_t_headset
    TriggerKind.CHARGING -> R.string.rules_t_charging
    TriggerKind.TIME -> R.string.rules_t_time
    TriggerKind.WIFI -> R.string.rules_t_wifi
    TriggerKind.BATTERY -> R.string.rules_t_battery
    TriggerKind.BLUETOOTH -> R.string.rules_t_bt
    TriggerKind.DARK -> R.string.rules_t_dark
    TriggerKind.PLACE -> R.string.rules_t_place
    TriggerKind.LOCKED -> R.string.rules_t_locked
    TriggerKind.CALL -> R.string.rules_t_call
    TriggerKind.DAYS -> R.string.rules_t_days
}

internal fun ActionKind.labelRes(): Int = when (this) {
    ActionKind.SHOW -> R.string.rules_a_show
    ActionKind.HIDE -> R.string.rules_a_hide
    ActionKind.STYLE -> R.string.rules_a_style
    ActionKind.RUN -> R.string.rules_a_run
    ActionKind.MODULE -> R.string.rules_a_module
    ActionKind.FOCUS -> R.string.rules_a_focus
}
