package com.karelisio.bulle.modules.alerts.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.core.feedback.Feedback
import com.karelisio.bulle.core.feedback.FeedbackSound
import com.karelisio.bulle.core.feedback.HapticPattern
import com.karelisio.bulle.core.feedback.SoundKind
import com.karelisio.bulle.ui.components.rememberReduceMotion

/** Libellé d'un motif haptique. */
fun hapticLabel(p: HapticPattern): Int = when (p) {
    HapticPattern.NONE -> R.string.feedback_haptic_none
    HapticPattern.LIGHT -> R.string.feedback_haptic_light
    HapticPattern.DOUBLE_TAP -> R.string.feedback_haptic_double_tap
    HapticPattern.HEARTBEAT -> R.string.feedback_haptic_heartbeat
    HapticPattern.RISE -> R.string.feedback_haptic_rise
    HapticPattern.WAVE -> R.string.feedback_haptic_wave
    HapticPattern.INSISTENT -> R.string.feedback_haptic_insistent
    HapticPattern.CRISP -> R.string.feedback_haptic_crisp
}

/** Libellé d'un son synthétisé (ou « aucun » / « système »). */
fun soundLabel(k: SoundKind): Int = when (k) {
    SoundKind.NONE -> R.string.feedback_sound_none
    SoundKind.SYSTEM -> R.string.feedback_sound_system_default
    SoundKind.CHIME -> R.string.feedback_sound_chime
    SoundKind.BUBBLE -> R.string.feedback_sound_bubble
    SoundKind.POP -> R.string.feedback_sound_pop
    SoundKind.SOFT_DING -> R.string.feedback_sound_soft_ding
    SoundKind.TWINKLE -> R.string.feedback_sound_twinkle
}

/**
 * Deux sélecteurs « Vibration » et « Son » : toucher une puce la choisit ET la joue (aperçu).
 * L'aperçu suit les mêmes règles que l'île (silencieux, Ne pas déranger) : un message
 * explique pourquoi rien ne s'entend le cas échéant.
 */
@Composable
fun AlertFeedbackPickers(
    haptic: HapticPattern,
    sound: FeedbackSound,
    onHaptic: (HapticPattern) -> Unit,
    onSound: (FeedbackSound) -> Unit,
    section: @Composable (Int) -> Unit,
) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(false) }

    fun preview(h: HapticPattern, s: FeedbackSound) {
        val d = Feedback.preview(context, h, s)
        muted = !s.isNone && !d.sound
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        val picked = if (uri == null) {
            FeedbackSound.None
        } else {
            val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            FeedbackSound(SoundKind.SYSTEM, uri.toString(), title)
        }
        onSound(picked)
        preview(HapticPattern.NONE, picked)
    }

    section(R.string.feedback_haptic_title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HapticPattern.entries.forEach { p ->
            PlayChip(
                selected = haptic == p,
                label = stringResource(hapticLabel(p)),
                icon = Icons.Rounded.Vibration,
            ) {
                onHaptic(p)
                preview(p, FeedbackSound.None)
            }
        }
    }

    section(R.string.feedback_sound_title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val choices = listOf(FeedbackSound.None) + FeedbackSound.synthesized.map { FeedbackSound(it) } +
            FeedbackSound(SoundKind.SYSTEM)
        choices.forEach { s ->
            PlayChip(
                selected = sound.kind == s.kind && (s.kind != SoundKind.SYSTEM || sound.uri == null),
                label = stringResource(soundLabel(s.kind)),
                icon = Icons.Rounded.MusicNote,
            ) {
                onSound(s)
                preview(HapticPattern.NONE, s)
            }
        }
        val custom = sound.kind == SoundKind.SYSTEM && sound.uri != null
        PlayChip(
            selected = custom,
            label = if (custom) sound.title ?: stringResource(R.string.feedback_sound_system_pick) else stringResource(R.string.feedback_sound_system_pick),
            icon = Icons.Rounded.LibraryMusic,
        ) {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.feedback_sound_system_pick))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound.uri?.let(Uri::parse))
            runCatching { picker.launch(intent) }
        }
    }
    AnimatedVisibility(muted, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Text(
            stringResource(R.string.feedback_sound_muted_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Puce qui rebondit légèrement quand elle est choisie (ressort, coupé si « réduire les animations »). */
@Composable
private fun PlayChip(selected: Boolean, label: String, icon: ImageVector, onClick: () -> Unit) {
    val reduce = rememberReduceMotion()
    val scale by animateFloatAsState(
        if (selected) 1.05f else 1f,
        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipScale",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    )
}
