package com.karelisio.bulle.modules.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.modules.translate.TranslateLanguages
import com.karelisio.bulle.modules.translate.TranslatePanel
import com.karelisio.bulle.modules.translate.TranslatePrefs
import com.karelisio.bulle.modules.translate.TranslateSettings
import com.karelisio.bulle.modules.translate.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Réglages de la traduction : langue cible, téléchargement en Wi-Fi, modèles hors ligne (état, suppression). */
@Composable
fun TranslateSettingsScreen(onBack: () -> Unit) = TranslateSettingsContent(onBack, TranslatePanel.engine)

@Composable
internal fun TranslateSettingsContent(onBack: () -> Unit, engine: TranslationEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by remember(context) { TranslatePrefs.flow(context) }.collectAsStateWithLifecycle(TranslateSettings())
    val supported = remember(engine) { engine.languages }
    var downloaded by remember { mutableStateOf<Set<String>?>(null) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var failed by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(version) { downloaded = runCatching { engine.downloaded() }.getOrNull() ?: emptySet() }
    val target = TranslateLanguages.target(settings, supported)

    fun update(transform: (TranslateSettings) -> TranslateSettings) {
        scope.launch { TranslatePrefs.update(context, transform) }
    }

    fun act(code: String, block: suspend () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        busy = busy + code
        failed = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = code
            } finally {
                busy = busy - code
                version++
            }
        }
    }

    ModuleSettingsScaffold(title = stringResource(R.string.translate_settings_title), onBack = onBack) {
        item {
            Text(
                stringResource(R.string.translate_settings_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    stringResource(R.string.translate_settings_phone_language),
                    stringResource(R.string.translate_settings_phone_language_summary, TranslateLanguages.displayName(TranslateLanguages.target(TranslateSettings(), supported))),
                    settings.targetLanguage == null,
                ) { on -> update { it.copy(targetLanguage = if (on) null else target) } }
                ModuleSwitchRow(
                    stringResource(R.string.translate_settings_wifi),
                    stringResource(R.string.translate_settings_wifi_summary),
                    settings.wifiOnly,
                ) { on -> update { it.copy(wifiOnly = on) } }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.translate_settings_models)) }
        item {
            ModuleSettingsCard {
                val languages = TranslateLanguages.ordered(supported).let { all ->
                    if (showAll) all else all.filter { it in TranslateLanguages.FAVORITES || it == target || downloaded?.contains(it) == true }
                }
                languages.forEachIndexed { i, code ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    val have = downloaded?.contains(code) == true || code == "en"
                    ListItem(
                        headlineContent = { Text(TranslateLanguages.displayName(code)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    when {
                                        failed == code -> R.string.translate_settings_model_failed
                                        code in busy -> R.string.translate_settings_model_busy
                                        code == "en" -> R.string.translate_settings_model_builtin
                                        have -> R.string.translate_settings_model_ready
                                        else -> R.string.translate_settings_model_absent
                                    },
                                ),
                            )
                        },
                        leadingContent = if (code == target) {
                            { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.translate_settings_target), tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        trailingContent = {
                            when {
                                code in busy -> CircularWavyProgressIndicator(Modifier.padding(4.dp))
                                code == "en" -> Unit
                                have -> IconButton(onClick = { act(code) { engine.delete(code) } }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.translate_settings_delete))
                                }
                                else -> FilledTonalIconButton(onClick = { act(code) { engine.download(code, settings.wifiOnly) } }) {
                                    Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.translate_download))
                                }
                            }
                        },
                        // Toucher une langue la choisit comme langue cible.
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            update { it.copy(targetLanguage = code) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        if (!showAll) {
            item {
                androidx.compose.material3.TextButton(onClick = { showAll = true }) {
                    Text(stringResource(R.string.translate_settings_show_all, supported.size))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.translate_settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
