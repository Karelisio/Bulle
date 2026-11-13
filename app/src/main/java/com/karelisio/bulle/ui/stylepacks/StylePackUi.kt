package com.karelisio.bulle.ui.stylepacks

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.core.style.EntryAnimation
import com.karelisio.bulle.core.style.StylePack
import com.karelisio.bulle.island.ui.addSmoothRoundRect
import com.karelisio.bulle.island.ui.islandFill
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.theme.bulleColorScheme

@StringRes
fun EntryAnimation.labelRes(): Int = when (this) {
    EntryAnimation.INFLATE -> R.string.stylepacks_entry_inflate
    EntryAnimation.FADE -> R.string.stylepacks_entry_fade
    EntryAnimation.SLIDE -> R.string.stylepacks_entry_slide
    EntryAnimation.ELASTIC -> R.string.stylepacks_entry_elastic
    EntryAnimation.DROP -> R.string.stylepacks_entry_drop
    EntryAnimation.RIPPLE -> R.string.stylepacks_entry_ripple
}

/** Libellé d'un type de contenu (moduleId) ; null pour un module inconnu. */
@StringRes
fun moduleLabelRes(moduleId: String): Int? = when (moduleId) {
    "music" -> R.string.stylepacks_module_music
    "messages" -> R.string.stylepacks_module_messages
    "alerts" -> R.string.stylepacks_module_alerts
    "call" -> R.string.stylepacks_module_call
    "live" -> R.string.stylepacks_module_live
    "calendar" -> R.string.stylepacks_module_calendar
    "weather" -> R.string.stylepacks_module_weather
    "focus" -> R.string.stylepacks_module_focus
    "battery" -> R.string.stylepacks_module_battery
    "network" -> R.string.stylepacks_module_network
    "volume" -> R.string.stylepacks_module_volume
    "echo" -> R.string.stylepacks_module_echo
    "external" -> R.string.stylepacks_module_external
    else -> null
}

/** Nom et description traduits d'un pack intégré. */
fun builtInLabels(id: String): Pair<Int, Int> = when (id) {
    "ink" -> R.string.stylepacks_pack_ink to R.string.stylepacks_pack_ink_desc
    "frost" -> R.string.stylepacks_pack_frost to R.string.stylepacks_pack_frost_desc
    "neon" -> R.string.stylepacks_pack_neon to R.string.stylepacks_pack_neon_desc
    "pastel" -> R.string.stylepacks_pack_pastel to R.string.stylepacks_pack_pastel_desc
    "retro" -> R.string.stylepacks_pack_retro to R.string.stylepacks_pack_retro_desc
    "minimal" -> R.string.stylepacks_pack_minimal to R.string.stylepacks_pack_minimal_desc
    "liquid" -> R.string.stylepacks_pack_liquid to R.string.stylepacks_pack_liquid_desc
    else -> R.string.stylepacks_pack_bulle to R.string.stylepacks_pack_bulle_desc
}

/** StyleSettings complets correspondant à un pack (pour les aperçus). */
fun StylePack.previewStyle(): StyleSettings = applyTo(com.karelisio.bulle.core.settings.BulleSettings()).style

/** Petite pastille statique de l'île dans le style du pack (listes). */
@Composable
fun PackSwatch(pack: StylePack, modifier: Modifier = Modifier) {
    val style = pack.previewStyle()
    val dark = bulleColorScheme(dark = true)
    val fill = islandFill(style, dark)
    val outline = dark.primary
    Box(
        modifier
            .size(56.dp, 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(40.dp, 16.dp)) {
            val h = size.height
            val r = h / 2
            val path = Path().apply {
                addSmoothRoundRect(0f, 0f, size.width, h, r * style.shape.topRadius, r * style.shape.bottomRadius, style.cornerSmoothing)
            }
            drawPath(path, fill)
            if (style.outline) drawPath(path, outline, style = Stroke(style.outlineWidthDp.dp.toPx()))
            drawCircle(Color(0xFF1C2333), radius = 2.dp.toPx(), center = Offset(size.width / 2, h / 2))
        }
    }
}

/** Carte de section, dans le style des autres écrans de réglages. */
@Composable
fun PackSection(title: String, index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth().cascade(index),
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

/** Mini île (aperçu d'animation d'entrée) : pastille noire avec un contenu factice. */
@Composable
fun MiniIslandContent(accent: Color) {
    Row(
        Modifier.width(120.dp).height(30.dp).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(accent))
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF1C2333)))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(8.dp, 14.dp, 10.dp).forEach { h ->
                Box(Modifier.padding(horizontal = 1.dp).width(3.dp).height(h).clip(RoundedCornerShape(2.dp)).background(accent))
            }
        }
    }
}
