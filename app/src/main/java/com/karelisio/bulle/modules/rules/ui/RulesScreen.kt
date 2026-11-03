package com.karelisio.bulle.modules.rules.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.rules.Rule
import com.karelisio.bulle.modules.rules.RuleAction
import com.karelisio.bulle.modules.rules.RuleTrigger
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.settings.ActionList
import com.karelisio.bulle.ui.settings.AppList
import com.karelisio.bulle.ui.settings.label
import kotlinx.coroutines.launch

/** Phrase lisible d'une règle : « Quand …, … ». */
@Composable
fun Rule.sentence(): String = stringResource(R.string.rule_sentence, trigger.text(), action.text())

@Composable
fun RuleTrigger.text(): String = when (this) {
    is RuleTrigger.AppOpened -> stringResource(R.string.rule_when_app, label)
    RuleTrigger.HeadsetConnected -> stringResource(R.string.rule_when_headset)
    RuleTrigger.Charging -> stringResource(R.string.rule_when_charging)
    is RuleTrigger.TimeRange -> stringResource(R.string.rule_when_time, hhmm(startMinute), hhmm(endMinute))
    is RuleTrigger.WifiConnected -> if (ssid.isNullOrBlank()) stringResource(R.string.rule_when_wifi_any)
    else stringResource(R.string.rule_when_wifi, ssid)
}

@Composable
fun RuleAction.text(): String = when (this) {
    RuleAction.ShowIsland -> stringResource(R.string.rule_do_show)
    RuleAction.HideIsland -> stringResource(R.string.rule_do_hide)
    is RuleAction.SetMotionStyle -> stringResource(R.string.rule_do_style, stringResource(style.labelRes()))
    is RuleAction.Run -> stringResource(R.string.rule_do_run, action.label().replaceFirstChar { it.lowercase() })
}

fun MotionStyle.labelRes(): Int = when (this) {
    MotionStyle.SOFT -> R.string.motion_soft
    MotionStyle.SNAPPY -> R.string.motion_snappy
    MotionStyle.SOBER -> R.string.motion_sober
}

private fun hhmm(m: Int) = "%d:%02d".format(m / 60, m % 60)

