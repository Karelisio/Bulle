package com.karelisio.bulle.modules.volet.notifications

import android.graphics.drawable.Icon
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.notifications.NotificationIcons
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign

/** Dimensions selon la densité du volet. */
internal data class NotifDims(val pad: Dp, val gap: Dp, val badge: Dp, val corner: Dp, val compact: Boolean)

private val Spring1 = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

// --- Carte de groupe ---

/** Groupe : carte simple (1 notification) ou pile repliable (en-tête, aperçu, enfants). */
@Composable
internal fun NotifGroupCard(
    group: NotifGroup,
    host: VoletHost,
    dims: NotifDims,
    expanded: Boolean,
    onToggle: () -> Unit,
    focusKey: String?,
    clearing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (group.items.size == 1) {
        val item = group.lead
        SwipeToDismiss(item.clearable, clearing && item.clearable, onDismiss = { dismissItem(group, item) }, modifier) {
            NotifCard(item, host, dims, focused = item.key == focusKey, initiallyExpanded = item.key == focusKey)
        }
        return
    }
    val shape = RoundedCornerShape(dims.corner)
    SwipeToDismiss(
        enabled = !expanded && group.clearable,
        clearing = clearing && group.clearable,
        onDismiss = { group.items.filter { it.clearable }.forEach { NotifCommands.cancel(it.key) }; group.summaryKey?.let(NotifCommands::cancel) },
        modifier = modifier,
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .halo(group.items.any { it.key == focusKey }, shape)
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
        ) {
            Column {
                GroupHeader(group, dims, expanded, onToggle)
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessHigh)) using SizeTransform(clip = false) },
                    label = "group",
                ) { open ->
                    if (open) {
                        Column(
                            Modifier.padding(start = dims.gap, end = dims.gap, bottom = dims.gap),
                            verticalArrangement = Arrangement.spacedBy(dims.gap / 2),
                        ) {
                            group.items.forEach { item ->
                                androidx.compose.runtime.key(item.key) {
                                    SwipeToDismiss(item.clearable, false, onDismiss = { dismissItem(group, item) }) {
                                        NotifCard(item, host, dims, focused = item.key == focusKey, initiallyExpanded = item.key == focusKey, nested = true)
                                    }
                                }
                            }
                        }
                    } else {
                        Column {
                            NotifBody(group.lead, host, dims, expanded = false, onToggle = onToggle, showApp = false, onReply = null)
                            group.items.drop(1).take(2).forEach { PreviewLine(it, dims) }
                            if (group.items.size > 3) {
                                Text(
                                    stringResource(R.string.notif_more, group.items.size - 3),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = dims.pad + dims.badge + 12.dp, bottom = dims.pad),
                                )
                            } else {
                                Spacer(Modifier.size(dims.pad / 2))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dismissItem(group: NotifGroup, item: NotifItem) {
    NotifCommands.cancel(item.key)
    // Dernier enfant effacé : le résumé part avec lui.
    if (group.items.size == 1) group.summaryKey?.let(NotifCommands::cancel)
}

@Composable
private fun GroupHeader(group: NotifGroup, dims: NotifDims, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Spring1, label = "chevron")
    val expandLabel = stringResource(if (expanded) R.string.notif_collapse else R.string.notif_expand)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { contentDescription = expandLabel }
            .padding(start = dims.pad, end = dims.pad / 2, top = dims.pad, bottom = dims.gap / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge(group.lead, dims.badge * 0.75f)
        Spacer(Modifier.width(10.dp))
        Text(
            group.appName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${group.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                M3Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                )
            }
        }
    }
}

@Composable
private fun PreviewLine(item: NotifItem, dims: NotifDims) {
    Row(Modifier.padding(start = dims.pad + dims.badge + 12.dp, end = dims.pad, bottom = 4.dp)) {
        Text(
            item.title?.toString() ?: item.appName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            (if (item.redacted) stringResource(R.string.notif_hidden) else item.messages.lastOrNull()?.text ?: item.text)?.toString().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- Carte d'une notification ---

@Composable
internal fun NotifCard(
    item: NotifItem,
    host: VoletHost,
    dims: NotifDims,
    focused: Boolean,
    initiallyExpanded: Boolean,
    nested: Boolean = false,
) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(initiallyExpanded) }
    var menu by remember(item.key) { mutableStateOf(false) }
    var replyAction by remember(item.key) { mutableIntStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    // La carte s'enfonce légèrement au toucher.
    val press by animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    val shape = RoundedCornerShape(if (nested) dims.corner * 0.6f else dims.corner)
    val scheme = MaterialTheme.colorScheme
    val container = when {
        nested -> scheme.surfaceContainerHigh
        item.persistent -> scheme.surfaceContainerLow
        else -> scheme.surfaceContainer
    }
    Surface(
        shape = shape,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .halo(focused, shape)
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    when {
                        menu -> Unit
                        item.contentIntent != null && !item.redacted -> NotifCommands.open(host, item)
                        item.expandable -> expanded = !expanded
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            )
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        AnimatedContent(
            targetState = menu,
            transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessHigh)) using SizeTransform(clip = false) },
            label = "menu",
        ) { showMenu ->
            if (showMenu) {
                NotifMenu(item, host, dims) { menu = false }
            } else {
                Column {
                    NotifBody(
                        item, host, dims,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        showApp = true,
                        onReply = { replyAction = it },
                    )
                    val reply = item.actions.firstOrNull { it.index == replyAction && it.remoteInput != null }
                    AnimatedVisibility(
                        visible = reply != null,
                        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
                    ) {
                        // Garde l'action affichée pendant la sortie animée.
                        val shown = remember { mutableStateOf(reply) }
                        if (reply != null) shown.value = reply
                        shown.value?.let { a ->
                            ReplyArea(a, host, dims, onClose = {
                                replyAction = -1
                                host.requestKeyboard(false)
                            })
                        }
                    }
                }
            }
        }
    }
}

