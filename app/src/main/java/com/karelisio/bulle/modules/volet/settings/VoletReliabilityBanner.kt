package com.karelisio.bulle.modules.volet.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.system.Reliability
import kotlinx.coroutines.flow.map

/**
 * Bannière de l'accueil : n'affiche rien si tout va bien ; sinon signale que le Volet ne peut
 * pas fonctionner (service tué ou désactivé par MagicOS) et propose « Réactiver ».
 */
@Composable
fun VoletReliabilityBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val voletEnabled by remember { SettingsRepository.flow(context).map { it.volet.enabled } }
        .collectAsStateWithLifecycle(false)
    val running by IslandAccessibilityService.running.collectAsStateWithLifecycle()
    val connected by NotificationRepository.connected.collectAsStateWithLifecycle()
    // Les réglages système ne sont pas observables : relus à chaque retour au premier plan.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val state = remember(voletEnabled, running, connected, resumes) {
        VoletServiceState(
            voletEnabled = voletEnabled,
            accessibilityEnabled = Reliability.isAccessibilityEnabled(context),
            accessibilityRunning = running,
            listenerGranted = NotificationRepository.isAccessGranted(context),
            listenerConnected = connected,
        )
    }
    val issue = VoletReliability.issues(state).firstOrNull()

    AnimatedVisibility(
        visible = issue != null,
        enter = fadeIn() + expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val current = issue ?: return@AnimatedVisibility
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.vset_rel_title), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    stringResource(
                        if (VoletReliability.isAccessibility(current)) {
                            R.string.vset_rel_text_accessibility
                        } else {
                            R.string.vset_rel_text_listener
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        VoletReliability.fix(context, current)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.vset_rel_action)) }
            }
        }
    }
}
