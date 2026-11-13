package com.karelisio.bulle.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.a11y.rememberSystemHighContrast
import com.karelisio.bulle.core.settings.AccessibilitySettings
import com.karelisio.bulle.core.settings.IslandAnnouncements
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.ui.components.radioRow
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Bornes de la taille du texte de l'île (même plage que dans Style). */
private const val MinTextScale = 0.85f
private const val MaxTextScale = 1.25f

/**
 * Écran « Accessibilité » : contraste élevé, taille du texte de l'île, annonces TalkBack,
 * état de « Supprimer les animations » et lien vers les réglages d'accessibilité d'Android.
 */
@Composable
fun AccessibilitySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(context) { SettingsRepository.flow(context) }
    val settings by flow.collectAsState(initial = null)
    val a11y = settings?.accessibility ?: AccessibilitySettings()
    val textScale = settings?.style?.textScale ?: 1f
    val systemContrast by rememberSystemHighContrast()
    val reduceMotion = rememberReduceMotion()

    fun edit(transform: (AccessibilitySettings) -> AccessibilitySettings) {
        scope.launch { SettingsRepository.update(context) { it.copy(accessibility = transform(it.accessibility)) } }
    }

    ModuleSettingsScaffold(stringResource(R.string.a11y_settings_title), onBack) {
        item { ModuleSectionHeader(stringResource(R.string.a11y_section_display)) }
        item {
            ModuleSettingsCard {
                Column {
                    ModuleSwitchRow(
                        stringResource(R.string.a11y_high_contrast),
                        stringResource(R.string.a11y_high_contrast_summary),
                        a11y.highContrast,
                    ) { on -> edit { it.copy(highContrast = on) } }
                    ModuleSwitchRow(
                        stringResource(R.string.a11y_follow_system),
                        if (systemContrast) stringResource(R.string.a11y_system_contrast_active)
                        else stringResource(R.string.a11y_follow_system_summary),
                        a11y.followSystemContrast,
                    ) { on -> edit { it.copy(followSystemContrast = on) } }
                    ModuleSwitchRow(
                        stringResource(R.string.a11y_island_outline),
                        stringResource(R.string.a11y_island_outline_summary),
                        a11y.islandOutline,
                        enabled = a11y.highContrastActive(systemContrast),
                    ) { on -> edit { it.copy(islandOutline = on) } }
                }
            }
        }
        item {
            ModuleSettingsCard {
                TextScaleRow(textScale) { v ->
                    scope.launch { SettingsRepository.update(context) { it.copy(style = it.style.copy(textScale = v)) } }
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.a11y_section_talkback)) }
        item {
            ModuleSettingsCard {
                Column(Modifier.padding(vertical = 4.dp).selectableGroup()) {
                    Text(
                        stringResource(R.string.a11y_announcements),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                    AnnouncementOption(IslandAnnouncements.ALL, R.string.a11y_announce_all, R.string.a11y_announce_all_summary, a11y.announcements) { edit { it.copy(announcements = IslandAnnouncements.ALL) } }
                    AnnouncementOption(IslandAnnouncements.IMPORTANT, R.string.a11y_announce_important, R.string.a11y_announce_important_summary, a11y.announcements) { edit { it.copy(announcements = IslandAnnouncements.IMPORTANT) } }
                    AnnouncementOption(IslandAnnouncements.NONE, R.string.a11y_announce_none, R.string.a11y_announce_none_summary, a11y.announcements) { edit { it.copy(announcements = IslandAnnouncements.NONE) } }
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.a11y_section_system)) }
        item {
            ModuleSettingsCard {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(if (reduceMotion) R.string.a11y_reduce_motion_on else R.string.a11y_reduce_motion_off))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.a11y_system_settings)) },
                        supportingContent = { Text(stringResource(R.string.a11y_system_settings_summary)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Curseur de taille du texte de l'île, valeur lue en pourcentage par TalkBack. */
@Composable
private fun TextScaleRow(value: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val percent = (local * 100).roundToInt()
    val valueText = stringResource(R.string.a11y_text_size_value, percent)
    val title = stringResource(R.string.a11y_text_size)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.a11y_text_size_summary) + " · " + valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = MinTextScale..MaxTextScale,
            steps = 7,
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = valueText
            },
        )
    }
}

@Composable
private fun AnnouncementOption(
    option: IslandAnnouncements,
    titleRes: Int,
    summaryRes: Int,
    current: IslandAnnouncements,
    onSelect: () -> Unit,
) {
    val selected = option == current
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(summaryRes)) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.radioRow(selected, onClick = onSelect),
    )
}
