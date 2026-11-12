package com.karelisio.bulle.modules.push

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.ui.components.rememberReduceMotion

/** Alerte de démonstration : vérifie le gonflement et la file de priorité. */
data class DemoContent(
    override val postedAt: Long = android.os.SystemClock.elapsedRealtime(),
) : IslandContent() {
    override val key = "push:demo"
    override val moduleId = PushModule.id
    override val priority = Priority.ALERT
    override val timeoutMs = 8_000L
    override val autoExpandMs = 3_000L
    override val accent = Color(0xFF8FA8FF)

    @Composable
    override fun Leading() = Sparkle(18)

    @Composable
    override fun Trailing() {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Sparkle(24) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.demo_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = { host.dismiss(key) }) { Text(stringResource(R.string.demo_close)) }
            }
        }
    }

    @Composable
    private fun Sparkle(sizeDp: Int) {
        // Animation infinie coupée si les animations sont supprimées ; valeur lue au dessin
        // uniquement (pas de recomposition à chaque image).
        val anim = if (!rememberReduceMotion()) {
            rememberInfiniteTransition(label = "sparkle")
                .animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "r")
        } else {
            null
        }
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(sizeDp.dp)
                .graphicsLayer { val r = anim?.value ?: 0.5f; rotationZ = -12f + 24f * r; scaleX = 0.9f + 0.1f * r; scaleY = scaleX },
        )
    }
}
