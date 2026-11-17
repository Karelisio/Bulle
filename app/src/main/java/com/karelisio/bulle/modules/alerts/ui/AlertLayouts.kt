package com.karelisio.bulle.modules.alerts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Mise en page commune de la vue étendue d'une alerte :
 * pastille teintée de l'accent + titre + sous-titre, puis une rangée d'actions facultative.
 */
@Composable
fun AlertExpanded(
    accent: Color,
    title: String,
    subtitle: String?,
    glyph: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val accent = tinted(accent)
    val icon = LocalAlertIcon.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp)
                    .alertEntrance(LocalAlertEntrance.current, title)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                // Icône choisie par l'utilisateur à la place du glyphe animé.
                if (icon != null) Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp)) else glyph()
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (extra != null) {
            Spacer(Modifier.height(12.dp))
            extra()
        }
        // « Ne plus montrer ce type » : coupe cette alerte (réactivable dans Contenus › Alertes).
        LocalAlertId.current?.let { id ->
            val def = remember(id) { runCatching { com.karelisio.bulle.modules.alerts.AlertCatalog.def(id) }.getOrNull() }
            if (def != null) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                androidx.compose.material3.TextButton(
                    onClick = {
                        scope.launch {
                            com.karelisio.bulle.core.settings.SettingsRepository.update(context) { it.copy(alerts = def.set(it.alerts, false)) }
                            com.karelisio.bulle.island.IslandLog.log("Alerte « $id » coupée depuis l'île")
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(androidx.compose.ui.res.stringResource(com.karelisio.bulle.R.string.alerts_never_show), color = Color.White.copy(alpha = 0.6f)) }
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
