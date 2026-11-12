package com.karelisio.bulle.modules.alerts.sources

import android.content.ClipboardManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.PopIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private val ClipViolet = Color(0xFFC9A7FF)

/**
 * Copie dans le presse-papiers. Depuis Android 10, seules l'appli au premier plan et le clavier
 * par défaut sont prévenues : le rappel peut ne jamais arriver quand Bulle est en arrière-plan
 * (comportement variable selon les surcouches). Le contenu copié n'est jamais lu.
 */
object ClipboardSource : AlertSource {
    override val id = "clipboard"

    override fun isEnabled(settings: AlertSettings) = settings.clipboard

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow<IslandContent?> {
        val cm = env.context.applicationContext.getSystemService(ClipboardManager::class.java)
        val listener = ClipboardManager.OnPrimaryClipChangedListener { trySend(ClipboardContent(alertNow())) }
        cm.addPrimaryClipChangedListener(listener)
        awaitClose { cm.removePrimaryClipChangedListener(listener) }
    }
        .flowOn(Dispatchers.Main)
        .latestAlive()
}

/** « Copié » : icône qui rebondit, sans jamais afficher le contenu. */
data class ClipboardContent(override val postedAt: Long) : AlertContent() {
    override val key = "alerts:clipboard:$postedAt"
    override val alertId = "clipboard"
    override val accent = ClipViolet
    override val compactSlotWidth = 64.dp
    override val baseTimeoutMs: Long? = 2_500L
    override val expandable = false

    @Composable
    override fun Leading() = PopIcon(Icons.Rounded.ContentCopy, ClipViolet, size = 18.dp)

    @Composable
    override fun Trailing() = AlertLabel(stringResource(R.string.alerts_copied), color = ClipViolet)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(ClipViolet, stringResource(R.string.alerts_copied), null, { PopIcon(Icons.Rounded.ContentCopy, ClipViolet) })
    }
}
