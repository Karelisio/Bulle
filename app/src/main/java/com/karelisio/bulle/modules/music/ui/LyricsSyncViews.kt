package com.karelisio.bulle.modules.music.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.music.sync.LyricsSync
import com.karelisio.bulle.modules.music.sync.OutputKind

/** Nom lisible d'une sortie audio : le nom du produit, sinon un libellé selon sa famille. */
@Composable
internal fun outputLabel(kind: OutputKind, name: String): String =
    name.trim().ifEmpty {
        stringResource(
            when (kind) {
                OutputKind.SPEAKER -> R.string.music_output_speaker
                OutputKind.BLUETOOTH -> R.string.music_output_bluetooth
                OutputKind.HEARING_AID -> R.string.music_output_hearing
                OutputKind.WIRED -> R.string.music_output_wired
                OutputKind.USB -> R.string.music_output_usb
                OutputKind.HDMI -> R.string.music_output_hdmi
                OutputKind.OTHER -> R.string.music_output_other
            },
        )
    }

/** Icône d'une famille de sortie audio. */
internal fun outputIcon(kind: OutputKind): ImageVector = when (kind) {
    OutputKind.SPEAKER -> Icons.Rounded.PhoneAndroid
    OutputKind.BLUETOOTH -> Icons.Rounded.Bluetooth
    OutputKind.HEARING_AID -> Icons.Rounded.Hearing
    OutputKind.WIRED -> Icons.Rounded.Cable
    OutputKind.USB -> Icons.Rounded.Usb
    OutputKind.HDMI -> Icons.Rounded.SettingsInputHdmi
    OutputKind.OTHER -> Icons.Rounded.Speaker
}

/**
 * Réglage rapide du décalage des paroles : « − 250 ms + », par pas de 50 ms, avec un tic haptique
 * (un retour plus marqué en butée). [onChange] reçoit la nouvelle valeur déjà bornée.
 */
@Composable
internal fun OffsetStepper(
    offsetMs: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
) {
    val haptics = LocalHapticFeedback.current
    fun step(delta: Int) {
        val next = LyricsSync.clampOffset(offsetMs + delta)
        if (next == offsetMs) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onChange(next)
        }
    }
    val colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { step(-LyricsSync.STEP_MS) }, colors = colors, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.music_sync_earlier), modifier = Modifier.size(20.dp))
        }
        Text(
            LyricsSync.formatOffset(offsetMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor.copy(alpha = if (offsetMs == 0) 0.6f else 0.92f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 58.dp),
        )
        IconButton(onClick = { step(LyricsSync.STEP_MS) }, colors = colors, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.music_sync_later), modifier = Modifier.size(20.dp))
        }
    }
}
