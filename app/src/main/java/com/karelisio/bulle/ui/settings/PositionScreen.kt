package com.karelisio.bulle.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.PositionTweak
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.CutoutLocator
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberLiveValue
import com.karelisio.bulle.ui.components.sharedElement
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Position : l'île est calée automatiquement sur la caméra, ces réglages la décalent finement.
 * Aperçu en direct : la vraie île se déplace pendant le réglage.
 */
@Composable
fun PositionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val p = settings.position
    val cutout = remember { CutoutLocator.screenInfo(context) }

    suspend fun save(t: (PositionTweak) -> PositionTweak) =
        SettingsRepository.update(context) { it.copy(position = t(it.position)) }

    fun edit(t: (PositionTweak) -> PositionTweak) = scope.launch { save(t) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.position_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IslandPreview(settings.style, settings.motion, Modifier.sharedElement("island_preview"), height = 150.dp)
            Card(
                Modifier.fillMaxWidth().cascade(0),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.position_live_hint), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    val c = cutout.cutout
                    Text(
                        if (c != null) stringResource(
                            R.string.position_cutout_found,
                            (c.width() / cutout.density).roundToInt(), (c.height() / cutout.density).roundToInt(),
                        ) else stringResource(R.string.position_cutout_none),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            TweakSlider(stringResource(R.string.position_x), p.offsetXDp, -60f..60f, 1) { v -> save { it.copy(offsetXDp = v) } }
            TweakSlider(stringResource(R.string.position_y), p.offsetYDp, -20f..40f, 2) { v -> save { it.copy(offsetYDp = v) } }
            TweakSlider(stringResource(R.string.position_width), p.extraWidthDp, -30f..120f, 3) { v -> save { it.copy(extraWidthDp = v) } }
            TweakSlider(stringResource(R.string.position_height), p.extraHeightDp, -12f..30f, 4) { v -> save { it.copy(extraHeightDp = v) } }
            FilledTonalButton(onClick = { edit { PositionTweak() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.CenterFocusStrong, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.position_reset))
            }
        }
    }
}

/** Curseur avec boutons −/+ au demi-dp près, pour un calage fin. */
@Composable
private fun TweakSlider(label: String, stored: Float, range: ClosedFloatingPointRange<Float>, index: Int, persist: suspend (Float) -> Unit) {
    // Valeur locale + écriture conflatée (voir LiveValue) ; les boutons −/+ partent de la
    // valeur affichée, sinon des appuis rapides se basaient sur une valeur pas encore relue.
    val live = rememberLiveValue(stored, persist)
    val value = live.value
    Card(
        Modifier.fillMaxWidth().cascade(index),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("%+.1f dp".format(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { live.set((value - 0.5f).coerceIn(range)) }) { Icon(Icons.Rounded.Remove, null) }
                Slider(
                    value = value,
                    onValueChange = { live.drag((it * 2).roundToInt() / 2f) },
                    onValueChangeFinished = live::finish,
                    valueRange = range,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                FilledTonalIconButton(onClick = { live.set((value + 0.5f).coerceIn(range)) }) { Icon(Icons.Rounded.Add, null) }
            }
        }
    }
}
