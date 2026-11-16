package com.karelisio.bulle.modules.music.ui

import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R

/** Titre suivant de la file d'attente du lecteur (logique pure, testée). */
data class QueueEntry(val id: Long, val title: String, val subtitle: String?)

object MusicQueueLogic {
    /** Nombre de titres listés dans l'onglet File. */
    const val LIST_MAX = 20

    /** Titres après celui en cours ([activeId] ; inconnu : toute la file), au plus [max]. */
    fun upNext(queue: List<QueueEntry>, activeId: Long?, max: Int = 5): List<QueueEntry> {
        val start = if (activeId == null) 0 else queue.indexOfFirst { it.id == activeId }.let { if (it < 0) 0 else it + 1 }
        return queue.drop(start).take(max)
    }

    fun from(items: List<MediaSession.QueueItem>?): List<QueueEntry> = items.orEmpty().mapNotNull { q ->
        val title = q.description.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        QueueEntry(q.queueId, title, q.description.subtitle?.toString()?.takeIf { it.isNotBlank() })
    }
}

/** État de la file d'attente du lecteur, suivi en direct (titres et titre en cours). */
internal class QueueState(val upNext: List<QueueEntry>, val all: List<QueueEntry>)

@Composable
internal fun rememberQueueState(controller: MediaController): QueueState {
    var queue by remember(controller) { mutableStateOf(MusicQueueLogic.from(runCatching { controller.queue }.getOrNull())) }
    var activeId by remember(controller) { mutableStateOf(runCatching { controller.playbackState?.activeQueueItemId }.getOrNull()) }
    DisposableEffect(controller) {
        val cb = object : MediaController.Callback() {
            override fun onQueueChanged(q: MutableList<MediaSession.QueueItem>?) {
                queue = MusicQueueLogic.from(q)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                activeId = state?.activeQueueItemId
            }
        }
        runCatching { controller.registerCallback(cb) }
        onDispose { runCatching { controller.unregisterCallback(cb) } }
    }
    val active = activeId?.takeIf { it != MediaSession.QueueItem.UNKNOWN_ID.toLong() }
    return QueueState(MusicQueueLogic.upNext(queue, active, max = MusicQueueLogic.LIST_MAX), queue)
}

/**
 * Aperçu discret du titre suivant sous la barre de progression : toucher = lire ce titre,
 * appui long = ouvrir l'onglet « File ».
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun NextUpLine(next: QueueEntry, accent: Color, onPlay: () -> Unit, onShowQueue: () -> Unit) {
    val line = next.subtitle?.let { "${next.title} — $it" } ?: next.title
    Text(
        stringResource(R.string.music_next_up, line),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.62f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .combinedClickable(
                onClickLabel = stringResource(R.string.music_next_up_play),
                onLongClickLabel = stringResource(R.string.music_tab_queue),
                onLongClick = onShowQueue,
                onClick = onPlay,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

/** Pastilles « Paroles | File » (masquées en mode paroles). */
@Composable
internal fun QueueTabs(queueSelected: Boolean, accent: Color, onSelect: (Boolean) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.Row(
        Modifier.padding(top = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        listOf(false to R.string.music_tab_lyrics, true to R.string.music_tab_queue).forEach { (queue, label) ->
            androidx.compose.material3.FilterChip(
                selected = queueSelected == queue,
                onClick = {
                    if (queueSelected != queue) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                    onSelect(queue)
                },
                label = { Text(stringResource(label)) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.25f),
                    selectedLabelColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

/** Liste « À suivre » de l'onglet File : défile dans une hauteur bornée ; toucher = lire ce titre. */
@Composable
internal fun QueueList(entries: List<QueueEntry>, accent: Color, onPlay: (QueueEntry) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .heightIn(max = 260.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
    ) {
        entries.forEachIndexed { index, e ->
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable { onPlay(e) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.width(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    e.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

