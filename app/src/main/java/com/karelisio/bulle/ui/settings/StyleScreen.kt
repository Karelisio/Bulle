package com.karelisio.bulle.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.CardStyle
import com.karelisio.bulle.core.settings.IslandColorMode
import com.karelisio.bulle.core.settings.IslandShape
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.modules.rules.ui.labelRes
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.sharedElement
import kotlinx.coroutines.launch

/** Nuancier proposé pour la couleur personnalisée. */
private val Swatches = listOf(
    0xFF000000, 0xFF1B1B1F, 0xFF2B2D42, 0xFF14213D, 0xFF1D3557, 0xFF003049,
    0xFF2D6A4F, 0xFF3A0CA3, 0xFF6A040F, 0xFF7B2CBF, 0xFFB5179E, 0xFF5E503F,
)

/** Écran Style : forme, taille, couleur, contour, ombre, animations, carte étendue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val style = settings.style
    val motion = settings.motion

    fun edit(transform: (BulleSettings) -> BulleSettings) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { SettingsRepository.update(context, transform) }
    }
    fun editStyle(t: (StyleSettings) -> StyleSettings) = edit { it.copy(style = t(it.style)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.style_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Aperçu toujours visible en haut.
            IslandPreview(style, motion, Modifier.padding(horizontal = 16.dp).sharedElement("island_preview"), height = 190.dp)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Section(stringResource(R.string.style_shape), 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IslandShape.entries.forEach { shape ->
                            ShapeOption(shape, selected = style.shape == shape, modifier = Modifier.weight(1f)) {
                                editStyle { it.copy(shape = shape) }
                            }
                        }
                    }
                }
                Section(stringResource(R.string.style_size), 1) {
                    Slider(
                        value = style.size,
                        onValueChange = { v -> scope.launch { SettingsRepository.update(context) { it.copy(style = it.style.copy(size = v)) } } },
                        valueRange = 0.5f..1.6f,
                        steps = 21,
                    )
                    Text(stringResource(R.string.style_size_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Section(stringResource(R.string.style_color), 2) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        IslandColorMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = style.colorMode == mode,
                                onClick = { editStyle { it.copy(colorMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, IslandColorMode.entries.size),
                            ) { Text(stringResource(mode.labelRes())) }
                        }
                    }
                    AnimatedVisibility(style.colorMode == IslandColorMode.CUSTOM, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Swatches.forEach { argb ->
                                    val selected = style.customColor == argb
                                    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(dampingRatio = 0.5f), label = "ring")
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .border(BorderStroke(ring, MaterialTheme.colorScheme.primary), CircleShape)
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(Color(argb.toInt()))
                                            .clickable { editStyle { it.copy(customColor = argb) } },
                                        contentAlignment = Alignment.Center,
                                    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            HexField(style.customColor) { v -> editStyle { it.copy(customColor = v) } }
                        }
                    }
                }
                Section(stringResource(R.string.style_outline_shadow), 3) {
                    SwitchRow(stringResource(R.string.style_outline), style.outline) { on -> editStyle { it.copy(outline = on) } }
                    AnimatedVisibility(style.outline) {
                        Slider(value = style.outlineWidthDp, onValueChange = { v ->
                            scope.launch { SettingsRepository.update(context) { it.copy(style = it.style.copy(outlineWidthDp = v)) } }
                        }, valueRange = 0.5f..3f)
                    }
                    SwitchRow(stringResource(R.string.style_shadow), style.shadow) { on -> editStyle { it.copy(shadow = on) } }
                    SwitchRow(stringResource(R.string.style_shimmer), style.shimmer) { on -> editStyle { it.copy(shimmer = on) } }
                }
                Section(stringResource(R.string.style_motion), 4) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        MotionStyle.entries.forEachIndexed { i, st ->
                            SegmentedButton(
                                selected = motion.style == st,
                                onClick = { edit { it.copy(motion = it.motion.copy(style = st)) } },
                                shape = SegmentedButtonDefaults.itemShape(i, MotionStyle.entries.size),
                            ) { Text(stringResource(st.labelRes()).replaceFirstChar { it.uppercase() }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.style_speed), style = MaterialTheme.typography.labelLarge)
                    Slider(value = motion.speed, onValueChange = { v ->
                        scope.launch { SettingsRepository.update(context) { it.copy(motion = it.motion.copy(speed = v)) } }
                    }, valueRange = 0.5f..2f)
                }
                Section(stringResource(R.string.style_card), 5) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CardStyle.entries.forEachIndexed { i, cs ->
                            SegmentedButton(
                                selected = style.cardStyle == cs,
                                onClick = { editStyle { it.copy(cardStyle = cs) } },
                                shape = SegmentedButtonDefaults.itemShape(i, CardStyle.entries.size),
                            ) { Text(stringResource(if (cs == CardStyle.FLOATING) R.string.style_card_floating else R.string.style_card_wide)) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun IslandColorMode.labelRes() = when (this) {
    IslandColorMode.BLACK -> R.string.style_color_black
    IslandColorMode.DYNAMIC -> R.string.style_color_dynamic
    IslandColorMode.CUSTOM -> R.string.style_color_custom
}

@Composable
private fun Section(title: String, index: Int, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().cascade(index),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

/** Carte de choix de forme, avec un mini-dessin de la forme. */
@Composable
private fun ShapeOption(shape: IslandShape, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val corner by animateDpAsState(if (selected) 24.dp else 16.dp, spring(dampingRatio = 0.6f), label = "corner")
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val h = 22.dp
            Box(
                Modifier
                    .width(60.dp)
                    .height(h)
                    .clip(
                        RoundedCornerShape(
                            topStart = h / 2 * shape.topRadius, topEnd = h / 2 * shape.topRadius,
                            bottomEnd = h / 2 * shape.bottomRadius, bottomStart = h / 2 * shape.bottomRadius,
                        ),
                    )
                    .background(Color.Black),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (shape) {
                        IslandShape.PILL -> R.string.style_shape_pill
                        IslandShape.DROP -> R.string.style_shape_drop
                        IslandShape.ROUNDED -> R.string.style_shape_rounded
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Champ hexadécimal (#RRGGBB) synchronisé avec le nuancier. */
@Composable
private fun HexField(argb: Long, onValid: (Long) -> Unit) {
    var text by remember(argb) { mutableStateOf("#%06X".format(argb and 0xFFFFFF)) }
    val valid = Regex("^#?[0-9a-fA-F]{6}$").matches(text)
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            if (Regex("^#?[0-9a-fA-F]{6}$").matches(v)) onValid(0xFF000000 or v.removePrefix("#").toLong(16))
        },
        label = { Text(stringResource(R.string.style_hex)) },
        isError = !valid,
        singleLine = true,
        leadingIcon = {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color((0xFF000000 or (argb and 0xFFFFFF)).toInt())))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
