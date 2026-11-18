package com.karelisio.bulle.modules.standby.ui

import android.provider.Settings
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.standby.PhotoAlbum
import com.karelisio.bulle.modules.standby.StandbyCharger
import com.karelisio.bulle.modules.standby.StandbyLauncher
import com.karelisio.bulle.modules.standby.StandbyLogic
import com.karelisio.bulle.modules.standby.StandbyNightMode
import com.karelisio.bulle.modules.standby.StandbyPage
import com.karelisio.bulle.modules.standby.StandbyPhotos
import com.karelisio.bulle.modules.standby.StandbySettings
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.ui.components.ModuleCard
import com.karelisio.bulle.ui.components.ModuleChoices
import com.karelisio.bulle.ui.components.ModuleFormat
import com.karelisio.bulle.ui.components.ModuleNote
import com.karelisio.bulle.ui.components.ModuleSection
import com.karelisio.bulle.ui.components.ModuleSettingsScaffold
import com.karelisio.bulle.ui.components.ModuleSlider
import com.karelisio.bulle.ui.components.ModuleSwitch
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun standbyPageLabel(p: StandbyPage): String = stringResource(
    when (p) {
        StandbyPage.CLOCKS -> R.string.standby_page_clocks
        StandbyPage.WIDGETS -> R.string.standby_page_widgets
        StandbyPage.MUSIC -> R.string.standby_page_music
        StandbyPage.PHOTOS -> R.string.standby_page_photos
        StandbyPage.ECHO -> R.string.standby_page_echo
        StandbyPage.SCENE -> R.string.standby_page_scene
    },
)

private fun hhmm(minutes: Float): String {
    val m = (minutes.roundToInt() / 15 * 15).coerceIn(0, 1425)
    return String.format(Locale.getDefault(), "%02d:%02d", m / 60, m % 60)
}