/** Contenu reconstruit : en-tête, titre, texte / conversation / lignes, image, progression, actions. */
@Composable
private fun NotifBody(
    item: NotifItem,
    host: VoletHost,
    dims: NotifDims,
    expanded: Boolean,
    onToggle: () -> Unit,
    showApp: Boolean,
    onReply: ((Int) -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = item.persistent
    val pad = if (muted) dims.pad * 0.75f else dims.pad
    val textAlpha = if (muted) 0.78f else 1f
    Column(Modifier.padding(horizontal = pad, vertical = pad * 0.85f)) {
        Row(verticalAlignment = Alignment.Top) {
            if (showApp) {
                AppBadge(item, if (muted) dims.badge * 0.8f else dims.badge)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).graphicsLayer { alpha = textAlpha }) {
                MetaLine(item, showApp, expanded, onToggle)
                item.title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.toString(),
                        style = if (muted) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    item.redacted -> BodyText(stringResource(R.string.notif_hidden), 1)
                    item.customView != null -> CustomContent(item.customView, item)
                    item.isMessaging -> Messages(item, expanded)
                    else -> {
                        val body = if (expanded) item.bigText ?: item.text else item.text
                        body?.takeIf { it.isNotBlank() }?.let { BodyText(it.toString(), if (expanded) 12 else if (muted) 1 else 2) }
                        if (expanded && item.lines.isNotEmpty()) {
                            Spacer(Modifier.size(4.dp))
                            item.lines.take(7).forEach { BodyText(it.toString(), 1) }
                        }
                    }
                }
                item.progress?.let { p ->
                    Spacer(Modifier.size(8.dp))
                    if (p.indeterminate) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (!item.isMessaging && item.largeIcon != null && !item.redacted) {
                Spacer(Modifier.width(12.dp))
                IconImage(item.largeIcon, 44.dp, Modifier.clip(RoundedCornerShape(12.dp)))
            }
        }
        if (expanded && item.picture != null) {
            Spacer(Modifier.size(10.dp))
            IconImage(
                item.picture, null,
                Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(dims.corner * 0.6f)),
                ContentScale.Crop,
            )
        }
        val actions = item.actions
        if (expanded && actions.isNotEmpty() && onReply != null) {
            Spacer(Modifier.size(dims.gap))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                actions.take(3).forEach { a ->
                    FilledTonalButton(
                        onClick = { if (a.remoteInput != null) onReply(a.index) else host.send(a.intent) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Text(a.title.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(item: NotifItem, showApp: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val time = if (item.showTime) relativeTimeLabel(item.time) else null
    val parts = listOfNotNull(item.appName.takeIf { showApp }, item.subText?.toString()?.takeIf { it.isNotBlank() && !item.redacted }, time)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.expandable && showApp) {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, Spring1, label = "chevron")
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                M3Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.notif_collapse else R.string.notif_expand),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                )
            }
        }
    }
}

@Composable
private fun BodyText(text: String, maxLines: Int) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun relativeTimeLabel(time: Long): String {
    val r = RelativeTime.of(System.currentTimeMillis(), time)
    return when (r.unit) {
        RelativeTime.Unit.NOW -> stringResource(R.string.notif_time_now)
        RelativeTime.Unit.MINUTES -> stringResource(R.string.notif_time_min, r.value.toInt())
        RelativeTime.Unit.HOURS -> stringResource(R.string.notif_time_hour, r.value.toInt())
        RelativeTime.Unit.DAYS -> stringResource(R.string.notif_time_day, r.value.toInt())
    }
}

// --- Conversation ---

@Composable
private fun Messages(item: NotifItem, expanded: Boolean) {
    val shown = if (expanded) item.messages.takeLast(5) else item.messages.takeLast(1)
    val showNames = item.isGroupConversation || item.messages.mapNotNull { it.sender?.toString() }.distinct().size > 1
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { m -> MessageBubble(m, showNames, expanded) }
    }
}

