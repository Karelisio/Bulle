package com.karelisio.bulle.modules.halo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.halo.HaloAppFilter
import com.karelisio.bulle.modules.halo.HaloBus
import com.karelisio.bulle.modules.halo.HaloCanvas
import com.karelisio.bulle.modules.halo.HaloColorMode
import com.karelisio.bulle.modules.halo.HaloCamera
import com.karelisio.bulle.modules.halo.HaloDirection
import com.karelisio.bulle.modules.halo.HaloStart
import com.karelisio.bulle.modules.halo.HaloKind
import com.karelisio.bulle.modules.halo.HaloLook
import com.karelisio.bulle.modules.halo.HaloRect
import com.karelisio.bulle.modules.halo.HaloRequest
import com.karelisio.bulle.modules.halo.HaloScreen
import com.karelisio.bulle.modules.halo.HaloSettings
import com.karelisio.bulle.modules.halo.HaloStyle
import com.karelisio.bulle.ui.components.ModuleCard
import com.karelisio.bulle.ui.components.ModuleChoices
import com.karelisio.bulle.ui.components.ModuleFormat
import com.karelisio.bulle.ui.components.ModuleNote
import com.karelisio.bulle.ui.components.ModuleSection
import com.karelisio.bulle.ui.components.ModuleSettingsScaffold
import com.karelisio.bulle.ui.components.ModuleSlider
import com.karelisio.bulle.ui.components.ModuleSwitch
import com.karelisio.bulle.ui.components.ModuleWarning
import com.karelisio.bulle.ui.settings.AppList
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Couleurs proposées (couleur fixe, couleur par appli). */
internal val HaloSwatches: List<Long> = listOf(
    0xFF7FD3FF, 0xFFB4C5FF, 0xFFD0BCFF, 0xFFFF8FB8, 0xFFFF6FA5, 0xFFFFB36B,
    0xFFFFE07A, 0xFF8BE3A0, 0xFF5BE38A, 0xFF7FE7D7, 0xFFFFFFFF, 0xFFFF6B6B,
)

@Composable
fun haloStyleLabel(style: HaloStyle): String = stringResource(
    when (style) {
        HaloStyle.SIGNATURE -> R.string.halo_style_signature
        HaloStyle.STROKE -> R.string.halo_style_stroke
        HaloStyle.PULSE -> R.string.halo_style_pulse
        HaloStyle.WAVE -> R.string.halo_style_wave
        HaloStyle.GRADIENT -> R.string.halo_style_gradient
        HaloStyle.SPARKLES -> R.string.halo_style_sparkles
    },
)

private enum class Picking { FILTER_APP, COLOR_APP }

