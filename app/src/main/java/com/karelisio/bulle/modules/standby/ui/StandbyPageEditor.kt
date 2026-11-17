package com.karelisio.bulle.modules.standby.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.standby.StandbyLayout
import com.karelisio.bulle.modules.standby.StandbyLogic
import com.karelisio.bulle.modules.standby.StandbyPage
import com.karelisio.bulle.modules.standby.StandbySettings
import com.karelisio.bulle.modules.standby.StandbyWidget
import com.karelisio.bulle.ui.components.ModuleSection
import com.karelisio.bulle.ui.components.ModuleSlider

@Composable
fun standbyWidgetLabel(w: StandbyWidget): String = stringResource(
    when (w) {
        StandbyWidget.CLOCK -> R.string.standby_w_clock
        StandbyWidget.DATE -> R.string.standby_w_date
        StandbyWidget.WEATHER -> R.string.standby_w_weather
        StandbyWidget.EVENT -> R.string.standby_w_event
        StandbyWidget.BATTERY -> R.string.standby_w_battery
        StandbyWidget.LIVE -> R.string.standby_w_live
        StandbyWidget.MUSIC -> R.string.standby_w_music
        StandbyWidget.LYRICS -> R.string.standby_w_lyrics
        StandbyWidget.QUEUE -> R.string.standby_w_queue
        StandbyWidget.PHOTOS -> R.string.standby_w_photos
        StandbyWidget.ECHO -> R.string.standby_w_echo
        StandbyWidget.COUNTDOWN -> R.string.standby_w_countdown
    },
)

/**
 * Éditeur d'une page : aperçu en direct, puis pour chaque panneau (gauche, droite) la pile de
 * widgets (retirer, monter, descendre, ajouter) et la largeur du panneau gauche.
 */
@Composable
fun StandbyPageEditor(page: StandbyPage, settings: StandbySettings, onChange: (StandbyLayout?) -> Unit) {
    val layout = StandbyLogic.layoutFor(page, settings)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Text(standbyPageLabel(page), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val base = LocalDensity.current
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(19.5f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
            CompositionLocalProvider(LocalDensity provides Density(base.density * 0.42f, base.fontScale)) {
                StandbyScreen(StandbyInputs(settings.copy(pages = listOf(page)), night = false), exiting = false, onExited = {})
            }
        }
        Stack(stringResource(R.string.standby_panel_left), layout.left) { onChange(layout.copy(left = it)) }
        Stack(stringResource(R.string.standby_panel_right), layout.right) { onChange(layout.copy(right = it)) }
        ModuleSlider(
            stringResource(R.string.standby_left_width), layout.leftWeight, StandbySettings.WEIGHT,
            { String.format(java.util.Locale.getDefault(), "%.1f : 1", it) },
        ) { v -> onChange(layout.copy(leftWeight = v)) }
        TextButton(onClick = { onChange(null) }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.standby_layout_reset))
        }
    }
}

/** Pile de widgets d'un panneau. */
@Composable
private fun Stack(title: String, items: List<StandbyWidget>, onChange: (List<StandbyWidget>) -> Unit) {
    ModuleSection(title, Modifier.padding(start = 16.dp, top = 12.dp))
    items.forEach { w ->
        ListItem(
            headlineContent = { Text(standbyWidgetLabel(w)) },
            trailingContent = {
                Row {
                    IconButton(onClick = { onChange(StandbyLogic.moveItem(items, w, -1)) }) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.standby_move_up)) }
                    IconButton(onClick = { onChange(StandbyLogic.moveItem(items, w, 1)) }) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.standby_move_down)) }
                    IconButton(onClick = { if (items.size > 1) onChange(items - w) }, enabled = items.size > 1) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.standby_widget_remove, standbyWidgetLabel(w)))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    val missing = StandbyWidget.entries - items.toSet()
    if (missing.isNotEmpty() && items.size < StandbySettings.MAX_STACK) {
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            missing.forEach { w ->
                AssistChip(
                    onClick = { onChange(items + w) },
                    label = { Text(standbyWidgetLabel(w)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.align(Alignment.CenterVertically)) },
                )
            }
        }
    }
}
