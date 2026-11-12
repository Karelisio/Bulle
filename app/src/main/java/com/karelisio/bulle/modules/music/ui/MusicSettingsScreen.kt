package com.karelisio.bulle.modules.music.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.music.MusicModule
import com.karelisio.bulle.modules.music.MusicSettings
import com.karelisio.bulle.modules.music.lyrics.LyricsRepository
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.launch

/** Écran de réglages du module Musique. */
@Composable
fun MusicSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val music = settings.music
    val enabled = MusicModule.isEnabled(settings)
    var accessGranted by remember { mutableStateOf(NotificationRepository.isAccessGranted(context)) }
    var cached by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val cleared = stringResource(R.string.music_cache_cleared)

    LifecycleResumeEffect(Unit) {
        accessGranted = NotificationRepository.isAccessGranted(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { cached = LyricsRepository.cachedCount(context) }

    fun updateMusic(transform: (MusicSettings) -> MusicSettings) = scope.launch {
        SettingsRepository.update(context) { it.copy(music = transform(it.music)) }
    }

    // L'accès aux notifications est requis pour lire les sessions média des autres applis.
    val accessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        accessGranted = NotificationRepository.isAccessGranted(context)
    }
    fun requestAccess() {
        val component = NotificationRepository.component(context)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { accessLauncher.launch(detail) }.onFailure { runCatching { accessLauncher.launch(fallback) } }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.module_music)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.music_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(R.string.music_setting_enable),
                summary = stringResource(R.string.music_setting_enable_summary),
                checked = enabled,
            ) { on ->
                scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (MusicModule.id to on)) } }
                if (on && !accessGranted) requestAccess()
            }

            AnimatedVisibility(enabled && !accessGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.music_access_needed), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = ::requestAccess) { Text(stringResource(R.string.music_access_grant)) }
                    }
                }
            }

            SwitchRow(
                icon = Icons.Rounded.PauseCircle,
                title = stringResource(R.string.music_setting_hide_paused),
                summary = stringResource(R.string.music_setting_hide_paused_summary, music.pausedHideMinutes),
                checked = music.hideWhenPaused,
                enabled = enabled,
            ) { on -> updateMusic { it.copy(hideWhenPaused = on) } }

            SwitchRow(
                icon = Icons.Rounded.Lyrics,
                title = stringResource(R.string.music_setting_lyrics),
                summary = stringResource(R.string.music_setting_lyrics_summary),
                checked = music.lyricsEnabled,
                enabled = enabled,
            ) { on -> updateMusic { it.copy(lyricsEnabled = on) } }

            SwitchRow(
                icon = Icons.Rounded.Public,
                title = stringResource(R.string.music_setting_online),
                summary = stringResource(R.string.music_setting_online_summary),
                checked = music.onlineLyrics,
                enabled = enabled && music.lyricsEnabled,
            ) { on -> updateMusic { it.copy(onlineLyrics = on) } }

            SwitchRow(
                icon = Icons.Rounded.PushPin,
                title = stringResource(R.string.music_setting_keep_lyrics_open),
                summary = stringResource(R.string.music_setting_keep_lyrics_open_summary),
                checked = music.keepLyricsOpen,
                enabled = enabled && music.lyricsEnabled,
            ) { on -> updateMusic { it.copy(keepLyricsOpen = on) } }

            SwitchRow(
                icon = Icons.Rounded.Visibility,
                title = stringResource(R.string.music_setting_visible_lyrics),
                summary = stringResource(R.string.music_setting_visible_lyrics_summary),
                checked = music.stayVisibleWithLyrics,
                enabled = enabled && music.lyricsEnabled,
            ) { on -> updateMusic { it.copy(stayVisibleWithLyrics = on) } }

            ListItem(
                leadingContent = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.music_cache_title)) },
                supportingContent = { Text(pluralStringResource(R.plurals.music_cache_count, cached, cached)) },
                trailingContent = {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                LyricsRepository.clearCache(context)
                                cached = LyricsRepository.cachedCount(context)
                                snackbar.showSnackbar(cleared)
                            }
                        },
                    ) { Text(stringResource(R.string.music_cache_clear)) }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    )
}
