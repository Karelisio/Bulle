package com.karelisio.bulle.modules.alerts.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertDef
import com.karelisio.bulle.modules.alerts.AlertEntrance
import com.karelisio.bulle.modules.alerts.AlertStyle
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.components.rememberReduceMotion

/** Exemple générique pour les alertes sans contenu de démonstration (alarme, capture d'écran). */
data class SampleAlertContent(
    override val alertId: String,
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:sample:$alertId:$postedAt"
    override val accent = tint
    override val compactSlotWidth = 84.dp

    @Composable
    override fun Leading() = PopIcon(icon, tint)

    @Composable
    override fun Trailing() = AlertLabel(title, color = tint)

    @Composable
    override fun Expanded(host: IslandHost) = AlertExpanded(tint, title, null, { PopIcon(icon, tint, size = 26.dp) })
}

/** Contenu d'exemple d'une alerte, habillé du style en cours d'édition. */
fun sampleOf(ui: AlertUi, context: Context): IslandContent =
    ui.sample?.invoke(context) ?: SampleAlertContent(ui.id, ui.icon, ui.tint, context.getString(ui.titleRes), alertNow())

/** Hôte inerte : l'aperçu n'agit jamais sur le téléphone. */
private class PreviewHost(override val context: Context) : IslandHost {
    override fun expand(key: String) = Unit
    override fun collapse() = Unit
    override fun dismiss(key: String) = Unit
    override fun openPanel(panel: IslandPanel) = Unit
    override fun closePanel() = Unit
    override fun requestInput(enabled: Boolean) = Unit
    override fun runAction(action: GestureAction) = Unit
}

/**
 * Feuille d'édition du style d'une alerte : aperçu animé (vue compacte + vue étendue, rendues
 * par les composables de l'alerte elle-même), icône, couleur, animation d'entrée et durée.
 */
@Composable
fun AlertStyleSheet(
    ui: AlertUi,
    def: AlertDef,
    style: AlertStyle,
    onChange: (AlertStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AlertStyleEditor(ui, def, style, onChange)
    }
}

