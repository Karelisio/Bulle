package com.karelisio.bulle.modules.profiles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.profiles.BulleProfile
import com.karelisio.bulle.modules.profiles.ProfileField
import com.karelisio.bulle.modules.profiles.Profiles
import com.karelisio.bulle.ui.components.ModuleCard
import com.karelisio.bulle.ui.components.ModuleChoices
import com.karelisio.bulle.ui.components.ModuleNote
import com.karelisio.bulle.ui.components.ModuleSettingsScaffold
import kotlinx.coroutines.launch

private val Emojis = listOf("💼", "🌙", "🎮", "🏠", "🚗", "🏃", "📚", "🎧", "✈️", "⭐", "🔕", "✨")

@Composable
private fun fieldLabel(f: ProfileField): String = stringResource(
    when (f) {
        ProfileField.ISLAND -> R.string.profile_f_island
        ProfileField.HALO -> R.string.halo_title
        ProfileField.EDGES -> R.string.edges_title
        ProfileField.VOLET -> R.string.home_entry_volet
        ProfileField.STANDBY -> R.string.standby_title
        ProfileField.ALERTS -> R.string.home_entry_alerts
        ProfileField.HAPTICS -> R.string.profile_f_haptics
    },
)

/**
 * Profils : chacun change plusieurs réglages d'un coup (île, Halo, Bords, Volet, Veille, alertes,
 * vibrations). Applicables ici, par un geste, une règle (« Quand… appliquer le profil »).
 */
@Composable
fun ProfilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val profiles = settings.profiles
    var editing by remember { mutableStateOf<BulleProfile?>(null) }

    fun save(t: (List<BulleProfile>) -> List<BulleProfile>) {
        scope.launch { SettingsRepository.update(context) { it.copy(profiles = it.profiles.copy(list = t(it.profiles.list))) } }
    }

    ModuleSettingsScaffold(stringResource(R.string.profiles_title), onBack) {
        item("intro") { ModuleNote(stringResource(R.string.profiles_intro)) }
        profiles.list.forEach { p ->
            item(p.id) {
                val active = profiles.activeId == p.id
                ModuleCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.emoji, style = MaterialTheme.typography.headlineMedium)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                val changes = ProfileField.entries.mapNotNull { f -> Profiles.get(p, f)?.let { f to it } }
                                val labels = changes.map { (f, on) -> (if (on) "✓ " else "✕ ") + fieldLabel(f) }
                                Text(
                                    if (changes.isEmpty()) stringResource(R.string.profile_no_change) else labels.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editing = p }) { Icon(Icons.Rounded.Edit, stringResource(R.string.profile_edit, p.name)) }
                            IconButton(onClick = { save { list -> list.filterNot { it.id == p.id } } }) {
                                Icon(Icons.Rounded.Delete, stringResource(R.string.profile_delete, p.name))
                            }
                        }
                        if (active) {
                            FilledTonalButton(onClick = { Profiles.apply(context, p.id) }, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.profile_active))
                            }
                        } else {
                            Button(onClick = { Profiles.apply(context, p.id) }, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.profile_apply))
                            }
                        }
                    }
                }
            }
        }
        item("add") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { editing = BulleProfile(Profiles.newId(), context.getString(R.string.profile_new_name)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.profile_new), Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = {
                    val p = Profiles.snapshot(settings, Profiles.newId(), context.getString(R.string.profile_snapshot_name))
                    save { it + p }
                }) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                    Text(stringResource(R.string.profile_snapshot), Modifier.padding(start = 6.dp))
                }
            }
            ModuleNote(stringResource(R.string.profiles_rules_note))
        }
    }

    editing?.let { initial ->
        var draft by remember(initial.id) { mutableStateOf(initial) }
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(24)) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Emojis.forEach { e ->
                        FilterChip(selected = draft.emoji == e, onClick = { draft = draft.copy(emoji = e) }, label = { Text(e) })
                    }
                }
                ProfileField.entries.forEach { f ->
                    Text(fieldLabel(f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    ModuleChoices(
                        listOf(null, true, false), Profiles.get(draft, f),
                        {
                            stringResource(
                                when (it) {
                                    null -> R.string.profile_unchanged
                                    true -> R.string.profile_on
                                    false -> R.string.profile_off
                                },
                            )
                        },
                        Modifier.padding(start = 0.dp),
                    ) { v -> draft = Profiles.set(draft, f, v) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.halo_close)) }
                    Button(onClick = {
                        val p = draft.copy(name = draft.name.ifBlank { context.getString(R.string.profile_new_name) })
                        save { list -> if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p }
                        editing = null
                    }) { Text(stringResource(R.string.profile_save)) }
                }
            }
        }
    }
}
