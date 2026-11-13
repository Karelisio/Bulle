package com.karelisio.bulle.modules.capture.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.capture.CaptureActions
import com.karelisio.bulle.modules.capture.CaptureFormat
import com.karelisio.bulle.modules.capture.CaptureState
import com.karelisio.bulle.modules.capture.memo.Memo
import com.karelisio.bulle.modules.capture.memo.MemoStore
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Liste des mémos vocaux : lecture, renommer, partager, supprimer. */
@Composable
fun MemosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val version by MemoStore.version.collectAsStateWithLifecycle()
    val memoSession by CaptureState.memo.collectAsStateWithLifecycle()
    val memos by produceState<List<Pair<Memo, Long>>?>(null, version) {
        value = withContext(Dispatchers.IO) { MemoStore.list(context).map { it to MemoStore.durationMs(it.file) } }
    }
    val player = remember { MemoPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var renaming by remember { mutableStateOf<Memo?>(null) }
    var deleting by remember { mutableStateOf<Memo?>(null) }

    ModuleSettingsScaffold(title = stringResource(R.string.capture_memos_title), onBack = onBack) {
        item {
            ModuleSettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(if (memoSession.active) R.string.capture_memo_recording else R.string.capture_memo_new)) },
                    supportingContent = { Text(stringResource(R.string.capture_memo_new_summary)) },
                    leadingContent = { Icon(if (memoSession.active) Icons.Rounded.Stop else Icons.Rounded.Mic, contentDescription = null) },
                    trailingContent = {
                        FilledTonalButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            CaptureActions.toggleMemo(context)
                        }) { Text(stringResource(if (memoSession.active) R.string.capture_stop else R.string.capture_record)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        val list = memos
        when {
            list == null -> Unit
            list.isEmpty() -> item {
                Text(
                    stringResource(R.string.capture_memos_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> items(list, key = { it.first.file.absolutePath }) { (memo, duration) ->
                MemoRow(
                    memo = memo,
                    durationMs = duration,
                    player = player,
                    onRename = { renaming = memo },
                    onShare = { shareMemo(context, memo) },
                    onDelete = { deleting = memo },
                )
            }
        }
    }

    renaming?.let { memo ->
        var text by remember(memo) { mutableStateOf(memo.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.capture_rename)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = { MemoStore.rename(context, memo, text); renaming = null },
                    enabled = MemoStore.sanitizeName(text) != null,
                ) { Text(stringResource(R.string.capture_ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.capture_cancel)) } },
        )
    }
    deleting?.let { memo ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.capture_delete_confirm)) },
            text = { Text(memo.name) },
            confirmButton = {
                TextButton(onClick = {
                    if (player.current == memo.file.absolutePath) player.stop()
                    MemoStore.delete(memo)
                    deleting = null
                }) { Text(stringResource(R.string.capture_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.capture_cancel)) } },
        )
    }
}

@Composable
private fun MemoRow(memo: Memo, durationMs: Long, player: MemoPlayer, onRename: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val playing = player.current == memo.file.absolutePath && player.playing
    var menu by remember { mutableStateOf(false) }
    ModuleSettingsCard {
        Column {
            ListItem(
                leadingContent = {
                    FilledTonalIconButton(onClick = { player.toggle(memo.file.absolutePath) }) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (playing) R.string.capture_pause else R.string.capture_play),
                        )
                    }
                },
                headlineContent = { Text(memo.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        DateUtils.formatDateTime(context, memo.modifiedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH) +
                            " · " + CaptureFormat.chrono(durationMs),
                    )
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.capture_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.capture_rename)) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; onRename() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.capture_share)) },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = { menu = false; onShare() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.capture_delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menu = false; onDelete() },
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (player.current == memo.file.absolutePath) {
                LaunchedEffect(player.current, player.playing) {
                    while (player.playing) {
                        player.refreshProgress()
                        delay(200)
                    }
                }
                LinearProgressIndicator(
                    progress = { player.progress },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/** Lecteur unique : un seul mémo joue à la fois. */
private class MemoPlayer {
    private var mp: MediaPlayer? = null
    var current by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    fun toggle(path: String) {
        val p = mp
        if (current == path && p != null) {
            if (p.isPlaying) p.pause() else p.start()
            playing = p.isPlaying
            return
        }
        stop()
        runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { this@MemoPlayer.playing = false; this@MemoPlayer.progress = 1f }
                prepare()
                start()
            }
        }.onSuccess {
            mp = it
            current = path
            playing = true
            progress = 0f
        }
    }

    fun refreshProgress() {
        val p = mp ?: return
        runCatching { if (p.duration > 0) progress = p.currentPosition.toFloat() / p.duration }
    }

    fun stop() {
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null
        current = null
        playing = false
        progress = 0f
    }

    fun release() = stop()
}

private fun shareMemo(context: Context, memo: Memo) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".capture.files", memo.file)
        val send = Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, memo.name))
    }
}