/** Réglages du Halo (bords lumineux), avec aperçu en direct. */
@Composable
fun HaloSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val saved = settings.halo
    // Brouillon : suit les curseurs en direct (aperçu) avant l'enregistrement au lâcher.
    var draft by remember(saved) { mutableStateOf(saved) }
    val running by HaloBus.running.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Picking?>(null) }
    var colorFor by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = AppCatalog.apps(context) }

    fun update(t: (HaloSettings) -> HaloSettings) {
        draft = t(draft)
        scope.launch { SettingsRepository.update(context) { it.copy(halo = t(it.halo)) } }
    }

    ModuleSettingsScaffold(stringResource(R.string.halo_title), onBack) {
        item("enable") {
            ModuleCard {
                ModuleSwitch(stringResource(R.string.halo_enable), stringResource(R.string.halo_enable_summary), saved.enabled) { on ->
                    update { it.copy(enabled = on) }
                }
            }
        }
        item("preview") {
            ModuleCard {
                HaloPreview(draft, Modifier.padding(16.dp))
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { HaloBus.test(draft.style) }, enabled = running) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.halo_test_screen), Modifier.padding(start = 6.dp))
                    }
                }
                if (!running) ModuleNote(stringResource(R.string.halo_service_off))
            }
        }
        item("style") {
            ModuleSection(stringResource(R.string.halo_section_style))
            ModuleCard {
                ModuleChoices(HaloStyle.entries, draft.style, { haloStyleLabel(it) }, Modifier.padding(top = 8.dp)) { st ->
                    update { it.copy(style = st) }
                }
                ModuleSlider(
                    stringResource(R.string.halo_thickness), draft.thicknessDp, HaloSettings.THICKNESS, ModuleFormat::dp,
                    onPreview = { v -> draft = draft.copy(thicknessDp = v) },
                ) { v -> update { it.copy(thicknessDp = v) } }
                ModuleSlider(
                    stringResource(R.string.halo_duration), draft.durationMs.toFloat(),
                    HaloSettings.DURATION.first.toFloat()..HaloSettings.DURATION.last.toFloat(), ModuleFormat::seconds,
                    onPreview = { v -> draft = draft.copy(durationMs = v.roundToInt()) },
                ) { v -> update { it.copy(durationMs = v.roundToInt()) } }
                ModuleSlider(
                    stringResource(R.string.halo_passes), draft.passes.toFloat(), 1f..5f, { "${it.roundToInt()}" }, steps = 3,
                    onPreview = { v -> draft = draft.copy(passes = v.roundToInt()) },
                ) { v -> update { it.copy(passes = v.roundToInt()) } }
                ModuleSlider(
                    stringResource(R.string.halo_intensity), draft.intensity, HaloSettings.INTENSITY, ModuleFormat::percent,
                    onPreview = { v -> draft = draft.copy(intensity = v) },
                ) { v -> update { it.copy(intensity = v) } }
                ModuleSlider(
                    stringResource(R.string.halo_speed), draft.speed, HaloSettings.SPEED, ModuleFormat::times,
                    onPreview = { v -> draft = draft.copy(speed = v) },
                ) { v -> update { it.copy(speed = v) } }
            }
        }
        item("shape") {
            ModuleSection(stringResource(R.string.halo_section_shape))
            ModuleCard {
                ModuleSlider(
                    stringResource(R.string.halo_trail), draft.trail, HaloSettings.TRAIL, ModuleFormat::percent,
                    onPreview = { v -> draft = draft.copy(trail = v) },
                ) { v -> update { it.copy(trail = v) } }
                ModuleSlider(
                    stringResource(R.string.halo_glow), draft.glow, 0f..1f, ModuleFormat::percent,
                    onPreview = { v -> draft = draft.copy(glow = v) },
                ) { v -> update { it.copy(glow = v) } }
                Text(stringResource(R.string.halo_direction), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ModuleChoices(
                    HaloDirection.entries, draft.direction,
                    {
                        stringResource(
                            when (it) {
                                HaloDirection.BOTH -> R.string.halo_dir_both
                                HaloDirection.CLOCKWISE -> R.string.halo_dir_cw
                                HaloDirection.COUNTER -> R.string.halo_dir_ccw
                            },
                        )
                    },
                ) { d -> update { it.copy(direction = d) } }
                Text(stringResource(R.string.halo_start), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ModuleChoices(
                    HaloStart.entries, draft.start,
                    { stringResource(if (it == HaloStart.CAMERA) R.string.halo_start_camera else R.string.halo_start_bottom) },
                ) { st -> update { it.copy(start = st) } }
                Text(stringResource(R.string.halo_camera), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ModuleChoices(
                    HaloCamera.entries, draft.camera,
                    {
                        stringResource(
                            when (it) {
                                HaloCamera.EMERGE -> R.string.halo_camera_emerge
                                HaloCamera.AROUND -> R.string.halo_camera_around
                                HaloCamera.IGNORE -> R.string.halo_camera_ignore
                            },
                        )
                    },
                ) { c -> update { it.copy(camera = c) } }
                if (draft.camera != HaloCamera.IGNORE) {
                    ModuleSlider(
                        stringResource(R.string.halo_camera_gap), draft.cameraGapDp, HaloSettings.CAMERA_GAP, ModuleFormat::dp,
                        onPreview = { v -> draft = draft.copy(cameraGapDp = v) },
                    ) { v -> update { it.copy(cameraGapDp = v) } }
                }
                if (draft.style == HaloStyle.SIGNATURE) {
                    ModuleSwitch(stringResource(R.string.halo_burst), null, draft.burst) { on -> update { it.copy(burst = on) } }
                    if (draft.burst) {
                        ModuleSlider(
                            stringResource(R.string.halo_burst_size), draft.burstSize, HaloSettings.BURST_SIZE, ModuleFormat::times,
                            onPreview = { v -> draft = draft.copy(burstSize = v) },
                        ) { v -> update { it.copy(burstSize = v) } }
                    }
                }
            }
        }
        item("color") {
            ModuleSection(stringResource(R.string.halo_section_color))
            ModuleCard {
                ModuleChoices(
                    HaloColorMode.entries, draft.colorMode,
                    {
                        stringResource(
                            when (it) {
                                HaloColorMode.APP_ICON -> R.string.halo_color_icon
                                HaloColorMode.DYNAMIC -> R.string.halo_color_dynamic
                                HaloColorMode.FIXED -> R.string.halo_color_fixed
                            },
                        )
                    },
                    Modifier.padding(top = 8.dp),
                ) { m -> update { it.copy(colorMode = m) } }
                if (draft.colorMode == HaloColorMode.FIXED) {
                    Swatches(draft.fixedColor, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { c -> update { it.copy(fixedColor = c) } }
                }
                ModuleSwitch(stringResource(R.string.halo_second_color), stringResource(R.string.halo_second_color_summary), draft.secondColor != null) { on ->
                    update { it.copy(secondColor = if (on) 0xFFFF8FB8 else null) }
                }
                draft.secondColor?.let { second ->
                    Swatches(second, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { c -> update { it.copy(secondColor = c) } }
                }
                ModuleSwitch(stringResource(R.string.halo_artwork), stringResource(R.string.halo_artwork_summary), draft.artworkForMusic) { on ->
                    update { it.copy(artworkForMusic = on) }
                }
                ModuleSwitch(stringResource(R.string.halo_track), stringResource(R.string.halo_track_summary), draft.trackChange) { on ->
                    update { it.copy(trackChange = on) }
                }
                ModuleSwitch(stringResource(R.string.halo_echo), stringResource(R.string.halo_echo_summary), draft.echoHeart) { on ->
                    update { it.copy(echoHeart = on) }
                }
                AppsHeader(stringResource(R.string.halo_app_colors), stringResource(R.string.halo_app_colors_summary)) { picking = Picking.COLOR_APP }
                draft.appColors.toSortedMap().forEach { (pkg, argb) ->
                    AppLine(apps.firstOrNull { it.packageName == pkg }, pkg, color = Color(argb.toInt()), onClick = { colorFor = pkg }) {
                        update { it.copy(appColors = it.appColors - pkg) }
                    }
                }
            }
        }
        item("filters") {
            ModuleSection(stringResource(R.string.halo_section_filters))
            ModuleCard {
                ModuleChoices(
                    HaloAppFilter.entries, draft.appFilter,
                    { stringResource(if (it == HaloAppFilter.ALL) R.string.halo_apps_all else R.string.halo_apps_selected) },
                    Modifier.padding(top = 8.dp),
                ) { f -> update { it.copy(appFilter = f) } }
                if (draft.appFilter == HaloAppFilter.SELECTED) {
                    AppsHeader(stringResource(R.string.halo_apps_list), stringResource(R.string.halo_apps_list_summary)) { picking = Picking.FILTER_APP }
                    draft.selectedApps.sorted().forEach { pkg ->
                        AppLine(apps.firstOrNull { it.packageName == pkg }, pkg) { update { it.copy(selectedApps = it.selectedApps - pkg) } }
                    }
                }
                ModuleSwitch(stringResource(R.string.halo_conversations), null, draft.conversationsOnly) { on -> update { it.copy(conversationsOnly = on) } }
                ModuleSwitch(stringResource(R.string.halo_ignore_silent), null, draft.ignoreSilent) { on -> update { it.copy(ignoreSilent = on) } }
                ModuleSwitch(stringResource(R.string.halo_ignore_ongoing), null, draft.ignoreOngoing) { on -> update { it.copy(ignoreOngoing = on) } }
                ModuleSwitch(stringResource(R.string.halo_dnd), stringResource(R.string.halo_dnd_summary), draft.muteInDnd) { on -> update { it.copy(muteInDnd = on) } }
                ModuleNote(stringResource(R.string.halo_visibility_note))
            }
        }
        item("screen_off") {
            ModuleSection(stringResource(R.string.halo_section_screen_off))
            ModuleCard {
                ModuleSwitch(stringResource(R.string.halo_screen_off), stringResource(R.string.halo_screen_off_summary), draft.screenOff) { on ->
                    update { it.copy(screenOff = on) }
                }
                if (draft.screenOff) {
                    ModuleSlider(
                        stringResource(R.string.halo_screen_off_seconds), draft.screenOffSeconds.toFloat(), 2f..8f, { "${it.roundToInt()} s" }, steps = 5,
                    ) { v -> update { it.copy(screenOffSeconds = v.roundToInt()) } }
                    ModuleSlider(
                        stringResource(R.string.halo_screen_off_rate), draft.screenOffMaxPerHour.toFloat(), 1f..20f,
                        { context.getString(R.string.halo_per_hour, it.roundToInt()) }, steps = 18,
                    ) { v -> update { it.copy(screenOffMaxPerHour = v.roundToInt()) } }
                    ModuleWarning(stringResource(R.string.halo_screen_off_warning))
                }
            }
        }
        item("charge") {
            ModuleSection(stringResource(R.string.halo_section_charge))
            ModuleCard {
                ModuleSwitch(stringResource(R.string.halo_charge), stringResource(R.string.halo_charge_summary), draft.chargingHalo) { on ->
                    update { it.copy(chargingHalo = on) }
                }
            }
        }
    }

    picking?.let { target ->
        ModalBottomSheet(onDismissRequest = { picking = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AppList { app ->
                when (target) {
                    Picking.FILTER_APP -> update { it.copy(selectedApps = it.selectedApps + app.packageName) }
                    Picking.COLOR_APP -> colorFor = app.packageName
                }
                picking = null
            }
        }
    }
    colorFor?.let { pkg ->
        AlertDialog(
            onDismissRequest = { colorFor = null },
            title = { Text(apps.firstOrNull { it.packageName == pkg }?.label ?: pkg) },
            text = {
                Swatches(draft.appColors[pkg] ?: -1L) { c ->
                    update { it.copy(appColors = it.appColors + (pkg to c)) }
                    colorFor = null
                }
            },
            confirmButton = { TextButton(onClick = { colorFor = null }) { Text(stringResource(R.string.halo_close)) } },
        )
    }
}

/**
 * Aperçu : téléphone miniature (coins arrondis, caméra) sur fond noir, le halo y tourne en boucle
 * avec le brouillon des réglages.
 */
@Composable
internal fun HaloPreview(draft: HaloSettings, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val color = when (draft.colorMode) {
        HaloColorMode.FIXED -> draft.fixedColor.toInt()
        else -> scheme.primary.toArgb()
    }
    val density = LocalDensity.current.density
    val request = remember(draft.style, color) { HaloRequest(HaloKind.TEST, color, draft.style) }
    val description = stringResource(R.string.halo_preview_desc)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .width(150.dp)
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(22.dp))
                .semantics { contentDescription = description },
        ) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val r = 22f * density
            val cam = 9f * density
            val screen = remember(w, h) {
                HaloScreen(
                    w, h, r, r, r, r,
                    cutout = HaloRect(w / 2 - cam / 2, 8f * density, w / 2 + cam / 2, 8f * density + cam),
                    density = density,
                )
            }
            // Épaisseur réduite à l'échelle de la miniature.
            val look = HaloLook.of(draft, density * 0.6f)
            HaloCanvas(request, screen, look, loop = true)
        }
    }
}

@Composable
private fun Swatches(selected: Long, modifier: Modifier = Modifier, onPick: (Long) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HaloSwatches.forEach { c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c.toInt()))
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun AppsHeader(title: String, summary: String, onAdd: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            TextButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.deck_add))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AppLine(app: AppEntry?, pkg: String, color: Color? = null, onClick: (() -> Unit)? = null, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(app?.label ?: pkg) },
        leadingContent = { if (app != null) AppIcon(app, 36.dp) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                color?.let { Box(Modifier.size(22.dp).clip(CircleShape).background(it)) }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.visibility_remove_app, app?.label ?: pkg))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
