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
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow
import com.karelisio.bulle.core.settings.IslandFont
import com.karelisio.bulle.island.ui.IslandFonts
import com.karelisio.bulle.island.ui.IslandTextScaleRange
import com.karelisio.bulle.island.ui.islandShape
import com.karelisio.bulle.island.ui.islandTypography
import com.karelisio.bulle.ui.components.rememberReduceMotion
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
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
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.animation.togetherWith
import com.karelisio.bulle.ui.stylepacks.EntryAnimationsScreen
import com.karelisio.bulle.ui.stylepacks.StylePacksScreen
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
import com.karelisio.bulle.ui.components.switchRow
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.CardStyle
import com.karelisio.bulle.core.settings.IslandColorMode
import com.karelisio.bulle.core.settings.IslandShape
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.modules.rules.ui.labelRes
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberLiveValue
import com.karelisio.bulle.ui.components.sharedElement
import kotlinx.coroutines.launch

/** Nuancier proposé pour la couleur personnalisée. */
private val Swatches = listOf(
    0xFF000000, 0xFF1B1B1F, 0xFF2B2D42, 0xFF14213D, 0xFF1D3557, 0xFF003049,
    0xFF2D6A4F, 0xFF3A0CA3, 0xFF6A040F, 0xFF7B2CBF, 0xFFB5179E, 0xFF5E503F,
)

/**
 * Écran Style : forme et angles, taille, couleur, contour, ombre, texte, animations, carte étendue.
 * [onOpenStylePacks] / [onOpenEntryAnimations] : routes de navigation dédiées si l'hôte les fournit ;
 * null = sous-écrans ouverts sur place (retour système géré).
 */