/** Liste des règles automatiques. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val rules = settings.rules.rules
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun update(transform: (List<Rule>) -> List<Rule>) = scope.launch {
        SettingsRepository.update(context) { it.copy(rules = it.rules.copy(rules = transform(it.rules.rules))) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.rules_add)) },
            )
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.rules_empty), style = MaterialTheme.typography.bodyLarge)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rules, key = { it.id }) { rule ->
                val dismiss = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) update { l -> l.filterNot { r -> r.id == rule.id } } },
                    modifier = Modifier.animateItem().cascade(rules.indexOf(rule)).clip(RoundedCornerShape(28.dp)),
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                    },
                ) {
                    Card(
                        onClick = { editing = rule },
                        colors = CardDefaults.cardColors(
                            containerColor = if (rule.enabled) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.sentence(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(checked = rule.enabled, onCheckedChange = { on ->
                                update { l -> l.map { if (it.id == rule.id) it.copy(enabled = on) else it } }
                            })
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        RuleEditorSheet(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { rule ->
                update { l -> if (l.any { it.id == rule.id }) l.map { if (it.id == rule.id) rule else it } else l + rule }
                creating = false
                editing = null
            },
        )
    }
}

private enum class TriggerKind { APP, HEADSET, CHARGING, TIME, WIFI }
private enum class ActionKind { SHOW, HIDE, STYLE, RUN }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorSheet(initial: Rule?, onDismiss: () -> Unit, onSave: (Rule) -> Unit) {
    var trigger by remember { mutableStateOf(initial?.trigger ?: RuleTrigger.Charging) }
    var action by remember { mutableStateOf(initial?.action ?: RuleAction.HideIsland) }
    var picker by remember { mutableStateOf<String?>(null) }

    val kind = when (trigger) {
        is RuleTrigger.AppOpened -> TriggerKind.APP
        RuleTrigger.HeadsetConnected -> TriggerKind.HEADSET
        RuleTrigger.Charging -> TriggerKind.CHARGING
        is RuleTrigger.TimeRange -> TriggerKind.TIME
        is RuleTrigger.WifiConnected -> TriggerKind.WIFI
    }
    val aKind = when (action) {
        RuleAction.ShowIsland -> ActionKind.SHOW
        RuleAction.HideIsland -> ActionKind.HIDE
        is RuleAction.SetMotionStyle -> ActionKind.STYLE
        is RuleAction.Run -> ActionKind.RUN
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            // Aperçu de la phrase, animé à chaque changement.
            val preview = Rule(trigger = trigger, action = action).sentence()
            AnimatedContent(preview, transitionSpec = {
                (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut())
            }, label = "sentence") {
                Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.rules_trigger), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerKind.entries.forEach { k ->
                    FilterChip(
                        selected = k == kind,
                        onClick = {
                            when (k) {
                                TriggerKind.APP -> picker = "app"
                                TriggerKind.HEADSET -> trigger = RuleTrigger.HeadsetConnected
                                TriggerKind.CHARGING -> trigger = RuleTrigger.Charging
                                TriggerKind.TIME -> trigger = RuleTrigger.TimeRange(22 * 60, 7 * 60)
                                TriggerKind.WIFI -> trigger = RuleTrigger.WifiConnected(null)
                            }
                        },
                        label = { Text(stringResource(k.labelRes())) },
                    )
                }
            }
            when (val t = trigger) {
                is RuleTrigger.TimeRange -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker = "start" }) { Text(stringResource(R.string.rules_from, hhmm(t.startMinute))) }
                    OutlinedButton(onClick = { picker = "end" }) { Text(stringResource(R.string.rules_to, hhmm(t.endMinute))) }
                }
                is RuleTrigger.WifiConnected -> OutlinedTextField(
                    value = t.ssid.orEmpty(),
                    onValueChange = { trigger = RuleTrigger.WifiConnected(it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.rules_wifi_name)) },
                    supportingText = { Text(stringResource(R.string.rules_wifi_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> Unit
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.rules_action), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionKind.entries.forEach { k ->
                    FilterChip(
                        selected = k == aKind,
                        onClick = {
                            when (k) {
                                ActionKind.SHOW -> action = RuleAction.ShowIsland
                                ActionKind.HIDE -> action = RuleAction.HideIsland
                                ActionKind.STYLE -> action = RuleAction.SetMotionStyle(MotionStyle.SOBER)
                                ActionKind.RUN -> picker = "action"
                            }
                        },
                        label = { Text(stringResource(k.labelRes())) },
                    )
                }
            }
            (action as? RuleAction.SetMotionStyle)?.let { a ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotionStyle.entries.forEach { st ->
                        FilterChip(selected = a.style == st, onClick = { action = RuleAction.SetMotionStyle(st) }, label = { Text(stringResource(st.labelRes())) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.padding(4.dp))
                Button(onClick = { onSave(Rule(id = initial?.id ?: Rule(trigger = trigger, action = action).id, enabled = initial?.enabled ?: true, trigger = trigger, action = action)) }) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    when (picker) {
        "app" -> ModalBottomSheet(onDismissRequest = { picker = null }) {
            AppList { app -> trigger = RuleTrigger.AppOpened(app.packageName, app.label); picker = null }
        }
        "action" -> ModalBottomSheet(onDismissRequest = { picker = null }) {
            ActionList(current = (action as? RuleAction.Run)?.action?.type) { type ->
                if (type != ActionType.OPEN_APP && type != ActionType.LAUNCH_SHORTCUT) action = RuleAction.Run(GestureAction(type))
                picker = null
            }
        }
        "start", "end" -> {
            val t = trigger as? RuleTrigger.TimeRange ?: return
            val initialMin = if (picker == "start") t.startMinute else t.endMinute
            val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val m = state.hour * 60 + state.minute
                        trigger = if (picker == "start") t.copy(startMinute = m) else t.copy(endMinute = m)
                        picker = null
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
                text = { TimePicker(state = state) },
            )
        }
    }
}

private fun TriggerKind.labelRes() = when (this) {
    TriggerKind.APP -> R.string.rules_t_app
    TriggerKind.HEADSET -> R.string.rules_t_headset
    TriggerKind.CHARGING -> R.string.rules_t_charging
    TriggerKind.TIME -> R.string.rules_t_time
    TriggerKind.WIFI -> R.string.rules_t_wifi
}

private fun ActionKind.labelRes() = when (this) {
    ActionKind.SHOW -> R.string.rules_a_show
    ActionKind.HIDE -> R.string.rules_a_hide
    ActionKind.STYLE -> R.string.rules_a_style
    ActionKind.RUN -> R.string.rules_a_run
}
