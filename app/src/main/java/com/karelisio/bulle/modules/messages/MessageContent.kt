package com.karelisio.bulle.modules.messages

import android.app.RemoteInput
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.a11y.IslandSpeech
import com.karelisio.bulle.core.island.IslandAccessibilityAction
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.volet.notifications.AppBadge
import com.karelisio.bulle.modules.volet.notifications.Avatar
import com.karelisio.bulle.modules.volet.notifications.MessageBubble
import com.karelisio.bulle.modules.volet.notifications.NotifAction
import com.karelisio.bulle.modules.volet.notifications.NotifCommands
import com.karelisio.bulle.notifications.NotificationIntents
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Réponse rapide : intent complémentaire portant le texte (même construction que dans le Volet). */
object MessageReply {
    fun fillIn(input: RemoteInput, text: CharSequence, fromChoice: Boolean): Intent =
        NotifCommands.replyFillIn(input, text, fromChoice)
}

/** Un nouveau message dans l'île : compact (avatar + nom), étendu (bulles, réponse rapide). */
data class MessageContent(
    val info: MessageInfo,
    val visibility: MessageVisibility,
    val contentKey: String,
    val baseTimeoutMs: Long,
    val autoExpand: Boolean,
    /** Réponse en cours : le contenu n'expire pas et passe devant les autres alertes. */
    val typing: Boolean,
    /** Fin de la dernière saisie (elapsedRealtime) : la durée d'affichage repart de là. */
    val releasedAt: Long?,
    override val postedAt: Long,
    override val compactSubtitle: String?,
) : IslandContent() {
    override val key: String get() = contentKey
    override val moduleId: String get() = MessagesModule.ID
    override val priority: Priority get() = if (typing) Priority(Priority.ALERT.value + 10) else Priority.ALERT
    override val notificationKey: String get() = info.key
    override val timeoutMs: Long?
        get() = if (typing) null else maxOf(baseTimeoutMs, (releasedAt ?: postedAt) - postedAt + baseTimeoutMs)
    override val autoExpandMs: Long? get() = if (autoExpand && !typing) baseTimeoutMs else null
    override val accent: Color? get() = info.item.color.takeIf { it != 0 }?.let { Color(it).copy(alpha = 1f) }
    override val compactSlotWidth: Dp get() = 84.dp
    override val stayExpandedOnOutsideTouch: Boolean get() = typing

    /** Nom affiché selon la confidentialité. */
    val displayName: String get() = (if (visibility.showSender) info.sender else null) ?: info.appName
    val canReply: Boolean get() = visibility.canReply && info.reply != null

    override fun describe(res: IslandSpeech.Res): String = IslandSpeech.message(
        res,
        info.sender?.takeIf { visibility.showSender },
        info.appName,
        info.messages.lastOrNull()?.text?.toString()?.takeIf { visibility.showText },
        typing,
    )

    override fun accessibilityActions(host: IslandHost, res: IslandSpeech.Res): List<IslandAccessibilityAction> = buildList {
        info.contentIntent?.let { add(IslandAccessibilityAction(res.str(R.string.a11y_action_open)) { NotificationIntents.send(host.context, it) }) }
        info.markAsRead?.takeIf { visibility.canReply }?.let { add(IslandAccessibilityAction(res.str(R.string.a11y_action_mark_read)) { NotificationIntents.send(host.context, it) }) }
    }

    @Composable
    override fun Leading() = SenderAvatar(24.dp)

    @Composable
    override fun Trailing() = AlertLabel(displayName)

    @Composable
    override fun Minimal() = SenderAvatar(22.dp)

    @Composable
    override fun Expanded(host: IslandHost) {
        // Clé explicite : l'état de saisie ne passe jamais d'une conversation à une autre.
        key(contentKey) { MessageExpanded(this, host) }
    }

    /** Toucher la carte ouvre la conversation, sauf pendant une réponse. */
    override fun onExpandedTap(host: IslandHost): Boolean {
        if (typing) return true
        return open(host)
    }

    internal fun open(host: IslandHost): Boolean {
        if (!NotificationIntents.send(host.context, info.contentIntent)) return false
        if (info.item.autoCancel) NotifCommands.cancel(info.key)
        host.collapse()
        host.dismiss(key)
        return true
    }

    @Composable
    internal fun SenderAvatar(size: Dp) {
        if (visibility.showSender && info.avatar != null) {
            Avatar(info.sender, info.avatar, size)
        } else {
            AppBadge(info.item, size)
        }
    }
}