@Composable
fun StyleScreen(
    onBack: () -> Unit,
    onOpenStylePacks: (() -> Unit)? = null,
    onOpenEntryAnimations: (() -> Unit)? = null,
) {
    // 0 = Style, 1 = Packs de styles, 2 = Animations d'entrée (navigation locale de repli).
    var sub by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.animation.AnimatedContent(
        targetState = sub,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "style_sub",
    ) { screen ->
        when (screen) {
            1 -> {
                androidx.activity.compose.BackHandler { sub = 0 }
                StylePacksScreen(onBack = { sub = 0 })
            }
            2 -> {
                androidx.activity.compose.BackHandler { sub = 0 }
                EntryAnimationsScreen(onBack = { sub = 0 })
            }
            else -> StyleMain(
                onBack = onBack,
                onOpenStylePacks = onOpenStylePacks ?: { sub = 1 },
                onOpenEntryAnimations = onOpenEntryAnimations ?: { sub = 2 },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleMain(onBack: () -> Unit, onOpenStylePacks: () -> Unit, onOpenEntryAnimations: () -> Unit) {
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
            IslandPreview(style, motion, Modifier.padding(horizontal = 16.dp).sharedElement("island_preview"), height = 190.dp, openDirection = settings.position.openDirection)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Packs de styles : point d'entrée mis en avant.
                NavCard(
                    title = stringResource(R.string.stylepacks_title),
                    subtitle = stringResource(R.string.stylepacks_open_hint),
                    icon = Icons.Rounded.Palette,
                    onClick = onOpenStylePacks,
                )
                Section(stringResource(R.string.style_shape), 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IslandShape.entries.forEach { shape ->
                            ShapeOption(shape, selected = style.shape == shape, smoothing = style.cornerSmoothing, modifier = Modifier.weight(1f)) {
                                editStyle { it.copy(shape = shape) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.style_corner_smoothing), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(
                            "${(style.cornerSmoothing * 100).roundToInt()} %",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val smoothing = rememberLiveValue(style.cornerSmoothing) { v ->
                        SettingsRepository.update(context) { it.copy(style = it.style.copy(cornerSmoothing = v)) }
                    }
                    Slider(value = smoothing.value, onValueChange = smoothing::drag, onValueChangeFinished = smoothing::finish, valueRange = 0f..1f)
                    Row {
                        Text(stringResource(R.string.style_corner_round), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.style_corner_squircle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Section(stringResource(R.string.style_size), 1) {
                    val size = rememberLiveValue(style.size) { v ->
                        SettingsRepository.update(context) { it.copy(style = it.style.copy(size = v)) }
                    }
                    Slider(
                        value = size.value,
                        onValueChange = size::drag,
                        onValueChangeFinished = size::finish,
                        valueRange = 0.5f..1.6f,
                        steps = 21,
                    )
                    Text(stringResource(R.string.style_size_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Largeurs maximales quand l'île s'allonge (compact) et quand elle s'ouvre (carte).
                    val maxCompact = rememberLiveValue(style.maxCompactWidth) { v ->
                        SettingsRepository.update(context) { it.copy(style = it.style.copy(maxCompactWidth = v)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.style_max_compact, (maxCompact.value * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(value = maxCompact.value, onValueChange = maxCompact::drag, onValueChangeFinished = maxCompact::finish, valueRange = 0.3f..1f, steps = 13)
                    val maxExpanded = rememberLiveValue(style.maxExpandedWidth) { v ->
                        SettingsRepository.update(context) { it.copy(style = it.style.copy(maxExpandedWidth = v)) }
                    }
                    Text(
                        stringResource(R.string.style_max_expanded, (maxExpanded.value * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(value = maxExpanded.value, onValueChange = maxExpanded::drag, onValueChangeFinished = maxExpanded::finish, valueRange = 0.5f..1f, steps = 9)
                    SwitchRow(stringResource(R.string.style_hide_idle), style.hideWhenIdle) { on -> editStyle { it.copy(hideWhenIdle = on) } }
                    Text(stringResource(R.string.style_hide_idle_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchRow(stringResource(R.string.style_stable_window), style.stableWindow) { on -> editStyle { it.copy(stableWindow = on) } }
                    Text(stringResource(R.string.style_stable_window_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val width = rememberLiveValue(style.outlineWidthDp) { v ->
                            SettingsRepository.update(context) { it.copy(style = it.style.copy(outlineWidthDp = v)) }
                        }
                        Slider(value = width.value, onValueChange = width::drag, onValueChangeFinished = width::finish, valueRange = 0.5f..3f)
                    }
                    SwitchRow(stringResource(R.string.style_shadow), style.shadow) { on -> editStyle { it.copy(shadow = on) } }
                    SwitchRow(stringResource(R.string.style_shimmer), style.shimmer) { on -> editStyle { it.copy(shimmer = on) } }
                }
                Section(stringResource(R.string.style_text), 4) {
                    TextSample(style)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.style_font), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fonts = remember { IslandFonts.available() }
                        fonts.forEach { f ->
                            FilterChip(
                                selected = style.font == f,
                                onClick = { editStyle { it.copy(font = f) } },
                                label = { Text(stringResource(f.labelRes()), fontFamily = IslandFonts.family(f)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.style_text_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text("${(style.textScale * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    val textScale = rememberLiveValue(style.textScale) { v ->
                        SettingsRepository.update(context) { it.copy(style = it.style.copy(textScale = v)) }
                    }
                    Slider(
                        value = textScale.value,
                        onValueChange = { textScale.drag((it * 20).roundToInt() / 20f) },
                        onValueChangeFinished = textScale::finish,
                        valueRange = IslandTextScaleRange,
                        steps = 7,
                    )
                    SwitchRow(stringResource(R.string.style_marquee), style.marquee) { on -> editStyle { it.copy(marquee = on) } }
                    SwitchRow(stringResource(R.string.style_two_line), style.twoLineCompact) { on -> editStyle { it.copy(twoLineCompact = on) } }
                    Text(stringResource(R.string.style_two_line_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Section(stringResource(R.string.style_motion), 5) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        MotionStyle.entries.forEachIndexed { i, st ->
                            SegmentedButton(
                                selected = motion.style == st,
                                onClick = { edit { it.copy(motion = it.motion.copy(style = st)) } },
                                shape = SegmentedButtonDefaults.itemShape(i, MotionStyle.entries.size),
                            ) { Text(stringResource(st.labelRes()).replaceFirstChar { it.uppercase() }, maxLines = 1, softWrap = false) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.style_speed), style = MaterialTheme.typography.labelLarge)
                    val speed = rememberLiveValue(motion.speed) { v ->
                        SettingsRepository.update(context) { it.copy(motion = it.motion.copy(speed = v)) }
                    }
                    Slider(value = speed.value, onValueChange = speed::drag, onValueChangeFinished = speed::finish, valueRange = 0.5f..2f)
                    SwitchRow(stringResource(R.string.style_blur), motion.blurTransitions) { on ->
                        edit { it.copy(motion = it.motion.copy(blurTransitions = on)) }
                    }
                    SwitchRow(stringResource(R.string.style_haptics), motion.haptics) { on ->
                        edit { it.copy(motion = it.motion.copy(haptics = on)) }
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.stylepacks_entry_title)) },
                        supportingContent = { Text(stringResource(R.string.stylepacks_entry_open_hint)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onOpenEntryAnimations),
                    )
                }
                Section(stringResource(R.string.style_card), 6) {
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

private fun IslandFont.labelRes() = when (this) {
    IslandFont.DEFAULT -> R.string.style_font_default
    IslandFont.ROUNDED -> R.string.style_font_rounded
    IslandFont.CONDENSED -> R.string.style_font_condensed
    IslandFont.SERIF -> R.string.style_font_serif
    IslandFont.MONO -> R.string.style_font_mono
}

/**
 * Aperçu du texte de l'île : une pilule compacte rendue avec la typographie de l'île
 * (police, taille, défilement, seconde ligne).
 */
@Composable
private fun TextSample(style: StyleSettings) {
    val base = MaterialTheme.typography
    val typography = remember(base, style.font, style.textScale) {
        islandTypography(base, IslandFonts.family(style.font), style.textScale)
    }
    val reduceMotion = rememberReduceMotion()
    val twoLines = style.twoLineCompact
    val pillHeight by animateDpAsState(if (twoLines) 46.dp else 36.dp, spring(dampingRatio = 0.7f), label = "pill")
    val corner = with(LocalDensity.current) { (pillHeight / 2).toPx() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .width(260.dp)
                .height(pillHeight)
                .clip(islandShape(corner, corner, style.cornerSmoothing))
                .background(Color.Black)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialTheme(typography = typography) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Text(stringResource(R.string.style_text_sample_lead), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    // Caméra factice.
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF1C2333)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        val scroll = if (style.marquee && !reduceMotion) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 900) else Modifier
                        Text(
                            stringResource(R.string.style_text_sample_long),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = if (style.marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = scroll,
                        )
                        if (twoLines) {
                            Text(
                                stringResource(R.string.style_text_sample_sub),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }
                }
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

/** Carte de navigation vers un sous-écran (packs de styles). */
@Composable
private fun NavCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().cascade(0),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, null) },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.switchRow(checked, onChange = onChange),
    )
}

/** Carte de choix de forme, avec un mini-dessin de la forme. */
@Composable
private fun ShapeOption(shape: IslandShape, selected: Boolean, smoothing: Float, modifier: Modifier, onClick: () -> Unit) {
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
            val half = with(LocalDensity.current) { (h / 2).toPx() }
            Box(
                Modifier
                    .width(60.dp)
                    .height(h)
                    .clip(islandShape(half * shape.topRadius, half * shape.bottomRadius, smoothing))
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
