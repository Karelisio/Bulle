package com.karelisio.bulle.modules.capture.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.capture.CaptureModule
import com.karelisio.bulle.modules.capture.CaptureState

/** Résultat d'un scan QR : alerte ponctuelle (8 s) qui s'ouvre d'elle-même avec les actions adaptées. */
data class QrResultContent(
    val payload: QrPayload,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "capture:qr:$postedAt"
    override val moduleId = CaptureModule.ID
    override val priority = Priority.ALERT
    override val timeoutMs: Long = TIMEOUT_MS
    override val autoExpandMs: Long = TIMEOUT_MS
    override val accent: Color = Color(0xFF7FD7C4)
    override val compactSlotWidth: Dp = 84.dp
    override val canBeSecondary: Boolean = false

    @Composable
    override fun Leading() {
        Icon(iconOf(payload), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }

    @Composable
    override fun Trailing() {
        Text(
            stringResource(labelOf(payload)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val ctx = host.context
        fun done() {
            CaptureState.clearQr()
            host.collapse()
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(iconOf(payload), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(labelOf(payload)), style = MaterialTheme.typography.labelLarge, color = accent)
                    Text(
                        summaryOf(payload),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (payload !is QrPayload.Text) {
                    FilledTonalButton(onClick = { QrIntents.open(ctx, payload); done() }) {
                        Icon(actionIconOf(payload), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(actionOf(payload)), maxLines = 1)
                    }
                }
                val copyText = (payload as? QrPayload.Wifi)?.password ?: payload.raw
                TextButton(onClick = {
                    QrIntents.copy(ctx, copyText, if (payload is QrPayload.Wifi) R.string.capture_qr_copied_password else R.string.capture_qr_copied)
                    done()
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (payload is QrPayload.Wifi && payload.password != null) R.string.capture_qr_copy_password else R.string.capture_qr_copy), maxLines = 1)
                }
                if (payload is QrPayload.Text) {
                    TextButton(onClick = { QrIntents.share(ctx, payload.raw); done() }) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.capture_share), maxLines = 1)
                    }
                }
            }
        }
    }

    companion object {
        const val TIMEOUT_MS = 8_000L

        fun iconOf(p: QrPayload): ImageVector = when (p) {
            is QrPayload.Url -> Icons.Rounded.Language
            is QrPayload.Wifi -> Icons.Rounded.Wifi
            is QrPayload.Contact -> Icons.Rounded.PersonAdd
            is QrPayload.Phone -> Icons.Rounded.Call
            is QrPayload.Sms -> Icons.Rounded.Sms
            is QrPayload.Email -> Icons.Rounded.Email
            is QrPayload.Geo -> Icons.Rounded.Place
            is QrPayload.Text -> Icons.Rounded.QrCode2
        }

        private fun actionIconOf(p: QrPayload): ImageVector = when (p) {
            is QrPayload.Url, is QrPayload.Geo -> Icons.AutoMirrored.Rounded.OpenInNew
            else -> iconOf(p)
        }

        fun labelOf(p: QrPayload): Int = when (p) {
            is QrPayload.Url -> R.string.capture_qr_type_url
            is QrPayload.Wifi -> R.string.capture_qr_type_wifi
            is QrPayload.Contact -> R.string.capture_qr_type_contact
            is QrPayload.Phone -> R.string.capture_qr_type_phone
            is QrPayload.Sms -> R.string.capture_qr_type_sms
            is QrPayload.Email -> R.string.capture_qr_type_email
            is QrPayload.Geo -> R.string.capture_qr_type_geo
            is QrPayload.Text -> R.string.capture_qr_type_text
        }

        fun actionOf(p: QrPayload): Int = when (p) {
            is QrPayload.Url -> R.string.capture_qr_open
            is QrPayload.Wifi -> R.string.capture_qr_connect
            is QrPayload.Contact -> R.string.capture_qr_add_contact
            is QrPayload.Phone -> R.string.capture_qr_call
            is QrPayload.Sms -> R.string.capture_qr_sms
            is QrPayload.Email -> R.string.capture_qr_email
            is QrPayload.Geo -> R.string.capture_qr_map
            is QrPayload.Text -> R.string.capture_share
        }

        /** Ligne principale lisible (sans mot de passe en clair). */
        fun summaryOf(p: QrPayload): String = when (p) {
            is QrPayload.Url -> p.url.removePrefix("https://").removePrefix("http://")
            is QrPayload.Wifi -> p.ssid
            is QrPayload.Contact -> p.name ?: p.phones.firstOrNull() ?: p.emails.firstOrNull() ?: p.raw.take(60)
            is QrPayload.Phone -> p.number
            is QrPayload.Sms -> p.number + (p.body?.let { " · $it" } ?: "")
            is QrPayload.Email -> p.address
            is QrPayload.Geo -> p.query ?: "%.5f, %.5f".format(java.util.Locale.ROOT, p.latitude, p.longitude)
            is QrPayload.Text -> p.raw
        }
    }
}
