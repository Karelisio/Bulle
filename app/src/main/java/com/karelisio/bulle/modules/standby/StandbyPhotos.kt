package com.karelisio.bulle.modules.standby

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un album (dossier MediaStore) et son nombre de photos. */
data class PhotoAlbum(val id: String, val name: String, val count: Int)

/** Lecture locale des photos pour le diaporama de Veille (rien n'est copié ni envoyé). */
object StandbyPhotos {

    fun permission(sdk: Int = Build.VERSION.SDK_INT): String =
        if (sdk >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Albums, du plus fourni au moins fourni. */
    suspend fun albums(context: Context): List<PhotoAlbum> = withContext(Dispatchers.IO) {
        if (!granted(context)) return@withContext emptyList()
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getString(idCol) ?: continue
                    val name = c.getString(nameCol) ?: "?"
                    counts[id] = name to ((counts[id]?.second ?: 0) + 1)
                }
            }
        }
        counts.map { (id, v) -> PhotoAlbum(id, v.first, v.second) }.sortedByDescending { it.count }
    }

    /** Photos d'un album, des plus récentes aux plus anciennes (au plus [limit]). */
    suspend fun photos(context: Context, albumId: String, limit: Int = 200): List<Uri> = withContext(Dispatchers.IO) {
        if (!granted(context)) return@withContext emptyList()
        val out = ArrayList<Uri>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.BUCKET_ID} = ?",
                arrayOf(albumId),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && out.size < limit) {
                    out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
                }
            }
        }
        out
    }

    /** Image réduite à la taille de l'écran (mémoire bornée). */
    suspend fun load(context: Context, uri: Uri, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val s = info.size
                val scale = maxPx.toFloat() / maxOf(s.width, s.height)
                if (scale < 1f) decoder.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

    /** Miniature (réglages). */
    suspend fun thumbnail(context: Context, uri: Uri, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(px, px), null) }.getOrNull()
    }
}

/** Effet Ken Burns (logique pure) : zoom lent et déplacement, alterné d'une photo à l'autre. */
object KenBurns {
    /** Échelle et décalage (fractions de la taille) à l'avancement [t] (0..1) de la photo n° [index]. */
    fun frame(index: Int, t: Float): Triple<Float, Float, Float> {
        val p = t.coerceIn(0f, 1f)
        val zoomIn = index % 2 == 0
        val scale = if (zoomIn) 1.04f + 0.10f * p else 1.14f - 0.10f * p
        val dirX = if (index % 4 < 2) 1f else -1f
        val dirY = if (index % 3 == 0) 1f else -1f
        return Triple(scale, dirX * 0.03f * (p - 0.5f), dirY * 0.02f * (p - 0.5f))
    }
}