@Composable
private fun MessageBubble(m: NotifMessage, showName: Boolean, expanded: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val self = m.fromSelf
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!self) {
            Avatar(m.sender, m.avatar, 26.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (self) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = false)) {
            if (showName && !self) {
                Text(
                    m.sender?.toString().orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, if (self) 4.dp else 18.dp, if (self) 18.dp else 4.dp),
                color = if (self) scheme.primaryContainer else scheme.surfaceContainerHighest,
            ) {
                Text(
                    m.text.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (self) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun Avatar(name: CharSequence?, icon: Icon?, size: Dp) {
    val bitmap = rememberIconBitmap(icon, size)
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(CircleShape).background(scheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(initials(name), style = MaterialTheme.typography.labelMedium, color = scheme.onTertiaryContainer)
        }
    }
}

// --- Réponse directe ---

@Composable
private fun ReplyArea(action: NotifAction, host: VoletHost, dims: NotifDims, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var text by remember(action.index) { mutableStateOf("") }
    // 0 saisie, 1 envoi, 2 envoyé.
    var stage by remember(action.index) { mutableIntStateOf(0) }
    val canFreeForm = action.remoteInput?.allowFreeFormInput == true

    fun send(value: CharSequence, fromChoice: Boolean) {
        if (value.isBlank() || stage != 0) return
        stage = 1
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            val ok = NotifCommands.reply(host, action, value, fromChoice)
            delay(350)
            stage = if (ok) 2 else 0
            if (ok) {
                text = ""
                delay(700)
                onClose()
            }
        }
    }

    LaunchedEffect(action.index) {
        if (canFreeForm) runCatching { focus.requestFocus() }
    }

    Column(Modifier.padding(start = dims.pad, end = dims.pad, bottom = dims.pad)) {
        if (action.choices.isNotEmpty() && stage == 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                action.choices.forEach { c ->
                    SuggestionChip(onClick = { send(c, fromChoice = true) }, label = { Text(c.toString(), maxLines = 1) })
                }
            }
        }
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { -it / 2 } + fadeOut())
            },
            label = "reply",
        ) { s ->
            when (s) {
                0 -> if (canFreeForm) {
                    Surface(shape = CircleShape, color = scheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) {
                                M3Icon(Icons.Rounded.Close, stringResource(R.string.notif_close_reply), tint = scheme.onSurfaceVariant)
                            }
                            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                if (text.isEmpty()) {
                                    Text(
                                        action.remoteInput?.label?.toString() ?: stringResource(R.string.notif_reply_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                                BasicTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                                    cursorBrush = SolidColor(scheme.primary),
                                    maxLines = 4,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { send(text, fromChoice = false) }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focus)
                                        .onFocusChanged { if (it.isFocused) host.requestKeyboard(true) },
                                )
                            }
                            FilledIconButton(onClick = { send(text, fromChoice = false) }, enabled = text.isNotBlank()) {
                                M3Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.notif_send))
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                else -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (s == 2) {
                        M3Icon(Icons.Rounded.Check, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(if (s == 1) R.string.notif_sending else R.string.notif_sent),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                }
            }
        }
    }
}

// --- Menu de l'appui long ---

