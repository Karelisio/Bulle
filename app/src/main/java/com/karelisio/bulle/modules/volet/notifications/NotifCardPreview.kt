package com.karelisio.bulle.modules.volet.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZone

/** Hôte inerte de l'aperçu : aucune action n'est déclenchée. */
private class PreviewHost(override val context: Context, override val settings: VoletSettings) : VoletHost {
    override val expansion = 1f
    override val zone = VoletZone.NOTIFICATIONS
    override val locked = false
    override val focusKey: String? = null
    override var editingTiles = false
    override fun close() = Unit
    override fun expandFull() = Unit
    override fun collapseToHalf() = Unit
    override fun dragBy(dyPx: Float) = Unit
    override fun settle(velocityPxPerSec: Float) = Unit
    override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean) = false
    override fun startActivity(intent: Intent) = Unit
    override fun runAction(action: GestureAction) = Unit
    override fun requestKeyboard(show: Boolean) = Unit
    override fun openSystemShade() = Unit
}

/** Notification d'exemple (aucune intention attachée). */
internal fun sampleNotifItem(
    key: String,
    appName: String,
    title: CharSequence,
    text: CharSequence,
    smallIcon: Icon?,
    messages: List<NotifMessage> = emptyList(),
    section: NotifSection = NotifSection.ALERTING,
): NotifItem = NotifItem(
    key = key, packageName = "preview", appName = appName, groupKey = key, channelId = null, shortcutId = null,
    time = System.currentTimeMillis() - 4 * 60_000L, showTime = true, title = title, text = text, subText = null,
    bigText = null, lines = emptyList(), messages = messages, conversationTitle = null, isGroupConversation = false,
    largeIcon = null, picture = null, smallIcon = smallIcon, color = 0, progress = null, actions = emptyList(),
    contentIntent = null, autoCancel = false, clearable = true, persistent = false, isSummary = false,
    section = section, rank = -1, redacted = false, customView = null,
)

/**
 * Aperçu en direct des cartes du volet (une conversation et une notification) avec la
 * densité, la taille du texte, les coins et le nombre de lignes de [settings].
 */
@Composable
internal fun NotifCardPreview(settings: VoletSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dims = NotifDims.of(settings)
    val host = remember(context, settings) { PreviewHost(context, settings) }
    val sender = stringResource(R.string.vset_cards_sample_sender)
    val message = stringResource(R.string.vset_cards_sample_message)
    val app = stringResource(R.string.vset_cards_sample_app)
    val title = stringResource(R.string.vset_cards_sample_title)
    val text = stringResource(R.string.vset_cards_sample_text)
    val items = remember(sender, message, app, title, text) {
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_bulle)
        listOf(
            sampleNotifItem(
                "preview|chat", app, sender, message, icon,
                messages = listOf(NotifMessage(sender, message, System.currentTimeMillis(), null, fromSelf = false)),
                section = NotifSection.CONVERSATIONS,
            ),
            sampleNotifItem("preview|mail", app, title, text, icon),
        )
    }
    val base = LocalDensity.current
    val density = remember(base, dims.textScale) {
        if (dims.textScale == 1f) base else Density(base.density, base.fontScale * dims.textScale)
    }
    CompositionLocalProvider(LocalDensity provides density) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dims.gap)) {
            items.forEach { NotifCard(it, host, dims, focused = false, initiallyExpanded = false) }
        }
    }
}
