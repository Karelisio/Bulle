package com.karelisio.bulle.modules.volet.background

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
    val hidden by remember { com.karelisio.bulle.core.settings.SettingsRepository.flow(context) }
        .collectAsState(com.karelisio.bulle.core.settings.BulleSettings())
    val hiddenApps = hidden.volet.bgHiddenApps
    val fromNotifs = remember(active, hiddenApps) { BackgroundApps.fromActive(active.values, context.packageName, hiddenApps) }
    var all by remember { mutableStateOf(fromNotifs) }
    // Avec Shizuku : liste complète des services (relue régulièrement tant que le Volet est ouvert).
    LaunchedEffect(fromNotifs) {
        while (true) {
            all = runCatching { BackgroundApps.withServices(context, fromNotifs, hiddenApps) }.getOrDefault(fromNotifs)
            delay(10_000L)
        }
    }
    return all
}

/**
 * Pastille des applis en fond : petit Android + nombre, de la hauteur des boutons ronds de
 * l'en-tête (40 dp, 36 dp en paysage). Rien si aucune appli en fond.
 */
@Composable
fun BackgroundAppsChip(count: Int, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, dot: Boolean = false) {
    if (count <= 0) return
    val scheme = MaterialTheme.colorScheme
    val fg = if (open) scheme.onPrimary else scheme.onSecondaryContainer
    Row(
        modifier
            .height(if (compact) 36.dp else 40.dp)
            .widthIn(min = if (compact) 36.dp else 40.dp)
            .clip(CircleShape)
            .background(if (open) scheme.primary else scheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Android,
            contentDescription = pluralStringResource(R.plurals.volet_bg_count, count, count),
            tint = fg,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("$count", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false)
        // Nouvelle appli en fond depuis la dernière ouverture de la liste.
        if (dot && !open) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(scheme.error))
        }
    }
}

/** Liste des applis en fond (sous l'en-tête) : ouvrir, arrêter. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BackgroundAppsPanel(host: VoletHost, apps: List<BackgroundApp>, visible: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canForce = remember(visible) { BackgroundApps.canForceStop() }
    val full = remember(visible) { BackgroundApps.fullListAvailable() }
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        // Appui long : fiche de l'appli (batterie, forcer l'arrêt).
                        .combinedClickable(onClick = {}, onLongClick = { host.startActivity(BackgroundApps.detailsIntent(app.packageName)) })
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape)) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val minutes = BackgroundApps.sinceMinutes(app.since, System.currentTimeMillis())
                        val sinceText = minutes?.let {
                            if (it >= 60) stringResource(R.string.volet_bg_since_h, it / 60) else stringResource(R.string.volet_bg_since_min, it.coerceAtLeast(1))
                        }
                        listOfNotNull(app.details.firstOrNull(), sinceText).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    androidx.compose.material3.IconButton(onClick = {
                        scope.launch {
                            com.karelisio.bulle.core.settings.SettingsRepository.update(context) {
                                it.copy(volet = it.volet.copy(bgHiddenApps = it.volet.bgHiddenApps + app.packageName))
                            }
                        }
                    }) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.VisibilityOff, contentDescription = stringResource(R.string.volet_bg_hide, label), modifier = Modifier.size(18.dp))
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
            val stoppable = BackgroundApps.stoppable(apps)
            if (canForce && stoppable.size > 1) {
                TextButton(onClick = {
                    scope.launch { stoppable.forEach { BackgroundApps.stop(context, it.packageName) } }
                }, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(stringResource(R.string.volet_bg_stop_all, stoppable.size))
                }
            }
            Text(
                stringResource(if (full) R.string.volet_bg_note_shizuku else R.string.volet_bg_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
    }
}
