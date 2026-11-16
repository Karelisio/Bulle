package com.karelisio.bulle.modules.music.ui

import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * « À suivre » : file d'attente du lecteur quand il la fournit, repliée par défaut sous les
 * commandes (masquée en mode paroles : la lecture des paroles reste intacte). Toucher = lire ce titre.
 */
@Composable
internal fun MusicQueue(controller: MediaController, accent: Color) {
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
    val next = MusicQueueLogic.upNext(queue, active)
    if (next.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { open = !open }) {
            Text(
                if (open) stringResource(R.string.music_queue_hide) else stringResource(R.string.music_queue, next.size),
                color = accent,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        AnimatedVisibility(open) {
            Column {
                next.forEach { e ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { runCatching { controller.transportControls.skipToQueueItem(e.id) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        e.subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
