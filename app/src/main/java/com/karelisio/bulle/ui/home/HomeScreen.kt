package com.karelisio.bulle.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.HiddenReason
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.island.islandHiddenReason
import com.karelisio.bulle.modules.push.DemoContent
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.modules.volet.settings.VoletReliabilityBanner
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.system.Reliability
import com.karelisio.bulle.ui.Routes
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.sharedElement
import com.karelisio.bulle.ui.onboarding.PermissionChecks
import com.karelisio.bulle.ui.onboarding.PermissionKey
import com.karelisio.bulle.ui.settings.IslandPreview
import kotlinx.coroutines.launch

/**
 * Onglet « Accueil » : aperçu de l'île (élément partagé vers l'écran Style), état du service
 * et des accès, bascule de l'île et bouton de test. Les réglages sont dans les autres onglets.
 * [contentPadding] réserve la place des barres système et de la barre flottante.
 */
@Composable
fun HomeScreen(onOpen: (String) -> Unit = {}, contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val running by IslandAccessibilityService.running.collectAsStateWithLifecycle()
    var enabledInSystem by remember { mutableStateOf(Reliability.isAccessibilityEnabled(context)) }
    var notifGranted by remember { mutableStateOf(NotificationRepository.isAccessGranted(context)) }
    LifecycleResumeEffect(Unit) {
        enabledInSystem = Reliability.isAccessibilityEnabled(context)
        notifGranted = NotificationRepository.isAccessGranted(context)
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        IslandPreview(settings.style, settings.motion, Modifier.sharedElement("island_preview").clickable { onOpen(Routes.STYLE) })
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        VoletReliabilityBanner(Modifier.fillMaxWidth().padding(bottom = 12.dp).cascade(0))
        ServiceCard(
            running = running,
            enabledInSystem = enabledInSystem,
            // Via PermissionChecks : intents gardés (ActivityNotFoundException) avec repli,
            // l'écran de détail de l'accès aux notifications manquant sur certaines surcouches.
            onOpenAccessibility = { PermissionChecks.openSettings(context, PermissionKey.ACCESSIBILITY) },
            modifier = Modifier.cascade(1),
        )
        Spacer(Modifier.height(12.dp))
        StatusCard(
            ok = notifGranted,
            title = stringResource(R.string.home_notif_title),
            text = stringResource(if (notifGranted) R.string.home_notif_on else R.string.home_notif_off),
            action = stringResource(R.string.home_open_notif_access),
            onAction = { PermissionChecks.openSettings(context, PermissionKey.NOTIFICATION_ACCESS) },
            modifier = Modifier.cascade(2),
        )
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().cascade(3), shape = MaterialTheme.shapes.extraLarge) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_island_switch),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.islandEnabled,
                    onCheckedChange = { on ->
                        scope.launch { SettingsRepository.update(context) { it.copy(islandEnabled = on) } }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(
            enabled = running && settings.islandEnabled,
            onClick = { PushModule.push(DemoContent()) },
            modifier = Modifier.cascade(4),
        ) { Text(stringResource(R.string.home_test_island)) }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ServiceCard(
    running: Boolean,
    enabledInSystem: Boolean,
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reason by islandHiddenReason.collectAsStateWithLifecycle()
    val base = stringResource(
        when {
            running -> R.string.home_service_running
            enabledInSystem -> R.string.home_service_enabled_not_running
            else -> R.string.home_service_off
        },
    )
    // Diagnostic : pourquoi l'île est masquée en ce moment.
    val why = if (running) reason?.let { stringResource(R.string.home_hidden_reason, stringResource(it.labelRes())) } else null
    StatusCard(
        ok = running,
        title = stringResource(R.string.home_service_title),
        text = if (why != null) "$base\n$why" else base,
        action = stringResource(R.string.home_open_accessibility),
        onAction = onOpenAccessibility,
        modifier = modifier,
    )
}

@Composable
private fun StatusCard(
    ok: Boolean,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(ok, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "icon") { good ->
                    Icon(
                        if (good) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (!ok) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

private fun HiddenReason.labelRes(): Int = when (this) {
    HiddenReason.DISABLED -> R.string.hidden_disabled
    HiddenReason.RULE -> R.string.hidden_rule
    HiddenReason.FULLSCREEN -> R.string.hidden_fullscreen
    HiddenReason.LANDSCAPE -> R.string.hidden_landscape
    HiddenReason.LOCKSCREEN -> R.string.hidden_lockscreen
    HiddenReason.HIDDEN_APP -> R.string.hidden_app
}