@Composable
private fun NotifMenu(item: NotifItem, host: VoletHost, dims: NotifDims, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(dims.pad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBadge(item, dims.badge * 0.8f)
            Spacer(Modifier.width(10.dp))
            Text(item.appName, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text(stringResource(R.string.notif_done)) }
        }
        Spacer(Modifier.size(6.dp))
        MenuRow(Icons.Rounded.Settings, stringResource(R.string.notif_settings)) {
            host.startActivity(NotifCommands.settingsIntent(item))
        }
        MenuRow(Icons.Rounded.NotificationsOff, stringResource(R.string.notif_mute)) {
            host.startActivity(NotifCommands.settingsIntent(item))
        }
        if (item.clearable) {
            Text(
                stringResource(R.string.notif_snooze),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.notif_snooze_15 to 15L,
                    R.string.notif_snooze_60 to 60L,
                    R.string.notif_snooze_120 to 120L,
                ).forEach { (label, minutes) ->
                    AssistChip(
                        onClick = {
                            NotifCommands.snooze(item.key, minutes * 60_000L)
                            onDone()
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        M3Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

// --- Vues personnalisées (RemoteViews) ---

@Composable
private fun CustomContent(views: RemoteViews, item: NotifItem) {
    val context = LocalContext.current
    // Application de la vue de l'appli ; en cas d'échec, repli sur le texte.
    val frame = remember(views) {
        runCatching {
            FrameLayout(context).apply { addView(views.apply(context, this)) }
        }.getOrNull()
    }
    if (frame != null) {
        AndroidView(factory = { frame }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 260.dp))
    } else {
        BodyText(item.text?.toString() ?: item.appName, 2)
    }
}

// --- Icônes ---

/** Petite icône de l'appli, teintée de sa couleur, dans une pastille ronde. */
@Composable
internal fun AppBadge(item: NotifItem, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (item.color != 0) Color(item.color).copy(alpha = 1f) else scheme.primary
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val bitmap = rememberIconBitmap(item.smallIcon, size * 0.6f)
    Box(
        Modifier.size(size).clip(CircleShape).background(if (item.persistent) scheme.surfaceContainerHighest else accent),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.size(size * 0.6f),
                colorFilter = ColorFilter.tint(if (item.persistent) accent else onAccent),
            )
        } else {
            Text(initials(item.appName), style = MaterialTheme.typography.labelMedium, color = onAccent)
        }
    }
}

@Composable
private fun IconImage(icon: Icon, size: Dp?, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val bitmap = rememberIconBitmap(icon, size ?: 360.dp) ?: return
    Image(bitmap, null, if (size != null) modifier.size(size) else modifier, contentScale = scale)
}

@Composable
private fun rememberIconBitmap(icon: Icon?, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val state = produceState<ImageBitmap?>(null, icon, px) {
        value = if (icon == null) null else withContext(Dispatchers.IO) { NotificationIcons.load(context, icon, px) }
    }
    return state.value
}

// --- Gestes et effets ---

/** Glisser horizontalement pour ignorer (effaçables) ; sinon élastique qui revient au ressort. */
@Composable
internal fun SwipeToDismiss(
    enabled: Boolean,
    clearing: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    var gone by remember { mutableStateOf(false) }
    val dismissLabel = stringResource(R.string.notif_dismiss)

    // « Tout effacer » : la carte s'en va vers la droite.
    LaunchedEffect(clearing) {
        if (clearing && !gone) {
            gone = true
            if (!reduce) offset.animateTo(width * 1.1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Toujours là après l'effacement (listener absent, refus) : la carte revient.
    LaunchedEffect(gone) {
        if (!gone) return@LaunchedEffect
        delay(1500)
        gone = false
        offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics { if (enabled) contentDescription = dismissLabel }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { d ->
                    // Non effaçable : résistance forte, la carte revient.
                    val delta = if (enabled) d else d * 0.25f
                    scope.launch { offset.snapTo(offset.value + delta) }
                },
                onDragStopped = { v ->
                    val far = abs(offset.value) > width * 0.4f
                    val fling = abs(v) > 1800f && sign(v) == sign(offset.value)
                    if (enabled && (far || fling)) {
                        gone = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        val target = sign(offset.value).takeIf { it != 0f }?.times(width * 1.1f) ?: width
                        if (!reduce) offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow), initialVelocity = v)
                        onDismiss()
                    } else {
                        offset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialVelocity = v)
                    }
                },
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.7f
            },
    ) { content() }
}

/** Halo animé autour de la notification mise en avant (ouverte depuis l'île). */
private fun Modifier.halo(active: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
    val reduce = rememberReduceMotion()
    val glow = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) {
            glow.animateTo(0f)
            return@LaunchedEffect
        }
        if (reduce) {
            glow.snapTo(1f)
            return@LaunchedEffect
        }
        // Quelques pulsations puis halo fixe (animation finie).
        repeat(3) {
            glow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
            glow.animateTo(0.35f, spring(stiffness = Spring.StiffnessLow))
        }
        glow.animateTo(0.8f, spring(stiffness = Spring.StiffnessLow))
    }
    val color = MaterialTheme.colorScheme.primary
    // Valeur animée lue au dessin : pas de recomposition à chaque image.
    drawWithContent {
        drawContent()
        val a = glow.value.coerceIn(0f, 1f)
        if (a > 0.01f) {
            drawOutline(shape.createOutline(size, layoutDirection, this), color.copy(alpha = a), style = Stroke(2.dp.toPx()))
        }
    }
}
