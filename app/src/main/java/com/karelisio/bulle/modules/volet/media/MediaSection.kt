package com.karelisio.bulle.modules.volet.media

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.session.MediaController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.karelisio.bulle.modules.music.media.ArtworkLoader
import com.karelisio.bulle.modules.music.media.MediaSessionSource
import com.karelisio.bulle.modules.music.media.MediaSnapshot
import com.karelisio.bulle.modules.volet.VoletCardDensity
import com.karelisio.bulle.modules.volet.VoletHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carte média du volet (session en cours). Rien si aucune session. */
@Composable
fun MediaSection(host: VoletHost, modifier: Modifier) {
    val context = host.context
    val snapshot by remember(context) { MediaSessionSource.observe(context) }.collectAsState(null)
    // Dernière session connue : la carte reste affichée pendant son animation de sortie.
    var last by remember { mutableStateOf<MediaSnapshot?>(null) }
    snapshot?.let { last = it }

    AnimatedVisibility(
        visible = snapshot != null,
        enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        modifier = modifier,
    ) {
        last?.let { MediaSessionCard(host, it) }
    }
}

/** Carte d'une session : charge pochette, couleurs, appli, actions et sortie, puis relie les commandes. */
@Composable
internal fun MediaSessionCard(host: VoletHost, snap: MediaSnapshot, modifier: Modifier = Modifier) {
    val context = host.context
    val controller = snap.controller
    val track = snap.track
    val pkg = track.packageName

    // Pochette : la précédente reste affichée jusqu'au chargement de la suivante (fondu croisé net).
    var artwork by remember { mutableStateOf<ImageBitmap?>(null) }
    var seeds by remember { mutableStateOf<Pair<Int?, Int?>>(null to null) }
    LaunchedEffect(track.artIdentity) {
        val art = ArtworkLoader.load(context, track)
        seeds = art?.let { withContext(Dispatchers.Default) { paletteSeeds(it.image) } } ?: (null to null)
        artwork = art?.image
    }
    val app by produceState(AppInfo(pkg, null), pkg) { value = withContext(Dispatchers.IO) { appInfo(context, pkg) } }
    val customKey = runCatching { controller.playbackState?.customActions }.getOrNull().orEmpty().take(2)
    val customActions by produceState(emptyList<MediaCustomAction>(), customKey.map { it.action to it.icon }) {
        value = withContext(Dispatchers.IO) { customKey.map { loadCustom(context, pkg, it) } }
    }
    val localOutput by remember(context) { MediaOutput.observe(context) }.collectAsState(MediaOutput.current(context))
    val output = remember(snap.playback, localOutput) { MediaOutput.remoteOutput(controller) ?: localOutput }

    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fallback = MaterialTheme.colorScheme.primary.toArgb()
    val scheme = remember(seeds, dark, fallback) { MediaColors.scheme(seeds.first, seeds.second, dark, fallback) }

    val data = MediaCardData(
        title = track.title,
        artist = track.artist,
        trackKey = track.identity,
        durationMs = track.durationMs,
        playback = snap.playback,
        appName = app.label,
        appIcon = app.icon,
        artwork = artwork,
        scheme = scheme,
        customActions = customActions,
        output = output,
    )
    val actions = remember(controller, host) { ControllerActions(host, controller, pkg) }
    MediaCard(
        data = data,
        actions = actions,
        cornerRadius = host.settings.cornerRadiusDp.dp,
        compact = host.settings.effectiveCardDensity == VoletCardDensity.COMPACT,
        modifier = modifier,
    )
}

/** Commandes reliées au MediaController de la session. */
private class ControllerActions(
    private val host: VoletHost,
    private val controller: MediaController,
    private val pkg: String,
) : MediaCardActions {
    private val controls get() = controller.transportControls

    override fun open() {
        if (host.send(controller.sessionActivity)) return
        host.context.packageManager.getLaunchIntentForPackage(pkg)?.let { host.startActivity(it) }
    }

    override fun play() = runSafely { controls.play() }
    override fun pause() = runSafely { controls.pause() }
    override fun previous() = runSafely { controls.skipToPrevious() }
    override fun next() = runSafely { controls.skipToNext() }
    override fun seekTo(positionMs: Long) = runSafely { controls.seekTo(positionMs) }

    override fun custom(id: String) = runSafely {
        val action = controller.playbackState?.customActions?.firstOrNull { it.action == id }
        controls.sendCustomAction(id, action?.extras)
    }

    override fun output() = MediaOutput.openSwitcher(host.context, pkg, host::close, host::startActivity)

    // Session détruite entre-temps : la commande est simplement ignorée.
    private inline fun runSafely(block: () -> Unit) {
        runCatching(block)
    }
}

private data class AppInfo(val label: String, val icon: ImageBitmap?)

private fun appInfo(context: Context, pkg: String): AppInfo {
    val pm = context.packageManager
    return try {
        val info = pm.getApplicationInfo(pkg, 0)
        AppInfo(pm.getApplicationLabel(info).toString(), runCatching { pm.getApplicationIcon(info).toBitmap(64, 64).asImageBitmap() }.getOrNull())
    } catch (_: PackageManager.NameNotFoundException) {
        AppInfo(pkg, null)
    }
}

/** Icône d'une action personnalisée : ressource du paquet du lecteur. */
private fun loadCustom(context: Context, pkg: String, action: android.media.session.PlaybackState.CustomAction): MediaCustomAction {
    val icon = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        ResourcesCompat.getDrawable(res, action.icon, null)?.toBitmap(72, 72)?.asImageBitmap()
    }.getOrNull()
    return MediaCustomAction(action.action, action.name?.toString().orEmpty(), icon)
}

/** Couleurs graines de la pochette : dominante (fond) et vive (accent). */
private fun paletteSeeds(image: ImageBitmap): Pair<Int?, Int?> = runCatching {
    val bitmap = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    val p = Palette.from(bitmap).maximumColorCount(16).generate()
    val dominant = p.dominantSwatch?.rgb
    val vivid = (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: p.mutedSwatch)?.rgb
    dominant to vivid
}.getOrDefault(null to null)
