package com.karelisio.bulle.modules.volet.background

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.live.LiveModule
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.launch

/** Applis en fond, en direct (notifications de service au premier plan). */
@Composable
fun rememberBackgroundApps(): List<BackgroundApp> {
    val context = LocalContext.current
    val active by NotificationRepository.active.collectAsState()
    return remember(active) { BackgroundApps.fromActive(active.values, context.packageName) }
}

/** Pastille « n actives » de l'en-tête du Volet (rien si aucune appli en fond). */
@Composable
fun BackgroundAppsChip(count: Int, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .background(if (open) scheme.primary else scheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Memory, contentDescription = null, tint = if (open) scheme.onPrimary else scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            pluralStringResource(R.plurals.volet_bg_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = if (open) scheme.onPrimary else scheme.onSecondaryContainer,
        )
    }
}

/** Liste des applis en fond (sous l'en-tête) : ouvrir, arrêter. */
@Composable
fun BackgroundAppsPanel(host: VoletHost, apps: List<BackgroundApp>, visible: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canForce = remember(visible) { BackgroundApps.canForceStop() }
    AnimatedVisibility(visible && apps.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.volet_bg_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            apps.forEach { app ->
                val label = remember(app.packageName) { LiveModule.appLabel(context, app.packageName) ?: app.packageName }
                val icon = remember(app.packageName) {
                    runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(64, 64).asImageBitmap() }.getOrNull()
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape)) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        app.details.firstOrNull()?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = {
                        if (app.contentIntent == null || !host.send(app.contentIntent)) {
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(host::startActivity)
                        }
                    }) { Text(stringResource(R.string.volet_bg_open)) }
                    FilledTonalButton(onClick = {
                        scope.launch {
                            if (!BackgroundApps.stop(context, app.packageName)) host.close()
                        }
                    }) { Text(stringResource(if (canForce) R.string.volet_bg_stop else R.string.volet_bg_details)) }
                }
            }
            Text(
                stringResource(if (canForce) R.string.volet_bg_note_shizuku else R.string.volet_bg_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
    }
}
