package com.karelisio.bulle.modules.music

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.music.lyrics.LyricsState
import com.karelisio.bulle.modules.music.media.Artwork
import com.karelisio.bulle.modules.music.media.ArtworkLoader
import com.karelisio.bulle.modules.music.media.PlaybackSnapshot
import com.karelisio.bulle.modules.music.media.TrackInfo
import com.karelisio.bulle.modules.music.ui.CompactArtwork
import com.karelisio.bulle.modules.music.ui.CompactEqualizer
import com.karelisio.bulle.modules.music.ui.MinimalArtwork
import com.karelisio.bulle.modules.music.ui.MusicPlayer

/** Contenu « musique » de l'île : une session média et tout ce qu'il faut pour l'afficher. */
data class MusicContent(
    val controller: MediaController,
    val track: TrackInfo,
    val playback: PlaybackSnapshot,
    val artwork: Artwork?,
    val lyrics: LyricsState,
    override val postedAt: Long,
    override val timeoutMs: Long?,
    /** Réglage [MusicSettings.keepLyricsOpen] au moment de la publication. */
    val keepLyricsOpen: Boolean = true,
    /** Réglage [MusicSettings.stayVisibleWithLyrics] au moment de la publication. */
    val stayVisibleWithLyrics: Boolean = true,
) : IslandContent() {
    override val key = "music:${track.packageName}"
    override val moduleId = MusicModule.id
    override val priority = Priority.MEDIA
    override val accent: Color = artwork?.accent ?: ArtworkLoader.DefaultAccent
    override val compactSlotWidth: Dp = 42.dp

    val isPlaying: Boolean get() = playback.isPlaying

    /**
     * Paroles synchronisées disponibles pour ce morceau (donc affichées dans la carte étendue).
     * Ne dépend que de l'état des paroles : ne change pas à chaque tick de position.
     */
    val hasSyncedLyrics: Boolean
        get() = (lyrics as? LyricsState.Found)?.lyrics?.let { it.synced && it.lines.isNotEmpty() } == true

    // Calculés à la construction : l'arbitre et le contrôleur les lisent souvent.
    override val stayExpandedOnOutsideTouch: Boolean = keepLyricsOpen && hasSyncedLyrics
    override val showWhenHidden: Boolean = stayVisibleWithLyrics && isPlaying && hasSyncedLyrics

    @Composable
    override fun Leading() = CompactArtwork(artwork?.image, isPlaying, accent)

    @Composable
    override fun Trailing() = CompactEqualizer(isPlaying, accent)

    @Composable
    override fun Minimal() = MinimalArtwork(artwork?.image, isPlaying, accent)

    @Composable
    override fun Expanded(host: IslandHost) = MusicPlayer(this, host)

    /** Ouvre le lecteur (activité de la session, sinon l'appli). */
    fun openPlayer(context: Context) {
        val pending = controller.sessionActivity
        if (pending != null) {
            val ok = runCatching {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                pending.send(context, 0, null, null, null, null, options.toBundle())
            }.onFailure { Log.d("BulleMusic", "sessionActivity refusée", it) }.isSuccess
            if (ok) return
        }
        context.packageManager.getLaunchIntentForPackage(track.packageName)?.let { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
