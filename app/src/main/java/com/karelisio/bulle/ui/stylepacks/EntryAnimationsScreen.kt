package com.karelisio.bulle.ui.stylepacks

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.style.EntryAnimation
import com.karelisio.bulle.core.style.EntryAnimations
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Écran « Animations d'entrée » : une animation par défaut et, au besoin, une par type de contenu.
 * Chaque animation est montrée en aperçu qui se rejoue en boucle (arrêté si les animations
 * système sont réduites).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryAnimationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val entries = settings.motion.entryAnimations
    val speed = settings.motion.speed
    val reduce = rememberReduceMotion()
    val default = EntryAnimations.resolve(null, entries)

    fun set(key: String, animation: EntryAnimation?) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        scope.launch {
            SettingsRepository.update(context) {
                it.copy(motion = it.motion.copy(entryAnimations = EntryAnimations.with(it.motion.entryAnimations, key, animation)))
            }
        }
    }

    // Horloge commune des aperçus : tous se rejouent ensemble.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) {
            delay(1800)
            tick++
        }
    }

    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stylepacks_entry_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.stylepacks_entry_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reduce) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(20.dp)) {
                    Text(stringResource(R.string.stylepacks_entry_reduce_motion), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            PackSection(stringResource(R.string.stylepacks_entry_default), 0) {
                AnimationGrid(selected = default, tick = tick, speed = speed) { set(EntryAnimations.DEFAULT_KEY, it) }
            }
            PackSection(stringResource(R.string.stylepacks_entry_per_type), 1) {
                EntryAnimations.ConfigurableModules.forEach { module ->
                    val label = moduleLabelRes(module) ?: return@forEach
                    val own = EntryAnimation.parse(entries[module])
                    ListItem(
                        headlineContent = { Text(stringResource(label)) },
                        supportingContent = {
                            Column {
                                Text(
                                    if (own != null) stringResource(own.labelRes())
                                    else stringResource(R.string.stylepacks_entry_same_as_default, stringResource(default.labelRes())),
                                )
                                if (module == "alerts") {
                                    Text(stringResource(R.string.stylepacks_entry_alerts_note), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { picking = module },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picking?.let { module ->
        ModalBottomSheet(onDismissRequest = { picking = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(moduleLabelRes(module) ?: R.string.stylepacks_entry_default), style = MaterialTheme.typography.titleLarge)
                val own = EntryAnimation.parse(entries[module])
                ListItem(
                    headlineContent = { Text(stringResource(R.string.stylepacks_entry_follow_default)) },
                    supportingContent = { Text(stringResource(default.labelRes())) },
                    trailingContent = { if (own == null) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { set(module, null); picking = null },
                )
                AnimationGrid(selected = own, tick = tick, speed = speed) { set(module, it); picking = null }
            }
        }
    }
}

/** Grille des six animations, chacune avec son aperçu en boucle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimationGrid(selected: EntryAnimation?, tick: Int, speed: Float, onSelect: (EntryAnimation) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EntryAnimation.entries.forEach { a ->
            EntryPreviewCard(a, selected == a, tick, speed, Modifier.weight(1f)) { onSelect(a) }
        }
    }
}

/** Carte d'aperçu : mini île dont le contenu rejoue l'animation à chaque [tick]. */
@Composable
fun EntryPreviewCard(animation: EntryAnimation, selected: Boolean, tick: Int, speed: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val corner by animateDpAsState(if (selected) 28.dp else 18.dp, spring(dampingRatio = 0.6f), label = "corner")
    val accent = MaterialTheme.colorScheme.tertiary
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.width(120.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                EntryAnimated(animation, accent, speed, replayKey = tick, preview = true) { MiniIslandContent(accent) }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(animation.labelRes()), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
