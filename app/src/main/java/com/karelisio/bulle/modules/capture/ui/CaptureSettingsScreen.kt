package com.karelisio.bulle.modules.capture.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.karelisio.bulle.modules.capture.CaptureActions
import com.karelisio.bulle.modules.capture.CapturePrefs
import com.karelisio.bulle.modules.capture.CaptureSettings
import com.karelisio.bulle.modules.capture.CaptureState
import com.karelisio.bulle.modules.capture.MemoQuality
import com.karelisio.bulle.modules.capture.VideoQuality
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow

/**
 * Réglages « Capture » : scanner QR, mémos vocaux (accès à la liste) et enregistreur d'écran.
 * La liste des mémos s'ouvre dans le même écran (aucune route supplémentaire nécessaire).
 */
@Composable
fun CaptureSettingsScreen(onBack: () -> Unit) {
    var showMemos by rememberSaveable { mutableStateOf(false) }
    if (showMemos) {
        androidx.activity.compose.BackHandler { showMemos = false }
        MemosScreen(onBack = { showMemos = false })
    } else {
        CaptureSettingsContent(onBack = onBack, onOpenMemos = { showMemos = true })
    }
}

@Composable
private fun CaptureSettingsContent(onBack: () -> Unit, onOpenMemos: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings by remember(context) { CapturePrefs.loaded(context) }.collectAsStateWithLifecycle()
    val screen by CaptureState.screen.collectAsStateWithLifecycle()
    fun update(t: (CaptureSettings) -> CaptureSettings) = CapturePrefs.update(context, t)

    ModuleSettingsScaffold(title = stringResource(R.string.capture_settings_title), onBack = onBack) {
        // ---- Scanner QR ----
        item { ModuleSectionHeader(stringResource(R.string.capture_section_qr)) }
        item {
            ModuleSettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.capture_qr_scan)) },
                    supportingContent = { Text(stringResource(R.string.capture_qr_scan_summary)) },
                    leadingContent = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                    trailingContent = {
                        FilledTonalButton(onClick = { CaptureActions.scanQr(context) }) { Text(stringResource(R.string.capture_qr_scan_now)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ModuleSwitchRow(
                    title = stringResource(R.string.capture_qr_haptics),
                    summary = null,
                    checked = settings.qrHaptics,
                ) { on -> update { it.copy(qrHaptics = on) } }
            }
        }

        // ---- Mémos vocaux ----
        item { ModuleSectionHeader(stringResource(R.string.capture_section_memo)) }
        item {
            ModuleSettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.capture_memos_title)) },
                    supportingContent = { Text(stringResource(R.string.capture_memos_summary)) },
                    leadingContent = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onOpenMemos() },
                )
                ChoiceRow(
                    title = stringResource(R.string.capture_memo_quality),
                    options = listOf(MemoQuality.STANDARD to R.string.capture_quality_standard, MemoQuality.HIGH to R.string.capture_quality_high),
                    selected = settings.memoQuality,
                ) { q -> update { it.copy(memoQuality = q) } }
                ModuleSwitchRow(
                    title = stringResource(R.string.capture_memo_music),
                    summary = stringResource(R.string.capture_memo_music_summary),
                    checked = settings.memoToMusic,
                ) { on -> update { it.copy(memoToMusic = on) } }
            }
        }

        // ---- Enregistreur d'écran ----
        item { ModuleSectionHeader(stringResource(R.string.capture_section_screen)) }
        item {
            ModuleSettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.capture_screen_title)) },
                    supportingContent = { Text(stringResource(R.string.capture_screen_summary)) },
                    leadingContent = { Icon(Icons.Rounded.Videocam, contentDescription = null) },
                    trailingContent = {
                        FilledTonalButton(onClick = { CaptureActions.toggleScreenRecord(context) }) {
                            Text(stringResource(if (screen.active) R.string.capture_stop else R.string.capture_record))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ChoiceRow(
                    title = stringResource(R.string.capture_screen_quality),
                    options = listOf(
                        VideoQuality.NATIVE to R.string.capture_quality_native,
                        VideoQuality.HD to R.string.capture_quality_720,
                        VideoQuality.SD to R.string.capture_quality_480,
                    ),
                    selected = settings.videoQuality,
                ) { q -> update { it.copy(videoQuality = q) } }
                ModuleSwitchRow(
                    title = stringResource(R.string.capture_screen_audio),
                    summary = stringResource(R.string.capture_screen_audio_summary),
                    checked = settings.screenMicAudio,
                ) { on -> update { it.copy(screenMicAudio = on) } }
            }
        }
        item {
            Text(
                stringResource(R.string.capture_settings_footer),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
