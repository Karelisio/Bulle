package com.karelisio.bulle.modules.standby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.standby.ScenePalette
import com.karelisio.bulle.modules.standby.SceneSettings
import com.karelisio.bulle.modules.standby.StandbyScene
import com.karelisio.bulle.ui.components.ModuleChoices
import com.karelisio.bulle.ui.components.ModuleNote
import com.karelisio.bulle.ui.components.ModuleSlider
import com.karelisio.bulle.ui.components.ModuleSwitch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun standbySceneLabel(s: StandbyScene): String = stringResource(
    when (s) {
        StandbyScene.LAVA -> R.string.standby_scene_lava
        StandbyScene.BUBBLES -> R.string.standby_scene_bubbles
        StandbyScene.AURORA -> R.string.standby_scene_aurora
        StandbyScene.WAVES -> R.string.standby_scene_waves
        StandbyScene.STARS -> R.string.standby_scene_stars
        StandbyScene.LIQUID_CLOCK -> R.string.standby_scene_liquid_clock
    },
)

@Composable
private fun paletteLabel(p: ScenePalette): String = stringResource(
    when (p) {
        ScenePalette.DYNAMIC -> R.string.standby_palette_dynamic
        ScenePalette.ARTWORK -> R.string.standby_palette_artwork
        ScenePalette.OCEAN -> R.string.standby_palette_ocean
        ScenePalette.SUNSET -> R.string.standby_palette_sunset
        ScenePalette.FOREST -> R.string.standby_palette_forest
        ScenePalette.MONO -> R.string.standby_palette_mono
    },
)

/**
 * Réglages des scènes : aperçu en direct, scène, palette, vitesse, densité, horloge, réaction aux
 * notifications, rotation et mode éco. Communs à la page Scène et au widget Scène.
 */
@Composable
fun StandbySceneSettings(scene: SceneSettings, onChange: (SceneSettings) -> Unit) {
    val s = scene.sanitized()
    Column(Modifier.fillMaxWidth()) {
        val base = LocalDensity.current
        Box(
            Modifier.padding(16.dp).fillMaxWidth().aspectRatio(19.5f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
            CompositionLocalProvider(LocalDensity provides Density(base.density * 0.42f, base.fontScale)) {
                StandbySceneView(s, music = null, fullScreen = true)
            }
        }
        Text(stringResource(R.string.standby_scene), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
        ModuleChoices(StandbyScene.entries, s.scene, { standbySceneLabel(it) }) { v -> onChange(s.copy(scene = v)) }
        Text(stringResource(R.string.standby_palette), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        ModuleChoices(ScenePalette.entries, s.palette, { paletteLabel(it) }) { v -> onChange(s.copy(palette = v)) }
        ModuleSlider(
            stringResource(R.string.standby_scene_speed), s.speed, SceneSettings.SPEED,
            { String.format(Locale.getDefault(), "× %.2f", it) },
        ) { v -> onChange(s.copy(speed = v)) }
        ModuleSlider(
            stringResource(R.string.standby_scene_density), s.density, SceneSettings.DENSITY,
            { String.format(Locale.getDefault(), "× %.1f", it) },
        ) { v -> onChange(s.copy(density = v)) }
        ModuleSlider(
            stringResource(R.string.standby_scene_cycle), s.cycleMinutes.toFloat(), 0f..60f,
            { m -> m.roundToInt().let { if (it == 0) "—" else "$it min" } }, steps = 11,
        ) { v -> onChange(s.copy(cycleMinutes = (v.roundToInt() / 5) * 5)) }
        if (s.cycleMinutes > 0) {
            ModuleNote(pluralStringResource(R.plurals.standby_scene_cycle_note, s.cycleMinutes, s.cycleMinutes))
        }
        ModuleSwitch(stringResource(R.string.standby_scene_clock), stringResource(R.string.standby_scene_clock_summary), s.clock) { on ->
            onChange(s.copy(clock = on))
        }
        ModuleSwitch(stringResource(R.string.standby_scene_react), stringResource(R.string.standby_scene_react_summary), s.react) { on ->
            onChange(s.copy(react = on))
        }
        ModuleSwitch(stringResource(R.string.standby_scene_eco), stringResource(R.string.standby_scene_eco_summary), s.eco) { on ->
            onChange(s.copy(eco = on))
        }
        ModuleNote(stringResource(R.string.standby_scene_note))
    }
}