/** Réglages de Veille (mode StandBy), avec aperçu miniature. */
@Composable
fun StandbySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val s = settings.standby
    var albums by remember { mutableStateOf<List<PhotoAlbum>?>(null) }
    var pickingAlbum by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StandbyPage?>(null) }

    fun update(t: (StandbySettings) -> StandbySettings) {
        scope.launch { SettingsRepository.update(context) { it.copy(standby = t(it.standby)) } }
    }

    // Accès aux photos : demandé seulement quand la page Photos est activée.
    val photosPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            update { it.copy(pages = (it.pages + StandbyPage.PHOTOS).distinct()) }
            pickingAlbum = true
        }
    }
    LaunchedEffect(pickingAlbum) { if (pickingAlbum) albums = StandbyPhotos.albums(context) }

    fun togglePage(p: StandbyPage, on: Boolean) {
        if (on && p == StandbyPage.PHOTOS && !StandbyPhotos.granted(context)) {
            photosPermission.launch(StandbyPhotos.permission())
            return
        }
        update { it.copy(pages = if (on) (it.pages + p).distinct() else it.pages - p) }
        if (on && p == StandbyPage.PHOTOS && s.albumId == null) pickingAlbum = true
    }

    ModuleSettingsScaffold(stringResource(R.string.standby_title), onBack) {
        item("enable") {
            ModuleCard {
                ModuleSwitch(stringResource(R.string.standby_enable), stringResource(R.string.standby_enable_summary), s.enabled) { on ->
                    update { it.copy(enabled = on) }
                }
            }
        }
        item("preview") {
            ModuleCard {
                StandbyPreview(s, Modifier.padding(16.dp))
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    FilledTonalButton(onClick = { StandbyLauncher.launchNow(context, reason = "réglages") }) {
                        Icon(Icons.Rounded.Nightlight, contentDescription = null)
                        Text(stringResource(R.string.standby_launch_now), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        item("trigger") {
            ModuleSection(stringResource(R.string.standby_section_trigger))
            ModuleCard {
                ModuleSwitch(stringResource(R.string.standby_auto), stringResource(R.string.standby_auto_summary), s.autoLaunch) { on ->
                    update { it.copy(autoLaunch = on) }
                }
                ModuleSlider(
                    stringResource(R.string.standby_delay), s.delaySeconds.toFloat(), 0f..60f, { "${it.roundToInt()} s" },
                ) { v -> update { it.copy(delaySeconds = v.roundToInt()) } }
                Text(stringResource(R.string.standby_charger), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ModuleChoices(
                    StandbyCharger.entries, s.charger,
                    {
                        stringResource(
                            when (it) {
                                StandbyCharger.ANY -> R.string.standby_charger_any
                                StandbyCharger.WIRED -> R.string.standby_charger_wired
                                StandbyCharger.WIRELESS -> R.string.standby_charger_wireless
                            },
                        )
                    },
                ) { c -> update { it.copy(charger = c) } }
                ModuleSwitch(stringResource(R.string.standby_schedule), stringResource(R.string.standby_schedule_summary), s.scheduleEnabled) { on ->
                    update { it.copy(scheduleEnabled = on) }
                }
                if (s.scheduleEnabled) {
                    ModuleSlider(stringResource(R.string.standby_night_start), s.scheduleStart.toFloat(), 0f..1425f, ::hhmm, steps = 94) { v ->
                        update { it.copy(scheduleStart = v.roundToInt() / 15 * 15) }
                    }
                    ModuleSlider(stringResource(R.string.standby_night_end), s.scheduleEnd.toFloat(), 0f..1425f, ::hhmm, steps = 94) { v ->
                        update { it.copy(scheduleEnd = v.roundToInt() / 15 * 15) }
                    }
                }
                ModuleNote(stringResource(R.string.standby_trigger_note))
                TextButton(
                    onClick = { OemSettings.openSafely(context, Intent(Settings.ACTION_DREAM_SETTINGS)) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.standby_dream_open)) }
            }
        }
        item("pages") {
            ModuleSection(stringResource(R.string.standby_section_pages))
            ModuleCard {
                val ordered = s.pages + StandbyPage.entries.filter { it !in s.pages }
                ordered.forEach { p ->
                    val on = p in s.pages
                    ListItem(
                        headlineContent = { Text(standbyPageLabel(p)) },
                        supportingContent = if (p == StandbyPage.PHOTOS) ({ Text(s.albumName ?: stringResource(R.string.standby_album_none)) }) else null,
                        leadingContent = { Checkbox(checked = on, onCheckedChange = { togglePage(p, it) }) },
                        trailingContent = {
                            if (on) {
                                Row {
                                    IconButton(onClick = { editing = p }) {
                                        Icon(Icons.Rounded.Edit, stringResource(R.string.standby_page_edit, standbyPageLabel(p)))
                                    }
                                    IconButton(onClick = { update { it.copy(pages = StandbyLogic.move(it.pages, p, -1)) } }) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.standby_move_up))
                                    }
                                    IconButton(onClick = { update { it.copy(pages = StandbyLogic.move(it.pages, p, 1)) } }) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.standby_move_down))
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = if (p == StandbyPage.PHOTOS && on) Modifier.clickable { pickingAlbum = true } else Modifier,
                    )
                }
                ModuleNote(stringResource(R.string.standby_pages_note))
            }
        }
        item("scene") {
            ModuleSection(stringResource(R.string.standby_section_scene))
            ModuleCard {
                StandbySceneSettings(s.scene) { sc -> update { it.copy(scene = sc) } }
            }
        }
        item("night") {
            ModuleSection(stringResource(R.string.standby_section_night))
            ModuleCard {
                ModuleChoices(
                    StandbyNightMode.entries, s.nightMode,
                    {
                        stringResource(
                            when (it) {
                                StandbyNightMode.AUTO -> R.string.standby_night_auto
                                StandbyNightMode.SCHEDULE -> R.string.standby_night_schedule
                                StandbyNightMode.LIGHT -> R.string.standby_night_light
                                StandbyNightMode.OFF -> R.string.standby_night_off
                            },
                        )
                    },
                    Modifier.padding(top = 8.dp),
                ) { m -> update { it.copy(nightMode = m) } }
                val schedule = s.nightMode == StandbyNightMode.AUTO || s.nightMode == StandbyNightMode.SCHEDULE
                ModuleSlider(
                    stringResource(R.string.standby_night_start), s.nightStart.toFloat(), 0f..1425f, ::hhmm, steps = 94, enabled = schedule,
                ) { v -> update { it.copy(nightStart = v.roundToInt() / 15 * 15) } }
                ModuleSlider(
                    stringResource(R.string.standby_night_end), s.nightEnd.toFloat(), 0f..1425f, ::hhmm, steps = 94, enabled = schedule,
                ) { v -> update { it.copy(nightEnd = v.roundToInt() / 15 * 15) } }
                ModuleSlider(
                    stringResource(R.string.standby_dark_lux), s.darkLux, StandbySettings.LUX, { "${it.roundToInt()} lx" },
                    enabled = s.nightMode == StandbyNightMode.AUTO || s.nightMode == StandbyNightMode.LIGHT,
                ) { v -> update { it.copy(darkLux = v) } }
                ModuleNote(stringResource(R.string.standby_night_note))
            }
        }
        item("misc") {
            ModuleSection(stringResource(R.string.standby_section_misc))
            ModuleCard {
                ModuleSwitch(stringResource(R.string.standby_notifications), stringResource(R.string.standby_notifications_summary), s.notifications) { on ->
                    update { it.copy(notifications = on) }
                }
                ModuleNote(stringResource(R.string.standby_permissions_note))
            }
        }
    }

    editing?.let { page ->
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            StandbyPageEditor(page, s, onScene = { sc -> update { it.copy(scene = sc) } }) { layout ->
                update { it.copy(layouts = if (layout == null) it.layouts - page else it.layouts + (page to layout)) }
            }
        }
    }

    if (pickingAlbum) {
        ModalBottomSheet(onDismissRequest = { pickingAlbum = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text(stringResource(R.string.standby_album_pick), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val list = albums
            if (list != null && list.isEmpty()) ModuleNote(stringResource(R.string.standby_photos_none))
            LazyColumn {
                items(list.orEmpty(), key = { it.id }) { a ->
                    ListItem(
                        headlineContent = { Text(a.name) },
                        supportingContent = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.standby_album_count, a.count, a.count)) },
                        modifier = Modifier.clickable {
                            update { it.copy(albumId = a.id, albumName = a.name, pages = (it.pages + StandbyPage.PHOTOS).distinct()) }
                            pickingAlbum = false
                        },
                    )
                }
            }
        }
    }
}

/** Aperçu : l'écran Veille réduit dans un cadre de téléphone à l'horizontale. */
@Composable
private fun StandbyPreview(s: StandbySettings, modifier: Modifier = Modifier) {
    val base = LocalDensity.current
    val description = stringResource(R.string.standby_preview_desc)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(19.5f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(base.density * 0.42f, base.fontScale)) {
            StandbyScreen(StandbyInputs(s, night = false), exiting = false, onExited = {})
        }
    }
}