/** Contenu de la feuille, séparé pour être testé sans la fenêtre de la feuille. */
@Composable
fun AlertStyleEditor(ui: AlertUi, def: AlertDef, style: AlertStyle, onChange: (AlertStyle) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var replay by remember { mutableIntStateOf(0) }
    fun change(s: AlertStyle) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onChange(s)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(ui.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { replay++ }) { Icon(Icons.Rounded.Replay, stringResource(R.string.alerts_style_replay)) }
        }
        Spacer(Modifier.height(12.dp))

        // Aperçu : rejoué à chaque changement de style ou sur « Rejouer ».
        key(style, replay) {
            val sample = remember { styled(sampleOf(ui, context), mapOf(ui.id to style)) }
            StylePreview(sample)
        }

        Section(R.string.alerts_style_icon)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconChoice(Icons.Rounded.AutoAwesome, stringResource(R.string.alerts_style_default), style.icon == null, ui.tint) {
                change(style.copy(icon = null))
            }
            AlertIcons.choicesFor(ui.id).forEach { k ->
                val v = AlertIcons.vector(k) ?: return@forEach
                IconChoice(v, k, style.icon == k, accentColor(style.accent) ?: ui.tint) { change(style.copy(icon = k)) }
            }
        }

        Section(R.string.alerts_style_color)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatch(ui.tint, stringResource(R.string.alerts_style_default), style.accent == null, isDefault = true) {
                change(style.copy(accent = null))
            }
            AlertSwatches.forEachIndexed { i, argb ->
                Swatch(Color(argb), stringResource(R.string.alerts_style_color_n, i + 1), style.accent == argb) {
                    change(style.copy(accent = argb))
                }
            }
        }

        Section(R.string.alerts_style_entrance)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(
                null to R.string.alerts_style_default,
                AlertEntrance.NONE to R.string.alerts_entrance_none,
                AlertEntrance.BOUNCE to R.string.alerts_entrance_bounce,
                AlertEntrance.PULSE to R.string.alerts_entrance_pulse,
                AlertEntrance.SLIDE to R.string.alerts_entrance_slide,
            )
            options.forEach { (e, label) ->
                FilterChip(
                    selected = style.entrance == e,
                    onClick = { change(style.copy(entrance = e)) },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        // Vibration et son à l'apparition : l'aperçu joue lui-même le retour, pas de tic en plus.
        AlertFeedbackPickers(
            haptic = style.haptic,
            sound = style.sound,
            onHaptic = { onChange(style.copy(haptic = it)) },
            onSound = { onChange(style.copy(sound = it)) },
            section = { Section(it) },
        )

        Section(R.string.alerts_style_duration)
        if (def.persistent) {
            Text(
                stringResource(R.string.alerts_style_persistent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DurationPicker(style.durationSec) { change(style.copy(durationSec = it)) }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { change(AlertStyle()) }, enabled = !style.isDefault) {
                Text(stringResource(R.string.alerts_style_reset))
            }
            if (ui.pushable && !def.persistent) {
                FilledTonalButton(onClick = { PushModule.push(styled(sampleOf(ui, context), mapOf(ui.id to style))) }) {
                    Text(stringResource(R.string.alerts_style_try))
                }
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int) {
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(10.dp))
}

/** Mini île (vue compacte) et carte noire (vue étendue) rendues par le contenu lui-même. */
@Composable
private fun StylePreview(content: IslandContent) {
    val context = LocalContext.current
    val host = remember(context) { PreviewHost(context) }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black)
                        .heightIn(min = 38.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(content.compactSlotWidth), contentAlignment = Alignment.CenterStart) { content.Leading() }
                    // Emplacement de la caméra.
                    Box(Modifier.padding(horizontal = 10.dp).size(14.dp).clip(CircleShape).background(Color(0xFF1C1C22)))
                    Box(Modifier.width(content.compactSlotWidth), contentAlignment = Alignment.CenterEnd) { content.Trailing() }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.Black)
                        .padding(18.dp),
                ) { content.Expanded(host) }
            }
            // Couche qui absorbe les touchers : les boutons de l'aperçu ne font rien.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
            )
        }
    }
}

@Composable
private fun IconChoice(icon: ImageVector, label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val reduce = rememberReduceMotion()
    val scale by animateFloatAsState(
        if (selected) 1.08f else 1f,
        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale",
    )
    val bg by animateColorAsState(
        if (selected) tint.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "iconBg",
    )
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun Swatch(color: Color, label: String, selected: Boolean, isDefault: Boolean = false, onClick: () -> Unit) {
    val reduce = rememberReduceMotion()
    val ring by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ring",
    )
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = ring), CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected }
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().clip(CircleShape).background(color))
        if (isDefault) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        } else if (selected) {
            Icon(
                Icons.Rounded.Check, null, tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = ring; scaleY = ring },
            )
        }
    }
}

@Composable
private fun DurationPicker(seconds: Int?, onChange: (Int?) -> Unit) {
    // Valeur locale pendant le glisser ; enregistrée au relâchement (pas d'écriture à chaque pas).
    var drag by remember(seconds) { mutableIntStateOf(seconds ?: 4) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = seconds == null,
            onClick = { onChange(null) },
            label = { Text(stringResource(R.string.alerts_style_default)) },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.alerts_style_seconds, drag),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (seconds == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
    Slider(
        value = drag.toFloat(),
        onValueChange = { drag = it.toInt().coerceIn(AlertStyle.MIN_SECONDS, AlertStyle.MAX_SECONDS) },
        onValueChangeFinished = { onChange(drag) },
        valueRange = AlertStyle.MIN_SECONDS.toFloat()..AlertStyle.MAX_SECONDS.toFloat(),
        steps = AlertStyle.MAX_SECONDS - AlertStyle.MIN_SECONDS - 1,
    )
}
