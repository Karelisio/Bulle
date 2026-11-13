package com.karelisio.bulle.modules.external.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.ecosystem.Ecosystem
import com.karelisio.bulle.modules.ecosystem.KarelisioApp
import com.karelisio.bulle.modules.external.ExternalAppInfo
import com.karelisio.bulle.modules.external.ExternalIcons
import com.karelisio.bulle.modules.external.ExternalModule
import com.karelisio.bulle.modules.external.ExternalSamples
import com.karelisio.bulle.modules.external.ExternalSpec
import com.karelisio.bulle.modules.external.ExternalTemplate
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.ui.components.cascade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Adresse de la documentation de l'API d'intégration. */
val IntegrationDocUrl: String = "https://github.com/${BuildConfig.GITHUB_REPO}/blob/main/docs/INTEGRATION.md"

/**
 * Écran « Intégrations » : applis qui ont poussé du contenu vers l'île (autoriser / bloquer,
 * contenus en cours), explication, lien vers la documentation et bouton « Tester ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntegrationsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    remember(context) { ExternalModule.ensureLoaded(context); true }
    val apps by ExternalModule.apps.collectAsStateWithLifecycle()
    val board by ExternalModule.board.collectAsStateWithLifecycle()

    ModuleSettingsScaffold(title = stringResource(R.string.ext_settings_title), onBack = onBack) {
        item(key = "intro") {
            ModuleSettingsCard(Modifier.cascade(0)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ext_settings_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.ext_settings_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            ExternalModule.runDemo(context)
                        }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ext_settings_test))
                        }
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, IntegrationDocUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ext_settings_doc))
                        }
                    }
                }
            }
        }

        item(key = "templates") {
            ModuleSettingsCard(Modifier.cascade(1)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ext_settings_templates), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExternalTemplate.entries.forEach { t ->
                            AssistChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    val sample = ExternalSamples.of(context, t, System.currentTimeMillis())
                                    ExternalModule.pushLocal(sample.copy(timeoutMs = 8_000L, autoExpand = true))
                                },
                                label = { Text(templateLabel(t)) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "apps-header") { ModuleSectionHeader(stringResource(R.string.ext_settings_apps)) }
        val known = apps.entries.sortedByDescending { it.value.lastSeen }
        if (known.isEmpty()) {
            item(key = "apps-empty") {
                ModuleSettingsCard(Modifier.cascade(2)) {
                    Text(
                        stringResource(R.string.ext_settings_no_apps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else {
            items(known, key = { "app:" + it.key }) { (pkg, info) ->
                val current = board.forApp(pkg).filter { !it.local }
                AppCard(pkg, info, current, onBlocked = { ExternalModule.setBlocked(context, pkg, it) })
            }
        }

        item(key = "eco-header") { ModuleSectionHeader(stringResource(R.string.ext_settings_ecosystem)) }
        item(key = "eco") {
            ModuleSettingsCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    KarelisioApp.entries.forEachIndexed { i, app ->
                        val installed = remember(app) { Ecosystem.isInstalled(context, app.packageName) }
                        ListItem(
                            headlineContent = { Text(stringResource(app.nameRes)) },
                            supportingContent = { Text(stringResource(app.roleRes)) },
                            trailingContent = {
                                Text(
                                    stringResource(if (installed) R.string.ext_settings_installed else R.string.ext_settings_not_installed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (i < KarelisioApp.entries.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

/** Carte d'une appli : icône, nom, dernière activité, interrupteur, contenus en cours. */
@Composable
private fun AppCard(pkg: String, info: ExternalAppInfo, current: List<ExternalSpec>, onBlocked: (Boolean) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val label = remember(pkg) { ExternalIcons.appLabel(context, pkg) }
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { ExternalIcons.appIcon(context, pkg, 96)?.asImageBitmap() }
    }
    ModuleSettingsCard {
        Column {
            ListItem(
                leadingContent = {
                    val b = icon
                    if (b != null) {
                        Image(b, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                    } else {
                        Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                },
                headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val ago = DateUtils.getRelativeTimeSpanString(info.lastSeen, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    Text(
                        if (info.blocked) stringResource(R.string.ext_settings_blocked) else stringResource(R.string.ext_settings_last_seen, ago),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = !info.blocked,
                        onCheckedChange = { allowed ->
                            haptic.performHapticFeedback(if (allowed) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            onBlocked(!allowed)
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            current.forEach { spec ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = 8.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            spec.title ?: spec.text ?: spec.id,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            templateLabel(spec.template),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); ExternalModule.dismiss(spec.key) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ext_dismiss))
                    }
                }
            }
            if (current.isNotEmpty()) Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun templateLabel(t: ExternalTemplate): String = stringResource(
    when (t) {
        ExternalTemplate.STANDARD -> R.string.ext_tpl_standard
        ExternalTemplate.PROGRESS -> R.string.ext_tpl_progress
        ExternalTemplate.COUNTDOWN -> R.string.ext_tpl_countdown
        ExternalTemplate.CHRONOMETER -> R.string.ext_tpl_chronometer
        ExternalTemplate.LIST -> R.string.ext_tpl_list
        ExternalTemplate.STATUS -> R.string.ext_tpl_status
        ExternalTemplate.HEART -> R.string.ext_tpl_heart
        ExternalTemplate.BREATHING -> R.string.ext_tpl_breathing
    },
)
