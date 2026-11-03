package com.karelisio.bulle.modules.alerts.sources

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertRules
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.hasPermission
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.CheckGlyph
import com.karelisio.bulle.modules.alerts.ui.rememberEntrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

private val ShotWhite = Color(0xFFE8ECF4)

/**
 * Nouvelle capture d'écran : observe MediaStore.Images et retient les images récentes
 * d'un dossier « Screenshots ». Nécessite READ_MEDIA_IMAGES (Android 13+) ou
 * READ_EXTERNAL_STORAGE (Android 12), demandée à l'activation.
 */
object ScreenshotSource : AlertSource {
    override val id = "screenshot"

    /** Permission de lecture des images selon la version d'Android. */
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    override fun isEnabled(settings: AlertSettings) = settings.screenshot

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        val ctx = env.context.applicationContext
        if (!ctx.hasPermission(permission)) return@flow
        val seen = HashSet<Long>()
        // Image la plus récente au démarrage : référence, jamais annoncée.
        withContext(Dispatchers.IO) { latestScreenshot(ctx.contentResolver, windowSec = 3600) }?.let { seen += it.first }
        changes(ctx.contentResolver).conflate().collect {
            val found = withContext(Dispatchers.IO) { latestScreenshot(ctx.contentResolver, windowSec = 20) } ?: return@collect
            if (seen.add(found.first)) emit(ScreenshotContent(found.second, alertNow()))
        }
    }.latestAlive()

    private fun changes(resolver: ContentResolver): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** (id, uri) de la capture la plus récente des [windowSec] dernières secondes, ou null. */
    private fun latestScreenshot(resolver: ContentResolver, windowSec: Long): Pair<Long, Uri>? = runCatching {
        val col = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val since = System.currentTimeMillis() / 1000 - windowSec
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.IS_PENDING} = 0",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(since.toString()))
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, 5)
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DISPLAY_NAME,
        )
        resolver.query(col, projection, args, null)?.use { c ->
            while (c.moveToNext()) {
                if (AlertRules.isScreenshotPath(c.getString(1), c.getString(2))) {
                    val id = c.getLong(0)
                    return@runCatching id to ContentUris.withAppendedId(col, id)
                }
            }
        }
        null
    }.getOrNull()

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Suppression : MediaStore.createDeleteRequest renvoie un PendingIntent de confirmation système.
     * Lancé depuis le service d'accessibilité (autorisé à démarrer une activité en arrière-plan) ;
     * en cas d'échec, on ouvre l'image pour que l'utilisateur la supprime lui-même.
     */
    fun delete(context: Context, uri: Uri) {
        val ok = runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).sendFromIsland()
        }.isSuccess
        if (!ok) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }
    }
}

@Composable
private fun rememberThumbnail(uri: Uri, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(px, px * 2), null).asImageBitmap() }.getOrNull()
        }
    }
    return bmp
}

/** Capture enregistrée : coche tracée, miniature, Partager / Supprimer. */
data class ScreenshotContent(
    val uri: Uri,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:screenshot:$postedAt"
    override val accent = ShotWhite
    override val compactSlotWidth = 44.dp
    override val timeoutMs = 6_000L

    @Composable
    override fun Leading() = CheckGlyph(ShotWhite)

    @Composable
    override fun Trailing() {
        val bmp = rememberThumbnail(uri, 96)
        val e = rememberEntrance(from = 0.4f)
        Box(
            Modifier
                .size(18.dp, 26.dp)
                .graphicsLayer { scaleX = e.value; scaleY = e.value }
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.15f)),
        ) {
            if (bmp != null) Image(bmp, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val bmp = rememberThumbnail(uri, 360)
        AlertExpanded(
            accent = ShotWhite,
            title = stringResource(R.string.alerts_screenshot),
            subtitle = stringResource(R.string.alerts_screenshot_saved),
            glyph = { CheckGlyph(ShotWhite, size = 26.dp) },
            extra = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (bmp != null) {
                        Image(
                            bmp, null,
                            Modifier.heightIn(max = 200.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            },
            actions = {
                FilledTonalButton(onClick = { ScreenshotSource.delete(host.context, uri); host.dismiss(key) }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.alerts_delete))
                }
                FilledTonalButton(onClick = { ScreenshotSource.share(host.context, uri); host.dismiss(key) }) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.alerts_share))
                }
            },
        )
    }
}