private enum class ReplyStage { IDLE, COMPOSING, SENDING, SENT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageExpanded(c: MessageContent, host: IslandHost) {
    val haptic = LocalHapticFeedback.current
    val dim = Color.White.copy(alpha = 0.65f)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            c.SenderAvatar(44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (c.visibility.showSender) c.info.appName else stringResource(R.string.messages_new),
                    style = MaterialTheme.typography.labelMedium,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (c.visibility.showText) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.info.messages.forEach { m -> MessageBubble(m, showName = c.info.isGroup, expanded = true) }
            }
        } else {
            Text(
                stringResource(if (c.visibility.showSender) R.string.messages_new else R.string.messages_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = dim,
            )
        }
        val reply = c.info.reply
        if (c.canReply && reply != null) {
            Spacer(Modifier.height(10.dp))
            ReplySection(c, reply, host)
        }
        val markAsRead = c.info.markAsRead.takeIf { c.visibility.canReply }
        if (markAsRead != null || c.info.contentIntent != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (markAsRead != null) {
                    FilledTonalButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        NotificationIntents.send(host.context, markAsRead)
                        host.collapse()
                        host.dismiss(c.key)
                    }) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.messages_mark_read), maxLines = 1)
                    }
                }
                if (c.info.contentIntent != null) {
                    FilledTonalButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        c.open(host)
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.messages_open), maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplySection(c: MessageContent, action: NotifAction, host: IslandHost) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var stage by remember { mutableStateOf(ReplyStage.IDLE) }
    var text by remember { mutableStateOf("") }
    val input = action.remoteInput ?: return
    val freeForm = input.allowFreeFormInput

    // Prolonge le contenu tant que le champ est ouvert (focus demandé) ou contient du texte.
    val typing = stage == ReplyStage.COMPOSING || stage == ReplyStage.SENDING || text.isNotEmpty()
    LaunchedEffect(typing) { MessageInput.set(c.key, typing) }
    DisposableEffect(Unit) { onDispose { MessageInput.clear(c.key) } }

    // Saisie clavier : l'île devient focusable le temps de la réponse.
    val composing = stage == ReplyStage.COMPOSING
    DisposableEffect(composing) {
        if (composing) host.requestInput(true)
        onDispose { if (composing) host.requestInput(false) }
    }
    LaunchedEffect(composing) {
        if (!composing) return@LaunchedEffect
        delay(120) // le temps que la fenêtre devienne focusable
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    fun send(value: CharSequence, fromChoice: Boolean) {
        if (value.isBlank() || stage == ReplyStage.SENDING || stage == ReplyStage.SENT) return
        val previous = stage
        stage = ReplyStage.SENDING
        scope.launch {
            val ok = NotificationIntents.send(host.context, action.intent, MessageReply.fillIn(input, value, fromChoice))
            if (ok) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                keyboard?.hide()
                text = ""
                stage = ReplyStage.SENT
                delay(900)
                host.collapse()
                host.dismiss(c.key)
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                stage = previous
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (action.choices.isNotEmpty() && stage != ReplyStage.SENT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                action.choices.forEach { choice ->
                    SuggestionChip(
                        onClick = { send(choice, fromChoice = true) },
                        label = { Text(choice.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        AnimatedContent(
            targetState = stage == ReplyStage.SENT,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { -it / 2 } + fadeOut())
            },
            label = "messageReply",
        ) { sent ->
            when {
                sent -> SentConfirmation()
                !freeForm -> Spacer(Modifier.size(1.dp))
                stage == ReplyStage.IDLE -> Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            stage = ReplyStage.COMPOSING
                        },
                ) {
                    Text(
                        input.label?.toString() ?: stringResource(R.string.messages_reply_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                else -> Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            keyboard?.hide()
                            text = ""
                            stage = ReplyStage.IDLE
                        }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.messages_cancel_reply), tint = Color.White.copy(alpha = 0.7f))
                        }
                        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            if (text.isEmpty()) {
                                Text(
                                    input.label?.toString() ?: stringResource(R.string.messages_reply_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.45f),
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                                cursorBrush = SolidColor(scheme.primary),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { send(text, fromChoice = false) }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focus)
                                    // Focus repris (toucher sur le champ) : l'île redevient focusable.
                                    .onFocusChanged { if (it.isFocused) host.requestInput(true) },
                            )
                        }
                        FilledIconButton(
                            onClick = { send(text, fromChoice = false) },
                            enabled = text.isNotBlank() && stage == ReplyStage.COMPOSING,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.messages_send))
                        }
                    }
                }
            }
        }
    }
}

/** Coche qui apparaît au ressort, puis l'île se replie. */
@Composable
private fun SentConfirmation() {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = rememberReduceMotion()
    val scale = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.messages_sent), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
    }
}
