package com.karelisio.bulle.modules.capture.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.a11y.IslandSpeech
import com.karelisio.bulle.core.island.IslandAccessibilityAction
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.capture.CaptureFormat
import com.karelisio.bulle.modules.capture.CaptureModule
import com.karelisio.bulle.modules.capture.CaptureState
import com.karelisio.bulle.modules.capture.LevelBars
import com.karelisio.bulle.modules.capture.LevelWave
import com.karelisio.bulle.modules.capture.MemoPhase
import com.karelisio.bulle.modules.capture.MemoSession
import com.karelisio.bulle.modules.capture.rememberChrono
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.RollingText

/** Mémo vocal en cours : niveau sonore réel + chrono ; vue étendue avec pause / reprise / arrêt. */
data class MemoContent(val session: MemoSession) : IslandContent() {

    override fun describe(res: IslandSpeech.Res): String = IslandSpeech.recording(
        res, res.str(R.string.a11y_island_memo), null, paused = session.phase != MemoPhase.RECORDING,
    )
    override val key = KEY
    override val moduleId = CaptureModule.ID
    override val priority = Priority.LIVE
    override val postedAt: Long = session.startedAt
    override val accent: Color = MemoRed
    // Assez large pour « 1:02:03 » (le contenu n'est pas recréé à chaque seconde).
    override val compactSlotWidth: Dp = 60.dp

    private val running get() = session.phase == MemoPhase.RECORDING

    @Composable
    override fun Leading() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            LevelBars(CaptureState.memoLevel, accent, Modifier.size(width = 18.dp, height = 16.dp))
        }
    }

    @Composable
    override fun Minimal() {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }

    @Composable
    override fun Trailing() {
        val elapsed = rememberChrono(running, session::elapsed)
        RollingText(
            CaptureFormat.chrono(elapsed),
            style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
            color = if (running) Color.White else Color.White.copy(alpha = 0.55f),
        )
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val ctx = host.context
        val haptic = LocalHapticFeedback.current
        val elapsed = rememberChrono(running, session::elapsed)
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (running) R.string.capture_memo_recording else R.string.capture_memo_paused),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    RollingText(
                        CaptureFormat.chrono(elapsed),
                        style = MaterialTheme.typography.displaySmall.merge(CompactDigits),
                        color = Color.White,
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        if (running) MemoService.pause(ctx) else MemoService.resume(ctx)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (running) R.string.capture_pause else R.string.capture_resume),
                    )
                }
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        MemoService.stop(ctx)
                        host.collapse()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = Color.White),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.capture_stop))
                }
            }
            LevelWave(CaptureState.memoLevel, accent, Modifier.fillMaxWidth().height(32.dp))
        }
    }

    companion object {
        const val KEY = "capture:memo"
        val MemoRed = Color(0xFFFF6B5E)
    }
}
