package com.karelisio.bulle.modules.countdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.countdown.CountdownDetails
import com.karelisio.bulle.modules.countdown.CountdownEvent
import com.karelisio.bulle.modules.countdown.CountdownIdleMode
import com.karelisio.bulle.modules.countdown.CountdownMath
import com.karelisio.bulle.modules.countdown.CountdownModule
import com.karelisio.bulle.modules.countdown.CountdownPrefs
import com.karelisio.bulle.modules.countdown.CountdownSettings
import com.karelisio.bulle.modules.countdown.countdownLongLabel
import com.karelisio.bulle.modules.countdown.occurrenceDate
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/** Réglages du module « Compte à rebours » : événements (ajout, édition, suppression, épingle) et affichage. */
@Composable
fun CountdownSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val countdown by remember(context) { CountdownPrefs.store.loaded(context) }.collectAsStateWithLifecycle()
    val enabled = CountdownModule.isEnabled(settings)
    // Événement en cours d'édition : null = fermé, id vide = nouvel événement.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    fun update(transform: (CountdownSettings) -> CountdownSettings) {
        CountdownPrefs.store.update(context, transform)
    }

    val now = remember(countdown) { ZonedDateTime.now() }
    val upcoming = remember(countdown, now) { CountdownMath.upcoming(countdown.events, now) }

    ModuleSettingsScaffold(title = stringResource(R.string.countdown_settings_title), onBack = onBack) {
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.countdown_settings_enable),
                    summary = stringResource(R.string.countdown_settings_enable_summary),
                    checked = enabled,
                ) { on -> scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (CountdownModule.ID to on)) } } }
            }
        }
        val preview = countdown.idleEvent(now)?.let { idle -> upcoming.firstOrNull { it.event.id == idle.id } } ?: upcoming.firstOrNull()
        if (preview != null) {
            item {
                ModuleSettingsCard { CountdownDetails(preview, upcoming, Modifier.padding(16.dp), onDark = false) }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.countdown_settings_events)) }
        item {
            ModuleSettingsCard {
                if (countdown.events.isEmpty()) {
                    Text(
                        stringResource(R.string.countdown_settings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                // Les événements passés (non annuels) restent listés pour pouvoir les supprimer.
                val ordered = upcoming.map { it.event } + countdown.events.filter { e -> upcoming.none { it.event.id == e.id } }
                ordered.forEach { event ->
                    val occurrence = upcoming.firstOrNull { it.event.id == event.id }
                    val pinned = countdown.pinnedId == event.id
                    ListItem(
                        modifier = Modifier.clickable { editingId = event.id },
                        leadingContent = {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(event.color).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) { Text(event.emoji, fontSize = 20.sp) }
                        },
                        headlineContent = { Text(event.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                if (occurrence != null) occurrenceDate(occurrence) + " · " + countdownLongLabel(occurrence.status) else stringResource(R.string.countdown_settings_past),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Row {
                                val pinLabel = stringResource(R.string.countdown_settings_pin)
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                        update { it.copy(pinnedId = event.id) }
                                    },
                                    modifier = Modifier.semantics { contentDescription = pinLabel },
                                ) {
                                    Icon(
                                        if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = null,
                                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    update { CountdownPrefs.remove(it, event.id) }
                                }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.countdown_settings_delete))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = { editingId = "" }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.countdown_settings_add))
                    }
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.countdown_settings_display)) }
        item {
            ModuleSettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.countdown_settings_idle)) },
                    supportingContent = { Text(stringResource(R.string.countdown_settings_idle_summary)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                val modes = listOf(
                    CountdownIdleMode.PINNED to R.string.countdown_idle_pinned,
                    CountdownIdleMode.NEXT to R.string.countdown_idle_next,
                    CountdownIdleMode.OFF to R.string.countdown_idle_off,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = countdown.idleMode == mode,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                update { it.copy(idleMode = mode) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            enabled = enabled,
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                ModuleSwitchRow(
                    title = stringResource(R.string.countdown_settings_remind),
                    summary = stringResource(R.string.countdown_settings_remind_summary),
                    checked = countdown.remindDayBefore,
                    enabled = enabled,
                ) { on -> update { it.copy(remindDayBefore = on) } }
            }
        }
    }

    editingId?.let { id ->
        val existing = countdown.events.firstOrNull { it.id == id }
        CountdownEventEditor(
            initial = existing,
            onDismiss = { editingId = null },
            onSave = { event ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                update { CountdownPrefs.upsert(it, event) }
                editingId = null
            },
        )
    }
}

/** Éditeur d'un événement : nom, émoji, date (DatePicker), heure optionnelle (TimePicker), récurrence, couleur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownEventEditor(initial: CountdownEvent?, onDismiss: () -> Unit, onSave: (CountdownEvent) -> Unit) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji ?: CountdownEvent.DEFAULT_EMOJI) }
    var dateText by rememberSaveable { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(7).toString()) }
    var timeText by rememberSaveable { mutableStateOf(initial?.time) }
    var yearly by rememberSaveable { mutableStateOf(initial?.yearly ?: false) }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: CountdownEvent.DEFAULT_COLOR) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val date = CountdownMath.parseDate(dateText) ?: LocalDate.now()
    val time = CountdownMath.parseTime(timeText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.countdown_editor_new else R.string.countdown_editor_edit)) },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        CountdownEvent(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            emoji = emoji.trim().ifEmpty { CountdownEvent.DEFAULT_EMOJI },
                            date = date.toString(),
                            time = time?.toString(),
                            yearly = yearly,
                            color = color,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.countdown_editor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(8) },
                        label = { Text(stringResource(R.string.countdown_editor_emoji)) },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        CountdownEvent.EMOJIS.forEach { e ->
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }
                AssistChip(
                    onClick = { picker = "date" },
                    label = { Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.countdown_editor_time), modifier = Modifier.weight(1f))
                    Switch(checked = time != null, onCheckedChange = { on -> if (on) picker = "time" else timeText = null })
                }
                if (time != null) {
                    AssistChip(
                        onClick = { picker = "time" },
                        label = { Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.countdown_editor_yearly))
                        Text(stringResource(R.string.countdown_editor_yearly_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = yearly, onCheckedChange = { yearly = it })
                }
                Text(stringResource(R.string.countdown_editor_color), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountdownEvent.COLORS.forEach { c ->
                        val selected = c == color
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable(role = Role.RadioButton) { color = c },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
    )

    when (picker) {
        "date" -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                        picker = null
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
            ) { DatePicker(state = state) }
        }
        "time" -> {
            val start = time ?: LocalTime.of(9, 0)
            val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
            AlertDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        timeText = LocalTime.of(state.hour, state.minute).toString()
                        picker = null
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
                text = { TimePicker(state = state) },
            )
        }
    }
}
