package com.karelisio.bulle.modules.music.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.max

/** Pochette réduite et sa couleur d'accent. */
data class Artwork(val image: ImageBitmap, val accent: Color)

/**
 * Chargement des pochettes : bitmap fourni par le lecteur, sinon URI (content://, file://,
 * android.resource:// ou http(s)). Les images téléchargées restent EN MÉMOIRE UNIQUEMENT
 * (LruCache), jamais écrites sur disque.
 */
object ArtworkLoader {

    private const val TAG = "BulleMusic"
    private const val TARGET_PX = 256
    private const val MAX_DOWNLOAD_BYTES = 6 * 1024 * 1024

    /** Couleur d'accent par défaut (pas de pochette). */
    val DefaultAccent = Color(0xFFB4C5FF)

    private val cache = object : LruCache<String, Artwork>(12 * TARGET_PX * TARGET_PX * 4) {
        override fun sizeOf(key: String, value: Artwork) = value.image.width * value.image.height * 4
    }

    suspend fun load(context: Context, track: TrackInfo): Artwork? = withContext(Dispatchers.IO) {
        val key = track.artIdentity
        cache.get(key)?.let { return@withContext it }
        val bitmap = track.artBitmap?.let { runCatching { downscale(it) }.getOrNull() }
            ?: track.artUris.firstNotNullOfOrNull { uri -> runCatching { fromUri(context, uri) }.onFailure { Log.d(TAG, "Pochette illisible : $uri", it) }.getOrNull() }
            ?: return@withContext null
        Artwork(bitmap.asImageBitmap(), accentOf(bitmap)).also { cache.put(key, it) }
    }

    private fun downscale(src: Bitmap): Bitmap {
        val config = src.config
        val soft = if (config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val longest = max(soft.width, soft.height)
        if (longest <= TARGET_PX) return if (soft === src) src.copy(Bitmap.Config.ARGB_8888, false) else soft
        val ratio = TARGET_PX.toFloat() / longest
        return soft.scale((soft.width * ratio).toInt().coerceAtLeast(1), (soft.height * ratio).toInt().coerceAtLeast(1))
    }

    private fun fromUri(context: Context, uriString: String): Bitmap? {
        val uri = uriString.toUri()
        val source = when (uri.scheme?.lowercase()) {
            "http", "https" -> ImageDecoder.createSource(ByteBuffer.wrap(download(uriString) ?: return null))
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE, ContentResolver.SCHEME_ANDROID_RESOURCE ->
                ImageDecoder.createSource(context.contentResolver, uri)
            else -> return null
        }
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Logiciel : nécessaire pour la palette ; taille réduite dès le décodage.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > TARGET_PX) {
                val r = TARGET_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * r).toInt().coerceAtLeast(1), (info.size.height * r).toInt().coerceAtLeast(1))
            }
        }
    }

    /** Téléchargement en mémoire (thread IO), taille plafonnée. */
    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = true
            conn.useCaches = false
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_DOWNLOAD_BYTES) return null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Couleur vibrante de la pochette, éclaircie si besoin pour rester lisible sur le noir de l'île. */
    private fun accentOf(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.dominantSwatch
            ?: return DefaultAccent
        return Color(readableOnBlack(swatch.rgb))
    }

    /** Remonte la luminosité (et un peu la saturation) d'une couleur trop sombre pour un fond noir. */
    fun readableOnBlack(argb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[2] = hsl[2].coerceIn(0.62f, 0.82f)
        if (hsl[1] > 0.08f) hsl[1] = hsl[1].coerceAtLeast(0.45f)
        return ColorUtils.HSLToColor(hsl)
    }
}
