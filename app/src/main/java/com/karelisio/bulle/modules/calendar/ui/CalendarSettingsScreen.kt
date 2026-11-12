package com.karelisio.bulle.modules.calendar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.calendar.CalendarEvent
import com.karelisio.bulle.modules.calendar.CalendarModule
import com.karelisio.bulle.modules.calendar.CalendarPlanner
import com.karelisio.bulle.modules.calendar.CalendarSettings
import com.karelisio.bulle.modules.calendar.CalendarSettingsBinding
import com.karelisio.bulle.modules.calendar.CalendarSource
import com.karelisio.bulle.modules.calendar.timeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Réglages du module « Agenda ». La permission d'agenda est demandée à l'activation. */
@Composable
fun CalendarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val calendar = CalendarSettingsBinding.read(settings)
    val enabled = CalendarModule.isEnabled(settings)

    // Permission relue à chaque retour sur l'écran (elle peut être retirée dans les paramètres).
    var granted by remember { mutableStateOf(CalendarSource.hasPermission(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        granted = CalendarSource.hasPermission(context)
        refresh++
        onPauseOrDispose { }
    }

    fun setEnabled(on: Boolean) {
        scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (CalendarModule.ID to on)) } }
    }

    fun update(transform: (CalendarSettings) -> CalendarSettings) {
        scope.launch {
            SettingsRepository.update(context) { CalendarSettingsBinding.write(it, transform(CalendarSettingsBinding.read(it))) }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) setEnabled(true)
    }

    val next by produceState<CalendarEvent?>(null, granted, refresh) {
        value = if (!granted) null else withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            CalendarPlanner.nextEvent(CalendarSource.query(context, now, now + CalendarPlanner.HORIZON_MS), now)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.calendar_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.calendar_settings_back))
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
                        title = stringResource(R.string.calendar_settings_enable),
                        summary = stringResource(R.string.calendar_settings_enable_summary),
                        checked = enabled && granted,
                    ) { on ->
                        if (on && !granted) permission.launch(Manifest.permission.READ_CALENDAR) else setEnabled(on)
                    }
                    if (enabled && !granted) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.calendar_settings_permission_missing)) },
                            trailingContent = {
                                TextButton(onClick = { permission.launch(Manifest.permission.READ_CALENDAR) }) {
                                    Text(stringResource(R.string.calendar_settings_permission_grant))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.calendar_settings_reminder_header)) }
            item {
                SettingsCard {
                    Text(
                        stringResource(R.string.calendar_settings_reminder_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                    val choices = CalendarSettings.REMINDER_CHOICES
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                        choices.forEachIndexed { i, minutes ->
                            SegmentedButton(
                                selected = calendar.effectiveReminderMinutes == minutes,
                                onClick = { update { it.copy(reminderMinutes = minutes) } },
                                shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                                enabled = enabled,
                            ) { Text(stringResource(R.string.calendar_minutes_short, minutes.toLong()), maxLines = 1) }
                        }
                    }
                    SwitchRow(
                        title = stringResource(R.string.calendar_settings_ongoing),
                        summary = stringResource(R.string.calendar_settings_ongoing_summary),
                        checked = calendar.showOngoing,
                        enabled = enabled,
                    ) { on -> update { it.copy(showOngoing = on) } }
                }
            }
            item { SectionHeader(stringResource(R.string.calendar_settings_next_header)) }
            item {
                SettingsCard {
                    val event = next
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                        headlineContent = {
                            Text(
                                when {
                                    !granted -> stringResource(R.string.calendar_settings_next_no_permission)
                                    event == null -> stringResource(R.string.calendar_settings_next_none)
                                    else -> event.title.ifEmpty { stringResource(R.string.calendar_untitled) }
                                },
                            )
                        },
                        supportingContent = event?.let { e ->
                            { Text(listOfNotNull(timeRange(context, e), e.location).joinToString(" · ")) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item { FootNote(stringResource(R.string.calendar_settings_privacy)) }
        }
    }
}
