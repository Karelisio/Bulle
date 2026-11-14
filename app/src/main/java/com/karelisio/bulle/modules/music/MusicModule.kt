package com.karelisio.bulle.modules.music

import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.music.karaoke.KaraokeBridge
import com.karelisio.bulle.modules.music.lyrics.LyricsRepository
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.media.Artwork
import com.karelisio.bulle.modules.music.media.ArtworkLoader
import com.karelisio.bulle.modules.music.media.MediaSessionSource
import com.karelisio.bulle.modules.music.media.MediaSnapshot
import com.karelisio.bulle.modules.music.sync.lyricsPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Module Musique : la session média en cours (pochette, égaliseur, lecteur complet, paroles).
 * Tout est lu localement ; seule la recherche de paroles en ligne (si autorisée) contacte lrclib.net.
 */
object MusicModule : IslandModule {
    override val id = "music"
    override val titleRes = R.string.module_music

    /** Pochette chargée (null = morceau sans pochette). */
    private class ArtFor(val artwork: Artwork?)

    /** Paroles chargées, rattachées au morceau. */
    private class LyricsFor(val trackIdentity: String, val state: LyricsState)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = channelFlow {
        val context = env.context.applicationContext
        val media = MediaSessionSource.observe(context).shareIn(this, SharingStarted.Eagerly, replay = 1)
        val settings = env.settings.map { it.music }.distinctUntilChanged()

        val artwork = media
            .map { it?.track }
            .distinctUntilChanged { a, b -> a?.artIdentity == b?.artIdentity }
            .mapLatest { t -> t?.let { ArtFor(ArtworkLoader.load(context, it)) } }
            .onStart { emit(null) }

        val lyrics = combine(
            media.map { it?.track }.distinctUntilChanged { a, b -> a?.identity == b?.identity && a?.playerLyrics == b?.playerLyrics },
            settings.map { it.lyricsEnabled to it.onlineLyrics }.distinctUntilChanged(),
        ) { t, s -> t to s }
            .transformLatest { (t, s) ->
                when {
                    t == null -> emit(null)
                    !s.first -> emit(LyricsFor(t.identity, LyricsState.Off))
                    else -> {
                        emit(LyricsFor(t.identity, LyricsState.Loading))
                        val found = LyricsRepository.load(context, t, online = s.second)
                        emit(LyricsFor(t.identity, found))
                        // Paroles déjà affichées ; en arrière-plan, version mot à mot éventuelle.
                        if (s.second && found is LyricsState.Found) {
                            LyricsRepository.upgradeToWordSync(context, t, found.lyrics)
                                ?.let { emit(LyricsFor(t.identity, LyricsState.Found(it))) }
                        }
                    }
                }
            }
            .onStart { emit(null) }

        var lastArt: ArtFor? = null
        combine(media, artwork, lyrics, settings) { m, a, l, s ->
            if (a != null) lastArt = a
            if (m == null) lastArt = null
            build(m, lastArt, l, s)
        }
            .distinctUntilChanged()
            .collect {
                // La vue karaoké (activité à part) suit le même contenu, mis à jour en direct.
                KaraokeBridge.publish(it.firstOrNull() as? MusicContent)
                send(it)
            }
    }

    private fun build(m: MediaSnapshot?, art: ArtFor?, lyrics: LyricsFor?, s: MusicSettings): List<IslandContent> {
        m ?: return emptyList()
        val id = m.track.identity
        val playing = m.playback.isPlaying
        return listOf(
            MusicContent(
                controller = m.controller,
                track = m.track,
                playback = m.playback,
                // Pendant le chargement de la nouvelle pochette, on garde l'ancienne (pas de flash du
                // visuel par défaut) ; un morceau sans pochette arrive vite avec un ArtFor(id, null).
                artwork = art?.artwork,
                lyrics = lyrics?.takeIf { it.trackIdentity == id }?.state ?: LyricsState.Loading,
                postedAt = m.lastActive,
                timeoutMs = if (!playing && s.hideWhenPaused) s.pausedHideMinutes.coerceAtLeast(1) * 60_000L else null,
                keepLyricsOpen = s.keepLyricsOpen,
                stayVisibleWithLyrics = s.stayVisibleWithLyrics,
                lyricsPrefs = s.lyricsPrefs(),
                realVisualizer = s.realVisualizer,
            ),
        )
    }
}
