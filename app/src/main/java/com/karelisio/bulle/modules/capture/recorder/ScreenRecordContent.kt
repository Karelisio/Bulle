package com.karelisio.bulle.modules.capture.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.capture.CaptureFormat
import com.karelisio.bulle.modules.capture.CaptureModule
import com.karelisio.bulle.modules.capture.RecorderSession
import com.karelisio.bulle.modules.capture.rememberChrono
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.PulseDot
import com.karelisio.bulle.modules.live.ui.RollingText

/**
 * Enregistrement d'écran en cours : point rouge qui respire + chrono. Toucher l'île ouvre la
 * confirmation d'arrêt (« Arrêter » / « Continuer »).
 */
data class ScreenRecordContent(val session: RecorderSession) : IslandContent() {
    override val key = KEY
    override val moduleId = CaptureModule.ID
    override val priority = Priority.LIVE
    override val postedAt: Long = session.startedAt
    override val accent: Color = RecRed
    override val compactSlotWidth: Dp = 60.dp
    override val showWhenHidden: Boolean = false

    @Composable
    override fun Leading() = PulseDot(accent, Modifier.size(16.dp))

    @Composable
    override fun Trailing() {
        val elapsed = rememberChrono(true, session::elapsed)
        RollingText(CaptureFormat.chrono(elapsed), style = MaterialTheme.typography.labelLarge.merge(CompactDigits), color = Color.White)
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val haptic = LocalHapticFeedback.current
        val elapsed = rememberChrono(true, session::elapsed)
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(accent, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.capture_screen_stop_confirm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RollingText(CaptureFormat.chrono(elapsed), style = MaterialTheme.typography.labelLarge.merge(CompactDigits), color = accent)
                        if (session.withAudio) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.capture_screen_with_audio), tint = accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = host::collapse) { Text(stringResource(R.string.capture_continue)) }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        ScreenRecordService.stop(host.context)
                        host.collapse()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.capture_stop))
                }
            }
        }
    }

    companion object {
        const val KEY = "capture:screen"
        val RecRed = Color(0xFFFF4D4D)
    }
}
